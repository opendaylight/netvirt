/*
 * Copyright (c) 2017, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InventoryNodeListener extends AbstractSyncDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryNodeListener.class);
    private final IMdsalApiManager mdsalApiManager;

    @Inject
    public InventoryNodeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalApiManager) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(Nodes.class).child(Node.class));
        this.mdsalApiManager = mdsalApiManager;

    }

    @Override
    public void remove(@Nonnull InstanceIdentifier<Node> instanceIdentifier, @Nonnull Node node) {
        // Do nothing
    }

    @Override
    public void update(@Nonnull InstanceIdentifier<Node> instanceIdentifier, @Nonnull Node originalNode,
                       @Nonnull final Node updatedNode) {
        // Nothing to do
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<Node> instanceIdentifier, @Nonnull Node node) {
        NodeId nodeId = node.getId();
        String[] nodeIdVal =  nodeId.getValue().split(":");
        if (nodeIdVal.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(nodeIdVal[1]);
        setupTableMissForCoeKubeProxyTable(dpId);
    }

    private void setupTableMissForCoeKubeProxyTable(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.COE_KUBE_PROXY_TABLE,
                "COEKubeProxyTableMissFlow",0,
                "COEKubeProxy Table Miss Flow", 0, 0,
                NwConstants.COOKIE_COE_KUBE_PROXY_TABLE, matches, instructions);
        mdsalApiManager.installFlow(flowEntity);
    }
}
