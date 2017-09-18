/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;
import java.util.Collections;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanTunnelInterfaceStateListener extends AsyncDataTreeChangeListenerBase<StateTunnelList,
    ElanTunnelInterfaceStateListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ElanTunnelInterfaceStateListener.class);
    private final DataBroker dataBroker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final ElanUtils elanUtils;

    public ElanTunnelInterfaceStateListener(final DataBroker dataBroker,
            final ElanInterfaceManager elanInterfaceManager, final ElanUtils elanUtils) {
        super(StateTunnelList.class, ElanTunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.elanInterfaceManager = elanInterfaceManager;
        this.elanUtils = elanUtils;
    }

    @Override
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> key, StateTunnelList delete) {
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> key, StateTunnelList original,
            StateTunnelList update) {
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> key, StateTunnelList add) {
        LOG.info("processing add state for StateTunnelList {}", add);
        if (!isInternalTunnel(add)) {
            LOG.trace("tunnel {} is not a internal vxlan tunnel", add);
            return;
        }
        if (elanUtils.isTunnelInLogicalGroup(add.getTunnelInterfaceName())) {
            LOG.trace("MULTIPLE_VxLAN_TUNNELS: ignoring the tunnel event for {}", add.getTunnelInterfaceName());
            return;
        }
        TunnelOperStatus tunOpStatus = add.getOperState();
        if (tunOpStatus != TunnelOperStatus.Down && tunOpStatus != TunnelOperStatus.Up) {
            LOG.trace("Returning because unsupported tunnelOperStatus {}", tunOpStatus);
            return;
        }
        DataStoreJobCoordinator.getInstance().enqueueJob(add.getTunnelInterfaceName(), () -> {
            BigInteger srcDpId = new BigInteger(add.getSrcInfo().getTepDeviceId());
            BigInteger dstDpId = new BigInteger(add.getDstInfo().getTepDeviceId());
            try {
                LOG.info("Handling tunnel state event for srcDpId {} and dstDpId {} ",
                        srcDpId, dstDpId);
                elanInterfaceManager.handleInternalTunnelStateEvent(srcDpId, dstDpId);
            } catch (ElanException e) {
                LOG.error("Failed handle tunnel state event between srcDpId {} and dstDpId {} ",
                        srcDpId, dstDpId, e);
            }
            return Collections.emptyList();
        }, ElanConstants.JOB_MAX_RETRIES);
    }

    @Override
    protected ElanTunnelInterfaceStateListener getDataTreeChangeListener() {
        return this;
    }

    private boolean isInternalTunnel(StateTunnelList stateTunnelList) {
        return stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class;
    }

}
