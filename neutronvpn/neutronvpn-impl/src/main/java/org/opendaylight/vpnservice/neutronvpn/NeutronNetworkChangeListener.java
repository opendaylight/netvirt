/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.neutronvpn;


import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NeutronNetworkChangeListener extends AbstractDataChangeListener<Network> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronNetworkChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private NeutronvpnManager nvpnManager;


    public NeutronNetworkChangeListener(final DataBroker db, NeutronvpnManager nVpnMgr) {
        super(Network.class);
        broker = db;
        nvpnManager = nVpnMgr;
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("N_Network listener Closed");
    }


    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).
                            child(Networks.class).child(Network.class),
                    NeutronNetworkChangeListener.this, DataChangeScope.SUBTREE);
            LOG.info("Neutron Manager Network DataChange listener registration Success!");
        } catch (final Exception e) {
            LOG.error("Neutron Manager Network DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager Network DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Network> identifier, Network input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding Network : key: " + identifier + ", value=" + input);
        }
        //Create ELAN instance for this network
        createElanInstance(input.getUuid().getValue());
    }

    @Override
    protected void remove(InstanceIdentifier<Network> identifier, Network input) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Removing Network : key: " + identifier + ", value=" + input);
        }
        //Delete ELAN instance for this network
        deleteElanInstance(input.getUuid().getValue());
    }

    @Override
    protected void update(InstanceIdentifier<Network> identifier, Network original, Network update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating Network : key: " + identifier + ", original value=" + original + ", update value=" +
                    update);
        }
    }

    private void createElanInstance(String elanInstanceName) {
        ElanInstance elanInstance = new ElanInstanceBuilder().setElanInstanceName(elanInstanceName).setKey(new
                ElanInstanceKey(elanInstanceName)).build();
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, elanInstance);

    }

    private void deleteElanInstance(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> id = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
    }

}
