/**
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import java.math.BigInteger;
import java.util.Collections;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Instructions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSource;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Metadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionResubmitNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.Dst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmit;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmitBuilder;

/**
 * Definitions of complex objects expected in tests.
 * 
 * These were originally generated {@link AssertDataObjects#assertEqualBeans}.
 */
@SuppressWarnings("all")
public class ExpectedObjects {
  public static String ELAN1 = "34701c04-1118-4c65-9425-78a80d49a211";
  
  public static Long ELAN1_SEGMENT_ID = Long.valueOf(100L);
  
  public static Interface newInterfaceConfig(final String interfaceName, final String parentName) {
    InterfaceBuilder _interfaceBuilder = new InterfaceBuilder();
    final Procedure1<InterfaceBuilder> _function = (InterfaceBuilder it) -> {
      it.setDescription(interfaceName);
      it.setName(interfaceName);
      it.setType(L2vlan.class);
      ParentRefsBuilder _parentRefsBuilder = new ParentRefsBuilder();
      final Procedure1<ParentRefsBuilder> _function_1 = (ParentRefsBuilder it_1) -> {
        it_1.setParentInterface(parentName);
      };
      ParentRefs _doubleGreaterThan = XtendBuilderExtensions.<ParentRefs, ParentRefsBuilder>operator_doubleGreaterThan(_parentRefsBuilder, _function_1);
      it.addAugmentation(ParentRefs.class, _doubleGreaterThan);
      IfL2vlanBuilder _ifL2vlanBuilder = new IfL2vlanBuilder();
      final Procedure1<IfL2vlanBuilder> _function_2 = (IfL2vlanBuilder it_1) -> {
        it_1.setL2vlanMode(IfL2vlan.L2vlanMode.Trunk);
        VlanId _vlanId = new VlanId(Integer.valueOf(0));
        it_1.setVlanId(_vlanId);
      };
      IfL2vlan _doubleGreaterThan_1 = XtendBuilderExtensions.<IfL2vlan, IfL2vlanBuilder>operator_doubleGreaterThan(_ifL2vlanBuilder, _function_2);
      it.addAugmentation(IfL2vlan.class, _doubleGreaterThan_1);
    };
    return XtendBuilderExtensions.<Interface, InterfaceBuilder>operator_doubleGreaterThan(_interfaceBuilder, _function);
  }
  
  public static ElanInstances createElanInstance() {
    ElanInstancesBuilder _elanInstancesBuilder = new ElanInstancesBuilder();
    final Procedure1<ElanInstancesBuilder> _function = (ElanInstancesBuilder it) -> {
      ElanInstanceBuilder _elanInstanceBuilder = new ElanInstanceBuilder();
      final Procedure1<ElanInstanceBuilder> _function_1 = (ElanInstanceBuilder it_1) -> {
        it_1.setDescription("TestElan description");
        it_1.setElanInstanceName("TestElanName");
        it_1.setElanTag(Long.valueOf(5000L));
        it_1.setMacTimeout(Long.valueOf(12345L));
      };
      ElanInstance _doubleGreaterThan = XtendBuilderExtensions.<ElanInstance, ElanInstanceBuilder>operator_doubleGreaterThan(_elanInstanceBuilder, _function_1);
      it.setElanInstance(Collections.<ElanInstance>unmodifiableList(CollectionLiterals.<ElanInstance>newArrayList(_doubleGreaterThan)));
    };
    return XtendBuilderExtensions.<ElanInstances, ElanInstancesBuilder>operator_doubleGreaterThan(_elanInstancesBuilder, _function);
  }
  
  public static ElanInstance createElanInstance(final String elan, final Long segmentId) {
    ElanInstanceBuilder _elanInstanceBuilder = new ElanInstanceBuilder();
    final Procedure1<ElanInstanceBuilder> _function = (ElanInstanceBuilder it) -> {
      it.setElanInstanceName(elan);
      it.setDescription(ExpectedObjects.ELAN1);
      it.setSegmentType(SegmentTypeVxlan.class);
      it.setSegmentationId(segmentId);
      it.setMacTimeout(Long.valueOf(12345L));
    };
    return XtendBuilderExtensions.<ElanInstance, ElanInstanceBuilder>operator_doubleGreaterThan(_elanInstanceBuilder, _function);
  }
  
