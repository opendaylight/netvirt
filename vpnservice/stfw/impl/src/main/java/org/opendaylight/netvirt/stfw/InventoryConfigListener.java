/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsSimulator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InventoryConfigListener extends AsyncDataTreeChangeListenerBase<Flow, InventoryConfigListener> {
    private static final Logger LOG = LoggerFactory.getLogger(InventoryConfigListener.class);

    private final DataBroker dataBroker;
    private final OvsSimulator ovsSimulator;

    @Inject
    public InventoryConfigListener(final DataBroker dataBroker, final OvsSimulator simulator) {
        super(Flow.class, InventoryConfigListener.class);
        this.dataBroker = dataBroker;
        this.ovsSimulator = simulator;
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        LOG.info("InterfaceConfigListener registered");
    }

    @Override
    protected void add(InstanceIdentifier<Flow> identifier, Flow flow) {
        LOG.trace("STFW:Received flow add event for Flow {}", flow.getId().getValue());
        String nodeId = String.valueOf(identifier.firstKeyOf(Node.class).getId());
        String [] splitId = nodeId.split("=");
        String dpnId = splitId[1].split(":")[1];
        dpnId = dpnId.substring(0, dpnId.length() - 1);
        LOG.trace("STFW:Incrementing flow count for DPN {}", dpnId);
        ovsSimulator.incrementDpnToFlowCount(dpnId);
        LOG.trace("STFW:Flow count for DPN {} is {}", dpnId, ovsSimulator.getDpnToFlowCount().get(dpnId));
    }

    @Override
    protected InstanceIdentifier<Flow> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class)
            .child(Table.class).child(Flow.class);
    }

    @Override
    protected InventoryConfigListener getDataTreeChangeListener() {
        return InventoryConfigListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Flow> identifier, Flow flow) {
        LOG.trace("STFW:Received flow remove event {}", flow);
        String nodeId = String.valueOf(identifier.firstKeyOf(Node.class).getId());
        String [] splitId = nodeId.split("=");
        String dpnId = splitId[1].split(":")[1];
        dpnId = dpnId.substring(0, dpnId.length() - 1);
        LOG.trace("STFW:Decrementing flow count for DPN {}", dpnId);
        ovsSimulator.decrementDpnToFlowCount(dpnId);
        LOG.trace("STFW:Flow count for DPN {} is {}", dpnId, ovsSimulator.getDpnToFlowCount().get(dpnId));

    }

    @Override
    protected void update(InstanceIdentifier<Flow> identifier,
                          Flow original, Flow update) {
        LOG.trace("STFW:Flow update event received {}", original);

    }
}
