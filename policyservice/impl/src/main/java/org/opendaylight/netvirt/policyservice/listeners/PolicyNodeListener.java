/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import java.math.BigInteger;
import java.util.Collections;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceFlowUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyNodeListener extends AsyncDataTreeChangeListenerBase<FlowCapableNode, PolicyNodeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyNodeListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceFlowUtil policyFlowUtil;

    @Inject
    public PolicyNodeListener(final DataBroker dataBroker, final PolicyServiceFlowUtil policyFlowUtil) {
        this.dataBroker = dataBroker;
        this.policyFlowUtil = policyFlowUtil;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected PolicyNodeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode flowCapableNode) {

    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode origFlowCapableNode,
            FlowCapableNode updatedFlowCapableNode) {

    }

    @Override
    protected void add(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode flowCapableNode) {
        BigInteger dpId = MDSALUtil.getDpnIdFromNodeName(key.firstKeyOf(Node.class).getId());
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        createTableMissFlow(dpId, NwConstants.EGRESS_POLICY_CLASSIFIER_TABLE,
                NwConstants.EGRESS_POLICY_CLASSIFIER_COOKIE, tx);
        createTableMissFlow(dpId, NwConstants.EGRESS_POLICY_ROUTING_TABLE, NwConstants.EGRESS_POLICY_ROUTING_COOKIE,
                tx);
        tx.submit();
    }

    private void createTableMissFlow(BigInteger dpId, short tableId, BigInteger cookie, WriteTransaction tx) {
        policyFlowUtil.updateFlowToTx(dpId, tableId, tableId + "_Miss", NwConstants.TABLE_MISS_PRIORITY, cookie,
                Collections.emptyList(), policyFlowUtil.getTableMissInstructions(), NwConstants.ADD_FLOW, tx);
    }

}
