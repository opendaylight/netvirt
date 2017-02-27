/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.impl;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnInterfaceManager;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnPopulator implements VpnPopulator {
    protected final VpnInterfaceManager vpnInterfaceManager;
    protected final IBgpManager bgpManager;
    protected final IFibManager fibManager;
    protected final DataBroker broker;
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnPopulator.class);

    protected L3vpnPopulator(DataBroker dataBroker, VpnInterfaceManager vpnInterfaceManager,
                             IBgpManager bgpManager, IFibManager fibManager) {
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.broker = dataBroker;
    }

    @Override
    public void populateFib(L3vpnInput input, WriteTransaction writeCfgTxn,
                            WriteTransaction writeOperTxn) {}

    @Override
    public void addSubnetRouteFibEntry(String rd, String vpnName, String prefix, String nextHop, int label,
                                       long elantag, BigInteger dpnId, WriteTransaction writeTxn, VrfEntry.EncapType encapType) {
        SubnetRoute route = new SubnetRouteBuilder().setElantag(elantag).build();
        RouteOrigin origin = RouteOrigin.CONNECTED; // Only case when a route is considered as directly connected
        VrfEntry vrfEntry =
                new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(Collections.singletonList(nextHop))
                        .setLabel((long) label).setOrigin(origin.getValue()).setEncapType(encapType)
                        .addAugmentation(SubnetRoute.class, route).build();

        LOG.debug("Created vrfEntry for {} nexthop {} label {} and elantag {}", prefix, nextHop, label, elantag);

        //TODO: What should be parentVpnId? Get it from RD?
        //long vpnId = VpnUtil.getVpnId(broker, vpnName);
        //addToLabelMapper((long) label, dpnId, prefix, Collections.singletonList(nextHop), vpnId, null, elantag, true,
        //      rd, null);
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

        if (!entry.isPresent()) {
            LOG.debug("*****L3vpnPopulator {}", entry);
            List<VrfEntry> vrfEntryList = Collections.singletonList(vrfEntry);

            InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
            InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

            VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).setVrfEntry(vrfEntryList).build();
            LOG.debug("*****L3vpnPopulator {}", writeTxn);

            if (writeTxn != null) {
                writeTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew, true);
            } else {
                VpnUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
            }
        } else { // Found in MDSAL database
            LOG.debug("*****L3vpnPopulator*****");
            if (writeTxn != null) {
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
            } else {
                VpnUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            }
            LOG.debug("Updated vrfEntry for {} nexthop {} label {}", prefix, nextHop, label);
        }
        LOG.debug("*****L3vpnPopulator*****subnetroute*****");
    }

    @Override
    public Adjacency createOperationalAdjacency(L3vpnInput input) {
        return new AdjacencyBuilder().build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void addPrefixToBGP(String rd, String primaryRd, String macAddress, String prefix, String nextHopIp,
                                  VrfEntry.EncapType encapType, long label, long l3vni, String gatewayMac,
                                  DataBroker broker, WriteTransaction writeConfigTxn) {
        try {
            List<String> nextHopList = Collections.singletonList(nextHopIp);
            LOG.info("ADD: Adding Fib entry rd {} prefix {} nextHop {} label {} l3vni {}", rd, prefix, nextHopIp,
                    label, l3vni);
            fibManager.addOrUpdateFibEntry(broker, primaryRd, macAddress, prefix, nextHopList,
                    encapType, (int)label, l3vni, gatewayMac, RouteOrigin.LOCAL, writeConfigTxn);
            LOG.info("ADD: Added Fib entry rd {} prefix {} nextHop {} label {}, l3vni {}", rd, prefix, nextHopIp,
                    label, l3vni);
            // Advertise the prefix to BGP only if nexthop ip is available
            if (nextHopList != null && !nextHopList.isEmpty()) {
                bgpManager.advertisePrefix(rd, macAddress, prefix, nextHopList, encapType, (int)label,
                        l3vni, gatewayMac);
            } else {
                LOG.warn("NextHopList is null/empty. Hence rd {} prefix {} is not advertised to BGP", rd, prefix);
            }
        } catch (Exception e) {
            LOG.error("Add prefix failed", e);
        }
    }
}
