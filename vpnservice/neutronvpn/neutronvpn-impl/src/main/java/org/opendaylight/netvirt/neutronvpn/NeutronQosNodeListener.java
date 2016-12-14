/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


public class NeutronQosNodeListener extends AsyncDataTreeChangeListenerBase<Node, NeutronQosNodeListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronQosNodeListener.class);

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalUtils;

    public NeutronQosNodeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalUtils) {
        super(Node.class, NeutronQosNodeListener.class);
        this.dataBroker=dataBroker;
        this.mdsalUtils=mdsalUtils;
    }

    public void init() {
        LOG.trace("NeutronQoSNodeListener is initiated");
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> key, Node dataObjectModification) {
        //do nothing
    }

    @Override
    protected void update(InstanceIdentifier<Node> key, Node dataObjectModificationBefore, Node dataObjectModificationAfter) {
        //do nothing
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier, Node add) {

        NodeId nodeId = add.getId();
        String[] node =  nodeId.getValue().split(":");
        if(node.length < 2) {
            LOG.warn("Unexpected nodeId {}", nodeId.getValue());
            return;
        }
        BigInteger dpId = new BigInteger(node[1]);
        createTableMissEntry(dpId);
    }

    @Override
    public void close() throws Exception {
        super.close();
        LOG.debug("NeutronQosNodeListener Closed");
    }

    @Override
    protected NeutronQosNodeListener getDataTreeChangeListener() {
        return NeutronQosNodeListener.this;
    }

    public void createTableMissEntry(BigInteger dpnId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[]{
                Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE, "QoSTableMissFlow",
                0, "QoS Table Miss Flow", 0, 0,
                NwConstants.COOKIE_QOS_TABLE, matches, instructions);
        mdsalUtils.installFlow(flowEntity);
    }
}

