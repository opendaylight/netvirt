/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TransactionAdapter;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvpnVrfEntryHandler extends BaseVrfEntryHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnVrfEntryHandler.class);

    private final ManagedNewTransactionRunner txRunner;
    private final VrfEntryListener vrfEntryListener;
    private final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler;
    private final NexthopManager nexthopManager;
    private final JobCoordinator jobCoordinator;

    EvpnVrfEntryHandler(DataBroker broker, VrfEntryListener vrfEntryListener,
            BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler, NexthopManager nexthopManager,
            JobCoordinator jobCoordinator, FibUtil fibUtil,
            final UpgradeState upgradeState, final DataTreeEventCallbackRegistrar eventCallbacks) {
        super(broker, nexthopManager, null, fibUtil, upgradeState, eventCallbacks);
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.vrfEntryListener = vrfEntryListener;
        this.bgpRouteVrfEntryHandler = bgpRouteVrfEntryHandler;
        this.nexthopManager = nexthopManager;
        this.jobCoordinator = jobCoordinator;
    }

    void createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        LOG.info("Initiating creation of Evpn Flows");
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final VpnInstanceOpDataEntry vpnInstance = getFibUtil().getVpnInstanceOpData(
                vrfTableKey.getRouteDistinguisher()).get();
        Uint32 vpnId = vpnInstance.getVpnId();
        checkNotNull(vpnInstance, "Vpn Instance not available %s", vrfTableKey.getRouteDistinguisher());
        checkNotNull(vpnId, "Vpn Instance with rd %s has null vpnId!", vpnInstance.getVrfId());
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.CONNECTED) {
            SubnetRoute subnetRoute = vrfEntry.augmentation(SubnetRoute.class);
            final Map<VpnToDpnListKey, VpnToDpnList> keyVpnToDpnListMap = vpnInstance.nonnullVpnToDpnList();
            final long elanTag = subnetRoute.getElantag().toJava();
            LOG.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                    rd, vrfEntry.getDestPrefix(), elanTag);
            if (keyVpnToDpnListMap != null) {
                jobCoordinator.enqueueJob("FIB-" + rd + "-" + vrfEntry.getDestPrefix(),
                    () -> Collections.singletonList(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                            for (final VpnToDpnList curDpn : keyVpnToDpnListMap.values()) {
                                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                    vrfEntryListener.installSubnetRouteInFib(curDpn.getDpnId(), elanTag, rd,
                                        vpnId, vrfEntry, tx);
                                }
                            }
                        })));
            }
            return;
        }
        Prefixes localNextHopInfo = getFibUtil().getPrefixToInterface(vpnInstance.getVpnId(),
                                                                            vrfEntry.getDestPrefix());
        List<Uint64> localDpnId = new ArrayList<>();
        boolean isNatPrefix = false;
        if (Prefixes.PrefixCue.Nat.equals(localNextHopInfo.getPrefixCue())) {
            LOG.info("NAT Prefix {} with vpnId {} rd {}. Skip local dpn {} FIB processing",
                    vrfEntry.getDestPrefix(), vpnId, rd, localNextHopInfo.getDpnId());
            localDpnId.add(localNextHopInfo.getDpnId());
            isNatPrefix = true;
        } else {
            localDpnId = createLocalEvpnFlows(vpnInstance.getVpnId(), rd, vrfEntry,
                    localNextHopInfo);
        }
        createRemoteEvpnFlows(rd, vrfEntry, vpnInstance, localDpnId, vrfTableKey, isNatPrefix);
    }

    void removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final VpnInstanceOpDataEntry vpnInstance = getFibUtil().getVpnInstanceOpData(
                vrfTableKey.getRouteDistinguisher()).get();
        if (vpnInstance == null) {
            LOG.error("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        VpnNexthop localNextHopInfo = nexthopManager.getVpnNexthop(vpnInstance.getVpnId(),
                vrfEntry.getDestPrefix());
        List<Uint64> localDpnId = checkDeleteLocalEvpnFLows(vpnInstance.getVpnId(),
                                                                    rd, vrfEntry, localNextHopInfo);
        deleteRemoteEvpnFlows(rd, vrfEntry, vpnInstance, vrfTableKey, localDpnId);
        vrfEntryListener.cleanUpOpDataForFib(vpnInstance.getVpnId(), rd, vrfEntry);
    }

    private List<Uint64> createLocalEvpnFlows(Uint32 vpnId, String rd, VrfEntry vrfEntry,
                                                  Prefixes localNextHopInfo) {
        List<Uint64> returnLocalDpnId = new ArrayList<>();
        String localNextHopIP = vrfEntry.getDestPrefix();
        if (localNextHopInfo == null) {
            //Handle extra routes and imported routes
            Routes extraRoute = getVpnToExtraroute(vpnId, rd, vrfEntry.getDestPrefix());
            if (extraRoute != null && extraRoute.getNexthopIpList() != null) {
                for (String nextHopIp : extraRoute.getNexthopIpList()) {
                    LOG.info("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                    if (nextHopIp != null) {
                        localNextHopInfo = getFibUtil().getPrefixToInterface(vpnId, nextHopIp + "/32");
                        if (localNextHopInfo != null) {
                            localNextHopIP = nextHopIp + "/32";
                            Uint64 dpnId = checkCreateLocalEvpnFlows(localNextHopInfo, localNextHopIP, vpnId,
                                    rd, vrfEntry);
                            returnLocalDpnId.add(dpnId);
                        }
                    }
                }
            }
        } else {
            LOG.info("Creating local EVPN flows for prefix {} rd {} route-paths {} evi {}.",
                    vrfEntry.getDestPrefix(), rd, vrfEntry.getRoutePaths(), vrfEntry.getL3vni());
            Uint64 dpnId = checkCreateLocalEvpnFlows(localNextHopInfo, localNextHopIP, vpnId,
                    rd, vrfEntry);
            returnLocalDpnId.add(dpnId);
        }
        return returnLocalDpnId;
    }

    // Allow deprecated TransactionRunner calls for now
    @SuppressWarnings("ForbidCertainMethod")
    private Uint64 checkCreateLocalEvpnFlows(Prefixes localNextHopInfo, String localNextHopIP,
                                                 final Uint32 vpnId, final String rd,
                                                 final VrfEntry vrfEntry) {
        final Uint64 dpnId = localNextHopInfo.getDpnId();
        String jobKey = FibUtil.getCreateLocalNextHopJobKey(vpnId, dpnId, vrfEntry.getDestPrefix());
        final long groupId = nexthopManager.createLocalNextHop(vpnId, dpnId,
            localNextHopInfo.getVpnInterfaceName(), localNextHopIP, vrfEntry.getDestPrefix(),
            vrfEntry.getGatewayMacAddress(), /*parentVpnId*/ null);
        LOG.debug("LocalNextHopGroup {} created/reused for prefix {} rd {} evi {} route-paths {}", groupId,
            vrfEntry.getDestPrefix(), rd, vrfEntry.getL3vni(), vrfEntry.getRoutePaths());

        final List<InstructionInfo> instructions = Collections.singletonList(
            new InstructionApplyActions(
                Collections.singletonList(new ActionGroup(groupId))));
        jobCoordinator.enqueueJob(jobKey,
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW,
                        TransactionAdapter.toWriteTransaction(tx), null))));
        return dpnId;
    }

    // Allow deprecated TransactionRunner calls for now
    @SuppressWarnings("ForbidCertainMethod")
    private void createRemoteEvpnFlows(String rd, VrfEntry vrfEntry, VpnInstanceOpDataEntry vpnInstance,
                                       List<Uint64> localDpnId, VrfTablesKey vrfTableKey, boolean isNatPrefix) {
        LOG.info("Creating remote EVPN flows for prefix {} rd {} route-paths {} evi {}",
            vrfEntry.getDestPrefix(), rd, vrfEntry.getRoutePaths(), vrfEntry.getL3vni());
        Map<VpnToDpnListKey, VpnToDpnList> keyVpnToDpnListMap = vpnInstance.nonnullVpnToDpnList();
        if (keyVpnToDpnListMap != null) {
            jobCoordinator.enqueueJob("FIB" + rd + vrfEntry.getDestPrefix(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        Datastore.CONFIGURATION, tx -> {
                        for (VpnToDpnList vpnDpn : keyVpnToDpnListMap.values()) {
                            if (!localDpnId.contains(vpnDpn.getDpnId())) {
                                if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                    createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(),
                                            vrfTableKey, vrfEntry, isNatPrefix,
                                            TransactionAdapter.toWriteTransaction(tx));
                                }
                            }
                        }
                    })));
        }
    }

    private void createRemoteFibEntry(final Uint64 remoteDpnId, final Uint32 vpnId, final VrfTablesKey vrfTableKey,
                                      final VrfEntry vrfEntry, boolean isNatPrefix, WriteTransaction tx) {

        String rd = vrfTableKey.getRouteDistinguisher();
        List<SubTransaction> subTxns =  new ArrayList<>();
        LOG.debug("createremotefibentry: adding route {} for rd {} with transaction {}",
                vrfEntry.getDestPrefix(), rd, tx);
        List<NexthopManager.AdjacencyResult> tunnelInterfaceList = resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);

        if (tunnelInterfaceList.isEmpty()) {
            LOG.error("Could not get interface for route-paths: {} in vpn {}",
                    vrfEntry.getRoutePaths(), rd);
            LOG.warn("Failed to add Route: {} in vpn: {}",
                    vrfEntry.getDestPrefix(), rd);
            return;
        }

        for (NexthopManager.AdjacencyResult adjacencyResult : tunnelInterfaceList) {
            List<ActionInfo> actionInfos = new ArrayList<>();
            Uint64 tunnelId = Uint64.ZERO;
            String prefix = adjacencyResult.getPrefix();
            Prefixes prefixInfo = getFibUtil().getPrefixToInterface(vpnId, prefix);
            String interfaceName = prefixInfo.getVpnInterfaceName();
            if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin()) || isNatPrefix) {
                tunnelId = Uint64.valueOf(vrfEntry.getL3vni().longValue());
            } else if (FibUtil.isVxlanNetwork(prefixInfo.getNetworkType())) {
                tunnelId = Uint64.valueOf(prefixInfo.getSegmentationId().longValue());
            } else {
                try {
                    StateTunnelList stateTunnelList = getFibUtil().getTunnelState(interfaceName);
                    if (stateTunnelList == null || stateTunnelList.getOperState() != TunnelOperStatus.Up) {
                        LOG.trace("Tunnel is not up for interface {}", interfaceName);
                        return;
                    }
                    tunnelId = Uint64.valueOf(stateTunnelList.getIfIndex().intValue());
                } catch (ReadFailedException e) {
                    LOG.error("createRemoteFibEntry: error in fetching tunnel state for interface {}",
                            interfaceName, e);
                    continue;
                }
            }
            LOG.debug("adding set tunnel id action for label {}", tunnelId);
            String macAddress = null;
            String vpnName = getFibUtil().getVpnNameFromId(vpnId);
            if (vpnName == null) {
                LOG.debug("Failed to get VPN name for vpnId {}", vpnId);
                return;
            }
            if (interfaceName != null) {
                macAddress = getFibUtil().getMacAddressFromPrefix(interfaceName, vpnName, prefix);
                actionInfos.add(new ActionSetFieldEthernetDestination(new MacAddress(macAddress)));
            }
            actionInfos.add(new ActionSetFieldTunnelId(tunnelId));
            List<ActionInfo> egressActions =
                    nexthopManager.getEgressActionsForInterface(adjacencyResult.getInterfaceName(), actionInfos.size(),
                            true, vpnId, vrfEntry.getDestPrefix());
            if (egressActions.isEmpty()) {
                LOG.error("Failed to retrieve egress action for prefix {} route-paths {} interface {}."
                        + " Aborting remote FIB entry creation..", vrfEntry.getDestPrefix(),
                        vrfEntry.getRoutePaths(), adjacencyResult.getInterfaceName());
                return;
            }
            actionInfos.addAll(egressActions);
            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionApplyActions(actionInfos));
            makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW, tx, subTxns);
        }
        LOG.debug("Successfully added FIB entry for prefix {} in rd {}", vrfEntry.getDestPrefix(), rd);
    }

    // Allow deprecated TransactionRunner calls for now
    @SuppressWarnings("ForbidCertainMethod")
    private void deleteRemoteEvpnFlows(String rd, VrfEntry vrfEntry, VpnInstanceOpDataEntry vpnInstance,
                                       VrfTablesKey vrfTableKey, List<Uint64> localDpnIdList) {
        Map<VpnToDpnListKey, VpnToDpnList> keyVpnToDpnListMap = vpnInstance.nonnullVpnToDpnList();
        List<SubTransaction> subTxns =  new ArrayList<>();
        if (keyVpnToDpnListMap != null) {
            jobCoordinator.enqueueJob("FIB" + rd + vrfEntry.getDestPrefix(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        Datastore.CONFIGURATION, tx -> {
                        final Optional<Routes> extraRouteOptional = Optional.empty();
                        if (localDpnIdList.size() <= 0) {
                            for (VpnToDpnList curDpn1 : keyVpnToDpnListMap.values()) {
                                if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                    if (curDpn1.getDpnState() == VpnToDpnList.DpnState.Active) {
                                        bgpRouteVrfEntryHandler.deleteRemoteRoute(Uint64.ZERO,
                                                curDpn1.getDpnId(),
                                                vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                extraRouteOptional, TransactionAdapter.toWriteTransaction(tx),
                                                subTxns);
                                    }
                                } else {
                                    deleteRemoteRoute(Uint64.ZERO, curDpn1.getDpnId(),
                                            vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                            extraRouteOptional, TransactionAdapter.toWriteTransaction(tx));
                                }
                            }
                        } else {
                            for (Uint64 localDpnId : localDpnIdList) {
                                for (VpnToDpnList curDpn2 : keyVpnToDpnListMap.values()) {
                                    if (!Objects.equals(curDpn2.getDpnId(), localDpnId)) {
                                        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                            if (curDpn2.getDpnState() == VpnToDpnList.DpnState.Active) {
                                                bgpRouteVrfEntryHandler.deleteRemoteRoute(localDpnId,
                                                        curDpn2.getDpnId(),
                                                        vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                        extraRouteOptional,
                                                        TransactionAdapter.toWriteTransaction(tx), subTxns);
                                            }
                                        } else {
                                            deleteRemoteRoute(localDpnId, curDpn2.getDpnId(),
                                                    vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                    extraRouteOptional, TransactionAdapter.toWriteTransaction(tx));
                                        }
                                    }
                                }
                            }
                        }
                    })));
        }
    }

    // Allow deprecated TransactionRunner calls for now
    @SuppressWarnings("ForbidCertainMethod")
    private List<Uint64> checkDeleteLocalEvpnFLows(Uint32 vpnId, String rd, VrfEntry vrfEntry,
                                                       VpnNexthop localNextHopInfo) {
        List<Uint64> returnLocalDpnId = new ArrayList<>();
        if (localNextHopInfo == null) {
            //Handle extra routes and imported routes
        } else {
            final Uint64 dpnId = localNextHopInfo.getDpnId();
            jobCoordinator.enqueueJob("FIB-" + rd + "-" + vrfEntry.getDestPrefix(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW,
                            TransactionAdapter.toWriteTransaction(tx), null))));
            //TODO: verify below adjacency call need to be optimized (?)
            deleteLocalAdjacency(dpnId, vpnId, vrfEntry.getDestPrefix(), vrfEntry.getDestPrefix());
            returnLocalDpnId.add(dpnId);
        }
        return returnLocalDpnId;
    }
}
