/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.pmcounters;

import java.lang.Thread.UncaughtExceptionHandler;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.FlowTableStatisticsUpdate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.GetFlowTablesStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.GetFlowTablesStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.OpendaylightFlowTableStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.OpendaylightFlowTableStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.flow.table.and.statistics.map.FlowTableAndStatisticsMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.GetAllNodeConnectorsStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.GetAllNodeConnectorsStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.NodeConnectorStatisticsUpdate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.OpendaylightPortStatisticsListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.OpendaylightPortStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class NodeConnectorStatsImpl extends AbstractDataChangeListener<Node>{

    private static final Logger logger = LoggerFactory.getLogger(NodeConnectorStatsImpl.class);
    private static final int THREAD_POOL_SIZE = 4;
    private static final int NO_DELAY = 0;
    public static final PMAgentForNodeConnectorCounters pmagent = new PMAgentForNodeConnectorCounters();
    private PortRpcStatisticsListener portStatsListener = new PortRpcStatisticsListener();
    private FlowRpcStatisticsListener flowTableStatsListener = new FlowRpcStatisticsListener();
    private List<BigInteger> nodes = new ArrayList<>();
    Map<String, Map<String, String>> nodeAndNcIdOFPortDurationMap = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndNcIdOFPortReceiveDropMap = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndNcIdOFPortReceiveError = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndNcIdPacketSentMap = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndNcIdPacketReceiveMap = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndNcIdBytesSentMap = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndNcIdBytesReceiveMap = new ConcurrentHashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> nodeAndEntriesPerOFTableMap = new ConcurrentHashMap<String, Map<String, String>>();
    private ScheduledFuture<?> scheduledResult;
    private OpendaylightPortStatisticsService statPortService;
    private ScheduledExecutorService portStatExecutorService;
    private OpendaylightFlowTableStatisticsService opendaylightFlowTableStatisticsService;
    public NodeConnectorStatsImpl(DataBroker db, NotificationService notificationService, OpendaylightPortStatisticsService statPortService, OpendaylightFlowTableStatisticsService opendaylightFlowTableStatisticsService) {
        super(Node.class);
        this.statPortService = statPortService;
        this.opendaylightFlowTableStatisticsService = opendaylightFlowTableStatisticsService;
        registerListener(db);
        portStatExecutorService = Executors.newScheduledThreadPool(THREAD_POOL_SIZE, getThreadFactory("Port Stats Request Task"));
        notificationService.registerNotificationListener(portStatsListener);
        notificationService.registerNotificationListener(flowTableStatsListener);
        pmagent.registerMbean();
    }

    private void registerListener(final DataBroker db) {
        try {
            db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), NodeConnectorStatsImpl.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("NodeConnectorStatsImpl: DataChange listener registration fail!", e);
            throw new IllegalStateException("NodeConnectorStatsImpl: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    /*
     * PortStat request task is started when first DPN gets connected
     */
    private void schedulePortStatRequestTask() {
        logger.info("Scheduling port statistics request");
        PortStatRequestTask portStatRequestTask = new PortStatRequestTask();
        scheduledResult = portStatExecutorService.scheduleAtFixedRate(portStatRequestTask, NO_DELAY, 10000, TimeUnit.MILLISECONDS);
    }

    /*
     * PortStat request task is stopped when last DPN is removed.
     */
    private void stopPortStatRequestTask() {
        if(scheduledResult != null) {
            logger.info("Stopping port statistics request");
            scheduledResult.cancel(true);
        }
    }

    /*
     * This task queries for node connector statistics as well as flowtables statistics every 10 secs.
     * Minimum period which can be configured for PMJob is 10 secs.
     */
    private class PortStatRequestTask implements Runnable {

        @Override
        public void run() {
            if(logger.isTraceEnabled()) {
                logger.trace("Requesting port stats - {}");
            }
            for (BigInteger node : nodes) {
                logger.trace("Requesting AllNodeConnectorStatistics for node - {}", node);
                statPortService.getAllNodeConnectorsStatistics(buildGetAllNodeConnectorStatistics(node));
                opendaylightFlowTableStatisticsService.getFlowTablesStatistics(buildGetFlowTablesStatistics(node));
            }
        }

        private GetAllNodeConnectorsStatisticsInput buildGetAllNodeConnectorStatistics(BigInteger dpId) {
            return new GetAllNodeConnectorsStatisticsInputBuilder()
                    .setNode(
                            new NodeRef(InstanceIdentifier.builder(Nodes.class)
                                    .child(Node.class, new NodeKey(new NodeId("openflow:" + dpId.toString()))).build())).build();
        }

        private GetFlowTablesStatisticsInput buildGetFlowTablesStatistics(BigInteger dpId) {
            return new GetFlowTablesStatisticsInputBuilder()
                    .setNode(
                            new NodeRef(InstanceIdentifier.builder(Nodes.class)
                                    .child(Node.class, new NodeKey(new NodeId("openflow:" + dpId.toString()))).build())).build();
        }

    }

    private ThreadFactory getThreadFactory(String threadNameFormat) {
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat(threadNameFormat);
        builder.setUncaughtExceptionHandler( new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.error("Received Uncaught Exception event in Thread: {}", t.getName(), e);
            }
        });
        return builder.build();
    }

    /*
     * PortRpcStatisticsListener listens for the NodeConnectorStatisticsUpdate and then update the corresponding counter map
     */
    class PortRpcStatisticsListener implements OpendaylightPortStatisticsListener {

        @Override
        public void onNodeConnectorStatisticsUpdate(NodeConnectorStatisticsUpdate ncStats) {
            Map<String, String> ncIdOFPortDurationMap = new HashMap<String, String>();
            Map<String, String> ncIdOFPortReceiveDropMap = new HashMap<String, String>();
            Map<String, String> ncIdOFPortReceiveError = new HashMap<String, String>();
            Map<String, String> ncIdPacketSentMap = new HashMap<String, String>();
            Map<String, String> ncIdPacketReceiveMap = new HashMap<String, String>();
            Map<String, String> ncIdBytesSentMap = new HashMap<String, String>();
            Map<String, String> ncIdBytesReceiveMap = new HashMap<String, String>();
            List<NodeConnectorStatisticsAndPortNumberMap> ncStatsAndPortMapList = ncStats.getNodeConnectorStatisticsAndPortNumberMap();
            NodeId nodeId = ncStats.getId();
            String node = nodeId.getValue().split(":")[1];
            for (NodeConnectorStatisticsAndPortNumberMap ncStatsAndPortMap : ncStatsAndPortMapList) {
                NodeConnectorId nodeConnector = ncStatsAndPortMap.getNodeConnectorId();
                String port = nodeConnector.getValue().split(":")[2];
                String nodePortStr = "dpnId_" + node + "_portNum_" + port;
                ncIdOFPortDurationMap.put("OFPortDuration:" + nodePortStr + "_OFPortDuration", ncStatsAndPortMap.getDuration().getSecond().getValue().toString());
                ncIdOFPortReceiveDropMap.put("PacketsPerOFPortReceiveDrop:" + nodePortStr + "_PacketsPerOFPortReceiveDrop", ncStatsAndPortMap.getReceiveDrops().toString());
                ncIdOFPortReceiveError.put("PacketsPerOFPortReceiveError:" + nodePortStr + "_PacketsPerOFPortReceiveError", ncStatsAndPortMap.getReceiveErrors().toString());
                ncIdPacketSentMap.put("PacketsPerOFPortSent:" + nodePortStr + "_PacketsPerOFPortSent", ncStatsAndPortMap.getPackets().getTransmitted().toString());
                ncIdPacketReceiveMap.put("PacketsPerOFPortReceive:" + nodePortStr + "_PacketsPerOFPortReceive", ncStatsAndPortMap.getPackets().getReceived().toString());
                ncIdBytesSentMap.put("BytesPerOFPortSent:" + nodePortStr + "_BytesPerOFPortSent", ncStatsAndPortMap.getBytes().getTransmitted().toString());
                ncIdBytesReceiveMap.put("BytesPerOFPortReceive:" + nodePortStr + "_BytesPerOFPortReceive", ncStatsAndPortMap.getBytes().getReceived().toString());
            }
            logger.trace("Port Stats {}", ncStatsAndPortMapList);
            //Storing allNodeConnectorStats(like ncIdOFPortDurationMap) in a map with key as node for easy removal and addition of allNodeConnectorStats.
            nodeAndNcIdOFPortDurationMap.put(node, ncIdOFPortDurationMap);
            nodeAndNcIdOFPortReceiveDropMap.put(node, ncIdOFPortReceiveDropMap);
            nodeAndNcIdOFPortReceiveError.put(node, ncIdOFPortReceiveError);
            nodeAndNcIdPacketSentMap.put(node, ncIdPacketSentMap);
            nodeAndNcIdPacketReceiveMap.put(node, ncIdPacketReceiveMap);
            nodeAndNcIdBytesSentMap.put(node, ncIdBytesSentMap);
            nodeAndNcIdBytesReceiveMap.put(node, ncIdBytesReceiveMap);
            //Combining the stats of all nodeconnectors in all nodes. This Map will be stored under MBean which will be queried as regular intervals.
            ncIdOFPortDurationMap = combineAllNodesStats(nodeAndNcIdOFPortDurationMap);
            ncIdOFPortReceiveDropMap = combineAllNodesStats(nodeAndNcIdOFPortReceiveDropMap);
            ncIdOFPortReceiveError = combineAllNodesStats(nodeAndNcIdOFPortReceiveError);
            ncIdPacketSentMap = combineAllNodesStats(nodeAndNcIdPacketSentMap);
            ncIdPacketReceiveMap = combineAllNodesStats(nodeAndNcIdPacketReceiveMap);
            ncIdBytesSentMap = combineAllNodesStats(nodeAndNcIdBytesSentMap);
            ncIdBytesReceiveMap = combineAllNodesStats(nodeAndNcIdBytesReceiveMap);
            pmagent.connectToPMAgent(ncIdOFPortDurationMap, ncIdOFPortReceiveDropMap, ncIdOFPortReceiveError, ncIdPacketSentMap, ncIdPacketReceiveMap, ncIdBytesSentMap, ncIdBytesReceiveMap);
        }

        /*
         * Input allNodesStats contains statistics of all nodeConnectors of all nodes. Key is the node and values contains another map with key as node connector and value as statresult.
         * Output will be a map with key as nodeconnector and value as the statresult. The key contains nodeconnectors of all the nodes.
         */
    }

    private Map<String, String> combineAllNodesStats(Map<String, Map<String, String>> allNodesStats) {
        Map<String, String> allNcsStatsMap = new HashMap<String, String>();
        for (Map.Entry<String, Map<String, String>> entry : allNodesStats.entrySet()) {
            Map<String, String> ncStatsMap = entry.getValue();
            for (Map.Entry<String, String> statResult : ncStatsMap.entrySet()) {
                allNcsStatsMap.put(statResult.getKey(), statResult.getValue());
            }
        }
        return allNcsStatsMap;
    }

    /*
     * FlowRpcStatisticsListener listens for the FlowTableStatisticsUpdate and then update the corresponding counter map
     */
    class FlowRpcStatisticsListener implements OpendaylightFlowTableStatisticsListener {

        @Override
        public void onFlowTableStatisticsUpdate(FlowTableStatisticsUpdate flowTableStats) {
            String node = flowTableStats.getId().getValue().split(":")[1];
            Map<String, String> entriesPerOFTableMap = new HashMap<String, String>();
            List<FlowTableAndStatisticsMap> flowTableAndStatisticsMapList = flowTableStats.getFlowTableAndStatisticsMap();
            for (FlowTableAndStatisticsMap flowTableAndStatisticsMap : flowTableAndStatisticsMapList) {
                String nodeTableStr =  "dpnId_" + node + "_table_" + flowTableAndStatisticsMap.getTableId().getValue().toString();
                entriesPerOFTableMap.put("EntriesPerOFTable:" + nodeTableStr + "_EntriesPerOFTable", flowTableAndStatisticsMap.getActiveFlows().getValue().toString());
            }
            nodeAndEntriesPerOFTableMap.put(node, entriesPerOFTableMap);
            entriesPerOFTableMap = combineAllNodesStats(nodeAndEntriesPerOFTableMap);
            pmagent.connectToPMAgentAndInvokeEntriesPerOFTable(entriesPerOFTableMap);
        }

    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node node) {
        NodeId nodeId = node.getId();
        String nodeVal = nodeId.getValue().split(":")[1];
        BigInteger dpId = new BigInteger(nodeVal);
        if (nodes.contains(dpId)) {
            nodes.remove(dpId);
            nodeAndNcIdOFPortDurationMap.remove(nodeVal);
            nodeAndNcIdOFPortReceiveDropMap.remove(nodeVal);
            nodeAndNcIdOFPortReceiveError.remove(nodeVal);
            nodeAndNcIdPacketSentMap.remove(nodeVal);
            nodeAndNcIdPacketReceiveMap.remove(nodeVal);
            nodeAndNcIdBytesSentMap.remove(nodeVal);
            nodeAndNcIdBytesReceiveMap.remove(nodeVal);
            nodeAndEntriesPerOFTableMap.remove(nodeVal);
        }
        if (nodes.isEmpty()) {
            stopPortStatRequestTask();
        }
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original,
            Node update) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node node) {
        NodeId nodeId = node.getId();
        BigInteger dpId = new BigInteger(nodeId.getValue().split(":")[1]);
        if (nodes.contains(dpId)) {
            return;
        }
        nodes.add(dpId);
        if (nodes.size() == 1) {
            schedulePortStatRequestTask();
        }
    }
}
