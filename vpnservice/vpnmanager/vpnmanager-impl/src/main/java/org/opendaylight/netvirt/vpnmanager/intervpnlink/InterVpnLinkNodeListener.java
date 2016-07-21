/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Listens for Nodes going down, in order to check if the InterVpnLink must be
 * moved to some other DPN
 *
 */
public class InterVpnLinkNodeListener extends AbstractDataChangeListener<Node>
                                      implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(InterVpnLinkNodeListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private static final String NBR_OF_DPNS_PROPERTY_NAME = "vpnservice.intervpnlink.number.dpns";
    private final DataBroker broker;
    final IMdsalApiManager mdsalManager;

    public InterVpnLinkNodeListener(final DataBroker db, IMdsalApiManager mdsalMgr) {
        super(Node.class);
        broker = db;
        mdsalManager = mdsalMgr;
        registerListener(db);
    }

    @Override
    public void close() {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                                                                 getWildCardPath(),
                                                                 InterVpnLinkNodeListener.this,
                                                                 AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("InterVpnLinkNodeListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("InterVpnLinkNodeListener: registration Listener failed.", e);
        }
    }

    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {
        NodeId nodeId = add.getId();
        String[] node =  nodeId.getValue().split(":");
        if(node.length < 2) {
            logger.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        coordinator.enqueueJob("IVpnLink" + dpId.toString(), new InterVpnLinkNodeAddTask(broker, mdsalManager, dpId));
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
        logger.info("Node {} has been deleted", identifier.firstKeyOf(Node.class).toString());
        NodeId nodeId = del.getId();
        String[] node =  nodeId.getValue().split(":");
        if(node.length < 2) {
            logger.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
        coordinator.enqueueJob("IVpnLink" + dpId.toString(), new InterVpnLinkNodeWorker(broker, dpId));

    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
        logger.info("Node {} has changed", identifier.firstKeyOf(Node.class).toString());
    }

    protected class InterVpnLinkNodeWorker implements Callable<List<ListenableFuture<Void>>> {

        private DataBroker broker;
        private BigInteger dpnId;

        public InterVpnLinkNodeWorker(final DataBroker broker, final BigInteger dpnId) {
            this.broker = broker;
            this.dpnId = dpnId;
        }
        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> result = new ArrayList<ListenableFuture<Void>>();

            List<InterVpnLink> allInterVpnLinks = VpnUtil.getAllInterVpnLinks(broker);
            for ( InterVpnLink interVpnLink : allInterVpnLinks ) {
                InterVpnLinkState interVpnLinkState = VpnUtil.getInterVpnLinkState(broker, interVpnLink.getName());
                if ( interVpnLinkState == null ) {
                    logger.warn("Could not find State info for InterVpnLink={}", interVpnLink.getName());
                    continue;
                }

                if ( interVpnLinkState.getFirstEndpointState().getDpId().contains(dpnId)
                     || interVpnLinkState.getSecondEndpointState().getDpId().contains(dpnId) ) {
                    // InterVpnLink affected by Node DOWN.
                    // Lets move the InterVpnLink to some other place. Basically, remove it and create it again
                    InstanceIdentifier<InterVpnLink> interVpnLinkIid =
                        InstanceIdentifier.builder(InterVpnLinks.class)
                                          .child(InterVpnLink.class, new InterVpnLinkKey(interVpnLink.getName()))
                                          .build();
                    // Remove it
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinkIid);
                    // Create it again, but first we have to wait for everything to be removed from dataplane
                    // TODO: the wait causes an IllegalMonitorStateException
                    Long timeToWait = Long.getLong("wait.time.sync.install", 1500L);
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted while waiting for Flows removal sync.", e);
                    }

                    InterVpnLink interVpnLink2 = new InterVpnLinkBuilder(interVpnLink).build();
                    WriteTransaction tx = broker.newWriteOnlyTransaction();
                    tx.put(LogicalDatastoreType.CONFIGURATION, interVpnLinkIid, interVpnLink2, true);
                    CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
                    result.add(futures);
                }
            }

            return result;
        }

    }

}
