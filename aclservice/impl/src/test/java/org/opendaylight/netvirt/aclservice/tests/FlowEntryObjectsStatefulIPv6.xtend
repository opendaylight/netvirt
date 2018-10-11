/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.NwConstants
import org.opendaylight.genius.mdsalutil.actions.ActionDrop
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack
import org.opendaylight.genius.mdsalutil.actions.ActionNxCtClear
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.netvirt.aclservice.utils.AclConstants
import java.math.BigInteger
import java.util.Collections

import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination

class FlowEntryObjectsStatefulIPv6 extends FlowEntryObjectsBaseIpv6 {

    protected def etherFlows(String ip1 ,String ip2, String prefix) {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1(ip1, prefix)
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + etherEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2(ip2, prefix)
        + etherIngressFlowsPort2
        + etherIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort2(ip2, prefix)
        + fixedConntrackEgressFlowsPort2(ip2, prefix)
        + etheregressFlowPort2
        + remoteEgressFlowsPort1(ip1, prefix)
        + remoteEgressFlowsPort2(ip2, prefix)
        + ingressCommitNonConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + ingressDispatcherFirst
        + ingressDispatcherFirst
        + ingressDispatcherLast
        + ingressDispatcherLast
    }

    protected def tcpFlows(String ip1 ,String ip2, String prefix) {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1(ip1, prefix)
        + tcpIngressFlowPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2(ip2, prefix)
        + tcpIngressFlowPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort2(ip2, prefix)
        + fixedConntrackEgressFlowsPort2(ip2, prefix)
        + tcpEgressFlowPort2
        + tcpEgressFlowPort2
        + egressDispatcherFirst
        + egressDispatcherFirst
        + egressDispatcherLast
        + egressDispatcherLast
        + remoteIngressFlowsPort1(ip1, prefix)
        + remoteIngressFlowsPort2(ip2, prefix)
        + ingressCommitNonConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + egressfixedAclMissDrop1

    }

    protected def udpFlows(String ip1 ,String ip2, String prefix) {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1(ip1, prefix)
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + udpEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2(ip2, prefix)
        + udpIngressFlowsPort2
        + udpIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort2(ip2, prefix)
        + fixedConntrackEgressFlowsPort2(ip2, prefix)
        + udpEgressFlowsPort2
        + ingressDispatcherFirst
        + ingressDispatcherFirst
        + ingressDispatcherLast
        + ingressDispatcherLast
        + remoteEgressFlowsPort1(ip1, prefix)
        + remoteEgressFlowsPort2(ip2, prefix)
        + ingressCommitNonConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + egressfixedAclMissDrop1
    }

    protected def icmpFlows(String ip1 ,String ip2, String prefix) {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1(ip1, prefix)
        + icmpIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2(ip2, prefix)
        + icmpIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort2(ip2, prefix)
        + fixedConntrackEgressFlowsPort2(ip2, prefix)
        + icmpEgressFlowsPort2
        + icmpEgressFlowsPort2
        + egressDispatcherFirst
        + egressDispatcherFirst
        + egressDispatcherLast
        + egressDispatcherLast
        + remoteIngressFlowsPort1(ip1, prefix)
        + remoteIngressFlowsPort2(ip2, prefix)
        + ingressCommitNonConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + egressfixedAclMissDrop1
    }

    protected def dstRangeFlows(String ip1, String prefix) {
        fixedIngressFlowsPort1
        +fixedConntrackIngressFlowsPort1(ip1, prefix)
        + udpIngressPortRangeFlows
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + tcpEgressRangeFlows
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1

    }

    protected def dstAllFlows(String ip1, String prefix) {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1(ip1, prefix)
        + udpIngressAllFlows
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + tcpEgressAllFlows
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
    }

