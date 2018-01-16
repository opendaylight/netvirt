/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.scaleinservice.rpcservice;

import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesEndInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesRecoverInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinComputesStartInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.scalein.rpcs.rev171220.ScaleinRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIdsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ScaleInRpcManager implements ScaleinRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(ScaleInRpcManager.class);
    private static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));
    private static final InstanceIdentifier<Topology> OVSDB_NODE_IID = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID));
    private static final String TOMBSTONED = "TOMBSTONED";

    private final DataBroker dataBroker;

    @Inject
    public ScaleInRpcManager(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public Future<RpcResult<Void>> scaleinComputesRecover(ScaleinComputesRecoverInput input) {
        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
        input.getRecoverNodeIds().forEach(s -> tombstoneTheNode(s, tx, true));
        try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to tombstone all the nodes {} ", input.getRecoverNodeIds());
            return Futures.immediateFuture(RpcResultBuilder.<Void>failed().build());
        }
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    @Override
    public Future<RpcResult<Void>> scaleinComputesEnd(ScaleinComputesEndInput input) {
        //TODO
        return null;
    }

    @Override
    public Future<RpcResult<Void>> scaleinComputesStart(ScaleinComputesStartInput input) {
        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
        input.getScaleinNodeIds().forEach(s -> tombstoneTheNode(s, tx, true));
        try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to tombstone all the nodes {} ", input.getScaleinNodeIds());
            return Futures.immediateFuture(RpcResultBuilder.<Void>failed().build());
        }
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    private void tombstoneTheNode(String nodeId, ReadWriteTransaction tx, Boolean tombstone) {
        InstanceIdentifier<BridgeExternalIds> bridgeExternalIdsInstanceIdentifier = OVSDB_NODE_IID
                .child(Node.class, new NodeKey(new NodeId(nodeId))).augmentation(OvsdbBridgeAugmentation.class)
                .child(BridgeExternalIds.class, new BridgeExternalIdsKey(TOMBSTONED));
        BridgeExternalIds bridgeExternalIds = new BridgeExternalIdsBuilder()
                .setBridgeExternalIdKey(TOMBSTONED)
                .setBridgeExternalIdValue(tombstone.toString())
                .build();
        if (tombstone) {
            tx.put(LogicalDatastoreType.CONFIGURATION, bridgeExternalIdsInstanceIdentifier, bridgeExternalIds, true);
        } else {
            tx.delete(LogicalDatastoreType.CONFIGURATION, bridgeExternalIdsInstanceIdentifier);
        }
    }
}
