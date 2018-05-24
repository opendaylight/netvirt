/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
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
    private final List<BigInteger> connectedDpnIds;
    private final VpnConfig vpnConfig;

    @Inject
    public VpnNodeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager, JobCoordinator jobCoordinator,
                           VpnConfig vpnConfig) {
        super(Node.class, VpnNodeListener.class);
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.jobCoordinator = jobCoordinator;
        this.connectedDpnIds = new CopyOnWriteArrayList<>();
        this.vpnConfig = vpnConfig;
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
        BigInteger dpId = MDSALUtil.getDpnIdFromNodeName(add.getId());
        if (!connectedDpnIds.contains(dpId)) {
            connectedDpnIds.add(dpId);
        }
        processNodeAdd(dpId);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
        BigInteger dpId = MDSALUtil.getDpnIdFromNodeName(del.getId());
        connectedDpnIds.remove(dpId);
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
    }

    public boolean isConnectedNode(BigInteger nodeId) {
        return nodeId != null && connectedDpnIds.contains(nodeId);
    }

    private void processNodeAdd(BigInteger dpId) {
        jobCoordinator.enqueueJob("VPNNODE-" + dpId.toString(),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                LOG.debug("Received notification to install TableMiss entries for dpn {} ", dpId);
                makeTableMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                makeL3IntfTblMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                makeSubnetRouteTableMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                makeIpv6SubnetRouteTableMissFlow(tx, dpId, NwConstants.ADD_FLOW);
                createTableMissForVpnGwFlow(tx, dpId);
                createL3GwMacArpFlows(tx, dpId);
                programTableMissForVpnVniDemuxTable(tx, dpId, NwConstants.ADD_FLOW);
            })), 3);
    }

    private void makeTableMissFlow(WriteTransaction writeFlowTx, BigInteger dpnId, int addOrRemove) {
        final BigInteger cookieTableMiss = new BigInteger("1030000", 16);
        // Instruction to goto L3 InterfaceTable
        List<InstructionInfo> instructions =
                Collections.singletonList(new InstructionGotoTable(NwConstants.L3_INTERFACE_TABLE));
        List<MatchInfo> matches = new ArrayList<>();
        FlowEntity flowEntityLfib = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_LFIB_TABLE,
            getTableMissFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "Table Miss", 0, 0, cookieTableMiss, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("Invoking MDSAL to install Table Miss Entry");
            mdsalManager.addFlowToTx(flowEntityLfib, writeFlowTx);
        } else {
            mdsalManager.removeFlowToTx(flowEntityLfib, writeFlowTx);
        }
    }

    private void makeL3IntfTblMissFlow(WriteTransaction writeFlowTx, BigInteger dpnId, int addOrRemove) {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<MatchInfo> matches = new ArrayList<>();
        final BigInteger cookieTableMiss = new BigInteger("1030000", 16);
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
            mdsalManager.addFlowToTx(flowEntityL3Intf, writeFlowTx);
        } else {
            mdsalManager.removeFlowToTx(flowEntityL3Intf, writeFlowTx);
        }
    }

    private void makeSubnetRouteTableMissFlow(WriteTransaction writeFlowTx, BigInteger dpnId, int addOrRemove) {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        int learnTimeout = vpnConfig.getSubnetRoutePuntTimeout().intValue();
        if (learnTimeout != 0) {
            actionsInfos.add(new ActionLearn(0, learnTimeout, VpnConstants.DEFAULT_FLOW_PRIORITY,
                    VpnConstants.COOKIE_SUBNETROUTE_TABLE_MISS, 0, NwConstants.L3_SUBNET_ROUTE_TABLE,
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
        String flowRef = getTableMissFlowRef(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef,
            NwConstants.TABLE_MISS_PRIORITY, "Subnet Route Table Miss", 0, 0,
                VpnConstants.COOKIE_SUBNETROUTE_TABLE_MISS, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.addFlowToTx(flowEntity, writeFlowTx);
        } else {
            mdsalManager.removeFlowToTx(flowEntity, writeFlowTx);
        }
    }

    private void makeIpv6SubnetRouteTableMissFlow(WriteTransaction writeFlowTx, BigInteger dpnId, int addOrRemove) {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        int learnTimeout = vpnConfig.getSubnetRoutePuntTimeout().intValue();
        if (learnTimeout != 0) {
            actionsInfos.add(new ActionLearn(0, learnTimeout, VpnConstants.DEFAULT_FLOW_PRIORITY,
                    VpnConstants.COOKIE_SUBNETROUTE_TABLE_MISS, 0, NwConstants.L3_SUBNET_ROUTE_TABLE,
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
        String flowRef = getIpv6SubnetRouteTableMissFlowId(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE,
                NwConstants.ETHTYPE_IPV6, NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, "IPv6 Subnet Route Table Miss", 0, 0,
                VpnConstants.COOKIE_SUBNETROUTE_TABLE_MISS, matches, instructions);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("makeIpv6SubnetRouteTableMissFlow: Install Ipv6 Subnet Route Table  Miss entries");
            mdsalManager.addFlowToTx(flowEntity, writeFlowTx);
        } else {
            mdsalManager.removeFlowToTx(flowEntity, writeFlowTx);
        }
    }

    private void programTableMissForVpnVniDemuxTable(WriteTransaction writeFlowTx, BigInteger dpnId, int addOrRemove) {
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionNxResubmit(NwConstants
                .LPORT_DISPATCHER_TABLE));
        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        List<MatchInfo> matches = new ArrayList<>();
        String flowRef = getTableMissFlowRef(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                NwConstants.TABLE_MISS_FLOW);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                flowRef, NwConstants.TABLE_MISS_PRIORITY, "VPN-VNI Demux Table Miss", 0, 0,
                new BigInteger("1080000", 16), matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.addFlowToTx(flowEntity, writeFlowTx);
        } else {
            mdsalManager.removeFlowToTx(flowEntity, writeFlowTx);
        }
    }

    private void createTableMissForVpnGwFlow(WriteTransaction writeFlowTx, BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<ActionInfo> actionsInfos =
                Collections.singletonList(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntityMissforGw = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_GW_MAC_TABLE,
            getTableMissFlowRef(dpId, NwConstants.L3_GW_MAC_TABLE, NwConstants.TABLE_MISS_FLOW),
            NwConstants.TABLE_MISS_PRIORITY, "L3 Gw Mac Table Miss", 0, 0, new BigInteger("1080000", 16), matches,
            instructions);
        LOG.trace("Invoking MDSAL to install L3 Gw Mac Table Miss Entry");
        mdsalManager.addFlowToTx(flowEntityMissforGw, writeFlowTx);
    }

    private void createL3GwMacArpFlows(WriteTransaction writeFlowTx, BigInteger dpId) {
        FlowEntity arpReqGwMacTbl = ArpResponderUtil.createArpDefaultFlow(dpId, NwConstants.L3_GW_MAC_TABLE,
                NwConstants.ARP_REQUEST, () -> Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REQUEST),
            () -> Collections.singletonList(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE)));
        LOG.trace("Invoking MDSAL to install Arp Rquest Match Flow for table {}", NwConstants.L3_GW_MAC_TABLE);
        mdsalManager.addFlowToTx(arpReqGwMacTbl, writeFlowTx);
        FlowEntity arpRepGwMacTbl = ArpResponderUtil.createArpDefaultFlow(dpId, NwConstants.L3_GW_MAC_TABLE,
                NwConstants.ARP_REPLY, () -> Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REPLY),
            () -> Collections.singletonList(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE)));
        LOG.trace("Invoking MDSAL to install  Arp Reply Match Flow for Table {} ", NwConstants.L3_GW_MAC_TABLE);
        mdsalManager.addFlowToTx(arpRepGwMacTbl, writeFlowTx);
    }

    private String getTableMissFlowRef(BigInteger dpnId, short tableId, int tableMiss) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + tableMiss
                + FLOWID_PREFIX;
    }

    private String getIpv6SubnetRouteTableMissFlowId(BigInteger dpnId, short tableId, int etherType, int tableMiss) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + etherType
                + NwConstants.FLOWID_SEPARATOR + tableMiss + FLOWID_PREFIX;
    }
}
