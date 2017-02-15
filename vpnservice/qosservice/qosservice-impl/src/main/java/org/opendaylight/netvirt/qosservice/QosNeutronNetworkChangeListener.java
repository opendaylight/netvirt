/*
 * Copyright (c) 2017 Intel Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosNetworkExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosNeutronNetworkChangeListener extends AsyncDataTreeChangeListenerBase<Network, QosNeutronNetworkChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosNeutronNetworkChangeListener.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;

    public QosNeutronNetworkChangeListener(final DataBroker dataBroker, OdlInterfaceRpcService odlInterfaceRpcService) {
        super(Network.class, QosNeutronNetworkChangeListener.class);
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Network> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Networks.class).child(Network.class);
    }

    @Override
    protected QosNeutronNetworkChangeListener getDataTreeChangeListener() {
        return QosNeutronNetworkChangeListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        //do Nothing
    }

    @Override
    protected void update(InstanceIdentifier<Network> instanceIdentifier, Network original, Network update) {
        QosNetworkExtension updateQos = update.getAugmentation(QosNetworkExtension.class);
        QosNetworkExtension originalQos = original.getAugmentation(QosNetworkExtension.class);
        if (originalQos == null && updateQos != null) {
            // qosservice policy add
            QosNeutronMappings.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            QosUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {
            // qosservice policy update
            QosNeutronMappings.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
            QosNeutronMappings.addToQosNetworksCache(updateQos.getQosPolicyId(), update);
            QosUtils.handleNeutronNetworkQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qosservice policy delete
            QosUtils.handleNeutronNetworkQosRemove(dataBroker, odlInterfaceRpcService,
                    original, originalQos.getQosPolicyId());
            QosNeutronMappings.removeFromQosNetworksCache(originalQos.getQosPolicyId(), original);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Network> instanceIdentifier, Network network) {
        //do Nothing
    }
}

