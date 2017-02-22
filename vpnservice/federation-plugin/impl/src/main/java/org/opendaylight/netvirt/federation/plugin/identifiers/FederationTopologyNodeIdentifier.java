/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.identifiers;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class FederationTopologyNodeIdentifier implements FederationPluginIdentifier<Node, Topology, NetworkTopology> {

    @Inject
    public FederationTopologyNodeIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY,
                LogicalDatastoreType.OPERATIONAL, this);
    }

    @Override
    public InstanceIdentifier<Node> getInstanceIdentifier() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(new Uri("ovsdb:1")))).child(Node.class);
    }

    @Override
    public InstanceIdentifier<Topology> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                new TopologyKey(new TopologyId(new Uri("ovsdb:1"))));
    }

    @Override
    public InstanceIdentifier<NetworkTopology> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(NetworkTopology.class);
    }
}
