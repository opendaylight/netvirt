/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelEndPointChangeListener
    extends AsyncDataTreeChangeListenerBase<TunnelEndPoints, TunnelEndPointChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelEndPointChangeListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;

    @Inject
    public TunnelEndPointChangeListener(final DataBroker broker, final VpnInterfaceManager vpnInterfaceManager,
            final JobCoordinator jobCoordinator, VpnUtil vpnUtil) {
        super(TunnelEndPoints.class, TunnelEndPointChangeListener.class);
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<TunnelEndPoints> getWildCardPath() {
        return InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class).child(TunnelEndPoints.class)
            .build();
    }

    @Override
    protected void remove(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints tep) {
    }

    @Override
    protected void update(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints origTep,
        TunnelEndPoints updatedTep) {
    }

    @Override
    protected void add(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints tep) {
        BigInteger dpnId = key.firstIdentifierOf(DPNTEPsInfo.class).firstKeyOf(DPNTEPsInfo.class).getDPNID();
        if (BigInteger.ZERO.equals(dpnId)) {
            LOG.warn("add: Invalid DPN id for TEP {}", tep.getInterfaceName());
            return;
        }

        List<VpnInstance> vpnInstances = VpnHelper.getAllVpnInstances(broker);
        if (vpnInstances == null || vpnInstances.isEmpty()) {
            LOG.warn("add: dpnId: {}: tep: {}: No VPN instances defined", dpnId, tep.getInterfaceName());
            return;
        }

        for (VpnInstance vpnInstance : vpnInstances) {
            final String vpnName = vpnInstance.getVpnInstanceName();
            final long vpnId = vpnUtil.getVpnId(vpnName);
            LOG.info("add: Handling TEP {} add for VPN instance {}", tep.getInterfaceName(), vpnName);
            final String primaryRd = vpnUtil.getPrimaryRd(vpnName);
            if (!vpnUtil.isVpnPendingDelete(primaryRd)) {
                List<VpnInterfaces> vpnInterfaces = vpnUtil.getDpnVpnInterfaces(vpnInstance, dpnId);
                if (vpnInterfaces != null) {
                    for (VpnInterfaces vpnInterface : vpnInterfaces) {
                        String vpnInterfaceName = vpnInterface.getInterfaceName();
                        jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterfaceName,
                            () -> {
                                final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                                        .rev140508.interfaces.state.Interface
                                        interfaceState =
                                        InterfaceUtils.getInterfaceStateFromOperDS(broker, vpnInterfaceName);
                                if (interfaceState == null) {
                                    LOG.debug("add: Cannot retrieve interfaceState for vpnInterfaceName {}, "
                                            + "cannot generate lPortTag and process adjacencies", vpnInterfaceName);
                                    return Collections.emptyList();
                                }
                                final int lPortTag = interfaceState.getIfIndex();
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                                    writeConfigTxn -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                                        writeOperTxn -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                                            writeInvTxn -> vpnInterfaceManager.processVpnInterfaceAdjacencies(
                                                    dpnId, lPortTag, vpnName, primaryRd, vpnInterfaceName, vpnId,
                                                    writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState)))))));
                                LOG.trace("add: Handled TEP {} add for VPN instance {} VPN interface {}",
                                        tep.getInterfaceName(), vpnName, vpnInterfaceName);
                                return futures;
                            });
                    }
                }
            } else {
                LOG.error("add: Ignoring addition of tunnel interface {} dpn {} for vpnInstance {} with primaryRd {},"
                        + " as the VPN is already marked for deletion", tep.getInterfaceName(), dpnId,
                        vpnName, primaryRd);
            }
        }
    }

    @Override
    protected TunnelEndPointChangeListener getDataTreeChangeListener() {
        return this;
    }
}
