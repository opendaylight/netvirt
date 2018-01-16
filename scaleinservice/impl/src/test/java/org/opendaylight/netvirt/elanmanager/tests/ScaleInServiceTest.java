/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.netvirt.scaleinservice.api.ScaleInConstants;
import org.opendaylight.netvirt.scaleinservice.rpcservice.ScaleInRpcManager;
import org.opendaylight.netvirt.scaleinservice.rpcservice.TombstonedNodeManagerImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesRecoverInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesStartInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIds;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScaleInServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(ScaleInServiceTest.class);

    private static final String NODEID1  = "ovsdb://uuid/2de70a99-29a5-4f2a-b87e-96d6ed57e411/bridge/br-int";
    private static final String NODEID2  = "ovsdb://uuid/2de70a99-29a5-4f2a-b87e-96d6ed57e412/bridge/br-int";
    private static final String NODEID3  = "ovsdb://uuid/2de70a99-29a5-4f2a-b87e-96d6ed57e413/bridge/br-int";

    private static final BigInteger DPN1 = new BigInteger("1");
    private static final BigInteger DPN2 = new BigInteger("2");
    private static final BigInteger DPN3 = new BigInteger("3");
    private static final BigInteger DPN4 = new BigInteger("4");

    private static final InstanceIdentifier<BridgeRefEntry> DPN1_BRIDGE_REF
        = InstanceIdentifier.builder(BridgeRefInfo.class)
        .child(BridgeRefEntry.class, new BridgeRefEntryKey(DPN1)).build();

    private static final InstanceIdentifier<BridgeRefEntry> DPN2_BRIDGE_REF
        = InstanceIdentifier.builder(BridgeRefInfo.class)
        .child(BridgeRefEntry.class, new BridgeRefEntryKey(DPN2)).build();

    private static final InstanceIdentifier<BridgeRefEntry> DPN3_BRIDGE_REF
        = InstanceIdentifier.builder(BridgeRefInfo.class)
        .child(BridgeRefEntry.class, new BridgeRefEntryKey(DPN3)).build();

    private static ConditionFactory AWAITER = Awaitility.await("TestableListener")
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS);

    public @Rule LogRule logRule = new LogRule();
    public @Rule MethodRule guice = new GuiceRule(ScaleInServiceTestModule.class, JobCoordinatorTestModule.class);

    private @Inject ScaleInRpcManager scaleInRpcManager;
    private @Inject TombstonedNodeManagerImpl tombstonedNodeManager;
    private @Inject SingleTransactionDataBroker dataBroker;

    @Before public void before() throws TransactionCommitFailedException {
        dataBroker.syncWrite(OPERATIONAL, DPN1_BRIDGE_REF, buildBridgeRefEntry(DPN1, NODEID1));
        dataBroker.syncWrite(OPERATIONAL, DPN2_BRIDGE_REF, buildBridgeRefEntry(DPN2, NODEID2));
        dataBroker.syncWrite(OPERATIONAL, DPN3_BRIDGE_REF, buildBridgeRefEntry(DPN3, NODEID3));
    }

    @Test public void testScaleinComputesStartRpc()
            throws ExecutionException, InterruptedException, ReadFailedException {

        Future<RpcResult<Void>> ft = scaleInRpcManager.scaleinComputesStart(
                new ScaleinComputesStartInputBuilder().setScaleinNodeIds(
                        Lists.newArrayList(NODEID1, NODEID2)).build());
        assertTrue("Scalein computes start rpc should return success code ", ft.get().isSuccessful());

        BridgeExternalIds bridgeExternalIds = dataBroker.syncRead(
                CONFIGURATION, ScaleInConstants.buildBridgeExternalIids(new NodeId(NODEID1)));
        assertNotNull("Bridge externalids should be found in config topology", bridgeExternalIds);
        assertTrue("Bridge externalids should be set to true",
                Boolean.parseBoolean(bridgeExternalIds.getBridgeExternalIdValue()));

    }

    @Test public void testScaleinComputesRecoverRpc()
            throws ExecutionException, InterruptedException, ReadFailedException {

        testScaleinComputesStartRpc();

        Future<RpcResult<Void>> ft = scaleInRpcManager.scaleinComputesRecover(
                new ScaleinComputesRecoverInputBuilder().setRecoverNodeIds(Lists.newArrayList(NODEID1)).build());
        assertTrue("scalein computes recover rpc should return success code", ft.get().isSuccessful());

        Optional<BridgeExternalIds> bridgeExternalIdsOptional = dataBroker.syncReadOptional(
                CONFIGURATION, ScaleInConstants.buildBridgeExternalIids(new NodeId(NODEID1)));
        assertFalse("Bridge externalids should be deleted from config topology",
                bridgeExternalIdsOptional.isPresent());

        BridgeExternalIds bridgeExternalIds = dataBroker.syncRead(
                CONFIGURATION, ScaleInConstants.buildBridgeExternalIids(new NodeId(NODEID2)));
        assertNotNull("Bridge externalids should not be deleted from config topology", bridgeExternalIds);
    }

    @Test public void testIsDpnTombstonedApi() throws ExecutionException, InterruptedException, ReadFailedException {

        testScaleinComputesStartRpc();
        assertTrue("Dpn 1 should be marked as tombstoned", tombstonedNodeManager.isDpnTombstoned(DPN1));
        assertTrue("Dpn 2 should be marked as tombstoned", tombstonedNodeManager.isDpnTombstoned(DPN2));
    }

    @Test public void testfilterTombstoned() throws ExecutionException, InterruptedException, ReadFailedException {

        testScaleinComputesStartRpc();
        List<BigInteger> filtered = tombstonedNodeManager.filterTombStoned(Lists.newArrayList(DPN1, DPN2, DPN3, DPN4));
        assertTrue("Dpn 1 and 2 should be filtered ",
                Sets.difference(Sets.newHashSet(DPN3, DPN4), Sets.newHashSet(filtered)).isEmpty());
    }

    @Test public void testRecoveryCallback() throws ExecutionException, InterruptedException, ReadFailedException {

        Set<BigInteger> nodesRecoverd = new HashSet<>();
        tombstonedNodeManager.addOnRecoveryCallback((dpnId) -> {
            nodesRecoverd.add(dpnId);
            return null;
        });
        testScaleinComputesRecoverRpc();
        AWAITER.until(() -> nodesRecoverd.contains(DPN1));
    }

    private BridgeRefEntry buildBridgeRefEntry(BigInteger dpnId, String nodeId) {
        return new BridgeRefEntryBuilder()
            .setDpid(dpnId)
            .setBridgeReference(new OvsdbBridgeRef(buildNodeIid(nodeId)))
            .build();
    }

    public static InstanceIdentifier<Node> buildNodeIid(String nodeId) {
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(ScaleInConstants.OVSDB_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(new NodeId(nodeId))).build();
    }
}
