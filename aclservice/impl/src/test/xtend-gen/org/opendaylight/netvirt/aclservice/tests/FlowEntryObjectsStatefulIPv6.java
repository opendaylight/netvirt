/**
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import com.google.common.collect.Iterables;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha;
import org.opendaylight.genius.mdsalutil.matches.MatchArpSpa;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchUdpDestinationPort;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.netvirt.aclservice.tests.FlowEntryObjectsStateful;
import org.opendaylight.netvirt.aclservice.tests.IdHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;

@SuppressWarnings("all")
public class FlowEntryObjectsStatefulIPv6 extends FlowEntryObjectsStateful {
  @Override
  public Iterable<FlowEntity> icmpFlowsForTwoAclsHavingSameRules() {
    List<FlowEntity> _fixedIngressFlowsPort3 = this.fixedIngressFlowsPort3();
    List<FlowEntity> _fixedConntrackIngressFlowsPort3 = this.fixedConntrackIngressFlowsPort3();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort3, _fixedConntrackIngressFlowsPort3);
    List<FlowEntity> _icmpIngressFlowsPort4 = this.icmpIngressFlowsPort4();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _icmpIngressFlowsPort4);
    List<FlowEntity> _fixedEgressFlowsPort3 = this.fixedEgressFlowsPort3();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedEgressFlowsPort3);
    List<FlowEntity> _fixedConntrackEgressFlowsPort5 = this.fixedConntrackEgressFlowsPort5();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedConntrackEgressFlowsPort5);
    List<FlowEntity> _icmpEgressFlowsPort4 = this.icmpEgressFlowsPort4();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _icmpEgressFlowsPort4);
    List<FlowEntity> _ingressCommitNonConntrack1 = this.ingressCommitNonConntrack1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _ingressCommitNonConntrack1);
    List<FlowEntity> _egressCommitNonConntrack1 = this.egressCommitNonConntrack1();
    Iterable<FlowEntity> _plus_6 = Iterables.<FlowEntity>concat(_plus_5, _egressCommitNonConntrack1);
    List<FlowEntity> _ingressCommitConntrack1 = this.ingressCommitConntrack1();
    Iterable<FlowEntity> _plus_7 = Iterables.<FlowEntity>concat(_plus_6, _ingressCommitConntrack1);
    List<FlowEntity> _egressCommitConntrack1 = this.egressCommitConntrack1();
    Iterable<FlowEntity> _plus_8 = Iterables.<FlowEntity>concat(_plus_7, _egressCommitConntrack1);
    List<FlowEntity> _ingressfixedAclMissDrop1 = this.ingressfixedAclMissDrop1();
    Iterable<FlowEntity> _plus_9 = Iterables.<FlowEntity>concat(_plus_8, _ingressfixedAclMissDrop1);
    List<FlowEntity> _egressfixedAclMissDrop1 = this.egressfixedAclMissDrop1();
    Iterable<FlowEntity> _plus_10 = Iterables.<FlowEntity>concat(_plus_9, _egressfixedAclMissDrop1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_11 = Iterables.<FlowEntity>concat(_plus_10, _fixedIngressL3BroadcastFlows);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort3 = this.fixedEgressL2BroadcastFlowsPort3();
    return Iterables.<FlowEntity>concat(_plus_11, _fixedEgressL2BroadcastFlowsPort3);
  }
  
  @Override
  public List<FlowEntity> fixedConntrackIngressFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 241));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.1", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _matchEthernetDestination, _matchEthernetType, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, ((short) 243));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(100);
      it.setTableId(NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48, 48);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  @Override
  public List<FlowEntity> etherIngressFlowsPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "ETHERnullIngress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 246));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(32L), BigInteger.valueOf(16777200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchRegister, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> fixedConntrackEgressFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 211));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.1", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, ((short) 213));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchMetadata)));
      it.setPriority(100);
      it.setTableId(NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  @Override
  public List<FlowEntity> fixedConntrackIngressFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.2/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 241));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.2", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _matchEthernetDestination, _matchEthernetType, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, ((short) 243));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(100);
      it.setTableId(NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48, 48);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  @Override
  public List<FlowEntity> fixedConntrackEgressFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.2/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 211));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.2", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, ((short) 213));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchMetadata)));
      it.setPriority(100);
      it.setTableId(NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  @Override
  public List<FlowEntity> fixedConntrackIngressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 241));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.3", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _matchEthernetDestination, _matchEthernetType, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, ((short) 243));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(100);
      it.setTableId(NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48, 48);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  @Override
  public List<FlowEntity> fixedConntrackEgressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 211));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.3", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _matchMetadata, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  @Override
  public List<FlowEntity> etherEgressFlowsPort1() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "ETHERnullEgress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchMetadata)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> etheregressFlowPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "ETHERnullEgress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchMetadata)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> tcpIngressFlowPort1() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(80, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> tcpIngressFlowPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(80, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> tcpEgressFlowPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "TCP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 216));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(80, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614944L), BigInteger.valueOf(1152920405111996400L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> udpEgressFlowsPort1() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "UDP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchUdpDestinationPort _nxMatchUdpDestinationPort = new NxMatchUdpDestinationPort(80, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchUdpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> udpIngressFlowsPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "UDP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 246));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchUdpDestinationPort _nxMatchUdpDestinationPort = new NxMatchUdpDestinationPort(80, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(32L), BigInteger.valueOf(16777200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchUdpDestinationPort, _matchIpProtocol, _nxMatchRegister, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> udpEgressFlowsPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "UDP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchUdpDestinationPort _nxMatchUdpDestinationPort = new NxMatchUdpDestinationPort(80, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchUdpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> icmpIngressFlowsPort1() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> icmpIngressFlowsPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> icmpEgressFlowsPort2() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 216));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614944L), BigInteger.valueOf(1152920405111996400L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> udpIngressPortRangeFlows() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "UDP_DESTINATION_2000_65532Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchUdpDestinationPort _nxMatchUdpDestinationPort = new NxMatchUdpDestinationPort(2000, 65532);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchUdpDestinationPort, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> tcpEgressRangeFlows() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "TCP_DESTINATION_776_65534Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId2 = "TCP_DESTINATION_512_65280Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId3 = "TCP_DESTINATION_334_65534Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId4 = "TCP_DESTINATION_333_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId5 = "TCP_DESTINATION_336_65520Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId6 = "TCP_DESTINATION_352_65504Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId7 = "TCP_DESTINATION_384_65408Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId8 = "TCP_DESTINATION_768_65528Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(776, 65534);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId2);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(512, 65280);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId2);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
      FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId3);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(334, 65534);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId3);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
      FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId4);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(333, 65535);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId4);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
      FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId5);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(336, 65520);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId5);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
      FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId6);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(352, 65504);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId6);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
      FlowEntityBuilder _flowEntityBuilder_6 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_6 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId7);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(384, 65408);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId7);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
      FlowEntityBuilder _flowEntityBuilder_7 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_7 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId8);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        NxMatchTcpDestinationPort _nxMatchTcpDestinationPort = new NxMatchTcpDestinationPort(768, 65528);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _nxMatchTcpDestinationPort, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId8);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> udpIngressAllFlows() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "UDP_DESTINATION_1_0Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> tcpEgressAllFlows() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String theFlowId = "TCP_DESTINATION_1_0Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(theFlowId);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 6));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(theFlowId);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> icmpIngressFlowsPort3() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      final String flowId2 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId2);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(flowId2);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE);
      };
      FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1));
    }
    return _xblockexpression;
  }
  
  protected List<FlowEntity> icmpIngressFlowsPort4() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7";
      final String flowId2 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _nxMatchRegister)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId2);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _nxMatchRegister)));
        it.setPriority(1004);
        it.setTableId(NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> icmpEgressFlowsPort3() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId2 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId2);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId2);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE);
      };
      FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1));
    }
    return _xblockexpression;
  }
  
  protected List<FlowEntity> icmpEgressFlowsPort4() {
    List<FlowEntity> _xblockexpression = null;
    {
      final String flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6";
      final String flowId2 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21";
      FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId1);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _matchMetadata)));
        Integer _id = IdHelper.getId(flowId1);
        it.setPriority((_id).intValue());
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
      FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
      final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
        it.setDpnId(BigInteger.valueOf(123L));
        it.setCookie(BigInteger.valueOf(110100480L));
        it.setFlowId(flowId2);
        it.setFlowName("ACL");
        InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
        it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
        MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
        MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(34525L);
        MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 2), ((short) 3));
        MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
        MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetType_1, _matchIcmpv6, _matchIpProtocol, _matchMetadata)));
        it.setPriority(1004);
        it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
      };
      FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
      _xblockexpression = Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1));
    }
    return _xblockexpression;
  }
  
  @Override
  public List<FlowEntity> fixedEgressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:F5_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(67);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(68);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Server_v4123_987_Drop_");
      it.setFlowName("ACL");
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList()));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(68);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(67);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Server_v6_123_987_Drop_");
      it.setFlowName("ACL");
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList()));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(546);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(547);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ICMPv6_123_987_134_Drop_");
      it.setFlowName("ACL");
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList()));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 134), ((short) 0));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _matchMetadata)));
      it.setPriority(63020);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ICMPv6_123_987_133_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 133), ((short) 0));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ICMPv6_123_987_135_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 135), ((short) 0));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    FlowEntityBuilder _flowEntityBuilder_6 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_6 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ICMPv6_123_987_136_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 136), ((short) 0));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    FlowEntityBuilder _flowEntityBuilder_7 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_7 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:F510.0.0.3/32");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.3/32");
      MatchArpSpa _matchArpSpa = new MatchArpSpa(_ipv4Prefix);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchArpSpa, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7));
  }
  
  protected List<FlowEntity> fixedConntrackEgressFlowsPort5() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 211));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.3", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(((short) 210));
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62020);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, ((short) 213));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), BigInteger.valueOf(1152920405095219200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchMetadata)));
      it.setPriority(100);
      it.setTableId(NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
}
