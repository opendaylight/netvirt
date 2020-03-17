/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanTunnelInterfaceStateListener extends AbstractAsyncDataTreeChangeListener<StateTunnelList> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanTunnelInterfaceStateListener.class);
    private final DataBroker dataBroker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final ElanUtils elanUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public ElanTunnelInterfaceStateListener(final DataBroker dataBroker,
            final ElanInterfaceManager elanInterfaceManager, final ElanUtils elanUtils,
            final JobCoordinator jobCoordinator) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(TunnelsState.class)
                .child(StateTunnelList.class),
                Executors.newListeningSingleThreadExecutor("ElanTunnelInterfaceStateListener", LOG));
        this.dataBroker = dataBroker;
        this.elanInterfaceManager = elanInterfaceManager;
        this.elanUtils = elanUtils;
        this.jobCoordinator = jobCoordinator;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<StateTunnelList> key, StateTunnelList delete) {
    }

    @Override
    public void update(InstanceIdentifier<StateTunnelList> key, StateTunnelList original,
            StateTunnelList update) {
    }

    @Override
    public void add(InstanceIdentifier<StateTunnelList> key, StateTunnelList add) {
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
        try {
            Uint64 srcDpId = Uint64.valueOf(add.getSrcInfo().getTepDeviceId());
            Uint64 dstDpId = Uint64.valueOf(add.getDstInfo().getTepDeviceId());
            jobCoordinator.enqueueJob(add.getTunnelInterfaceName(), () -> {
                LOG.info("Handling tunnel state event for srcDpId {} and dstDpId {} ",
                        srcDpId, dstDpId);
                elanInterfaceManager.handleInternalTunnelStateEvent(srcDpId, dstDpId);
                return Collections.emptyList();
            }, ElanConstants.JOB_MAX_RETRIES);
        } catch (NumberFormatException e) {
            LOG.error("Invalid source TepDeviceId {} or destination TepDeviceId {}", add.getSrcInfo().getTepDeviceId(),
                add.getDstInfo().getTepDeviceId());
        }
    }

    private static boolean isInternalTunnel(StateTunnelList stateTunnelList) {
        return stateTunnelList.getDstInfo() != null
                ? stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class : false;
    }

}
