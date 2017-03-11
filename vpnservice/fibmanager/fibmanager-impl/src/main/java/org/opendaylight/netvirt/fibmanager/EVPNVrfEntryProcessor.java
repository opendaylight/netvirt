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
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.ExtraRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.ExtraRoutesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.RoutesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EVPNVrfEntryProcessor {
    private final Logger logger = LoggerFactory.getLogger(EVPNVrfEntryProcessor.class);
    private VrfEntry vrfEntry;
    private VrfEntry updatedVrfEntry;
    private InstanceIdentifier<VrfEntry> identifier;
    private DataBroker dataBroker;
    private VrfEntryListener vrfEntryListener;
    private static HashMap<BigInteger, Prefixes> adjMap = new HashMap<>();

    EVPNVrfEntryProcessor(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, DataBroker broker,
                          VrfEntryListener vrfEntryListener) {
        this.identifier = identifier;
        this.vrfEntry = vrfEntry;
        this.dataBroker = broker;
        this.vrfEntryListener = vrfEntryListener;
    }

    EVPNVrfEntryProcessor(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update,
                          DataBroker broker, VrfEntryListener vrfEntryListener) {
        this(identifier, original, broker, vrfEntryListener);
        this.updatedVrfEntry = update;
    }

    public void installFlows() {
        logger.info("Initiating creation of Evpn Flows");
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final String rd = vrfTableKey.getRouteDistinguisher();
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstanceOpData(dataBroker,
                vrfTableKey.getRouteDistinguisher()).get();
        Long vpnId = vpnInstance.getVpnId();
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnId, "Vpn Instance with rd " + vpnInstance.getVrfId()
                + " has null vpnId!");
        if (RouteOrigin.valueOf(vrfEntry.getOrigin()).equals(RouteOrigin.CONNECTED)) {
            SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
            final List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
            final long elanTag = subnetRoute.getElantag();
            logger.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                    rd, vrfEntry.getDestPrefix(), elanTag);
            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("FIB-" + rd + "-" + vrfEntry.getDestPrefix(),
                        () -> {
                            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                            for (final VpnToDpnList curDpn : vpnToDpnList) {
                                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                    vrfEntryListener.installSubnetRouteInFib(curDpn.getDpnId(), elanTag, rd,
                                            vpnId, vrfEntry, tx);
                                }
                            }
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(tx.submit());
                            return futures;
                        });
            }
            return;
        }
        Prefixes localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnInstance.getVpnId(),
                vrfEntry.getDestPrefix());
        List<BigInteger> localDpnId = new ArrayList<>();
        boolean isNatPrefix = false;
        if (Boolean.TRUE.equals(localNextHopInfo.isNatPrefix())) {
            logger.info("NAT Prefix {} with vpnId {} rd {}. Skip local dpn {} FIB processing",
                    vrfEntry.getDestPrefix(), vpnId, rd, localNextHopInfo.getDpnId());
            localDpnId.add(localNextHopInfo.getDpnId());
            isNatPrefix = true;
        } else {
            localDpnId = createLocalEvpnFlows(vpnInstance.getVpnId(), rd, vrfEntry,
                    localNextHopInfo);
        }
        createRemoteEvpnFlows(rd, vrfEntry, vpnInstance, localDpnId, vrfTableKey, isNatPrefix);
    }

    private List<BigInteger> createLocalEvpnFlows(long vpnId, String rd, VrfEntry vrfEntry,
                                                  Prefixes localNextHopInfo) {
        List<BigInteger> returnLocalDpnId = new ArrayList<>();
        String localNextHopIP = vrfEntry.getDestPrefix();
        if (localNextHopInfo == null) {
            //Handle extra routes and imported routes
            Routes extraRoute = vrfEntryListener.getVpnToExtraroute(rd, vrfEntry.getDestPrefix());
            if (extraRoute != null) {
                for (String nextHopIp : extraRoute.getNexthopIpList()) {
                    logger.info("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                    if (nextHopIp != null) {
                        localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, nextHopIp + "/32");
                        if (localNextHopInfo != null) {
                            String localNextHopIP = nextHopIp + "/32";
                            BigInteger dpnId = checkCreateLocalEvpnFlows(localNextHopInfo, localNextHopIP, vpnId,
                                    rd, vrfEntry);
                            returnLocalDpnId.add(dpnId);
                        }
                    }
                }
            }
        } else {
            logger.info("Creating local EVPN flows for prefix {} rd {} route-paths {} evi {}.",
                    vrfEntry.getDestPrefix(), rd, vrfEntry.getRoutePaths(), vrfEntry.getL3vni());
            BigInteger dpnId = checkCreateLocalEvpnFlows(localNextHopInfo, localNextHopIP, vpnId,
                    rd, vrfEntry);
            returnLocalDpnId.add(dpnId);
            adjMap.put(dpnId, localNextHopInfo);
        }
        return returnLocalDpnId;
    }

    private BigInteger checkCreateLocalEvpnFlows(Prefixes localNextHopInfo, String localNextHopIP,
                                                final Long vpnId, final String rd,
                                                final VrfEntry vrfEntry) {
        final BigInteger dpnId = localNextHopInfo.getDpnId();
        String jobKey = "FIB-" + vpnId.toString() + "-" + dpnId.toString() + "-" + vrfEntry.getDestPrefix();
        final long groupId = vrfEntryListener.getNextHopManager().createLocalNextHop(vpnId, dpnId,
                localNextHopInfo.getVpnInterfaceName(), localNextHopIP, vrfEntry.getDestPrefix(),
                vrfEntry.getGatewayMacAddress(), jobKey);
        logger.debug("LocalNextHopGroup {} created/reused for prefix {} rd {} evi {} route-paths {}", groupId,
                vrfEntry.getDestPrefix(), rd, vrfEntry.getL3vni(), vrfEntry.getRoutePaths());

        final List<InstructionInfo> instructions = Collections.singletonList(
                new InstructionApplyActions(
                        Collections.singletonList(new ActionGroup(groupId))));
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("FIB-" + vpnId.toString() + "-" + dpnId.toString()
                + "-" + vrfEntry.getDestPrefix(),
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {
                        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                        vrfEntryListener.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions,
                                NwConstants.ADD_FLOW, tx);
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(tx.submit());
                        return futures;
                    }
                });
        return dpnId;
    }

    private void createRemoteEvpnFlows(String rd, VrfEntry vrfEntry, VpnInstanceOpDataEntry vpnInstance,
                                       List<BigInteger> localDpnId, VrfTablesKey vrfTableKey, boolean isNatPrefix) {
        logger.info("Creating remote EVPN flows for prefix {} rd {} route-paths {} evi {}",
                vrfEntry.getDestPrefix(), rd, vrfEntry.getRoutePaths(), vrfEntry.getL3vni());
        List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            logger.debug("DJC {}", dataStoreCoordinator);
            dataStoreCoordinator.enqueueJob("FIB" + rd.toString() + vrfEntry.getDestPrefix(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            for (VpnToDpnList vpnDpn : vpnToDpnList) {
                                if (!localDpnId.contains(vpnDpn.getDpnId())) {
                                    if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                        createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(),
                                                vrfTableKey, vrfEntry, isNatPrefix, tx);
                                    }
                                }
                            }
                            futures.add(tx.submit());
                            return futures;
                        }
                    });
        }
    }

    private void createRemoteFibEntry(final BigInteger remoteDpnId, final long vpnId, final VrfTablesKey vrfTableKey,
                                      final VrfEntry vrfEntry, boolean isNatPrefix, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }
        String rd = vrfTableKey.getRouteDistinguisher();
        logger.debug("createremotefibentry: adding route {} for rd {} with transaction {}",
                vrfEntry.getDestPrefix(), rd, tx);
        List<NexthopManager.AdjacencyResult> tunnelInterfaceList = vrfEntryListener
                .resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);

        if (tunnelInterfaceList.isEmpty()) {
            logger.error("Could not get interface for route-paths: {} in vpn {}",
                    vrfEntry.getRoutePaths(), rd);
            logger.warn("Failed to add Route: {} in vpn: {}",
                    vrfEntry.getDestPrefix(), rd);
            return;
        }

        for (NexthopManager.AdjacencyResult adjacencyResult : tunnelInterfaceList) {
            List<ActionInfo> actionInfos = new ArrayList<>();
            BigInteger tunnelId;
            String prefix = adjacencyResult.getPrefix();
            Prefixes prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, prefix);
            String interfaceName = prefixInfo.getVpnInterfaceName();
            if (vrfEntry.getOrigin().equals(RouteOrigin.BGP) || isNatPrefix) {
                tunnelId = BigInteger.valueOf(vrfEntry.getL3vni());
            } else {
                Interface interfaceState = FibUtil.getInterfaceStateFromOperDS(dataBroker, interfaceName);
                tunnelId = BigInteger.valueOf(interfaceState.getIfIndex());
            }
            logger.debug("adding set tunnel id action for label {}", tunnelId);
            String macAddress = FibUtil.getMacAddressFromPrefix(dataBroker, interfaceName, prefix);
            actionInfos.add(new ActionSetFieldEthernetDestination(new MacAddress(macAddress)));
            actionInfos.add(new ActionSetFieldTunnelId(tunnelId));
            List<ActionInfo> egressActions = vrfEntryListener.getNextHopManager()
                    .getEgressActionsForInterface(adjacencyResult.getInterfaceName(), actionInfos.size());
            if (egressActions.isEmpty()) {
                logger.error("Failed to retrieve egress action for prefix {} route-paths {} interface {}."
                        + " Aborting remote FIB entry creation..", vrfEntry.getDestPrefix(),
                        vrfEntry.getRoutePaths(), adjacencyResult.getInterfaceName());
                return;
            }
            actionInfos.addAll(egressActions);
            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionApplyActions(actionInfos));
            vrfEntryListener.makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions,
                    NwConstants.ADD_FLOW, tx);
        }
        if (!wrTxPresent) {
            tx.submit();
        }
        logger.debug("Successfully added FIB entry for prefix {} in rd {}", vrfEntry.getDestPrefix(), rd);
    }

    public void removeFlows() {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final String rd  = vrfTableKey.getRouteDistinguisher();
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstanceOpData(dataBroker,
                vrfTableKey.getRouteDistinguisher()).get();
        if (vpnInstance == null) {
            logger.error("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        VpnNexthop localNextHopInfo = vrfEntryListener.getNextHopManager().getVpnNexthop(vpnInstance.getVpnId(),
                vrfEntry.getDestPrefix());
        List<BigInteger> localDpnId = checkDeleteLocalEvpnFLows(vpnInstance.getVpnId(), rd, vrfEntry, localNextHopInfo);
        deleteRemoteEvpnFlows(rd, vpnInstance, vrfTableKey, localDpnId);
        vrfEntryListener.cleanUpOpDataForFib(vpnInstance.getVpnId(), rd, vrfEntry);
    }

    private void deleteRemoteEvpnFlows(String rd, VpnInstanceOpDataEntry vpnInstance, VrfTablesKey vrfTableKey,
                                       List<BigInteger> localDpnIdList) {
        List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB" + rd.toString() + vrfEntry.getDestPrefix(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                            final Optional<Routes> extraRouteOptional = Optional.absent();
                            if (localDpnIdList.size() <= 0) {
                                for (VpnToDpnList curDpn : vpnToDpnList) {
                                    if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                        if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                            vrfEntryListener.deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(),
                                                    vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                    extraRouteOptional, tx);
                                        }
                                    } else {
                                        vrfEntryListener.deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(),
                                                vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                extraRouteOptional, tx);
                                    }
                                }
                            } else {
                                for (BigInteger localDpnId : localDpnIdList) {
                                    for (VpnToDpnList curDpn : vpnToDpnList) {
                                        if (!curDpn.getDpnId().equals(localDpnId)) {
                                            if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                                    vrfEntryListener.deleteRemoteRoute(localDpnId, curDpn.getDpnId(),
                                                            vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                            extraRouteOptional, tx);
                                                }
                                            } else {
                                                vrfEntryListener.deleteRemoteRoute(localDpnId, curDpn.getDpnId(),
                                                        vpnInstance.getVpnId(), vrfTableKey, vrfEntry,
                                                        extraRouteOptional, tx);
                                            }
                                        }
                                    }
                                }
                            }
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(tx.submit());
                            return futures;
                        }
                    });
        }
    }

    private List<BigInteger> checkDeleteLocalEvpnFLows(long vpnId, String rd, VrfEntry vrfEntry,
                                                 VpnNexthop localNextHopInfo) {
        List<BigInteger> returnLocalDpnId = new ArrayList<>();
        if (localNextHopInfo == null) {
            //Handle extra routes and imported routes
        } else {
            final BigInteger dpnId = localNextHopInfo.getDpnId();
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB-" + rd + "-" + vrfEntry.getDestPrefix(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                            vrfEntryListener.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null /* instructions */,
                                    NwConstants.DEL_FLOW, tx);
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            futures.add(tx.submit());
                            return futures;
                        }
                    });
            //TODO: verify below adjacency call need to be optimized (?)
            vrfEntryListener.deleteLocalAdjacency(dpnId, vpnId, vrfEntry.getDestPrefix(), vrfEntry.getDestPrefix());
            returnLocalDpnId.add(dpnId);
        }
        return returnLocalDpnId;
    }

    static  InstanceIdentifier<Routes> getVpnToExtrarouteIdentifier(String vpnName, String vrfId, String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroutes.class)
                .child(Vpn.class, new VpnKey(vpnName)).child(ExtraRoutes.class,
                        new ExtraRoutesKey(vrfId)).child(Routes.class, new RoutesKey(ipPrefix)).build();
    }

    public Routes getVpnToExtraroute(String vpnRd, String destPrefix) {
        Optional<String> optVpnName = FibUtil.getVpnNameFromRd(dataBroker, vpnRd);
        if (optVpnName.isPresent()) {
            InstanceIdentifier<Routes> vpnExtraRoutesId = getVpnToExtrarouteIdentifier(
                    optVpnName.get(), vpnRd, destPrefix);
            return FibUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId).orNull();
        }
        return null;
    }
}
