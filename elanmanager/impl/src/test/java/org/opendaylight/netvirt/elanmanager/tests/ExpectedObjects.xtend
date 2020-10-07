/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests

import java.math.BigInteger
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo
import org.opendaylight.genius.mdsalutil.MetaDataUtil
import org.opendaylight.genius.mdsalutil.NwConstants
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects
import org.opendaylight.netvirt.elanmanager.api.ElanHelper
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.networkscontainer.Networks
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.networkscontainer.NetworksBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.field._case.SetFieldBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan.L2vlanMode
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.TunnelBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstancesBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmitBuilder

import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.yangtools.yang.common.Uint32

/**
 * Definitions of complex objects expected in tests.
 *
 * These were originally generated {@link AssertDataObjects#assertEqualBeans}.
 */
class ExpectedObjects {

    public static String ELAN1 = "34701c04-1118-4c65-9425-78a80d49a211"
    public static Uint32 ELAN1_SEGMENT_ID = Uint32.valueOf(100L)


    static def newInterfaceConfig(String interfaceName, String parentName) {
        new InterfaceBuilder >> [
            description = interfaceName
            name = interfaceName
            type = L2vlan
            addAugmentation(new ParentRefsBuilder >> [
                parentInterface = parentName
            ])addAugmentation(new IfL2vlanBuilder >> [
                l2vlanMode = L2vlanMode.Trunk
                vlanId = new VlanId(0)
            ])
        ]
    }

