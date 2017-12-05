/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
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

    private SingleTransactionDataBroker singleTxdataBroker;
    private OdlInterfaceRpcService odlInterfaceRpcService;
    private Map<String, TunnelInterfaceDetails> extnIntfs = new HashMap<>();

    protected ConditionFactory getAwaiter() {
        return Awaitility.await("TestableListener")
                .atMost(30, TimeUnit.SECONDS)//TODO constant
                .pollInterval(100, TimeUnit.MILLISECONDS);
    }

    public Verifications(SingleTransactionDataBroker singleTxdataBroker,
                         OdlInterfaceRpcService odlInterfaceRpcService,
                         Map<String, TunnelInterfaceDetails> extnIntfs) {
        this.singleTxdataBroker = singleTxdataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.extnIntfs = extnIntfs;
    }

    public void checkForRemoteMcastMacInTorTowardsTeps(InstanceIdentifier<Node> torNodeId,
                                                       boolean existFlag,
                                                       List<String> tepIps) {
        for (String tepIp : tepIps) {
            getAwaiter().until(() -> checkForRemoteMcastMac(torNodeId, tepIp, existFlag));
        }
    }

    public boolean checkForRemoteMcastMac(InstanceIdentifier<Node> torNodeId, String tepIp, boolean existFlag) {
        try {
            Node node = singleTxdataBroker.syncRead(LogicalDatastoreType.CONFIGURATION, torNodeId);
            HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation == null || augmentation.getRemoteMcastMacs() == null
                    || augmentation.getRemoteMcastMacs().isEmpty()) {
                if (existFlag) {
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
            if (existFlag) {
                return (remoteMcastFoundFlag == true);
            } else {
                return (remoteMcastFoundFlag == false);
            }
        } catch (ReadFailedException e) {
            return false;
        }
    }

    public void checkForRemoteUcastMacs(InstanceIdentifier<Node> torNodeId, List<String> macs, boolean existFlag)
            throws ReadFailedException {
        for (String mac : macs) {
            getAwaiter().until(() -> checkForRemoteUcastMac(torNodeId, mac, existFlag));
        }
    }

    public boolean checkForRemoteUcastMac(InstanceIdentifier<Node> torNodeId, String dpnMac, boolean existFlag)
            throws ReadFailedException {

        try {
            Node node = singleTxdataBroker.syncRead(LogicalDatastoreType.CONFIGURATION, torNodeId);
            HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation == null || augmentation.getRemoteUcastMacs() == null
                    || augmentation.getRemoteUcastMacs().isEmpty()) {
                if (existFlag) {
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
            if (existFlag) {
                return (remoteUcastFoundFlag == true);
            } else {
                return (remoteUcastFoundFlag == false);
            }
        } catch (ReadFailedException e) {
            return false;
        }
    }

    public List<Bucket> getRemoteBCGroupTunnelBuckets(ElanInstance elanInfo,
                                                      List<BigInteger> otherDpns, List<String> otherTors,
                                                      BigInteger dpnId, int bucketId,
                                                      long elanTagOrVni)
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

    public Group setupStandardElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId, List<BigInteger> otherdpns,
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
        List<Bucket> listBucketInfoRemote = getRemoteBCGroupBuckets(elanInfo, dpnId, otherdpns, tepIps, bucketId);
        listBucket.addAll(listBucketInfoRemote);
        long groupId = ElanUtils.getElanRemoteBCGId(elanTag);
        Group group = MDSALUtil.buildGroup(groupId, elanInfo.getElanInstanceName(), GroupTypes.GroupAll,
                MDSALUtil.buildBucketLists(listBucket));
        return group;
    }

    public List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo,
                                                BigInteger dpnId,
                                                List<BigInteger> dpns,
                                                List<String> tepIps,
                                                int bucketId) throws ExecutionException, InterruptedException {
        List<Bucket> listBucketInfo = new ArrayList<>();
        listBucketInfo.addAll(getRemoteBCGroupTunnelBuckets(elanInfo, dpns, tepIps, dpnId, bucketId,
                elanInfo.getSegmentationId()));
        return listBucketInfo;
    }

    public void validateGroup(ElanInstance actualElanInstances, BigInteger dpnId, List<BigInteger> otherdpns,
                              List<String> torips)
            throws org.opendaylight.controller.md.sal.common.api.data.ReadFailedException,
            TransactionCommitFailedException {
        getAwaiter().conditionEvaluationListener((conditionEvaluationListener) -> {
        }).until(() -> validateGroup(actualElanInstances, dpnId, otherdpns, torips, false));
    }

    public boolean validateGroup(ElanInstance actualElanInstances, BigInteger dpnId, List<BigInteger> otherdpns,
                                 List<String> torips, boolean printError)
            throws org.opendaylight.controller.md.sal.common.api.data.ReadFailedException,
            TransactionCommitFailedException, ExecutionException, InterruptedException {
        Group expected = setupStandardElanBroadcastGroups(actualElanInstances, dpnId, otherdpns, torips);
        InstanceIdentifier<Group> grpIid = DpnNodeBuilders.createGroupIid(expected, dpnId);
        final Group actual = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);
        singleTxdataBroker.syncWrite(CONFIGURATION, grpIid, expected);
        expected = singleTxdataBroker.syncRead(CONFIGURATION, grpIid);

        if (!(actual.getBuckets().getBucket().size() == expected.getBuckets().getBucket().size())
                || !Objects.equals(expected, actual)) {
            if (!printError) {
                return false;
            }
            AssertDataObjects.assertEqualBeans(expected, actual);
        }
        if (!(actual.getBuckets().getBucket().containsAll(expected.getBuckets().getBucket()))
                || !Objects.equals(expected, actual)) {
            if (!printError) {
                return false;
            }
            AssertDataObjects.assertEqualBeans(expected, actual);
        }
        return true;
    }

    public void validateDPNGroup(BigInteger dpnId, List<BigInteger> otherdpns, List<String> othertors,
                                 boolean existFlag)
            throws InterruptedException, ReadFailedException, TransactionCommitFailedException {

        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        InstanceIdentifier<Group> grpIid =
                DpnNodeBuilders.buildGroupInstanceIdentifier(ElanUtils.getElanRemoteBCGId(
                        actualElanInstances.getElanTag()),
                        DpnNodeBuilders.buildDpnNode(dpnId));
        if (existFlag) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, grpIid);
            validateGroup(actualElanInstances, dpnId, otherdpns, othertors);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, grpIid);
        }
    }

    public void  verifyDmacFlowOfOtherDPN(BigInteger srcDpnId, BigInteger dpnId, String dpnMac,
                                          boolean createFlag)
            throws ReadFailedException, InterruptedException {
        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef((short)51,
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

    public void verifyDmacFlowOfTORInDpns(List<BigInteger> dpns, String torNodeId, String mac, boolean createFlag)
            throws ReadFailedException,InterruptedException {
        for (BigInteger srcDpnId : dpns) {
            verifyDmacFlowOfTOR(srcDpnId, torNodeId, mac, createFlag);
        }
    }

    public void verifyDmacFlowOfTOR(BigInteger srcDpnId, String torNodeId, String mac, boolean createFlag)
            throws ReadFailedException,InterruptedException {

        InstanceIdentifier<ElanInstance> elanInstanceIid = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(ExpectedObjects.ELAN1)).build();
        ElanInstance actualElanInstances = singleTxdataBroker.syncRead(CONFIGURATION, elanInstanceIid);
        FlowId flowId = new FlowId(
                ElanUtils.getKnownDynamicmacFlowRef((short)51,
                        srcDpnId,
                        torNodeId,
                        mac,
                        actualElanInstances.getElanTag(),
                        false));

        InstanceIdentifier<Flow> flowInstanceIidDst = getFlowIid(NwConstants.ELAN_DMAC_TABLE, flowId, srcDpnId);

        if (createFlag) {
            awaitForData(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        } else {
            awaitForDataDelete(LogicalDatastoreType.CONFIGURATION, flowInstanceIidDst);
        }
    }

    void awaitForData(LogicalDatastoreType dsType, InstanceIdentifier<? extends DataObject> iid) {
        getAwaiter().until(() -> singleTxdataBroker.syncReadOptional(dsType, iid).isPresent());
    }

    void awaitForDataDelete(LogicalDatastoreType dsType, InstanceIdentifier<? extends DataObject> iid) {
        getAwaiter().until(() -> !singleTxdataBroker.syncReadOptional(dsType, iid).isPresent());
    }

    protected InstanceIdentifier<Flow> getFlowIid(short tableId, FlowId flowid, BigInteger dpnId) {

        FlowKey flowKey = new FlowKey(new FlowId(flowid));
        NodeId nodeId =
                new NodeId("openflow:" + dpnId);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node nodeDpn =
                new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();
        return InstanceIdentifier.builder(Nodes.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                        nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(tableId)).child(Flow.class, flowKey).build();
    }
}
