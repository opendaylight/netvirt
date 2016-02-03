/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.dhcpservice.api.DHCPMConstants;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class NodeListener extends AbstractDataChangeListener<Node> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NodeListener.class);

    private IMdsalApiManager mdsalManager;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private DhcpManager dhcpManager;

    public NodeListener(final DataBroker db, final DhcpManager dhcpMgr) {
        super(Node.class);
        broker = db;
        dhcpManager = dhcpMgr;
        registerListener(db);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), NodeListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("NodeListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("NodeListener: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }


    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {

    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {

    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {
        NodeId nodeId = add.getId();
        String[] node =  nodeId.getValue().split(":");
        BigInteger dpId = new BigInteger(node[1]);
        dhcpManager.setupTableMissForDhcpTable(dpId);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up NodeListener.", e);
            }
            listenerRegistration = null;
            //ToDo: Should we delete DHCP flows when we are closed?
        }
        LOG.debug("Node Listener Closed");
    }
}
