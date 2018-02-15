/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests

import org.opendaylight.mdsal.binding.testutils.AssertDataObjects
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstancesBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;

import java.math.BigInteger
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import static extension org.opendaylight.mdsal.binding.testutils.XtendBuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.BucketsBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.group.buckets.bucket.action.action.NxActionRegLoadNodesNodeGroupBucketsBucketActionsCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeBuilder
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowInfoKey;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.GroupInfoKey;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.BucketId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.Buckets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmitBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.group.buckets.bucket.action.action.NxActionRegLoadNodesNodeGroupBucketsBucketActionsCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.group.buckets.bucket.action.action.NxActionResubmitNodesNodeGroupBucketsBucketActionsCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCaseBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.field._case.SetFieldBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.GroupActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.group.action._case.GroupActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.TunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoadBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.Dst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nx.action.reg.load.grouping.nx.reg.load.DstBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableBuilder
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.^extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionResubmitNodesNodeTableFlowApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan.L2vlanMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan.L2vlanMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;


/**
 * Definitions of complex objects expected in tests.
 *
 * These were originally generated {@link AssertDataObjects#assertEqualBeans}.
 */
class ExpectedObjects {

    public static String ELAN1 = "34701c04-1118-4c65-9425-78a80d49a211"
    public static Long ELAN1_SEGMENT_ID = 100L


    static def newInterfaceConfig(String interfaceName, String parentName) {
        new InterfaceBuilder >> [
            description = interfaceName
            name = interfaceName
            type = L2vlan
            addAugmentation(ParentRefs, new ParentRefsBuilder >> [
                parentInterface = parentName
            ])addAugmentation(IfL2vlan, new IfL2vlanBuilder >> [
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
                    elanTag = 5000L
                    macTimeout = 12345L
                ]
            ]
        ]
    }

    def static createElanInstance(String elan, Long segmentId) {
        new ElanInstanceBuilder >> [
                    elanInstanceName = elan
                    description = ELAN1
                    segmentType = SegmentTypeVxlan
                    segmentationId = segmentId
                    macTimeout = 12345L
        ]
    }

    def static createElanInstance(String elan, Long segmentId, Long tag) {
        new ElanInstanceBuilder >> [
                    elanInstanceName = elan
                    description = ELAN1
                    elanTag = tag
                    segmentationId = segmentId
                    segmentType = SegmentTypeVxlan
                    macTimeout = 12345L
        ]
    }

    def static Flow checkSmac(String flowId, InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
        new FlowBuilder >> [
            flowName = ELAN1
            hardTimeout = 0
            id = new FlowId(flowId)
            idleTimeout = 0
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
                    metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag(), interfaceInfo.interfaceTag)
                    metadataMask = ElanHelper.getElanMetadataMask()
                ]
            ]
            priority = 20
            tableId = 50 as short
        ]
    }

    def static Flow checkDmacOfSameDpn(String flowId, InterfaceInfo interfaceInfo, ElanInstance elanInstance) {
    val regvalue = MetaDataUtil.getReg6ValueForLPortDispatcher(interfaceInfo.getInterfaceTag(), NwConstants.DEFAULT_SERVICE_INDEX);
        new FlowBuilder >> [
            flowName = ELAN1
            hardTimeout = 0
            id = new FlowId(flowId)
            idleTimeout = 0
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
                    metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag())
                    metadataMask = MetaDataUtil.METADATA_MASK_SERVICE
                ]
            ]
            priority = 20
            tableId = 51 as short
        ]
    }

    def static Flow checkDmacOfOtherDPN(String flowId, InterfaceInfo interfaceInfo, TunnelInterfaceDetails tepDetails, ElanInstance elanInstance) {
        val regvalue = MetaDataUtil.getReg6ValueForLPortDispatcher(tepDetails.getInterfaceInfo().getInterfaceTag(), NwConstants.DEFAULT_SERVICE_INDEX);
        val tnlId = new BigInteger(""+interfaceInfo.getInterfaceTag())
        new FlowBuilder >> [
            flowName = ELAN1
            hardTimeout = 0
            id = new FlowId(flowId)
            idleTimeout = 0
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
                         metadata = ElanHelper.getElanMetadataLabel(elanInstance.getElanTag())
                         metadataMask = MetaDataUtil.METADATA_MASK_SERVICE
                ]
            ]
            priority = 20
            tableId = 51 as short
        ]
    }

    def static checkEvpnAdvertiseRoute(Long vni, String mac, String tepip, String prefix, String rd1) {
       new NetworksBuilder >> [
           bgpControlPlaneType = BgpControlPlaneType.PROTOCOLEVPN
           encapType = EncapType.VXLAN
           ethtag = 0L
           l2vni = vni
           l3vni = 0L
           label = 0L
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

