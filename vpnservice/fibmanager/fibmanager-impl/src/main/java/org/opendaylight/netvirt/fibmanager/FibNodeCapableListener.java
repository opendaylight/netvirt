/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager;

import java.math.BigInteger;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibNodeCapableListener extends AbstractDataChangeListener<FlowCapableNode> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(FibNodeCapableListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private VfrEntryListener fibManager;

    public FibNodeCapableListener(final DataBroker dataBroker, VfrEntryListener fibManager) {
        super(FlowCapableNode.class);
        this.fibManager = fibManager;
        registerListener(dataBroker);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), FibNodeCapableListener.this, DataChangeScope.ONE);
        } catch (final Exception e) {
            LOG.error("FibNodeConnectorListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("FibNodeConnectorListener: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
        LOG.info("FibNodeConnectorListener Closed");
    }

    @Override
    protected void add(InstanceIdentifier<FlowCapableNode> identifier, FlowCapableNode node) {
        LOG.trace("FlowCapableNode Added: key: " + identifier + ", value=" + node );
        NodeKey nodeKey = identifier.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        fibManager.processNodeAdd(dpnId);
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> identifier, FlowCapableNode del) {
    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> identifier, FlowCapableNode original, FlowCapableNode update) {
    }
}
