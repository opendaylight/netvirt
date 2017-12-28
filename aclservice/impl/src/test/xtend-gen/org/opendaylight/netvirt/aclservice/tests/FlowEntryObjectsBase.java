/**
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
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
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;

@SuppressWarnings("all")
public class FlowEntryObjectsBase {
  protected List<? extends Iterable<FlowEntity>> fixedFlowsPort1() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    return Collections.<Iterable<FlowEntity>>unmodifiableList(CollectionLiterals.<Iterable<FlowEntity>>newArrayList(_fixedIngressFlowsPort1, _fixedEgressFlowsPort1));
  }
  
  protected List<FlowEntity> fixedIngressFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v4123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(68);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(67);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v6_123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(546);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(547);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_130_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 130), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_135_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 135), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_136_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 136), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ARP_123_987");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5));
  }
  
  protected List<FlowEntity> fixedEgressL2BroadcastFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F3");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchMetadata)));
      it.setPriority(61005);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected List<FlowEntity> fixedIngressL3BroadcastFlows() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setDpnId(BigInteger.valueOf(123L));
      it.setFlowId("Ingress_v4_Broadcast_123_987_10.0.0.255_Permit");
      it.setFlowName("ACL");
      it.setHardTimeOut(0);
      it.setIdleTimeOut(0);
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE);
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MacAddress _macAddress = new MacAddress("ff:ff:ff:ff:ff:ff");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.255/32");
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination(_ipv4Prefix);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetDestination, _matchEthernetType, _matchIpv4Destination, _nxMatchRegister)));
      it.setPriority(61010);
      it.setSendFlowRemFlag(false);
      it.setStrictFlag(false);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected Iterable<FlowEntity> fixedEgressFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:F3_Permit_");
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
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    List<FlowEntity> _fixedEgressArpFlowsPort1 = this.fixedEgressArpFlowsPort1();
    return Iterables.<FlowEntity>concat(Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6)), _fixedEgressArpFlowsPort1);
  }
  
  protected List<FlowEntity> fixedEgressArpFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:F310.0.0.1/32");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.1/32");
      MatchArpSpa _matchArpSpa = new MatchArpSpa(_ipv4Prefix);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchArpSpa, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected List<FlowEntity> fixedIngressFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v4123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(68);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(67);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v6_123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(546);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(547);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_130_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 130), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_135_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 135), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_136_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 136), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ARP_123_987");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5));
  }
  
  protected List<FlowEntity> fixedEgressL2BroadcastFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F4");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchMetadata)));
      it.setPriority(61005);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected Iterable<FlowEntity> fixedEgressFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:F4_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 67));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 68));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 68));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 67));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 546));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 547));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    List<FlowEntity> _fixedEgressArpFlowsPort2 = this.fixedEgressArpFlowsPort2();
    return Iterables.<FlowEntity>concat(Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6)), _fixedEgressArpFlowsPort2);
  }
  
  protected List<FlowEntity> fixedEgressArpFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:F410.0.0.2/32");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.2/32");
      MatchArpSpa _matchArpSpa = new MatchArpSpa(_ipv4Prefix);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchArpSpa, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected List<FlowEntity> fixedIngressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v4123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(68);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(67);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v6_123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(546);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(547);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_130_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 130), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_135_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 135), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_136_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 136), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ARP_123_987");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5));
  }
  
  protected List<FlowEntity> fixedEgressL2BroadcastFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F5");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchMetadata)));
      it.setPriority(61005);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected List<FlowEntity> fixedEgressFlowsPort3() {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7));
  }
  
  public static List<FlowEntity> fixedIngressFlowsPort4() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v4123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(68);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(67);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v6_123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(546);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(547);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_130_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 130), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_135_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 135), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_136_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 136), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ARP_123_987");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5));
  }
  
  public static List<FlowEntity> fixedEgressFlowsPort4() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v4123_987__Permit_");
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
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v6_123_987__Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(547);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(546);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    FlowEntityBuilder _flowEntityBuilder_6 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_6 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    FlowEntityBuilder _flowEntityBuilder_7 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_7 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
    FlowEntityBuilder _flowEntityBuilder_8 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_8 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:F6");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F6");
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress("0D:AA:D8:42:30:F6");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_8 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_8, _function_8);
    FlowEntityBuilder _flowEntityBuilder_9 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_9 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:F6");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F6");
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress("0D:AA:D8:42:30:F6");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_9 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_9, _function_9);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7, _doubleGreaterThan_8, _doubleGreaterThan_9));
  }
  
  protected Iterable<FlowEntity> remoteFlows() {
    List<FlowEntity> _remoteIngressFlowsPort1 = this.remoteIngressFlowsPort1();
    List<FlowEntity> _remoteEgressFlowsPort1 = this.remoteEgressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_remoteIngressFlowsPort1, _remoteEgressFlowsPort1);
    List<FlowEntity> _remoteIngressFlowsPort2 = this.remoteIngressFlowsPort2();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _remoteIngressFlowsPort2);
    List<FlowEntity> _remoteEgressFlowsPort2 = this.remoteEgressFlowsPort2();
    return Iterables.<FlowEntity>concat(_plus_1, _remoteEgressFlowsPort2);
  }
  
  protected List<FlowEntity> remoteIngressFlowsPort1() {
    FlowEntity _remoteIngressFlowsPort = this.remoteIngressFlowsPort("10.0.0.1");
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_remoteIngressFlowsPort));
  }
  
  protected List<FlowEntity> remoteIngressFlowsPort2() {
    FlowEntity _remoteIngressFlowsPort = this.remoteIngressFlowsPort("10.0.0.2");
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_remoteIngressFlowsPort));
  }
  
  protected List<FlowEntity> remoteEgressFlowsPort1() {
    FlowEntity _remoteEgressFlowsPort = this.remoteEgressFlowsPort("10.0.0.1");
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_remoteEgressFlowsPort));
  }
  
  protected List<FlowEntity> remoteEgressFlowsPort2() {
    FlowEntity _remoteEgressFlowsPort = this.remoteEgressFlowsPort("10.0.0.2");
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_remoteEgressFlowsPort));
  }
  
  protected FlowEntity remoteIngressFlowsPort(final String ip) {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Acl_Filter_Egress_" + ip) + "/32_2"));
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE);
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(32L), BigInteger.valueOf(16777200L));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination(ip, "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetType, _matchIpv4Destination)));
      it.setPriority(100);
      it.setTableId(NwConstants.INGRESS_REMOTE_ACL_TABLE);
    };
    return XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
  }
  
  protected FlowEntity remoteEgressFlowsPort(final String ip) {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Acl_Filter_Ingress_" + ip) + "/32_2"));
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE);
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(32L), BigInteger.valueOf(16777200L));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source(ip, "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(100);
      it.setTableId(NwConstants.EGRESS_REMOTE_ACL_TABLE);
    };
    return XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
  }
  
  protected List<FlowEntity> remoteIngressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Acl_Filter_Ingress_10.0.0.2/32_4");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 247));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(64L), BigInteger.valueOf(16777200L));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.2", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(100);
      it.setTableId(((short) 246));
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected List<FlowEntity> remoteEgressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Acl_Filter_Egress_10.0.0.2/32_4");
      it.setFlowName("ACL");
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(((short) 217));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionGotoTable)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(64L), BigInteger.valueOf(16777200L));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.2", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _matchEthernetType, _matchIpv4Destination)));
      it.setPriority(100);
      it.setTableId(((short) 216));
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan));
  }
  
  protected List<FlowEntity> expectedFlows(final String mac) {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v4123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(68);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(67);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v6_123_987_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(546);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(547);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_130_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 130), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_135_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 135), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ICMPv6_123_987_136_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 58));
      MatchIcmpv6 _matchIcmpv6 = new MatchIcmpv6(((short) 136), ((short) 0));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchIcmpv6, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_ARP_123_987");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    FlowEntityBuilder _flowEntityBuilder_6 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_6 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Egress_DHCP_Client_v4123_987_" + mac) + "_Permit_"));
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
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    FlowEntityBuilder _flowEntityBuilder_7 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_7 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Egress_DHCP_Client_v6_123_987_" + mac) + "_Permit_"));
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(547);
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(546);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
    FlowEntityBuilder _flowEntityBuilder_8 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_8 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_8 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_8, _function_8);
    FlowEntityBuilder _flowEntityBuilder_9 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_9 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_9 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_9, _function_9);
    FlowEntityBuilder _flowEntityBuilder_10 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_10 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_10 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_10, _function_10);
    FlowEntityBuilder _flowEntityBuilder_11 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_11 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_11 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_11, _function_11);
    FlowEntityBuilder _flowEntityBuilder_12 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_12 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_12 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_12, _function_12);
    FlowEntityBuilder _flowEntityBuilder_13 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_13 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_13 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_13, _function_13);
    FlowEntityBuilder _flowEntityBuilder_14 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_14 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId(("Egress_ARP_123_987_" + mac));
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress(mac);
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE);
    };
    FlowEntity _doubleGreaterThan_14 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_14, _function_14);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7, _doubleGreaterThan_8, _doubleGreaterThan_9, _doubleGreaterThan_10, _doubleGreaterThan_11, _doubleGreaterThan_12, _doubleGreaterThan_13, _doubleGreaterThan_14));
  }
}
