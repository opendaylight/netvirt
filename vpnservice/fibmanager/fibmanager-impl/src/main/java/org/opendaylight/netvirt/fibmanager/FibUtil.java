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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FibUtil.class);

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

    public static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn
            .rev130911.VpnInstanceOpData.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .VpnInstanceOpDataEntry.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                    .VpnInstanceOpDataEntryKey(rd))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.VpnToDpnList.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                    .vpn.instance.op.data.entry.VpnToDpnListKey(dpnId)).build();
    }

    static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    static Optional<VpnInstanceOpDataEntry> getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnInstanceOpDataIdentifier(rd);
        return MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
    }

    static String getNextHopLabelKey(String rd, String prefix) {
        String key = rd + FibConstants.SEPARATOR + prefix;
        return key;
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
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
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
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).transform(VpnInstance::getVpnId).or(-1L);
    }

    /**
     * Retrieves the VpnInstance name (typically the VPN Uuid) out from the route-distinguisher.
     *
     * @param broker The DataBroker
     * @param rd The route-distinguisher
     * @return The vpn instance
     */
    public static Optional<String> getVpnNameFromRd(DataBroker broker, String rd) {
        return getVpnInstanceOpData(broker, rd).transform(VpnInstanceOpDataEntry::getVpnInstanceName);
    }

    static List<InterVpnLink> getAllInterVpnLinks(DataBroker broker) {
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();

        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinksIid).transform(
            InterVpnLinks::getInterVpnLink).or(new ArrayList<>());
    }

    /**
     * Returns the instance identifier for a given vpnLinkName.
     *
     * @param vpnLinkName The vpn link name
     * @return InstanceIdentifier
     */
    public static InstanceIdentifier<InterVpnLinkState> getInterVpnLinkStateIid(String vpnLinkName) {
        return InstanceIdentifier.builder(InterVpnLinkStates.class)
            .child(InterVpnLinkState.class, new InterVpnLinkStateKey(vpnLinkName)).build();
    }

    /**
     * Checks if the InterVpnLink is in Active state.
     *
     * @param broker The DataBroker
     * @param vpnLinkName The vpn linkname
     * @return The link state
     */
    public static boolean isInterVpnLinkActive(DataBroker broker, String vpnLinkName) {
        Optional<InterVpnLinkState> interVpnLinkState = getInterVpnLinkState(broker, vpnLinkName);
        if (!interVpnLinkState.isPresent()) {
            LOG.warn("Could not find Operative State for InterVpnLink {}", vpnLinkName);
            return false;
        }

        return interVpnLinkState.get().getState().equals(InterVpnLinkState.State.Active);
    }

    /**
     * Checks if the state of the interVpnLink.
     *
     * @param broker The DataBroker
     * @param vpnLinkName The vpn linkname
     * @return The link state
     */
    public static Optional<InterVpnLinkState> getInterVpnLinkState(DataBroker broker, String vpnLinkName) {
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = getInterVpnLinkStateIid(vpnLinkName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid);
    }

    /**
     * Obtains the route-distinguisher for a given vpn-name.
     *
     * @param broker The DataBroker
     * @param vpnName vpn name
     * @return route-distinguisher
     */
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).transform(VpnInstance::getVrfId).orNull();
    }

    public static int getUniqueId(IdManagerService idManager, String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();

        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id", e);
        }
        return 0;
    }

    public static String getVpnNameFromId(DataBroker broker, long vpnId) {
        InstanceIdentifier<VpnIds> id = getVpnIdToVpnInstanceIdentifier(vpnId);
        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id).transform(VpnIds::getVpnInstanceName)
                .orNull();
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
            LOG.error("addFibEntryToDS: error ", e);
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
            LOG.error("addFibEntryToDS: error ", e);
        }
    }

    private static void buildVpnEncapSpecificInfo(VrfEntryBuilder builder, VrfEntry.EncapType encapType, long label,
                                         long l3vni, String macAddress, String gatewayMac, List<String> nextHopList) {
        if (encapType == null) {
            builder.setMac(macAddress);
            return;
        }
        //if (!encapType.equals(VrfEntry.EncapType.Mplsgre)) {
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
                        .map(nextHop -> {
                            return FibHelper.buildRoutePath(nextHop, lbl);
                        }).collect(toList());
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
                if (writeConfigTxn != null) {
                    writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, routePathsId);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, routePathsId);
                }
                LOG.info("Removed Route Path rd {} prefix {}, nextHop {}, label {}", rd, prefix,
                        routePath.getNexthopAddress(), routePath.getLabel());
            }
        } else {
            LOG.warn("Could not find VrfEntry for Route-Distinguisher={} and prefix={}", rd, prefix);
        }
    }

    public static void updateFibEntry(DataBroker broker, String rd, String prefix, List<String> nextHopList,
                                      String gwMacAddress, long label, WriteTransaction writeConfigTxn) {

        LOG.debug("Updating fib entry for prefix {} with nextHopList {} for rd {}", prefix, nextHopList, rd);

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

        if (entry.isPresent()) {
            RouteOrigin routeOrigin = RouteOrigin.value(entry.get().getOrigin());
            // Update the VRF entry with nextHopList
            VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder(entry.get(), label, nextHopList, routeOrigin,
                    null /* parentVpnRd */).setGatewayMacAddress(gwMacAddress).build();

            if (nextHopList.isEmpty()) {
                if (writeConfigTxn != null) {
                    writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
            } else {
                if (writeConfigTxn != null) {
                    writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
            }
            LOG.debug("Updated fib entry for prefix {} with nextHopList {} for rd {}", prefix, nextHopList, rd);
        } else {
            LOG.warn("Could not find VrfEntry for Route-Distinguisher={} and prefix={}", rd, prefix);
        }
    }

    public static void addVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
        LOG.debug("Adding vrf table for rd {}", rd);
        InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        VrfTablesBuilder vrfTablesBuilder = new VrfTablesBuilder().setKey(new VrfTablesKey(rd))
            .setRouteDistinguisher(rd).setVrfEntry(new ArrayList<>());
        if (writeConfigTxn != null) {
            writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTablesBuilder.build());
        } else {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTablesBuilder.build());
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

    public static List<String> getNextHopListFromRoutePaths(final VrfEntry vrfEntry) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        return routePaths.stream()
                .map(routePath -> routePath.getNexthopAddress())
                .collect(toList());
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
                .map(routePath -> java.util.Optional.of(routePath.getLabel()))
                .orElse(java.util.Optional.empty());
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

    public static void updateUsedRdAndVpnToExtraRoute(WriteTransaction writeOperTxn, DataBroker broker,
                                                      String nextHopToRemove, String primaryRd, String prefix) {
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
        java.util.Optional<String> rdToRemove = usedRds.stream()
                .map(usedRd -> {
                    Optional<Routes> vpnExtraRoutes = VpnExtraRouteHelper
                            .getVpnExtraroutes(broker, vpnName, usedRd, prefix);
                    // Since all the nexthops under one OVS will be present under one rd, only 1 nexthop is read
                    // to identify the OVS
                    return vpnExtraRoutes.isPresent() ? new ImmutablePair<String, String>(
                            vpnExtraRoutes.get().getNexthopIpList().get(0),
                            usedRd) : new ImmutablePair<String, String>("", "");
                })
                .filter(pair -> {
                    if (pair.getLeft().isEmpty()) {
                        return false;
                    }
                    Prefixes prefixToInterface = getPrefixToInterface(broker, vpnId, getIpPrefix(pair.getLeft()));
                    return prefixToInterface != null ? nextHopToRemove
                            .equals(getEndpointIpAddressForDPN(broker, prefixToInterface.getDpnId())) : false;
                })
                .map(pair -> pair.getRight()).findFirst();
        if (!rdToRemove.isPresent()) {
            return;
        }
        Optional<Routes> optRoutes = VpnExtraRouteHelper.getVpnExtraroutes(broker, vpnName, rdToRemove.get(), prefix);
        if (!optRoutes.isPresent()) {
            return;
        }
        Prefixes prefixToInterface = getPrefixToInterface(broker, vpnId,
                getIpPrefix(optRoutes.get().getNexthopIpList().get(0)));
        if (prefixToInterface != null) {
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                    getAdjacencyIdentifier(prefixToInterface.getVpnInterfaceName(), prefix));
        }
        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, rdToRemove.get(), prefix));
        usedRds.remove(rdToRemove.get());
        writeOperTxn.put(LogicalDatastoreType.CONFIGURATION,
                VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, prefix),
                getDestPrefixesBuilder(prefix, usedRds).build());
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
                nextHopIp = nexthopIpList.get(0).getIpAddress().getValue().toString();
            }
        }
        return nextHopIp;
    }

    public static DestPrefixesBuilder getDestPrefixesBuilder(String destPrefix, List<String> rd) {
        return new DestPrefixesBuilder().setKey(new DestPrefixesKey(destPrefix)).setDestPrefix(destPrefix).setRds(rd);
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

    public static Optional<Routes> getLastRoutePathExtraRouteIfPresent(DataBroker dataBroker, Long vpnId,
            String rd, String prefix) {
        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, prefix);
        String vpnName = getVpnNameFromId(dataBroker, vpnId);
        if (usedRds == null || usedRds.isEmpty()) {
            LOG.debug("No used rd found for prefix {} on vpn {}", prefix, vpnName);
            return Optional.absent();
        } else if (usedRds.size() > 1) {
            LOG.debug("The extra route prefix is still present in some DPNs");
            return Optional.absent();
        } else {
            rd = usedRds.get(0);
        }
        //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
        return VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                getVpnNameFromId(dataBroker, vpnId), rd, prefix);
    }

    public static InstanceIdentifier<VrfEntry> getNextHopIdentifier(String rd, String prefix) {
        return InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class,new VrfTablesKey(rd)).child(VrfEntry.class,new VrfEntryKey(prefix)).build();
    }

    public static List<String> getNextHopAddresses(DataBroker broker, String rd, String prefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = getNextHopIdentifier(rd, prefix);
        Optional<VrfEntry> vrfEntry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        if (vrfEntry.isPresent()) {
            return getNextHopListFromRoutePaths(vrfEntry.get());
        } else {
            return Collections.emptyList();
        }
    }

    public static Optional<String> getGatewayMac(DataBroker dataBroker, String rd, String localNextHopIP) {
        InstanceIdentifier<VrfEntry> vrfEntryId = getNextHopIdentifier(rd, localNextHopIP);
        Optional<VrfEntry> vrfEntry = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        if (vrfEntry.isPresent()) {
            return Optional.fromNullable(vrfEntry.get().getGatewayMacAddress());
        } else {
            return Optional.absent();
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
        if (targetDeviceIds.size() == 0) {
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

    protected static boolean isVxlanNetworkAndRouterBasedVpn(DataBroker dataBroker, Uuid subnetId, String
            vpnInstanceName, String rd) {
        Subnetmap subnetmap = getSubnetMap(dataBroker, subnetId);
        if (subnetmap != null) {
            return subnetmap.getNetworkType() == NetworkAttributes.NetworkType.VXLAN && vpnInstanceName.equals(rd);
        }
        return false;
    }

    protected static Long getVniForNetwork(DataBroker dataBroker, Uuid subnetId) {
        Subnetmap subnetmap = getSubnetMap(dataBroker, subnetId);
        if (subnetmap != null) {
            return subnetmap.getSegmentationId();
        }
        return null;
    }
}
