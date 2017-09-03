/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.opendaylight.netvirt.fibmanager.VrfEntryListener.isOpenStackVniSemanticsEnforced;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.fibmanager.NexthopManager.AdjacencyResult;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.BucketId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.Buckets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.BucketsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.DpidL3vpnLbNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.L3vpnLbNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpid.l3vpn.lb.nexthops.DpnLbNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpid.l3vpn.lb.nexthops.DpnLbNexthopsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpid.l3vpn.lb.nexthops.DpnLbNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.l3vpn.lb.nexthops.Nexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.l3vpn.lb.nexthops.NexthopsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.l3vpn.lb.nexthops.NexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FibUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FibUtil.class);
    private static final String FLOWID_PREFIX = "L3.";

    private FibUtil() {
    }

    static InstanceIdentifier<Adjacency> getAdjacencyIdentifier(String vpnInterfaceName, String ipAddress) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang
            .l3vpn.rev140815.VpnInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                .VpnInterface.class,
                new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                    .VpnInterfaceKey(vpnInterfaceName))
            .augmentation(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list
                    .AdjacencyKey(ipAddress)).build();
    }

    static InstanceIdentifier<Adjacencies> getAdjListPath(String vpnInterfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn
            .rev140815.VpnInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                .VpnInterface.class,
                new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                    .VpnInterfaceKey(vpnInterfaceName))
            .augmentation(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies.class)
            .build();
    }

    static InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(long vpnId, String ipPrefix) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn
            .rev130911.PrefixToInterface.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                .VpnIds.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                    .VpnIdsKey(vpnId))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                    .vpn.ids.Prefixes.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to
                    ._interface.vpn.ids.PrefixesKey(ipPrefix)).build();
    }

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn
            .rev140815.VpnInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                .VpnInterface.class,
                new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                    .VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    static Optional<VpnInstanceOpDataEntry> getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnInstanceOpDataIdentifier(rd);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
    }

    static VpnInstanceOpDataEntry getVpnInstance(DataBroker dataBroker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id =
                InstanceIdentifier.create(VpnInstanceOpData.class)
                        .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd));
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        return vpnInstanceOpData.isPresent() ? vpnInstanceOpData.get() : null;
    }

    static String getNextHopLabelKey(String rd, String prefix) {
        return rd + FibConstants.SEPARATOR + prefix;
    }

    static Prefixes getPrefixToInterface(DataBroker broker, Long vpnId, String ipPrefix) {
        Optional<Prefixes> localNextHopInfoData = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
            getPrefixToInterfaceIdentifier(vpnId, ipPrefix));
        return localNextHopInfoData.isPresent() ? localNextHopInfoData.get() : null;
    }

    static String getMacAddressFromPrefix(DataBroker broker, String ifName, String ipPrefix) {
        Optional<Adjacency> adjacencyData = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
            getAdjacencyIdentifier(ifName, ipPrefix));
        return adjacencyData.isPresent() ? adjacencyData.get().getMacAddress() : null;
    }

    static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get Unique Id for key {} returned with Errors {}", idKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id for key {}", idKey, e);
        }
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                    .VpnInstance.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                    .VpnInstanceKey(vpnName)).build();
    }

    public static long getVpnId(DataBroker broker, String vpnName) {

        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                VpnInstance::getVpnId).orElse(-1L);
    }

    /**
     * Retrieves the VpnInstance name (typically the VPN Uuid) out from the route-distinguisher.
     *
     * @param broker The DataBroker
     * @param rd The route-distinguisher
     * @return The vpn instance
     */
    public static Optional<String> getVpnNameFromRd(DataBroker broker, String rd) {
        return Optional.fromJavaUtil(
                getVpnInstanceOpData(broker, rd).toJavaUtil().map(VpnInstanceOpDataEntry::getVpnInstanceName));
    }

    public static String getVpnNameFromId(DataBroker broker, long vpnId) {
        InstanceIdentifier<VpnIds> id = getVpnIdToVpnInstanceIdentifier(vpnId);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                VpnIds::getVpnInstanceName).orElse(null);
    }

    static InstanceIdentifier<VpnIds> getVpnIdToVpnInstanceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(VpnIdToVpnInstance.class)
            .child(VpnIds.class, new VpnIdsKey(vpnId)).build();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addOrUpdateFibEntry(DataBroker broker, String rd, String macAddress, String prefix,
                                           List<String> nextHopList, VrfEntry.EncapType encapType, long label,
                                           long l3vni, String gwMacAddress, String parentVpnRd, RouteOrigin origin,
                                           WriteTransaction writeConfigTxn) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        Preconditions.checkNotNull(nextHopList, "NextHopList can't be null");

        try {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

            writeFibEntryToDs(vrfEntryId, prefix, nextHopList, label, l3vni, encapType, origin, macAddress,
                    gwMacAddress, parentVpnRd, writeConfigTxn, broker);
            LOG.debug("Created/Updated vrfEntry for {} nexthop {} label {}", prefix, nextHopList, label);
        } catch (Exception e) {
            LOG.error("addOrUpdateFibEntry: Prefix {} rd {} label {} error ", prefix, rd, label, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void writeFibEntryToDs(InstanceIdentifier<VrfEntry> vrfEntryId, String prefix,
                                         List<String> nextHopList, long label, Long l3vni,
                                         VrfEntry.EncapType encapType, RouteOrigin origin, String macAddress,
                                         String gatewayMacAddress, String parentVpnRd,
                                         WriteTransaction writeConfigTxn, DataBroker broker) {
        VrfEntryBuilder vrfEntryBuilder = new VrfEntryBuilder().setDestPrefix(prefix).setOrigin(origin.getValue());
        if (parentVpnRd != null) {
            vrfEntryBuilder.setParentVpnRd(parentVpnRd);
        }
        buildVpnEncapSpecificInfo(vrfEntryBuilder, encapType, label, l3vni, macAddress, gatewayMacAddress, nextHopList);
        if (writeConfigTxn != null) {
            writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntryBuilder.build(), true);
        } else {
            MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntryBuilder.build());
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addFibEntryForRouterInterface(DataBroker broker,
                                                     String rd,
                                                     String prefix,
                                                     RouterInterface routerInterface,
                                                     long label,
                                                     WriteTransaction writeConfigTxn) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        try {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

            // Filling the nextHop with dummy nextHopAddress
            VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder(prefix, label,
                    FibConstants.DEFAULT_NEXTHOP_IP, RouteOrigin.LOCAL, null /* parentVpnRd */)
                .addAugmentation(RouterInterface.class, routerInterface).build();

            if (writeConfigTxn != null) {
                writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
            } else {
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            }
            LOG.debug("Created vrfEntry for router-interface-prefix {} rd {} label {}", prefix, rd, label);
        } catch (Exception e) {
            LOG.error("addFibEntryForRouterInterface: prefix {} rd {} label {} error ", prefix, rd, label, e);
        }
    }

    private static void buildVpnEncapSpecificInfo(VrfEntryBuilder builder, VrfEntry.EncapType encapType, long label,
                                         long l3vni, String macAddress, String gatewayMac, List<String> nextHopList) {
        if (encapType == null) {
            builder.setMac(macAddress);
            return;
        }

        // TODO - validate this check
        if (l3vni != 0) {
            builder.setL3vni(l3vni);
        }
        builder.setEncapType(encapType);
        builder.setGatewayMacAddress(gatewayMac);
        builder.setMac(macAddress);
        Long lbl = encapType.equals(VrfEntry.EncapType.Mplsgre) ? label : null;
        List<RoutePaths> routePaths = nextHopList.stream()
                        .filter(nextHop -> nextHop != null && !nextHop.isEmpty())
                        .map(nextHop -> FibHelper.buildRoutePath(nextHop, lbl)).collect(toList());
        builder.setRoutePaths(routePaths);
    }

    public static void removeFibEntry(DataBroker broker, String rd, String prefix, WriteTransaction writeConfigTxn) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        InstanceIdentifier.InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(rd)).child(VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        if (writeConfigTxn != null) {
            writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        } else {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        }
    }

    /**
     * Removes a specific Nexthop from a VrfEntry. If Nexthop to remove is the
     * last one in the VrfEntry, then the VrfEntry is removed too.
     *
     * @param broker          dataBroker service reference
     * @param rd              Route-Distinguisher to which the VrfEntry belongs to
     * @param prefix          Destination of the route
     * @param nextHopToRemove Specific nexthop within the Route to be removed.
     *                        If null or empty, then the whole VrfEntry is removed
     */
    public static void removeOrUpdateFibEntry(DataBroker broker, String rd, String prefix, String nextHopToRemove,
                                              WriteTransaction writeConfigTxn) {

        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        if (entry.isPresent()) {
            final List<RoutePaths> routePaths = entry.get().getRoutePaths();
            if (routePaths == null || routePaths.isEmpty()) {
                LOG.warn("routePaths is null/empty for given rd {}, prefix {}", rd, prefix);
                return;
            }
            java.util.Optional<RoutePaths> optRoutePath =
                    routePaths.stream()
                              .filter(routePath -> routePath.getNexthopAddress().equals(
                                    nextHopToRemove)).findFirst();
            if (!optRoutePath.isPresent()) {
                LOG.error("Unable to find a routePath that contains the given nextHop to remove {}", nextHopToRemove);
                return;
            }
            RoutePaths routePath = optRoutePath.get();
            if (routePaths.size() == 1) {
                // Remove the whole entry
                if (writeConfigTxn != null) {
                    writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, vrfEntryId);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
                }
                LOG.info("Removed Fib Entry rd {} prefix {}", rd, prefix);
            } else {
                InstanceIdentifier<RoutePaths> routePathsId =
                        FibHelper.buildRoutePathId(rd, prefix, routePath.getNexthopAddress());
                // Remove route
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routePathsId);

                LOG.info("Removed Route Path rd {} prefix {}, nextHop {}, label {}", rd, prefix,
                        routePath.getNexthopAddress(), routePath.getLabel());
            }
        } else {
            LOG.warn("Could not find VrfEntry for Route-Distinguisher={} and prefix={}", rd, prefix);
        }
    }

    /**
     * Adds or removes nextHop from routePath based on the flag nextHopAdd.
     */
    public static void updateRoutePathForFibEntry(DataBroker broker, String rd, String prefix, String nextHop,
            long label, boolean nextHopAdd, WriteTransaction writeConfigTxn) {

        LOG.debug("Updating fib entry for prefix {} with nextHop {} for rd {}.", prefix, nextHop, rd);

        InstanceIdentifier<RoutePaths> routePathId = FibHelper.buildRoutePathId(rd, prefix, nextHop);
        String routePathKey = rd + prefix + nextHop;
        synchronized (routePathKey.intern()) {
            if (nextHopAdd) {
                RoutePaths routePaths = FibHelper.buildRoutePath(nextHop, label);
                if (writeConfigTxn != null) {
                    writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, routePathId, routePaths,
                            WriteTransaction.CREATE_MISSING_PARENTS);
                } else {
                    MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, routePathId, routePaths);
                }
                LOG.debug("Added routepath with nextHop {} for prefix {} and label {}.", nextHop, prefix, label);
            } else {
                Optional<RoutePaths> routePath = MDSALUtil.read(broker,
                        LogicalDatastoreType.CONFIGURATION, routePathId);
                if (!routePath.isPresent()) {
                    LOG.warn("Couldn't find RoutePath with rd {}, prefix {} and nh {} for deleting",
                            rd, prefix, nextHop);
                    return;
                }
                if (writeConfigTxn != null) {
                    writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, routePathId);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routePathId);
                }
                LOG.info("Removed routepath with nextHop {} for prefix {} and rd {}.", nextHop, prefix, rd);
            }
        }
    }

    public static void removeVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
        LOG.debug("Removing vrf table for rd {}", rd);
        InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        if (writeConfigTxn != null) {
            writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, vrfTableId);
        } else {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId);
        }
    }

    public static java.util.Optional<Long> getLabelFromRoutePaths(final VrfEntry vrfEntry) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty() || vrfEntry.getRoutePaths().get(0).getLabel() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(vrfEntry.getRoutePaths().get(0).getLabel());
    }

    public static java.util.Optional<String> getFirstNextHopAddress(final VrfEntry vrfEntry) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(vrfEntry.getRoutePaths().get(0).getNexthopAddress());
    }

    public static java.util.Optional<Long> getLabelForNextHop(final VrfEntry vrfEntry, String nextHopIp) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty()) {
            return java.util.Optional.empty();
        }
        return routePaths.stream()
                .filter(routePath -> routePath.getNexthopAddress().equals(nextHopIp))
                .findFirst()
                .map(RoutePaths::getLabel);
    }

    public static InstanceIdentifier<Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                        .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                                .interfaces.state.Interface.class, new org.opendaylight.yang.gen.v1.urn.ietf
                                .params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
                                .InterfaceKey(interfaceName));
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.state.Interface> id = idBuilder.build();
        return id;
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .state.Interface getInterfaceStateFromOperDS(DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> ifStateId = buildStateInterfaceId(interfaceName);
        Optional<Interface> ifStateOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, ifStateId);
        if (ifStateOptional.isPresent()) {
            return ifStateOptional.get();
        }

        return null;
    }

    public static String getCreateLocalNextHopJobKey(Long vpnId, BigInteger dpnId, String prefix) {
        return "FIB-" + vpnId.toString() + "-" + dpnId.toString() + "-" + prefix;
    }

    public static String getJobKeyForRdPrefix(String rd, String prefix) {
        return "FIB-" + rd + "-" + prefix;
    }

    public static String getJobKeyForVpnIdDpnId(Long vpnId, BigInteger dpnId) {
        return "FIB-" + vpnId.toString() + "-" + dpnId.toString() ;
    }

    public static void updateUsedRdAndVpnToExtraRoute(WriteTransaction writeOperTxn, DataBroker broker,
                                                      String tunnelIpRemoved, String primaryRd, String prefix) {
        Optional<VpnInstanceOpDataEntry> optVpnInstance = getVpnInstanceOpData(broker, primaryRd);
        if (!optVpnInstance.isPresent()) {
            return;
        }
        VpnInstanceOpDataEntry vpnInstance = optVpnInstance.get();
        String vpnName = vpnInstance.getVpnInstanceName();
        long vpnId = vpnInstance.getVpnId();
        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(broker, vpnId, prefix);
        // To identify the rd to be removed, iterate through the allocated rds for the prefix and check
        // which rd is allocated for the particular OVS.
        for (String usedRd : usedRds) {
            Optional<Routes> vpnExtraRoutes = VpnExtraRouteHelper
                    .getVpnExtraroutes(broker, vpnName, usedRd, prefix);
            if (vpnExtraRoutes.isPresent()) {
                // Since all the nexthops under one OVS will be present under one rd, only 1 nexthop is read
                // to identify the OVS
                String nextHopRemoved = vpnExtraRoutes.get().getNexthopIpList().get(0);
                Prefixes prefixToInterface = getPrefixToInterface(broker, vpnId, getIpPrefix(nextHopRemoved));
                if (prefixToInterface != null && tunnelIpRemoved
                        .equals(getEndpointIpAddressForDPN(broker, prefixToInterface.getDpnId()))) {
                    InstanceIdentifier<Adjacency> adjId = getAdjacencyIdentifier(
                            prefixToInterface.getVpnInterfaceName(), prefix);
                    Interface ifState = FibUtil.getInterfaceStateFromOperDS(
                            broker, prefixToInterface.getVpnInterfaceName());
                    //Delete datastore only if extra route is deleted or VM interface is deleted/down
                    if (!MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, adjId).isPresent()
                            || ifState == null || ifState.getOperStatus() == OperStatus.Down) {
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                                getAdjacencyIdentifier(prefixToInterface.getVpnInterfaceName(), prefix));
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                                VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, usedRd, prefix));
                        writeOperTxn.delete(LogicalDatastoreType.CONFIGURATION,
                                VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, prefix, nextHopRemoved));
                        break;
                    }
                }
            }
        }
    }

    private static String getEndpointIpAddressForDPN(DataBroker broker, BigInteger dpnId) {
        //TODO: Move it to a common place for vpn and fib
        String nextHopIp = null;
        InstanceIdentifier<DPNTEPsInfo> tunnelInfoId =
            InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class, new DPNTEPsInfoKey(dpnId)).build();
        Optional<DPNTEPsInfo> tunnelInfo = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, tunnelInfoId);
        if (tunnelInfo.isPresent()) {
            List<TunnelEndPoints> nexthopIpList = tunnelInfo.get().getTunnelEndPoints();
            if (nexthopIpList != null && !nexthopIpList.isEmpty()) {
                nextHopIp = String.valueOf(nexthopIpList.get(0).getIpAddress().getValue());
            }
        }
        return nextHopIp;
    }

    public static String getIpPrefix(String prefix) {
        String[] prefixValues = prefix.split(FibConstants.PREFIX_SEPARATOR);
        if (prefixValues.length == 1) {
            prefix = prefix + NwConstants.IPV4PREFIX;
        }
        return prefix;
    }

    public static boolean isTunnelInterface(AdjacencyResult adjacencyResult) {
        return Tunnel.class.equals(adjacencyResult.getInterfaceType());
    }

    public static InstanceIdentifier<VrfEntry> getNextHopIdentifier(String rd, String prefix) {
        return InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class,new VrfTablesKey(rd)).child(VrfEntry.class,new VrfEntryKey(prefix)).build();
    }

    public static List<String> getNextHopAddresses(DataBroker broker, String rd, String prefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = getNextHopIdentifier(rd, prefix);
        Optional<VrfEntry> vrfEntry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        if (vrfEntry.isPresent()) {
            return FibHelper.getNextHopListFromRoutePaths(vrfEntry.get());
        } else {
            return Collections.emptyList();
        }
    }

    public static Subnetmap getSubnetMap(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier.builder(Subnetmaps.class)
            .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, subnetmapId).orNull();
    }

    public static String getGreLbGroupKey(List<String> availableDcGws) {
        Preconditions.checkNotNull(availableDcGws, "AvailableDcGws is null");
        return "gre-" + availableDcGws.stream().sorted().collect(joining(":"));
    }

    public static void updateLbGroupInfo(BigInteger dpnId, String destinationIp, String groupIdKey,
            String groupId, WriteTransaction tx) {
        InstanceIdentifier<DpnLbNexthops> id = getDpnLbNexthopsIdentifier(dpnId, destinationIp);
        DpnLbNexthops dpnToLbNextHop = buildDpnLbNextHops(dpnId, destinationIp, groupIdKey);
        tx.merge(LogicalDatastoreType.OPERATIONAL, id, dpnToLbNextHop);
        InstanceIdentifier<Nexthops> nextHopsId = getNextHopsIdentifier(groupIdKey);
        Nexthops nextHopsToGroupId = buildNextHops(dpnId, groupIdKey, groupId);
        tx.merge(LogicalDatastoreType.OPERATIONAL, nextHopsId, nextHopsToGroupId);
    }

    public static void removeDpnIdToNextHopInfo(String destinationIp, BigInteger dpnId, WriteTransaction tx) {
        InstanceIdentifier<DpnLbNexthops> id = getDpnLbNexthopsIdentifier(dpnId, destinationIp);
        tx.delete(LogicalDatastoreType.OPERATIONAL, id);
    }

    public static void removeOrUpdateNextHopInfo(BigInteger dpnId, String nextHopKey, String groupId,
            Nexthops nexthops, WriteTransaction tx) {
        InstanceIdentifier<Nexthops> nextHopsId = getNextHopsIdentifier(nextHopKey);
        List<String> targetDeviceIds = nexthops.getTargetDeviceId();
        targetDeviceIds.remove(dpnId.toString());
        if (targetDeviceIds.isEmpty()) {
            tx.delete(LogicalDatastoreType.OPERATIONAL, nextHopsId);
        } else {
            Nexthops nextHopsToGroupId = new NexthopsBuilder().setKey(new NexthopsKey(nextHopKey))
                .setNexthopKey(nextHopKey)
                .setGroupId(groupId)
                .setTargetDeviceId(targetDeviceIds).build();
            tx.put(LogicalDatastoreType.OPERATIONAL, nextHopsId, nextHopsToGroupId);
        }
    }

    private static InstanceIdentifier<DpnLbNexthops> getDpnLbNexthopsIdentifier(BigInteger dpnId,
            String destinationIp) {
        return InstanceIdentifier.builder(DpidL3vpnLbNexthops.class)
                .child(DpnLbNexthops.class, new DpnLbNexthopsKey(destinationIp, dpnId))
                .build();
    }

    private static InstanceIdentifier<Nexthops> getNextHopsIdentifier(String groupIdKey) {
        return InstanceIdentifier.builder(L3vpnLbNexthops.class)
                .child(Nexthops.class, new NexthopsKey(groupIdKey)).build();
    }

    private static Nexthops buildNextHops(BigInteger dpnId, String groupIdKey, String groupId) {
        return new NexthopsBuilder().setKey(new NexthopsKey(groupIdKey))
                .setNexthopKey(groupIdKey)
                .setGroupId(groupId)
                .setTargetDeviceId(Collections.singletonList(dpnId.toString())).build();
    }

    private static DpnLbNexthops buildDpnLbNextHops(BigInteger dpnId, String destinationIp,
            String groupIdKey) {
        return new DpnLbNexthopsBuilder().setKey(new DpnLbNexthopsKey(destinationIp, dpnId))
                .setDstDeviceId(destinationIp).setSrcDpId(dpnId)
                .setNexthopKey(Collections.singletonList(groupIdKey)).build();
    }

    public static Optional<Nexthops> getNexthops(DataBroker dataBroker, String nextHopKey) {
        InstanceIdentifier<Nexthops> nextHopsId = InstanceIdentifier.builder(L3vpnLbNexthops.class)
                .child(Nexthops.class, new NexthopsKey(nextHopKey)).build();
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, nextHopsId);
    }

    public static Optional<DpnLbNexthops> getDpnLbNexthops(DataBroker dataBroker, BigInteger dpnId,
            String destinationIp) {
        InstanceIdentifier<DpnLbNexthops> id = InstanceIdentifier.builder(DpidL3vpnLbNexthops.class)
                .child(DpnLbNexthops.class, new DpnLbNexthopsKey(destinationIp, dpnId))
                .build();
        return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
    }

    protected static boolean isVxlanNetworkAndInternalRouterVpn(DataBroker dataBroker, Uuid subnetId, String
            vpnInstanceName, String rd) {
        if (rd.equals(vpnInstanceName)) {
            Subnetmap subnetmap = getSubnetMap(dataBroker, subnetId);
            if (subnetmap != null) {
                return subnetmap.getNetworkType() == NetworkAttributes.NetworkType.VXLAN;
            }
        }
        return false;
    }

    protected static java.util.Optional<Long> getVniForVxlanNetwork(DataBroker dataBroker, Uuid subnetId) {
        Subnetmap subnetmap = getSubnetMap(dataBroker, subnetId);
        if (subnetmap != null && subnetmap.getNetworkType() == NetworkAttributes.NetworkType.VXLAN) {
            return java.util.Optional.of(subnetmap.getSegmentationId());
        }
        return java.util.Optional.empty();
    }

    protected static boolean enforceVxlanDatapathSemanticsforInternalRouterVpn(DataBroker dataBroker, Uuid subnetId,
                                                                               long vpnId, String rd) {
        return isOpenStackVniSemanticsEnforced
                && isVxlanNetworkAndInternalRouterVpn(dataBroker, subnetId, getVpnNameFromId(dataBroker, vpnId), rd);
    }

    protected static boolean enforceVxlanDatapathSemanticsforInternalRouterVpn(DataBroker dataBroker, Uuid subnetId,
                                                                               String vpnName, String rd) {
        return isOpenStackVniSemanticsEnforced
                && isVxlanNetworkAndInternalRouterVpn(dataBroker, subnetId, vpnName, rd);
    }

    static NodeRef buildNodeRef(BigInteger dpId) {
        return new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId("openflow:" + dpId))).build());
    }

    static InstanceIdentifier<Group> buildGroupInstanceIdentifier(long groupId, BigInteger dpId) {
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(new NodeId("openflow:" + dpId)))
                .augmentation(FlowCapableNode.class).child(Group.class, new GroupKey(new GroupId(groupId))).build();
    }

    static Buckets buildBuckets(List<BucketInfo> listBucketInfo) {
        long index = 0;
        BucketsBuilder bucketsBuilder = new BucketsBuilder();
        if (listBucketInfo != null) {
            List<Bucket> bucketList = new ArrayList<>();

            for (BucketInfo bucketInfo : listBucketInfo) {
                BucketBuilder bucketBuilder = new BucketBuilder();
                bucketBuilder.setAction(bucketInfo.buildActions());
                bucketBuilder.setWeight(bucketInfo.getWeight());
                bucketBuilder.setBucketId(new BucketId(index++));
                bucketBuilder.setWeight(bucketInfo.getWeight()).setWatchPort(bucketInfo.getWatchPort())
                        .setWatchGroup(bucketInfo.getWatchGroup());
                bucketList.add(bucketBuilder.build());
            }

            bucketsBuilder.setBucket(bucketList);
        }
        return bucketsBuilder.build();
    }

    static String getFlowRef(BigInteger dpnId, short tableId, long label, int priority) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + label
                + NwConstants.FLOWID_SEPARATOR + priority;
    }

    static String getFlowRef(BigInteger dpnId, short tableId, String rd, int priority, InetAddress destPrefix) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + rd
                + NwConstants.FLOWID_SEPARATOR + priority + NwConstants.FLOWID_SEPARATOR + destPrefix.getHostAddress();
    }

    static String getL3VpnGatewayFlowRef(short l3GwMacTable, BigInteger dpId, long vpnId, String gwMacAddress) {
        return gwMacAddress + NwConstants.FLOWID_SEPARATOR + vpnId + NwConstants.FLOWID_SEPARATOR + dpId
                + NwConstants.FLOWID_SEPARATOR + l3GwMacTable;
    }

    static Node buildDpnNode(BigInteger dpnId) {
        return new NodeBuilder().setId(new NodeId("openflow:" + dpnId))
                .setKey(new NodeKey(new NodeId("openflow:" + dpnId))).build();
    }

    public static String getBroadcastAddressFromCidr(String cidr) {
        String[] ipaddressValues = cidr.split("/");
        int address = InetAddresses.coerceToInteger(InetAddresses.forString(ipaddressValues[0]));
        int cidrPart = Integer.parseInt(ipaddressValues[1]);
        int netmask = 0;
        for (int j = 0; j < cidrPart; ++j) {
            netmask |= 1 << 31 - j;
        }
        int network = address & netmask;
        int broadcast = network | ~netmask;
        return InetAddresses.toAddrString(InetAddresses.fromInteger(broadcast));
    }
}
