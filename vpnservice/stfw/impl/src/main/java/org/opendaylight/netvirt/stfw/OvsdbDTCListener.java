/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw;

import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsBridge;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsConstants;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsSimulator;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsdbSwitch;
import org.opendaylight.netvirt.stfw.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OvsdbDTCListener implements DataTreeChangeListener<Node>, AutoCloseable {

    private ListenerRegistration<OvsdbDTCListener> registration;
    private final DataBroker db;
    private WriteTransaction topologyTransaction;
    WriteTransaction inventoryTransaction;
    private final OvsSimulator ovsSimulator;
    private static final Logger LOG = LoggerFactory.getLogger(OvsdbDTCListener.class);

    @Inject
    public OvsdbDTCListener(final DataBroker dataBroker, final OvsSimulator ovsSimulator) {
        LOG.info("Registering OvsDListener");
        this.db = dataBroker;
        this.ovsSimulator = ovsSimulator;
        registerListener(db);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void registerListener(DataBroker db) {
        final DataTreeIdentifier<Node> treeId =
            new DataTreeIdentifier<Node>(LogicalDatastoreType.CONFIGURATION, getWildcardPath());
        try {
            LOG.trace("Registering on path: {}", treeId);
            registration = db.registerDataTreeChangeListener(treeId, OvsdbDTCListener.this);
        } catch (final Exception e) {
            LOG.warn("OvsDTCListener registration failed", e);
        }
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<Node>> changes) {

        topologyTransaction = db.newWriteOnlyTransaction();
        inventoryTransaction = db.newWriteOnlyTransaction();
        for (DataTreeModification<Node> change : changes) {
            final InstanceIdentifier<Node> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Node> mod = change.getRootNode();
            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.trace("Data deleted: {}", mod.getDataBefore());
                    //disconnect(mod);
                    break;
                case SUBTREE_MODIFIED:
                    LOG.trace("Data updated to {}", mod.getDataAfter());
                    dataUpdated(mod);
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        LOG.trace("Data added: {}", mod.getDataAfter());
                    } else {
                        LOG.trace("Data updated to {}", mod.getDataAfter());
                    }
                    dataUpdated(mod);
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }
        MdsalUtils.submitTransaction(topologyTransaction);
        MdsalUtils.submitTransaction(inventoryTransaction);
    }

    @Override
    public void close() throws Exception {
        if (registration != null) {
            registration.close();
        }
    }

    private void dataUpdated(DataObjectModification<Node> mod) {
        Node node = mod.getDataAfter();
        List<TerminationPoint> tpList = node.getTerminationPoint();
        OvsdbBridgeAugmentation bridgeAug = node.getAugmentation(OvsdbBridgeAugmentation.class);
        if ((tpList == null)) {
            //We don't handle bridge node additions without termination points yet
            LOG.debug("No changes to tpList on bridge {}", node.getKey());
            return;
        }
        String bridgeName = null;
        OvsBridge bridge = null;
        OvsdbSwitch ovsSwitch = ovsSimulator.getSwitchFromNodeId(node.getNodeId());
        if (bridgeAug != null) {
            bridgeName = bridgeAug.getBridgeName().getValue();
            bridge = ovsSwitch.getBridge(bridgeName);
        } else {
            bridge = getBridgeFromNodeId(node.getNodeId(), ovsSwitch);
        }

        for (TerminationPoint tp : tpList) {
            String name = tp.getTpId().getValue();
            if (bridge.getPort(name) != null) {
                LOG.debug("Port {} already created, ignoring updates", name);
                continue;
            }
            ovsSimulator.addPort(topologyTransaction, inventoryTransaction, bridge, tp);
            LOG.debug("Added port {} to bridge{} on switch {}",
                name, bridge.getName(), ovsSwitch.getKey().getIpv4Address().getValue());
        }
    }

    private OvsBridge getBridgeFromNodeId(NodeId nodeId, OvsdbSwitch ovsSwitch) {
        // TODO Fix to not be default br-int but extract from nodeid.
        return ovsSwitch.getBridge(OvsConstants.DEFAULT_BRIDGE);
    }

    private InstanceIdentifier<Node> getWildcardPath() {
        InstanceIdentifier<Node> path = InstanceIdentifier
            .create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(OvsConstants.OVS_TOPOLOGY_ID))
            .child(Node.class);
        return path;
    }

}

