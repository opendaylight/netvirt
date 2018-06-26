/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.statemanager;

import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
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
    private final ManagedNewTransactionRunner txRunner;

    // This class relies on its arguments being ready, even though it doesn't use them
    @SuppressWarnings("unused")
    @Inject
    public StateManager(final DataBroker dataBroker, final IBgpManager bgpManager, final IElanService elanService,
                        final IFibManager fibManager, final INeutronVpnManager neutronVpnManager,
                        final IVpnManager vpnManager) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    /**
     * Start method called by blueprint.
     */
    @PostConstruct
    public void start() {
        setReady(true);
    }

    private void initializeNetvirtTopology() {
        final TopologyId topologyId = new TopologyId("netvirt:1");
        InstanceIdentifier<Topology> path =
                InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(topologyId));
        TopologyBuilder tpb = new TopologyBuilder();
        tpb.setTopologyId(topologyId);
        try {
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                tx -> tx.put(path, tpb.build())).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("StateManager error initializing netvirt topology", e);
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
