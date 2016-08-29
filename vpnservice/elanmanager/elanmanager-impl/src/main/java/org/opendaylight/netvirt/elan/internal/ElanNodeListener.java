/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanNodeListener extends AbstractDataChangeListener<Node> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanNodeListener.class);

    private final DataBroker broker;
    private final IMdsalApiManager mdsalManager;

    private ListenerRegistration<DataChangeListener> listenerRegistration;

    public ElanNodeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        super(Node.class);
        this.broker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    public void init() {
        registerListener(broker);
    }

    private void registerListener(final DataBroker db) {
        listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
            getWildCardPath(), ElanNodeListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
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
        BigInteger dpId = new BigInteger(node[1]);
        createTableMissEntry(dpId);
    }

    public void createTableMissEntry(BigInteger dpnId) {
        setupTableMissSmacFlow(dpnId);
        setupTableMissDmacFlow(dpnId);
    }

    private void setupTableMissSmacFlow(BigInteger dpId) {
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[] {}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.ELAN_DMAC_TABLE }));

        List<MatchInfo> mkMatches = new ArrayList<>();
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_SMAC_TABLE, getTableMissFlowRef(NwConstants.ELAN_SMAC_TABLE),
            0, "ELAN sMac Table Miss Flow", 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_SMAC,
            mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);
    }

    private void setupTableMissDmacFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.ELAN_UNKNOWN_DMAC_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE, getTableMissFlowRef(NwConstants.ELAN_DMAC_TABLE),
            0, "ELAN dMac Table Miss Flow", 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC,
            mkMatches, mkInstructions);
        mdsalManager.installFlow(flowEntity);
    }

    private String getTableMissFlowRef(long tableId) {
        return new StringBuffer().append(tableId).toString();
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
    }
}
