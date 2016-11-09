/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
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
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId()
                + " has null vpnId!");
        Prefixes localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnInstance.getVpnId(),
                vrfEntry.getDestPrefix());
        Interface interfaceState = FibUtil.getInterfaceStateFromOperDS(dataBroker,
                localNextHopInfo.getVpnInterfaceName());
        final int lportTag = interfaceState.getIfIndex();
        List<BigInteger> localDpnId = createLocalEvpnFlows(vpnInstance.getVpnId(), rd, vrfEntry,
                localNextHopInfo, lportTag);
        createRemoteEvpnFlows(rd, localNextHopInfo, vrfEntry, vpnInstance, localDpnId, vrfTableKey, lportTag);
    }

    private List<BigInteger> createLocalEvpnFlows(long vpnId, String rd, VrfEntry vrfEntry, Prefixes localNextHopInfo,
                                                  int lportTag) {
        if (localNextHopInfo != null) {
            logger.info("Creating local EVPN flows for prefix {} rd {} nexthop {} evi {}.", vrfEntry.getDestPrefix(),
                    rd, vrfEntry.getNextHopAddressList(), vrfEntry.getL3vni());
            String localNextHopIP = vrfEntry.getDestPrefix();
            BigInteger dpnId = localNextHopInfo.getDpnId();
            final long groupId = vrfEntryListener.getNextHopManager().createLocalNextHop(vpnId, dpnId,
                    localNextHopInfo.getVpnInterfaceName(), localNextHopIP, vrfEntry.getDestPrefix());
            logger.debug("LocalNextHopGroup {} created/reused for prefix {} rd {} evi {} nexthop {}", groupId,
                    vrfEntry.getDestPrefix(), rd, vrfEntry.getL3vni(), vrfEntry.getNextHopAddressList());

            final List<InstructionInfo> instructions = Collections.singletonList(
                    new InstructionApplyActions(
                            Collections.singletonList(new ActionGroup(groupId))));
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB" + String.valueOf(vpnId) + dpnId.toString()
                    + vrfEntry.getDestPrefix(),
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
            return Arrays.asList(new BigInteger[] {dpnId});
        }
        return Arrays.asList(new BigInteger[] {BigInteger.ZERO});
    }

    private void createRemoteEvpnFlows(String rd, Prefixes localNextHopInfo, VrfEntry vrfEntry,
                           VpnInstanceOpDataEntry vpnInstance, List<BigInteger> localDpnId,
                                       VrfTablesKey vrfTableKey, int lportTag) {
        logger.info("Creating remote EVPN flows for prefix {} rd {} nexthop {} evi {}", vrfEntry.getDestPrefix(), rd,
                vrfEntry.getNextHopAddressList(), vrfEntry.getL3vni());
        Optional<Adjacency> adjacencyData =  FibUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                vrfEntryListener.getNextHopManager().getAdjacencyIdentifier(localNextHopInfo.getVpnInterfaceName(),
                        vrfEntry.getDestPrefix()));
        String macAddress = adjacencyData.get().getMacAddress();

        List<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB" + rd.toString() + vrfEntry.getDestPrefix(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                            for (VpnToDpnList vpnDpn : vpnToDpnList) {
                                if ( !localDpnId.contains(vpnDpn.getDpnId())) {
                                    if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                        createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(),
                                                vrfTableKey, vrfEntry, lportTag, macAddress, tx);
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

    private void createRemoteFibEntry(final BigInteger remoteDpnId, final long vpnId, final VrfTablesKey vrfTableKey,
                                      final VrfEntry vrfEntry, int lportTag, String macAddress, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }
        String rd = vrfTableKey.getRouteDistinguisher();
        logger.debug(  "createremotefibentry: adding route {} for rd {} with transaction {}",
                vrfEntry.getDestPrefix(), rd, tx);
        List<NexthopManager.AdjacencyResult> tunnelInterfaceList = vrfEntryListener
                .resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);

        if (tunnelInterfaceList.isEmpty()) {
            logger.error("Could not get interface for nexthop: {} in vpn {}",
                    vrfEntry.getNextHopAddressList(), rd);
            logger.warn("Failed to add Route: {} in vpn: {}",
                    vrfEntry.getDestPrefix(), rd);
            return;
        }

        for (NexthopManager.AdjacencyResult adjacencyResult : tunnelInterfaceList) {
            List<ActionInfo> actionInfos = new ArrayList<>();
            BigInteger tunnelId;
            if (vrfEntry.getOrigin().equals(RouteOrigin.BGP)) {
                tunnelId = BigInteger.valueOf(vrfEntry.getL3vni());
            } else {
                tunnelId = BigInteger.valueOf(lportTag);
            }

            logger.debug("adding set tunnel id action for label {}", tunnelId);
            actionInfos.add(new ActionSetFieldEthernetDestination(new MacAddress(macAddress)));
            actionInfos.add(new ActionSetFieldTunnelId(tunnelId));
            List<ActionInfo> egressActions = vrfEntryListener.getNextHopManager()
                    .getEgressActionsForInterface(adjacencyResult.getInterfaceName());
            if (egressActions.isEmpty()) {
                logger.error("Failed to retrieve egress action for prefix {} nextHop {} interface {}."
                        + " Aborting remote FIB entry creation..", vrfEntry.getDestPrefix(),
                        vrfEntry.getNextHopAddressList(), adjacencyResult.getInterfaceName());
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
        logger.debug("Successfully added FIB entry for prefix {} in vpnId {}", vrfEntry.getDestPrefix(), vpnId);
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
        List<BigInteger> localDpnId = deleteLocalEvpnFlow(vpnInstance.getVpnId(), rd, vrfEntry, localNextHopInfo);
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

                            if (localDpnIdList.size() <= 0) {
                                for (VpnToDpnList curDpn : vpnToDpnList) {
                                    if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                        if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                            vrfEntryListener.deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(),
                                                    vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                        }
                                    } else {
                                        vrfEntryListener.deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(),
                                                vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                    }
                                }
                            } else {
                                for (BigInteger localDpnId : localDpnIdList) {
                                    for (VpnToDpnList curDpn : vpnToDpnList) {
                                        if (!curDpn.getDpnId().equals(localDpnId)) {
                                            if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
                                                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                                    vrfEntryListener.deleteRemoteRoute(localDpnId, curDpn.getDpnId(),
                                                            vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
                                                }
                                            } else {
                                                vrfEntryListener.deleteRemoteRoute(localDpnId, curDpn.getDpnId(),
                                                        vpnInstance.getVpnId(), vrfTableKey, vrfEntry, tx);
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

    private List<BigInteger> deleteLocalEvpnFlow(long vpnId, String rd, VrfEntry vrfEntry,
                                                 VpnNexthop localNextHopInfo) {
        if (localNextHopInfo != null) {
            final BigInteger dpnId = localNextHopInfo.getDpnId();
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("FIB" + String.valueOf(vpnId) + dpnId.toString()
                    + vrfEntry.getDestPrefix(),
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
            return Arrays.asList(new BigInteger[] {dpnId});
        }
        return Arrays.asList(new BigInteger[] {BigInteger.ZERO});
    }
}
