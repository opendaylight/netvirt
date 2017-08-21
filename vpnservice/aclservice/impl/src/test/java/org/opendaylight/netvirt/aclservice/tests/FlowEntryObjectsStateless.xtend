/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests

import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions
import org.opendaylight.genius.mdsalutil.matches.MatchArpSha
import org.opendaylight.genius.mdsalutil.matches.MatchArpSpa
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv4
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata
import org.opendaylight.genius.mdsalutil.matches.MatchTcpFlags
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchTcpDestinationPort
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchUdpDestinationPort
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan

class FlowEntryObjectsStateless extends FlowEntryObjectsBase {

    protected def etherFlows() {
        fixedIngressFlowsPort1
        + etherFlowIngressPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedEgressFlowsPort1
        + etherFlowEgressPort1
        + fixedIngressFlowsPort2
        + etherIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedEgressFlowsPort2
        + etheregressFlowPort2
        + remoteFlows
    }

    protected def tcpFlows() {
        fixedIngressFlowsPort1
        + tcpIngressFlowPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedEgressFlowsPort1
        + tcpEgressFlowPort1
        + fixedIngressFlowsPort2
        + tcpIngressFlowPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedEgressFlowsPort2
        + tcpEgressFlowPort2
        + remoteFlows
    }

    protected def udpFlows() {
        fixedIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedEgressFlowsPort2
        + remoteFlows
    }

    protected def icmpFlows() {
        fixedIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedEgressFlowsPort1
        + fixedIngressFlowsPort2
        + fixedEgressL2BroadcastFlowsPort2
        + fixedEgressFlowsPort2
        + remoteFlows
    }

    protected def dstRangeFlows() {
        fixedIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedEgressFlowsPort1
        + tcpEgressRangeFlows
    }

    protected def dstAllFlows() {
        fixedIngressFlowsPort1
        + fixedEgressL2BroadcastFlowsPort1
        + fixedEgressFlowsPort1
    }

    protected def icmpFlowsForTwoAclsHavingSameRules() {
        fixedIngressFlowsPort3
        + fixedEgressL2BroadcastFlowsPort3
        + fixedEgressFlowsPort3
    }

