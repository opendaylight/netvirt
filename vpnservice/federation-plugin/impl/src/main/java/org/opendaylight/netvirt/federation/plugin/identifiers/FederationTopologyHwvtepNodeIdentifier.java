/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin.identifiers;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationTopologyHwvtepNodeIdentifier
        implements FederationPluginIdentifier<Node, Topology, NetworkTopology> {

    private static final Logger LOG = LoggerFactory.getLogger(FederationTopologyHwvtepNodeIdentifier.class);

    @Inject
    public FederationTopologyHwvtepNodeIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(
                FederationPluginConstants.TOPOLOGY_HWVTEP_NODE_CONFIG_KEY, LogicalDatastoreType.CONFIGURATION, this);
        FederationPluginIdentifierRegistry.registerIdentifier(
                FederationPluginConstants.TOPOLOGY_HWVTEP_NODE_OPER_KEY, LogicalDatastoreType.OPERATIONAL, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public InstanceIdentifier<Node> getInstanceIdentifier() {
        InstanceIdentifier<Node> nodeId = InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, FederationPluginConstants.HWVTEP_TOPOLOGY_KEY).child(Node.class);
        LOG.info("InstanceId: {}", nodeId);
        return nodeId;
    }

    @Override
    public InstanceIdentifier<Topology> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(NetworkTopology.class).child(Topology.class,
                FederationPluginConstants.HWVTEP_TOPOLOGY_KEY);
    }

    @Override
    public InstanceIdentifier<NetworkTopology> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(NetworkTopology.class);
    }
}