  public static ElanInstance createElanInstance(final String elan, final Long segmentId, final Long tag) {
    ElanInstanceBuilder _elanInstanceBuilder = new ElanInstanceBuilder();
    final Procedure1<ElanInstanceBuilder> _function = (ElanInstanceBuilder it) -> {
      it.setElanInstanceName(elan);
      it.setDescription(ExpectedObjects.ELAN1);
      it.setElanTag(tag);
      it.setSegmentationId(segmentId);
      it.setSegmentType(SegmentTypeVxlan.class);
      it.setMacTimeout(Long.valueOf(12345L));
    };
    return XtendBuilderExtensions.<ElanInstance, ElanInstanceBuilder>operator_doubleGreaterThan(_elanInstanceBuilder, _function);
  }
  
  public static Flow checkSmac(final String flowId, final InterfaceInfo interfaceInfo, final ElanInstance elanInstance) {
    FlowBuilder _flowBuilder = new FlowBuilder();
    final Procedure1<FlowBuilder> _function = (FlowBuilder it) -> {
      it.setFlowName(ExpectedObjects.ELAN1);
      it.setHardTimeout(Integer.valueOf(0));
      FlowId _flowId = new FlowId(flowId);
      it.setId(_flowId);
      it.setIdleTimeout(Integer.valueOf(0));
      InstructionsBuilder _instructionsBuilder = new InstructionsBuilder();
      final Procedure1<InstructionsBuilder> _function_1 = (InstructionsBuilder it_1) -> {
        InstructionBuilder _instructionBuilder = new InstructionBuilder();
        final Procedure1<InstructionBuilder> _function_2 = (InstructionBuilder it_2) -> {
          GoToTableCaseBuilder _goToTableCaseBuilder = new GoToTableCaseBuilder();
          final Procedure1<GoToTableCaseBuilder> _function_3 = (GoToTableCaseBuilder it_3) -> {
            GoToTableBuilder _goToTableBuilder = new GoToTableBuilder();
            final Procedure1<GoToTableBuilder> _function_4 = (GoToTableBuilder it_4) -> {
              it_4.setTableId(Short.valueOf(((short) 51)));
            };
            GoToTable _doubleGreaterThan = XtendBuilderExtensions.<GoToTable, GoToTableBuilder>operator_doubleGreaterThan(_goToTableBuilder, _function_4);
            it_3.setGoToTable(_doubleGreaterThan);
          };
          GoToTableCase _doubleGreaterThan = XtendBuilderExtensions.<GoToTableCase, GoToTableCaseBuilder>operator_doubleGreaterThan(_goToTableCaseBuilder, _function_3);
          it_2.setInstruction(_doubleGreaterThan);
          it_2.setOrder(Integer.valueOf(0));
        };
        Instruction _doubleGreaterThan = XtendBuilderExtensions.<Instruction, InstructionBuilder>operator_doubleGreaterThan(_instructionBuilder, _function_2);
        it_1.setInstruction(Collections.<Instruction>unmodifiableList(CollectionLiterals.<Instruction>newArrayList(_doubleGreaterThan)));
      };
      Instructions _doubleGreaterThan = XtendBuilderExtensions.<Instructions, InstructionsBuilder>operator_doubleGreaterThan(_instructionsBuilder, _function_1);
      it.setInstructions(_doubleGreaterThan);
      MatchBuilder _matchBuilder = new MatchBuilder();
      final Procedure1<MatchBuilder> _function_2 = (MatchBuilder it_1) -> {
        EthernetMatchBuilder _ethernetMatchBuilder = new EthernetMatchBuilder();
        final Procedure1<EthernetMatchBuilder> _function_3 = (EthernetMatchBuilder it_2) -> {
          EthernetSourceBuilder _ethernetSourceBuilder = new EthernetSourceBuilder();
          final Procedure1<EthernetSourceBuilder> _function_4 = (EthernetSourceBuilder it_3) -> {
            String _macAddress = interfaceInfo.getMacAddress();
            MacAddress _macAddress_1 = new MacAddress(_macAddress);
            it_3.setAddress(_macAddress_1);
          };
          EthernetSource _doubleGreaterThan_1 = XtendBuilderExtensions.<EthernetSource, EthernetSourceBuilder>operator_doubleGreaterThan(_ethernetSourceBuilder, _function_4);
          it_2.setEthernetSource(_doubleGreaterThan_1);
        };
        EthernetMatch _doubleGreaterThan_1 = XtendBuilderExtensions.<EthernetMatch, EthernetMatchBuilder>operator_doubleGreaterThan(_ethernetMatchBuilder, _function_3);
        it_1.setEthernetMatch(_doubleGreaterThan_1);
        MetadataBuilder _metadataBuilder = new MetadataBuilder();
        final Procedure1<MetadataBuilder> _function_4 = (MetadataBuilder it_2) -> {
          Long _elanTag = elanInstance.getElanTag();
          int _interfaceTag = interfaceInfo.getInterfaceTag();
          BigInteger _elanMetadataLabel = ElanHelper.getElanMetadataLabel((_elanTag).longValue(), _interfaceTag);
          it_2.setMetadata(_elanMetadataLabel);
          BigInteger _elanMetadataMask = ElanHelper.getElanMetadataMask();
          it_2.setMetadataMask(_elanMetadataMask);
        };
        Metadata _doubleGreaterThan_2 = XtendBuilderExtensions.<Metadata, MetadataBuilder>operator_doubleGreaterThan(_metadataBuilder, _function_4);
        it_1.setMetadata(_doubleGreaterThan_2);
      };
      Match _doubleGreaterThan_1 = XtendBuilderExtensions.<Match, MatchBuilder>operator_doubleGreaterThan(_matchBuilder, _function_2);
      it.setMatch(_doubleGreaterThan_1);
      it.setPriority(Integer.valueOf(20));
      it.setTableId(Short.valueOf(((short) 50)));
    };
    return XtendBuilderExtensions.<Flow, FlowBuilder>operator_doubleGreaterThan(_flowBuilder, _function);
  }
  
