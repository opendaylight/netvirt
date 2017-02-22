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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("deprecation")
public class FederationInventoryNodeModificationCreator implements FederationPluginModificationCreator<Node, Nodes> {
    private static final Logger LOG = LoggerFactory.getLogger(FederationInventoryNodeModificationCreator.class);

    @Inject
    public FederationInventoryNodeModificationCreator() {
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY, this);
        FederationPluginCreatorRegistry.registerCreator(FederationPluginConstants.INVENTORY_NODE_OPER_KEY, this);
    }

    @Override
    public Collection<DataTreeModification<Node>> createDataTreeModifications(Nodes nodes) {
        if (nodes == null || nodes.getNode() == null) {
            LOG.debug("No inventory nodes found");
            return Collections.emptyList();
        }

        Collection<DataTreeModification<Node>> modifications = new ArrayList<>();
        for (Node node : nodes.getNode()) {
            modifications.add(new FullSyncDataTreeModification<Node>(node));
        }

        return modifications;
    }

}
