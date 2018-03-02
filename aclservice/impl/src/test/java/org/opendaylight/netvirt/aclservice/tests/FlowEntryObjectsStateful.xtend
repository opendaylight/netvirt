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
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha
import org.opendaylight.genius.mdsalutil.matches.MatchArpSpa
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv4
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source
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
import java.util.Collections

import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata


class FlowEntryObjectsStateful extends FlowEntryObjectsBase {

    protected def etherFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + etherEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + etherIngressFlowsPort2
        + etherIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + etheregressFlowPort2
        + remoteEgressFlowsPort1
        + remoteEgressFlowsPort2
        + remoteEgressFlowsPort1
        + remoteEgressFlowsPort2
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

    protected def tcpFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + tcpIngressFlowPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + tcpIngressFlowPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + tcpEgressFlowPort2
        + tcpEgressFlowPort2
        + egressDispatcherFirst
        + egressDispatcherFirst
        + egressDispatcherLast
        + egressDispatcherLast
        + remoteIngressFlowsPort1
        + remoteIngressFlowsPort2
        + remoteIngressFlowsPort1
        + remoteIngressFlowsPort2
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

    protected def udpFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + udpEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + udpIngressFlowsPort2
        + udpIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + udpEgressFlowsPort2
        + ingressDispatcherFirst
        + ingressDispatcherFirst
        + ingressDispatcherLast
        + ingressDispatcherLast
        + remoteEgressFlowsPort1
        + remoteEgressFlowsPort2
        + remoteEgressFlowsPort1
        + remoteEgressFlowsPort2
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

    protected def icmpFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + icmpIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + icmpIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + icmpEgressFlowsPort2
        + icmpEgressFlowsPort2
        + egressDispatcherFirst
        + egressDispatcherFirst
        + egressDispatcherLast
        + egressDispatcherLast
        + remoteIngressFlowsPort1
        + remoteIngressFlowsPort2
        + remoteIngressFlowsPort1
        + remoteIngressFlowsPort2
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

    protected def dstRangeFlows() {
        fixedIngressFlowsPort1
        +fixedConntrackIngressFlowsPort1
        + udpIngressPortRangeFlows
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + tcpEgressRangeFlows
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1

    }

