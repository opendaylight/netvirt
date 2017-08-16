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
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosInterfaceStateChangeListener extends AsyncDataTreeChangeListenerBase<Interface,
        QosInterfaceStateChangeListener> implements
        AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(QosInterfaceStateChangeListener.class);

    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final INeutronVpnManager neutronVpnManager;
    private final IMdsalApiManager mdsalUtils;
    private final UuidUtil uuidUtil;

    @Inject
    public QosInterfaceStateChangeListener(final DataBroker dataBroker,
                                           final OdlInterfaceRpcService odlInterfaceRpcService,
                                           final INeutronVpnManager neutronVpnManager,
                                           final IMdsalApiManager mdsalUtils) {
        super(Interface.class, QosInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.neutronVpnManager = neutronVpnManager;
        this.mdsalUtils = mdsalUtils;
        this.uuidUtil = new UuidUtil();
        LOG.debug("{} created",  getClass().getSimpleName());
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        LOG.debug("{} init and registerListener done", getClass().getSimpleName());
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
                    Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());
                    LOG.trace("Qos Service : Received interface {} PORT UP event ", interfaceName);
                    if (port.getAugmentation(QosPortExtension.class) != null) {
                        Uuid portQosUuid = port.getAugmentation(QosPortExtension.class).getQosPolicyId();
                        if (portQosUuid != null) {
                            QosNeutronUtils.addToQosPortsCache(portQosUuid, port);
                            QosNeutronUtils.handleNeutronPortQosAdd(dataBroker, odlInterfaceRpcService, mdsalUtils,
                                    port, portQosUuid);
                        }

                    } else {
                        if (network.getAugmentation(QosNetworkExtension.class) != null) {
                            Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
                            if (networkQosUuid != null) {
                                QosNeutronUtils.handleNeutronPortQosAdd(dataBroker, odlInterfaceRpcService, mdsalUtils,
                                        port, networkQosUuid);
                            }
                        }
                    }
                    if (QosNeutronUtils.hasBandwidthLimitRule(neutronVpnManager, port)) {
                        QosAlertManager.addToQosAlertCache(port);
                    }
                });
            }
        } catch (Exception e) {
            LOG.error("Qos:Exception caught in Interface Operational State Up event", e);
        }
    }

    private java.util.Optional<Port> getNeutronPort(String portName) {
        return uuidUtil.newUuidIfValidPattern(portName)
                // .toJavaUtil()
                .transform(java.util.Optional::of).or(java.util.Optional.empty())
                .map(neutronVpnManager::getNeutronPort);
    }

    private Optional<Port> getNeutronPortForRemove(Interface intrf) {
        final String portName = intrf.getName();
        Optional<Uuid> uuid = uuidUtil.newUuidIfValidPattern(portName);
        if (uuid.isPresent()) {
            Port port = neutronVpnManager.getNeutronPort(portName);
            if (port != null) {
                // Don’t use Optional.transform() here, getNeutronPort() can return null
                return Optional.fromNullable(neutronVpnManager.getNeutronPort(uuid.get()));
            }
            LOG.trace("Qos Service : interface {} clearing stale flow entries if any", portName);
            QosNeutronUtils.removeStaleFlowEntry(dataBroker,mdsalUtils,odlInterfaceRpcService,intrf);
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
                QosAlertManager.removeFromQosAlertCache(new NodeConnectorId(lowerLayerIf));
                QosPortExtension removeQos = port.getAugmentation(QosPortExtension.class);
                if (removeQos != null) {
                    QosNeutronUtils.handleNeutronPortRemove(dataBroker, odlInterfaceRpcService,
                            mdsalUtils, port, removeQos.getQosPolicyId(), intrf);
                    QosNeutronUtils.removeFromQosPortsCache(removeQos.getQosPolicyId(), port);
                } else {
                    Network network = neutronVpnManager.getNeutronNetwork(port.getNetworkId());
                    if (network != null && network.getAugmentation(QosNetworkExtension.class) != null) {
                        Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
                        if (networkQosUuid != null) {
                            QosNeutronUtils.handleNeutronPortRemove(dataBroker, odlInterfaceRpcService,
                                    mdsalUtils, port, networkQosUuid, intrf);
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