    protected def icmpFlowsForTwoAclsHavingSameRules(String ip3, String prefix) {
        fixedIngressFlowsPort3
        + fixedConntrackIngressFlowsPort3(ip3, prefix)
        + icmpIngressFlowsPort3
        + fixedEgressFlowsPort3(ip3, prefix)
        + fixedConntrackEgressFlowsPort3(ip3, prefix)
        + icmpEgressFlowsPort3
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressL2BroadcastFlowsPort3
    }

    protected def aapWithIpv4AllFlows(String ip1 ,String ip2, String prefix) {
        icmpFlows(ip1, ip2, prefix)
        + aapIpv4AllFlowsPort2
    }

    protected def aapFlows(String ip1 ,String ip2, String ip100, String ip101, String prefix) {
        icmpFlows(ip1, ip2, prefix)
        + aapRemoteFlowsPort1(ip100, ip101, prefix)
        + aapFlowsPort2(ip100, ip101, prefix)
    }

    protected def multipleAcl(String ip1 ,String ip2, String prefix) {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1(ip1, prefix)
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort1(ip1, prefix)
        + fixedConntrackEgressFlowsPort1(ip1, prefix)
        + etherEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2(ip2, prefix)
        + etherIngressFlowsPort2
        + etherIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows()
        + fixedEgressFlowsPort2(ip2, prefix)
        + fixedConntrackEgressFlowsPort2(ip2, prefix)
        + etheregressFlowPort2
        + remoteEgressFlowsPort1(ip1, prefix)
        + remoteEgressFlowsPort2(ip2, prefix)
        + tcpEgressFlowPort2WithRemoteIpSg
        + tcpEgressFlowPort2WithRemoteIpSg
        + tcpIngressFlowPort1WithMultipleSG
        + tcpIngressFlowPort1WithMultipleSG
        + ingressCommitNonConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + remoteEgressFlowsPort3(ip2, prefix)
        + egressDispatcherLast1
        + egressDispatcherFirst1
        + ingressDispatcherLast
        + ingressDispatcherFirst
        + egressDispatcherLast1
        + egressDispatcherFirst1
        + ingressDispatcherLast
        + ingressDispatcherFirst
        + ingressDispatcherFirst
        + ingressDispatcherFirst
        + ingressDispatcherLast
        + ingressDispatcherLast

    }

