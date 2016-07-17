/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.BridgeUtils;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listen for new OVSDB nodes and then make sure they have the necessary bridges configured
 */
public class ElanOvsdbNodeListener extends AbstractDataChangeListener<Node> {

    private static final Logger logger = LoggerFactory.getLogger(ElanOvsdbNodeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private BridgeUtils bridgeUtils;
    private IElanService elanProvider;
    private boolean generateIntBridgeMac;

    /**
     * Constructor
     * @param db the DataBroker
     * @param generateIntBridgeMac true if the integration bridge should be given a fixed, random MAC address
     */
    public ElanOvsdbNodeListener(final DataBroker db, boolean generateIntBridgeMac, BridgeUtils bridgeUtils, IElanService elanProvider) {
        super(Node.class);
        this.bridgeUtils = bridgeUtils;
        this.elanProvider = elanProvider;
        this.generateIntBridgeMac = generateIntBridgeMac;
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), ElanOvsdbNodeListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("ElanOvsdbNodeListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("ElanOvsdbNodeListener: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                .child(Node.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
        logger.debug("ElanOvsdbNodeListener.update, updated node detected. original: {} new: {}", original, update);
        doNodeUpdate(update);
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node node) {
        logger.debug("ElanOvsdbNodeListener.add, new node detected {}", node);
        doNodeUpdate(node);
    }

    private void doNodeUpdate(Node node) {
        bridgeUtils.processNodePrep(node, generateIntBridgeMac);
        if (bridgeUtils.isOvsdbNode(node)) {
            elanProvider.createExternalElanNetworks(node);
        }
    }

}
