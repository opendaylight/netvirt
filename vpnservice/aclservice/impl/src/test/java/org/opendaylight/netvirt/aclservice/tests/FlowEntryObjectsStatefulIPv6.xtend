/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.ActionInfoBuilder
import org.opendaylight.genius.mdsalutil.ActionType
import org.opendaylight.genius.mdsalutil.FlowEntity
import org.opendaylight.genius.mdsalutil.InstructionInfo
import org.opendaylight.genius.mdsalutil.InstructionType
import org.opendaylight.genius.mdsalutil.MatchFieldType
import org.opendaylight.genius.mdsalutil.MatchInfoBuilder
import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.NxMatchFieldType
import org.opendaylight.genius.mdsalutil.NxMatchInfoBuilder

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan

class FlowEntryObjectsStatefulIPv6 extends FlowEntryObjectsBase {

    static def fixedFlowsPort1() {
        #[ fixedIngressFlowsPort1, fixedEgressFlowsPort1 ]
    }

    static def etherFlows() {
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

    static def tcpFlows() {
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

    static def udpFlows() {
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

    static def icmpFlows() {
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

    static def dstRangeFlows() {
        fixedIngressFlowsPort1
        +fixedConntrackIngressFlowsPort1
        + udpIngressPortRangeFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + tcpEgressRangeFlows
    }

    static def dstAllFlows() {
        fixedIngressFlowsPort1
        + fixedConntrackIngressFlowsPort1
        + udpIngressAllFlows
        + fixedEgressFlowsPort1
        + fixedConntrackEgressFlowsPort1
        + tcpEgressAllFlows
    }

    static def icmpFlowsForTwoAclsHavingSameRules() {
        fixedIngressFlowsPort3
        + fixedConntrackIngressFlowsPort3
        + icmpIngressFlowsPort3
        + fixedEgressFlowsPort3
        + fixedConntrackEgressFlowsPort3
        + icmpEgressFlowsPort3
    }
    static def fixedConntrackIngressFlowsPort1() {
        #[
           new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_2001:db8::1/64_Recirc"
            flowName = "ACL"
            instructionInfoList = #[
                new InstructionInfo(InstructionType.apply_actions, #[
                    new ActionInfoBuilder >> [
                        actionKey = 2
                        actionType = ActionType.nx_conntrack
                        actionValues = #[
                            "0",
                            "0",
                            "5000",
                            "252"
                        ]
                    ]
                ])
            ]
            matchInfoList = #[
                new MatchInfoBuilder >> [
                    matchField = MatchFieldType.eth_type
                    matchValues = #[
                        34525L
                    ]
                ],
                new MatchInfoBuilder >> [
                    matchField = MatchFieldType.eth_dst
                    stringMatchValues = #[
                        "0D:AA:D8:42:30:F3"
                ]
            ],
            new MatchInfoBuilder >> [
                matchField = MatchFieldType.eth_type
                matchValues = #[
                    34525L
                ]
            ],
            new MatchInfoBuilder >> [
                matchField = MatchFieldType.ipv6_destination
                stringMatchValues = #[
                    "2001:db8::/64"
                        ]
                    ]
                ]
                priority = 61010
                tableId = 251 as short
            ]
        ]
    }

    static def etherIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnull_ipv6_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F3], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::1/64]]]]"
                        +"Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnull_ipv6_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F4], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::2/64]]]]"
                        +"Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def fixedConntrackEgressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F3_2001:db8::1/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "0",
                                "0",
                                "5000",
                                "41"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ]
                ]
                priority = 61010
                tableId = 40 as short
            ]
        ]
    }

    static def fixedConntrackIngressFlowsPort2() {
        #[
             new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_2001:db8::2/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "0",
                                "0",
                                "5000",
                                "252"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_dst
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F4"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_destination
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ]
                ]
                priority = 61010
                tableId = 251 as short
            ]
        ]
    }

    static def fixedConntrackEgressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F4_2001:db8::2/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "0",
                                "0",
                                "5000",
                                "41"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F4"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ]
                ]
                priority = 61010
                tableId = 40 as short
            ]
        ]
    }

    static def fixedConntrackIngressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_2001:db8::3/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "0",
                                "0",
                                "5000",
                                "252"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_dst
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F5"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_destination
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ]
                ]
                priority = 61010
                tableId = 251 as short
            ]
        ]

    }

    static def fixedConntrackEgressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_123_0D:AA:D8:42:30:F5_2001:db8::3/64_Recirc"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "0",
                                "0",
                                "5000",
                                "41"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F5"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ]
                ]
                priority = 61010
                tableId = 40 as short
            ]
        ]
    }

    static def fixedEgressFlowsPort2 () {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            67L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            68L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            547L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            546L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
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
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            68L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            67L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
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
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            546L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            547L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
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
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            58L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            134L,
                            0L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63020
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F4"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.arp_sha
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F4"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 40 as short
            ]
        ]

    }

    static def etherEgressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def etheregressFlowPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def tcpIngressFlowPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def tcpIngressFlowPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def tcpEgressFlowPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F3], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::1/64]]]]"
                        +"Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_destination
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_AllowedAddressPairsKey [_macAddress=MacAddress [_value=0D:AA:D8:42:30:F4], _ipAddress=IpPrefixOrAddress [_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::2/64]]]]Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_destination
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def udpEgressFlowsPort1() {
        #[
             new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def udpIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F3], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::1/64]]]]"
                        +"Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535_ipv6_remoteACL_interface_aap_AllowedAddressPairsKey [_macAddress=MacAddress [_value=0D:AA:D8:42:30:F4], _ipAddress=IpPrefixOrAddress [_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::2/64]]]]Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def udpEgressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            80L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def icmpIngressFlowsPort1() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def icmpIngressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def icmpEgressFlowsPort2() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23__ipv6_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F3], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::1/64]]]]"
                        +"Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_destination
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23__ipv6_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F4], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv6Prefix=Ipv6Prefix [_value=2001:db8::2/64]]]]"
                        +"Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_destination
                        stringMatchValues = #[
                            "2001:db8::/64"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def udpIngressPortRangeFlows() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_2000_65532Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_udp_dst_with_mask
                        matchValues = #[
                            2000L,
                            65532L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def tcpEgressRangeFlows() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_776_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            776L,
                            65534L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_512_65280Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            512L,
                            65280L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_334_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            334L,
                            65534L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_333_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            333L,
                            65535L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_336_65520Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            336L,
                            65520L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_352_65504Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            352L,
                            65504L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_384_65408Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            384L,
                            65408L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_768_65528Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.nx_tcp_dst_with_mask
                        matchValues = #[
                            768L,
                            65528L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
        ]
    }

    static def udpIngressAllFlows() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_1_0Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ]
        ]
    }

    static def tcpEgressAllFlows() {
         #[
             new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "TCP_DESTINATION_1_0Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            6L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ]
         ]

     }

    static def icmpIngressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 252 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426a22"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_2
                tableId = 252 as short
            ]
        ]
    }

    static def icmpEgressFlowsPort3() {
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_1
                tableId = 41 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "ICMP_V6_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426a21"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "5000",
                                "255"
                            ]
                        ],
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ipv6_source
                        stringMatchValues = #[
                            "0::0/0"
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            2L,
                            3L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            1L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ],
                    new NxMatchInfoBuilder >> [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]
                ]
                priority = AclServiceTestBase.FLOW_PRIORITY_SG_2
                tableId = 41 as short
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
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            68L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            67L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_DHCP_Server_v6_123_987___Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            546L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            547L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_ICMPv6_123_987_130_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            58L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            130L,
                            0L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_ICMPv6_123_987_135_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            58L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            135L,
                            0L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_ICMPv6_123_987_136_Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            58L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            136L,
                            0L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Ingress_ARP_123_987"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "220"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2054L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 251 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_987__Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            67L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            68L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_987__Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        new ActionInfoBuilder >> [
                            actionType = ActionType.nx_resubmit
                            actionValues = #[
                                "17"
                            ]
                        ]
                    ])
                ]
                matchInfoList = #[
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            547L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            546L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
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
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            68L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            67L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
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
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            546L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            547L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
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
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            34525L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            58L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        matchField = MatchFieldType.icmp_v6
                        matchValues = #[
                            134L,
                            0L
                        ]
                    ],
                    new MatchInfoBuilder >> [
                        bigMatchValues = #[
                            1085217976614912bi,
                            MetaDataUtil.METADATA_MASK_LPORT_TAG
                        ]
                        matchField = MatchFieldType.metadata
                    ]
                ]
                priority = 63020
                tableId = 40 as short
            ]
        ]
    }
}
