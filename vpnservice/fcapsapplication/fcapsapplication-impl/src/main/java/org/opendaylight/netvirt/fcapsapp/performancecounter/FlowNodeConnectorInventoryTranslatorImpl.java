/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp.performancecounter;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipState;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.fcapsapp.portinfo.PortNameMapping;
import org.opendaylight.openflowplugin.common.wait.SimpleTaskRetryLooper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.String;
import java.util.HashMap;
import java.util.Collection;
import java.util.concurrent.Callable;

public class FlowNodeConnectorInventoryTranslatorImpl extends NodeConnectorEventListener<FlowCapableNodeConnector>  {
    public static final int STARTUP_LOOP_TICK = 500;
    public static final int STARTUP_LOOP_MAX_RETRIES = 8;
    private static final Logger LOG = LoggerFactory.getLogger(FlowNodeConnectorInventoryTranslatorImpl.class);
    private final EntityOwnershipService entityOwnershipService;

    private ListenerRegistration<FlowNodeConnectorInventoryTranslatorImpl> dataTreeChangeListenerRegistration;

    public static final String SEPARATOR = ":";
    private static final PMAgent pmAgent = new PMAgent();

    private static final InstanceIdentifier<FlowCapableNodeConnector> II_TO_FLOW_CAPABLE_NODE_CONNECTOR
            = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class)
            .child(NodeConnector.class)
            .augmentation(FlowCapableNodeConnector.class)
            .build();

    private static Multimap<Long,String> dpnToPortMultiMap = Multimaps.synchronizedListMultimap(ArrayListMultimap.<Long,String>create());

    private static HashMap<String, String> nodeConnectorCountermap = new HashMap<String, String>();

    public FlowNodeConnectorInventoryTranslatorImpl(final DataBroker dataBroker,final EntityOwnershipService eos) {
        super( FlowCapableNodeConnector.class);
        Preconditions.checkNotNull(dataBroker, "DataBroker can not be null!");

        entityOwnershipService = eos;
        final DataTreeIdentifier<FlowCapableNodeConnector> treeId =
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, getWildCardPath());
        try {
            SimpleTaskRetryLooper looper = new SimpleTaskRetryLooper(STARTUP_LOOP_TICK,
                    STARTUP_LOOP_MAX_RETRIES);
            dataTreeChangeListenerRegistration = looper.loopUntilNoException(new Callable<ListenerRegistration<FlowNodeConnectorInventoryTranslatorImpl>>() {
                @Override
                public ListenerRegistration<FlowNodeConnectorInventoryTranslatorImpl> call() throws Exception {
                    return dataBroker.registerDataTreeChangeListener(treeId, FlowNodeConnectorInventoryTranslatorImpl.this);
                }
            });
        } catch (final Exception e) {
            LOG.warn(" FlowNodeConnectorInventoryTranslatorImpl listener registration fail!");
            LOG.debug("FlowNodeConnectorInventoryTranslatorImpl DataChange listener registration fail ..", e);
            throw new IllegalStateException("FlowNodeConnectorInventoryTranslatorImpl startup fail! System needs restart.", e);
        }
    }


    protected InstanceIdentifier<FlowCapableNodeConnector> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class)
                .child(Node.class)
                .child(NodeConnector.class)
                .augmentation(FlowCapableNodeConnector.class);
    }

    @Override
    public void close() {
        if (dataTreeChangeListenerRegistration != null) {
            try {
                dataTreeChangeListenerRegistration.close();
            } catch (final Exception e) {
                LOG.warn("Error by stop FRM FlowNodeConnectorInventoryTranslatorImpl: {}", e.getMessage());
                LOG.debug("Error by stop FRM FlowNodeConnectorInventoryTranslatorImpl..", e);
            }
            dataTreeChangeListenerRegistration = null;
        }
    }
    @Override
    public void remove(InstanceIdentifier<FlowCapableNodeConnector> identifier, FlowCapableNodeConnector del, InstanceIdentifier<FlowCapableNodeConnector> nodeConnIdent) {
        if(compareInstanceIdentifierTail(identifier,II_TO_FLOW_CAPABLE_NODE_CONNECTOR)) {
            String sNodeConnectorIdentifier = getNodeConnectorId(String.valueOf(nodeConnIdent.firstKeyOf(NodeConnector.class).getId()));
            long nDpId = getDpIdFromPortName(sNodeConnectorIdentifier);
            if (dpnToPortMultiMap.containsKey(nDpId)) {
                LOG.debug("Node Connector {} removed", sNodeConnectorIdentifier);
                dpnToPortMultiMap.remove(nDpId, sNodeConnectorIdentifier);
                sendNodeConnectorUpdation(nDpId);
                PortNameMapping.updatePortMap("openflow:" + nDpId + ":" + del.getName(), sNodeConnectorIdentifier, "DELETE");
            }
        }
    }

    @Override
    public void update(InstanceIdentifier<FlowCapableNodeConnector> identifier, FlowCapableNodeConnector original, FlowCapableNodeConnector update, InstanceIdentifier<FlowCapableNodeConnector> nodeConnIdent) {
        if(compareInstanceIdentifierTail(identifier,II_TO_FLOW_CAPABLE_NODE_CONNECTOR)) {

            //donot need to do anything as we are not considering updates here
            String sNodeConnectorIdentifier = getNodeConnectorId(String.valueOf(nodeConnIdent.firstKeyOf(NodeConnector.class).getId()));
            long nDpId = getDpIdFromPortName(sNodeConnectorIdentifier);
            if (isNodeOwner(getNodeId(nDpId))) {
                boolean original_portstatus = original.getConfiguration().isPORTDOWN();
                boolean update_portstatus = update.getConfiguration().isPORTDOWN();

                if (update_portstatus == true) {
                    //port has gone down
                    LOG.debug("Node Connector {} updated port is down", sNodeConnectorIdentifier);
                } else if (original_portstatus == true) {
                    //port has come up
                    LOG.debug("Node Connector {} updated port is up", sNodeConnectorIdentifier);
                }
            }
        }
    }

    @Override
    public void add(InstanceIdentifier<FlowCapableNodeConnector> identifier, FlowCapableNodeConnector add, InstanceIdentifier<FlowCapableNodeConnector> nodeConnIdent) {
        if (compareInstanceIdentifierTail(identifier,II_TO_FLOW_CAPABLE_NODE_CONNECTOR)){

            String sNodeConnectorIdentifier = getNodeConnectorId(String.valueOf(nodeConnIdent.firstKeyOf(NodeConnector.class).getId()));
            long nDpId = getDpIdFromPortName(sNodeConnectorIdentifier);
            if (isNodeOwner(getNodeId(nDpId))) {
                if (!dpnToPortMultiMap.containsEntry(nDpId, sNodeConnectorIdentifier)) {
                    LOG.debug("Node Connector {} added", sNodeConnectorIdentifier);
                    dpnToPortMultiMap.put(nDpId, sNodeConnectorIdentifier);
                    sendNodeConnectorUpdation(nDpId);
                    PortNameMapping.updatePortMap("openflow:" + nDpId + ":" + add.getName(), sNodeConnectorIdentifier, "ADD");
                } else {
                    LOG.error("Duplicate Event.Node Connector already added");
                }
            }
        }
    }
    private String getNodeConnectorId(String node) {
        //Uri [_value=openflow:1:1]
        String temp[] = node.split("=");
        String dpnId = temp[1].substring(0,temp[1].length() - 1);
        return dpnId;
    }

    private String getNodeId(Long dpnId){
        return "openflow:" + dpnId;
    }
    /**
     * Method checks if *this* instance of controller is owner of
     * the given openflow node.
     * @param nodeId openflow node Id
     * @return True if owner, else false
     */
    public boolean isNodeOwner(String nodeId) {
        Entity entity = new Entity("openflow", nodeId);
        Optional<EntityOwnershipState> eState = this.entityOwnershipService.getOwnershipState(entity);
        if(eState.isPresent()) {
            return eState.get().isOwner();
        }
        return false;
    }

    private boolean compareInstanceIdentifierTail(InstanceIdentifier<?> identifier1,
                                                  InstanceIdentifier<?> identifier2) {
        return Iterables.getLast(identifier1.getPathArguments()).equals(Iterables.getLast(identifier2.getPathArguments()));
    }

    private long getDpIdFromPortName(String portName) {
        String dpId = portName.substring(portName.indexOf(SEPARATOR) + 1, portName.lastIndexOf(SEPARATOR));
        return Long.parseLong(dpId);
    }

    private void sendNodeConnectorUpdation(Long dpnId) {
        Collection<String> portname = dpnToPortMultiMap.get(dpnId);
        String nodeListPortsCountStr,counterkey;
        nodeListPortsCountStr = "dpnId_" + dpnId + "_NumberOfOFPorts";
        counterkey = "NumberOfOFPorts:" + nodeListPortsCountStr;

        if (portname.size()!=0) {
            nodeConnectorCountermap.put(counterkey, "" + portname.size());
        } else {
            nodeConnectorCountermap.remove(counterkey);
        }
        LOG.debug("NumberOfOFPorts:" + nodeListPortsCountStr + " portlistsize " + portname.size());
        pmAgent.connectToPMAgentForNOOfPorts(nodeConnectorCountermap);
    }
}