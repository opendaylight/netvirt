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
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
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
    private final DataBroker dataBroker;
    private final VrfEntryListener vrfEntryListener;

    public FibNodeCapableListener(final DataBroker dataBroker, final VrfEntryListener vrfEntryListener) {
        super(FlowCapableNode.class);
        this.dataBroker = dataBroker;
        this.vrfEntryListener = vrfEntryListener;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), this, AsyncDataBroker.DataChangeScope.ONE);
    }

    private InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
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
    protected void add(InstanceIdentifier<FlowCapableNode> identifier, FlowCapableNode node) {
        LOG.trace("FlowCapableNode Added: key: {}, value: {}", identifier, node);
        NodeKey nodeKey = identifier.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        vrfEntryListener.processNodeAdd(dpnId);
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> identifier, FlowCapableNode del) {
    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> identifier, FlowCapableNode original, FlowCapableNode update) {
    }
}