  public static Flow checkDmacOfSameDpn(final String flowId, final InterfaceInfo interfaceInfo, final ElanInstance elanInstance) {
    Flow _xblockexpression = null;
    {
      int _interfaceTag = interfaceInfo.getInterfaceTag();
      final long regvalue = MetaDataUtil.getReg6ValueForLPortDispatcher(_interfaceTag, NwConstants.DEFAULT_SERVICE_INDEX);
      FlowBuilder _flowBuilder = new FlowBuilder();
      final Procedure1<FlowBuilder> _function = (FlowBuilder it) -> {
        it.setFlowName(ExpectedObjects.ELAN1);
        it.setHardTimeout(Integer.valueOf(0));
        FlowId _flowId = new FlowId(flowId);
        it.setId(_flowId);
        it.setIdleTimeout(Integer.valueOf(0));
        InstructionsBuilder _instructionsBuilder = new InstructionsBuilder();
        final Procedure1<InstructionsBuilder> _function_1 = (InstructionsBuilder it_1) -> {
          InstructionBuilder _instructionBuilder = new InstructionBuilder();
          final Procedure1<InstructionBuilder> _function_2 = (InstructionBuilder it_2) -> {
            ApplyActionsCaseBuilder _applyActionsCaseBuilder = new ApplyActionsCaseBuilder();
            final Procedure1<ApplyActionsCaseBuilder> _function_3 = (ApplyActionsCaseBuilder it_3) -> {
              ApplyActionsBuilder _applyActionsBuilder = new ApplyActionsBuilder();
              final Procedure1<ApplyActionsBuilder> _function_4 = (ApplyActionsBuilder it_4) -> {
                ActionBuilder _actionBuilder = new ActionBuilder();
                final Procedure1<ActionBuilder> _function_5 = (ActionBuilder it_5) -> {
                  NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder _nxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder = new NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder();
                  final Procedure1<NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder> _function_6 = (NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder it_6) -> {
                    NxResubmitBuilder _nxResubmitBuilder = new NxResubmitBuilder();
                    final Procedure1<NxResubmitBuilder> _function_7 = (NxResubmitBuilder it_7) -> {
                      it_7.setInPort(Integer.valueOf(65528));
                      it_7.setTable(Short.valueOf(((short) 220)));
                    };
                    NxResubmit _doubleGreaterThan = XtendBuilderExtensions.<NxResubmit, NxResubmitBuilder>operator_doubleGreaterThan(_nxResubmitBuilder, _function_7);
                    it_6.setNxResubmit(_doubleGreaterThan);
                  };
                  NxActionResubmitNodesNodeTableFlowApplyActionsCase _doubleGreaterThan = XtendBuilderExtensions.<NxActionResubmitNodesNodeTableFlowApplyActionsCase, NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder>operator_doubleGreaterThan(_nxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder, _function_6);
                  it_5.setAction(_doubleGreaterThan);
                  it_5.setOrder(Integer.valueOf(1));
                };
                Action _doubleGreaterThan = XtendBuilderExtensions.<Action, ActionBuilder>operator_doubleGreaterThan(_actionBuilder, _function_5);
                ActionBuilder _actionBuilder_1 = new ActionBuilder();
                final Procedure1<ActionBuilder> _function_6 = (ActionBuilder it_5) -> {
                  NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder _nxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder = new NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder();
                  final Procedure1<NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder> _function_7 = (NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder it_6) -> {
                    NxRegLoadBuilder _nxRegLoadBuilder = new NxRegLoadBuilder();
                    final Procedure1<NxRegLoadBuilder> _function_8 = (NxRegLoadBuilder it_7) -> {
                      DstBuilder _dstBuilder = new DstBuilder();
                      final Procedure1<DstBuilder> _function_9 = (DstBuilder it_8) -> {
                        DstNxRegCaseBuilder _dstNxRegCaseBuilder = new DstNxRegCaseBuilder();
                        final Procedure1<DstNxRegCaseBuilder> _function_10 = (DstNxRegCaseBuilder it_9) -> {
                          it_9.setNxReg(NxmNxReg6.class);
                        };
                        DstNxRegCase _doubleGreaterThan_1 = XtendBuilderExtensions.<DstNxRegCase, DstNxRegCaseBuilder>operator_doubleGreaterThan(_dstNxRegCaseBuilder, _function_10);
                        it_8.setDstChoice(_doubleGreaterThan_1);
                        it_8.setEnd(Integer.valueOf(31));
                        it_8.setStart(Integer.valueOf(0));
                      };
                      Dst _doubleGreaterThan_1 = XtendBuilderExtensions.<Dst, DstBuilder>operator_doubleGreaterThan(_dstBuilder, _function_9);
                      it_7.setDst(_doubleGreaterThan_1);
                      BigInteger _bigInteger = new BigInteger(("" + Long.valueOf(regvalue)));
                      it_7.setValue(_bigInteger);
                    };
                    NxRegLoad _doubleGreaterThan_1 = XtendBuilderExtensions.<NxRegLoad, NxRegLoadBuilder>operator_doubleGreaterThan(_nxRegLoadBuilder, _function_8);
                    it_6.setNxRegLoad(_doubleGreaterThan_1);
                  };
                  NxActionRegLoadNodesNodeTableFlowApplyActionsCase _doubleGreaterThan_1 = XtendBuilderExtensions.<NxActionRegLoadNodesNodeTableFlowApplyActionsCase, NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder>operator_doubleGreaterThan(_nxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder, _function_7);
                  it_5.setAction(_doubleGreaterThan_1);
                  it_5.setOrder(Integer.valueOf(0));
                };
                Action _doubleGreaterThan_1 = XtendBuilderExtensions.<Action, ActionBuilder>operator_doubleGreaterThan(_actionBuilder_1, _function_6);
                it_4.setAction(Collections.<Action>unmodifiableList(CollectionLiterals.<Action>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1)));
              };
              ApplyActions _doubleGreaterThan = XtendBuilderExtensions.<ApplyActions, ApplyActionsBuilder>operator_doubleGreaterThan(_applyActionsBuilder, _function_4);
              it_3.setApplyActions(_doubleGreaterThan);
            };
            ApplyActionsCase _doubleGreaterThan = XtendBuilderExtensions.<ApplyActionsCase, ApplyActionsCaseBuilder>operator_doubleGreaterThan(_applyActionsCaseBuilder, _function_3);
            it_2.setInstruction(_doubleGreaterThan);
            it_2.setOrder(Integer.valueOf(0));
          };
          Instruction _doubleGreaterThan = XtendBuilderExtensions.<Instruction, InstructionBuilder>operator_doubleGreaterThan(_instructionBuilder, _function_2);
          it_1.setInstruction(Collections.<Instruction>unmodifiableList(CollectionLiterals.<Instruction>newArrayList(_doubleGreaterThan)));
        };
        Instructions _doubleGreaterThan = XtendBuilderExtensions.<Instructions, InstructionsBuilder>operator_doubleGreaterThan(_instructionsBuilder, _function_1);
        it.setInstructions(_doubleGreaterThan);
        MatchBuilder _matchBuilder = new MatchBuilder();
        final Procedure1<MatchBuilder> _function_2 = (MatchBuilder it_1) -> {
          EthernetMatchBuilder _ethernetMatchBuilder = new EthernetMatchBuilder();
          final Procedure1<EthernetMatchBuilder> _function_3 = (EthernetMatchBuilder it_2) -> {
            EthernetDestinationBuilder _ethernetDestinationBuilder = new EthernetDestinationBuilder();
            final Procedure1<EthernetDestinationBuilder> _function_4 = (EthernetDestinationBuilder it_3) -> {
              String _macAddress = interfaceInfo.getMacAddress();
              MacAddress _macAddress_1 = new MacAddress(_macAddress);
              it_3.setAddress(_macAddress_1);
            };
            EthernetDestination _doubleGreaterThan_1 = XtendBuilderExtensions.<EthernetDestination, EthernetDestinationBuilder>operator_doubleGreaterThan(_ethernetDestinationBuilder, _function_4);
            it_2.setEthernetDestination(_doubleGreaterThan_1);
          };
          EthernetMatch _doubleGreaterThan_1 = XtendBuilderExtensions.<EthernetMatch, EthernetMatchBuilder>operator_doubleGreaterThan(_ethernetMatchBuilder, _function_3);
          it_1.setEthernetMatch(_doubleGreaterThan_1);
          MetadataBuilder _metadataBuilder = new MetadataBuilder();
          final Procedure1<MetadataBuilder> _function_4 = (MetadataBuilder it_2) -> {
            Long _elanTag = elanInstance.getElanTag();
            BigInteger _elanMetadataLabel = ElanHelper.getElanMetadataLabel((_elanTag).longValue());
            it_2.setMetadata(_elanMetadataLabel);
            it_2.setMetadataMask(MetaDataUtil.METADATA_MASK_SERVICE);
          };
          Metadata _doubleGreaterThan_2 = XtendBuilderExtensions.<Metadata, MetadataBuilder>operator_doubleGreaterThan(_metadataBuilder, _function_4);
          it_1.setMetadata(_doubleGreaterThan_2);
        };
        Match _doubleGreaterThan_1 = XtendBuilderExtensions.<Match, MatchBuilder>operator_doubleGreaterThan(_matchBuilder, _function_2);
        it.setMatch(_doubleGreaterThan_1);
        it.setPriority(Integer.valueOf(20));
        it.setTableId(Short.valueOf(((short) 51)));
      };
      _xblockexpression = XtendBuilderExtensions.<Flow, FlowBuilder>operator_doubleGreaterThan(_flowBuilder, _function);
    }
    return _xblockexpression;
  }
  
  public static Flow checkDmacOfOtherDPN(final String flowId, final InterfaceInfo interfaceInfo, final /* TunnelInterfaceDetails */Object tepDetails, final ElanInstance elanInstance) {
    throw new Error("Unresolved compilation problems:"
      + "\ngetInterfaceInfo cannot be resolved"
      + "\ngetInterfaceTag cannot be resolved");
  }
  
  public static Networks checkEvpnAdvertiseRoute(final Long vni, final String mac, final String tepip, final String prefix, final String rd1) {
    NetworksBuilder _networksBuilder = new NetworksBuilder();
    final Procedure1<NetworksBuilder> _function = (NetworksBuilder it) -> {
      it.setBgpControlPlaneType(BgpControlPlaneType.PROTOCOLEVPN);
      it.setEncapType(EncapType.VXLAN);
      it.setEthtag(Long.valueOf(0L));
      it.setL2vni(vni);
      it.setL3vni(Long.valueOf(0L));
      it.setLabel(Long.valueOf(0L));
      it.setMacaddress(mac);
      Ipv4Address _ipv4Address = new Ipv4Address(tepip);
      it.setNexthop(_ipv4Address);
      it.setPrefixLen(prefix);
      it.setRd(rd1);
    };
    return XtendBuilderExtensions.<Networks, NetworksBuilder>operator_doubleGreaterThan(_networksBuilder, _function);
  }
  
  public static Networks checkEvpnWithdrawRT2DelIntf() {
    return null;
  }
}
