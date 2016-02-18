/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.mdsalutil.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class MDSALManager implements AutoCloseable {

    private static final Logger s_logger = LoggerFactory.getLogger(MDSALManager.class);

    private DataBroker m_dataBroker;

    private PacketProcessingService m_packetProcessingService;
    private ListenerRegistration<DataChangeListener> groupListenerRegistration;
    private ListenerRegistration<DataChangeListener> flowListenerRegistration;
    private ConcurrentMap<FlowInfoKey, Runnable> flowMap = new ConcurrentHashMap<FlowInfoKey, Runnable>();
    private ConcurrentMap<GroupInfoKey, Runnable> groupMap = new ConcurrentHashMap<GroupInfoKey, Runnable> ();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Writes the flows and Groups to the MD SAL DataStore
     * which will be sent to the openflowplugin for installing flows/groups on the switch.
     * Other modules of VPN service that wants to install flows / groups on the switch
     * uses this utility
     *
     * @param db - dataBroker reference
     * @param pktProcService- PacketProcessingService for sending the packet outs
     */
    public MDSALManager(final DataBroker db, PacketProcessingService pktProcService) {
        m_dataBroker = db;
        m_packetProcessingService = pktProcService;
        registerListener(db);
        s_logger.info( "MDSAL Manager Initialized ") ;
    }

    @Override
    public void close() throws Exception {
        groupListenerRegistration.close();
        flowListenerRegistration.close();
        s_logger.info("MDSAL Manager Closed");
    }

    private void registerListener(DataBroker db) {
        try {
            flowListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, getWildCardFlowPath(),
                                                                        new FlowListener(),
                                                                        DataChangeScope.SUBTREE);
            groupListenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, getWildCardGroupPath(),
                                                                        new GroupListener(),
                                                                        DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            s_logger.error("GroupEventHandler: DataChange listener registration fail!", e);
            throw new IllegalStateException("GroupEventHandler: registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Group> getWildCardGroupPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class).child(Group.class);
    }

    private InstanceIdentifier<Flow> getWildCardFlowPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class).child(Table.class).child(Flow.class);
    }

    public void installFlow(FlowEntity flowEntity) {

        try {
            s_logger.trace("InstallFlow for flowEntity {} ", flowEntity);

            if (flowEntity.getCookie() == null) {
               flowEntity.setCookie(new BigInteger("0110000", 16));
            }

            FlowKey flowKey = new FlowKey( new FlowId(flowEntity.getFlowId()) );

            FlowBuilder flowbld = flowEntity.getFlowBuilder();

            Node nodeDpn = buildDpnNode(flowEntity.getDpnId());
            InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class,flowKey).build();

            WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();

            modification.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flowbld.build(),true );

            CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

            Futures.addCallback(submitFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(final Void result) {
                    // Commited successfully
                    s_logger.debug( "Install Flow -- Committedsuccessfully ") ;
                }

                @Override
                public void onFailure(final Throwable t) {
                    // Transaction failed

                    if(t instanceof OptimisticLockFailedException) {
                        // Failed because of concurrent transaction modifying same data
                        s_logger.error( "Install Flow -- Failed because of concurrent transaction modifying same data ") ;
                    } else {
                       // Some other type of TransactionCommitFailedException
                        s_logger.error( "Install Flow -- Some other type of TransactionCommitFailedException " + t) ;
                    }
                }
            });
        } catch (Exception e) {
            s_logger.error("Could not install flow: {}", flowEntity, e);
        }
    }

    public CheckedFuture<Void,TransactionCommitFailedException> installFlow(BigInteger dpId, Flow flow) {
        FlowKey flowKey = new FlowKey( new FlowId(flow.getId()) );
        Node nodeDpn = buildDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class,flowKey).build();
        WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();
        modification.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flow, true);
        return modification.submit();
    }

    public void installGroup(GroupEntity groupEntity) {
        try {
            Group group = groupEntity.getGroupBuilder().build();

            Node nodeDpn = buildDpnNode(groupEntity.getDpnId());

            InstanceIdentifier<Group> groupInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Group.class, new GroupKey(new GroupId(groupEntity.getGroupId()))).build();

            WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();

            modification.put(LogicalDatastoreType.CONFIGURATION, groupInstanceId, group, true);

            CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

            Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    // Commited successfully
                    s_logger.debug( "Install Group -- Committedsuccessfully ") ;
                }

                @Override
                public void onFailure(final Throwable t) {
                    // Transaction failed

                    if(t instanceof OptimisticLockFailedException) {
                        // Failed because of concurrent transaction modifying same data
                        s_logger.error( "Install Group -- Failed because of concurrent transaction modifying same data ") ;
                    } else {
                       // Some other type of TransactionCommitFailedException
                        s_logger.error( "Install Group -- Some other type of TransactionCommitFailedException " + t) ;
                    }
                }
             });
           } catch (Exception e) {
            s_logger.error("Could not install Group: {}", groupEntity, e);
            throw e;
        }
    }

    public void removeFlow(FlowEntity flowEntity) {
        try {
            s_logger.debug("Remove flow {}",flowEntity);
            Node nodeDpn = buildDpnNode(flowEntity.getDpnId());
            FlowKey flowKey = new FlowKey(new FlowId(flowEntity.getFlowId()));
            InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class, flowKey).build();


                WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();
                modification.delete(LogicalDatastoreType.CONFIGURATION,flowInstanceId);

                CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

                Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        // Commited successfully
                        s_logger.debug( "Delete Flow -- Committedsuccessfully ") ;
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        // Transaction failed
                        if(t instanceof OptimisticLockFailedException) {
                            // Failed because of concurrent transaction modifying same data
                            s_logger.error( "Delete Flow -- Failed because of concurrent transaction modifying same data ") ;
                        } else {
                           // Some other type of TransactionCommitFailedException
                            s_logger.error( "Delete Flow -- Some other type of TransactionCommitFailedException " + t) ;
                        }
                    }

                });
        } catch (Exception e) {
            s_logger.error("Could not remove Flow: {}", flowEntity, e);
        }
    }

    public CheckedFuture<Void,TransactionCommitFailedException> removeFlowNew(BigInteger dpnId, Flow flowEntity) {
        s_logger.debug("Remove flow {}",flowEntity);
        Node nodeDpn = buildDpnNode(dpnId);
        FlowKey flowKey = new FlowKey(new FlowId(flowEntity.getId()));
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class, flowKey).build();
        WriteTransaction  modification = m_dataBroker.newWriteOnlyTransaction();
        modification.delete(LogicalDatastoreType.CONFIGURATION,flowInstanceId );
        return modification.submit();
    }

    public void removeGroup(GroupEntity groupEntity) {
        try {
            Node nodeDpn = buildDpnNode(groupEntity.getDpnId());
            InstanceIdentifier<Group> groupInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Group.class, new GroupKey(new GroupId(groupEntity.getGroupId()))).build();

            WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();

            modification.delete(LogicalDatastoreType.CONFIGURATION,groupInstanceId );

            CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

            Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    // Commited successfully
                    s_logger.debug( "Install Group -- Committedsuccessfully ") ;
                }

                @Override
                public void onFailure(final Throwable t) {
                    // Transaction failed
                    if(t instanceof OptimisticLockFailedException) {
                        // Failed because of concurrent transaction modifying same data
                        s_logger.error( "Install Group -- Failed because of concurrent transaction modifying same data ") ;
                    } else {
                       // Some other type of TransactionCommitFailedException
                        s_logger.error( "Install Group -- Some other type of TransactionCommitFailedException " + t) ;
                    }
                }
            });
        } catch (Exception e) {
            s_logger.error("Could not remove Group: {}", groupEntity, e);
        }
    }

    public void modifyGroup(GroupEntity groupEntity) {

        installGroup(groupEntity);
    }

    public void sendPacketOut(BigInteger dpnId, int groupId, byte[] payload) {

        List<ActionInfo> actionInfos = new ArrayList<ActionInfo>();
        actionInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));

        sendPacketOutWithActions(dpnId, groupId, payload, actionInfos);
    }

    public void sendPacketOutWithActions(BigInteger dpnId, long groupId, byte[] payload, List<ActionInfo> actionInfos) {

        m_packetProcessingService.transmitPacket(MDSALUtil.getPacketOut(actionInfos, payload, dpnId,
                getNodeConnRef("openflow:" + dpnId, "0xfffffffd")));
    }

    public void sendARPPacketOutWithActions(BigInteger dpnId, byte[] payload, List<ActionInfo> actions) {
        m_packetProcessingService.transmitPacket(MDSALUtil.getPacketOut(actions, payload, dpnId,
                getNodeConnRef("openflow:" + dpnId, "0xfffffffd")));
    }

    public InstanceIdentifier<Node> nodeToInstanceId(Node node) {
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, node.getKey()).toInstance();
    }

    private static NodeConnectorRef getNodeConnRef(final String nodeId, final String port) {
        StringBuilder _stringBuilder = new StringBuilder(nodeId);
        StringBuilder _append = _stringBuilder.append(":");
        StringBuilder sBuild = _append.append(port);
        String _string = sBuild.toString();
        NodeConnectorId _nodeConnectorId = new NodeConnectorId(_string);
        NodeConnectorKey _nodeConnectorKey = new NodeConnectorKey(_nodeConnectorId);
        NodeConnectorKey nConKey = _nodeConnectorKey;
        InstanceIdentifierBuilder<Nodes> _builder = InstanceIdentifier.<Nodes> builder(Nodes.class);
        NodeId _nodeId = new NodeId(nodeId);
        NodeKey _nodeKey = new NodeKey(_nodeId);
        InstanceIdentifierBuilder<Node> _child = _builder.<Node, NodeKey> child(Node.class, _nodeKey);
        InstanceIdentifierBuilder<NodeConnector> _child_1 = _child.<NodeConnector, NodeConnectorKey> child(
                NodeConnector.class, nConKey);
        InstanceIdentifier<NodeConnector> path = _child_1.toInstance();
        NodeConnectorRef _nodeConnectorRef = new NodeConnectorRef(path);
        return _nodeConnectorRef;
    }

    private Node buildDpnNode(BigInteger dpnId) {
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();

        return nodeDpn;
    }

    public void syncSetUpFlow(FlowEntity flowEntity, long delay, boolean isRemove) {
        s_logger.trace("syncSetUpFlow for flowEntity {} ", flowEntity);
        if (flowEntity.getCookie() == null) {
            flowEntity.setCookie(new BigInteger("0110000", 16));
        }
        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        BigInteger dpId = flowEntity.getDpnId();
        short tableId = flowEntity.getTableId();
        Match matches = flow.getMatch();
        FlowKey flowKey = new FlowKey( new FlowId(flowId));
        Node nodeDpn = buildDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();
        Runnable notifyTask = new NotifyTask();
        FlowInfoKey flowInfoKey = new FlowInfoKey(dpId, tableId, matches, flowId);
        synchronized (flowInfoKey.toString().intern()) {
            flowMap.put(flowInfoKey, notifyTask);
            if (isRemove) {
                MDSALUtil.syncDelete(m_dataBroker, LogicalDatastoreType.CONFIGURATION, flowInstanceId);
            } else {
                MDSALUtil.syncWrite(m_dataBroker, LogicalDatastoreType.CONFIGURATION, flowInstanceId, flow);
            }
            synchronized (notifyTask) {
                try {
                    notifyTask.wait(delay);
                } catch (InterruptedException e){}
            }
        }
    }

    public void syncSetUpGroup(GroupEntity groupEntity, long delayTime, boolean isRemove) {
        s_logger.trace("syncSetUpGroup for groupEntity {} ", groupEntity);
        Group group = groupEntity.getGroupBuilder().build();
        BigInteger dpId = groupEntity.getDpnId();
        Node nodeDpn = buildDpnNode(dpId);
        long groupId = groupEntity.getGroupId();
        GroupKey groupKey = new GroupKey(new GroupId(groupId));
        InstanceIdentifier<Group> groupInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Group.class, groupKey).build();
        Runnable notifyTask = new NotifyTask();
        GroupInfoKey groupInfoKey = new GroupInfoKey(dpId, groupId);
        synchronized (groupInfoKey.toString().intern()) {
            s_logger.trace("syncsetupGroupKey groupKey {}", groupInfoKey);
            groupMap.put(groupInfoKey, notifyTask);
            if (isRemove) {
                MDSALUtil.syncDelete(m_dataBroker, LogicalDatastoreType.CONFIGURATION, groupInstanceId);
            } else {
                MDSALUtil.syncWrite(m_dataBroker, LogicalDatastoreType.CONFIGURATION, groupInstanceId, group);
            }
            synchronized (notifyTask) {
                try {
                    notifyTask.wait(delayTime);
                } catch (InterruptedException e){}
            }
        }
    }

    public void syncSetUpGroup(BigInteger dpId, Group group, long delayTime, boolean isRemove) {
        s_logger.trace("syncSetUpGroup for group {} ", group);
        Node nodeDpn = buildDpnNode(dpId);
        long groupId = group.getGroupId().getValue();
        GroupKey groupKey = new GroupKey(new GroupId(groupId));
        InstanceIdentifier<Group> groupInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Group.class, groupKey).build();
        Runnable notifyTask = new NotifyTask();
        GroupInfoKey groupInfoKey = new GroupInfoKey(dpId, groupId);
        synchronized (groupInfoKey.toString().intern()) {
            s_logger.trace("syncsetupGroupKey groupKey {}", groupInfoKey);
            groupMap.put(groupInfoKey, notifyTask);
            if (isRemove) {
                MDSALUtil.syncDelete(m_dataBroker, LogicalDatastoreType.CONFIGURATION, groupInstanceId);
            } else {
                MDSALUtil.syncWrite(m_dataBroker, LogicalDatastoreType.CONFIGURATION, groupInstanceId, group);
            }
            synchronized (notifyTask) {
                try {
                    notifyTask.wait(delayTime);
                } catch (InterruptedException e){}
            }
        }
    }

    class GroupListener extends AbstractDataChangeListener<Group> {

        public GroupListener() {
            super(Group.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Group> identifier, Group del) {
            BigInteger dpId = getDpnFromString(identifier.firstKeyOf(Node.class, NodeKey.class).getId().getValue());
            executeNotifyTaskIfRequired(dpId, del);
        }

        private void executeNotifyTaskIfRequired(BigInteger dpId, Group group) {
            GroupInfoKey groupKey = new GroupInfoKey(dpId, group.getGroupId().getValue());
            Runnable notifyTask = groupMap.remove(groupKey);
            if (notifyTask == null) {
                return;
            }
            executorService.execute(notifyTask);
        }

        @Override
        protected void update(InstanceIdentifier<Group> identifier, Group original, Group update) {
            BigInteger dpId = getDpnFromString(identifier.firstKeyOf(Node.class, NodeKey.class).getId().getValue());
            executeNotifyTaskIfRequired(dpId, update);
        }

        @Override
        protected void add(InstanceIdentifier<Group> identifier, Group add) {
            BigInteger dpId = getDpnFromString(identifier.firstKeyOf(Node.class, NodeKey.class).getId().getValue());
            executeNotifyTaskIfRequired(dpId, add);
        }
    }
    
    class FlowListener extends AbstractDataChangeListener<Flow> {

        public FlowListener() {
            super(Flow.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Flow> identifier, Flow del) {
            BigInteger dpId = getDpnFromString(identifier.firstKeyOf(Node.class, NodeKey.class).getId().getValue());
            notifyTaskIfRequired(dpId, del);
        }

        private void notifyTaskIfRequired(BigInteger dpId, Flow flow) {
            FlowInfoKey flowKey = new FlowInfoKey(dpId, flow.getTableId(), flow.getMatch(), flow.getId().getValue());
            Runnable notifyTask = flowMap.remove(flowKey);
            if (notifyTask == null) {
                return;
            }
            executorService.execute(notifyTask);
        }

        @Override
        protected void update(InstanceIdentifier<Flow> identifier, Flow original, Flow update) {
        }

        @Override
        protected void add(InstanceIdentifier<Flow> identifier, Flow add) {
            BigInteger dpId = getDpnFromString(identifier.firstKeyOf(Node.class, NodeKey.class).getId().getValue());
            notifyTaskIfRequired(dpId, add);
        }
    }
    
    private BigInteger getDpnFromString(String dpnString) {
        String[] split = dpnString.split(":");
        return new BigInteger(split[1]);
    }

}
