/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import com.google.common.collect.Sets;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.awaitility.core.ConditionFactory;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.testutils.interfacemanager.TunnelInterfaceDetails;
import org.opendaylight.mdsal.binding.testutils.AssertDataObjects;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.BucketId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class Verifications {

    private static final boolean CHECK_FOR_EXISTS = true;
    private static final boolean CHECK_FOR_DELETED = false;
    private static final Function<BigInteger, NodeId> GET_OPENFLOW_NODE_ID = (dpnId) -> new NodeId("openflow:" + dpnId);
    private static final InstanceIdentifier<ElanInstance> ELAN_IID = InstanceIdentifier
            .builder(ElanInstances.class)
            .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1))
            .build();
    private static final BiPredicate<Group, Group> BUCKETS_SIZE_MIS_MATCHED = (actual, expected) -> {
        return !(actual.getBuckets().getBucket().size() == expected.getBuckets().getBucket().size());
    };

    private final SingleTransactionDataBroker singleTxdataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final Map<String, TunnelInterfaceDetails> extnIntfs;
    private final ConditionFactory awaiter;

    public Verifications(final SingleTransactionDataBroker singleTxdataBroker,
                         final OdlInterfaceRpcService odlInterfaceRpcService,
                         final Map<String, TunnelInterfaceDetails> extnIntfs,
                         final ConditionFactory awaiter) {
        this.singleTxdataBroker = singleTxdataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.extnIntfs = extnIntfs;
        this.awaiter = awaiter;
    }

    private void awaitForData(LogicalDatastoreType dsType, InstanceIdentifier<? extends DataObject> iid) {
        awaiter.until(() -> singleTxdataBroker.syncReadOptional(dsType, iid).isPresent());
    }

    private void awaitForDataDelete(LogicalDatastoreType dsType, InstanceIdentifier<? extends DataObject> iid) {
        awaiter.until(() -> !singleTxdataBroker.syncReadOptional(dsType, iid).isPresent());
    }

    public void verifyThatMcastMacTepsCreated(InstanceIdentifier<Node> torNodeId, List<String> tepIps) {
        for (String tepIp : tepIps) {
            awaiter.until(() -> checkForRemoteMcastMac(torNodeId, tepIp, CHECK_FOR_EXISTS));
        }
    }

    public void verifyThatMcastMacTepsDeleted(InstanceIdentifier<Node> torNodeId, List<String> tepIps) {
        for (String tepIp : tepIps) {
            awaiter.until(() -> checkForRemoteMcastMac(torNodeId, tepIp, CHECK_FOR_DELETED));
        }
    }

    private boolean checkForRemoteMcastMac(InstanceIdentifier<Node> torNodeId, String tepIp, boolean checkForExists) {
        try {
            Node node = singleTxdataBroker.syncRead(LogicalDatastoreType.CONFIGURATION, torNodeId);
            HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation == null || augmentation.getRemoteMcastMacs() == null
                    || augmentation.getRemoteMcastMacs().isEmpty()) {
                if (checkForExists) {
                    return false;
                } else {
                    return true;
                }
            }
            boolean remoteMcastFoundFlag = false;
            for (RemoteMcastMacs remoteMcastMacs : augmentation.getRemoteMcastMacs()) {
                for (LocatorSet locatorSet : remoteMcastMacs.getLocatorSet()) {
                    TpId tpId = locatorSet.getLocatorRef().getValue().firstKeyOf(TerminationPoint.class).getTpId();
                    if (tpId.getValue().contains(tepIp)) {
                        remoteMcastFoundFlag = true;
                        break;
                    }
                }
            }
            if (checkForExists) {
                return (remoteMcastFoundFlag == true);
            } else {
                return (remoteMcastFoundFlag == false);
            }
        } catch (ReadFailedException e) {
            return false;
        }
    }

    public void verifyThatUcastCreated(InstanceIdentifier<Node> torNodeId, List<String> macs) {
        for (String mac : macs) {
            awaiter.until(() -> checkForRemoteUcastMac(torNodeId, mac, CHECK_FOR_EXISTS));
        }
    }

    public void verifyThatUcastDeleted(InstanceIdentifier<Node> torNodeId, List<String> macs) {
        for (String mac : macs) {
            awaiter.until(() -> checkForRemoteUcastMac(torNodeId, mac, CHECK_FOR_DELETED));
        }
    }

    public boolean checkForRemoteUcastMac(InstanceIdentifier<Node> torNodeId, String dpnMac, boolean checkForExists) {
        try {
            Node node = singleTxdataBroker.syncRead(LogicalDatastoreType.CONFIGURATION, torNodeId);
            HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation == null || augmentation.getRemoteUcastMacs() == null
                    || augmentation.getRemoteUcastMacs().isEmpty()) {
                if (checkForExists) {
                    return false;
                } else {
                    return true;
                }
            }
            boolean remoteUcastFoundFlag = false;
            for (RemoteUcastMacs remoteUcastMacs : augmentation.getRemoteUcastMacs()) {
                String mac = remoteUcastMacs.getMacEntryKey().getValue();
                if (mac.equals(dpnMac)) {
                    remoteUcastFoundFlag = true;
                    break;
                }
            }
            if (checkForExists) {
                return (remoteUcastFoundFlag == true);
            } else {
                return (remoteUcastFoundFlag == false);
            }
        } catch (ReadFailedException e) {
            return false;
        }
    }

    private List<Bucket> buildRemoteBcGroupBuckets(ElanInstance elanInfo,
                                                  List<BigInteger> otherDpns,
                                                  List<String> otherTors,
                                                  BigInteger dpnId,
                                                  int bucketId)
            throws ExecutionException, InterruptedException {
        List<Bucket> listBucketInfo = new ArrayList<>();
        if (otherDpns != null) {
            for (BigInteger otherDpn : otherDpns) {
                GetEgressActionsForInterfaceInput getEgressActInput = new GetEgressActionsForInterfaceInputBuilder()
                        .setIntfName(extnIntfs.get(dpnId + ":" + otherDpn).getInterfaceInfo().getInterfaceName())
                        .setTunnelKey(elanInfo.getElanTag()).build();
                List<Action> actionsList =
                        odlInterfaceRpcService.getEgressActionsForInterface(getEgressActInput).get().getResult()
                                .getAction();
                listBucketInfo.add(MDSALUtil.buildBucket(actionsList, MDSALUtil.GROUP_WEIGHT, bucketId,
                        MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));

                bucketId++;
            }
        }

        if (otherTors != null) {
            for (String otherTor : otherTors) {
                GetEgressActionsForInterfaceInput getEgressActInput = new GetEgressActionsForInterfaceInputBuilder()
                        .setIntfName(extnIntfs.get(dpnId + ":" + otherTor).getInterfaceInfo().getInterfaceName())
                        .setTunnelKey(elanInfo.getSegmentationId()).build();
                List<Action> actionsList =
                        odlInterfaceRpcService.getEgressActionsForInterface(getEgressActInput).get().getResult()
                                .getAction();
                listBucketInfo.add(MDSALUtil.buildBucket(actionsList, MDSALUtil.GROUP_WEIGHT, bucketId,
                        MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP));

                bucketId++;
            }
        }
        return listBucketInfo;
    }

    private Group buildStandardElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId, List<BigInteger> otherdpns,
                                                  List<String> tepIps)
            throws ExecutionException, InterruptedException {
        List<Bucket> listBucket = new ArrayList<>();
        int bucketId = 0;
        int actionKey = 0;
        Long elanTag = elanInfo.getElanTag();
        List<Action> listAction = new ArrayList<>();
        listAction.add(new ActionGroup(ElanUtils.getElanLocalBCGId(elanTag)).buildAction(++actionKey));
        listBucket.add(MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, bucketId, MDSALUtil.WATCH_PORT,
                MDSALUtil.WATCH_GROUP));
        bucketId++;
        listBucket.addAll(buildRemoteBcGroupBuckets(elanInfo, otherdpns, tepIps, dpnId, bucketId));
        long groupId = ElanUtils.getElanRemoteBCGId(elanTag);
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        return group;
    }

    private boolean validateGroup(ElanInstance actualElanInstances,
                                 BigInteger dpnId,
                                 List<BigInteger> otherdpns,
                                 List<String> torips)
            throws ExecutionException, InterruptedException, ReadFailedException, TransactionCommitFailedException {
        Group expected = buildStandardElanBroadcastGroups(actualElanInstances, dpnId, otherdpns, torips);
        InstanceIdentifier<Group> grpIid = DpnNodeBuilders.createGroupIid(expected, dpnId);
        Group actual = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);
        singleTxdataBroker.syncWrite(CONFIGURATION, grpIid, expected);
        expected = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);

        if (BUCKETS_SIZE_MIS_MATCHED.test(expected, actual)) {
            AssertDataObjects.assertEqualBeans(expected, actual);
        }

        Set<Bucket> actualBuckets = modifyBucketId(actual.getBuckets().getBucket());
        Set<Bucket> expectedBuckets = modifyBucketId(expected.getBuckets().getBucket());
        Set<Bucket> diff = Sets.difference(actualBuckets, expectedBuckets);
        if (diff != null && !diff.isEmpty()) {
            AssertDataObjects.assertEqualBeans(expected, actual);
        }
        return true;
    }

    private Set<Bucket> modifyBucketId(List<Bucket> input) {
        return input.stream()
                .map(bucket -> new BucketBuilder(bucket).setBucketId(new BucketId(1L))
                        .setKey(new BucketKey(new BucketId(1L))).build())
                .collect(Collectors.toSet());
    }

    public void verifyThatDpnGroupUpdated(BigInteger dpnId, List<BigInteger> otherdpns, List<String> othertors)
            throws ReadFailedException, TransactionCommitFailedException, ExecutionException, InterruptedException {
        verifyDPNGroup(dpnId, otherdpns, othertors, CHECK_FOR_EXISTS);
    }

    public void verifyThatDpnGroupDeleted(BigInteger dpnId)
            throws ReadFailedException, TransactionCommitFailedException, ExecutionException, InterruptedException {
        verifyDPNGroup(dpnId, Collections.emptyList(), Collections.emptyList(), CHECK_FOR_DELETED);
    }

    public void verifyLocalBcGroup(BigInteger dpnId, int expectedNoBuckets)
            throws ReadFailedException, TransactionCommitFailedException, ExecutionException, InterruptedException {
        awaiter.until(() -> {
            ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, ELAN_IID);
            InstanceIdentifier<Group> grpIid = buildLocalGroupIid(actualElanInstances, dpnId);
            awaitForData(CONFIGURATION, grpIid);
            Group localGroup = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);
            if (localGroup != null && localGroup.getBuckets() != null
                    && localGroup.getBuckets().getBucket() != null) {
                return localGroup.getBuckets().getBucket().size() == expectedNoBuckets;
            }
            return false;
        });
    }

    public void verifyThatLocalBcGroupDeleted(BigInteger dpnId)
            throws ReadFailedException, TransactionCommitFailedException, ExecutionException, InterruptedException {
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, ELAN_IID);
        InstanceIdentifier<Group> grpIid = buildLocalGroupIid(actualElanInstances, dpnId);
        awaitForDataDelete(CONFIGURATION, grpIid);
    }

    public void verifyDPNGroup(BigInteger dpnId,
                               List<BigInteger> otherdpns,
                               List<String> othertors,
                               boolean checkForExists)
            throws ReadFailedException, TransactionCommitFailedException, ExecutionException, InterruptedException {

        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, ELAN_IID);
        InstanceIdentifier<Group> grpIid = buildGroupIid(actualElanInstances, dpnId);

        if (checkForExists) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, grpIid);
            validateGroup(actualElanInstances, dpnId, otherdpns, othertors);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, grpIid);
        }
    }

    public void verifyThatDmacFlowOfTORCreated(List<BigInteger> dpns,
                                               InstanceIdentifier<Node> torNodeId,
                                               List<String> macs) throws ReadFailedException {
        for (String mac : macs) {
            for (BigInteger srcDpnId : dpns) {
                verifyDmacFlowOfTOR(srcDpnId, torNodeId, mac, CHECK_FOR_EXISTS);
            }
        }
    }

    public void verifyThatDmacFlowOfTORDeleted(List<BigInteger> dpns,
                                               InstanceIdentifier<Node> torNodeId,
                                               List<String> macs) throws ReadFailedException {
        for (String mac : macs) {
            for (BigInteger srcDpnId : dpns) {
                verifyDmacFlowOfTOR(srcDpnId, torNodeId, mac, CHECK_FOR_DELETED);
            }
        }
    }

    public void verifyDmacFlowOfTOR(BigInteger srcDpnId,
                                    InstanceIdentifier<Node> torNodeIid,
                                    String mac,
                                    boolean checkForExists) throws ReadFailedException {

        String torNodeId = torNodeIid.firstKeyOf(Node.class).getNodeId().getValue();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, ELAN_IID);
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE,
                        srcDpnId,
                        torNodeId,
                        mac,
                        actualElanInstances.getElanTag(),
                        false));

        InstanceIdentifier<Flow> flowIid = getFlowIid(NwConstants.ELAN_DMAC_TABLE, flowId, srcDpnId);

        if (checkForExists) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, flowIid);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, flowIid);
        }
    }

    public void  verifyThatDmacOfOtherDpnCreated(BigInteger srcDpnId, BigInteger dpnId, List<String> dpnMacs)
            throws ReadFailedException, InterruptedException {
        for (String dpnMac : dpnMacs) {
            verifyDmacFlowOfOtherDPN(srcDpnId, dpnId, dpnMac, CHECK_FOR_EXISTS);
        }
    }

    public void  verifyThatDmacOfOtherDPNDeleted(BigInteger srcDpnId, BigInteger dpnId, List<String> dpnMacs)
            throws ReadFailedException, InterruptedException {
        for (String dpnMac : dpnMacs) {
            verifyDmacFlowOfOtherDPN(srcDpnId, dpnId, dpnMac, CHECK_FOR_DELETED);
        }
    }

    private void  verifyDmacFlowOfOtherDPN(BigInteger srcDpnId, BigInteger dpnId, String dpnMac, boolean createFlag)
            throws ReadFailedException, InterruptedException {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE,
                        srcDpnId,
                        dpnId,
                        dpnMac,
                        actualElanInstances.getElanTag()));
        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE, flowId, srcDpnId);
        if (createFlag) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        }
    }

    private InstanceIdentifier<Flow> getFlowIid(short tableId, FlowId flowid, BigInteger dpnId) {

        FlowKey flowKey = new FlowKey(new FlowId(flowid));
        NodeId nodeId = GET_OPENFLOW_NODE_ID.apply(dpnId);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node nodeDpn =
                new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();
        return InstanceIdentifier.builder(Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                        nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(tableId)).child(Flow.class, flowKey).build();
    }

    private InstanceIdentifier<Group> buildGroupIid(ElanInstance actualElanInstances, BigInteger dpnId) {
        return DpnNodeBuilders.buildGroupInstanceIdentifier(
                ElanUtils.getElanRemoteBCGId(actualElanInstances.getElanTag()), DpnNodeBuilders.buildDpnNode(dpnId));
    }

    private InstanceIdentifier<Group> buildLocalGroupIid(ElanInstance actualElanInstances, BigInteger dpnId) {
        return DpnNodeBuilders.buildGroupInstanceIdentifier(
                ElanUtils.getElanLocalBCGId(actualElanInstances.getElanTag()), DpnNodeBuilders.buildDpnNode(dpnId));
    }
}
