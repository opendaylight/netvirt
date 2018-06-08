/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnOpStatusListener extends AsyncDataTreeChangeListenerBase<VpnInstanceOpDataEntry, VpnOpStatusListener> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnOpStatusListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IBgpManager bgpManager;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final VpnFootprintService vpnFootprintService;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnOpStatusListener(final DataBroker dataBroker, final IBgpManager bgpManager,
                               final IdManagerService idManager, final IFibManager fibManager,
                               final IMdsalApiManager mdsalManager, final VpnFootprintService vpnFootprintService,
                               final JobCoordinator jobCoordinator, VpnUtil vpnUtil) {
        super(VpnInstanceOpDataEntry.class, VpnOpStatusListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.mdsalManager = mdsalManager;
        this.vpnFootprintService = vpnFootprintService;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInstanceOpDataEntry> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class);
    }

    @Override
    protected VpnOpStatusListener getDataTreeChangeListener() {
        return VpnOpStatusListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry value) {
        LOG.info("remove: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                          VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        LOG.info("update: Processing update for vpn {} with rd {}", update.getVpnInstanceName(), update.getVrfId());
        if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.PendingDelete
                && vpnFootprintService.isVpnFootPrintCleared(update)) {
            //Cleanup VPN data
            final String vpnName = update.getVpnInstanceName();
            final List<String> rds = update.getRd();
            String primaryRd = update.getVrfId();
            final long vpnId = vpnUtil.getVpnId(vpnName);
            jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
                // Two transactions are used, one for operational, one for config; we only submit the config
                // transaction if the operational transaction succeeds
                ListenableFuture<Void> operationalFuture = txRunner.callWithNewWriteOnlyTransactionAndSubmit(operTx -> {
                    // Clean up VPNExtraRoutes Operational DS
                    if (VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                        if (update.getType() == VpnInstanceOpDataEntry.Type.L2) {
                            rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false, AddressFamily.L2VPN));
                        }
                        if (update.isIpv4Configured()) {
                            rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false, AddressFamily.IPV4));
                        }
                        if (update.isIpv6Configured()) {
                            rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false, AddressFamily.IPV6));
                        }
                    }
                    InstanceIdentifier<Vpn> vpnToExtraroute =
                            VpnExtraRouteHelper.getVpnToExtrarouteVpnIdentifier(vpnName);
                    Optional<Vpn> optVpnToExtraroute = Optional.absent();
                    try {
                        optVpnToExtraroute = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.OPERATIONAL, vpnToExtraroute);
                    } catch (ReadFailedException e) {
                        LOG.error("update: Failed to read VpnToExtraRoute for vpn {}", vpnName);
                    }
                    if (optVpnToExtraroute.isPresent()) {
                        VpnUtil.removeVpnExtraRouteForVpn(vpnName, operTx);
                    }
                    if (VpnUtil.isL3VpnOverVxLan(update.getL3vni())) {
                        vpnUtil.removeExternalTunnelDemuxFlows(vpnName);
                    }
                    // Clean up PrefixToInterface Operational DS
                    Optional<VpnIds> optPrefixToIntf = Optional.absent();
                    try {
                        optPrefixToIntf = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.OPERATIONAL, VpnUtil.getPrefixToInterfaceIdentifier(vpnId));
                    } catch (ReadFailedException e) {
                        LOG.error("update: Failed to read PrefixToInterface for vpn {}", vpnName);
                    }
                    if (optPrefixToIntf.isPresent()) {
                        VpnUtil.removePrefixToInterfaceForVpnId(vpnId, operTx);
                    }
                    // Clean up L3NextHop Operational DS
                    InstanceIdentifier<VpnNexthops> vpnNextHops = InstanceIdentifier.builder(L3nexthop.class).child(
                            VpnNexthops.class, new VpnNexthopsKey(vpnId)).build();
                    Optional<VpnNexthops> optL3nexthopForVpnId = Optional.absent();
                    try {
                        optL3nexthopForVpnId = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.OPERATIONAL, vpnNextHops);
                    } catch (ReadFailedException e) {
                        LOG.error("update: Failed to read VpnNextHops for vpn {}", vpnName);
                    }
                    if (optL3nexthopForVpnId.isPresent()) {
                        VpnUtil.removeL3nexthopForVpnId(vpnId, operTx);
                    }

                    // Clean up VPNInstanceOpDataEntry
                    VpnUtil.removeVpnOpInstance(primaryRd, operTx);
                });

                Futures.addCallback(operationalFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Futures.addCallback(txRunner.callWithNewWriteOnlyTransactionAndSubmit(confTx -> {
                            // Clean up VpnInstanceToVpnId from Config DS
                            VpnUtil.removeVpnIdToVpnInstance(vpnId, confTx);
                            VpnUtil.removeVpnInstanceToVpnId(vpnName, confTx);
                            LOG.trace("Removed vpnIdentifier for  rd{} vpnname {}", primaryRd, vpnName);

                            // Clean up FIB Entries Config DS
                            synchronized (vpnName.intern()) {
                                fibManager.removeVrfTable(primaryRd, confTx);
                            }
                        }), new VpnOpStatusListener.PostDeleteVpnInstanceWorker(vpnName),
                                MoreExecutors.directExecutor());
                        // Note: Release the of VpnId will happen in PostDeleteVpnInstancWorker only if
                        // operationalTxn/Config succeeds.
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        LOG.error("Error deleting VPN {}", vpnName, throwable);
                    }
                }, MoreExecutors.directExecutor());

                LOG.info("Removed vpn data for vpnname {}", vpnName);
                return Collections.singletonList(operationalFuture);
            }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        } else if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.Created) {
            final String vpnName = update.getVpnInstanceName();
            final List<String> rds = update.getRd();
            String primaryRd = update.getVrfId();
            if (!VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                return;
            }
            if (original == null) {
                LOG.error("VpnOpStatusListener.update: vpn {} with RD {}. add() handler already called",
                       vpnName, primaryRd);
                return;
            }
            if (update.getVpnTargets() == null) {
                LOG.error("VpnOpStatusListener.update: vpn {} with RD {} vpnTargets not ready",
                       vpnName, primaryRd);
                return;
            }
            List<VpnTarget> vpnTargetList = update.getVpnTargets().getVpnTarget();
            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();
            if (vpnTargetList != null) {
                for (VpnTarget vpnTarget : vpnTargetList) {
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                        ertList.add(vpnTarget.getVrfRTValue());
                    }
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                        irtList.add(vpnTarget.getVrfRTValue());
                    }
                    if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                        ertList.add(vpnTarget.getVrfRTValue());
                        irtList.add(vpnTarget.getVrfRTValue());
                    }
                }
            } else {
                LOG.error("VpnOpStatusListener.update: vpn target list is empty, cannot add BGP"
                      + " VPN {} RD {}", vpnName, primaryRd);
                return;
            }
            jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
                rds.parallelStream().forEach(rd -> {
                    try {
                        LOG.info("VpnOpStatusListener.update: updating BGPVPN for vpn {} with RD {}"
                                + " Type is {}, IPv4 is {}, IPv6 is {}", vpnName, primaryRd, update.getType(),
                                update.isIpv4Configured(), update.isIpv6Configured());
                        if (update.getType() == VpnInstanceOpDataEntry.Type.L2) {
                            bgpManager.addVrf(rd, irtList, ertList, AddressFamily.L2VPN);
                        } else {
                            bgpManager.deleteVrf(rd, false, AddressFamily.L2VPN);
                        }
                        if (!original.isIpv4Configured() && update.isIpv4Configured()) {
                            bgpManager.addVrf(rd, irtList, ertList, AddressFamily.IPV4);
                        } else if (original.isIpv4Configured() && !update.isIpv4Configured()) {
                            bgpManager.deleteVrf(rd, false, AddressFamily.IPV4);
                        }
                        if (!original.isIpv6Configured() && update.isIpv6Configured()) {
                            bgpManager.addVrf(rd, irtList, ertList, AddressFamily.IPV6);
                        } else if (original.isIpv6Configured() && !update.isIpv6Configured()) {
                            bgpManager.deleteVrf(rd, false, AddressFamily.IPV6);
                        }
                    } catch (Exception e) {
                        LOG.error("VpnOpStatusListener.update: Exception when updating VRF to BGP"
                               + " for vpn {} rd {}", vpnName, rd);
                    }
                });
                return Collections.emptyList();
            });
        }
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                       final VpnInstanceOpDataEntry value) {
        LOG.debug("add: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }

    private class PostDeleteVpnInstanceWorker implements FutureCallback<Void> {
        private final Logger log = LoggerFactory.getLogger(VpnOpStatusListener.PostDeleteVpnInstanceWorker.class);
        String vpnName;

        PostDeleteVpnInstanceWorker(String vpnName)  {
            this.vpnName = vpnName;
        }

        /**
         * This implies that all the future instances have returned success.
         * Release the ID used for VPN back to IdManager
         */
        @Override
        public void onSuccess(Void ignored) {
            vpnUtil.releaseId(VpnConstants.VPN_IDPOOL_NAME, vpnName);
            log.info("onSuccess: VpnId for VpnName {} is released to IdManager successfully.", vpnName);
        }

        /**
         * This method is used to handle failure callbacks.
         */
        @Override
        public void onFailure(Throwable throwable) {
            log.error("onFailure: Job for vpnInstance: {} failed with exception:",
                      vpnName , throwable);
        }
    }
}