    protected def etherFlowIngressPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
            cookie = 110100480bi
            flowId = "SYN_ETHERnullIngress98785cc3048-abc3-43cc-89b3-377341426ac7"
            flowName = "ACL_SYN_"
            instructionInfoList = #[
                new InstructionApplyActions(#[
                    new ActionNxResubmit(220 as short)
                ])
            ]
            matchInfoList = #[
                new MatchEthernetType(2048L),
                new MatchEthernetType(2048L),
                new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
            ]
            priority = 61005
            tableId = 241 as short
        ]
     ]
 }
    protected def etherFlowEgressPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 61005
                tableId = 211 as short
            ]
        ]
    }
    protected def etherIngressFlowsPort2() {
        #[
               new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_ETHERnullIngress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 61005
                tableId = 241 as short
            ]
        ]
    }

    protected def etherEgressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "[SYN_ETHERnullIngress98785cc3048-abc3-43cc-89b3-377341426ac7"
        flowName = "ACL_SYN_"
        instructionInfoList = #[
            new InstructionApplyActions(#[
                new ActionNxResubmit(220 as short)
            ])
        ]
        matchInfoList = #[
            new MatchEthernetType(2048L),
            new MatchEthernetType(2048L),
            new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
        ]
        priority = 61005
        tableId = 241 as short
            ]
        ]
    }

    protected def etheregressFlowPort2() {
        #[
           new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_ETHERnullEgress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG)
                ]
                priority = 61005
                tableId = 211 as short
            ]
        ]
    }

    protected def tcpIngressFlowPort1() {
        #[
                new FlowEntityBuilder >> [
                dpnId = 123bi
                    cookie = 110100480bi
                    flowId = "SYN_TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                    flowName = "ACL_SYN_"
                    instructionInfoList = #[
                        new InstructionApplyActions(#[
                            new ActionNxResubmit(220 as short)
                        ])
                    ]
                    matchInfoList = #[
                        new MatchEthernetType(2048L),
                        new MatchEthernetType(2048L),
                        new NxMatchTcpDestinationPort(80, 65535),
                        new MatchIpProtocol(6 as short),
                        new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                        new MatchTcpFlags(2)
                    ]
                    priority = 61005
                    tableId = 241 as short
                ]
        ]
    }

    protected def tcpIngressFlowPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_80_65535Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 241 as short
            ]
        ]
    }

    protected def tcpEgressFlowPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ]
        ]
    }

    protected def tcpEgressFlowPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_80_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(80, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ]
        ]
    }

    protected def udpEgressFlowsPort1() {
        #[
             new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 213 as short
            ]
        ]
    }

    protected def udpIngressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F3], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv4Prefix=Ipv4Prefix [_value=10.0.0.1/24]]]]"
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
                    new MatchIpv4Source("10.0.0.1", "24"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "UDP_DESTINATION_80_65535_ipv4_remoteACL_interface_aap_AllowedAddressPairsKey [_macAddress=MacAddress [_value=0D:AA:D8:42:30:F4], _ipAddress=IpPrefixOrAddress [_ipPrefix=IpPrefix [_ipv4Prefix=Ipv4Prefix [_value=10.0.0.2/24]]]]Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
                flowName = "ACL"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxConntrack(2, 1, 0, 5000, 255 as short),
                        new ActionNxResubmit(220 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchIpv4Source("10.0.0.2", "24"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 243 as short
            ]
        ]
    }

    protected def udpEgressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchUdpDestinationPort(80, 65535),
                    new MatchIpProtocol(17 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 213 as short
            ]
        ]
    }

    protected def icmpIngressFlowsPort1() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 243 as short
            ]
        ]
    }

    protected def icmpIngressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 243 as short
            ]
        ]
    }

    protected def icmpEgressFlowsPort2() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F3], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv4Prefix=Ipv4Prefix [_value=10.0.0.1/24]]]]"
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
                    new MatchIpv4Destination("10.0.0.1", "24"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "ICMP_V4_DESTINATION_23__ipv4_remoteACL_interface_aap_AllowedAddressPairsKey "
                        +"[_macAddress=MacAddress [_value=0D:AA:D8:42:30:F4], _ipAddress=IpPrefixOrAddress "
                        +"[_ipPrefix=IpPrefix [_ipv4Prefix=Ipv4Prefix [_value=10.0.0.2/24]]]]"
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
                    new MatchIpv4Destination("10.0.0.2", "24"),
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new MatchIcmpv4(2 as short, 3 as short),
                    new MatchIpProtocol(1 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 213 as short
            ]
        ]
    }

    protected def udpIngressPortRangeFlows() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchUdpDestinationPort(2000, 65532),
                    new MatchIpProtocol(17 as short),
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = 61010
                tableId = 243 as short
            ]
        ]
    }

    protected def tcpEgressRangeFlows() {
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_776_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(776, 65534),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_512_65280Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(512, 65280),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_334_65534Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(334, 65534),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_333_65535Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(333, 65535),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_336_65520Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(336, 65520),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_352_65504Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(352, 65504),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_384_65408Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(384, 65408),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = "SYN_TCP_DESTINATION_768_65528Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
                flowName = "ACL_SYN_"
                instructionInfoList = #[
                    new InstructionApplyActions(#[
                        new ActionNxResubmit(17 as short)
                    ])
                ]
                matchInfoList = #[
                    new MatchEthernetType(2048L),
                    new MatchEthernetType(2048L),
                    new NxMatchTcpDestinationPort(768, 65528),
                    new MatchIpProtocol(6 as short),
                    new MatchMetadata(1085217976614912bi, MetaDataUtil.METADATA_MASK_LPORT_TAG),
                    new MatchTcpFlags(2)
                ]
                priority = 61005
                tableId = 211 as short
            ]
        ]
    }

   protected def icmpIngressFlowsPort3() {
       val flowId1 = "ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426ac7"
       val flowId2 = "ICMP_V4_DESTINATION_23_Ingress98785cc3048-abc3-43cc-89b3-377341426a22"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 243 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L),
                    new NxMatchCtState(33L, 33L)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 243 as short
            ]
        ]
    }

    protected def icmpEgressFlowsPort3() {
        val flowId1 = "ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426ac6"
        val flowId2 = "ICMP_V4_DESTINATION_23_Egress98785cc3048-abc3-43cc-89b3-377341426a21"
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId1
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
                    new NxMatchCtState(33L, 33L)
                ]
                priority = IdHelper.getId(flowId1)
                tableId = 213 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
                cookie = 110100480bi
                flowId = flowId2
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
                    new NxMatchCtState(33L, 33L)
                ]
                priority = IdHelper.getId(flowId2)
                tableId = 213 as short
            ]
        ]
    }

    override def expectedFlows(String mac) {
        // Code auto. generated by https://github.com/vorburger/xtendbeans
        #[
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = 241 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                    new NxMatchRegister(NxmNxReg6, 252672L, 268435200L)
                ]
                priority = 63010
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 241 as short
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
                tableId = 211 as short
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
                tableId = 211 as short
            ],
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
            new FlowEntityBuilder >> [
                dpnId = 123bi
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
                tableId = 211 as short
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
                tableId = 211 as short
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
                tableId = 211 as short
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
                tableId = 211 as short
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
                    new MatchMetadata(1085217976614912bi, 1152920405095219200bi)
                ]
                priority = 63010
                tableId = 211 as short
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
                tableId = 211 as short
            ]
        ]
    }
}