    def static createElanInstance() {
        new ElanInstancesBuilder >> [
            elanInstance = #[
                new ElanInstanceBuilder >> [
                    description = "TestElan description"
                    elanInstanceName = "TestElanName"
                    elanTag = Uint32.valueOf(5000L)
                    macTimeout = Uint32.valueOf(12345L)
                ]
            ]
        ]
    }

    def static createElanInstance(String elan, Uint32 segmentId) {
        new ElanInstanceBuilder >> [
                    elanInstanceName = elan
                    description = ELAN1
                    segmentType = SegmentTypeVxlan
                    segmentationId = segmentId
                    macTimeout = Uint32.valueOf(12345L)
        ]
    }

    def static createElanInstance(String elan, Uint32 segmentId, Uint32 tag) {
        new ElanInstanceBuilder >> [
                    elanInstanceName = elan
                    description = ELAN1
                    elanTag = tag
                    segmentationId = segmentId
                    segmentType = SegmentTypeVxlan
                    macTimeout = Uint32.valueOf(12345L)
        ]
    }

    def static Flow checkSmac(String flowId, InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
        new FlowBuilder >> [
            flowName = ELAN1
            barrier = false
            hardTimeout = 0
            id = new FlowId(flowId)
            idleTimeout = 0
            installHw = true
            instructions = new InstructionsBuilder >> [
                instruction = #[
                    new InstructionBuilder >> [
                        instruction = new GoToTableCaseBuilder >> [
                            goToTable = new GoToTableBuilder >> [
                                tableId = 51 as short
                            ]
                        ]
                        order = 0
                    ]
                ]
            ]
            match = new MatchBuilder >> [
                ethernetMatch = new EthernetMatchBuilder >> [
                    ethernetSource = new EthernetSourceBuilder >> [
                        address = new MacAddress(interfaceInfo.macAddress)
                    ]
                ]
                metadata = new MetadataBuilder >> [
                    metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag().toJava(), interfaceInfo.interfaceTag)
                    metadataMask = ElanHelper.getElanMetadataMask()
                ]
            ]
            priority = 20
            strict = true
            tableId = 50 as short
        ]
    }

    def static Flow checkDmacOfSameDpn(String flowId, InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
    val regvalue = MetaDataUtil.getReg6ValueForLPortDispatcher(interfaceInfo.getInterfaceTag(), NwConstants.DEFAULT_SERVICE_INDEX);
        new FlowBuilder >> [
            barrier = false
            flowName = ELAN1
            hardTimeout = 0
            id = new FlowId(flowId)
            idleTimeout = 0
            installHw = true;
            instructions = new InstructionsBuilder >> [
                instruction = #[
                    new InstructionBuilder >> [
                        instruction = new ApplyActionsCaseBuilder >> [
                            applyActions = new ApplyActionsBuilder >> [
                                action = #[
                                    new ActionBuilder >> [
                                        action = new NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder >> [
                                            nxResubmit = new NxResubmitBuilder >> [
                                                inPort = 65528
                                                table = 220 as short
                                            ]
                                        ]
                                        order = 1
                                    ],
                                    new ActionBuilder >> [
                                        action = new NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder >> [
                                            nxRegLoad = new NxRegLoadBuilder >> [
                                                dst = new DstBuilder >> [
                                                    dstChoice = new DstNxRegCaseBuilder >> [
                                                        nxReg = NxmNxReg6
                                                    ]
                                                    end = 31
                                                    start = 0
                                                ]
                                                value = new BigInteger(""+regvalue)
                                            ]
                                        ]
                                        order = 0
                                    ]
                                ]
                            ]
                        ]
                        order = 0
                    ]
                ]
            ]
            match = new MatchBuilder >> [
                ethernetMatch = new EthernetMatchBuilder >> [
                    ethernetDestination = new EthernetDestinationBuilder >> [
                        address = new MacAddress(interfaceInfo.getMacAddress())
                    ]
                ]
                metadata = new MetadataBuilder >> [
                    metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag().toJava())
                    metadataMask = MetaDataUtil.METADATA_MASK_SERVICE
                ]
            ]
            priority = 20
            strict = true
            tableId = 51 as short
        ]
    }

    def static Flow checkDmacOfOtherDPN(String flowId, InterfaceInfo interfaceInfo, TunnelInterfaceDetails tepDetails, ElanInstance elanInstance) {
        val regvalue = MetaDataUtil.getReg6ValueForLPortDispatcher(tepDetails.getInterfaceInfo().getInterfaceTag(), NwConstants.DEFAULT_SERVICE_INDEX);
        val tnlId = new BigInteger(""+interfaceInfo.getInterfaceTag())
        new FlowBuilder >> [
            barrier = false
            flowName = ELAN1
            hardTimeout = 0
            id = new FlowId(flowId)
            idleTimeout = 0
            installHw = true
            instructions = new InstructionsBuilder >> [
                instruction = #[
                    new InstructionBuilder >> [
                        instruction = new ApplyActionsCaseBuilder >> [
                            applyActions = new ApplyActionsBuilder >> [
                                action = #[
                                    new ActionBuilder >> [
                                        action = new SetFieldCaseBuilder >> [
                                            setField = new SetFieldBuilder >> [
                                                tunnel = new TunnelBuilder >> [
                                                    tunnelId = tnlId
                                                ]
                                            ]
                                        ]
                                        order = 0
                                    ],
                                    new ActionBuilder >> [
                                        action = new NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder >> [
                                            nxRegLoad = new NxRegLoadBuilder >> [
                                                dst = new DstBuilder >> [
                                                    dstChoice = new DstNxRegCaseBuilder >> [
                                                        nxReg = NxmNxReg6
                                                    ]
                                                    end = 31
                                                    start = 0
                                                ]
                                                value = new BigInteger(""+regvalue)
                                            ]
                                        ]
                                        order = 1
                                    ],
                                    new ActionBuilder >> [
                                        action = new NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder >> [
                                            nxResubmit = new NxResubmitBuilder >> [
                                                inPort = 65528
                                                table = 220 as short
                                            ]
                                        ]
                                        order = 2
                                    ]
                                ]
                            ]
                        ]
                        order = 0
                    ]
                ]
            ]
            match = new MatchBuilder >> [
                ethernetMatch = new EthernetMatchBuilder >> [
                    ethernetDestination = new EthernetDestinationBuilder >> [
                        address = new MacAddress(interfaceInfo.getMacAddress())
                    ]
                ]
                metadata = new MetadataBuilder >> [
                         metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag().toJava())
                         metadataMask = MetaDataUtil.METADATA_MASK_SERVICE
                ]
            ]
            priority = 20
            strict = true
            tableId = 51 as short
        ]
    }

    def static checkEvpnAdvertiseRoute(Uint32 vni, String mac, String tepip, String prefix, String rd1) {
       new NetworksBuilder >> [
           bgpControlPlaneType = BgpControlPlaneType.PROTOCOLEVPN
           encapType = EncapType.VXLAN
           ethtag = Uint32.ZERO
           l2vni = vni
           l3vni = Uint32.ZERO
           label = Uint32.ZERO
           macaddress = mac
           nexthop = new Ipv4Address(tepip)
           prefixLen = prefix
           rd = rd1
       ]
    }

    def static Networks checkEvpnWithdrawRT2DelIntf() {
        return null
    }
}