    protected def dstAllFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + udpIngressAllFlows
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + tcpEgressAllFlows
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
    }

    protected def icmpFlowsForTwoAclsHavingSameRules() {
        fixedIngressFlowsPort3
        + fixedConntrackIngressFlowsPort3
        + icmpIngressFlowsPort3
        + fixedEgressFlowsPort3
        + fixedConntrackEgressFlowsPort3
        + icmpEgressFlowsPort3
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + fixedIngressL3BroadcastFlows
        + fixedEgressL2BroadcastFlowsPort3
    }

    protected def aapWithIpv4AllFlows() {
        icmpFlows()
        + aapIpv4AllFlowsPort2
    }

    protected def aapFlows() {
        icmpFlows()
        + aapRemoteFlowsPort1
        + aapRemoteFlowsPort1
        + aapFlowsPort2
    }

    protected def multipleAcl() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + etherEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + etherIngressFlowsPort2
        + etherIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedIngressL3BroadcastFlows
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + etheregressFlowPort2
        + remoteEgressFlowsPort1
        + remoteEgressFlowsPort2
        + remoteEgressFlowsPort1
        + remoteEgressFlowsPort2
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
        + remoteEgressFlowsPort3
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L)
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
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ARP_123_987_0D:AA:D8:42:30:F40.0.0.0/0"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:F4_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                    new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(67 as short),
                    new MatchUdpSourcePort(68 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ]
        ]
    }

    protected def aapRemoteFlowsPort1() {
        #[
            remoteIngressFlowsPort("10.0.0.100"),
            remoteIngressFlowsPort("10.0.0.101")
        ]
    }

    protected def aapFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.100/32"
                flowName = "ACL"
                instructionInfoList = #[
                   new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.100", "32")
                ]
                priority = 61010
                tableId = 210 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.100/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.100", "32")
                ]
                priority = 61010
                tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:A4_10.0.0.101/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.101", "32")
                ]
                priority = 61010
                tableId = 210 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:A4_10.0.0.101/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.101", "32")
                ]
                priority = 61010
                tableId = 240 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ARP_123_987_0D:AA:D8:42:30:F410.0.0.100/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchArpSpa(new Ipv4Prefix("10.0.0.100/32")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ARP_123_987_0D:AA:D8:42:30:A410.0.0.101/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:A4")),
                    new MatchArpSpa(new Ipv4Prefix("10.0.0.101/32")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:A4_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(67 as short),
                    new MatchUdpSourcePort(68 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:A4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:F4_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(67 as short),
                    new MatchUdpSourcePort(68 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                tableId = NwConstants.INGRESS_ACL_TABLE
            ]
        ]
    }

    protected def fixedConntrackIngressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32"
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE)
            ]
            matchInfoList = #[
                new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F3")),
                new MatchEthernetType(2048L),
                new MatchIpv4Destination("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 243 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(32bi, 16777200bi)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                       new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_FILTER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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

    protected def fixedConntrackIngressFlowsPort2() {
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.2/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.2", "32")
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_LEARN_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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

    protected def fixedConntrackEgressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.2/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.2", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 213 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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

    protected def fixedConntrackIngressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.3", "32")
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_FILTER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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

    protected def fixedConntrackEgressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.3", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_FILTER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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

    static def fixedConntrackIngressFlowsPort4() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_10.0.0.4/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.4", "32")
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                tableId = NwConstants.INGRESS_ACL_FILTER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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

    static def fixedConntrackEgressFlowsPort4() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_10.0.0.4/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.4", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                tableId = NwConstants.EGRESS_ACL_FILTER_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
        val theFlowId = "ICMP_V4_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpIngressFlowsPort2() {
        val theFlowId = "ICMP_V4_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpEgressFlowsPort2() {
        val theFlowId = "ICMP_V4_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchUdpDestinationPort(2000, 65532),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.EGRESS_LEARN_ACL_FILTER_TABLE
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
         ]

     }

    protected def icmpIngressFlowsPort3() {
        val flowId1 = "ICMP_V4_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "ICMP_V4_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22"
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    protected def icmpEgressFlowsPort3() {
        val flowId1 = "ICMP_V4_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V4_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21"
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
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
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    override def expectedFlows(String mac) {
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
                tableId = NwConstants.EGRESS_ACL_TABLE
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
                tableId = NwConstants.EGRESS_ACL_TABLE
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
                tableId = NwConstants.EGRESS_ACL_TABLE
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
                tableId = NwConstants.EGRESS_ACL_TABLE
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
                tableId = NwConstants.EGRESS_ACL_TABLE
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
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
                ]
                matchInfoList = #[
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchEthernetDestination(new MacAddress(mac)),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                flowId = "Egress_DHCP_Client_v4123_987_" + mac + "_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(67 as short),
                    new MatchUdpSourcePort(68 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress(mac))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_987_Drop_"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68 as short),
                    new MatchUdpSourcePort(67 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v6_123_987_Drop_"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546 as short),
                    new MatchUdpSourcePort(547 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ICMPv6_123_987_134_Drop_"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(134 as short, 0 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63020
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress(mac)),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                flowId = "Ingress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionApplyActions(#[
                    new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE)
                ])
            ]
            matchInfoList = #[
                new MatchEthernetType(2048L),
                new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 100
                tableId = NwConstants.EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_987_MatchEthernetType[2048]_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_FILTER_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_ARP_123_987_" + mac + "10.0.0.1/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress(mac)),
                    new MatchEthernetSource(new MacAddress(mac)),
                    new MatchArpSpa(new Ipv4Prefix("10.0.0.1/32")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntityBuilder >> [
                cookie = 110100480bi
                dpnId = 123bi
                flowId = "Ingress_v4_Broadcast_123_987_10.0.0.255_Permit"
                flowName = "ACL"
                hardTimeOut = 0
                idleTimeOut = 0
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE)
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
                tableId = NwConstants.EGRESS_ACL_TABLE
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
                cookie = 110100481bi
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
                cookie = 110100481bi
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
            ]
        ]
    }

    protected def ingressfixedAclMissDrop1() {
        val flowId1 = "Ingress_Fixed_Acl_Rule_Miss_Drop_123_987"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                cookie = 110100481bi
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
                cookie = 110100481bi
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
                tableId = NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE
            ]
        ]
    }

    protected def egressDispatcherLast() {
        val flowId1 = "Egress_ACL_Dispatcher_Last_123_987_2"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100481bi
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
                    tableId = NwConstants.INGRESS_LEARN_ACL_FILTER_TABLE
                ]
            ]
        }

        protected def egressDispatcherLast1() {
            val flowId1 = "Egress_ACL_Dispatcher_Last_123_987_4"
            #[
                new FlowEntityBuilder >> [
                    dpnId = 123bi
                    cookie = 110100481bi
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
