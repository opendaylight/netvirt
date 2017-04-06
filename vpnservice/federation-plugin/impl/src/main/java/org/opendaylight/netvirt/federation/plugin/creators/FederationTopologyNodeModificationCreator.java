/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.creators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederationPluginConstants;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FederationTopologyNodeModificationCreator implements FederationPluginModificationCreator<Node, Topology> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationTopologyNodeModificationCreator.class);

    @Inject
    public FederationTopologyNodeModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.TOPOLOGY_NODE_CONFIG_KEY, this);
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.TOPOLOGY_NODE_OPER_KEY, this);
    }

    @Override
    public Collection<DataTreeModification<Node>> createDataTreeModifications(Topology topology) {
        if (topology == null || topology.getNode() == null) {
            LOG.debug("No topology nodes found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<Node>> modifications = new ArrayList<>();
        for (Node node : topology.getNode()) {
            modifications.add(new FullSyncDataTreeModification<Node>(node));
        }

        return modifications;
    }

}
