/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests

import static org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Ignore

/**
 * Tests XtendBeanGenerator through FlowEntryObjects.
 * Note that NOT every *Objects needs a test like this;
 * this one was just written because it was the very first one.
 * @author Michael Vorburger
 */
class FlowEntryObjectsTest {

    @Test def void compareFlowEntryWithEquals() {
        assertEquals(FlowEntryObjects.expectedFlows("MAC"), FlowEntryObjects.expectedFlows("MAC"));
    }

    @Test def void compareFlowEntryWithToString() {
        assertEquals(FlowEntryObjects.expectedFlows("MAC").toString(), FlowEntryObjects.expectedFlows("MAC").toString());
    }

    @Ignore // TODO when XtendBeanGenerator has been moved somewhere else
    @Test def void expressionString() {
        assertEquals(
        '''
        #[
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v4123_0D:AA:D8:42:30:F3__Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        (new ActionInfoBuilder => [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "0",
                                "255"
                            ]
                        ]).build()
                    ]),
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            68L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            67L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Client_v6_123_0D:AA:D8:42:30:F3__Permit_"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        (new ActionInfoBuilder => [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "1",
                                "0",
                                "0",
                                "255"
                            ]
                        ]).build()
                    ]),
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            568L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            567L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4123_0D:AA:D8:42:30:F3__Drop_"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            67L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            68L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_DHCP_Server_v4_123_0D:AA:D8:42:30:F3__Drop_"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.ip_proto
                        matchValues = #[
                            17L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_dst
                        matchValues = #[
                            567L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.udp_src
                        matchValues = #[
                            568L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_Untrk_123_0D:AA:D8:42:30:F3_Untracked"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.apply_actions, #[
                        (new ActionInfoBuilder => [
                            actionKey = 2
                            actionType = ActionType.nx_conntrack
                            actionValues = #[
                                "0",
                                "0",
                                "0",
                                "40"
                            ]
                        ]).build()
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            0L,
                            32L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_Untrk_123_0D:AA:D8:42:30:F3_Tracked_Established"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            34L,
                            55L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build()
                ]
                priority = 62020
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_Untrk_123_0D:AA:D8:42:30:F3_Tracked_Related"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            36L,
                            55L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build()
                ]
                priority = 62020
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_NewDrop_123_0D:AA:D8:42:30:F3_Tracked_New"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            33L,
                            33L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build()
                ]
                priority = 36007
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_Fixed_Conntrk_NewDrop_123_0D:AA:D8:42:30:F3_Tracked_Invalid"
                flowName = "ACL"
                instructionInfoList = #[
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2048L
                        ]
                    ]).build(),
                    (new NxMatchInfoBuilder => [
                        matchField = NxMatchFieldType.ct_state
                        matchValues = #[
                            48L,
                            48L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_src
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build()
                ]
                priority = 36007
                tableId = 40 as short
            ],
            new FlowEntity(123bi) => [
                cookie = 110100480bi
                flowId = "Egress_ARP_123_0D:AA:D8:42:30:F3"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionInfo(InstructionType.goto_table, #[
                        41L
                    ])
                ]
                matchInfoList = #[
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.eth_type
                        matchValues = #[
                            2054L
                        ]
                    ]).build(),
                    (new MatchInfoBuilder => [
                        matchField = MatchFieldType.arp_sha
                        stringMatchValues = #[
                            "0D:AA:D8:42:30:F3"
                        ]
                    ]).build()
                ]
                priority = 61010
                tableId = 40 as short
            ]
        ]'''.toString,
        null // TODO when XtendBeanGenerator has been moved somewhere else: new XtendBeanGenerator().getExpression(FlowEntryObjects::expectedFlows)
        )
    }
}
