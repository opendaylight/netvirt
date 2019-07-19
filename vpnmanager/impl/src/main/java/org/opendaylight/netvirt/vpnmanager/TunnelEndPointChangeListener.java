/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelEndPointChangeListener
    extends AbstractAsyncDataTreeChangeListener<TunnelEndPoints> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelEndPointChangeListener.class);

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;
    private final IFibManager fibManager;

    @Inject
    public TunnelEndPointChangeListener(final DataBroker broker, final VpnInterfaceManager vpnInterfaceManager,
            final JobCoordinator jobCoordinator, VpnUtil vpnUtil, final IFibManager fibManager) {
        super(broker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(DpnEndpoints.class).child(DPNTEPsInfo.class).child(TunnelEndPoints.class),
                Executors.newListeningSingleThreadExecutor("TunnelEndPointChangeListener", LOG));
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
        this.fibManager = fibManager;
        start();
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }


    @Override
    public void remove(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints tep) {
    }

    @Override
    public void update(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints origTep,
        TunnelEndPoints updatedTep) {
    }

    @Override
    public void add(InstanceIdentifier<TunnelEndPoints> key, TunnelEndPoints tep) {
        Uint64 dpnId = key.firstIdentifierOf(DPNTEPsInfo.class).firstKeyOf(DPNTEPsInfo.class).getDPNID();
        if (Uint64.ZERO.equals(dpnId)) {
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
            final Uint32 vpnId = vpnUtil.getVpnId(vpnName);
            LOG.info("add: Handling TEP {} add for VPN instance {}", tep.getInterfaceName(), vpnName);
            final String primaryRd = vpnUtil.getPrimaryRd(vpnName);
            if (!vpnUtil.isVpnPendingDelete(primaryRd)) {
                List<VpnInterfaces> vpnInterfaces = vpnUtil.getDpnVpnInterfaces(broker, vpnInstance, dpnId);
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
                                List<ListenableFuture<?>> futures = new ArrayList<>();
                                Set<String> prefixesForRefreshFib = new HashSet<>();
                                ListenableFuture<?> writeConfigFuture =
                                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                                        writeConfigTxn -> futures.add(
                                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                                                writeOperTxn -> futures.add(
                                                    txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                                        writeInvTxn ->
                                                            vpnInterfaceManager.processVpnInterfaceAdjacencies(dpnId,
                                                                lPortTag, vpnName, primaryRd, vpnInterfaceName, vpnId,
                                                                writeConfigTxn, writeOperTxn, writeInvTxn,
                                                                interfaceState, prefixesForRefreshFib)
                                                    )))));
                                Futures.addCallback(writeConfigFuture, new FutureCallback<Object>() {
                                    @Override
                                    public void onSuccess(Object voidObj) {
                                        prefixesForRefreshFib.forEach(prefix -> {
                                            fibManager.refreshVrfEntry(primaryRd, prefix);
                                        });
                                    }

                                    @Override
                                    public void onFailure(Throwable throwable) {
                                        LOG.debug("addVpnInterface: write Tx config execution failed", throwable);
                                    }
                                }, MoreExecutors.directExecutor());
                                futures.add(writeConfigFuture);
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

}
