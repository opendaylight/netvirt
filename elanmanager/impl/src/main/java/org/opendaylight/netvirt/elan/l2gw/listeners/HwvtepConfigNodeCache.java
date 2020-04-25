/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HwvtepConfigNodeCache extends AbstractClusteredAsyncDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepConfigNodeCache.class);

    private final DataBroker dataBroker;
    private final Map<InstanceIdentifier<Node>, Node> cache = new ConcurrentHashMap<>();
    private final Map<InstanceIdentifier<Node>, List<Runnable>> waitList = new ConcurrentHashMap<>();

    @Inject
    public HwvtepConfigNodeCache(final DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class), Executors.newListeningSingleThreadExecutor("HwvtepConfigNodeCache", LOG));
        this.dataBroker = dataBroker;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(InstanceIdentifier<Node> key, Node deleted) {
        cache.remove(key);
    }

    @Override
    public void update(InstanceIdentifier<Node> key, Node old, Node added) {
        cache.put(key, added);
    }

    @Override
    public synchronized void add(InstanceIdentifier<Node> key, Node added) {
        cache.put(key, added);
        if (waitList.containsKey(key)) {
            waitList.remove(key).stream().forEach(runnable -> runnable.run());
        }
    }

    public Node getConfigNode(InstanceIdentifier<Node> key) {
        return cache.get(key);
    }

    public synchronized void runAfterNodeAvailable(InstanceIdentifier<Node> key, Runnable runnable) {
        if (cache.containsKey(key)) {
            runnable.run();
        } else {
            waitList.putIfAbsent(key, new ArrayList<>());
            waitList.get(key).add(runnable);
        }
    }
}