/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Rationale: for vpn-servicechain and elan-servicechain to coexist in the same deployment, it is necessary a flow
// in LPortDispatcher that sets SI to ELAN in case that VPN does not apply
/**
 * Listens for Node Up events in order to install the L2 to L3 default
 * fallback flow. This flow, with minimum priority, consists on matching on
 * SI=2 and sets SI=3.
 *
 */
public class NodeListener extends AbstractDataChangeListener<Node> implements AutoCloseable {

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private final IMdsalApiManager mdsalMgr;

    private static final String L3_TO_L2_DEFAULT_FLOW_REF = "L3VPN_to_Elan_Fallback_Default_Rule";

    private static final Logger logger = LoggerFactory.getLogger(NodeListener.class);

    public NodeListener(final DataBroker db, final IMdsalApiManager mdsalManager) {
        super(Node.class);
        this.broker = db;
        this.mdsalMgr = mdsalManager;
    }

    public void init() {
        registerListener(broker);
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration =
                    db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                            InstanceIdentifier.create(Nodes.class).child(Node.class),
                            NodeListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("vpnmanager's NodeListener registration Listener failed.", e);
            throw new IllegalStateException("vpnmanager's NodeListener registration Listener failed.", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up NodeListener in VpnManager.", e);
            }
            listenerRegistration = null;
        }
        logger.debug("VpnManager's NodeListener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier, Node del) {
        BigInteger dpnId = getDpnIdFromNodeId(del.getId());
        if ( dpnId == null ) {
            return;
        }
        logger.debug("Removing L3VPN to ELAN default Fallback flow in LPortDispatcher table");
        Flow flowToRemove = new FlowBuilder().setFlowName(L3_TO_L2_DEFAULT_FLOW_REF)
                .setId(new FlowId(L3_TO_L2_DEFAULT_FLOW_REF))
                .setTableId(NwConstants.LPORT_DISPATCHER_TABLE).build();
        mdsalMgr.removeFlow(dpnId, flowToRemove);
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier, Node original, Node update) {
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {
        BigInteger dpnId = getDpnIdFromNodeId(add.getId());
        if ( dpnId == null ) {
            return;
        }

        logger.debug("Installing L3VPN to ELAN default Fallback flow in LPortDispatcher table");
        BigInteger[] metadataToMatch = new BigInteger[] {
                MetaDataUtil.getServiceIndexMetaData(NwConstants.L3VPN_SERVICE_INDEX),
                MetaDataUtil.METADATA_MASK_SERVICE_INDEX
        };
        List<MatchInfo> matches = Arrays.asList(new MatchInfo(MatchFieldType.metadata, metadataToMatch));

        BigInteger metadataToWrite = MetaDataUtil.getServiceIndexMetaData(NwConstants.ELAN_SERVICE_INDEX);
        int instructionKey = 0;
        List<Instruction> instructions =
                Arrays.asList(MDSALUtil.buildAndGetWriteMetadaInstruction(metadataToWrite,
                        MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                        ++instructionKey),
                        MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_INTERFACE_TABLE, ++instructionKey));

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, L3_TO_L2_DEFAULT_FLOW_REF,
                NwConstants.TABLE_MISS_PRIORITY, L3_TO_L2_DEFAULT_FLOW_REF,
                0, 0, CloudServiceChainConstants.COOKIE_L3_BASE,
                matches, instructions);
        mdsalMgr.installFlow(dpnId, flow);
    }


    private BigInteger getDpnIdFromNodeId(NodeId nodeId) {
        String[] node =  nodeId.getValue().split(":");
        if(node.length < 2) {
            logger.warn("Unexpected nodeId {}", nodeId.getValue());
            return null;
        }
        return new BigInteger(node[1]);
    }
}
