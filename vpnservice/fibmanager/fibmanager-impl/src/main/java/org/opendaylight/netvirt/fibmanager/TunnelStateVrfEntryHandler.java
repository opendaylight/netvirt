/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class TunnelStateVrfEntryHandler extends BaseVrfEntryHandler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TunnelStateVrfEntryHandler.class);
    private final DataBroker dataBroker;
    private final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler;

    @Inject
    public TunnelStateVrfEntryHandler(final DataBroker dataBroker,
                                      final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler) {
        super(dataBroker, null, null);
        this.dataBroker = dataBroker;
        this.bgpRouteVrfEntryHandler = bgpRouteVrfEntryHandler;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void populateExternalRoutesOnDpn(final BigInteger dpnId, final long vpnId, final String rd,
                                            final String localNextHopIp, final String remoteNextHopIp) {
        LOG.trace("populateExternalRoutesOnDpn : dpn {}, vpn {}, rd {}, localNexthopIp {} , remoteNextHopIp {} ",
                dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
        InstanceIdentifier<VrfTables> id = FibUtil.buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (vrfTable.isPresent()) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    synchronized (vpnInstance.getVpnInstanceName().intern()) {
                        WriteTransaction writeCfgTxn = dataBroker.newWriteOnlyTransaction();
                        vrfTable.get().getVrfEntry().stream()
                                .filter(vrfEntry -> RouteOrigin.BGP == RouteOrigin.value(vrfEntry.getOrigin()))
                                .forEach(bgpRouteVrfEntryHandler.getConsumerForCreatingRemoteFib(dpnId, vpnId,
                                        rd, remoteNextHopIp, vrfTable, writeCfgTxn, txnObjects));
                        futures.add(writeCfgTxn.submit());
                    }
                    return futures;
                });
        }
    }

    public void cleanUpExternalRoutesOnDpn(final BigInteger dpnId, final long vpnId, final String rd,
                                           final String localNextHopIp, final String remoteNextHopIp) {
        LOG.trace("cleanUpExternalRoutesOnDpn : cleanup remote routes on dpn {} for vpn {}, rd {}, "
                        + " localNexthopIp {} , remoteNexhtHopIp {}",
                dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
        InstanceIdentifier<VrfTables> id = FibUtil.buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (vrfTable.isPresent()) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    synchronized (vpnInstance.getVpnInstanceName().intern()) {
                        WriteTransaction writeCfgTxn = dataBroker.newWriteOnlyTransaction();
                        vrfTable.get().getVrfEntry().stream()
                                .filter(vrfEntry -> RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP)
                                .forEach(bgpRouteVrfEntryHandler.getConsumerForDeletingRemoteFib(dpnId, vpnId, rd,
                                        remoteNextHopIp, vrfTable, writeCfgTxn, txnObjects));
                        futures.add(writeCfgTxn.submit());
                    }
                    return futures;
                });
        }
    }

    public void manageRemoteRouteOnDPN(final boolean action,
                                       final BigInteger localDpnId,
                                       final long vpnId,
                                       final String rd,
                                       final String destPrefix,
                                       final String destTepIp,
                                       final long label) {
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);

        if (vpnInstance == null) {
            LOG.error("VpnInstance for rd {} not present for prefix {}", rd, destPrefix);
            return;
        }
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, localDpnId),
            () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                synchronized (vpnInstance.getVpnInstanceName().intern()) {
                    WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
                    VrfTablesKey vrfTablesKey = new VrfTablesKey(rd);
                    VrfEntry vrfEntry = getVrfEntry(dataBroker, rd, destPrefix);
                    if (vrfEntry == null) {
                        return futures;
                    }
                    LOG.trace("manageRemoteRouteOnDPN :: action {}, DpnId {}, vpnId {}, rd {}, destPfx {}",
                            action, localDpnId, vpnId, rd, destPrefix);
                    List<RoutePaths> routePathList = vrfEntry.getRoutePaths();
                    VrfEntry modVrfEntry;
                    if (routePathList == null || (routePathList.isEmpty())) {
                        modVrfEntry = FibHelper.getVrfEntryBuilder(vrfEntry, label,
                                Collections.singletonList(destTepIp),
                                RouteOrigin.value(vrfEntry.getOrigin()), null /* parentVpnRd */).build();
                    } else {
                        modVrfEntry = vrfEntry;
                    }

                    if (action == true) {
                        LOG.trace("manageRemoteRouteOnDPN updated(add)  vrfEntry :: {}", modVrfEntry);
                        createRemoteFibEntry(localDpnId, vpnId, vrfTablesKey.getRouteDistinguisher(),
                                modVrfEntry, writeTransaction);
                    } else {
                        LOG.trace("manageRemoteRouteOnDPN updated(remove)  vrfEntry :: {}", modVrfEntry);
                        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnInstance.getVpnId(),
                                vrfEntry.getDestPrefix());
                        if (usedRds.size() > 1) {
                            LOG.debug("The extra route prefix is still present in some DPNs");
                            return futures;
                        }
                        //Is this fib route an extra route? If yes, get the nexthop which would be
                        //an adjacency in the vpn
                        Optional<Routes> extraRouteOptional = Optional.absent();
                        if (usedRds.size() != 0) {
                            extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                                    FibUtil.getVpnNameFromId(dataBroker, vpnInstance.getVpnId()),
                                    usedRds.get(0), vrfEntry.getDestPrefix());
                        }
                        deleteRemoteRoute(null, localDpnId, vpnId, vrfTablesKey, modVrfEntry,
                                extraRouteOptional, writeTransaction);
                    }
                    futures.add(writeTransaction.submit());
                }
                return futures;
            });
    }


}
