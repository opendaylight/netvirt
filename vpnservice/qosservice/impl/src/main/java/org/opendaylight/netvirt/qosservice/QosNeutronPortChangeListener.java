/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;


import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNeutronPortChangeListener extends AsyncDataTreeChangeListenerBase<Port, QosNeutronPortChangeListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final INeutronVpnManager neutronVpnManager;
    private  final IMdsalApiManager mdsalUtils;

    @Inject
    public QosNeutronPortChangeListener(final DataBroker dataBroker,
                                        final INeutronVpnManager neutronVpnManager,
                                        final OdlInterfaceRpcService odlInterfaceRpcService,
                                        final IMdsalApiManager mdsalUtils) {
        super(Port.class, QosNeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        this.neutronVpnManager = neutronVpnManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.mdsalUtils = mdsalUtils;
        LOG.info("{} created",  getClass().getSimpleName());
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.info("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    protected QosNeutronPortChangeListener getDataTreeChangeListener() {
        return QosNeutronPortChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Port> instanceIdentifier, Port port) {
       //do nothing
    }

    @Override
    protected void remove(InstanceIdentifier<Port> instanceIdentifier, Port port) {
        //Remove DSCP Flow when the port is removed
        //Qos Policy Deletion
        QosPortExtension removeQos = port.getAugmentation(QosPortExtension.class);
        if (removeQos != null) {
            QosNeutronUtils.handleNeutronPortRemove(dataBroker, odlInterfaceRpcService,
                    mdsalUtils, port, removeQos.getQosPolicyId());
            QosNeutronUtils.removeFromQosPortsCache(removeQos.getQosPolicyId(), port);
        } else {
            Network network =  neutronVpnManager.getNeutronNetwork(port.getNetworkId());
            if (network != null && network.getAugmentation(QosNetworkExtension.class) != null) {
                Uuid networkQosUuid = network.getAugmentation(QosNetworkExtension.class).getQosPolicyId();
                if (networkQosUuid != null) {
                    QosNeutronUtils.handleNeutronPortRemove(dataBroker, odlInterfaceRpcService,
                            mdsalUtils, port, networkQosUuid);
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Port> instanceIdentifier, Port original, Port update) {
        // check for QoS updates
        QosPortExtension updateQos = update.getAugmentation(QosPortExtension.class);
        QosPortExtension originalQos = original.getAugmentation(QosPortExtension.class);

        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            QosNeutronUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            QosNeutronUtils.handleNeutronPortQosAdd(dataBroker, odlInterfaceRpcService, mdsalUtils,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {
            // qosservice policy update
            QosNeutronUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
            QosNeutronUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            QosNeutronUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService, mdsalUtils,
                    update, updateQos.getQosPolicyId(), originalQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            QosNeutronUtils.handleNeutronPortQosRemove(dataBroker, odlInterfaceRpcService, neutronVpnManager,
                    mdsalUtils, original, originalQos.getQosPolicyId());
            QosNeutronUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
        }
    }
}

