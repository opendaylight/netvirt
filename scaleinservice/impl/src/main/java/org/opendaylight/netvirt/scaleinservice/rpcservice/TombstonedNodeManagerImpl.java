/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.scaleinservice.rpcservice;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.netvirt.scaleinservice.api.ScaleInConstants;
import org.opendaylight.netvirt.scaleinservice.api.TombstonedNodeManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIds;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TombstonedNodeManagerImpl implements TombstonedNodeManager {

    private static final Logger LOG = LoggerFactory.getLogger(TombstonedNodeManagerImpl.class);

    private final DataBroker dataBroker;
    private final CacheProvider cacheProvider;
    private final Set<Function<BigInteger, Void>> callbacks = ConcurrentHashMap.newKeySet();
    private final NodeIdToDpnConverter nodeIdToDpnConverter;

    private InstanceIdDataObjectCache<BridgeExternalIds> bridgeExternalIdsCache;

    @Inject
    public TombstonedNodeManagerImpl(DataBroker dataBroker,
                                     CacheProvider cacheProvider,
                                     NodeIdToDpnConverter nodeIdToDpnConverter) {
        this.dataBroker = dataBroker;
        this.cacheProvider = cacheProvider;
        this.nodeIdToDpnConverter = nodeIdToDpnConverter;
    }

    @PostConstruct
    void init() {
        bridgeExternalIdsCache = new InstanceIdDataObjectCache<BridgeExternalIds>(BridgeExternalIds.class, dataBroker,
                LogicalDatastoreType.CONFIGURATION, ScaleInConstants.BRIDGE_EXTERNAL_IID,
                cacheProvider) {
            @Override
            @SuppressWarnings({"all"})
            protected void removed(InstanceIdentifier<BridgeExternalIds> path, BridgeExternalIds dataObject) {
                InstanceIdentifier<Node> nodePath = path.firstIdentifierOf(Node.class);
                if (isNodePresentInConfig(nodePath)) {
                    BigInteger dpnId = nodeIdToDpnConverter.getDpnId(path.firstKeyOf(Node.class).getNodeId());
                    if (dpnId != null) {
                        callbacks.forEach(callback -> {
                            try {
                                callback.apply(dpnId);
                            } catch (Exception e) {
                                LOG.error("Failed to call on recovery callback");
                            }
                        });
                    } else {
                        LOG.error("Failed to find dpn id for node {}", path);
                    }
                }
            }
        };
    }

    @PreDestroy
    void close() {
        bridgeExternalIdsCache.close();
    }

    private boolean isNodePresentInConfig(InstanceIdentifier<Node> nodePath) {
        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        try {
            return tx.read(LogicalDatastoreType.CONFIGURATION, nodePath).get().isPresent();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read {}", nodePath);
        }
        tx.close();
        return false;
    }

    @Override
    public boolean isDpnTombstoned(BigInteger dpnId) throws ReadFailedException {
        NodeId nodeId = nodeIdToDpnConverter.getNodeId(dpnId);
        if (nodeId != null) {
            InstanceIdentifier<BridgeExternalIds> iid = ScaleInConstants.buildBridgeExternalIids(nodeId);
            Optional<BridgeExternalIds> optional = bridgeExternalIdsCache.get(iid);
            return optional.isPresent() && Boolean.parseBoolean(optional.get().getBridgeExternalIdValue());
        }
        return false;
    }

    @Override
    public void addOnRecoveryCallback(Function<BigInteger, Void> callback) {
        callbacks.add(callback);
    }

    @Override
    public List<BigInteger> filterTombStoned(List<BigInteger> dpns) throws ReadFailedException {
        return dpns.stream().filter((dpn) -> {
            try {
                return !isDpnTombstoned(dpn);
            } catch (ReadFailedException e) {
                LOG.error("Failed to read {}", dpn);
                return true;
            }
        }).collect(Collectors.toList());
    }
}
