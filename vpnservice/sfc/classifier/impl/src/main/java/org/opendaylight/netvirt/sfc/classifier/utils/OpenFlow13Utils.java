/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.classifier.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg0;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.ExtensionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.GeneralAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.GeneralAugMatchNodesNodeTableFlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.grouping.ExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.list.grouping.ExtensionList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.list.grouping.ExtensionListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.DstChoice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshMdtypeCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshNpCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc1CaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc2CaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNshc4CaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNsiCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxNspCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxTunIdCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxTunIpv4DstCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.group.buckets.bucket.action.action.NxActionRegLoadNodesNodeGroupBucketsBucketActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.group.buckets.bucket.action.action.NxActionRegMoveNodesNodeGroupBucketsBucketActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionPopNshNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionPushNshNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegMoveNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.write.actions._case.write.actions.action.action.NxActionResubmitNodesNodeTableFlowWriteActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.pop.nsh.grouping.NxPopNsh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.pop.nsh.grouping.NxPopNshBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.push.nsh.grouping.NxPushNsh;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.push.nsh.grouping.NxPushNshBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.Dst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.move.grouping.NxRegMove;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.move.grouping.NxRegMoveBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.move.grouping.nx.reg.move.SrcBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmitBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.SrcChoice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxNshc4CaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxRegCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.src.choice.grouping.src.choice.SrcNxTunIdCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxAugMatchNodesNodeTableFlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxEncapEthTypeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxNshc1Key;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxNshc2Key;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxNsiKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxNspKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxTunGpeNpKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxmNxTunIdKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.encap.eth.type.grouping.NxmNxEncapEthTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.nshc._1.grouping.NxmNxNshc1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.nshc._2.grouping.NxmNxNshc2Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.nsi.grouping.NxmNxNsiBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.nsp.grouping.NxmNxNspBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.tun.gpe.np.grouping.NxmNxTunGpeNpBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.nxm.nx.tun.id.grouping.NxmNxTunIdBuilder;

public final class OpenFlow13Utils {
    public static final int ETHERTYPE_NSH = 0x894f;
    public static final short TUN_GPE_NP_NSH = 0x4;

    private OpenFlow13Utils() {
    }

    public static MatchBuilder getNspMatch(long nsp) {
        MatchBuilder mb = new MatchBuilder();
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
            .setNxmNxNsp(new NxmNxNspBuilder().setValue(nsp).build()).build();
        addExtension(mb, NxmNxNspKey.class, am);

        return mb;
    }

