/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.utils.netvirt.it.utils;

import static org.junit.Assert.assertNotNull;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.netvirt.utils.mdsal.openflow.FlowUtils;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.mdsal.utils.NotifyingDataChangeListener;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains various utility methods used in netvirt integration tests (IT).
 */
public class NetvirtItUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NetvirtItUtils.class);
    MdsalUtils mdsalUtils;
    SouthboundUtils southboundUtils;
    DataBroker dataBroker;

    /**
     * Create a new NetvirtItUtils instance.
     * @param dataBroker  md-sal data broker
     */
    public NetvirtItUtils(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        mdsalUtils = new MdsalUtils(dataBroker);
        southboundUtils = new SouthboundUtils(mdsalUtils);
    }

    /**
     * Check that the netvirt topology is in the operational mdsal.
     * @return true if the netvirt topology was successfully retrieved
     */
    public Boolean getNetvirtTopology() {
        LOG.info("getNetvirtTopology: looking for {}...", ItConstants.NETVIRT_TOPOLOGY_ID);
        final TopologyId topologyId = new TopologyId(new Uri(ItConstants.NETVIRT_TOPOLOGY_ID));
        InstanceIdentifier<Topology> path =
                InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(topologyId));
        NotifyingDataChangeListener waitForIt = new NotifyingDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                path, null);
        waitForIt.registerDataChangeListener(dataBroker);
        try {
            waitForIt.waitForCreation(60 * 1000);
        } catch (InterruptedException e) {
            LOG.info("getNetvirtTopology: InterruptedException while wait(ing)ForCreation");
        }

        boolean found = null != mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, path);

        LOG.info("getNetvirtTopology: found {} == {}", ItConstants.NETVIRT_TOPOLOGY_ID, found);

        return found;
    }

    /**
     * Verify that the given flow was installed in a table. This method will wait 10 seconds for the flows
     * to appear in each of the md-sal CONFIGURATION and OPERATIONAL data stores
     * @param datapathId dpid where flow is installed
     * @param flowId The "name" of the flow, e.g., "TunnelFloodOut_100"
     * @param table integer value of table
     * @throws InterruptedException if interrupted while waiting for flow to appear in mdsal
     */
    public void verifyFlow(long datapathId, String flowId, short table) throws InterruptedException {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder nodeBuilder =
                FlowUtils.createNodeBuilder(datapathId);
        FlowBuilder flowBuilder =
                FlowUtils.initFlowBuilder(new FlowBuilder(), flowId, table);

        InstanceIdentifier<Flow> iid = FlowUtils.createFlowPath(flowBuilder, nodeBuilder);

        NotifyingDataChangeListener waitForIt = new NotifyingDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                iid, null);
        waitForIt.registerDataChangeListener(dataBroker);
        waitForIt.waitForCreation(10000);

        Flow flow = FlowUtils.getFlow(flowBuilder, nodeBuilder,
                        dataBroker.newReadOnlyTransaction(), LogicalDatastoreType.CONFIGURATION);
        assertNotNull("Could not find flow in config: " + flowBuilder.build() + "--" + nodeBuilder.build(), flow);

        waitForIt = new NotifyingDataChangeListener(LogicalDatastoreType.OPERATIONAL, iid, null);
        waitForIt.registerDataChangeListener(dataBroker);
        waitForIt.waitForCreation(10000);

        flow = FlowUtils.getFlow(flowBuilder, nodeBuilder,
                        dataBroker.newReadOnlyTransaction(), LogicalDatastoreType.OPERATIONAL);
        assertNotNull("Could not find flow in operational: " + flowBuilder.build() + "--" + nodeBuilder.build(),
                flow);
    }

    /**
     * Log the flows in a given table.
     * @param datapathId dpid
     * @param tableNum table number
     * @param store configuration or operational
     */
    public void logFlows(long datapathId, short tableNum, LogicalDatastoreType store) {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder nodeBuilder =
                FlowUtils.createNodeBuilder(datapathId);
        Table table = FlowUtils.getTable(nodeBuilder, tableNum, dataBroker.newReadOnlyTransaction(), store);
        if (table == null) {
            LOG.info("logFlows: Could not find table {} in {}", tableNum, store);
        }
        //TBD: Log table and store in one line, flows in following lines
        for (Flow flow : table.getFlow()) {
            LOG.info("logFlows: table {} flow {} in {}", tableNum, flow.getFlowName(), store);
        }
    }

    /**
     * Log the flows in a given table.
     * @param datapathId dpid
     * @param table table number
     */
    public void logFlows(long datapathId, short table) {
        logFlows(datapathId, table, LogicalDatastoreType.CONFIGURATION);
    }

    /**
     * Get a DataBroker and assert that it is not null.
     * @param providerContext ProviderContext from which to retrieve the DataBroker
     * @return the Databroker
     */
    public static DataBroker getDatabroker(BindingAwareBroker.ProviderContext providerContext) {
        DataBroker dataBroker = providerContext.getSALService(DataBroker.class);
        assertNotNull("dataBroker should not be null", dataBroker);
        return dataBroker;
    }
}
