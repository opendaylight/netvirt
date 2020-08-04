/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConfigMcastCache
        extends AsyncClusteredDataTreeChangeListenerBase<RemoteMcastMacs, ConfigMcastCache> {

    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");
    private final DataBroker dataBroker;
    private final Map<InstanceIdentifier, RemoteMcastMacs> cache = new ConcurrentHashMap<>();
    private final IdManagerService idManager;

    @Inject
    public ConfigMcastCache(DataBroker dataBroker, final IdManagerService idManager) {
        super(RemoteMcastMacs.class, ConfigMcastCache.class);
        this.dataBroker = dataBroker;
        this.idManager = idManager;
    }

    @PostConstruct
    public void init() {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        //RegisterListener is done in L2GatewayConnectionListener
        //this.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    public void remove(InstanceIdentifier<RemoteMcastMacs> identifier, RemoteMcastMacs del) {
        LOG.trace("Got mcast remove {}" , del);
        cache.remove(del.getLogicalSwitchRef().getValue());
    }

    @Override
    public void update(InstanceIdentifier<RemoteMcastMacs> identifier,
                           RemoteMcastMacs original,
                           RemoteMcastMacs update) {
        LOG.trace("Got mcast update {}", update);
        cache.put(update.getLogicalSwitchRef().getValue(), update);
    }

    @Override
    public void add(InstanceIdentifier<RemoteMcastMacs> identifier, RemoteMcastMacs add) {
        LOG.trace("Got mcast add {}", add);
        cache.put(add.getLogicalSwitchRef().getValue(), add);
    }

    @Override
    protected InstanceIdentifier<RemoteMcastMacs> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class).augmentation(HwvtepGlobalAugmentation.class)
                .child(RemoteMcastMacs.class);
    }

    public RemoteMcastMacs getMac(InstanceIdentifier lsIid) {
        return cache.get(lsIid);
    }

    @Override
    protected ConfigMcastCache getDataTreeChangeListener() {
        return ConfigMcastCache.this;
    }
}