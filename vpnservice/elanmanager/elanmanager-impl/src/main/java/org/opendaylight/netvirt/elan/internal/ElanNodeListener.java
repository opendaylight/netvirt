/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class ElanNodeListener extends AbstractDataChangeListener<Node> {

    private static final Logger logger = LoggerFactory.getLogger(ElanNodeListener.class);
    private static volatile ElanNodeListener elanNodeListener = null;
    private ElanServiceProvider elanServiceProvider = null;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    public static ElanNodeListener getElanNodeListener(ElanServiceProvider elanServiceProvider) {
        if (elanNodeListener == null)
            synchronized (ElanNodeListener.class) {
                if (elanNodeListener == null)
                {
                    ElanNodeListener elanNodeListener = new ElanNodeListener(elanServiceProvider);
                    return elanNodeListener;

                }
            }
        return elanNodeListener;
    }

    public ElanNodeListener(ElanServiceProvider elanServiceProvider) {
        super(Node.class);
        this.elanServiceProvider= elanServiceProvider;
        registerListener(this.elanServiceProvider.getBroker());
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), ElanNodeListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("ElanNodeListener: DataChange listener registration fail!", e);
            throw new IllegalStateException("ElanNodeListener: registration Listener failed.", e);
        }
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
            logger.warn("Unexpected nodeId {}", nodeId.getValue());
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
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        List <ActionInfo> actionsInfos = new ArrayList <ActionInfo> ();
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller, new String[] {}));
        mkInstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.ELAN_DMAC_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_SMAC_TABLE, getTableMissFlowRef(NwConstants.ELAN_SMAC_TABLE),
            0, "ELAN sMac Table Miss Flow", 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_SMAC,
            mkMatches, mkInstructions);
        this.elanServiceProvider.getMdsalManager().installFlow(flowEntity);
    }

    private void setupTableMissDmacFlow(BigInteger dpId) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();

        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.ELAN_UNKNOWN_DMAC_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.ELAN_DMAC_TABLE, getTableMissFlowRef(NwConstants.ELAN_DMAC_TABLE),
            0, "ELAN dMac Table Miss Flow", 0, 0, ElanConstants.COOKIE_ELAN_KNOWN_DMAC,
            mkMatches, mkInstructions);
        this.elanServiceProvider.getMdsalManager().installFlow(flowEntity);
    }

    private String getTableMissFlowRef(long tableId) {
        return new StringBuffer().append(tableId).toString();
    }


}
