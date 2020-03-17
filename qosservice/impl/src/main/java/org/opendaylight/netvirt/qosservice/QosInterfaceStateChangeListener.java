/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.util.Collections;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
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
public class QosInterfaceStateChangeListener extends AbstractClusteredAsyncDataTreeChangeListener<Interface>
        implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(QosInterfaceStateChangeListener.class);

    private final DataBroker dataBroker;
    private final UuidUtil uuidUtil;
    private final QosAlertManager qosAlertManager;
    private final QosNeutronUtils qosNeutronUtils;
    private final INeutronVpnManager neutronVpnManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public QosInterfaceStateChangeListener(final DataBroker dataBroker, final QosAlertManager qosAlertManager,
                                           final QosNeutronUtils qosNeutronUtils,
                                           final INeutronVpnManager neutronVpnManager,
                                           final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                           final QosServiceRecoveryHandler qosServiceRecoveryHandler,
                                           final JobCoordinator jobCoordinator) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(InterfacesState.class)
                .child(Interface.class),
                Executors.newListeningSingleThreadExecutor("QosInterfaceStateChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.uuidUtil = new UuidUtil();
        this.qosAlertManager = qosAlertManager;
        this.qosNeutronUtils = qosNeutronUtils;
        this.neutronVpnManager = neutronVpnManager;
        this.jobCoordinator = jobCoordinator;
        serviceRecoveryRegistry.addRecoverableListener(qosServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        LOG.trace("{} created",  getClass().getSimpleName());
    }

    public void init() {
        LOG.trace("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
    }

    @Override
    public void deregisterListener() {
    }


    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        if (L2vlan.class.equals(intrf.getType())) {
            final String interfaceName = intrf.getName();
            getNeutronPort(interfaceName).ifPresent(port -> {
                Network network = qosNeutronUtils.getNeutronNetwork(port.getNetworkId());
                LOG.debug("Qos Service : Received interface {} PORT UP event ", interfaceName);
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
    }

    private java.util.Optional<Port> getNeutronPort(String portName) {
        return uuidUtil.newUuidIfValidPattern(portName)
                .map(qosNeutronUtils::getNeutronPort);
    }

    private Optional<Port> getNeutronPortForRemove(Interface intrf) {
        final String portName = intrf.getName();
        Optional<Uuid> uuid = uuidUtil.newUuidIfValidPattern(portName);
        if (uuid.isPresent()) {
            Port port = qosNeutronUtils.getNeutronPort(portName);
            if (port != null) {
                return Optional.ofNullable(uuid.map(qosNeutronUtils::getNeutronPort).orElse(null));
            }
            if (qosNeutronUtils.isBindServiceDone(uuid)) {
                LOG.trace("Qos Service : interface {} clearing stale flow entries if any", portName);
                jobCoordinator.enqueueJob("QosPort-" + portName, () -> {
                    qosNeutronUtils.removeStaleFlowEntry(intrf, NwConstants.ETHTYPE_IPV4);
                    qosNeutronUtils.removeStaleFlowEntry(intrf, NwConstants.ETHTYPE_IPV6);
                    qosNeutronUtils.unbindservice(portName);
                    qosNeutronUtils.removeInterfaceInQosConfiguredPorts(uuid);
                    return Collections.emptyList();
                });
            }
        }
        return Optional.empty();
    }

    @Override
    public void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
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
    public void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        if (original.getType() == null && L2vlan.class.equals(update.getType())) {
            // IfType was missing at creation, add it now
            add(identifier, update);
        }
    }
}


