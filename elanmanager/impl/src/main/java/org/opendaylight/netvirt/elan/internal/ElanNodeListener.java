/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.infrautils.utils.concurrent.LoggingFutures.addErrorLogging;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NwConstants.NxmOfFieldType;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.CopyFromValue;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.MatchFromField;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.MatchFromValue;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchArpOp;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderConstant;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg4;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanNodeListener extends AsyncDataTreeChangeListenerBase<Node, ElanNodeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanNodeListener.class);
    private static final int LEARN_MATCH_REG4_VALUE = 1;
    private static final int ARP_LEARN_FLOW_PRIORITY = 10;
    private static final int ARP_LEARN_MATCH_VALUE = 0x1;
    private static final int GARP_LEARN_MATCH_VALUE = 0x101;
    private static final long ARP_LEARN_MATCH_MASK = 0xFFFFL;

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManagerService;
    private final int tempSmacLearnTimeout;
    private final int arpPuntTimeout;
    private final boolean puntLldpToController;
    private final JobCoordinator jobCoordinator;
    private final DataTreeEventCallbackRegistrar eventCallbacks;

    @Inject
    public ElanNodeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager, ElanConfig elanConfig,
            IdManagerService idManagerService, JobCoordinator jobCoordinator,
            DataTreeEventCallbackRegistrar eventCallbacks) {
        this.broker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.tempSmacLearnTimeout = elanConfig.getTempSmacLearnTimeout();
        this.arpPuntTimeout = elanConfig.getArpPuntTimeout().intValue();
        this.puntLldpToController = elanConfig.isPuntLldpToController();
        this.idManagerService = idManagerService;
        this.jobCoordinator = jobCoordinator;
        this.eventCallbacks = eventCallbacks;
    }

    @Override
    @PostConstruct
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
        addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION, tx -> {
                BigInteger dpId = new BigInteger(node[1]);
                createTableMissEntry(tx, dpId);
                createMulticastFlows(tx, dpId);
                createArpDefaultFlowsForArpCheckTable(dpId);
            }), LOG, "Error handling ELAN node addition for {}", add);
    }

    private void createArpDefaultFlowsForArpCheckTable(BigInteger dpId) {

        jobCoordinator.enqueueJob("ARP_CHECK_TABLE-" + dpId.toString(),
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                try {
                    LOG.debug("Received notification to install Arp Check Default entries for dpn {} ", dpId);
                    createArpRequestMatchFlows(dpId, tx);
                    createArpResponseMatchFlows(dpId, tx);
                    createArpPuntAndLearnFlow(dpId, tx);
                    addGarpLearnMatchFlow(dpId, tx);
                    addArpLearnMatchFlow(dpId, tx);
                } catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error programming ARP rules for dpn {}", dpId, e);
                }
            })));
    }

    public void createTableMissEntry(TypedWriteTransaction<Configuration> tx, BigInteger dpnId) {
        setupTableMissSmacFlow(tx, dpnId);
        setupTableMissDmacFlow(tx, dpnId);
        setupTableMissArpCheckFlow(tx, dpnId);
        setupTableMissApResponderFlow(tx, dpnId);
        setupExternalL2vniTableMissFlow(tx, dpnId);
    }

    private void createMulticastFlows(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
        createL2ControlProtocolDropFlows(tx, dpId);
        createMulticastPuntFlows(tx, dpId);
    }

    private void createMulticastPuntFlows(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
        if (puntLldpToController) {
            createLldpFlows(tx, dpId);
        }
    }

    private void createLldpFlows(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
        createLldpFlow(tx, dpId, ElanConstants.LLDP_DST_1, "LLDP dMac Table Flow 1");
        createLldpFlow(tx, dpId, ElanConstants.LLDP_DST_2, "LLDP dMac Table Flow 2");
        createLldpFlow(tx, dpId, ElanConstants.LLDP_DST_3, "LLDP dMac Table Flow 3");
    }

    private void createLldpFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpId, String dstMac,
            String flowName) {
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

        mdsalManager.addFlow(tx, lldpFlow);
    }

    private void setupTableMissSmacFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
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
        mdsalManager.addFlow(tx, flowEntity);

        addSmacBaseTableFlow(tx, dpId);
        addSmacLearnedTableFlow(tx, dpId);
    }

    private void addSmacBaseTableFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
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
        mdsalManager.addFlow(tx, doubleResubmitTable);
    }

    private void addSmacLearnedTableFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
        // T50 - match on Reg4 and goto T51
        List<MatchInfoBase> mkMatches = new ArrayList<>();
        mkMatches.add(new NxMatchRegister(NxmNxReg4.class, LEARN_MATCH_REG4_VALUE));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_DMAC_TABLE));
        String flowRef = new StringBuffer().append(NwConstants.ELAN_SMAC_TABLE).append(NwConstants.FLOWID_SEPARATOR)
                .append(LEARN_MATCH_REG4_VALUE).toString();
        FlowEntity flowEntity =
                MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_SMAC_TABLE, flowRef, 10, "ELAN sMac Table Reg4 Flow",
                        0, 0, ElanConstants.COOKIE_ELAN_KNOWN_SMAC.add(BigInteger.valueOf(LEARN_MATCH_REG4_VALUE)),
                        mkMatches, mkInstructions);
        mdsalManager.addFlow(tx, flowEntity);
    }

    private void setupTableMissDmacFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.ELAN_UNKNOWN_DMAC_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE,
                getTableMissFlowRef(NwConstants.ELAN_DMAC_TABLE), 0, "ELAN dMac Table Miss Flow", 0, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC, mkMatches, mkInstructions);

        mdsalManager.addFlow(tx, flowEntity);
    }

    private void setupExternalL2vniTableMissFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpnId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionNxResubmit(NwConstants
                        .LPORT_DISPATCHER_TABLE));
        List<InstructionInfo> instructions = Collections.singletonList(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                        getTableMissFlowRef(NwConstants.L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE), 0,
                        "External L2VNI Table Miss Flow", 0, 0,
                         ElanConstants.COOKIE_L2VNI_DEMUX, matches, instructions);
        mdsalManager.addFlow(tx, flowEntity);
    }


    private void createL2ControlProtocolDropFlows(TypedWriteTransaction<Configuration> tx, BigInteger dpId) {
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

        mdsalManager.addFlow(tx, flow);
    }

    private static String getTableMissFlowRef(long tableId) {
        return String.valueOf(tableId);
    }

    @Override
    protected ElanNodeListener getDataTreeChangeListener() {
        return ElanNodeListener.this;
    }

    private void setupTableMissApResponderFlow(TypedWriteTransaction<Configuration> tx, final BigInteger dpnId) {
        mdsalManager.addFlow(tx, ArpResponderUtil.getArpResponderTableMissFlow(dpnId));
    }

    private void setupTableMissArpCheckFlow(TypedWriteTransaction<Configuration> tx, BigInteger dpnId) {
        mdsalManager.addFlow(tx,
                MDSALUtil.buildFlowEntity(dpnId, NwConstants.ARP_CHECK_TABLE,
                        String.valueOf("L2.ELAN." + NwConstants.ARP_CHECK_TABLE), NwConstants.TABLE_MISS_PRIORITY,
                        ArpResponderConstant.DROP_FLOW_NAME.value(), 0, 0, NwConstants.COOKIE_ARP_RESPONDER,
                        new ArrayList<MatchInfo>(),
                        Collections.singletonList(new InstructionGotoTable(NwConstants.ELAN_BASE_TABLE))));
    }

    private void createArpRequestMatchFlows(BigInteger dpId, TypedReadWriteTransaction<Configuration> tx)
            throws ExecutionException, InterruptedException {
        long arpRequestGroupId = ArpResponderUtil.retrieveStandardArpResponderGroupId(idManagerService);
        List<BucketInfo> buckets = ArpResponderUtil.getDefaultBucketInfos(NwConstants.ARP_RESPONDER_TABLE);
        LOG.trace("Installing group flow on dpn {}", dpId);
        GroupEntity groupEntity =
            MDSALUtil.buildGroupEntity(dpId, arpRequestGroupId, ArpResponderConstant.GROUP_FLOW_NAME.value(),
                GroupTypes.GroupAll, buckets);
        mdsalManager.addGroup(tx, groupEntity);
        InstanceIdentifier<Group> groupIid = ElanUtils.getGroupInstanceid(dpId, arpRequestGroupId);
        if (tx.read(groupIid).get().isPresent()) {
            LOG.info("group {} is present in the config hence adding the flow", arpRequestGroupId);
            createArpRequestMatchFlowsForGroup(dpId, arpRequestGroupId,tx);
            return;
        }
        eventCallbacks.onAddOrUpdate(LogicalDatastoreType.CONFIGURATION,
                ElanUtils.getGroupInstanceid(dpId, arpRequestGroupId), (unused, newGroupId) -> {
                LOG.info("group {} added in the config", arpRequestGroupId);
                LoggingFutures.addErrorLogging(
                        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                            innerConfTx -> createArpRequestMatchFlowsForGroup(dpId, arpRequestGroupId,
                                    innerConfTx)),
                        LOG, "Error adding flow for the group {}",arpRequestGroupId);
                return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
            }, Duration.ofSeconds(5), iid -> LOG.error("arpRequestGroupId {} not found in Config datastore",
                        arpRequestGroupId));

    }

    private void createArpRequestMatchFlowsForGroup(BigInteger dpId, long arpRequestGroupId,
            TypedReadWriteTransaction<Configuration> tx) {
        FlowEntity arpReqArpCheckTbl = ArpResponderUtil.createArpDefaultFlow(dpId, NwConstants.ARP_CHECK_TABLE,
                NwConstants.ARP_REQUEST, () -> Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REQUEST), () ->
                        Arrays.asList(new ActionGroup(arpRequestGroupId),
                                new ActionNxResubmit(NwConstants.ARP_LEARN_TABLE_1),
                                new ActionNxResubmit(NwConstants.ARP_LEARN_TABLE_2),
                                new ActionNxResubmit(NwConstants.ELAN_BASE_TABLE)));
        LOG.trace("Invoking MDSAL to install Arp Rquest Match Flow for table {}", NwConstants.ARP_CHECK_TABLE);
        mdsalManager.addFlow(tx, arpReqArpCheckTbl);
    }

    private void createArpResponseMatchFlows(BigInteger dpId, TypedReadWriteTransaction<Configuration> tx) {
        FlowEntity arpRepArpCheckTbl = ArpResponderUtil.createArpDefaultFlow(dpId, NwConstants.ARP_CHECK_TABLE,
                NwConstants.ARP_REPLY, () -> Arrays.asList(MatchEthernetType.ARP, MatchArpOp.REPLY), () ->
                        Arrays.asList(new ActionNxResubmit(NwConstants.ARP_LEARN_TABLE_1),
                                new ActionNxResubmit(NwConstants.ARP_LEARN_TABLE_2),
                                new ActionNxResubmit(NwConstants.ELAN_BASE_TABLE)));
        LOG.trace("Invoking MDSAL to install  Arp Reply Match Flow for Table {} ", NwConstants.ARP_CHECK_TABLE);
        mdsalManager.addFlow(tx, arpRepArpCheckTbl);
    }

    private void createArpPuntAndLearnFlow(BigInteger dpId, TypedReadWriteTransaction<Configuration> tx) {
        LOG.debug("adding arp punt and learn entry in table {}", NwConstants.ARP_LEARN_TABLE_1);

        List<MatchInfo> matches = new ArrayList<>();
        List<ActionInfo> actions = new ArrayList<>();
        BigInteger cookie = new BigInteger("88880000", 16);

        matches.add(MatchEthernetType.ARP);
        actions.add(new ActionPuntToController());
        if (arpPuntTimeout != 0) {
            actions.add(new ActionLearn(0, arpPuntTimeout, ARP_LEARN_FLOW_PRIORITY, cookie, 0,
                    NwConstants.ARP_LEARN_TABLE_1, 0, 0,
                    Arrays.asList(
                            new MatchFromValue(NwConstants.ETHTYPE_ARP, NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                                    NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                            new MatchFromField(NxmOfFieldType.NXM_OF_ARP_OP.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_OP.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_OP.getFlowModHeaderLenInt()),
                            new MatchFromField(NxmOfFieldType.NXM_OF_ARP_TPA.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_TPA.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_TPA.getFlowModHeaderLenInt()),
                            new ActionLearn.MatchFromField(NxmOfFieldType.OXM_OF_METADATA.getType(),
                                    MetaDataUtil.METADATA_ELAN_TAG_OFFSET,
                                    NxmOfFieldType.OXM_OF_METADATA.getType(), MetaDataUtil.METADATA_ELAN_TAG_OFFSET,
                                    ElanConstants.ELAN_TAG_LENGTH),
                            new CopyFromValue(1, NxmOfFieldType.NXM_NX_REG4.getType(), 8))));

            actions.add(new ActionLearn(0, arpPuntTimeout, ARP_LEARN_FLOW_PRIORITY, cookie, 0,
                    NwConstants.ARP_LEARN_TABLE_2, 0, 0,
                    Arrays.asList(
                            new MatchFromValue(NwConstants.ETHTYPE_ARP, NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                                    NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                            new MatchFromField(NxmOfFieldType.NXM_OF_ARP_OP.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_OP.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_OP.getFlowModHeaderLenInt()),
                            new MatchFromField(NxmOfFieldType.NXM_OF_ARP_TPA.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_SPA.getType(),
                                    NxmOfFieldType.NXM_OF_ARP_TPA.getFlowModHeaderLenInt()),
                            new ActionLearn.MatchFromField(NxmOfFieldType.OXM_OF_METADATA.getType(),
                                    MetaDataUtil.METADATA_ELAN_TAG_OFFSET, NxmOfFieldType.OXM_OF_METADATA.getType(),
                                    MetaDataUtil.METADATA_ELAN_TAG_OFFSET, MetaDataUtil.METADATA_ELAN_TAG_BITLEN),
                            new CopyFromValue(1, NxmOfFieldType.NXM_NX_REG4.getType(), 8, 8))));
        }

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actions));
        String flowid = String.valueOf(NwConstants.ARP_LEARN_TABLE_1) + NwConstants.FLOWID_SEPARATOR + "arp.punt";
        FlowEntity flow = MDSALUtil.buildFlowEntity(dpId, NwConstants.ARP_LEARN_TABLE_1, flowid,
                NwConstants.TABLE_MISS_PRIORITY, "arp punt/learn flow", 0,
                0, cookie, matches, instructions);
        mdsalManager.addFlow(tx, flow);
    }

    private void addGarpLearnMatchFlow(BigInteger dpId, TypedReadWriteTransaction<Configuration> tx) {
        List<ActionInfo> actions = new ArrayList<>();
        List<MatchInfoBase> matches = new ArrayList<>();

        matches.add(MatchEthernetType.ARP);
        matches.add(new NxMatchRegister(NxmNxReg4.class, GARP_LEARN_MATCH_VALUE, ARP_LEARN_MATCH_MASK));

        actions.add(new ActionRegLoad(NxmNxReg4.class, 0, 31, 0));
        actions.add(new ActionPuntToController());
        actions.add(new ActionNxResubmit(NwConstants.ELAN_SMAC_LEARNED_TABLE));
        actions.add(new ActionNxResubmit(NwConstants.ELAN_SMAC_TABLE));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actions));
        String flowid = String.valueOf(NwConstants.ELAN_BASE_TABLE) + NwConstants.FLOWID_SEPARATOR + "garp.match";
        FlowEntity garpFlow = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_BASE_TABLE, flowid,
                NwConstants.DEFAULT_ARP_FLOW_PRIORITY, "GARP learn match flow", 0, 0,
                ElanConstants.COOKIE_ELAN_BASE_SMAC, matches, instructions);
        mdsalManager.addFlow(tx, garpFlow);
    }

    private void addArpLearnMatchFlow(BigInteger dpId, TypedReadWriteTransaction<Configuration> tx) {
        List<ActionInfo> actions = new ArrayList<>();
        List<MatchInfoBase> matches = new ArrayList<>();

        matches.add(MatchEthernetType.ARP);
        matches.add(new NxMatchRegister(NxmNxReg4.class, ARP_LEARN_MATCH_VALUE, ARP_LEARN_MATCH_MASK));

        actions.add(new ActionRegLoad(NxmNxReg4.class, 0, 31, 0));
        actions.add(new ActionNxResubmit(NwConstants.ELAN_SMAC_LEARNED_TABLE));
        actions.add(new ActionNxResubmit(NwConstants.ELAN_SMAC_TABLE));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actions));
        String flowid = String.valueOf(NwConstants.ELAN_BASE_TABLE) + NwConstants.FLOWID_SEPARATOR + "arp.match";
        FlowEntity arpFlow = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_BASE_TABLE, flowid,
                NwConstants.DEFAULT_ARP_FLOW_PRIORITY, "ARP learn match flow", 0, 0,
                ElanConstants.COOKIE_ELAN_BASE_SMAC, matches, instructions);
        mdsalManager.addFlow(tx, arpFlow);
    }

}
