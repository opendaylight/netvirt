/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnOpStatusListener extends AsyncDataTreeChangeListenerBase<VpnInstanceOpDataEntry, VpnOpStatusListener> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnOpStatusListener.class);
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final VpnFootprintService vpnFootprintService;
    private final VpnInterfaceOpListener vpnInterfaceOpListener;
    private final JobCoordinator jobCoordinator;

    @Inject
    public VpnOpStatusListener(final DataBroker dataBroker, final IBgpManager bgpManager,
                               final IdManagerService idManager, final IFibManager fibManager,
                               final IMdsalApiManager mdsalManager,
                               final VpnFootprintService vpnFootprintService,
                               final VpnInterfaceOpListener vpnInterfaceOpListener,
                               final JobCoordinator jobCoordinator) {
        super(VpnInstanceOpDataEntry.class, VpnOpStatusListener.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.mdsalManager = mdsalManager;
        this.vpnFootprintService = vpnFootprintService;
        this.vpnInterfaceOpListener = vpnInterfaceOpListener;
        this.jobCoordinator = jobCoordinator;
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

    private void processStaleVpnIfaces(String vpnName, List<VpnToDpnList> vpnToDpnList) {
        for (VpnToDpnList vpnToDpn: vpnToDpnList) {
            List<VpnInterfaces> ifaces = vpnToDpn.getVpnInterfaces();
            if ((ifaces == null) || (ifaces.isEmpty())) {
                continue;
            }
            for (VpnInterfaces vpnIface: ifaces) {
                String ifaceName = vpnIface.getInterfaceName();
                Optional<VpnInterfaceOpDataEntry> vpnIfaceOpDataEntry =
                        VpnUtil.getVpnInterfaceOpDataEntry(dataBroker, ifaceName, vpnName);
                InstanceIdentifier<VpnInterfaceOpDataEntry> ifaceOpEntryId =
                        VpnUtil.getVpnInterfaceOpDataEntryIdentifier(ifaceName, vpnName);
                if ((vpnIfaceOpDataEntry.isPresent()) && (ifaceOpEntryId != null)) {
                    LOG.debug("update: remove staled vpnInterface {}", ifaceName);
                    vpnInterfaceOpListener.remove(ifaceOpEntryId, vpnIfaceOpDataEntry.get());
                }
            }
        }
    }

    private void processPendingDeleteState(VpnInstanceOpDataEntry update) {
        final String vpnName = update.getVpnInstanceName();
        String primaryRd = update.getVrfId();
        if (!vpnFootprintService.isVpnFootPrintCleared(update)) {
            // remove staled vpnIfaces, hence update again VpnInstanceOpDataEntry
            processStaleVpnIfaces(vpnName, update.getVpnToDpnList());
            // need to return, because suppression of staled ifaces has already triggered
            // another update VpnInstanceOpDataEntry event
            return;
        }
        //Cleanup VPN data
        final List<String> rds = update.getRd();
        final long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            // Clean up VpnInstanceToVpnId from Config DS
            VpnUtil.removeVpnIdToVpnInstance(dataBroker, vpnId, writeConfigTxn);
            VpnUtil.removeVpnInstanceToVpnId(dataBroker, vpnName, writeConfigTxn);
            LOG.trace("Removed vpnIdentifier for rd {} vpnname {}", primaryRd, vpnName);
            // Clean up FIB Entries Config DS
            fibManager.removeVrfTable(primaryRd, null);
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
            InstanceIdentifier<Vpn> vpnToExtraroute = VpnExtraRouteHelper.getVpnToExtrarouteVpnIdentifier(vpnName);
            Optional<Vpn> optVpnToExtraroute = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, vpnToExtraroute);
            if (optVpnToExtraroute.isPresent()) {
                VpnUtil.removeVpnExtraRouteForVpn(dataBroker, vpnName, writeOperTxn);
            }

            // Clean up PrefixToInterface Operational DS
            Optional<VpnIds> optPrefixToIntf = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                                            VpnUtil.getPrefixToInterfaceIdentifier(vpnId));
            if (optPrefixToIntf.isPresent()) {
                VpnUtil.removePrefixToInterfaceForVpnId(dataBroker, vpnId, writeOperTxn);
            }

            // Clean up L3NextHop Operational DS
            InstanceIdentifier<VpnNexthops> vpnNextHops = InstanceIdentifier.builder(L3nexthop.class).child(
                VpnNexthops.class, new VpnNexthopsKey(vpnId)).build();
            Optional<VpnNexthops> optL3nexthopForVpnId = VpnUtil.read(dataBroker,
                                                                      LogicalDatastoreType.OPERATIONAL,
                                                                      vpnNextHops);
            if (optL3nexthopForVpnId.isPresent()) {
                VpnUtil.removeL3nexthopForVpnId(dataBroker, vpnId, writeOperTxn);
            }

            // Clean up VPNInstanceOpDataEntry
            VpnUtil.removeVpnOpInstance(dataBroker, primaryRd, writeOperTxn);

            // Note: Release the of VpnId will happen in PostDeleteVpnInstancWorker only if
            // operationalTxn/Config succeeds.

            CheckedFuture<Void, TransactionCommitFailedException> checkFutures = writeOperTxn.submit();
            try {
                checkFutures.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error deleting vpn {} ", vpnName);
                writeConfigTxn.cancel();
                throw new RuntimeException(e);
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeConfigTxn.submit());
            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
            Futures.addCallback(listenableFuture, new VpnOpStatusListener.PostDeleteVpnInstanceWorker(vpnName));
            LOG.info("Removed vpn data for vpnname {}", vpnName);
            return futures;
        }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
    }

    private void processCreatedState(VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
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
        final List<String> rds = update.getRd();
        jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
            rds.parallelStream().filter(rd -> {
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
                return false;
            }).count();
            return Collections.emptyList();
        });
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                          VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        LOG.info("update: Processing update for vpn {} with rd {}", update.getVpnInstanceName(), update.getVrfId());
        if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.PendingDelete) {
            processPendingDeleteState(update);
        } else if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.Created) {
            processCreatedState(original, update);
        }
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                       final VpnInstanceOpDataEntry value) {
        LOG.debug("add: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }

    private class PostDeleteVpnInstanceWorker implements FutureCallback<List<Void>> {
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
        public void onSuccess(List<Void> voids) {
            VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnName);
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
