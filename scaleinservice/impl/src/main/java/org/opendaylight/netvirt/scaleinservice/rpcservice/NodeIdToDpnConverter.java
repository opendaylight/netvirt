/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.scaleinservice.rpcservice;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class NodeIdToDpnConverter {

    private final DataBroker dataBroker;
    private final CacheProvider cacheProvider;
    private final Map<NodeId, BigInteger> nodeToDpnId = new ConcurrentHashMap<>();
    private InstanceIdDataObjectCache<BridgeRefEntry> dpnBridgeRefCache;

    @Inject
    public NodeIdToDpnConverter(final DataBroker dataBroker, CacheProvider cacheProvider) {
        this.dataBroker = dataBroker;
        this.cacheProvider = cacheProvider;
    }

    @PostConstruct
    void init() {
        dpnBridgeRefCache = new InstanceIdDataObjectCache<BridgeRefEntry>(BridgeRefEntry.class, dataBroker,
                LogicalDatastoreType.OPERATIONAL, getBridgeRefEntryIdentifier(),
                cacheProvider) {
            @Override
            protected void added(InstanceIdentifier<BridgeRefEntry> path, BridgeRefEntry dataObject) {
                addBridgeRef(path, dataObject);
            }
        };
    }

    @PreDestroy
    void close() {
        dpnBridgeRefCache.close();
    }

    private InstanceIdentifier<BridgeRefEntry> getBridgeRefEntryIdentifier() {
        return InstanceIdentifier.builder(BridgeRefInfo.class)
                .child(BridgeRefEntry.class).build();
    }

    private InstanceIdentifier<BridgeRefEntry> getBridgeRefEntryIdentifier(BigInteger dpnId) {
        return InstanceIdentifier.builder(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId)).build();
    }

    private NodeId addBridgeRef(InstanceIdentifier<BridgeRefEntry> path, BridgeRefEntry dataObject) {
        InstanceIdentifier<Node> iid = (InstanceIdentifier<Node>) dataObject.getBridgeReference().getValue();
        NodeId nodeId = iid.firstKeyOf(Node.class).getNodeId();
        nodeToDpnId.put(nodeId, dataObject.getDpid());
        return  nodeId;
    }

    public BigInteger getDpnId(NodeId nodeId) {
        return nodeToDpnId.get(nodeId);
    }

    public NodeId getNodeId(BigInteger dpnId) throws ReadFailedException {
        InstanceIdentifier<BridgeRefEntry> path = getBridgeRefEntryIdentifier(dpnId);
        BridgeRefEntry bridgeRefEntry = dpnBridgeRefCache.get(path).orNull();
        if (bridgeRefEntry != null) {
            return addBridgeRef(path, bridgeRefEntry);
        }
        return null;
    }
}
