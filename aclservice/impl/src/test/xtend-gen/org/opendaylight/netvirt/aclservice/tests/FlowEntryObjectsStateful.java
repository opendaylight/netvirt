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
import org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;

@SuppressWarnings("all")
public class FlowEntryObjectsStateful extends FlowEntryObjectsBase {
  protected Iterable<FlowEntity> etherFlows() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _etherEgressFlowsPort1 = this.etherEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _etherEgressFlowsPort1);
    List<FlowEntity> _fixedIngressFlowsPort2 = this.fixedIngressFlowsPort2();
    Iterable<FlowEntity> _plus_6 = Iterables.<FlowEntity>concat(_plus_5, _fixedIngressFlowsPort2);
    List<FlowEntity> _fixedConntrackIngressFlowsPort2 = this.fixedConntrackIngressFlowsPort2();
    Iterable<FlowEntity> _plus_7 = Iterables.<FlowEntity>concat(_plus_6, _fixedConntrackIngressFlowsPort2);
    List<FlowEntity> _etherIngressFlowsPort2 = this.etherIngressFlowsPort2();
    Iterable<FlowEntity> _plus_8 = Iterables.<FlowEntity>concat(_plus_7, _etherIngressFlowsPort2);
    List<FlowEntity> _etherIngressFlowsPort2_1 = this.etherIngressFlowsPort2();
    Iterable<FlowEntity> _plus_9 = Iterables.<FlowEntity>concat(_plus_8, _etherIngressFlowsPort2_1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort2 = this.fixedEgressL2BroadcastFlowsPort2();
    Iterable<FlowEntity> _plus_10 = Iterables.<FlowEntity>concat(_plus_9, _fixedEgressL2BroadcastFlowsPort2);
    List<FlowEntity> _fixedIngressL3BroadcastFlows_1 = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_11 = Iterables.<FlowEntity>concat(_plus_10, _fixedIngressL3BroadcastFlows_1);
    Iterable<FlowEntity> _fixedEgressFlowsPort2 = this.fixedEgressFlowsPort2();
    Iterable<FlowEntity> _plus_12 = Iterables.<FlowEntity>concat(_plus_11, _fixedEgressFlowsPort2);
    List<FlowEntity> _fixedConntrackEgressFlowsPort2 = this.fixedConntrackEgressFlowsPort2();
    Iterable<FlowEntity> _plus_13 = Iterables.<FlowEntity>concat(_plus_12, _fixedConntrackEgressFlowsPort2);
    List<FlowEntity> _etheregressFlowPort2 = this.etheregressFlowPort2();
    Iterable<FlowEntity> _plus_14 = Iterables.<FlowEntity>concat(_plus_13, _etheregressFlowPort2);
    Iterable<FlowEntity> _remoteFlows = this.remoteFlows();
    Iterable<FlowEntity> _plus_15 = Iterables.<FlowEntity>concat(_plus_14, _remoteFlows);
    Iterable<FlowEntity> _remoteFlows_1 = this.remoteFlows();
    return Iterables.<FlowEntity>concat(_plus_15, _remoteFlows_1);
  }
  
  protected Iterable<FlowEntity> tcpFlows() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _tcpIngressFlowPort1 = this.tcpIngressFlowPort1();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _tcpIngressFlowPort1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _fixedIngressFlowsPort2 = this.fixedIngressFlowsPort2();
    Iterable<FlowEntity> _plus_6 = Iterables.<FlowEntity>concat(_plus_5, _fixedIngressFlowsPort2);
    List<FlowEntity> _fixedConntrackIngressFlowsPort2 = this.fixedConntrackIngressFlowsPort2();
    Iterable<FlowEntity> _plus_7 = Iterables.<FlowEntity>concat(_plus_6, _fixedConntrackIngressFlowsPort2);
    List<FlowEntity> _tcpIngressFlowPort2 = this.tcpIngressFlowPort2();
    Iterable<FlowEntity> _plus_8 = Iterables.<FlowEntity>concat(_plus_7, _tcpIngressFlowPort2);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort2 = this.fixedEgressL2BroadcastFlowsPort2();
    Iterable<FlowEntity> _plus_9 = Iterables.<FlowEntity>concat(_plus_8, _fixedEgressL2BroadcastFlowsPort2);
    List<FlowEntity> _fixedIngressL3BroadcastFlows_1 = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_10 = Iterables.<FlowEntity>concat(_plus_9, _fixedIngressL3BroadcastFlows_1);
    Iterable<FlowEntity> _fixedEgressFlowsPort2 = this.fixedEgressFlowsPort2();
    Iterable<FlowEntity> _plus_11 = Iterables.<FlowEntity>concat(_plus_10, _fixedEgressFlowsPort2);
    List<FlowEntity> _fixedConntrackEgressFlowsPort2 = this.fixedConntrackEgressFlowsPort2();
    Iterable<FlowEntity> _plus_12 = Iterables.<FlowEntity>concat(_plus_11, _fixedConntrackEgressFlowsPort2);
    List<FlowEntity> _tcpEgressFlowPort2 = this.tcpEgressFlowPort2();
    Iterable<FlowEntity> _plus_13 = Iterables.<FlowEntity>concat(_plus_12, _tcpEgressFlowPort2);
    List<FlowEntity> _tcpEgressFlowPort2_1 = this.tcpEgressFlowPort2();
    Iterable<FlowEntity> _plus_14 = Iterables.<FlowEntity>concat(_plus_13, _tcpEgressFlowPort2_1);
    Iterable<FlowEntity> _remoteFlows = this.remoteFlows();
    Iterable<FlowEntity> _plus_15 = Iterables.<FlowEntity>concat(_plus_14, _remoteFlows);
    Iterable<FlowEntity> _remoteFlows_1 = this.remoteFlows();
    return Iterables.<FlowEntity>concat(_plus_15, _remoteFlows_1);
  }
  
  protected Iterable<FlowEntity> udpFlows() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _udpEgressFlowsPort1 = this.udpEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _udpEgressFlowsPort1);
    List<FlowEntity> _fixedIngressFlowsPort2 = this.fixedIngressFlowsPort2();
    Iterable<FlowEntity> _plus_6 = Iterables.<FlowEntity>concat(_plus_5, _fixedIngressFlowsPort2);
    List<FlowEntity> _fixedConntrackIngressFlowsPort2 = this.fixedConntrackIngressFlowsPort2();
    Iterable<FlowEntity> _plus_7 = Iterables.<FlowEntity>concat(_plus_6, _fixedConntrackIngressFlowsPort2);
    List<FlowEntity> _udpIngressFlowsPort2 = this.udpIngressFlowsPort2();
    Iterable<FlowEntity> _plus_8 = Iterables.<FlowEntity>concat(_plus_7, _udpIngressFlowsPort2);
    List<FlowEntity> _udpIngressFlowsPort2_1 = this.udpIngressFlowsPort2();
    Iterable<FlowEntity> _plus_9 = Iterables.<FlowEntity>concat(_plus_8, _udpIngressFlowsPort2_1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort2 = this.fixedEgressL2BroadcastFlowsPort2();
    Iterable<FlowEntity> _plus_10 = Iterables.<FlowEntity>concat(_plus_9, _fixedEgressL2BroadcastFlowsPort2);
    List<FlowEntity> _fixedIngressL3BroadcastFlows_1 = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_11 = Iterables.<FlowEntity>concat(_plus_10, _fixedIngressL3BroadcastFlows_1);
    Iterable<FlowEntity> _fixedEgressFlowsPort2 = this.fixedEgressFlowsPort2();
    Iterable<FlowEntity> _plus_12 = Iterables.<FlowEntity>concat(_plus_11, _fixedEgressFlowsPort2);
    List<FlowEntity> _fixedConntrackEgressFlowsPort2 = this.fixedConntrackEgressFlowsPort2();
    Iterable<FlowEntity> _plus_13 = Iterables.<FlowEntity>concat(_plus_12, _fixedConntrackEgressFlowsPort2);
    List<FlowEntity> _udpEgressFlowsPort2 = this.udpEgressFlowsPort2();
    Iterable<FlowEntity> _plus_14 = Iterables.<FlowEntity>concat(_plus_13, _udpEgressFlowsPort2);
    Iterable<FlowEntity> _remoteFlows = this.remoteFlows();
    Iterable<FlowEntity> _plus_15 = Iterables.<FlowEntity>concat(_plus_14, _remoteFlows);
    Iterable<FlowEntity> _remoteFlows_1 = this.remoteFlows();
    return Iterables.<FlowEntity>concat(_plus_15, _remoteFlows_1);
  }
  
  protected Iterable<FlowEntity> icmpFlows() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _icmpIngressFlowsPort1 = this.icmpIngressFlowsPort1();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _icmpIngressFlowsPort1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _fixedIngressFlowsPort2 = this.fixedIngressFlowsPort2();
    Iterable<FlowEntity> _plus_6 = Iterables.<FlowEntity>concat(_plus_5, _fixedIngressFlowsPort2);
    List<FlowEntity> _fixedConntrackIngressFlowsPort2 = this.fixedConntrackIngressFlowsPort2();
    Iterable<FlowEntity> _plus_7 = Iterables.<FlowEntity>concat(_plus_6, _fixedConntrackIngressFlowsPort2);
    List<FlowEntity> _icmpIngressFlowsPort2 = this.icmpIngressFlowsPort2();
    Iterable<FlowEntity> _plus_8 = Iterables.<FlowEntity>concat(_plus_7, _icmpIngressFlowsPort2);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort2 = this.fixedEgressL2BroadcastFlowsPort2();
    Iterable<FlowEntity> _plus_9 = Iterables.<FlowEntity>concat(_plus_8, _fixedEgressL2BroadcastFlowsPort2);
    List<FlowEntity> _fixedIngressL3BroadcastFlows_1 = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_10 = Iterables.<FlowEntity>concat(_plus_9, _fixedIngressL3BroadcastFlows_1);
    Iterable<FlowEntity> _fixedEgressFlowsPort2 = this.fixedEgressFlowsPort2();
    Iterable<FlowEntity> _plus_11 = Iterables.<FlowEntity>concat(_plus_10, _fixedEgressFlowsPort2);
    List<FlowEntity> _fixedConntrackEgressFlowsPort2 = this.fixedConntrackEgressFlowsPort2();
    Iterable<FlowEntity> _plus_12 = Iterables.<FlowEntity>concat(_plus_11, _fixedConntrackEgressFlowsPort2);
    List<FlowEntity> _icmpEgressFlowsPort2 = this.icmpEgressFlowsPort2();
    Iterable<FlowEntity> _plus_13 = Iterables.<FlowEntity>concat(_plus_12, _icmpEgressFlowsPort2);
    List<FlowEntity> _icmpEgressFlowsPort2_1 = this.icmpEgressFlowsPort2();
    Iterable<FlowEntity> _plus_14 = Iterables.<FlowEntity>concat(_plus_13, _icmpEgressFlowsPort2_1);
    Iterable<FlowEntity> _remoteFlows = this.remoteFlows();
    Iterable<FlowEntity> _plus_15 = Iterables.<FlowEntity>concat(_plus_14, _remoteFlows);
    Iterable<FlowEntity> _remoteFlows_1 = this.remoteFlows();
    return Iterables.<FlowEntity>concat(_plus_15, _remoteFlows_1);
  }
  
  protected Iterable<FlowEntity> dstRangeFlows() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _udpIngressPortRangeFlows = this.udpIngressPortRangeFlows();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _udpIngressPortRangeFlows);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _tcpEgressRangeFlows = this.tcpEgressRangeFlows();
    return Iterables.<FlowEntity>concat(_plus_5, _tcpEgressRangeFlows);
  }
  
  protected Iterable<FlowEntity> dstAllFlows() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _udpIngressAllFlows = this.udpIngressAllFlows();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _udpIngressAllFlows);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _tcpEgressAllFlows = this.tcpEgressAllFlows();
    return Iterables.<FlowEntity>concat(_plus_5, _tcpEgressAllFlows);
  }
  
  protected Iterable<FlowEntity> icmpFlowsForTwoAclsHavingSameRules() {
    List<FlowEntity> _fixedIngressFlowsPort3 = this.fixedIngressFlowsPort3();
    List<FlowEntity> _fixedConntrackIngressFlowsPort3 = this.fixedConntrackIngressFlowsPort3();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort3, _fixedConntrackIngressFlowsPort3);
    List<FlowEntity> _icmpIngressFlowsPort3 = this.icmpIngressFlowsPort3();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _icmpIngressFlowsPort3);
    List<FlowEntity> _fixedEgressFlowsPort3 = this.fixedEgressFlowsPort3();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedEgressFlowsPort3);
    List<FlowEntity> _fixedConntrackEgressFlowsPort3 = this.fixedConntrackEgressFlowsPort3();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedConntrackEgressFlowsPort3);
    List<FlowEntity> _icmpEgressFlowsPort3 = this.icmpEgressFlowsPort3();
    return Iterables.<FlowEntity>concat(_plus_3, _icmpEgressFlowsPort3);
  }
  
  protected Iterable<FlowEntity> aapWithIpv4AllFlows() {
    Iterable<FlowEntity> _icmpFlows = this.icmpFlows();
    List<FlowEntity> _aapIpv4AllFlowsPort2 = this.aapIpv4AllFlowsPort2();
    return Iterables.<FlowEntity>concat(_icmpFlows, _aapIpv4AllFlowsPort2);
  }
  
  protected Iterable<FlowEntity> aapFlows() {
    Iterable<FlowEntity> _icmpFlows = this.icmpFlows();
    List<FlowEntity> _aapRemoteFlowsPort1 = this.aapRemoteFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_icmpFlows, _aapRemoteFlowsPort1);
    List<FlowEntity> _aapRemoteFlowsPort1_1 = this.aapRemoteFlowsPort1();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _aapRemoteFlowsPort1_1);
    List<FlowEntity> _aapFlowsPort2 = this.aapFlowsPort2();
    return Iterables.<FlowEntity>concat(_plus_1, _aapFlowsPort2);
  }
  
  protected Iterable<FlowEntity> multipleAcl() {
    List<FlowEntity> _fixedIngressFlowsPort1 = this.fixedIngressFlowsPort1();
    List<FlowEntity> _fixedConntrackIngressFlowsPort1 = this.fixedConntrackIngressFlowsPort1();
    Iterable<FlowEntity> _plus = Iterables.<FlowEntity>concat(_fixedIngressFlowsPort1, _fixedConntrackIngressFlowsPort1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort1 = this.fixedEgressL2BroadcastFlowsPort1();
    Iterable<FlowEntity> _plus_1 = Iterables.<FlowEntity>concat(_plus, _fixedEgressL2BroadcastFlowsPort1);
    List<FlowEntity> _fixedIngressL3BroadcastFlows = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_2 = Iterables.<FlowEntity>concat(_plus_1, _fixedIngressL3BroadcastFlows);
    Iterable<FlowEntity> _fixedEgressFlowsPort1 = this.fixedEgressFlowsPort1();
    Iterable<FlowEntity> _plus_3 = Iterables.<FlowEntity>concat(_plus_2, _fixedEgressFlowsPort1);
    List<FlowEntity> _fixedConntrackEgressFlowsPort1 = this.fixedConntrackEgressFlowsPort1();
    Iterable<FlowEntity> _plus_4 = Iterables.<FlowEntity>concat(_plus_3, _fixedConntrackEgressFlowsPort1);
    List<FlowEntity> _etherEgressFlowsPort1 = this.etherEgressFlowsPort1();
    Iterable<FlowEntity> _plus_5 = Iterables.<FlowEntity>concat(_plus_4, _etherEgressFlowsPort1);
    List<FlowEntity> _etherEgressFlowsPort1_1 = this.etherEgressFlowsPort1();
    Iterable<FlowEntity> _plus_6 = Iterables.<FlowEntity>concat(_plus_5, _etherEgressFlowsPort1_1);
    List<FlowEntity> _etherEgressFlowsPort1_2 = this.etherEgressFlowsPort1();
    Iterable<FlowEntity> _plus_7 = Iterables.<FlowEntity>concat(_plus_6, _etherEgressFlowsPort1_2);
    List<FlowEntity> _etherEgressFlowsPort1AfterDelete = this.etherEgressFlowsPort1AfterDelete();
    Iterable<FlowEntity> _plus_8 = Iterables.<FlowEntity>concat(_plus_7, _etherEgressFlowsPort1AfterDelete);
    List<FlowEntity> _etherEgressFlowsPort1AfterDelete_1 = this.etherEgressFlowsPort1AfterDelete();
    Iterable<FlowEntity> _plus_9 = Iterables.<FlowEntity>concat(_plus_8, _etherEgressFlowsPort1AfterDelete_1);
    List<FlowEntity> _fixedIngressFlowsPort2 = this.fixedIngressFlowsPort2();
    Iterable<FlowEntity> _plus_10 = Iterables.<FlowEntity>concat(_plus_9, _fixedIngressFlowsPort2);
    List<FlowEntity> _fixedConntrackIngressFlowsPort2 = this.fixedConntrackIngressFlowsPort2();
    Iterable<FlowEntity> _plus_11 = Iterables.<FlowEntity>concat(_plus_10, _fixedConntrackIngressFlowsPort2);
    List<FlowEntity> _etherIngressFlowsPort2 = this.etherIngressFlowsPort2();
    Iterable<FlowEntity> _plus_12 = Iterables.<FlowEntity>concat(_plus_11, _etherIngressFlowsPort2);
    List<FlowEntity> _etherIngressFlowsPort2_1 = this.etherIngressFlowsPort2();
    Iterable<FlowEntity> _plus_13 = Iterables.<FlowEntity>concat(_plus_12, _etherIngressFlowsPort2_1);
    List<FlowEntity> _etherIngressFlowsPort2AfterDelete = this.etherIngressFlowsPort2AfterDelete();
    Iterable<FlowEntity> _plus_14 = Iterables.<FlowEntity>concat(_plus_13, _etherIngressFlowsPort2AfterDelete);
    List<FlowEntity> _etherIngressFlowsPort2AfterDelete_1 = this.etherIngressFlowsPort2AfterDelete();
    Iterable<FlowEntity> _plus_15 = Iterables.<FlowEntity>concat(_plus_14, _etherIngressFlowsPort2AfterDelete_1);
    List<FlowEntity> _fixedEgressL2BroadcastFlowsPort2 = this.fixedEgressL2BroadcastFlowsPort2();
    Iterable<FlowEntity> _plus_16 = Iterables.<FlowEntity>concat(_plus_15, _fixedEgressL2BroadcastFlowsPort2);
    List<FlowEntity> _fixedIngressL3BroadcastFlows_1 = this.fixedIngressL3BroadcastFlows();
    Iterable<FlowEntity> _plus_17 = Iterables.<FlowEntity>concat(_plus_16, _fixedIngressL3BroadcastFlows_1);
    Iterable<FlowEntity> _fixedEgressFlowsPort2 = this.fixedEgressFlowsPort2();
    Iterable<FlowEntity> _plus_18 = Iterables.<FlowEntity>concat(_plus_17, _fixedEgressFlowsPort2);
    List<FlowEntity> _fixedConntrackEgressFlowsPort2 = this.fixedConntrackEgressFlowsPort2();
    Iterable<FlowEntity> _plus_19 = Iterables.<FlowEntity>concat(_plus_18, _fixedConntrackEgressFlowsPort2);
    List<FlowEntity> _etheregressFlowPort2 = this.etheregressFlowPort2();
    Iterable<FlowEntity> _plus_20 = Iterables.<FlowEntity>concat(_plus_19, _etheregressFlowPort2);
    Iterable<FlowEntity> _remoteFlows = this.remoteFlows();
    Iterable<FlowEntity> _plus_21 = Iterables.<FlowEntity>concat(_plus_20, _remoteFlows);
    List<FlowEntity> _remoteIngressFlowsPort1 = this.remoteIngressFlowsPort1();
    Iterable<FlowEntity> _plus_22 = Iterables.<FlowEntity>concat(_plus_21, _remoteIngressFlowsPort1);
    List<FlowEntity> _remoteIngressFlowsPort1_1 = this.remoteIngressFlowsPort1();
    Iterable<FlowEntity> _plus_23 = Iterables.<FlowEntity>concat(_plus_22, _remoteIngressFlowsPort1_1);
    List<FlowEntity> _remoteEgressFlowsPort1 = this.remoteEgressFlowsPort1();
    Iterable<FlowEntity> _plus_24 = Iterables.<FlowEntity>concat(_plus_23, _remoteEgressFlowsPort1);
    List<FlowEntity> _remoteEgressFlowsPort1_1 = this.remoteEgressFlowsPort1();
    Iterable<FlowEntity> _plus_25 = Iterables.<FlowEntity>concat(_plus_24, _remoteEgressFlowsPort1_1);
    List<FlowEntity> _fixedEgressArpFlowsPort1 = this.fixedEgressArpFlowsPort1();
    Iterable<FlowEntity> _plus_26 = Iterables.<FlowEntity>concat(_plus_25, _fixedEgressArpFlowsPort1);
    List<FlowEntity> _fixedEgressArpFlowsPort2 = this.fixedEgressArpFlowsPort2();
    Iterable<FlowEntity> _plus_27 = Iterables.<FlowEntity>concat(_plus_26, _fixedEgressArpFlowsPort2);
    List<FlowEntity> _tcpEgressFlowPort2WithRemoteIpSg = this.tcpEgressFlowPort2WithRemoteIpSg();
    Iterable<FlowEntity> _plus_28 = Iterables.<FlowEntity>concat(_plus_27, _tcpEgressFlowPort2WithRemoteIpSg);
    List<FlowEntity> _tcpIngressFlowPort1WithMultipleSG = this.tcpIngressFlowPort1WithMultipleSG();
    Iterable<FlowEntity> _plus_29 = Iterables.<FlowEntity>concat(_plus_28, _tcpIngressFlowPort1WithMultipleSG);
    List<FlowEntity> _tcpIngressFlowPort1WithMultipleSG_1 = this.tcpIngressFlowPort1WithMultipleSG();
    Iterable<FlowEntity> _plus_30 = Iterables.<FlowEntity>concat(_plus_29, _tcpIngressFlowPort1WithMultipleSG_1);
    List<FlowEntity> _etherIngressFlowsPort1WithRemoteIpSg = this.etherIngressFlowsPort1WithRemoteIpSg("10.0.0.1", "ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7");
    Iterable<FlowEntity> _plus_31 = Iterables.<FlowEntity>concat(_plus_30, _etherIngressFlowsPort1WithRemoteIpSg);
    List<FlowEntity> _etherIngressFlowsPort1WithRemoteIpSg_1 = this.etherIngressFlowsPort1WithRemoteIpSg("10.0.0.2", "ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7");
    Iterable<FlowEntity> _plus_32 = Iterables.<FlowEntity>concat(_plus_31, _etherIngressFlowsPort1WithRemoteIpSg_1);
    List<FlowEntity> _etherIngressFlowsPort1WithRemoteIpSgAfterDelete = this.etherIngressFlowsPort1WithRemoteIpSgAfterDelete("10.0.0.2", "ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress987_IPv4_FlowAfterRuleDeleted");
    Iterable<FlowEntity> _plus_33 = Iterables.<FlowEntity>concat(_plus_32, _etherIngressFlowsPort1WithRemoteIpSgAfterDelete);
    List<FlowEntity> _etherIngressFlowsPort2WithRemoteIpSg = this.etherIngressFlowsPort2WithRemoteIpSg();
    Iterable<FlowEntity> _plus_34 = Iterables.<FlowEntity>concat(_plus_33, _etherIngressFlowsPort2WithRemoteIpSg);
    Iterable<FlowEntity> _remoteFlows_1 = this.remoteFlows();
    return Iterables.<FlowEntity>concat(_plus_34, _remoteFlows_1);
  }
  
  protected List<FlowEntity> etherIngressFlowsPort1WithRemoteIpSg(final String ip, final String theFlowId) {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> etherIngressFlowsPort1WithRemoteIpSgAfterDelete(final String ip, final String theFlowId) {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nThe method or field EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE is undefined for the type Class<NwConstants>");
  }
  
  protected List<FlowEntity> etherIngressFlowsPort2WithRemoteIpSg() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpEgressFlowPort2WithRemoteIpSg() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpIngressFlowPort1WithMultipleSG() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> aapIpv4AllFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_0.0.0.0/0_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_0.0.0.0/0_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_L2Broadcast_123_987_0D:AA:D8:42:30:A4");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchMetadata)));
      it.setPriority(61005);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3));
  }
  
  protected List<FlowEntity> aapRemoteFlowsPort1() {
    FlowEntity _remoteIngressFlowsPort = this.remoteIngressFlowsPort("10.0.0.100");
    FlowEntity _remoteIngressFlowsPort_1 = this.remoteIngressFlowsPort("10.0.0.101");
    FlowEntity _remoteEgressFlowsPort = this.remoteEgressFlowsPort("10.0.0.100");
    FlowEntity _remoteEgressFlowsPort_1 = this.remoteEgressFlowsPort("10.0.0.101");
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_remoteIngressFlowsPort, _remoteIngressFlowsPort_1, _remoteEgressFlowsPort, _remoteEgressFlowsPort_1));
  }
  
  protected List<FlowEntity> aapFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_10.0.0.100/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.100", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_10.0.0.100/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.100", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:A4_10.0.0.101/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.101", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:A4_10.0.0.101/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.101", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    FlowEntityBuilder _flowEntityBuilder_4 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_4 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:F410.0.0.100/32");
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
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.100/32");
      MatchArpSpa _matchArpSpa = new MatchArpSpa(_ipv4Prefix);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchArpSpa, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_4 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_4, _function_4);
    FlowEntityBuilder _flowEntityBuilder_5 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_5 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_ARP_123_987_0D:AA:D8:42:30:A410.0.0.101/32");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.101/32");
      MatchArpSpa _matchArpSpa = new MatchArpSpa(_ipv4Prefix);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchArpSpa, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    FlowEntityBuilder _flowEntityBuilder_6 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_6 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:A4_Permit_");
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
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    FlowEntityBuilder _flowEntityBuilder_7 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_7 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:A4_Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 547));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 546));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
    FlowEntityBuilder _flowEntityBuilder_8 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_8 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_L2Broadcast_123_987_0D:AA:D8:42:30:A4");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:A4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchMetadata)));
      it.setPriority(61005);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_8 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_8, _function_8);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7, _doubleGreaterThan_8));
  }
  
  protected List<FlowEntity> fixedConntrackIngressFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_10.0.0.1/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.1", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
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
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  protected List<FlowEntity> etherIngressFlowsPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> etherIngressFlowsPort2AfterDelete() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nThe method or field EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE is undefined for the type Class<NwConstants>");
  }
  
  protected List<FlowEntity> fixedConntrackEgressFlowsPort1() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_10.0.0.1/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F3");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.1", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
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
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  protected List<FlowEntity> fixedConntrackIngressFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_10.0.0.2/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.2", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
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
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  protected List<FlowEntity> fixedConntrackEgressFlowsPort2() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_10.0.0.2/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F4");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.2", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
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
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  protected List<FlowEntity> fixedConntrackIngressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_10.0.0.3/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.3", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
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
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  protected List<FlowEntity> fixedConntrackEgressFlowsPort3() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_10.0.0.3/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F5");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.3", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
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
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2));
  }
  
  public static List<FlowEntity> fixedConntrackIngressFlowsPort4() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_10.0.0.4/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F6");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.4", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F6");
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3));
  }
  
  public static List<FlowEntity> fixedConntrackEgressFlowsPort4() {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_10.0.0.4/32_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F6");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.4", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc");
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress("0D:AA:D8:42:30:F6");
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_1 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_1, _function_1);
    FlowEntityBuilder _flowEntityBuilder_2 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_2 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_2 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_2, _function_2);
    FlowEntityBuilder _flowEntityBuilder_3 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_3 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_3 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_3, _function_3);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3));
  }
  
  protected List<FlowEntity> etherEgressFlowsPort1() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> etherEgressFlowsPort1AfterDelete() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nThe method or field INGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE is undefined for the type Class<NwConstants>");
  }
  
  protected List<FlowEntity> etheregressFlowPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpIngressFlowPort1() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpIngressFlowPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpEgressFlowPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> udpEgressFlowsPort1() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> udpIngressFlowsPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> udpEgressFlowsPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> icmpIngressFlowsPort1() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> icmpIngressFlowsPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> icmpEgressFlowsPort2() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> udpIngressPortRangeFlows() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpEgressRangeFlows() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> udpIngressAllFlows() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> tcpEgressAllFlows() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> icmpIngressFlowsPort3() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved."
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  protected List<FlowEntity> icmpEgressFlowsPort3() {
    throw new Error("Unresolved compilation problems:"
      + "\nActionNxConntrack.NxCtMark cannot be resolved.");
  }
  
  @Override
  public List<FlowEntity> expectedFlows(final String mac) {
    FlowEntityBuilder _flowEntityBuilder = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v4123_987__Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 68));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 67));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder, _function);
    FlowEntityBuilder _flowEntityBuilder_1 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_1 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Ingress_DHCP_Server_v6_123_987___Permit_");
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 220));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 546));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 547));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _nxMatchRegister)));
      it.setPriority(63010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
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
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
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
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
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
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
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
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_5 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_5, _function_5);
    FlowEntityBuilder _flowEntityBuilder_6 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_6 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_6 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_6, _function_6);
    FlowEntityBuilder _flowEntityBuilder_7 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_7 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchMetadata, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.INGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_7 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_7, _function_7);
    FlowEntityBuilder _flowEntityBuilder_8 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_8 = (FlowEntityBuilder it) -> {
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
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 67));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 68));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_8 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_8, _function_8);
    FlowEntityBuilder _flowEntityBuilder_9 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_9 = (FlowEntityBuilder it) -> {
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
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 547));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 546));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata, _matchEthernetSource)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_9 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_9, _function_9);
    FlowEntityBuilder _flowEntityBuilder_10 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_10 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Server_v4123_987__Drop_");
      it.setFlowName("ACL");
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList()));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 68));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 67));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_10 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_10, _function_10);
    FlowEntityBuilder _flowEntityBuilder_11 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_11 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId("Egress_DHCP_Server_v6_123_987__Drop_");
      it.setFlowName("ACL");
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList()));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(34525L);
      MatchIpProtocol _matchIpProtocol = new MatchIpProtocol(((short) 17));
      MatchUdpDestinationPort _matchUdpDestinationPort = new MatchUdpDestinationPort(((short) 546));
      MatchUdpSourcePort _matchUdpSourcePort = new MatchUdpSourcePort(((short) 547));
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchIpProtocol, _matchUdpDestinationPort, _matchUdpSourcePort, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_11 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_11, _function_11);
    FlowEntityBuilder _flowEntityBuilder_12 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_12 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_12 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_12, _function_12);
    FlowEntityBuilder _flowEntityBuilder_13 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_13 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_13 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_13, _function_13);
    FlowEntityBuilder _flowEntityBuilder_14 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_14 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_14 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_14, _function_14);
    FlowEntityBuilder _flowEntityBuilder_15 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_15 = (FlowEntityBuilder it) -> {
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
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_15 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_15, _function_15);
    FlowEntityBuilder _flowEntityBuilder_16 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_16 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_New");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(32L, 32L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(50);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_16 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_16, _function_16);
    FlowEntityBuilder _flowEntityBuilder_17 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_17 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100481L));
      it.setFlowId("Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid");
      it.setFlowName("ACL");
      ActionDrop _actionDrop = new ActionDrop();
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionDrop)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      NxMatchRegister _nxMatchRegister = new NxMatchRegister(NxmNxReg6.class, 252672L, Long.valueOf(268435200L));
      NxMatchCtState _nxMatchCtState = new NxMatchCtState(48L, 48L);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_nxMatchRegister, _nxMatchCtState)));
      it.setPriority(62015);
      it.setTableId(NwConstants.EGRESS_ACL_FILTER_TABLE);
    };
    FlowEntity _doubleGreaterThan_17 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_17, _function_17);
    FlowEntityBuilder _flowEntityBuilder_18 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_18 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Ingress_Fixed_Conntrk_123_" + mac) + "_10.0.0.1/32_Recirc"));
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetDestination _matchEthernetDestination = new MatchEthernetDestination(_macAddress);
      MatchEthernetType _matchEthernetType_1 = new MatchEthernetType(2048L);
      MatchIpv4Destination _matchIpv4Destination = new MatchIpv4Destination("10.0.0.1", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchEthernetDestination, _matchEthernetType_1, _matchIpv4Destination)));
      it.setPriority(61010);
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_18 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_18, _function_18);
    FlowEntityBuilder _flowEntityBuilder_19 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_19 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Egress_Fixed_Conntrk_123_" + mac) + "_10.0.0.1/32_Recirc"));
      it.setFlowName("ACL");
      ActionNxConntrack _actionNxConntrack = new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE);
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxConntrack)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2048L);
      MatchIpv4Source _matchIpv4Source = new MatchIpv4Source("10.0.0.1", "32");
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchEthernetType, _matchIpv4Source)));
      it.setPriority(61010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_19 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_19, _function_19);
    FlowEntityBuilder _flowEntityBuilder_20 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_20 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId((("Egress_ARP_123_987_" + mac) + "10.0.0.1/32"));
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MatchEthernetType _matchEthernetType = new MatchEthernetType(2054L);
      MacAddress _macAddress = new MacAddress(mac);
      MatchArpSha _matchArpSha = new MatchArpSha(_macAddress);
      MacAddress _macAddress_1 = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress_1);
      Ipv4Prefix _ipv4Prefix = new Ipv4Prefix("10.0.0.1/32");
      MatchArpSpa _matchArpSpa = new MatchArpSpa(_ipv4Prefix);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetType, _matchArpSha, _matchEthernetSource, _matchArpSpa, _matchMetadata)));
      it.setPriority(63010);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_20 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_20, _function_20);
    FlowEntityBuilder _flowEntityBuilder_21 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_21 = (FlowEntityBuilder it) -> {
      it.setDpnId(BigInteger.valueOf(123L));
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setFlowId(("Egress_L2Broadcast_123_987_" + mac));
      it.setFlowName("ACL");
      ActionNxResubmit _actionNxResubmit = new ActionNxResubmit(((short) 17));
      InstructionApplyActions _instructionApplyActions = new InstructionApplyActions(
        Collections.<ActionInfo>unmodifiableList(CollectionLiterals.<ActionInfo>newArrayList(_actionNxResubmit)));
      it.setInstructionInfoList(Collections.<InstructionInfo>unmodifiableList(CollectionLiterals.<InstructionInfo>newArrayList(_instructionApplyActions)));
      MacAddress _macAddress = new MacAddress(mac);
      MatchEthernetSource _matchEthernetSource = new MatchEthernetSource(_macAddress);
      MatchMetadata _matchMetadata = new MatchMetadata(BigInteger.valueOf(1085217976614912L), MetaDataUtil.METADATA_MASK_LPORT_TAG);
      it.setMatchInfoList(Collections.<MatchInfoBase>unmodifiableList(CollectionLiterals.<MatchInfoBase>newArrayList(_matchEthernetSource, _matchMetadata)));
      it.setPriority(61005);
      it.setTableId(NwConstants.INGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_21 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_21, _function_21);
    FlowEntityBuilder _flowEntityBuilder_22 = new FlowEntityBuilder();
    final Procedure1<FlowEntityBuilder> _function_22 = (FlowEntityBuilder it) -> {
      it.setCookie(BigInteger.valueOf(110100480L));
      it.setDpnId(BigInteger.valueOf(123L));
      it.setFlowId("Ingress_v4_Broadcast_123_987_10.0.0.255_Permit");
      it.setFlowName("ACL");
      it.setHardTimeOut(0);
      it.setIdleTimeOut(0);
      InstructionGotoTable _instructionGotoTable = new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE);
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
      it.setTableId(NwConstants.EGRESS_ACL_TABLE);
    };
    FlowEntity _doubleGreaterThan_22 = XtendBuilderExtensions.<FlowEntity, FlowEntityBuilder>operator_doubleGreaterThan(_flowEntityBuilder_22, _function_22);
    return Collections.<FlowEntity>unmodifiableList(CollectionLiterals.<FlowEntity>newArrayList(_doubleGreaterThan, _doubleGreaterThan_1, _doubleGreaterThan_2, _doubleGreaterThan_3, _doubleGreaterThan_4, _doubleGreaterThan_5, _doubleGreaterThan_6, _doubleGreaterThan_7, _doubleGreaterThan_8, _doubleGreaterThan_9, _doubleGreaterThan_10, _doubleGreaterThan_11, _doubleGreaterThan_12, _doubleGreaterThan_13, _doubleGreaterThan_14, _doubleGreaterThan_15, _doubleGreaterThan_16, _doubleGreaterThan_17, _doubleGreaterThan_18, _doubleGreaterThan_19, _doubleGreaterThan_20, _doubleGreaterThan_21, _doubleGreaterThan_22));
  }
}