    public static void addMatchVxgpeNsh(MatchBuilder match) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
                .setNxmNxTunGpeNp(new NxmNxTunGpeNpBuilder().setValue(TUN_GPE_NP_NSH).build()).build();
        addExtension(match, NxmNxTunGpeNpKey.class, am);
    }

    public static void addMatchTunId(MatchBuilder match, long value) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
                .setNxmNxTunId(new NxmNxTunIdBuilder().setValue(BigInteger.valueOf(value)).build()).build();
        addExtension(match, NxmNxTunIdKey.class, am);
    }

    public static void addMatchEthNsh(MatchBuilder match) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
                .setNxmNxEncapEthType(new NxmNxEncapEthTypeBuilder().setValue(ETHERTYPE_NSH).build()).build();
        addExtension(match, NxmNxEncapEthTypeKey.class, am);
    }

    public static void addMatchInPort(MatchBuilder match, NodeId nodeId, long inPort) {
        match.setInPort(new NodeConnectorId(nodeId.getValue() + ":" + inPort));
    }

    public static void addMatchNsp(MatchBuilder match, long nsp) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
                .setNxmNxNsp(new NxmNxNspBuilder().setValue(nsp).build()).build();
        addExtension(match, NxmNxNspKey.class, am);
    }

    public static void addMatchNsi(MatchBuilder match, short nsi) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
                .setNxmNxNsi(new NxmNxNsiBuilder().setNsi(nsi).build()).build();
        addExtension(match, NxmNxNsiKey.class, am);
    }

    public static void addMatchNshNsc1(MatchBuilder match, long nsc) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
            .setNxmNxNshc1(new NxmNxNshc1Builder().setValue(nsc).build()).build();
        addExtension(match, NxmNxNshc1Key.class, am);
    }

    public static void addMatchNshNsc2(MatchBuilder match, long nsc) {
        NxAugMatchNodesNodeTableFlow am = new NxAugMatchNodesNodeTableFlowBuilder()
            .setNxmNxNshc2(new NxmNxNshc2Builder().setValue(nsc).build()).build();
        addExtension(match, NxmNxNshc2Key.class, am);
    }

    private static void addExtension(MatchBuilder match, Class<? extends ExtensionKey> extensionKey,
                                     NxAugMatchNodesNodeTableFlow am) {
        GeneralAugMatchNodesNodeTableFlow existingAugmentations = match
            .getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);
        List<ExtensionList> extensions = null;
        if (existingAugmentations != null) {
            extensions = existingAugmentations.getExtensionList();
        }
        if (extensions == null) {
            extensions = new ArrayList<>();
        }

        extensions.add(new ExtensionListBuilder().setExtensionKey(extensionKey)
            .setExtension(new ExtensionBuilder().addAugmentation(NxAugMatchNodesNodeTableFlow.class, am).build())
            .build());

        GeneralAugMatchNodesNodeTableFlow generalAugMatchNodesNode = new GeneralAugMatchNodesNodeTableFlowBuilder()
            .setExtensionList(extensions).build();
        match.addAugmentation(GeneralAugMatchNodesNodeTableFlow.class, generalAugMatchNodesNode);
    }

    public static Action createActionResubmitTable(final short toTable, int order) {
        return createActionBuilder(order)
                .setAction(new NxActionResubmitNodesNodeTableFlowWriteActionsCaseBuilder()
                        .setNxResubmit(new NxResubmitBuilder()
                                .setTable(toTable)
                                .build())
                        .build())
                .build();
    }

    public static Action createActionNxLoadTunIpv4Dst(long value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxTunIpv4DstCaseBuilder().setNxTunIpv4Dst(Boolean.TRUE).build(),
                BigInteger.valueOf(value), 31, false));

        return ab.build();
    }

    public static Action createActionNxLoadTunId(long value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxTunIdCaseBuilder().setNxTunId(Boolean.TRUE).build(),
                BigInteger.valueOf(value), 31, false));

        return ab.build();
    }

    public static Action createActionNxLoadNshMdtype(short value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxNshMdtypeCaseBuilder().setNxNshMdtype(Boolean.TRUE).build(),
            BigInteger.valueOf(value), 7, false));

        return ab.build();
    }

    public static Action createActionNxLoadNp(short value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxNshNpCaseBuilder().setNxNshNp(Boolean.TRUE).build(),
                BigInteger.valueOf(value), 7, false));

        return ab.build();
    }

    public static Action createActionNxLoadNsp(int value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxNspCaseBuilder().setNxNspDst(Boolean.TRUE).build(),
            BigInteger.valueOf(value), 23, false));

        return ab.build();
    }

    public static Action createActionNxLoadNsi(short value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxNsiCaseBuilder().setNxNsiDst(Boolean.TRUE).build(),
            BigInteger.valueOf(value), 7, false));

        return ab.build();
    }

    public static Action createActionNxLoadNshc1(long value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxNshc1CaseBuilder().setNxNshc1Dst(Boolean.TRUE).build(),
            BigInteger.valueOf(value), 31, false));

        return ab.build();
    }

    public static Action createActionNxLoadNshc2(long value, int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxLoadRegAction(new DstNxNshc2CaseBuilder().setNxNshc2Dst(Boolean.TRUE).build(),
            BigInteger.valueOf(value), 31, false));

        return ab.build();
    }

    public static Action createActionNxPushNsh(int order) {
        NxPushNshBuilder builder = new NxPushNshBuilder();
        NxPushNsh nxPushNsh = builder.build();

        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(new NxActionPushNshNodesNodeTableFlowApplyActionsCaseBuilder().setNxPushNsh(nxPushNsh).build());

        return ab.build();
    }

    public static Action createActionNxPopNsh(int order) {
        NxPopNshBuilder builder = new NxPopNshBuilder();
        NxPopNsh nxPopNsh = builder.build();

        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(new NxActionPopNshNodesNodeTableFlowApplyActionsCaseBuilder().setNxPopNsh(nxPopNsh).build());

        return ab.build();
    }

    public static Action createActionNxMoveTunIdToNsc2Register(int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxMoveRegAction(new SrcNxTunIdCaseBuilder().setNxTunId(Boolean.TRUE).build(),
            new DstNxNshc2CaseBuilder().setNxNshc2Dst(Boolean.TRUE).build(), 31, false));

        return ab.build();
    }

    public static Action createActionNxMoveReg0ToNsc1Register(int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxMoveRegAction(new SrcNxRegCaseBuilder().setNxReg(NxmNxReg0.class).build(),
                new DstNxNshc1CaseBuilder().setNxNshc1Dst(Boolean.TRUE).build(), 31, false));

        return ab.build();
    }

    public static Action createActionNxMoveReg6ToNsc4Register(int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxMoveRegAction(new SrcNxRegCaseBuilder().setNxReg(NxmNxReg6.class).build(),
                new DstNxNshc4CaseBuilder().setNxNshc4Dst(Boolean.TRUE).build(), 31, false));

        return ab.build();
    }

    public static Action createActionNxMoveNsc4ToReg6Register(int order) {
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(nxMoveRegAction(new SrcNxNshc4CaseBuilder().setNxNshc4Dst(Boolean.TRUE).build(),
                new DstNxRegCaseBuilder().setNxReg(NxmNxReg6.class).build(), 31, false));

        return ab.build();
    }

    public static org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action nxLoadRegAction(
        DstChoice dstChoice, BigInteger value, int endOffset, boolean groupBucket) {
        NxRegLoad regLoad = new NxRegLoadBuilder()
            .setDst(new DstBuilder().setDstChoice(dstChoice).setStart(0).setEnd(endOffset).build()).setValue(value)
            .build();

        if (groupBucket) {
            return new NxActionRegLoadNodesNodeGroupBucketsBucketActionsCaseBuilder().setNxRegLoad(regLoad).build();
        } else {
            return new NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder().setNxRegLoad(regLoad).build();
        }
    }

    public static org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action nxMoveRegAction(
        SrcChoice srcChoice, DstChoice dstChoice, int endOffset, boolean groupBucket) {
        NxRegMove nxRegMove = new NxRegMoveBuilder()
            .setSrc(new SrcBuilder().setSrcChoice(srcChoice).setStart(0).setEnd(endOffset).build())
            .setDst(new org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira
                .action.rev140714.nx.action.reg.move.grouping.nx.reg.move.DstBuilder()
                .setDstChoice(dstChoice).setStart(0).setEnd(endOffset).build())
            .build();

        if (groupBucket) {
            return new NxActionRegMoveNodesNodeGroupBucketsBucketActionsCaseBuilder().setNxRegMove(nxRegMove).build();
        } else {
            return new NxActionRegMoveNodesNodeTableFlowApplyActionsCaseBuilder().setNxRegMove(nxRegMove).build();
        }
    }

    public static Action createActionOutPort(final String portUri, final int order) {
        OutputActionBuilder output = new OutputActionBuilder();
        Uri value = new Uri(portUri);
        output.setOutputNodeConnector(value);
        ActionBuilder ab = createActionBuilder(order);
        ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());

        return ab.build();
    }

    public static ActionBuilder createActionBuilder(int order) {
        ActionBuilder ab = new ActionBuilder();
        ab.setOrder(order);
        ab.setKey(new ActionKey(order));

        return ab;
    }

    public static InstructionsBuilder wrapActionsIntoApplyActionsInstruction(List<Action> theActions) {
        // Create an Apply Action
        ApplyActionsBuilder aab = new ApplyActionsBuilder();
        aab.setAction(theActions);

        // Wrap our Apply Action in an Instruction
        InstructionBuilder ib = new InstructionBuilder();
        ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
        ib.setOrder(0);
        ib.setKey(new InstructionKey(0));

        // Put our Instruction in a list of Instructions
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(ib.build());
        return new InstructionsBuilder().setInstruction(instructions);
    }

    public static FlowBuilder createFlowBuilder(final short table, final int priority, final BigInteger cookieValue,
                                                final String flowName, final String flowIdStr, MatchBuilder match,
                                                InstructionsBuilder isb) {
        FlowBuilder flow = new FlowBuilder();
        flow.setId(new FlowId(flowIdStr));
        flow.setKey(new FlowKey(new FlowId(flowIdStr)));
        flow.setTableId(table);
        flow.setFlowName(flowName);
        flow.setCookie(new FlowCookie(cookieValue));
        flow.setCookieMask(new FlowCookie(cookieValue));
        flow.setContainerName(null);
        flow.setStrict(false);
        flow.setMatch(match.build());
        flow.setInstructions(isb.build());
        flow.setPriority(priority);
        flow.setHardTimeout(0);
        flow.setIdleTimeout(0);
        flow.setFlags(new FlowModFlags(false, false, false, false, false));
        if (null == flow.isBarrier()) {
            flow.setBarrier(Boolean.FALSE);
        }

        return flow;
    }

    public static InstructionsBuilder appendGotoTableInstruction(InstructionsBuilder isb, short nextTableId) {
        if (isb.getInstruction() == null) {
            isb.setInstruction(new ArrayList<>());
        }
        isb.getInstruction().add(createGotoTableInstruction(nextTableId, isb.getInstruction().size()));
        return isb;
    }

    public static Instruction createGotoTableInstruction(short nextTableId, int order) {
        GoToTableBuilder gotoIngress = createActionGotoTable(nextTableId);

        return new InstructionBuilder().setKey(new InstructionKey(order)).setOrder(order)
            .setInstruction(new GoToTableCaseBuilder().setGoToTable(gotoIngress.build()).build()).build();
    }

    public static GoToTableBuilder createActionGotoTable(final short toTable) {
        GoToTableBuilder gotoTb = new GoToTableBuilder();
        gotoTb.setTableId(toTable);

        return gotoTb;
    }

    public static NxRegLoad createNxLoadReg0(long value) {
        Dst dst = new DstBuilder()
                .setDstChoice(new DstNxRegCaseBuilder().setNxReg(NxmNxReg0.class).build())
                .setStart(0)
                .setEnd(31)
                .build();
        return new NxRegLoadBuilder()
                .setDst(dst)
                .setValue(BigInteger.valueOf(value))
                .build();
    }

    public static Action createAction(
            org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action action, int order) {
        return new ActionBuilder()
                .setOrder(order)
                .setKey(new ActionKey(order))
                .setAction(action)
                .build();
    }
}
