/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static java.util.Collections.emptyList;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnOpStatusListener extends AbstractAsyncDataTreeChangeListener<VpnInstanceOpDataEntry> {
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
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class), Executors
                .newListeningSingleThreadExecutor("VpnOpStatusListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.mdsalManager = mdsalManager;
        this.vpnFootprintService = vpnFootprintService;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
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
    public void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry value) {
        LOG.info("remove: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                       VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        LOG.info("update: Processing update for vpn {} with rd {}", update.getVpnInstanceName(), update.getVrfId());
        if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.PendingDelete
                && vpnFootprintService.isVpnFootPrintCleared(update)) {
            //Cleanup VPN data
            final String vpnName = update.getVpnInstanceName();
            final List<String> rds = update.getRd();
            String primaryRd = update.getVrfId();
            final Uint32 vpnId = vpnUtil.getVpnId(vpnName);
            jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
                // Two transactions are used, one for operational, one for config; we only submit the config
                // transaction if the operational transaction succeeds
                ListenableFuture<?> operationalFuture = txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        Datastore.OPERATIONAL, operTx -> {
                            // Clean up VPNExtraRoutes Operational DS
                        if (rds != null && VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                            if (update.getType() == VpnInstanceOpDataEntry.Type.L2) {
                                rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(
                                            rd, false, AddressFamily.L2VPN));
                            }
                            if (update.getIpAddressFamilyConfigured()
                                        == VpnInstanceOpDataEntry.IpAddressFamilyConfigured.Ipv4) {
                                rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(
                                            rd, false, AddressFamily.IPV4));
                            }
                            if (update.getIpAddressFamilyConfigured()
                                        == VpnInstanceOpDataEntry.IpAddressFamilyConfigured.Ipv6) {
                                rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(
                                            rd, false, AddressFamily.IPV6));
                            }
                            if (update.getIpAddressFamilyConfigured()
                                        == VpnInstanceOpDataEntry.IpAddressFamilyConfigured.Ipv4AndIpv6) {
                                rds.parallelStream()
                                            .forEach(rd -> bgpManager.deleteVrf(
                                                    rd, false, AddressFamily.IPV4));
                                rds.parallelStream()
                                            .forEach(rd -> bgpManager.deleteVrf(
                                                    rd, false, AddressFamily.IPV6));
                            }
                        }
                        InstanceIdentifier<Vpn> vpnToExtraroute =
                                VpnExtraRouteHelper.getVpnToExtrarouteVpnIdentifier(vpnName);
                        Optional<Vpn> optVpnToExtraroute = Optional.empty();
                        try {
                            optVpnToExtraroute = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                        LogicalDatastoreType.OPERATIONAL, vpnToExtraroute);
                        } catch (InterruptedException | ExecutionException e) {
                            LOG.error("update: Failed to read VpnToExtraRoute for vpn {}", vpnName);
                        }
                        if (optVpnToExtraroute.isPresent()) {
                            VpnUtil.removeVpnExtraRouteForVpn(vpnName, operTx);
                        }
                        if (VpnUtil.isL3VpnOverVxLan(update.getL3vni())) {
                            vpnUtil.removeExternalTunnelDemuxFlows(vpnName);
                        }
                            // Clean up PrefixToInterface Operational DS
                        Optional<VpnIds> optPrefixToIntf = Optional.empty();
                        try {
                            optPrefixToIntf = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                    LogicalDatastoreType.OPERATIONAL, VpnUtil.getPrefixToInterfaceIdentifier(vpnId));
                        } catch (InterruptedException | ExecutionException e) {
                            LOG.error("update: Failed to read PrefixToInterface for vpn {}", vpnName);
                        }
                        if (optPrefixToIntf.isPresent()) {
                            VpnUtil.removePrefixToInterfaceForVpnId(vpnId, operTx);
                        }
                        // Clean up L3NextHop Operational DS
                        InstanceIdentifier<VpnNexthops> vpnNextHops = InstanceIdentifier.builder(L3nexthop.class).child(
                                VpnNexthops.class, new VpnNexthopsKey(vpnId)).build();
                        Optional<VpnNexthops> optL3nexthopForVpnId = Optional.empty();
                        try {
                            optL3nexthopForVpnId = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                    LogicalDatastoreType.OPERATIONAL, vpnNextHops);
                        } catch (InterruptedException | ExecutionException e) {
                            LOG.error("update: Failed to read VpnNextHops for vpn {}", vpnName);
                        }
                        if (optL3nexthopForVpnId.isPresent()) {
                            VpnUtil.removeL3nexthopForVpnId(vpnId, operTx);
                        }

                        // Clean up VPNInstanceOpDataEntry
                        VpnUtil.removeVpnOpInstance(primaryRd, operTx);
                    });

                Futures.addCallback(operationalFuture, new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object result) {
                        Futures.addCallback(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                                Datastore.CONFIGURATION, confTx -> {
                                    // Clean up VpnInstanceToVpnId from Config DS
                                VpnUtil.removeVpnIdToVpnInstance(vpnId, confTx);
                                VpnUtil.removeVpnInstanceToVpnId(vpnName, confTx);
                                LOG.trace("Removed vpnIdentifier for  rd{} vpnname {}", primaryRd, vpnName);

                                // Clean up FIB Entries Config DS
                                // FIXME: separate out to somehow?
                                final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
                                lock.lock();
                                try {
                                    fibManager.removeVrfTable(primaryRd, confTx);
                                } finally {
                                    lock.unlock();
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
            Map<VpnTargetKey, VpnTarget> vpnTargetMap = update.getVpnTargets().getVpnTarget();
            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();
            if (vpnTargetMap != null) {
                for (VpnTarget vpnTarget : vpnTargetMap.values()) {
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
                //RD update case get only updated RD list
                List<String> rds = update.getRd() != null ? new ArrayList<>(update.getRd()) : new ArrayList<>();
                if (original.getRd() != null && original.getRd().size() != rds.size()) {
                    rds.removeAll(original.getRd());
                }
                rds.parallelStream().forEach(rd -> {
                    try {
                        List<String> importRTList = rd.equals(primaryRd) ? irtList : emptyList();
                        LOG.info("VpnOpStatusListener.update: updating BGPVPN for vpn {} with RD {}"
                                        + " Type is {}, IPtype is {}, iRT {}", vpnName, primaryRd, update.getType(),
                                update.getIpAddressFamilyConfigured(), importRTList);
                        int ipValue = VpnUtil.getIpFamilyValueToRemove(original,update);
                        switch (ipValue) {
                            case 4:
                                bgpManager.deleteVrf(rd, false, AddressFamily.IPV4);
                                break;
                            case 6:
                                bgpManager.deleteVrf(rd, false, AddressFamily.IPV6);
                                break;
                            case 10:
                                bgpManager.deleteVrf(rd, false, AddressFamily.IPV4);
                                bgpManager.deleteVrf(rd, false, AddressFamily.IPV6);
                                break;
                            default:
                                break;
                        }
                        /* Update vrf entry with newly added RD list. VPN does not support for
                         * deleting existing RDs
                         */
                        if (original.getRd().size() != update.getRd().size()) {
                            ipValue = VpnUtil.getIpFamilyValueToAdd(original,update);
                            switch (ipValue) {
                                case 4:
                                    bgpManager.addVrf(rd, importRTList, ertList, AddressFamily.IPV4);
                                    break;
                                case 6:
                                    bgpManager.addVrf(rd, importRTList, ertList, AddressFamily.IPV6);
                                    break;
                                case 10:
                                    bgpManager.addVrf(rd, importRTList, ertList, AddressFamily.IPV4);
                                    bgpManager.addVrf(rd, importRTList, ertList, AddressFamily.IPV6);
                                    break;
                                default:
                                    break;
                            }
                        }
                    } catch (RuntimeException e) {
                        LOG.error("VpnOpStatusListener.update: Exception when updating VRF to BGP for vpn {} rd {}",
                                vpnName, rd, e);
                    }
                });
                return emptyList();
            });
        }
    }

    @Override
    public void add(final InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                    final VpnInstanceOpDataEntry value) {
        LOG.debug("add: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }

    private class PostDeleteVpnInstanceWorker implements FutureCallback<Object> {
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
        public void onSuccess(Object ignored) {
            Integer releaseId = vpnUtil.releaseId(VpnConstants.VPN_IDPOOL_NAME, vpnName);
            log.info("onSuccess: VpnId for VpnName {} is released to IdManager successfully.", vpnName);
            if (releaseId == VpnConstants.INVALID_IDMAN_ID) {
                LOG.error("VpnOpStatusListener: onSuccess: Failed to release ID for vpn {}, vpn id pool {}", vpnName,
                        VpnConstants.VPN_IDPOOL_NAME);
            }
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
