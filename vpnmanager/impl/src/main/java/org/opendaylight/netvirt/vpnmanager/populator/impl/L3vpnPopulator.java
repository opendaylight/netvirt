/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;
import static org.opendaylight.infrautils.utils.concurrent.LoggingFutures.addErrorLogging;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class L3vpnPopulator implements VpnPopulator {
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnPopulator.class);

    protected final IBgpManager bgpManager;
    protected final IFibManager fibManager;
    protected final DataBroker broker;
    protected final ManagedNewTransactionRunner txRunner;
    protected final VpnUtil vpnUtil;

    protected L3vpnPopulator(DataBroker dataBroker, IBgpManager bgpManager, IFibManager fibManager,
                             VpnUtil vpnUtil) {
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnUtil = vpnUtil;
    }

    @Override
    public void populateFib(L3vpnInput input, TypedWriteTransaction<Configuration> writeCfgTxn) {

    }

    public void addSubnetRouteFibEntry(L3vpnInput input) {
        String rd = input.getRd();
        String vpnName = input.getVpnName();
        String prefix = input.getSubnetIp();
        String nextHop = input.getNextHopIp();
        Uint32 label = Uint32.valueOf(input.getLabel());
        Uint32 l3vni = Uint32.valueOf(input.getL3vni());
        Uint32 elantag = Uint32.valueOf(input.getElanTag());
        String networkName = input.getNetworkName();
        String gwMacAddress = input.getGatewayMac();
        SubnetRoute route = new SubnetRouteBuilder().setElantag(elantag).build();
        RouteOrigin origin = RouteOrigin.CONNECTED; // Only case when a route is considered as directly connected
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder(prefix, label, nextHop, origin, networkName)
                .addAugmentation(SubnetRoute.class, route).setL3vni(l3vni).setGatewayMacAddress(gwMacAddress).build();
        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label, elantag);
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = Optional.empty();
        try {
            entry = SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("addSubnetRouteFibEntry: Exception while reading vrfEntry for the prefix {} rd {}", prefix,
                    rd, e);
        }
        if (!entry.isPresent()) {
            List<VrfEntry> vrfEntryList = Collections.singletonList(vrfEntry);

            InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
            InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

            VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).setVrfEntry(vrfEntryList).build();
            vpnUtil.syncUpdate(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
            LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Added vrfEntry for {} nexthop {} label {} rd {}"
                    + " vpnName {}", prefix, nextHop, label, rd, vpnName);
        } else { // Found in MDSAL database
            vpnUtil.syncWrite(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Updated vrfEntry for {} nexthop {} label {} rd {}"
                    + " vpnName {}", prefix, nextHop, label, rd, vpnName);
        }

        //Will be handled appropriately with the iRT patch for EVPN
        if (input.getEncapType().equals(VrfEntryBase.EncapType.Mplsgre)) {
            List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnUtil.getVpnsImportingMyRoute(vpnName);
            if (vpnsToImportRoute.size() > 0) {
                VrfEntry importingVrfEntry = FibHelper.getVrfEntryBuilder(prefix, label, nextHop,
                        RouteOrigin.SELF_IMPORTED, rd).addAugmentation(SubnetRoute.class, route).build();
                List<VrfEntry> importingVrfEntryList = Collections.singletonList(importingVrfEntry);
                for (VpnInstanceOpDataEntry vpnInstance : vpnsToImportRoute) {
                    String importingRd = vpnInstance.getVrfId();
                    InstanceIdentifier<VrfTables> importingVrfTableId =
                            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class,
                                    new VrfTablesKey(importingRd)).build();
                    VrfTables importingVrfTable = new VrfTablesBuilder().setRouteDistinguisher(importingRd)
                            .setVrfEntry(importingVrfEntryList).build();
                    vpnUtil.syncUpdate(LogicalDatastoreType.CONFIGURATION, importingVrfTableId, importingVrfTable);
                    LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Exported route rd {} prefix {} nexthop {}"
                                    + " label {} to vpn {} importingRd {}", rd, prefix, nextHop, label,
                            vpnInstance.getVpnInstanceName(), importingRd);
                }
            }
        }
        LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Created vrfEntry for {} nexthop {} label {} and elantag {}"
                + "rd {} vpnName {}", prefix, nextHop, label, elantag, rd, vpnName);
    }

    public void addToLabelMapper(Uint32 label, Uint64 dpnId, String prefix, List<String> nextHopIpList, Uint32 vpnId,
                                 @Nullable String vpnInterfaceName, @Nullable Uint32 elanTag,
                                 boolean isSubnetRoute, String rd) {
        final String labelStr = Preconditions.checkNotNull(label, "addToLabelMapper: label cannot be null or empty!")
                .toString();
        Preconditions.checkNotNull(prefix, "addToLabelMapper: prefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnId, "addToLabelMapper: vpnId cannot be null or empty!");
        Preconditions.checkNotNull(rd, "addToLabelMapper: rd cannot be null or empty!");
        if (!isSubnetRoute) {
            // NextHop must be present for non-subnetroute entries
            Preconditions.checkNotNull(nextHopIpList, "addToLabelMapper: nextHopIp cannot be null or empty!");
        }

        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(labelStr);
        lock.lock();
        try {
            addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
                LOG.info("addToLabelMapper: label {} dpn {} prefix {} nexthoplist {} vpnid {} vpnIntfcName {} rd {}"
                        + " elanTag {}", labelStr, dpnId, prefix, nextHopIpList, vpnId, vpnInterfaceName, rd, elanTag);
                if (dpnId != null) {
                    LabelRouteInfoBuilder lriBuilder = new LabelRouteInfoBuilder();
                    lriBuilder.setLabel(label).setDpnId(dpnId).setPrefix(prefix).setNextHopIpList(nextHopIpList)
                            .setParentVpnid(vpnId).setIsSubnetRoute(isSubnetRoute);
                    if (elanTag != null) {
                        lriBuilder.setElanTag(elanTag);
                    } else {
                        LOG.warn("addToLabelMapper: elanTag is null for label {} prefix {} rd {} vpnId {}",
                                labelStr, prefix, rd, vpnId);
                    }
                    if (vpnInterfaceName != null) {
                        lriBuilder.setVpnInterfaceName(vpnInterfaceName);
                    } else {
                        LOG.warn("addToLabelMapper: vpn interface is null for label {} prefix {} rd {} vpnId {}",
                                labelStr, prefix, rd, vpnId);
                    }
                    lriBuilder.setParentVpnRd(rd);
                    VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnUtil.getVpnInstanceOpData(rd);
                    if (vpnInstanceOpDataEntry != null) {
                        List<String> vpnInstanceNames = Collections
                                .singletonList(vpnInstanceOpDataEntry.getVpnInstanceName());
                        lriBuilder.setVpnInstanceList(vpnInstanceNames);
                    }
                    LabelRouteInfo lri = lriBuilder.build();
                    InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
                            .child(LabelRouteInfo.class, new LabelRouteInfoKey(label)).build();
                    tx.mergeParentStructureMerge(lriIid, lri);
                    LOG.info("addToLabelMapper: Added label route info to label {} prefix {} nextHopList {} vpnId {}"
                                    + " interface {} rd {} elantag {}", labelStr, prefix, nextHopIpList, vpnId,
                            vpnInterfaceName, rd, elanTag);
                } else {
                    LOG.warn("addToLabelMapper: Can't add entry to label map for label {} prefix {} nextHopList {}"
                                    + " vpnId {} interface {} rd {} elantag {}, dpnId is null",
                            labelStr, prefix, nextHopIpList,
                            vpnId, vpnInterfaceName, rd, elanTag);
                }
            }), LOG, "addToLabelMapper");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        return new AdjacencyBuilder().build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void addPrefixToBGP(String rd, String primaryRd, @Nullable String macAddress, String prefix,
                                  String nextHopIp, VrfEntry.EncapType encapType, Uint32 label, Uint32 l3vni,
                                  String gatewayMac, RouteOrigin origin,
                                  TypedWriteTransaction<Configuration> writeConfigTxn) {
        try {
            List<String> nextHopList = Collections.singletonList(nextHopIp);
            LOG.info("ADD: addPrefixToBGP: Adding Fib entry rd {} prefix {} nextHop {} label {} gwMac {}", rd, prefix,
                    nextHopList, label, gatewayMac);
            fibManager.addOrUpdateFibEntry(primaryRd, macAddress, prefix, nextHopList,
                    encapType, label, l3vni, gatewayMac, null /*parentVpnRd*/, origin, writeConfigTxn);
            LOG.info("ADD: addPrefixToBGP: Added Fib entry rd {} prefix {} nextHop {} label {} gwMac {}", rd, prefix,
                    nextHopList, label, gatewayMac);
            // Advertise the prefix to BGP only if nexthop ip is available
            if (!nextHopList.isEmpty()) {
                bgpManager.advertisePrefix(rd, macAddress, prefix, nextHopList, encapType, label,
                        l3vni, Uint32.ZERO /*l2vni*/, gatewayMac);
            } else {
                LOG.error("addPrefixToBGP: NextHopList is null/empty. Hence rd {} prefix {} nextHop {} label {}"
                        + " gwMac {} is not advertised to BGP", rd, prefix, nextHopList, label, gatewayMac);
            }
        } catch (Exception e) {
            LOG.error("addPrefixToBGP: Add prefix {} with rd {} nextHop {} label {} gwMac {} failed", prefix, rd,
                    nextHopIp, label, gatewayMac, e);
        }
    }
}
