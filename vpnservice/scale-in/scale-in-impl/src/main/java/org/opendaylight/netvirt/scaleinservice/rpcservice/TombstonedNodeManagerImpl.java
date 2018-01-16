/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.scaleinservice.rpcservice;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.cache.DataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.bridge.attributes.BridgeExternalIds;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class TombstonedNodeManagerImpl implements TombstonedNodeManager {

    private static final Logger LOG = LoggerFactory.getLogger(TombstonedNodeManagerImpl.class);
    private final DataBroker dataBroker;
    private final CacheProvider cacheProvider;
    private final Set<Function<BigInteger, Void>> callbacks = ConcurrentHashMap.newKeySet();
    private DataObjectCache<BridgeExternalIds> dataObjectCache;

    @Inject
    public TombstonedNodeManagerImpl(final DataBroker dataBroker, CacheProvider cacheProvider) {
        this.dataBroker = dataBroker;
        this.cacheProvider = cacheProvider;
    }

    @PostConstruct
    @SuppressWarnings({"all"})
    void init() {
        dataObjectCache = new DataObjectCache<BridgeExternalIds>(BridgeExternalIds.class, dataBroker,
                LogicalDatastoreType.CONFIGURATION, ScaleInConstants.EXTERNAL_IID,
                cacheProvider) {
            @Override
            protected void removed(InstanceIdentifier<BridgeExternalIds> path, BridgeExternalIds dataObject) {
                callbacks.forEach(callback -> {
                    try {
                        BigInteger dpnId = null;//TODO
                        callback.apply(dpnId);
                    } catch (Exception e) {
                        LOG.error("Failed to call on recovery callback");
                    }
                });
            }
        };
    }

    @PreDestroy
    void close() {
        dataObjectCache.close();
    }

    @Override
    public boolean isDpnTombstoned(BigInteger dpnId) throws ReadFailedException {
        /*
        String nodeId = null;
        InstanceIdentifier<BridgeExternalIds> iid = ScaleInConstants.buildBridgeExternalIids(nodeId);
        Optional<BridgeExternalIds> optional = dataObjectCache.get(iid);
        return optional.isPresent() && Boolean.parseBoolean(optional.get().getBridgeExternalIdValue());
        */
        return false;//TODO
    }

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
