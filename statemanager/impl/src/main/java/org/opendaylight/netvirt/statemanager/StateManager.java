/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.statemanager;

import com.google.common.util.concurrent.CheckedFuture;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StateManager implements IStateManager {

    private static final Logger LOG = LoggerFactory.getLogger(StateManager.class);
    private final DataBroker dataBroker;

    @Inject
    public StateManager(final DataBroker databroker) {
        this.dataBroker = databroker;
    }

    /**
     * Start method called by blueprint.
     */
    @PostConstruct
    public void start() {
        setReady(true);
    }

    /**
     * Executes put as a blocking transaction.
     *
     * @param logicalDatastoreType {@link LogicalDatastoreType} which should be modified
     * @param path {@link InstanceIdentifier} for path to read
     * @param <D> the data object type
     * @return the result of the request
     */
    public <D extends org.opendaylight.yangtools.yang.binding.DataObject> boolean put(
            final LogicalDatastoreType logicalDatastoreType, final InstanceIdentifier<D> path, D data)  {
        boolean result = false;
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(logicalDatastoreType, path, data, WriteTransaction.CREATE_MISSING_PARENTS);
        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();
        try {
            future.checkedGet();
            result = true;
        } catch (TransactionCommitFailedException e) {
            LOG.warn("StateManager failed to put {} ", path, e);
        }
        return result;
    }

    private void initializeNetvirtTopology() {
        final TopologyId topologyId = new TopologyId("netvirt:1");
        InstanceIdentifier<Topology> path =
                InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(topologyId));
        TopologyBuilder tpb = new TopologyBuilder();
        tpb.setTopologyId(topologyId);
        if (!put(LogicalDatastoreType.OPERATIONAL, path, tpb.build())) {
            LOG.error("StateManager error initializing netvirt topology");
        }
    }

    private class WriteTopology implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOG.warn("StateManager thread was interrupted", e);
            }
            LOG.info("StateManager all is ready");
            initializeNetvirtTopology();
        }
    }

    @Override
    public void setReady(boolean ready) {
        if (ready) {
            new Thread(new WriteTopology()).start();
        }
    }
}
