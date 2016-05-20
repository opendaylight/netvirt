/*
 * Copyright (c) 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInstanceOpListener extends AbstractDataChangeListener<VpnInstanceOpDataEntry> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInstanceOpListener.class);

    private final VpnInstanceListener vpnInstanceListener;
    private final DataBroker broker;

    private ListenerRegistration<DataChangeListener> opListenerRegistration;

    private static final InstanceIdentifier<VpnInstanceOpDataEntry> VPN_INSTANCE_OP_DATA_ENTRY_IID = InstanceIdentifier
            .create(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class);

    public VpnInstanceOpListener(final DataBroker db, final VpnInstanceListener vpnInstanceListener) {
        super(VpnInstanceOpDataEntry.class);
        this.broker = db;
        this.vpnInstanceListener = vpnInstanceListener;
    }

    /**
     * Blueprint start method.
     */
    public void start() {
        opListenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                VPN_INSTANCE_OP_DATA_ENTRY_IID, this, DataChangeScope.SUBTREE);
    }

    /**
     * Blueprint close method.
     */
    @Override
    public void close() throws Exception {
        if (opListenerRegistration != null) {
            opListenerRegistration.close();
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry del) {

    }

    @Override
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        final VpnInstanceOpDataEntryKey key = identifier.firstKeyOf(VpnInstanceOpDataEntry.class);
        String vpnName = key.getVrfId();

        LOG.trace("VpnInstanceOpListener update: vpn name {} interface count in Old VpnOp Instance {} in New VpnOp Instance {}" ,
                        vpnName, original.getVpnInterfaceCount(), update.getVpnInterfaceCount() );

        if((original.getVpnInterfaceCount() != update.getVpnInterfaceCount()) && (update.getVpnInterfaceCount() == 0)) {
            vpnInstanceListener.notifyTaskIfRequired(vpnName);
        }
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry add) {
    }
}