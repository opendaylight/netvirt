/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.FlowEntityBuilder
import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.NwConstants
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination
import org.opendaylight.yangtools.yang.common.Uint64

class FlowEntryObjectsBaseIpv6 {

    protected def fixedFlowsPort1(String ip1, String prefix) {
        #[ fixedIngressFlowsPort1, fixedEgressFlowsPort1(ip1, prefix) ]
    }

    protected def fixedIngressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v4123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v4123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                        ])
                    ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68),
                    new MatchUdpSourcePort(67),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v6_123_987_Permit_"
            flowName = "Ingress_DHCP_Server_v6_123_987_Permit_"
            instructionInfoList = #[
                new InstructionApplyActions(#[
                    new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546),
                    new MatchUdpSourcePort(547),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
            flowName = "Ingress_ICMPv6_123_987_130_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
            flowName = "Ingress_ICMPv6_123_987_135_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
            flowName = "Ingress_ICMPv6_123_987_136_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ARP_123_987"
            flowName = "Ingress_ARP_123_987"
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
            ]

            ]
    }

    protected def fixedEgressL2BroadcastFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F3"
                flowName = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F3"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 61005
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
     }

    protected def fixedIngressL3BroadcastFlows() {
        #[
            new FlowEntityBuilder >> [
                cookie = Uint64.valueOf(110100480bi)
                dpnId = Uint64.valueOf(123bi)
                flowId = "Ingress_v4_Broadcast_123_987_10.0.0.255_Permit"
                flowName = "Ingress_v4_Broadcast_123_987_10.0.0.255_Permit"
                hardTimeOut = 0
                idleTimeOut = 0
                instructionInfoList = #[
                    new InstructionGotoTable(NwConstants.EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE)
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
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }
     protected def fixedEgressFlowsPort1(String ip1, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F3_Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F3_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547),
                    new MatchUdpSourcePort(546),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_133_Permit_"
                flowName = "Egress_ICMPv6_123_987_133_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(133 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_135_Permit_"
                flowName = "Egress_ICMPv6_123_987_135_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_136_Permit_"
                flowName = "Egress_ICMPv6_123_987_136_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]

    }

     protected def fixedIngressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v4123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v4123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68),
                    new MatchUdpSourcePort(67),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v6_123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v6_123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546),
                    new MatchUdpSourcePort(547),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
                flowName = "Ingress_ICMPv6_123_987_130_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
                flowName = "Ingress_ICMPv6_123_987_135_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
                flowName = "Ingress_ICMPv6_123_987_136_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ARP_123_987"
                flowName = "Ingress_ARP_123_987"
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
            ]
        ]
    }

     protected def fixedEgressL2BroadcastFlowsPort2 () {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F4"
                flowName = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F4"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 61005
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
         ]
     }

     protected def fixedEgressFlowsPort2 (String ip2, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F4_Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F4_Permit_"
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
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_133_Permit_"
                flowName = "Egress_ICMPv6_123_987_133_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(133 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_135_Permit_"
                flowName = "Egress_ICMPv6_123_987_135_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_136_Permit_"
                flowName = "Egress_ICMPv6_123_987_136_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }

     protected def fixedIngressFlowsPort3() {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v4123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v4123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68),
                    new MatchUdpSourcePort(67),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v6_123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v6_123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546),
                    new MatchUdpSourcePort(547),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
                flowName = "Ingress_ICMPv6_123_987_130_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
                flowName = "Ingress_ICMPv6_123_987_135_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
                flowName = "Ingress_ICMPv6_123_987_136_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ARP_123_987"
                flowName = "Ingress_ARP_123_987"
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
            ]
        ]
    }

    protected def fixedEgressL2BroadcastFlowsPort3 () {
       #[
           new FlowEntityBuilder >> [
               dpnId = Uint64.valueOf(123bi)
               cookie = Uint64.valueOf(110100480bi)
               flowId = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F5"
               flowName = "Egress_L2Broadcast_123_987_0D:AA:D8:42:30:F5"
               instructionInfoList = #[
                   new InstructionApplyActions(#[
                       new ActionNxResubmit(17 as short)
                   ])
               ]
               matchInfoList = #[
                   new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                   new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
               ]
               priority = 61005
               tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
           ]
        ]
    }

     protected def fixedEgressFlowsPort3 (String ip3, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F5_Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987_0D:AA:D8:42:30:F5_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547),
                    new MatchUdpSourcePort(546),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5"))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_133_Permit_"
                flowName = "Egress_ICMPv6_123_987_133_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(133 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_135_Permit_"
                flowName = "Egress_ICMPv6_123_987_135_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_136_Permit_"
                flowName = "Egress_ICMPv6_123_987_136_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }

    static def fixedIngressFlowsPort4() {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v4123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v4123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68),
                    new MatchUdpSourcePort(67),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v6_123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v6_123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546),
                    new MatchUdpSourcePort(547),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
                flowName = "Ingress_ICMPv6_123_987_130_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
                flowName = "Ingress_ICMPv6_123_987_135_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
                flowName = "Ingress_ICMPv6_123_987_136_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ARP_123_987"
                flowName = "Ingress_ARP_123_987"
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
            ]
        ]
    }

    static def fixedEgressFlowsPort4 () {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987__Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547),
                    new MatchUdpSourcePort(546),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987__Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547),
                    new MatchUdpSourcePort(546),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_133_Permit_"
                flowName = "Egress_ICMPv6_123_987_133_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(133 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_135_Permit_"
                flowName = "Egress_ICMPv6_123_987_135_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_136_Permit_"
                flowName = "Egress_ICMPv6_123_987_136_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }

    protected def remoteFlows(String ip1, String ip2, String prefix) {
        remoteIngressFlowsPort1(ip1, prefix)
        + remoteEgressFlowsPort1(ip1, prefix)
        + remoteIngressFlowsPort2(ip2, prefix)
        + remoteEgressFlowsPort2(ip2, prefix)
    }

    protected def remoteIngressFlowsPort1(String ip1, String prefix) {
        #[
            remoteIngressFlowsPort(ip1, prefix)
         ]
    }

    protected def remoteIngressFlowsPort2(String ip2, String prefix) {
        #[
            remoteIngressFlowsPort(ip2, prefix)
         ]
    }


    protected def remoteEgressFlowsPort1(String ip1, String prefix) {
        #[
            remoteEgressFlowsPort(ip1, prefix)
         ]
    }

    protected def remoteEgressFlowsPort2(String ip2, String prefix) {
        #[
            remoteEgressFlowsPort(ip2, prefix)
         ]
    }

    protected def remoteIngressFlowsPort(String ip, String prefix) {
        new FlowEntityBuilder >> [
            dpnId = Uint64.valueOf(123bi)
            cookie = Uint64.valueOf(110100480bi)
            flowId = "Acl_Filter_Egress_" + ip + "/" + prefix + "_2"
            flowName = "Acl_Filter_Egress_" + ip + "/" + prefix + "_2"
            instructionInfoList = #[
               new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
            ]
            matchInfoList = #[
                new MatchMetadata(Uint64.valueOf(32bi),Uint64.valueOf(16777200bi)),
                new MatchEthernetType(34525L),
                new MatchIpv6Destination(ip + "/"+ prefix)
            ]
            priority = 100
            tableId = NwConstants.INGRESS_REMOTE_ACL_TABLE
        ]
    }

    protected def remoteIngressFlowsPortIpv4(String ip, String prefix) {
        new FlowEntityBuilder >> [
            dpnId = Uint64.valueOf(123bi)
            cookie = Uint64.valueOf(110100480bi)
            flowId = "Acl_Filter_Egress_" + ip + "/" + prefix + "_2"
            flowName = "Acl_Filter_Egress_" + ip + "/" + prefix + "_2"
            instructionInfoList = #[
               new InstructionGotoTable(NwConstants.INGRESS_ACL_COMMITTER_TABLE)
            ]
            matchInfoList = #[
                new MatchMetadata(Uint64.valueOf(32bi),Uint64.valueOf(16777200bi)),
                new MatchEthernetType(2048L),
                new MatchIpv4Destination(ip, prefix)
            ]
            priority = 100
            tableId = NwConstants.INGRESS_REMOTE_ACL_TABLE
        ]
    }

    protected def remoteEgressFlowsPort(String ip, String prefix) {
        new FlowEntityBuilder >> [
            dpnId = Uint64.valueOf(123bi)
            cookie = Uint64.valueOf(110100480bi)
            flowId = "Acl_Filter_Ingress_" + ip + "/" + prefix + "_2"
            flowName = "Acl_Filter_Ingress_" + ip + "/" + prefix + "_2"
            instructionInfoList = #[
                new InstructionGotoTable(NwConstants.EGRESS_ACL_COMMITTER_TABLE)
            ]
            matchInfoList = #[
                new MatchMetadata(Uint64.valueOf(32bi),Uint64.valueOf(16777200bi)),
                new MatchEthernetType(34525L),
                new MatchIpv6Source(ip + "/"+ prefix)
            ]
            priority = 100
            tableId = NwConstants.EGRESS_REMOTE_ACL_TABLE
        ]
    }

    protected def remoteIngressFlowsPort3(String ip2, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Acl_Filter_Ingress_" + ip2 + "/" + prefix + "_4"
                flowName = "Acl_Filter_Ingress_" + ip2 + "/" + prefix + "_4"
                instructionInfoList = #[
                    new InstructionGotoTable(247 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(Uint64.valueOf(64bi), Uint64.valueOf(16777200bi)),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Source(ip2 + "/"+ prefix)
                ]
                priority = 100
                tableId = 246 as short
            ]
        ]
    }

    protected def remoteEgressFlowsPort3(String ip2, String prefix) {
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Acl_Filter_Egress_" + ip2 + "/" + prefix + "_4"
                flowName = "Acl_Filter_Egress_" + ip2 + "/" + prefix + "_4"
                instructionInfoList = #[
                    new InstructionGotoTable(217 as short)
                ]
                matchInfoList = #[
                    new MatchMetadata(Uint64.valueOf(64bi), Uint64.valueOf(16777200bi)),
                    new MatchEthernetType(34525L),
                    new MatchIpv6Destination(ip2 + "/"+ prefix)
                ]
                priority = 100
                tableId = 216 as short
            ]
        ]
    }

    protected def expectedFlows(String mac, String ip1, String prefix) {
        // Code auto. generated by https://github.com/vorburger/xtendbeans
        #[
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v4123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v4123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(68),
                    new MatchUdpSourcePort(67),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_DHCP_Server_v6_123_987_Permit_"
                flowName = "Ingress_DHCP_Server_v6_123_987_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(546),
                    new MatchUdpSourcePort(547),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = NwConstants.EGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
                flowName = "Ingress_ICMPv6_123_987_130_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
                flowName = "Ingress_ICMPv6_123_987_135_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
                flowName = "Ingress_ICMPv6_123_987_136_Permit_"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Ingress_ARP_123_987"
                flowName = "Ingress_ARP_123_987"
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
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987_" + mac + "_Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987_" + mac + "_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547),
                    new MatchUdpSourcePort(546),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress(mac))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_DHCP_Client_v6_123_987_" + mac + "_Permit_"
                flowName = "Egress_DHCP_Client_v6_123_987_" + mac + "_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(17 as short),
                    new MatchUdpDestinationPort(547),
                    new MatchUdpSourcePort(546),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchEthernetSource(new MacAddress(mac))
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_133_Permit_"
                flowName = "Egress_ICMPv6_123_987_133_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(133 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_135_Permit_"
                flowName = "Egress_ICMPv6_123_987_135_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(135 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ],
            new FlowEntityBuilder >> [
                dpnId = Uint64.valueOf(123bi)
                cookie = Uint64.valueOf(110100480bi)
                flowId = "Egress_ICMPv6_123_987_136_Permit_"
                flowName = "Egress_ICMPv6_123_987_136_Permit_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(34525L),
                    new MatchIpProtocol(58 as short),
                    new MatchIcmpv6(136 as short, 0 as short),
                    new MatchMetadata(Uint64.valueOf(1085217976614912bi), MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = NwConstants.INGRESS_ACL_ANTI_SPOOFING_TABLE
            ]
        ]
    }
}
