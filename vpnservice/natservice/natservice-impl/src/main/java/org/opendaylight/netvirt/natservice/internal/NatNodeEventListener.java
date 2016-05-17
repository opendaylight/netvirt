/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatNodeEventListener extends AbstractDataChangeListener<Node> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NatNodeEventListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;


    public NatNodeEventListener(final DataBroker db) {
        super(Node.class);
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), NatNodeEventListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("NatNodeEventListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("NatNodeEventListener: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
        LOG.debug("NatNodeEventListener: Node removed received");
        NodeId nodeId = del.getId();
        String[] node =  nodeId.getValue().split(":");
        if(node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpnId = new BigInteger(node[1]);
        LOG.debug("NodeId removed is {}",dpnId);
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {
        LOG.debug("NatNodeEventListener: Node added received");
        NodeId nodeId = add.getId();
        String[] node =  nodeId.getValue().split(":");
        if(node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpnId = new BigInteger(node[1]);
        LOG.debug("NodeId added is {}",dpnId);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("NatNodeEventListener Closed");
    }
}
