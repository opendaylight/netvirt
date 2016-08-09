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
    private final DataBroker dataBroker;

    public NatNodeEventListener(final DataBroker dataBroker) {
        super(Node.class);
        this.dataBroker = dataBroker;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("{} close", getClass().getSimpleName());
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
}
