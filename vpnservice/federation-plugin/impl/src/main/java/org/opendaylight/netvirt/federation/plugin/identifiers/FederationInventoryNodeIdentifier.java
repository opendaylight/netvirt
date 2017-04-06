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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
@SuppressWarnings("deprecation")
public class FederationInventoryNodeIdentifier implements FederationPluginIdentifier<Node, Nodes, Nodes> {

    @Inject
    public FederationInventoryNodeIdentifier() {
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.INVENTORY_NODE_CONFIG_KEY,
                LogicalDatastoreType.CONFIGURATION, this);
        FederationPluginIdentifierRegistry.registerIdentifier(FederationPluginConstants.INVENTORY_NODE_OPER_KEY,
                LogicalDatastoreType.OPERATIONAL, this);
    }

    @Override
    public InstanceIdentifier<Node> getInstanceIdentifier() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    public InstanceIdentifier<Nodes> getParentInstanceIdentifier() {
        return InstanceIdentifier.create(Nodes.class);
    }

    @Override
    public InstanceIdentifier<Nodes> getSubtreeInstanceIdentifier() {
        return InstanceIdentifier.create(Nodes.class);
    }

}
