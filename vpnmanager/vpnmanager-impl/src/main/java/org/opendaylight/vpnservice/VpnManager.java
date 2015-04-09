/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnManager extends AbstractDataChangeListener<VpnInstance> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnManager.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;

    /**
     * Listens for data change related to VPN Instance
     * Informs the BGP about VRF information
     * 
     * @param db - dataBroker reference
     */
    public VpnManager(final DataBroker db) {
        super(VpnInstance.class);
        broker = db;
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), VpnManager.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("VPN Service DataChange listener registration fail!", e);
            throw new IllegalStateException("VPN Service registration Listener failed.", e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance del) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance original, VpnInstance update) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance value) {
        LOG.info("key: " + identifier + ", value=" + value);
        //TODO: Generate VPN ID for this instance, where to store in model ... ?

        //TODO: Add VRF to BGP
    }

    private InstanceIdentifier<?> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstances.class).child(VpnInstance.class);
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
        LOG.info("VPN Manager Closed");
    }
}
