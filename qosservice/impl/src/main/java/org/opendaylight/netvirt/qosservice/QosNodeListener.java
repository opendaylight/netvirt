/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.qosservice.recovery.QosServiceRecoveryHandler;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosNodeListener extends AbstractAsyncDataTreeChangeListener<FlowCapableNode>
        implements RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(QosNodeListener.class);

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalUtils;

    @Inject
    public QosNodeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalUtils,
                           final ServiceRecoveryRegistry serviceRecoveryRegistry,
                           final QosServiceRecoveryHandler qosServiceRecoveryHandler) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Nodes.class).child(Node.class)
                .augmentation(FlowCapableNode.class),
                Executors.newListeningSingleThreadExecutor("QosNodeListener", LOG));
        this.dataBroker = dataBroker;
        this.mdsalUtils = mdsalUtils;
        serviceRecoveryRegistry.addRecoverableListener(qosServiceRecoveryHandler.buildServiceRegistryKey(),
                this);
        LOG.trace("{} created",  getClass().getSimpleName());
    }

    public void init() {
        LOG.trace("{} init and registerListener done", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
    }

    @Override
    public void deregisterListener() {
    }

    @Override
    public void remove(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        //do nothing
    }

    @Override
    public void update(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModificationBefore,
                          FlowCapableNode dataObjectModificationAfter) {
        //do nothing
    }

    @Override
    public void add(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        NodeKey nodeKey = key.firstKeyOf(Node.class);
        Uint64 dpId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        createTableMissEntry(dpId);
    }

    public void createTableMissEntry(Uint64 dpnId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.QOS_DSCP_TABLE, "QoSTableMissFlow",
                0, "QoS Table Miss Flow", 0, 0,
                NwConstants.COOKIE_QOS_TABLE, matches, instructions);
        mdsalUtils.installFlow(flowEntity);
    }
}


