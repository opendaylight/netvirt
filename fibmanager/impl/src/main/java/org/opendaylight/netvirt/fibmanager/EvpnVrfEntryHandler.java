/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EvpnVrfEntryHandler extends BaseVrfEntryHandler implements IVrfEntryHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnVrfEntryHandler.class);
    private final ManagedNewTransactionRunner txRunner;
    private final VrfEntryListener vrfEntryListener;
    private final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler;
    private final NexthopManager nexthopManager;
    private final JobCoordinator jobCoordinator;
    private final IElanService elanManager;

    EvpnVrfEntryHandler(DataBroker broker, VrfEntryListener vrfEntryListener,
            BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler, NexthopManager nexthopManager,
            JobCoordinator jobCoordinator, IElanService elanManager, FibUtil fibUtil) {
        super(broker, nexthopManager, null, fibUtil);
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.vrfEntryListener = vrfEntryListener;
        this.bgpRouteVrfEntryHandler = bgpRouteVrfEntryHandler;
        this.nexthopManager = nexthopManager;
        this.jobCoordinator = jobCoordinator;
        this.elanManager = elanManager;
    }

    @Override
    public void createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        LOG.info("Initiating creation of Evpn Flows");
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final VpnInstanceOpDataEntry vpnInstance = getFibUtil().getVpnInstanceOpData(
                vrfTableKey.getRouteDistinguisher()).get();
        Long vpnId = vpnInstance.getVpnId();
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnId, "Vpn Instance with rd " + vpnInstance.getVrfId()
                + " has null vpnId!");
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.CONNECTED) {
            SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
            final List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
            final long elanTag = subnetRoute.getElantag();
            LOG.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                    rd, vrfEntry.getDestPrefix(), elanTag);
            if (vpnToDpnList != null) {
                jobCoordinator.enqueueJob("FIB-" + rd + "-" + vrfEntry.getDestPrefix(),
                    () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                        for (final VpnToDpnList curDpn : vpnToDpnList) {
                            if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                vrfEntryListener.installSubnetRouteInFib(curDpn.getDpnId(), elanTag, rd,
                                        vpnId, vrfEntry, tx);
                            }
                        }
                    })));
            }
            return;
        }
        Prefixes localNextHopInfo = getFibUtil().getPrefixToInterface(vpnInstance.getVpnId(), vrfEntry.getDestPrefix());
        List<BigInteger> localDpnId = new ArrayList<>();
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

    @Override
    public void removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final VpnInstanceOpDataEntry vpnInstance = getFibUtil().getVpnInstanceOpData(
                vrfTableKey.getRouteDistinguisher()).get();
        if (vpnInstance == null) {
            LOG.error("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        VpnNexthop localNextHopInfo = nexthopManager.getVpnNexthop(vpnInstance.getVpnId(),
                vrfEntry.getDestPrefix());
        List<BigInteger> localDpnId = checkDeleteLocalEvpnFLows(vpnInstance.getVpnId(), rd, vrfEntry, localNextHopInfo);
        deleteRemoteEvpnFlows(rd, vrfEntry, vpnInstance, vrfTableKey, localDpnId);
        vrfEntryListener.cleanUpOpDataForFib(vpnInstance.getVpnId(), rd, vrfEntry);
    }

    @Override
    public void updateFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update, String rd) {
        //Not used
    }

    private List<BigInteger> createLocalEvpnFlows(long vpnId, String rd, VrfEntry vrfEntry,
                                                  Prefixes localNextHopInfo) {
        List<BigInteger> returnLocalDpnId = new ArrayList<>();
        String localNextHopIP = vrfEntry.getDestPrefix();
        if (localNextHopInfo == null) {
            //Handle extra routes and imported routes
            Routes extraRoute = getVpnToExtraroute(vpnId, rd, vrfEntry.getDestPrefix());
            if (extraRoute != null) {
                for (String nextHopIp : extraRoute.getNexthopIpList()) {
                    LOG.info("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                    if (nextHopIp != null) {
                        localNextHopInfo = getFibUtil().getPrefixToInterface(vpnId, nextHopIp + "/32");
                        if (localNextHopInfo != null) {
                            localNextHopIP = nextHopIp + "/32";
                            BigInteger dpnId = checkCreateLocalEvpnFlows(localNextHopInfo, localNextHopIP, vpnId,
                                    rd, vrfEntry);
                            returnLocalDpnId.add(dpnId);
                        }
                    }
                }
            }
        } else {
            LOG.info("Creating local EVPN flows for prefix {} rd {} route-paths {} evi {}.",
                    vrfEntry.getDestPrefix(), rd, vrfEntry.getRoutePaths(), vrfEntry.getL3vni());
            BigInteger dpnId = checkCreateLocalEvpnFlows(localNextHopInfo, localNextHopIP, vpnId,
                    rd, vrfEntry);
            returnLocalDpnId.add(dpnId);
        }
        return returnLocalDpnId;
    }

    private BigInteger checkCreateLocalEvpnFlows(Prefixes localNextHopInfo, String localNextHopIP,
                                                 final Long vpnId, final String rd,
                                                 final VrfEntry vrfEntry) {
        final BigInteger dpnId = localNextHopInfo.getDpnId();
        String jobKey = "FIB-" + vpnId.toString() + "-" + dpnId.toString() + "-" + vrfEntry.getDestPrefix();
        final long groupId = nexthopManager.createLocalNextHop(vpnId, dpnId,
            localNextHopInfo.getVpnInterfaceName(), localNextHopIP, vrfEntry.getDestPrefix(),
            vrfEntry.getGatewayMacAddress(), jobKey);
        LOG.debug("LocalNextHopGroup {} created/reused for prefix {} rd {} evi {} route-paths {}", groupId,
            vrfEntry.getDestPrefix(), rd, vrfEntry.getL3vni(), vrfEntry.getRoutePaths());

        final List<InstructionInfo> instructions = Collections.singletonList(
            new InstructionApplyActions(
                Collections.singletonList(new ActionGroup(groupId))));
        jobCoordinator.enqueueJob("FIB-" + vpnId.toString() + "-" + dpnId.toString()
                + "-" + vrfEntry.getDestPrefix(),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                tx -> makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW, tx,
                        null))));
        return dpnId;
    }

    private void createRemoteEvpnFlows(String rd, VrfEntry vrfEntry, VpnInstanceOpDataEntry vpnInstance,
                                       List<BigInteger> localDpnId, VrfTablesKey vrfTableKey, boolean isNatPrefix) {
        LOG.info("Creating remote EVPN flows for prefix {} rd {} route-paths {} evi {}",
            vrfEntry.getDestPrefix(), rd, vrfEntry.getRoutePaths(), vrfEntry.getL3vni());
        List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            jobCoordinator.enqueueJob("FIB" + rd + vrfEntry.getDestPrefix(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    for (VpnToDpnList vpnDpn : vpnToDpnList) {
                        if (!localDpnId.contains(vpnDpn.getDpnId())) {
                            if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(),
                                        vrfTableKey, vrfEntry, isNatPrefix, tx);
                            }
                        }
                    }
                })));
        }
    }

    private void createRemoteFibEntry(final BigInteger remoteDpnId, final long vpnId, final VrfTablesKey vrfTableKey,
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
            BigInteger tunnelId;
            String prefix = adjacencyResult.getPrefix();
            Prefixes prefixInfo = getFibUtil().getPrefixToInterface(vpnId, prefix);
            String interfaceName = prefixInfo.getVpnInterfaceName();
            if (vrfEntry.getOrigin().equals(RouteOrigin.BGP.getValue()) || isNatPrefix) {
                tunnelId = BigInteger.valueOf(vrfEntry.getL3vni());
            } else if (elanManager.isOpenStackVniSemanticsEnforced()) {
                tunnelId = BigInteger.valueOf(getFibUtil().getVniForVxlanNetwork(prefixInfo.getSubnetId()).get());
            } else {
                Interface interfaceState = getFibUtil().getInterfaceStateFromOperDS(interfaceName);
                tunnelId = BigInteger.valueOf(interfaceState.getIfIndex());
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
                            true);
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

    private void deleteRemoteEvpnFlows(String rd, VrfEntry vrfEntry, VpnInstanceOpDataEntry vpnInstance,
                                       VrfTablesKey vrfTableKey, List<BigInteger> localDpnIdList) {
        List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        List<SubTransaction> subTxns =  new ArrayList<>();
        if (vpnToDpnList != null) {
            jobCoordinator.enqueueJob("FIB" + rd + vrfEntry.getDestPrefix(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    final Optional<Routes> extraRouteOptional = Optional.absent();
                    if (localDpnIdList.size() <= 0) {
                        for (VpnToDpnList curDpn1 : vpnToDpnList) {
                            if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                if (curDpn1.getDpnState() == VpnToDpnList.DpnState.Active) {
                                    bgpRouteVrfEntryHandler.deleteRemoteRoute(BigInteger.ZERO, curDpn1.getDpnId(),
                                            vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                            extraRouteOptional, tx, subTxns);
                                }
                            } else {
                                deleteRemoteRoute(BigInteger.ZERO, curDpn1.getDpnId(),
                                        vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                        extraRouteOptional, tx);
                            }
                        }
                    } else {
                        for (BigInteger localDpnId : localDpnIdList) {
                            for (VpnToDpnList curDpn2 : vpnToDpnList) {
                                if (!curDpn2.getDpnId().equals(localDpnId)) {
                                    if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                        if (curDpn2.getDpnState() == VpnToDpnList.DpnState.Active) {
                                            bgpRouteVrfEntryHandler.deleteRemoteRoute(localDpnId, curDpn2.getDpnId(),
                                                    vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                    extraRouteOptional, tx, subTxns);
                                        }
                                    } else {
                                        deleteRemoteRoute(localDpnId, curDpn2.getDpnId(),
                                                vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                extraRouteOptional, tx);
                                    }
                                }
                            }
                        }
                    }
                })));
        }
    }

    private List<BigInteger> checkDeleteLocalEvpnFLows(long vpnId, String rd, VrfEntry vrfEntry,
                                                       VpnNexthop localNextHopInfo) {
        List<BigInteger> returnLocalDpnId = new ArrayList<>();
        if (localNextHopInfo == null) {
            //Handle extra routes and imported routes
        } else {
            final BigInteger dpnId = localNextHopInfo.getDpnId();
            jobCoordinator.enqueueJob("FIB-" + rd + "-" + vrfEntry.getDestPrefix(),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    tx -> makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null, NwConstants.DEL_FLOW, tx,
                            null))));
            //TODO: verify below adjacency call need to be optimized (?)
            deleteLocalAdjacency(dpnId, vpnId, vrfEntry.getDestPrefix(), vrfEntry.getDestPrefix());
            returnLocalDpnId.add(dpnId);
        }
        return returnLocalDpnId;
    }
}
