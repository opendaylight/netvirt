/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static java.util.stream.Collectors.toList;
import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationEth;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationIp;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIcmpType;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIp;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv4;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.genius.utils.batching.SubTransactionImpl;
import org.opendaylight.netvirt.fibmanager.NexthopManager.AdjacencyResult;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.ExtraRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.ExtraRoutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.RoutesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class BaseVrfEntryHandler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BaseVrfEntryHandler.class);
    private static final BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
    private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;

    private final DataBroker dataBroker;
    final NexthopManager nextHopManager;
    private final IMdsalApiManager mdsalManager;

    @Inject
    public BaseVrfEntryHandler(final DataBroker dataBroker,
                               final NexthopManager nexthopManager,
                               final IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.nextHopManager = nexthopManager;
        this.mdsalManager = mdsalManager;
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} closed", getClass().getSimpleName());
    }

    private void addAdjacencyResultToList(List<AdjacencyResult> adjacencyList, AdjacencyResult adjacencyResult) {
        if (adjacencyResult != null && !adjacencyList.contains(adjacencyResult)) {
            adjacencyList.add(adjacencyResult);
        }
    }

    protected void deleteLocalAdjacency(final BigInteger dpId, final long vpnId, final String ipAddress,
                              final String ipPrefixAddress) {
        LOG.trace("deleteLocalAdjacency called with dpid {}, vpnId{}, ipAddress {}", dpId, vpnId, ipAddress);
        try {
            nextHopManager.removeLocalNextHop(dpId, vpnId, ipAddress, ipPrefixAddress);
        } catch (NullPointerException e) {
            LOG.trace("", e);
        }
    }

    protected List<AdjacencyResult> resolveAdjacency(final BigInteger remoteDpnId, final long vpnId,
                                                     final VrfEntry vrfEntry, String rd) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        FibHelper.sortIpAddress(routePaths);
        List<AdjacencyResult> adjacencyList = new ArrayList<>();
        List<String> prefixIpList = new ArrayList<>();
        LOG.trace("resolveAdjacency called with remotedDpnId {}, vpnId{}, VrfEntry {}",
                remoteDpnId, vpnId, vrfEntry);
        try {
            if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
                List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
                List<Routes> vpnExtraRoutes = VpnExtraRouteHelper.getAllVpnExtraRoutes(dataBroker,
                        FibUtil.getVpnNameFromId(dataBroker, vpnId), usedRds, vrfEntry.getDestPrefix());
                if (vpnExtraRoutes.isEmpty()) {
                    Prefixes prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, vrfEntry.getDestPrefix());
                    // We don't want to provide an adjacencyList for an extra-route-prefix.
                    if (prefixInfo == null) {
                        LOG.debug("The extra route {} in rd {} for vpn {} has been removed from all the next hops",
                                vrfEntry.getDestPrefix(), rd, vpnId);
                        return adjacencyList;
                    }
                    prefixIpList = Collections.singletonList(vrfEntry.getDestPrefix());
                } else {
                    List<String> prefixIpListLocal = new ArrayList<>();
                    vpnExtraRoutes.stream().forEach(route -> {
                        route.getNexthopIpList().stream().forEach(extraRouteIp -> {
                            String ipPrefix;
                            if (isIpv4Address(extraRouteIp)) {
                                ipPrefix = extraRouteIp + NwConstants.IPV4PREFIX;
                            } else {
                                ipPrefix = extraRouteIp + NwConstants.IPV6PREFIX;
                            }
                            prefixIpListLocal.add(ipPrefix);
                        });
                    });
                    prefixIpList = prefixIpListLocal;
                }
            } else {
                prefixIpList = Collections.singletonList(vrfEntry.getDestPrefix());
            }

            for (String prefixIp : prefixIpList) {
                if (routePaths == null || routePaths.isEmpty()) {
                    LOG.trace("Processing Destination IP {} without NextHop IP", prefixIp);
                    AdjacencyResult adjacencyResult = nextHopManager.getRemoteNextHopPointer(remoteDpnId, vpnId,
                            prefixIp, null);
                    addAdjacencyResultToList(adjacencyList, adjacencyResult);
                    continue;
                }
                adjacencyList.addAll(routePaths.stream()
                        .map(routePath -> {
                            LOG.debug("NextHop IP for destination {} is {}", prefixIp,
                                    routePath.getNexthopAddress());
                            return nextHopManager.getRemoteNextHopPointer(remoteDpnId, vpnId,
                                    prefixIp, routePath.getNexthopAddress());
                        })
                        .filter(adjacencyResult -> adjacencyResult != null && !adjacencyList.contains(adjacencyResult))
                        .distinct()
                        .collect(toList()));
            }
        } catch (NullPointerException e) {
            LOG.trace("", e);
        }
        return adjacencyList;
    }

    protected void makeConnectedRoute(BigInteger dpId, long vpnId, VrfEntry vrfEntry, String rd,
                            List<InstructionInfo> instructions, int addOrRemove, WriteTransaction tx,
                            List<SubTransaction> subTxns) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        LOG.trace("makeConnectedRoute: vrfEntry {}", vrfEntry);
        String[] values = vrfEntry.getDestPrefix().split("/");
        String ipAddress = values[0];
        int prefixLength = values.length == 1 ? 0 : Integer.parseInt(values[1]);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Adding route to DPN {} for rd {} prefix {} ", dpId, rd, vrfEntry.getDestPrefix());
        } else {
            LOG.debug("Removing route from DPN {} for rd {} prefix {}", dpId, rd, vrfEntry.getDestPrefix());
        }
        InetAddress destPrefix;
        try {
            destPrefix = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            LOG.error("Failed to get destPrefix for prefix {} rd {} VpnId {} DPN {}",
                    vrfEntry.getDestPrefix(), rd, vpnId, dpId, e);
            return;
        }

        List<MatchInfo> matches = new ArrayList<>();

        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

        if (destPrefix instanceof Inet4Address) {
            matches.add(MatchEthernetType.IPV4);
            if (prefixLength != 0) {
                matches.add(new MatchIpv4Destination(destPrefix.getHostAddress(), Integer.toString(prefixLength)));
            }
        } else {
            matches.add(MatchEthernetType.IPV6);
            if (prefixLength != 0) {
                matches.add(new MatchIpv6Destination(destPrefix.getHostAddress() + "/" + prefixLength));
            }
        }

        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        String flowRef = FibUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, rd, priority, destPrefix);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef, priority,
                flowRef, 0, 0,
                COOKIE_VM_FIB_TABLE, matches, instructions);
        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        FlowKey flowKey = new FlowKey(new FlowId(flowId));
        Node nodeDpn = FibUtil.buildDpnNode(dpId);

        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            SubTransaction subTransaction = new SubTransactionImpl();
            if (addOrRemove == NwConstants.ADD_FLOW) {
                subTransaction.setInstanceIdentifier(flowInstanceId);
                subTransaction.setInstance(flow);
                subTransaction.setAction(SubTransaction.CREATE);
            } else {
                subTransaction.setInstanceIdentifier(flowInstanceId);
                subTransaction.setAction(SubTransaction.DELETE);
            }
            subTxns.add(subTransaction);
        }

        if (addOrRemove == NwConstants.ADD_FLOW) {
            tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flow, true);
        } else {
            tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
        }

        if (!wrTxPresent) {
            tx.submit();
        }
    }

    protected void addRewriteDstMacAction(long vpnId, VrfEntry vrfEntry, Prefixes prefixInfo,
                                        List<ActionInfo> actionInfos) {
        if (vrfEntry.getMac() != null) {
            actionInfos.add(new ActionSetFieldEthernetDestination(actionInfos.size(),
                    new MacAddress(vrfEntry.getMac())));
            return;
        }
        if (prefixInfo == null) {
            prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, vrfEntry.getDestPrefix());
            //Checking PrefixtoInterface again as it is populated later in some cases
            if (prefixInfo == null) {
                LOG.debug("No prefix info found for prefix {}", vrfEntry.getDestPrefix());
                return;
            }
        }
        String ipPrefix = prefixInfo.getIpAddress();
        String ifName = prefixInfo.getVpnInterfaceName();
        if (ifName == null) {
            LOG.debug("Failed to get VPN interface for prefix {}", ipPrefix);
            return;
        }
        String macAddress = FibUtil.getMacAddressFromPrefix(dataBroker, ifName, ipPrefix);
        if (macAddress == null) {
            LOG.warn("No MAC address found for VPN interface {} prefix {}", ifName, ipPrefix);
            return;
        }
        actionInfos.add(new ActionSetFieldEthernetDestination(actionInfos.size(), new MacAddress(macAddress)));
    }

    protected void addTunnelInterfaceActions(AdjacencyResult adjacencyResult, long vpnId, VrfEntry vrfEntry,
                                           List<ActionInfo> actionInfos, String rd) {
        Class<? extends TunnelTypeBase> tunnelType =
                VpnExtraRouteHelper.getTunnelType(nextHopManager.getInterfaceManager(),
                        adjacencyResult.getInterfaceName());
        if (tunnelType == null) {
            LOG.debug("Tunnel type not found for vrfEntry {}", vrfEntry);
            return;
        }
        // TODO - For now have added routePath into adjacencyResult so that we know for which
        // routePath this result is built for. If this is not possible construct a map which does
        // the same.
        String nextHopIp = adjacencyResult.getNextHopIp();
        java.util.Optional<Long> optionalLabel = FibUtil.getLabelForNextHop(vrfEntry, nextHopIp);
        if (!optionalLabel.isPresent()) {
            LOG.warn("NextHopIp {} not found in vrfEntry {}", nextHopIp, vrfEntry);
            return;
        }
        long label = optionalLabel.get();
        BigInteger tunnelId = null;
        Prefixes prefixInfo = null;
        // FIXME vxlan vni bit set is not working properly with OVS.need to
        // revisit
        if (tunnelType.equals(TunnelTypeVxlan.class)) {
            prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, vrfEntry.getDestPrefix());
            //For extra route, the prefixInfo is fetched from the primary adjacency
            if (prefixInfo == null) {
                prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, adjacencyResult.getPrefix());
            }
            // Internet VPN VNI will be used as tun_id for NAT use-cases
            if (Prefixes.PrefixCue.Nat.equals(prefixInfo.getPrefixCue())) {
                if (vrfEntry.getL3vni() != null && vrfEntry.getL3vni() != 0) {
                    tunnelId = BigInteger.valueOf(vrfEntry.getL3vni());
                }
            } else {
                if (FibUtil.enforceVxlanDatapathSemanticsforInternalRouterVpn(dataBroker, prefixInfo.getSubnetId(),
                        vpnId, rd)) {
                    java.util.Optional<Long> optionalVni = FibUtil.getVniForVxlanNetwork(dataBroker,
                            prefixInfo.getSubnetId());
                    if (!optionalVni.isPresent()) {
                        LOG.error("VNI not found for nexthop {} vrfEntry {} with subnetId {}", nextHopIp,
                                vrfEntry, prefixInfo.getSubnetId());
                        return;
                    }
                    tunnelId = BigInteger.valueOf(optionalVni.get());
                } else {
                    tunnelId = BigInteger.valueOf(label);
                }
            }
        } else {
            tunnelId = BigInteger.valueOf(label);
        }
        LOG.debug("adding set tunnel id action for label {}", label);
        actionInfos.add(new ActionSetFieldTunnelId(tunnelId));
        addRewriteDstMacAction(vpnId, vrfEntry, prefixInfo, actionInfos);
    }

    public void programRemoteFib(final BigInteger remoteDpnId, final long vpnId,
                                  final VrfEntry vrfEntry, WriteTransaction tx, String rd,
                                  List<AdjacencyResult> adjacencyResults,
                                  List<SubTransaction> subTxns) {
        List<InstructionInfo> instructions = new ArrayList<>();
        for (AdjacencyResult adjacencyResult : adjacencyResults) {
            List<ActionInfo> actionInfos = new ArrayList<>();
            String egressInterface = adjacencyResult.getInterfaceName();
            if (FibUtil.isTunnelInterface(adjacencyResult)) {
                addTunnelInterfaceActions(adjacencyResult, vpnId, vrfEntry, actionInfos, rd);
            } else {
                addRewriteDstMacAction(vpnId, vrfEntry, null, actionInfos);
            }
            List<ActionInfo> egressActions = nextHopManager.getEgressActionsForInterface(egressInterface,
                    actionInfos.size());
            if (egressActions.isEmpty()) {
                LOG.error(
                        "Failed to retrieve egress action for prefix {} route-paths {} interface {}. "
                                + "Aborting remote FIB entry creation.",
                        vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(), egressInterface);
                return;
            }
            actionInfos.addAll(egressActions);
            instructions.add(new InstructionApplyActions(actionInfos));
        }
        makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW, tx, subTxns);
    }

    public boolean checkDpnDeleteFibEntry(VpnNexthop localNextHopInfo, BigInteger remoteDpnId, long vpnId,
                                           VrfEntry vrfEntry, String rd,
                                           WriteTransaction tx, List<SubTransaction> subTxns) {
        boolean isRemoteRoute = true;
        if (localNextHopInfo != null) {
            isRemoteRoute = !remoteDpnId.equals(localNextHopInfo.getDpnId());
        }
        if (isRemoteRoute) {
            makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW, tx, subTxns);
            LOG.debug("Successfully delete FIB entry: vrfEntry={}, vpnId={}", vrfEntry.getDestPrefix(), vpnId);
            return true;
        } else {
            LOG.debug("Did not delete FIB entry: rd={}, vrfEntry={}, as it is local to dpnId={}",
                    rd, vrfEntry.getDestPrefix(), remoteDpnId);
            return false;
        }
    }

    public void deleteRemoteRoute(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                  final long vpnId, final VrfTablesKey vrfTableKey,
                                  final VrfEntry vrfEntry, Optional<Routes> extraRouteOptional,
                                  WriteTransaction tx) {

        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        LOG.debug("deleting remote route: prefix={}, vpnId={} localDpnId {} remoteDpnId {}",
                vrfEntry.getDestPrefix(), vpnId, localDpnId, remoteDpnId);
        String rd = vrfTableKey.getRouteDistinguisher();

        if (localDpnId != null && localDpnId != BigInteger.ZERO) {
            // localDpnId is not known when clean up happens for last vm for a vpn on a dpn
            if (extraRouteOptional.isPresent()) {
                nextHopManager.setupLoadBalancingNextHop(vpnId, remoteDpnId, vrfEntry.getDestPrefix(),
                        Collections.emptyList() /*listBucketInfo*/ , false);
            }
            makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW, tx, null);
            LOG.debug("Successfully delete FIB entry: vrfEntry={}, vpnId={}", vrfEntry.getDestPrefix(), vpnId);
            return;
        }

        // below two reads are kept as is, until best way is found to identify dpnID
        VpnNexthop localNextHopInfo = nextHopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
        if (extraRouteOptional.isPresent()) {
            nextHopManager.setupLoadBalancingNextHop(vpnId, remoteDpnId, vrfEntry.getDestPrefix(),
                    Collections.emptyList() /*listBucketInfo*/ , false);
        } else {
            checkDpnDeleteFibEntry(localNextHopInfo, remoteDpnId, vpnId, vrfEntry, rd, tx, null);
        }
        if (!wrTxPresent) {
            tx.submit();
        }
    }

    public static InstanceIdentifier<Routes> getVpnToExtrarouteIdentifier(String vpnName, String vrfId,
                                                                    String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).child(Routes.class, new RoutesKey(ipPrefix)).build();
    }

    public Routes getVpnToExtraroute(Long vpnId, String vpnRd, String destPrefix) {
        String optVpnName = FibUtil.getVpnNameFromId(dataBroker, vpnId);
        if (optVpnName != null) {
            InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(
                    optVpnName, vpnRd, destPrefix);
            return MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId).orNull();
        }
        return null;
    }

    public FlowEntity buildL3vpnGatewayFlow(BigInteger dpId, String gwMacAddress, long vpnId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(gwMacAddress)));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.L3_FIB_TABLE));
        String flowId = FibUtil.getL3VpnGatewayFlowRef(NwConstants.L3_GW_MAC_TABLE, dpId, vpnId, gwMacAddress);
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_GW_MAC_TABLE,
                flowId, 20, flowId, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE, mkMatches, mkInstructions);
    }

    public void installPingResponderFlowEntry(BigInteger dpnId, long vpnId, String routerInternalIp,
                                              MacAddress routerMac, long label, int addOrRemove) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchIpProtocol.ICMP);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIcmpv4((short) 8, (short) 0));
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpv4Destination(routerInternalIp, "32"));

        List<ActionInfo> actionsInfos = new ArrayList<>();

        // Set Eth Src and Eth Dst
        actionsInfos.add(new ActionMoveSourceDestinationEth());
        actionsInfos.add(new ActionSetFieldEthernetSource(routerMac));

        // Move Ip Src to Ip Dst
        actionsInfos.add(new ActionMoveSourceDestinationIp());
        actionsInfos.add(new ActionSetSourceIp(routerInternalIp, "32"));

        // Set the ICMP type to 0 (echo reply)
        actionsInfos.add(new ActionSetIcmpType((short) 0));

        actionsInfos.add(new ActionNxLoadInPort(BigInteger.ZERO));

        actionsInfos.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));

        List<InstructionInfo> instructions = new ArrayList<>();

        instructions.add(new InstructionApplyActions(actionsInfos));

        int priority = FibConstants.DEFAULT_FIB_FLOW_PRIORITY + FibConstants.DEFAULT_PREFIX_LENGTH;
        String flowRef = FibUtil.getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, label, priority);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_FIB_TABLE, flowRef, priority, flowRef,
                0, 0, NwConstants.COOKIE_VM_FIB_TABLE, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.syncInstallFlow(flowEntity, 1);
        } else {
            mdsalManager.syncInstallFlow(flowEntity, 1);
        }
    }
}
