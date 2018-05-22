/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import com.google.common.base.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.srm.RecoverableListener;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosInterfaceStateChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Interface,
        QosInterfaceStateChangeListener> implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(QosInterfaceStateChangeListener.class);

    private final DataBroker dataBroker;
    private final UuidUtil uuidUtil;
    private final QosAlertManager qosAlertManager;
    private final QosNeutronUtils qosNeutronUtils;
    private final INeutronVpnManager neutronVpnManager;

    @Inject
    public QosInterfaceStateChangeListener(final DataBroker dataBroker, final QosAlertManager qosAlertManager,
                                           final QosNeutronUtils qosNeutronUtils,
                                           final INeutronVpnManager neutronVpnManager,
                                           final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                           final QosServiceRecoveryHandler qosServiceRecoveryHandler) {
        super(Interface.class, QosInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.uuidUtil = new UuidUtil();
        this.qosAlertManager = qosAlertManager;
        this.qosNeutronUtils = qosNeutronUtils;
        this.neutronVpnManager = neutronVpnManager;
        serviceRecoveryRegistry.addRecoverableListener(qosServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        LOG.debug("{} created",  getClass().getSimpleName());
    }

    @PostConstruct
    public void init() {
        registerListener();
        LOG.debug("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected QosInterfaceStateChangeListener getDataTreeChangeListener() {
        return QosInterfaceStateChangeListener.this;
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        try {
            if (L2vlan.class.equals(intrf.getType())) {
                final String interfaceName = intrf.getName();
                getNeutronPort(interfaceName).ifPresent(port -> {
                    Network network = qosNeutronUtils.getNeutronNetwork(port.getNetworkId());
                    LOG.trace("Qos Service : Received interface {} PORT UP event ", interfaceName);
                    if (port.augmentation(QosPortExtension.class) != null) {
                        Uuid portQosUuid = port.augmentation(QosPortExtension.class).getQosPolicyId();
                        if (portQosUuid != null) {
                            qosNeutronUtils.addToQosPortsCache(portQosUuid, port);
                            qosNeutronUtils.handleQosInterfaceAdd(port, portQosUuid);
                        }
                    } else {
                        if (network.augmentation(QosNetworkExtension.class) != null) {
                            Uuid networkQosUuid = network.augmentation(QosNetworkExtension.class).getQosPolicyId();
                            if (networkQosUuid != null) {
                                qosNeutronUtils.handleQosInterfaceAdd(port, networkQosUuid);
                            }
                        }
                    }
                    qosAlertManager.processInterfaceUpEvent(interfaceName);
                });
            }
        } catch (Exception e) {
            LOG.error("Qos:Exception caught in Interface Operational State Up event {}", e);
        }
    }

    private java.util.Optional<Port> getNeutronPort(String portName) {
        return uuidUtil.newUuidIfValidPattern(portName)
                .toJavaUtil()
                .map(qosNeutronUtils::getNeutronPort);
    }

    private Optional<Port> getNeutronPortForRemove(Interface intrf) {
        final String portName = intrf.getName();
        Optional<Uuid> uuid = uuidUtil.newUuidIfValidPattern(portName);
        if (uuid.isPresent()) {
            Port port = qosNeutronUtils.getNeutronPort(portName);
            if (port != null) {
                return Optional.fromJavaUtil(uuid.toJavaUtil().map(qosNeutronUtils::getNeutronPort));
            }
            LOG.trace("Qos Service : interface {} clearing stale flow entries if any", portName);
            qosNeutronUtils.removeStaleFlowEntry(intrf, NwConstants.ETHTYPE_IPV4);
            qosNeutronUtils.removeStaleFlowEntry(intrf, NwConstants.ETHTYPE_IPV6);
        }
        return Optional.absent();
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        if (L2vlan.class.equals(intrf.getType())) {
            final String interfaceName = intrf.getName();
            // Guava Optional asSet().forEach() emulates Java 8 Optional ifPresent()
            getNeutronPortForRemove(intrf).asSet().forEach(port -> {
                LOG.trace("Qos Service : Received interface {} PORT DOWN event ", interfaceName);

                String lowerLayerIf = intrf.getLowerLayerIf().get(0);
                LOG.trace("lowerLayerIf {}", lowerLayerIf);
                qosAlertManager.removeLowerLayerIfFromQosAlertCache(lowerLayerIf);
                QosPortExtension removeQos = port.augmentation(QosPortExtension.class);
                if (removeQos != null) {
                    qosNeutronUtils.handleNeutronPortRemove(port, removeQos.getQosPolicyId(), intrf);
                    qosNeutronUtils.removeFromQosPortsCache(removeQos.getQosPolicyId(), port);
                } else {
                    Network network = qosNeutronUtils.getNeutronNetwork(port.getNetworkId());
                    if (network != null && network.augmentation(QosNetworkExtension.class) != null) {
                        Uuid networkQosUuid = network.augmentation(QosNetworkExtension.class).getQosPolicyId();
                        if (networkQosUuid != null) {
                            qosNeutronUtils.handleNeutronPortRemove(port, networkQosUuid, intrf);
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        if (original.getType() == null && L2vlan.class.equals(update.getType())) {
            // IfType was missing at creation, add it now
            add(identifier, update);
        }
    }
}


