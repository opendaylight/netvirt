/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpNodeListener extends AbstractClusteredAsyncDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpNodeListener.class);
    private final DataBroker broker;

    @Inject
    public DhcpNodeListener(DataBroker broker) {
        super(broker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(Nodes.class).child(Node.class),
                Executors.newListeningSingleThreadExecutor("DhcpNodeListener", LOG));
        this.broker = broker;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void remove(InstanceIdentifier<Node> key, Node del) {
        LOG.trace("Received remove for {}", del);
        NodeId nodeId = del.getId();
        String[] node =  nodeId.getValue().split(":");
        if (node.length < 2) {
            LOG.error("DhcpNodeListener: Failed to remove Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        DhcpServiceUtils.removeFromDpnIdCache(Uint64.valueOf(dpId));
    }

    @Override
    public void update(InstanceIdentifier<Node> key, Node dataObjectModificationBefore,
            Node dataObjectModificationAfter) {
    }

    @Override
    public void add(InstanceIdentifier<Node> key, Node add) {
        LOG.trace("Received add for {}", add);
        NodeId nodeId = add.getId();
        String[] node =  nodeId.getValue().split(":");
        if (node.length < 2) {
            LOG.error("DhcpNodeListener: Failed to add Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        DhcpServiceUtils.addToDpnIdCache(Uint64.valueOf(dpId));
    }
}
