/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
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
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FibDSWriter {
    private static final Logger LOG = LoggerFactory.getLogger(FibDSWriter.class);
    private final SingleTransactionDataBroker singleTxDB;
    private final BgpUtil bgpUtil;
    private final Map<String, ArrayList<String>> fibMap = new HashMap<>();

    @Inject
    public FibDSWriter(final DataBroker dataBroker, final BgpUtil bgpUtil) {
        this.bgpUtil = bgpUtil;
        this.singleTxDB = new SingleTransactionDataBroker(dataBroker);
    }

    public synchronized void clearFibMap() {
        fibMap.clear();
    }

    public synchronized void addEntryToFibMap(String rd, String prefix, String nextHop) {
        ArrayList<String> temp = new ArrayList<>();
        if (fibMap.get(appendrdtoprefix(rd, prefix)) != null) {
            temp.addAll(fibMap.get(appendrdtoprefix(rd, prefix)));
        }
        temp.add(nextHop);
        fibMap.put(appendrdtoprefix(rd, prefix),temp);
        LOG.debug("addEntryToFibMap rd {} prefix {} nexthop {}",
                rd, prefix, nextHop);

    }

    public synchronized void addFibEntryToDS(String rd, String prefix, List<String> nextHopList,
                                             VrfEntry.EncapType encapType, Uint32 label, Uint32 l3vni,
                                             String gatewayMacAddress, RouteOrigin origin) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        requireNonNull(nextHopList, "NextHopList can't be null");
        for (String nextHop : nextHopList) {
            if (nextHop == null || nextHop.isEmpty()) {
                LOG.error("nextHop list contains null element");
                return;
            }
            LOG.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nextHop, label);
        }

        LOG.debug("addFibEntryToDS rd {} prefix {} NH {}",
                rd, prefix, nextHopList.get(0));

        ArrayList<String> temp = new ArrayList<>();
        if (fibMap.get(appendrdtoprefix(rd, prefix)) != null) {
            temp.addAll(fibMap.get(appendrdtoprefix(rd, prefix)));
        }
        if (!temp.contains(nextHopList.get(0))) {
            temp.addAll(nextHopList);
            fibMap.put(appendrdtoprefix(rd, prefix), temp);
        }

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

        VrfEntryBuilder vrfEntryBuilder = new VrfEntryBuilder().setDestPrefix(prefix).setOrigin(origin.getValue());
        buildVpnEncapSpecificInfo(vrfEntryBuilder, encapType, label, l3vni,
                gatewayMacAddress, nextHopList);
        bgpUtil.update(vrfEntryId, vrfEntryBuilder.build());
    }

    private String appendrdtoprefix(String rd, String prefix) {
        return rd + "/" + prefix;
    }

    public void addMacEntryToDS(String rd, String macAddress, String prefix,
                                List<String> nextHopList, VrfEntry.EncapType encapType,
                                Uint32 l2vni, String gatewayMacAddress, RouteOrigin origin) {
        if (StringUtils.isEmpty(rd)) {
            LOG.error("Mac {} not associated with vpn", macAddress);
            return;
        }

        requireNonNull(nextHopList, "NextHopList can't be null");
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
        bgpUtil.update(macEntryId, macEntryBuilder.build());
    }

    private static void buildVpnEncapSpecificInfo(VrfEntryBuilder builder,
            VrfEntry.EncapType encapType, Uint32 label, Uint32 l3vni,
            String gatewayMac, List<String> nextHopList) {
        if (!encapType.equals(VrfEntry.EncapType.Mplsgre)) {
            builder.setL3vni(l3vni);
        }
        builder.setEncapType(encapType);
        builder.setGatewayMacAddress(gatewayMac);
        Uint32 lbl = encapType.equals(VrfEntry.EncapType.Mplsgre) ? label : null;
        List<RoutePaths> routePaths = nextHopList.stream()
                        .filter(StringUtils::isNotEmpty)
                        .map(nextHop -> FibHelper.buildRoutePath(nextHop, lbl)).collect(Collectors.toList());
        builder.setRoutePaths(routePaths);
    }

    private static void buildVpnEncapSpecificInfo(MacVrfEntryBuilder builder,
                                                  VrfEntry.EncapType encapType, Uint32 l2vni,
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
        bgpUtil.delete(vrfEntryId);

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
        bgpUtil.delete(macEntryId);

    }

    public synchronized void removeOrUpdateFibEntryFromDS(String rd, String prefix, String nextHop) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {} and nextHop {}",
                prefix, rd, nextHop);


        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

        LOG.debug("removeOrUpdateFibEntryFromDS rd {} prefix {} NH {}",
                rd, prefix, nextHop);

        if (fibMap.get(appendrdtoprefix(rd,prefix)) != null) { //Key is there
            // If nexthop is there, delete it from List
            List<String> list = fibMap.get(appendrdtoprefix(rd,prefix));
            list.remove(nextHop);
            if (list.isEmpty()) {
                fibMap.remove(appendrdtoprefix(rd, prefix));
                bgpUtil.delete(vrfEntryId);
            } else {
                InstanceIdentifier<RoutePaths> routePathId =
                        FibHelper.buildRoutePathId(rd, prefix, nextHop);
                bgpUtil.delete(routePathId);
            }
        } else {
            LOG.error("Invalid Delete from Quagga, RD {} Prefix {} Nexthop {}  ",
                    rd,prefix,nextHop);
        }
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
                Map<VrfEntryKey, VrfEntry> keyVrfEntryMap = vrfTable.getVrfEntry();
                if (keyVrfEntryMap == null) {
                    LOG.error("removeVrfSubFamilyFromDS : VrfEntry not found for rd {}", rd);
                    return;
                }
                for (VrfEntry vrfEntry : keyVrfEntryMap.values()) {
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
            LOG.error("removeVrfSubFamilyFromDS : Internal Error rd {}", rd, rfe);
        }
        return;
    }

    public synchronized void removeVrfFromDS(String rd) {
        LOG.debug("Removing vrf table for  rd {}", rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        bgpUtil.delete(vrfTableId);

    }
}