    protected def tcpEgressFlowPort2WithRemoteIpSg() {
        val theFlowId1 ="TCP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21"
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614976bi, 1152920405111996400bi)
                ]
                priority = IdHelper.getId(theFlowId1)
                tableId = NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    protected def tcpIngressFlowPort1WithMultipleSG() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def aapIpv4AllFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_0.0.0.0/0"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L)
                ]
                priority = 61010
                tableId = 210 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_0.0.0.0/0"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L)
                ]
                priority = 61010
                tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F4_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                    new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547 as short),
                    new MatchUdpSourcePort(546 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }

    protected def aapRemoteFlowsPort1(String ip100, String ip101, String prefix) {
        #[
            remoteIngressFlowsPort(ip100, prefix),
            remoteIngressFlowsPort(ip101, prefix)
        ]
    }

    protected def aapFlowsPort2(String ip100, String ip101, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_" + ip100 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                   new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip100 + "/"+ prefix)
                ]
                priority = 61010
                tableId = 210 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_" + ip100 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip100 + "/"+ prefix)
                ]
                priority = 61010
                tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:A4_" + ip101 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip101 + "/"+ prefix)
                ]
                priority = 61010
                tableId = 210 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:A4_" + ip101 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip101 + "/"+ prefix)
                ]
                priority = 61010
                tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:A4_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547 as short),
                    new MatchUdpSourcePort(546 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:A4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F4_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547 as short),
                    new MatchUdpSourcePort(546 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:A4"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 61005
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }

    protected def fixedConntrackIngressFlowsPort1(String ip1, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_" + ip1 + "/" + prefix
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
            ]
            matchInfoList = #[
                new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F3")),
                new MatchEthernetType(34525L),
                new MatchIpv6Destination(ip1 + "/" + prefix)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 243 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def etherIngressFlowsPort2() {
        val theFlowId = "ETHERnullIngress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                     new InstructionGotoTable(NwConstants.EGRESS_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(32bi, 16777200bi)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort1(String ip1, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_" + ip1 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip1 + "/" + prefix)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                       new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                   new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                   new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def fixedConntrackIngressFlowsPort2(String ip2, String prefix) {
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_" + ip2 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip2 + "/" + prefix)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort2(String ip2, String prefix){
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_" + ip2 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip2 + "/"+ prefix)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 213 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def fixedConntrackIngressFlowsPort3(String ip3, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_" + ip3 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip3 + "/" + prefix)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort3(String ip3, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_" + ip3 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip3 + "/"+ prefix)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    static def fixedConntrackIngressFlowsPort4(String ip4, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_" + ip4 + "/" + prefix + "_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip4 + "/" + prefix)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(34525L)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(32L, 32L)
                ]
                priority = 50
                tableId = NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    static def fixedConntrackEgressFlowsPort4(String ip4, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_" + ip4 + "/" + prefix + "_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip4 + "/"+ prefix)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(32L, 32L)
                ]
                priority = 50
                tableId = NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                   new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def etherEgressFlowsPort1() {
        val theFlowId = "ETHERnullEgress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def etheregressFlowPort2() {
        val theFlowId = "ETHERnullEgress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def tcpIngressFlowPort1() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                   new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def tcpIngressFlowPort2() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def tcpEgressFlowPort2() {
        val theFlowId = "TCP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614944bi, 1152920405111996400bi)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    protected def udpEgressFlowsPort1() {
        val theFlowId = "UDP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                     new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def udpIngressFlowsPort2() {
        val theFlowId = "UDP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(32bi, 16777200bi)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    protected def udpEgressFlowsPort2() {
        val theFlowId = "UDP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpIngressFlowsPort1() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpIngressFlowsPort2() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpEgressFlowsPort2() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new MatchMetadata(1085217976614944bi, 1152920405111996400bi)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    protected def udpIngressPortRangeFlows() {
        val theFlowId = "UDP_DESTINATION_2000_65532Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(2000, 65532),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def tcpEgressRangeFlows() {
        val flowId1 = "TCP_DESTINATION_776_65534Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "TCP_DESTINATION_512_65280Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId3 = "TCP_DESTINATION_334_65534Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId4 = "TCP_DESTINATION_333_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId5 = "TCP_DESTINATION_336_65520Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId6 = "TCP_DESTINATION_352_65504Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId7 = "TCP_DESTINATION_384_65408Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId8 = "TCP_DESTINATION_768_65528Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(776, 65534),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(512, 65280),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId3
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(334, 65534),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId3)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId4
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(333, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId4)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId5
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(336, 65520),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId5)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId6
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(352, 65504),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId6)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId7
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(384, 65408),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId7)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId8
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(768, 65528),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId8)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def udpIngressAllFlows() {
        val theFlowId = "UDP_DESTINATION_1_0Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def tcpEgressAllFlows() {
        val theFlowId = "TCP_DESTINATION_1_0Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
         #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525),
                    new MatchEthernetType(34525),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
         ]

     }

    protected def icmpIngressFlowsPort3() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpEgressFlowsPort3() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    override def expectedFlows(String mac, String ip1, String prefix) {
        // Code auto. generated by https://github.com/vorburger/xtendbeans
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v4123_987_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68 as short),
                    new MatchUdpSourcePort(67 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546 as short),
                    new MatchUdpSourcePort(547 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(130 as short, 0 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                cookie = 110100480bi
                dpnId = 123bi
                flowId = "Ingress_v4_Broadcast_123_987_10.0.0.255_Permit"
                flowName = "ACL"
                hardTimeOut = 0
                idleTimeOut = 0
                instructionInfoList = #[
                new InstructionGotoTable(241 as short)
                ]
                matchInfoList = #[
                new MatchEthernetDestination(new MacAddress("ff:ff:ff:ff:ff:ff")),
                new MatchEthernetType(2048L),
                new MatchIpv4Destination(new Ipv4Prefix("10.0.0.255/32")),
                new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 61010
               sendFlowRemFlag = false
               strictFlag = false
               tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_ARP_123_987"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_" + ip1 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress(mac)),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip1 + "/" + prefix)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987_" + mac + "_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547 as short),
                    new MatchUdpSourcePort(546 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress(mac))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ICMPv6_123_987_133_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(133 as short, 0 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ICMPv6_123_987_135_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ICMPv6_123_987_136_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_" + ip1 + "/" + prefix
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress(mac)),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip1 + "/"+ prefix)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionApplyActions(#[
                    new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                ])
            ]
            matchInfoList = #[
                new MatchEthernetType(34525L),
                new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[34525]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_L2Broadcast_123_987_" + mac
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress(mac)),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 61005
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Acl_Commit_Conntrack_123_987_MatchEthernetType[2048]"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                        ),
                        new ActionNxCtClear(),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(0bi, 2bi)
                    ]
                priority = 100
                tableId = 247 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Acl_Commit_Conntrack_123_987_MatchEthernetType[34525]"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                        ),
                        new ActionNxCtClear(),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(0bi, 2bi)
                ]
                priority = 100
                tableId = 247 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Acl_Commit_Conntrack_123_987_MatchEthernetType[2048]"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                    new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                    ),
                    new ActionNxCtClear(),
                    new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, 1152920405095219202bi)
                ]
                priority = 100
                tableId = 217 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Acl_Commit_Conntrack_123_987_MatchEthernetType[34525]"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                        ),
                        new ActionNxCtClear(),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                        new MatchMetadata(1085217976614912bi, 1152920405095219202bi)
                ]
                priority = 100
                tableId = 217 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Acl_Commit_Non_Conntrack_123_987"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                new MatchMetadata(1085217976614914bi, 1152920405095219202bi)
                ]
                priority = 100
                tableId = 217 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_123_987_Drop"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                   new MatchMetadata(1085217976614916bi, new BigInteger("0FFFFF0000000004", 16))
                ]
                priority = 62020
                tableId = 217 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Acl_Commit_Non_Conntrack_123_987"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                    new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(2bi, 2bi)
                ]
                priority = 100
                tableId = 247 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_123_987_Drop"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(4bi, 4bi)
                ]
                priority = 62020
                tableId = 247 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_Fixed_Acl_Rule_Miss_Drop_123_987"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 50
                tableId = 244 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_Fixed_Acl_Rule_Miss_Drop_123_987"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 50
                tableId = 214 as short
            ]
        ]
    }

    protected def ingressCommitConntrack1() {
        val flowId1 = "Ingress_Acl_Commit_Conntrack_123_987_MatchEthernetType[2048]"
        val flowId2 = "Ingress_Acl_Commit_Conntrack_123_987_MatchEthernetType[34525]"
        #[
            new FlowEntityBuilder >> [
                    dpnId = 123bi
                    cookie = 110100480bi
                    flowId = flowId1
                    flowName = "ACL"
                    instructionInfoList = #[
                        new InstructionApplyActions(#[
                            new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                                Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE)
                                )
                            ),
                            new ActionNxCtClear(),
                            new ActionNxResubmit(220 as short)
                        ])
                    ]
                    matchInfoList = #[
                        new MatchEthernetType(2048L),
                        new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                        new MatchMetadata(0bi, 2bi)
                    ]
                    priority = 100
                    tableId = 247 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                        ),
                        new ActionNxCtClear(),
                        new ActionNxResubmit(220 as short)
                        ])
                    ]
                    matchInfoList = #[
                        new MatchEthernetType(34525L),
                        new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                        new MatchMetadata(0bi, 2bi)
                    ]
                    priority = 100
                    tableId = 247 as short
            ]
        ]
    }

    protected def egressCommitConntrack1() {
        val flowId1 = "Egress_Acl_Commit_Conntrack_123_987_MatchEthernetType[2048]"
        val flowId2 = "Egress_Acl_Commit_Conntrack_123_987_MatchEthernetType[34525]"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                        ),
                        new ActionNxCtClear(),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, 1152920405095219202bi)
                ]
                priority = 100
                tableId = 217 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short,
                            Collections.singletonList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE))
                        ),
                        new ActionNxCtClear(),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                        new MatchMetadata(1085217976614912bi, 1152920405095219202bi)
                ]
                priority = 100
                tableId = 217 as short
            ]
        ]
    }

    protected def egressCommitNonConntrack1() {
        val flowId1 = "Egress_Acl_Commit_Non_Conntrack_123_987"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                    new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614914bi, 1152920405095219202bi)
                ]
                priority = 100
                tableId = 217 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Egress_123_987_Drop"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614916bi, new BigInteger("0FFFFF0000000004", 16))
                ]
                priority = 62020
                tableId = 217 as short
            ]
        ]
    }

    protected def ingressCommitNonConntrack1() {
        val flowId1 = "Ingress_Acl_Commit_Non_Conntrack_123_987"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                 matchInfoList = #[
                     new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                         new MatchMetadata(2bi, 2bi)
                     ]
                 priority = 100
                 tableId = 247 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = "Ingress_123_987_Drop"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(4bi, 4bi)
                ]
                priority = 62020
                tableId = 247 as short
            ]
        ]
    }

    protected def ingressfixedAclMissDrop1() {
        val flowId1 = "Ingress_Fixed_Acl_Rule_Miss_Drop_123_987"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 50
                tableId = 244 as short
            ]
        ]
    }

    protected def egressfixedAclMissDrop1() {
        val flowId1 = "Egress_Fixed_Acl_Rule_Miss_Drop_123_987"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 50
                tableId = 214 as short
            ]
        ]
    }

    protected def ingressDispatcherFirst() {
        val flowId1 = "Ingress_ACL_Dispatcher_First_123_987_2"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(245 as short),
                    new InstructionWriteMetadata(32bi, 16777200bi)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def ingressDispatcherLast() {
        val flowId1 = "Ingress_ACL_Dispatcher_Last_123_987_2"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(32bi, 16777200bi)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def egressDispatcherFirst() {
        val flowId1 = "Egress_ACL_Dispatcher_First_123_987_2"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(215 as short),
                    new InstructionWriteMetadata(32bi, 16777200bi)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def egressDispatcherLast() {
        val flowId1 = "Egress_ACL_Dispatcher_Last_123_987_2"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 1085218086715393bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614944bi, 1152920405111996400bi)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def egressDispatcherFirst1() {
            val flowId1 = "Egress_ACL_Dispatcher_First_123_987_4"
            #[
                new FlowEntityBuilder >> [
                    dpnId = 123bi
                    cookie = 110100480bi
                    flowId = flowId1
                    flowName = "ACL"
                    instructionInfoList = #[
                        new InstructionGotoTable(215 as short),
                        new InstructionWriteMetadata(64bi, 16777200bi)
                    ]
                    matchInfoList = #[
                        new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                    ]
                    priority = IdHelper.getId(flowId1)
                    tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
                ]
            ]
        }

        protected def egressDispatcherLast1() {
            val flowId1 = "Egress_ACL_Dispatcher_Last_123_987_4"
            #[
                new FlowEntityBuilder >> [
                    dpnId = 123bi
                    cookie = 1085218086715393bi
                    flowId = flowId1
                    flowName = "ACL"
                    instructionInfoList = #[
                        new InstructionApplyActions(#[
                            new ActionDrop()
                        ])
                    ]
                    matchInfoList = #[
                        new MatchMetadata(1085217976614976bi, 1152920405111996400bi)
                    ]
                    priority = IdHelper.getId(flowId1)
                    tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
                ]
            ]
        }
}
