/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.FlowEntity
import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress

class FlowEntryObjectsBase {

    static def fixedFlowsPort1() {
        #[ fixedIngressFlowsPort1, fixedEgressFlowsPort1 ]
    }

    static def fixedIngressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v4123_987__Permit_"
            flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987___Permit_"
            flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ]

            ]
    }

    static def fixedEgressFlowsPort1() {
        #[

            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v6_123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F3"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ]
        ]
    }


    static def fixedIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v4123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987___Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ]
        ]
    }

    static def fixedEgrssFlowsPort2 () {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v6_123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F4"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ]
        ]

    }

    static def fixedIngressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v4123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987___Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ]
        ]
    }

    static def fixedEgressFlowsPort3 () {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v6_123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F5"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ]
        ]
    }


    static def fixedIngressFlowsPort4() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v4123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987___Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ]
        ]
    }

    static def fixedEgressFlowsPort4 () {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v6_123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2054L),
                    new MatchArpSha(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ]
        ]
    }

    static def expectedFlows(String mac) {
        // Code auto. generated by https://github.com/vorburger/xtendbeans
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v4123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987___Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
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
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v6_123_987__Drop_"
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
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
                tableId = 40 as short
            ]
        ]
    }
}
