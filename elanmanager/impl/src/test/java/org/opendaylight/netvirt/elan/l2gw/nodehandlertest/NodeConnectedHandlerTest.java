/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.nodehandlertest;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.Optional;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.handlers.NodeConnectedHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by eaksahu on 10/14/2016.
 */
public class NodeConnectedHandlerTest extends AbstractConcurrentDataBrokerTest {

    // Uncomment this to keep running this test indefinitely
    // This is very useful to detect concurrency issues, respectively prove
    // that the use of AbstractConcurrentDataBrokerTest instead of AbstractDataBrokerTest
    // does NOT cause any concurrency issues and make this test flaky...
    // public static @ClassRule RunUntilFailureClassRule classRepeater = new RunUntilFailureClassRule();
    // public @Rule RunUntilFailureRule repeater = new RunUntilFailureRule(classRepeater);

    static Logger LOG = LoggerFactory.getLogger(NodeConnectedHandlerTest.class);

    NodeConnectedHandler nodeConnectedHandler;
    NodeConnectedHandlerUtils handlerUtils = new NodeConnectedHandlerUtils();

    String d1UUID;
    String d2UUID;

    Optional<Node> d1GlobalOpNode;
    Optional<Node> d2GlobalOpNode;
    Optional<Node> haGlobalOpNode;

    Optional<Node> d1PsOpNode;
    Optional<Node> d2PsOpNode;
    Optional<Node> haPsOpNode;

    Optional<Node> d1GlobalConfigNode;
    Optional<Node> d2GlobalConfigNode;
    Optional<Node> haGlobalConfigNode;

    Optional<Node> d1PsConfigNode;
    Optional<Node> d2PsConfigNode;
    Optional<Node> haPsConfigNode;

    static String managerHAId = "s3-clusterid";
    static String switchName = "s3";

    InstanceIdentifier<Node> d1NodePath;
    InstanceIdentifier<Node> d2NodePath;
    InstanceIdentifier<Node> haNodePath;

    InstanceIdentifier<Node> d1PsNodePath;
    InstanceIdentifier<Node> d2PsNodePath;
    InstanceIdentifier<Node> haPsNodePath;

    NodeId haNodeId;

    @Before
    public void setupForHANode() {
        d1UUID = java.util.UUID.nameUUIDFromBytes("d1uuid".getBytes()).toString();
        d2UUID = java.util.UUID.nameUUIDFromBytes("d2uuid".getBytes()).toString();
        d1NodePath = getInstanceIdentifier(d1UUID);
        d2NodePath = getInstanceIdentifier(d2UUID);
        haNodePath = getInstanceIdentifier(managerHAId);

        haNodeId = getNodeId(managerHAId);

        NodeId d1NodeId = d1NodePath.firstKeyOf(Node.class).getNodeId();
        String d1PsNodeIdVal = d1NodeId.getValue() + "/physicalswitch/" + switchName;
        d1PsNodePath = createInstanceIdentifier(d1PsNodeIdVal);

        NodeId d2NodeId = d2NodePath.firstKeyOf(Node.class).getNodeId();
        String d2PsNodeIdVal = d2NodeId.getValue() + "/physicalswitch/" + switchName;
        d2PsNodePath = createInstanceIdentifier(d2PsNodeIdVal);

        haPsNodePath = createInstanceIdentifier(haNodeId.getValue() + "/physicalswitch/" + switchName);

        nodeConnectedHandler = new NodeConnectedHandler(getDataBroker(), Mockito.mock(HwvtepNodeHACache.class));
    }

    @Test
    public void testD1Connect() throws Exception {
        ManagedNewTransactionRunner txRunner = new ManagedNewTransactionRunnerImpl(getDataBroker());
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
            tx -> handlerUtils.addPsNode(d1PsNodePath, d1NodePath, DataProvider.getPortNameListD1(), tx)).get();

        txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
            tx -> handlerUtils.addNode(d1NodePath, d1PsNodePath, DataProvider.getLogicalSwitchDataD1(),
                DataProvider.getLocalUcasMacDataD1(), DataProvider.getLocalMcastDataD1(),
                DataProvider.getRemoteMcastDataD1(), DataProvider.getRemoteUcasteMacDataD1(),
                DataProvider.getGlobalTerminationPointIpD1(), tx)).get();

        readNodes();
        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
            confTx -> txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL,
                operTx -> nodeConnectedHandler.handleNodeConnected(d1GlobalOpNode.get(), d1NodePath, haNodePath,
                    haGlobalConfigNode, haPsConfigNode, confTx, operTx)).get()).get();
        readNodes();
        //verify global ha manager config should have ha_children
        Assert.assertTrue(haGlobalConfigNode.isPresent());
        Assert.assertTrue(d1GlobalOpNode.isPresent());
        TestUtil.verifyHAconfigNode(haGlobalConfigNode.get(), d1GlobalOpNode.get());

        Assert.assertTrue(d1GlobalOpNode.isPresent());
        Assert.assertTrue(haGlobalOpNode.isPresent());
        Assert.assertTrue(d1PsOpNode.isPresent());
        Assert.assertTrue(haPsOpNode.isPresent());
        TestUtil.verifyHAOpNode(d1GlobalOpNode.get(), haGlobalOpNode.get(),
                d1PsOpNode.get(), haPsOpNode.get(), haNodePath, d1PsNodePath, haPsNodePath, getDataBroker());
    }

    public static InstanceIdentifier<Node> createInstanceIdentifier(String nodeIdString) {
        NodeId nodeId = new NodeId(new Uri(nodeIdString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(new TopologyId(new Uri("hwvtep:1")));
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, topoKey)
                .child(Node.class, nodeKey)
                .build();
    }

    public static InstanceIdentifier<Node> getInstanceIdentifier(String haUUidVal) {
        String nodeString = "hwvtep://uuid/" + UUID.nameUUIDFromBytes(haUUidVal.getBytes()).toString();
        NodeId nodeId = new NodeId(new Uri(nodeString));
        NodeKey nodeKey = new NodeKey(nodeId);
        TopologyKey topoKey = new TopologyKey(new TopologyId(new Uri("hwvtep:1")));
        return InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, topoKey)
                .child(Node.class, nodeKey).build();
    }

    public static NodeId getNodeId(String haId) {
        String nodeString = "hwvtep://uuid/" + UUID.nameUUIDFromBytes(haId.getBytes()).toString();
        NodeId nodeId = new NodeId(new Uri(nodeString));
        return nodeId;
    }

    public void readNodes() throws Exception {
        ReadTransaction tx = getDataBroker().newReadOnlyTransaction();
        d1GlobalOpNode = TestUtil.readNode(LogicalDatastoreType.OPERATIONAL, d1NodePath, tx);
        d2GlobalOpNode = TestUtil.readNode(LogicalDatastoreType.OPERATIONAL, d2NodePath, tx);
        haGlobalOpNode = TestUtil.readNode(LogicalDatastoreType.OPERATIONAL, haNodePath, tx);

        d1PsOpNode = TestUtil.readNode(LogicalDatastoreType.OPERATIONAL, d1PsNodePath, tx);
        d2PsOpNode = TestUtil.readNode(LogicalDatastoreType.OPERATIONAL, d2PsNodePath, tx);
        haPsOpNode = TestUtil.readNode(LogicalDatastoreType.OPERATIONAL, haPsNodePath, tx);

        haGlobalConfigNode = TestUtil.readNode(LogicalDatastoreType.CONFIGURATION, haNodePath, tx);
        d1GlobalConfigNode = TestUtil.readNode(LogicalDatastoreType.CONFIGURATION, d1NodePath, tx);
        d2GlobalConfigNode = TestUtil.readNode(LogicalDatastoreType.CONFIGURATION, d2NodePath, tx);

        haPsConfigNode = TestUtil.readNode(LogicalDatastoreType.CONFIGURATION, haPsNodePath, tx);
        d1PsConfigNode = TestUtil.readNode(LogicalDatastoreType.CONFIGURATION, d1PsNodePath, tx);
        d2PsConfigNode = TestUtil.readNode(LogicalDatastoreType.CONFIGURATION, d2PsNodePath, tx);

        tx.close();
    }

}
