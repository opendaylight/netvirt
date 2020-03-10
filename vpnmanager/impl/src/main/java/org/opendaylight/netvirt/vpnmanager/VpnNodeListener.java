/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NwConstants.NxmOfFieldType;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpOp;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.AlivenessMonitorUtils;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.mac.entry.data.dpn.to.mac.entry.MacEntryInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnNodeListener extends AsyncClusteredDataTreeChangeListenerBase<Node, VpnNodeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnNodeListener.class);
    private static final String FLOWID_PREFIX = "L3.";

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final JobCoordinator jobCoordinator;
    private final List<Uint64> connectedDpnIds;
    private final VpnConfig vpnConfig;
    private final AlivenessMonitorService alivenessMonitorService;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnNodeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager, JobCoordinator jobCoordinator,
                           VpnConfig vpnConfig, AlivenessMonitorService alivenessMonitorService,
                           AlivenessMonitorUtils alivenessMonitorUtils, VpnUtil vpnUtil) {
        super(Node.class, VpnNodeListener.class);
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.jobCoordinator = jobCoordinator;
        this.connectedDpnIds = new CopyOnWriteArrayList<>();
        this.vpnConfig = vpnConfig;
        this.alivenessMonitorService = alivenessMonitorService;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    protected VpnNodeListener getDataTreeChangeListener() {
        return VpnNodeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {
        Uint64 dpId = MDSALUtil.getDpnIdFromNodeName(add.getId());
        if (!connectedDpnIds.contains(dpId)) {
            connectedDpnIds.add(dpId);
        }
        processNodeAdd(dpId);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
        Uint64 dpId = MDSALUtil.getDpnIdFromNodeName(del.getId());
        connectedDpnIds.remove(dpId);
        processNodeDisConnect(dpId);
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
    }

    public boolean isConnectedNode(Uint64 nodeId) {
        return nodeId != null && connectedDpnIds.contains(nodeId);
    }

    private void processNodeAdd(Uint64 dpId) {
        jobCoordinator.enqueueJob("VPNNODE-" + dpId.toString(),
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                LOG.debug("Received notification to install TableMiss entries for dpn {} ", dpId);
                makeTableMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                makeL3IntfTblMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                makeSubnetRouteTableMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                makeIpv6SubnetRouteTableMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                createTableMissForVpnGwFlow(tx, dpId);
                createL3GwMacArpFlows(tx, dpId);
                programTableMissForVpnVniDemuxTable(tx, dpId, NwConstants.ADD_FLOW);
                resumeArpMonitoringForMipsOnDpn(dpId);
            })), 3);
    }

    private void processNodeDisConnect(Uint64 dpId) {
        jobCoordinator.enqueueJob("VPNNODE-" + dpId.toString(), () -> {
            List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
            pauseArpMonitoringForMipsOnDpn(dpId);
            return futures;
        });
    }

    private void makeTableMissFlow(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId, int addOrRemove)
            throws ExecutionException, InterruptedException {
        final Uint64 cookieTableMiss = Uint64.valueOf("1030000", 16).intern();
        // Instruction to goto L3 InterfaceTable
        List<InstructionInfo> instructions =
                Collections.singletonList(new InstructionGotoTable(NwConstants.L3_INTERFACE_TABLE));
        List<MatchInfo> matches = new ArrayList<>();
        FlowEntity flowEntityLfib = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_LFIB_TABLE,
            getTableMissFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "Table Miss", 0, 0, cookieTableMiss, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Invoking MDSAL to install Table Miss Entry");
            mdsalManager.addFlow(confTx, flowEntityLfib);
        } else {
            mdsalManager.removeFlow(confTx, flowEntityLfib);
        }
    }

    private void makeL3IntfTblMissFlow(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
            int addOrRemove) throws ExecutionException, InterruptedException {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<MatchInfo> matches = new ArrayList<>();
        final Uint64 cookieTableMiss = Uint64.valueOf("1030000", 16).intern();
        // Instruction to goto L3 InterfaceTable

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        //instructions.add(new InstructionGotoTable(NwConstants.LPORT_DISPATCHER_TABLE));

        FlowEntity flowEntityL3Intf = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_INTERFACE_TABLE,
            getTableMissFlowRef(dpnId, NwConstants.L3_INTERFACE_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "L3 Interface Table Miss", 0, 0, cookieTableMiss,
            matches, instructions);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Invoking MDSAL to install L3 interface Table Miss Entries");
            mdsalManager.addFlow(confTx, flowEntityL3Intf);
        } else {
            mdsalManager.removeFlow(confTx, flowEntityL3Intf);
        }
    }

    private void makeSubnetRouteTableMissFlow(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
            int addOrRemove) throws ExecutionException, InterruptedException {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        int learnTimeout = vpnConfig.getSubnetRoutePuntTimeout().intValue();
        if (learnTimeout != 0) {
            actionsInfos.add(new ActionLearn(0, learnTimeout, VpnConstants.DEFAULT_FLOW_PRIORITY,
                    NwConstants.COOKIE_SUBNET_ROUTE_TABLE_MISS, 0, NwConstants.L3_SUBNET_ROUTE_TABLE,
                    0, 0,
                    Arrays.asList(
                            new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV4,
                                    NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                                    NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                            new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
                                    NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
                                    NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLenInt()),
                            new ActionLearn.MatchFromField(NxmOfFieldType.OXM_OF_METADATA.getType(),
                                    MetaDataUtil.METADATA_VPN_ID_OFFSET,
                                    NxmOfFieldType.OXM_OF_METADATA.getType(), MetaDataUtil.METADATA_VPN_ID_OFFSET,
                                    MetaDataUtil.METADATA_VPN_ID_BITLEN))));
        }

        instructions.add(new InstructionApplyActions(actionsInfos));
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        String flowRef = getSubnetRouteTableMissFlowRef(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE,
                NwConstants.ETHTYPE_IPV4, NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef,
            NwConstants.TABLE_MISS_PRIORITY, "Subnet Route Table Miss", 0, 0,
                NwConstants.COOKIE_SUBNET_ROUTE_TABLE_MISS, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.addFlow(confTx, flowEntity);
        } else {
            mdsalManager.removeFlow(confTx, flowEntity);
        }
    }

    private void makeIpv6SubnetRouteTableMissFlow(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
            int addOrRemove) throws ExecutionException, InterruptedException {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        int learnTimeout = vpnConfig.getSubnetRoutePuntTimeout().intValue();
        if (learnTimeout != 0) {
            actionsInfos.add(new ActionLearn(0, learnTimeout, VpnConstants.DEFAULT_FLOW_PRIORITY,
                    NwConstants.COOKIE_SUBNET_ROUTE_TABLE_MISS, 0, NwConstants.L3_SUBNET_ROUTE_TABLE,
                    0, 0,
                    Arrays.asList(
                            new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV6,
                                    NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                                    NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                            new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.NXM_NX_IPV6_DST.getType(),
                                    NwConstants.NxmOfFieldType.NXM_NX_IPV6_DST.getType(),
                                    NwConstants.NxmOfFieldType.NXM_NX_IPV6_DST.getFlowModHeaderLenInt()),
                            new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.OXM_OF_METADATA.getType(),
                                    MetaDataUtil.METADATA_VPN_ID_OFFSET,
                                    NwConstants.NxmOfFieldType.OXM_OF_METADATA.getType(),
                                    MetaDataUtil.METADATA_VPN_ID_OFFSET,
                                    MetaDataUtil.METADATA_VPN_ID_BITLEN))));
        }

        instructions.add(new InstructionApplyActions(actionsInfos));
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        String flowRef = getSubnetRouteTableMissFlowRef(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE,
                NwConstants.ETHTYPE_IPV6, NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, "IPv6 Subnet Route Table Miss", 0, 0,
                NwConstants.COOKIE_SUBNET_ROUTE_TABLE_MISS, matches, instructions);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("makeIpv6SubnetRouteTableMissFlow: Install Ipv6 Subnet Route Table Miss entry");
            mdsalManager.addFlow(confTx, flowEntity);
        } else {
            LOG.debug("makeIpv6SubnetRouteTableMissFlow: Remove Ipv6 Subnet Route Table Miss entry");
            mdsalManager.removeFlow(confTx, flowEntity);
        }
    }

    private void programTableMissForVpnVniDemuxTable(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
            int addOrRemove) throws ExecutionException, InterruptedException {
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionNxResubmit(NwConstants
                .LPORT_DISPATCHER_TABLE));
        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        List<MatchInfo> matches = new ArrayList<>();
        String flowRef = getTableMissFlowRef(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                flowRef, NwConstants.TABLE_MISS_PRIORITY, "VPN-VNI Demux Table Miss", 0, 0,
                Uint64.valueOf("1080000", 16).intern(), matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.addFlow(confTx, flowEntity);
        } else {
            mdsalManager.removeFlow(confTx, flowEntity);
        }
    }

    private void createTableMissForVpnGwFlow(TypedWriteTransaction<Configuration> confTx, Uint64 dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<ActionInfo> actionsInfos =
                Collections.singletonList(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntityMissforGw = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_GW_MAC_TABLE,
            getTableMissFlowRef(dpId, NwConstants.L3_GW_MAC_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "L3 Gw Mac Table Miss", 0, 0,
                Uint64.valueOf("1080000", 16).intern(), matches,
            instructions);
        LOG.trace("Invoking MDSAL to install L3 Gw Mac Table Miss Entry");
        mdsalManager.addFlow(confTx, flowEntityMissforGw);
    }

    private void createL3GwMacArpFlows(TypedWriteTransaction<Configuration> confTx, Uint64 dpId) {
        FlowEntity arpReqGwMacTbl = ArpResponderUtil.createArpDefaultFlow(dpId, NwConstants.L3_GW_MAC_TABLE,
                NwConstants.ARP_REQUEST, () -> Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REQUEST),
            () -> Collections.singletonList(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE)));
        LOG.trace("Invoking MDSAL to install Arp Rquest Match Flow for table {}", NwConstants.L3_GW_MAC_TABLE);
        mdsalManager.addFlow(confTx, arpReqGwMacTbl);
        FlowEntity arpRepGwMacTbl = ArpResponderUtil.createArpDefaultFlow(dpId, NwConstants.L3_GW_MAC_TABLE,
                NwConstants.ARP_REPLY, () -> Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REPLY),
            () -> Collections.singletonList(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE)));
        LOG.trace("Invoking MDSAL to install  Arp Reply Match Flow for Table {} ", NwConstants.L3_GW_MAC_TABLE);
        mdsalManager.addFlow(confTx, arpRepGwMacTbl);
    }

    private String getTableMissFlowRef(Uint64 dpnId, short tableId, int tableMiss) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + tableMiss
                + FLOWID_PREFIX;
    }

    private String getSubnetRouteTableMissFlowRef(Uint64 dpnId, short tableId, int etherType, int tableMiss) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + etherType
                + NwConstants.FLOWID_SEPARATOR + tableMiss + FLOWID_PREFIX;
    }

    private void pauseArpMonitoringForMipsOnDpn(Uint64 dpnId) {
        try {
            List<MacEntryInfo> macEntries = vpnUtil.getDpnToMacEntryInfo(broker, dpnId);
            if (macEntries.isEmpty()) {
                LOG.trace("No MIP-IPs on Dpn {} ", dpnId);
                return;
            }

            for (MacEntryInfo entry: macEntries) {
                MacEntry macEntry = getMacEntry(entry);
                java.util.Optional<Uint32> monitorIdOptional = alivenessMonitorUtils
                        .getMonitorIdFromInterface(macEntry);
                if (!monitorIdOptional.isPresent()) {
                    LOG.error("MonitorId is unavailable for interface {} IP {} mac-addr {}",
                            macEntry.getInterfaceName(), macEntry.getIpAddress(), macEntry.getMacAddress());
                    return;
                }
                MonitorPauseInputBuilder input = new MonitorPauseInputBuilder().setMonitorId(monitorIdOptional.get());

                ListenableFuture<RpcResult<MonitorPauseOutput>> result = alivenessMonitorService
                        .monitorPause(input.build());
                if (!result.get().isSuccessful()) {
                    LOG.warn("RPC Call to Pause alivenessMonitor for monitorId {}, IP {}, Interface {} returned with "
                                    + "Errors {}", result.get().getErrors(), monitorIdOptional.get(),
                            macEntry.getIpAddress(), macEntry.getInterfaceName());
                }
                LOG.trace("AlivenessMonitor paused successfully for IP {}, monitor-id {}, on DPN {} ",
                        entry.getPortFixedIp(), monitorIdOptional.get(), dpnId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("VpnNodeListener: Error in pauseArpMonitoringForMipsOnDpn for Dpn {} with exception", dpnId, e);
        }
    }

    private void resumeArpMonitoringForMipsOnDpn(Uint64 dpnId) {
        try {
            List<MacEntryInfo> macEntries = vpnUtil.getDpnToMacEntryInfo(broker, dpnId);
            if (macEntries.isEmpty()) {
                LOG.trace("No MIP-IPs on Dpn {} ", dpnId);
                return;
            }
            for (MacEntryInfo entry: macEntries) {
                MacEntry macEntry = getMacEntry(entry);
                java.util.Optional<Uint32> monitorIdOptional = alivenessMonitorUtils.getMonitorIdFromInterface(
                        macEntry);
                if (!monitorIdOptional.isPresent()) {
                    LOG.error("MonitorId is unavailable for interface {} IP {} mac-addr {}",
                            macEntry.getInterfaceName(), macEntry.getIpAddress(), macEntry.getMacAddress());
                    return;
                }
                MonitorUnpauseInputBuilder input =
                        new MonitorUnpauseInputBuilder().setMonitorId(monitorIdOptional.get());
                ListenableFuture<RpcResult<MonitorUnpauseOutput>> result = alivenessMonitorService
                        .monitorUnpause(input.build());
                if (!result.get().isSuccessful()) {
                    LOG.warn("RPC Call to resume alivenessMonitor for monitorId {}, IP {}, Interface {} returned with"
                                    + "Errors {}", result.get().getErrors(), monitorIdOptional.get(),
                            macEntry.getIpAddress(), macEntry.getInterfaceName());
                }
                LOG.trace("AlivenessMonitor resumed successfully for IP {}, monitor-id {}, on DPN {} ",
                        entry.getPortFixedIp(), monitorIdOptional.get(), dpnId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("VpnNodeListener: Error in resumeArpMonitoringForMipsOnDpn for Dpn {} with exception", dpnId, e);
        }
    }

    private MacEntry getMacEntry(MacEntryInfo entry) {
        InetAddress srcInetAddr = null;
        String portFixedIp = null;
        try {
            portFixedIp = entry.getPortFixedIp();
            srcInetAddr = InetAddress.getByName(portFixedIp);
        } catch (UnknownHostException e) {
            LOG.error("Error in getMacEntry for IP with exception", portFixedIp);
        }
        String vpnName = entry.getVpnName();
        String creationTime = entry.getCreationTime();
        String interfaceName = entry.getPortName();
        MacAddress srcMacAddress = MacAddress.getDefaultInstance(entry.getMacAddress());
        MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName, creationTime);
        return macEntry;
    }
}
