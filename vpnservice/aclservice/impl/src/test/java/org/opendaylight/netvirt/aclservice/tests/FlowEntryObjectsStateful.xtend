/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.actions.ActionDrop
import org.opendaylight.genius.mdsalutil.FlowEntity
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
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
import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.NxMatchFieldType
import org.opendaylight.genius.mdsalutil.NxMatchInfoBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha
import org.opendaylight.genius.mdsalutil.NwConstants

class FlowEntryObjectsStateful extends FlowEntryObjectsBase {

    protected def etherFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + etherEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + etherIngressFlowsPort2
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + etheregressFlowPort2
    }

    protected def tcpFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + tcpIngressFlowPort1
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + tcpIngressFlowPort2
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + tcpEgressFlowPort2
    }

    protected def udpFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + udpEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + udpIngressFlowsPort2
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + udpEgressFlowsPort2
    }

    protected def icmpFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + icmpIngressFlowsPort1
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedConntrackIngressFlowsPort2
        + icmpIngressFlowsPort2
        + fixedEgressFlowsPort2
        + fixedConntrackEgressFlowsPort2
        + icmpEgressFlowsPort2
    }

    protected def dstRangeFlows() {
        fixedIngressFlowsPort1
        +fixedConntrackIngressFlowsPort1
        + udpIngressPortRangeFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + tcpEgressRangeFlows
    }

    protected def dstAllFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + udpIngressAllFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + tcpEgressAllFlows
    }

    protected def icmpFlowsForTwoAclsHavingSameRules() {
        fixedIngressFlowsPort3
        + fixedConntrackIngressFlowsPort3
        + icmpIngressFlowsPort3
        + fixedEgressFlowsPort3
        + fixedConntrackEgressFlowsPort3
        + icmpEgressFlowsPort3
    }

    protected def aapWithIpv4AllFlows() {
        icmpFlows()
        + aapIpv4AllFlowsPort2
    }

    protected def aapIpv4AllFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = NwConstants.INGRESS_ACL_TABLE
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = NwConstants.EGRESS_ACL_TABLE
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_987_0D:AA:D8:42:30:F4"
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
                tableId = NwConstants.INGRESS_ACL_TABLE
            ]
         ]
    }

    protected def fixedConntrackIngressFlowsPort1() {
        #[
           new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_10.0.0.1/32_Recirc"
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionApplyActions(#[
                    new ActionNxConntrack(2, 0, 0, 5000, 242 as short)
                ])
            ]
            matchInfoList = #[
                new MatchEthernetType(2048L),
                new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F3")),
            new MatchEthernetType(2048L),
            new MatchIpv4Destination("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = 241 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 213 as short
            ]
        ]
    }

    protected def etherIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32"
                        +"Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.1", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnull_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32"
                        +"Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.2", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_10.0.0.1/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 212 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F3")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.1", "32")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                           48L,
                           48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    protected def fixedConntrackIngressFlowsPort2() {
        #[
             new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_10.0.0.2/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 242 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.2", "32")
                ]
                priority = 61010
                tableId = 241 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 213 as short
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_10.0.0.2/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 212 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F4")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.2", "32")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    protected def fixedConntrackIngressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_10.0.0.3/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 242 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.3", "32")
                ]
                priority = 61010
                tableId = 241 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 213 as short
            ]
        ]
    }

    protected def fixedConntrackEgressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_10.0.0.3/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 212 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F5")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.3", "32")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    static def fixedConntrackIngressFlowsPort4() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_10.0.0.4/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 242 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.4", "32")
                ]
                priority = 61010
                tableId = 241 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 242 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetDestination(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = 241 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 213 as short
            ]
        ]
    }

    static def fixedConntrackEgressFlowsPort4() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_10.0.0.4/32_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 212 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.4", "32")
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F6_0.0.0.0/0_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 0, 0, 5000, 212 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetSource(new MacAddress("0D:AA:D8:42:30:F6")),
                    new MatchEthernetType(2048L)
                ]
                priority = 61010
                tableId = 211 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }

    protected def etherEgressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def etheregressFlowPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def tcpIngressFlowPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def tcpIngressFlowPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def tcpEgressFlowPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32"
                        +"Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.1", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.2", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def udpEgressFlowsPort1() {
        #[
             new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def udpIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32"
                        +"Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.1", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.2", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def udpEgressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def icmpIngressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def icmpIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def icmpEgressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F3_10.0.0.1/32"
                        +"Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.1", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_0D:AA:D8:42:30:F4_10.0.0.2/32"
                        +"Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Destination("10.0.0.2", "32"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def udpIngressPortRangeFlows() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_2000_65532Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            2000L,
                            65532L
                        ]
                    ],
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def tcpEgressRangeFlows() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_776_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            776L,
                            65534L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_512_65280Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            512L,
                            65280L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_334_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            334L,
                            65534L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_333_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            333L,
                            65535L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_336_65520Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            336L,
                            65520L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_352_65504Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            352L,
                            65504L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_384_65408Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            384L,
                            65408L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_768_65528Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            768L,
                            65528L
                        ]
                    ],
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    protected def udpIngressAllFlows() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_1_0Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def tcpEgressAllFlows() {
         #[
             new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_1_0Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
         ]

     }

    protected def icmpIngressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426a22"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 243 as short
            ]
        ]
    }

    protected def icmpEgressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426a21"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = IdHelper.getFlowPriority(flowId)
                tableId = 213 as short
            ]
        ]
    }

    override def expectedFlows(String mac) {
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
                    new MatchUdpDestinationPort(68 as short),
                    new MatchUdpSourcePort(67 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 241 as short
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
                    new MatchUdpDestinationPort(546 as short),
                    new MatchUdpSourcePort(547 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 241 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 213 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Ingress_Fixed_Conntrk_Drop123_987_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 213 as short
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
                    new MatchUdpDestinationPort(67 as short),
                    new MatchUdpSourcePort(68 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 211 as short
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
                    new MatchUdpDestinationPort(547 as short),
                    new MatchUdpSourcePort(546 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 211 as short
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
                    new MatchUdpDestinationPort(68 as short),
                    new MatchUdpSourcePort(67 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 211 as short
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
                    new MatchUdpDestinationPort(546 as short),
                    new MatchUdpSourcePort(547 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 63010
                tableId = 211 as short
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
                tableId = 211 as short
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
                tableId = 211 as short
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
                tableId = 211 as short
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
                tableId = 211 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100481bi
                flowId = "Egress_Fixed_Conntrk_Drop123_987_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionDrop()
                    ])
                ]
                matchInfoList = #[
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = 50
                tableId = 243 as short
            ],
            new FlowEntity(123bi) => [
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
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]
                ]
                priority = 62015
                tableId = 243 as short
            ]
        ]
    }
}
