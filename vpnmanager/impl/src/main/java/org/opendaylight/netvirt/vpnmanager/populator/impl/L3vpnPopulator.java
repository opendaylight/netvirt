/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import com.google.common.base.Optional;

import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class L3vpnPopulator implements VpnPopulator {
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnPopulator.class);

    protected final IBgpManager bgpManager;
    protected final IFibManager fibManager;
    protected final DataBroker broker;
    protected final ManagedNewTransactionRunner txRunner;

    protected L3vpnPopulator(DataBroker dataBroker, IBgpManager bgpManager, IFibManager fibManager) {
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public void populateFib(L3vpnInput input, WriteTransaction writeCfgTxn,
                            WriteTransaction writeOperTxn) {}

    public void addSubnetRouteFibEntry(L3vpnInput input) {
        String rd = input.getRd();
        String prefix = input.getSubnetIp();
        String nextHop = input.getNextHopIp();
        long label = input.getLabel();
        long l3vni = input.getL3vni();
        long elantag = input.getElanTag();
        String vpnName = input.getVpnName();
        long vpnId = VpnUtil.getVpnId(broker, vpnName);
        String gwMacAddress = input.getGatewayMac();
        SubnetRoute route = new SubnetRouteBuilder().setElantag(elantag).build();
        RouteOrigin origin = RouteOrigin.CONNECTED; // Only case when a route is considered as directly connected
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder(prefix, label, nextHop, origin, rd)
                .addAugmentation(SubnetRoute.class, route).setL3vni(l3vni).setGatewayMacAddress(gwMacAddress).build();
        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label, elantag);
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

        if (!entry.isPresent()) {
            List<VrfEntry> vrfEntryList = Collections.singletonList(vrfEntry);

            InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
            InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

            VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).setVrfEntry(vrfEntryList).build();
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
            LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Added vrfEntry for {} nexthop {} label {} rd {}"
                    + " vpnName {}", prefix, nextHop, label, rd, vpnName);
        } else { // Found in MDSAL database
            VpnUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Updated vrfEntry for {} nexthop {} label {} rd {}"
                    + " vpnName {}", prefix, nextHop, label, rd, vpnName);
        }
        VpnUtil.addToIpPrefixInfo(broker, rd, prefix, vpnName, Collections.singletonList(nextHop),
                vpnId, true /*isSubnetRoute*/, input.getDpnId(), null/*interfaceName*/, null /*writeOpenTxn*/);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = VpnUtil.getVpnsImportingMyRoute(broker, vpnName);
        if (vpnsToImportRoute.size() > 0) {
            for (VpnInstanceOpDataEntry vpnInstance : vpnsToImportRoute) {
                String importingRd = vpnInstance.getVrfId();
                fibManager.addOrUpdateFibEntry(importingRd, null /*macAddress*/,
                        prefix, Collections.singletonList(nextHop), input.getEncapType(), (int) label,
                        input.getL3vni(), input.getGatewayMac(), rd, RouteOrigin.SELF_IMPORTED,
                        null/*WriteCfgTxn*/);
                LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Exported route rd {} prefix {} nexthop {}"
                                + " label {} to vpn {} importingRd {}", rd, prefix, nextHop, label,
                        vpnInstance.getVpnInstanceName(), importingRd);
            }
        }
        LOG.info("SUBNETROUTE: addSubnetRouteFibEntryToDS: Created vrfEntry for {} nexthop {} label {} and elantag {}"
                + "rd {} vpnName {}", prefix, nextHop, label, elantag, rd, vpnName);
    }

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        return new AdjacencyBuilder().build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void addPrefixToBGP(String rd, String primaryRd, String macAddress, String prefix, String nextHopIp,
                                  VrfEntry.EncapType encapType, long label, long l3vni, String gatewayMac,
                                  RouteOrigin origin, WriteTransaction writeConfigTxn) {
        try {
            List<String> nextHopList = Collections.singletonList(nextHopIp);
            LOG.info("ADD: addPrefixToBGP: Adding Fib entry rd {} prefix {} nextHop {} label {} gwMac {}", rd, prefix,
                    nextHopList, label, gatewayMac);
            fibManager.addOrUpdateFibEntry(primaryRd, macAddress, prefix, nextHopList,
                    encapType, (int)label, l3vni, gatewayMac, primaryRd, origin, writeConfigTxn);
            LOG.info("ADD: addPrefixToBGP: Added Fib entry rd {} prefix {} nextHop {} label {} gwMac {}", rd, prefix,
                    nextHopList, label, gatewayMac);
            // Advertise the prefix to BGP only if nexthop ip is available
            if (!nextHopList.isEmpty()) {
                bgpManager.advertisePrefix(rd, macAddress, prefix, nextHopList, encapType, (int)label,
                        l3vni, 0 /*l2vni*/, gatewayMac);
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
