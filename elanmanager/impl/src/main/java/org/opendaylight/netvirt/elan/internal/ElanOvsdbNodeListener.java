/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.TransportZoneNotificationUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen for new OVSDB nodes and then make sure they have the necessary bridges configured.
 */
@Singleton
public class ElanOvsdbNodeListener extends AbstractAsyncDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanOvsdbNodeListener.class);
    private final DataBroker dataBroker;
    private final ElanBridgeManager bridgeMgr;
    private final IElanService elanProvider;
    private final boolean generateIntBridgeMac;
    private final boolean autoCreateBridge;
    private final TransportZoneNotificationUtil tzUtil;

    /**
     * Constructor.
     * @param dataBroker the DataBroker
     * @param elanConfig the elan configuration
     * @param bridgeMgr bridge manager
     * @param elanProvider elan provider
     * @param tzUtil TransportzoneNotificationUtils
     */
    @Inject
    public ElanOvsdbNodeListener(final DataBroker dataBroker, ElanConfig elanConfig,
                                 final ElanBridgeManager bridgeMgr,
                                 final IElanService elanProvider, final TransportZoneNotificationUtil tzUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID)).child(Node.class),
                Executors.newListeningSingleThreadExecutor("ElanOvsdbNodeListener", LOG));
        this.dataBroker = dataBroker;
        autoCreateBridge = elanConfig.isAutoCreateBridge();
        this.generateIntBridgeMac = elanConfig.isIntBridgeGenMac();
        this.bridgeMgr = bridgeMgr;
        this.elanProvider = elanProvider;
        this.tzUtil = tzUtil;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<Node> identifier, Node node) {
        elanProvider.deleteExternalElanNetworks(node);
    }

    @Override
    public void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
        LOG.debug("ElanOvsdbNodeListener.update, updated node detected. original: NodeId = {}", original.getNodeId());
        boolean integrationBridgeExist = bridgeMgr.isBridgeOnOvsdbNode(update, bridgeMgr.getIntegrationBridgeName());
        // ignore updates where the bridge was deleted
        if (!(bridgeMgr.isBridgeOnOvsdbNode(original, bridgeMgr.getIntegrationBridgeName())
                && !integrationBridgeExist)) {
            doNodeUpdate(update);
        }
        bridgeMgr.handleNewProviderNetBridges(original, update);
        if (integrationBridgeExist) {
            tzUtil.handleOvsdbNodeUpdate(original, update, identifier.firstKeyOf(Node.class).getNodeId().getValue());
        }
        elanProvider.updateExternalElanNetworks(original, update);
    }

    @Override
    public void add(InstanceIdentifier<Node> identifier, Node node) {
        LOG.debug("ElanOvsdbNodeListener.add, new node detected {}", node);
        doNodeUpdate(node);
        elanProvider.createExternalElanNetworks(node);
    }

    private void doNodeUpdate(Node node) {
        if (autoCreateBridge) {
            bridgeMgr.processNodePrep(node, generateIntBridgeMac);
        }
    }

    /* (non-Javadoc)
     * @see org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase#getDataTreeChangeListener()
     */
}
