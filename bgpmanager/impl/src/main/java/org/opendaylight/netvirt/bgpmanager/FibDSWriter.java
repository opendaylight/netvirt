/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase.EncapType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FibDSWriter {
    private static final Logger LOG = LoggerFactory.getLogger(FibDSWriter.class);
    private final SingleTransactionDataBroker singleTxDB;
    private final ManagedNewTransactionRunner txRunner;
    private final BgpUtil bgpUtil;

    @Inject
    public FibDSWriter(final DataBroker dataBroker, final BgpUtil bgpUtil) {
        this.bgpUtil = bgpUtil;
        this.singleTxDB = new SingleTransactionDataBroker(dataBroker);
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    public synchronized void addFibEntryToDS(String rd, String prefix, List<String> nextHopList,
            VrfEntry.EncapType encapType, int label, long l3vni,
            String gatewayMacAddress, RouteOrigin origin) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        Preconditions.checkNotNull(nextHopList, "NextHopList can't be null");
        for (String nextHop : nextHopList) {
            if (nextHop == null || nextHop.isEmpty()) {
                LOG.error("nextHop list contains null element");
                return;
            }
            LOG.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nextHop, label);
        }

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

        VrfEntryBuilder vrfEntryBuilder = new VrfEntryBuilder().setDestPrefix(prefix).setOrigin(origin.getValue());
        buildVpnEncapSpecificInfo(vrfEntryBuilder, encapType, label, l3vni,
                gatewayMacAddress, nextHopList);
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntryBuilder.build())),
            LOG, "Error adding FIB entry to DS");
    }

    public void addMacEntryToDS(String rd, String macAddress, String prefix,
                                List<String> nextHopList, VrfEntry.EncapType encapType,
                                long l2vni, String gatewayMacAddress, RouteOrigin origin) {
        if (StringUtils.isEmpty(rd)) {
            LOG.error("Mac {} not associated with vpn", macAddress);
            return;
        }

        Preconditions.checkNotNull(nextHopList, "NextHopList can't be null");
        for (String nextHop : nextHopList) {
            if (StringUtils.isEmpty(nextHop)) {
                LOG.error("nextHop list contains null element for macVrf");
                return;
            }
        }

        MacVrfEntryBuilder macEntryBuilder = new MacVrfEntryBuilder().setOrigin(origin.getValue());
        buildVpnEncapSpecificInfo(macEntryBuilder, encapType, l2vni,
                gatewayMacAddress, nextHopList);
        macEntryBuilder.setMac(macAddress);
        macEntryBuilder.setDestPrefix(prefix);
        InstanceIdentifier<MacVrfEntry> macEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(macAddress)).build();
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.merge(LogicalDatastoreType.CONFIGURATION, macEntryId, macEntryBuilder.build())),
            LOG, "Error adding MAC entry to DS");
    }

    private static void buildVpnEncapSpecificInfo(VrfEntryBuilder builder,
            VrfEntry.EncapType encapType, long label, long l3vni,
            String gatewayMac, List<String> nextHopList) {
        if (!encapType.equals(VrfEntry.EncapType.Mplsgre)) {
            builder.setL3vni(l3vni);
        }
        builder.setEncapType(encapType);
        builder.setGatewayMacAddress(gatewayMac);
        Long lbl = encapType.equals(VrfEntry.EncapType.Mplsgre) ? label : null;
        List<RoutePaths> routePaths = nextHopList.stream()
                        .filter(StringUtils::isNotEmpty)
                        .map(nextHop -> FibHelper.buildRoutePath(nextHop, lbl)).collect(Collectors.toList());
        builder.setRoutePaths(routePaths);
    }

    private static void buildVpnEncapSpecificInfo(MacVrfEntryBuilder builder,
                                                  VrfEntry.EncapType encapType, long l2vni,
                                                  String gatewayMac, List<String> nextHopList) {
        builder.setEncapType(encapType);
        builder.setGatewayMacAddress(gatewayMac);
        builder.setL2vni(l2vni);
        List<RoutePaths> routePaths = nextHopList.stream()
                .filter(StringUtils::isNotEmpty)
                .map(nextHop -> FibHelper.buildRoutePath(nextHop, null)).collect(Collectors.toList());
        builder.setRoutePaths(routePaths);
    }

    public synchronized void removeFibEntryFromDS(String rd, String prefix) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        InstanceIdentifierBuilder<VrfEntry> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(
                        VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, vrfEntryId)),
            LOG, "Error removing FIB entry from DS");
    }

    public void removeMacEntryFromDS(String rd, String macAddress) {

        if (StringUtils.isEmpty(rd)) {
            LOG.error("Mac {} not associated with vpn", macAddress);
            return;
        }
        LOG.debug("Removing Mac fib entry with Mac {} from vrf table for rd {}", macAddress, rd);

        InstanceIdentifierBuilder<MacVrfEntry> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(
                        MacVrfEntry.class, new MacVrfEntryKey(macAddress));
        InstanceIdentifier<MacVrfEntry> macEntryId = idBuilder.build();
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, macEntryId)),
            LOG, "Error removing MAC entry from DS");
    }

    public synchronized void removeOrUpdateFibEntryFromDS(String rd, String prefix, String nextHop) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {} and nextHop {}",
                prefix, rd, nextHop);
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                    InstanceIdentifier.builder(FibEntries.class)
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
            Optional<VrfEntry> existingVrfEntry =
                    tx.read(LogicalDatastoreType.CONFIGURATION, vrfEntryId).checkedGet();
            List<RoutePaths> routePaths =
                    existingVrfEntry.toJavaUtil().map(VrfEntry::getRoutePaths).orElse(Collections.emptyList());
            if (routePaths.size() == 1) {
                if (routePaths.get(0).getNexthopAddress().equals(nextHop)) {
                    tx.delete(LogicalDatastoreType.CONFIGURATION, vrfEntryId);
                }
            } else {
                routePaths.stream()
                    .map(RoutePaths::getNexthopAddress)
                    .filter(nextHopAddress -> nextHopAddress.equals(nextHop))
                    .findFirst()
                    .ifPresent(nh -> {
                        InstanceIdentifier<RoutePaths> routePathId =
                                FibHelper.buildRoutePathId(rd, prefix, nextHop);
                        tx.delete(LogicalDatastoreType.CONFIGURATION, routePathId);
                    });
            }
        }), LOG, "Error removing or updating FIB entries from DS");
    }


    public synchronized void removeVrfSubFamilyFromDS(String rd, AddressFamily addressFamily) {

        if (rd == null) {
            return;
        }
        LOG.debug("removeVrfSubFamilyFromDS : addressFamily {} from vrf rd {}",
                  addressFamily, rd);

        InstanceIdentifier<VrfTables> id = InstanceIdentifier.create(FibEntries.class)
            .child(VrfTables.class, new VrfTablesKey(rd));
        try {
            VrfTables vrfTable = singleTxDB.syncRead(LogicalDatastoreType.CONFIGURATION, id);
            if (vrfTable != null) {
                List<VrfEntry> vrfEntries = vrfTable.getVrfEntry();
                if (vrfEntries == null) {
                    String errMsg = "removeVrfSubFamilyFromDS : VrfEntry not found for rd " + rd;
                    LOG.error(errMsg);
                    return;
                }
                for (VrfEntry vrfEntry : vrfEntries) {
                    boolean found = false;
                    if (vrfEntry.getEncapType() != null) {
                        if (!vrfEntry.getEncapType().equals(EncapType.Mplsgre)
                             && addressFamily == AddressFamily.L2VPN) {
                            found = true;
                        } else if (vrfEntry.getEncapType().equals(EncapType.Mplsgre)) {
                            if (addressFamily == AddressFamily.IPV4
                                && FibHelper.isIpv4Prefix(vrfEntry.getDestPrefix())) {
                                found = true;
                            } else if (addressFamily == AddressFamily.IPV6
                                       && FibHelper.isIpv6Prefix(vrfEntry.getDestPrefix())) {
                                found = true;
                            }
                        }
                    }
                    if (found == false) {
                        continue;
                    }
                    bgpUtil.removeVrfEntry(rd, vrfEntry);
                }
            }
        } catch (ReadFailedException rfe) {
            String errMsg = "removeVrfSubFamilyFromDS : Internal Error rd " + rd;
            LOG.error(errMsg, rfe);
        }
        return;
    }

    public synchronized void removeVrfFromDS(String rd) {
        LOG.debug("Removing vrf table for  rd {}", rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.delete(LogicalDatastoreType.CONFIGURATION, vrfTableId)),
            LOG, "Error removing VRF from DS");
    }
}
