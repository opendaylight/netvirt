/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NodeListener extends AsyncDataTreeChangeListenerBase<Node, NodeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NodeListener.class);

    private final DataBroker broker;
    private final DhcpManager dhcpManager;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public NodeListener(final DataBroker db, final DhcpManager dhcpMgr,
            final DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        super(Node.class, NodeListener.class);
        this.broker = db;
        this.dhcpManager = dhcpMgr;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.txRunner = new ManagedNewTransactionRunnerImpl(db);
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
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
        if (node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        try {
            if (node[1] != null) {
                Uint64 dpId = Uint64.valueOf(node[1]);
                ListenableFutures.addErrorLogging(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION,
                            tx -> dhcpManager.setupDefaultDhcpFlows(tx, dpId)),
                            LOG, "Error handling node addition for {}", add);
                dhcpExternalTunnelManager.installDhcpDropActionOnDpn(dpId);
            } else {
                LOG.error("Unexpected nodeId {} found", nodeId.getValue());
            }
        } catch (NumberFormatException ex) {
            LOG.error("Unexpected nodeId {} found with Exception {} ", nodeId.getValue(),ex.getMessage());
        }
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.debug("Node Listener Closed");
    }

    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    protected NodeListener getDataTreeChangeListener() {
        return NodeListener.this;
    }
}
