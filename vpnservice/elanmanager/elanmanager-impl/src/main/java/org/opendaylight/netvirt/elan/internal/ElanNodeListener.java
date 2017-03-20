/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanNodeListener extends AsyncDataTreeChangeListenerBase<Node, ElanNodeListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanNodeListener.class);
    private static final int LEARN_MATCH_REG4_VALUE = 1;

    private final DataBroker broker;
    private final IMdsalApiManager mdsalManager;
    private final int tempSmacLearnTimeout;
    private final boolean puntLldpToController;


    public ElanNodeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager, ElanConfig elanConfig) {
        this.broker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.tempSmacLearnTimeout = elanConfig.getTempSmacLearnTimeout();
        this.puntLldpToController = elanConfig.isPuntLldpToController();
    }

    @Override
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {
        NodeId nodeId = add.getId();
        String[] node = nodeId.getValue().split(":");
        if (node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        createTableMissEntry(dpId);
        createMulticastFlows(dpId);
    }

    public void createTableMissEntry(BigInteger dpnId) {
        setupTableMissSmacFlow(dpnId);
        setupTableMissDmacFlow(dpnId);
    }

    private void createMulticastFlows(BigInteger dpId) {
        createL2ControlProtocolDropFlows(dpId);
        createMulticastPuntFlows(dpId);
    }

    private void createMulticastPuntFlows(BigInteger dpId) {
        if (puntLldpToController) {
            createLldpFlows(dpId);
        }
    }

    private void createLldpFlows(BigInteger dpId) {
        createLldpFlow(dpId, ElanConstants.LLDP_DST_1, "LLDP dMac Table Flow 1");
        createLldpFlow(dpId, ElanConstants.LLDP_DST_2, "LLDP dMac Table Flow 2");
        createLldpFlow(dpId, ElanConstants.LLDP_DST_3, "LLDP dMac Table Flow 3");
    }

    private void createLldpFlow(BigInteger dpId, String dstMac, String flowName) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchEthernetType(ElanConstants.LLDP_ETH_TYPE));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(dstMac)));

        List<ActionInfo> listActionInfo = new ArrayList<>();
        listActionInfo.add(new ActionPuntToController());

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionApplyActions(listActionInfo));

        String flowId = dpId.toString() + NwConstants.ELAN_DMAC_TABLE + "lldp" + ElanConstants.LLDP_ETH_TYPE + dstMac;
        FlowEntity lldpFlow = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE, flowId, 16, flowName, 0, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC, mkMatches, mkInstructions);

        mdsalManager.installFlow(lldpFlow);
    }

    private void setupTableMissSmacFlow(BigInteger dpId) {
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        actionsInfos.add(new ActionLearn(0, tempSmacLearnTimeout, 0, ElanConstants.COOKIE_ELAN_LEARNED_SMAC, 0,
                NwConstants.ELAN_SMAC_LEARNED_TABLE, 0, 0,
                Arrays.asList(
                        new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getType(),
                                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getType(),
                                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getFlowModHeaderLenInt()),
                        new ActionLearn.MatchFromField(NwConstants.NxmOfFieldType.NXM_NX_REG1.getType(),
                                NwConstants.NxmOfFieldType.NXM_NX_REG1.getType(), ElanConstants.INTERFACE_TAG_LENGTH),
                        new ActionLearn.CopyFromValue(LEARN_MATCH_REG4_VALUE,
                                NwConstants.NxmOfFieldType.NXM_NX_REG4.getType(), 8))));

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionApplyActions(actionsInfos));
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));

        List<MatchInfo> mkMatches = new ArrayList<>();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_SMAC_TABLE,
                getTableMissFlowRef(NwConstants.ELAN_SMAC_TABLE), 0, "ELAN sMac Table Miss Flow", 0, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_SMAC, mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);

        addSmacBaseTableFlow(dpId);
        addSmacLearnedTableFlow(dpId);
    }

    private void addSmacBaseTableFlow(BigInteger dpId) {
        // T48 - resubmit to T49 & T50
        List<ActionInfo> actionsInfo = new ArrayList<>();
        actionsInfo.add(new ActionNxResubmit(NwConstants.ELAN_SMAC_LEARNED_TABLE));
        actionsInfo.add(new ActionNxResubmit(NwConstants.ELAN_SMAC_TABLE));
        List<InstructionInfo> mkInstruct = new ArrayList<>();
        mkInstruct.add(new InstructionApplyActions(actionsInfo));
        List<MatchInfo> mkMatch = new ArrayList<>();
        FlowEntity doubleResubmitTable = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_BASE_TABLE,
                getTableMissFlowRef(NwConstants.ELAN_BASE_TABLE), 0, "Elan sMac resubmit table", 0, 0,
                ElanConstants.COOKIE_ELAN_BASE_SMAC, mkMatch, mkInstruct);
        mdsalManager.installFlow(doubleResubmitTable);
    }

    private void addSmacLearnedTableFlow(BigInteger dpId) {
        // T50 - match on Reg4 and goto T51
        List<MatchInfoBase> mkMatches = new ArrayList<>();
        mkMatches.add(new NxMatchInfo(NxMatchFieldType.nxm_reg_4, new long[] { Long.valueOf(LEARN_MATCH_REG4_VALUE) }));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        String flowRef = new StringBuffer().append(NwConstants.ELAN_SMAC_TABLE).append(NwConstants.FLOWID_SEPARATOR)
                .append(LEARN_MATCH_REG4_VALUE).toString();
        FlowEntity flowEntity =
                MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_SMAC_TABLE, flowRef, 10, "ELAN sMac Table Reg4 Flow",
                        0, 0, ElanConstants.COOKIE_ELAN_KNOWN_SMAC.add(BigInteger.valueOf(LEARN_MATCH_REG4_VALUE)),
                        mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);
    }

    private void setupTableMissDmacFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_UNKNOWN_DMAC_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE,
                getTableMissFlowRef(NwConstants.ELAN_DMAC_TABLE), 0, "ELAN dMac Table Miss Flow", 0, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC, mkMatches, mkInstructions);

        mdsalManager.installFlow(flowEntity);
    }

    private void createL2ControlProtocolDropFlows(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        MatchEthernetDestination matchEthDst =
                new MatchEthernetDestination(new MacAddress(ElanConstants.L2_CONTROL_PACKETS_DMAC),
                        new MacAddress(ElanConstants.L2_CONTROL_PACKETS_DMAC_MASK));

        mkMatches.add(matchEthDst);

        List<ActionInfo> listActionInfo = new ArrayList<>();
        listActionInfo.add(new ActionDrop());

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionApplyActions(listActionInfo));

        String flowId = dpId.toString() + NwConstants.ELAN_DMAC_TABLE + "l2control"
                + ElanConstants.L2_CONTROL_PACKETS_DMAC + ElanConstants.L2_CONTROL_PACKETS_DMAC_MASK;
        FlowEntity flow = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE, flowId, 15,
                "L2 control packets dMac Table Flow", 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC, mkMatches,
                mkInstructions);

        mdsalManager.installFlow(flow);
    }

    private String getTableMissFlowRef(long tableId) {
        return new StringBuffer().append(tableId).toString();
    }

    @Override
    protected ElanNodeListener getDataTreeChangeListener() {
        // TODO Auto-generated method stub
        return ElanNodeListener.this;
    }

}
