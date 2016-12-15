/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netvirt.qosservice;



import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QosInterfaceStateChangeListener extends AsyncDataTreeChangeListenerBase<Interface, QosInterfaceStateChangeListener> implements
        AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(QosInterfaceStateChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final INeutronVpnManager neutronVpnManager;

    public QosInterfaceStateChangeListener(final DataBroker dataBroker, final OdlInterfaceRpcService odlInterfaceRpcService,final INeutronVpnManager neutronVpnManager) {
        super(Interface.class, QosInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.neutronVpnManager = neutronVpnManager;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
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
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        try {
            final String interfaceName = intrf.getName();
            Port port = neutronVpnManager.getNeutronPort(new Uuid(interfaceName));
            Network network =  neutronVpnManager.getNeutronNetwork(port.getNetworkId());
            LOG.trace("Qos Service : Received interface {} PORT UP event ", interfaceName);
            if (port.getAugmentation(QosPortExtension.class)!= null) {
                Uuid portQosUuid = port.getAugmentation(QosPortExtension.class).getQosPolicyId();
                if (portQosUuid != null) {
                    QosNeutronUtils.addToQosPortsCache(portQosUuid, port);
                    QosNeutronUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService, port, portQosUuid);
                }

            } else {
                if (network.getAugmentation(QosNetworkExtension.class) != null) {
                    Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
                    if (networkQosUuid != null) {
                        QosNeutronUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService, port, networkQosUuid);
                    }
                }
            }

        } catch (Exception e) {
            LOG.error("Qos:Exception caught in Interface Operational State Up event", e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {

    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {

    }
}


