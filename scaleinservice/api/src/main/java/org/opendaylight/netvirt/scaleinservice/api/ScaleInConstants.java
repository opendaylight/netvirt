/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.scaleinservice.api;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
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

public final class ScaleInConstants {

    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));
    public static final String TOMBSTONED = "TOMBSTONED";
    public static final InstanceIdentifier<BridgeExternalIds> BRIDGE_EXTERNAL_IID
            = InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
            .child(Node.class)
            .augmentation(OvsdbBridgeAugmentation.class)
            .child(BridgeExternalIds.class, new BridgeExternalIdsKey(TOMBSTONED)).build();

    private ScaleInConstants() {
    }

    public static InstanceIdentifier<BridgeExternalIds> buildBridgeExternalIids(NodeId nodeId) {
        return InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(nodeId))
                .augmentation(OvsdbBridgeAugmentation.class)
                .child(BridgeExternalIds.class, new BridgeExternalIdsKey(TOMBSTONED)).build();
    }

    public static BridgeExternalIds buildBridgeExternalIds(Boolean tombstone) {
        return new BridgeExternalIdsBuilder()
                .setBridgeExternalIdKey(TOMBSTONED)
                .setBridgeExternalIdValue(tombstone.toString())
                .build();
    }
}
