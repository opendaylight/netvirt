/*
 * Copyright (c) 2013 Ericsson AB.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */

package org.opendaylight.vpnservice.mdsalutil.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableBuilder;
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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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

    private final AtomicInteger m_atomicInteger = new AtomicInteger();

    //TODO : IF ID MANAGER IS RQD

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

    }


    @Override
    public void close() throws Exception {
        s_logger.info("MDSAL Manager Closed");
    }


    public void printTest() {

        s_logger.info(" INTER MODULECOMMUNICATION IS WORKING!!!!");
    }

    public void installFlow(FlowEntity flowEntity) {

        try {
            s_logger.info("within installFlow {}", flowEntity.getDpnId());

            if (flowEntity.getCookie() == null) {
              //  s_logger.info("Helium_sync: Cookie is null");
                flowEntity.setCookie(new BigInteger("0110000", 16));
            }

            FlowKey flowKey = new FlowKey( new FlowId(flowEntity.getFlowId()) );

            FlowBuilder flowbld = flowEntity.getFlowBuilder();

            Flow flow = flowbld.build() ;

            Node nodeDpn = buildDpnNode(flowEntity.getDpnId());
            InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).build();

            InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class,flowKey).build();

            String sTransactionUri = generateTransactionUri();

            TableKey tableKey = new TableKey(flowEntity.getTableId() );
            InstanceIdentifier<Table> tableInstanceId = InstanceIdentifier.create(Nodes.class).child(Node.class, nodeDpn.getKey())
                    .augmentation(FlowCapableNode.class).child(Table.class, tableKey);
            Table table = new TableBuilder().setKey(tableKey).setFlow(Collections.<Flow>emptyList()).build();

            WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();

            //CHECK IF RQD
           // modification.put(LogicalDatastoreType.CONFIGURATION, nodeInstanceId, nodeDpn, true);


            modification.put(LogicalDatastoreType.CONFIGURATION, tableInstanceId, table);

            modification.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flowbld.build());

            CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

            Futures.addCallback(submitFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(final Void result) {
                    // Commited successfully
                    s_logger.info( "Install Flow -- Committedsuccessfully ") ;
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
            s_logger.error("Could not install flow: {}, exception: {}", flowEntity, e.getMessage());
        }
    }

    public void installGroup(GroupEntity groupEntity) {
        try {
            Group group = groupEntity.getGroupBuilder().build();

            Node nodeDpn = buildDpnNode(groupEntity.getDpnId());
            InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).build();
            InstanceIdentifier<Group> groupInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Group.class, new GroupKey(new GroupId(groupEntity.getGroupId()))).build();

            WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();

            //CHECK IF RQD
         //   modification.put(LogicalDatastoreType.CONFIGURATION, nodeInstanceId, nodeDpn);
            modification.put(LogicalDatastoreType.CONFIGURATION, groupInstanceId, group);

            CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

            Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    // Commited successfully
                    s_logger.info( "Install Group -- Committedsuccessfully ") ;
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
            s_logger.error("Could not install Group: {}, exception: {}", groupEntity, e.getMessage());
            throw e;
        }
    }

    public void removeFlow(FlowEntity flowEntity) {
        try {
            Node nodeDpn = buildDpnNode(flowEntity.getDpnId());
            FlowKey flowKey = new FlowKey(new FlowId(flowEntity.getFlowId()));
            InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                    .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class, flowKey).build();


                WriteTransaction modification = m_dataBroker.newWriteOnlyTransaction();
                modification.delete(LogicalDatastoreType.CONFIGURATION,flowInstanceId );

                CheckedFuture<Void,TransactionCommitFailedException> submitFuture  = modification.submit();

                Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(final Void result) {
                        // Commited successfully
                        s_logger.info( "Delete Flow -- Committedsuccessfully ") ;
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
            s_logger.error("Could not remove Flow: {}, exception: {}", flowEntity, e.getMessage());
        }
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
                    s_logger.info( "Install Group -- Committedsuccessfully ") ;
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
            s_logger.error("Could not remove Group: {}, exception: {}", groupEntity, e.getMessage());
        }
    }

    public void modifyGroup(GroupEntity groupEntity) {

        installGroup(groupEntity);
    }

    private String generateTransactionUri() {
        long lTransactionIdOut = m_atomicInteger.incrementAndGet();

        // TO DO Introduce this later
      //  return "" + (lTransactionIdOut | m_lTransactionIdPrefix);
        return "" + (lTransactionIdOut );
    }
/*
    private String generateTransactionUriForFlow(long nTransactionId) {
        long lTransactionIdOut = m_atomicInteger.incrementAndGet();
        return Long.toString((lTransactionIdOut | m_lTransactionIdPrefix)) + EUtil.TRANSACTION_ID_SEPARATOR
                + Long.toString(nTransactionId);
    }
*/

    public void sendPacketOut(long lDpnId, int groupId, byte[] payload) {

        List<ActionInfo> actionInfos = new ArrayList<ActionInfo>();
        actionInfos.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));

        sendPacketOutWithActions(lDpnId, groupId, payload, actionInfos);
    }

    public void sendPacketOutWithActions(long lDpnId, long groupId, byte[] payload, List<ActionInfo> actionInfos) {

        m_packetProcessingService.transmitPacket(MDSALUtil.getPacketOut(actionInfos, payload, lDpnId,
                getNodeConnRef("openflow:" + lDpnId, "0xfffffffd")));
    }

    public void sendARPPacketOutWithActions(long lDpnId, byte[] payload, List<ActionInfo> actions) {
        m_packetProcessingService.transmitPacket(MDSALUtil.getPacketOut(actions, payload, lDpnId,
                getNodeConnRef("openflow:" + lDpnId, "0xfffffffd")));
    }

    private NodeKey getNodeKey(long dpId) {
        String nodeId = "openflow:" + dpId;
        NodeKey nodeKey = new NodeKey(new NodeId(nodeId));
        return nodeKey;
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

    private long getDpnIdFromNodeName(String nodeName) {
        String dpId = nodeName.substring(nodeName.lastIndexOf(":") + 1);
        return Long.parseLong(dpId);
    }

    private Node buildDpnNode(long lDpnId) {
        NodeId nodeId = new NodeId("openflow:" + lDpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();

        return nodeDpn;
    }

}
