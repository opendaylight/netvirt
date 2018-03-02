/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.actions.ActionDrop
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha
import org.opendaylight.genius.mdsalutil.matches.MatchArpSpa
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata
import org.opendaylight.genius.mdsalutil.NwConstants
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable

class FlowEntryObjectsStatefulIPv6 extends FlowEntryObjectsStateful {

    override icmpFlowsForTwoAclsHavingSameRules() {
        fixedIngressFlowsPort3
        + fixedConntrackIngressFlowsPort3
        + icmpIngressFlowsPort4
        + fixedEgressFlowsPort3
        + fixedConntrackEgressFlowsPort5
        + icmpEgressFlowsPort4
        + ingressCommitNonConntrack1
        + egressCommitNonConntrack1
        + ingressCommitConntrack1
        + egressCommitConntrack1
        + ingressfixedAclMissDrop1
        + egressfixedAclMissDrop1
        + fixedIngressL3BroadcastFlows
        + fixedEgressL2BroadcastFlowsPort3

    }

    override fixedConntrackIngressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F3_10.0.0.1/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(241 as short)
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
               tableId = NwConstants.EGRESS_ACL_CONNTRACK_SENDER_TABLE
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
                   new NxMatchCtState(48, 48)
               ]
               priority = 62020
               tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
           ]
        ]
    }

    override etherIngressFlowsPort2() {
        val flowId1 = "ETHERnullIngress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(246 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(32bi, 16777200bi)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    override fixedConntrackEgressFlowsPort1() {
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
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
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
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi)

                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ]
        ]
    }

    override fixedConntrackIngressFlowsPort2() {
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.2/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(241 as short)
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
                     new NxMatchCtState(48, 48)
                 ]
                 priority = 62020
                 tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
             ]

        ]
    }

    override fixedConntrackEgressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F4_10.0.0.2/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.2", "32")
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
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
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(48L, 48L)
                ]
                priority =62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
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
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi)
                ]
                priority = 100
                tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
            ]
        ]
    }

    override fixedConntrackIngressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(241 as short)
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
                    new NxMatchCtState(48, 48)
                ]
                priority = 62020
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    override fixedConntrackEgressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(211 as short)
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
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                    new NxMatchCtState(48L, 48L)
                ]
                priority = 62020
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    override etherEgressFlowsPort1() {
        val theFlowId = "ETHERnullEgress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
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

    override etheregressFlowPort2() {
        val theFlowId = "ETHERnullEgress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
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

    override tcpIngressFlowPort1() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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

    override tcpIngressFlowPort2() {
        val theFlowId = "TCP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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

    override tcpEgressFlowPort2() {
        val flowId1 = "TCP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(216 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614944bi, 1152920405111996400bi)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    override udpEgressFlowsPort1() {
        val theFlowId = "UDP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
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

    override udpIngressFlowsPort2() {
        val flowId1 = "UDP_DESTINATION_80_65535Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(246 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchMetadata(32bi, 16777200bi)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.EGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    override udpEgressFlowsPort2() {
        val theFlowId = "UDP_DESTINATION_80_65535Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)

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

    override icmpIngressFlowsPort1() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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

    override icmpIngressFlowsPort2() {
        val theFlowId = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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

    override icmpEgressFlowsPort2() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(216 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new MatchMetadata(1085217976614944bi, 1152920405111996400bi)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = NwConstants.INGRESS_ACL_RULE_BASED_FILTER_TABLE
            ]
        ]
    }

    override udpIngressPortRangeFlows() {
        val theFlowId = "UDP_DESTINATION_2000_65532Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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

    override tcpEgressRangeFlows() {
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
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

    override udpIngressAllFlows() {
        val theFlowId = "UDP_DESTINATION_1_0Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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

    override tcpEgressAllFlows() {
        val theFlowId = "TCP_DESTINATION_1_0Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
         #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = theFlowId
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(theFlowId)
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
         ]

     }

    override icmpIngressFlowsPort3() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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
                    new InstructionGotoTable(247 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.EGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ]
        ]
    }

    protected def icmpIngressFlowsPort4() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426ac7"
        val flowId2 = "ICMP_V6_DESTINATION_23_Ingress_123_987_85cc3048-abc3-43cc-89b3-377341426a22"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
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
                    new InstructionGotoTable(247 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 1004
                tableId = NwConstants.EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    override icmpEgressFlowsPort3() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = NwConstants.INGRESS_ACL_STATEFUL_APPLY_CHANGE_EXIST_TRAFFIC_TABLE
            ]
        ]
    }

    protected def icmpEgressFlowsPort4() {
        val flowId1 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V6_DESTINATION_23_Egress_123_987_85cc3048-abc3-43cc-89b3-377341426a21"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
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
                    new InstructionGotoTable(217 as short)
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchEthernetType(34525L),
                    new MatchIcmpv6(2 as short, 3 as short),
                    new MatchIpProtocol(58 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 1004
                tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
            ]
        ]
    }

    override fixedEgressFlowsPort3 () {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987_0D:AA:D8:42:30:F5_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(67),
                    new MatchUdpSourcePort(68),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5"))
                ]
                priority = 63010
                tableId = 210 as short
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
                        new MatchUdpDestinationPort(68),
                        new MatchUdpSourcePort(67),
                        new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                    ]
                    priority = 63010
                    tableId = 210 as short
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
                        new MatchUdpDestinationPort(546),
                        new MatchUdpSourcePort(547),
                        new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                    ]
                    priority = 63010
                    tableId = 210 as short
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
                    tableId = 210 as short
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
                    tableId = 210 as short
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
                    tableId = 210 as short
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
                    tableId = 210 as short
                ],
                new FlowEntityBuilder >> [
                    dpnId = 123bi
                    cookie = 110100480bi
                    flowId = "Egress_ARP_123_987_0D:AA:D8:42:30:F510.0.0.3/32"
                    flowName = "ACL"
                    instructionInfoList = #[
                        new InstructionApplyActions(#[
                            new ActionNxResubmit(17 as short)
                        ])
                    ]
                    matchInfoList = #[
                        new MatchEthernetType(2054L),
                        new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F5")),
                        new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                        new MatchArpSpa(new Ipv4Prefix("10.0.0.3/32")),
                        new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                    ]
                    priority = 63010
                    tableId = 210 as short
                ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort5() {
            #[
                new FlowEntityBuilder >> [
                    dpnId = 123bi
                    cookie = 110100480bi
                    flowId = "Egress_Fixed_Goto_Classifier_123_987_0D:AA:D8:42:30:F5_10.0.0.3/32"
                    flowName = "ACL"
                    instructionInfoList = #[
                        new InstructionGotoTable(211 as short)
                    ]
                    matchInfoList = #[
                        new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                        new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                        new MatchEthernetType(2048L),
                        new MatchIpv4Source("10.0.0.3", "32")
                    ]
                    priority = 61010
                    tableId = 210 as short
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
                        new MatchMetadata(1085217976614912bi, 1152920405095219200bi),
                        new NxMatchCtState(48L, 48L)
                    ]
                    priority = 62020
                    tableId = NwConstants.INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE
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
                        new MatchMetadata(1085217976614912bi, 1152920405095219200bi)
                    ]
                    priority = 100
                    tableId = NwConstants.INGRESS_ACL_CONNTRACK_SENDER_TABLE
                ]
            ]
        }

}
