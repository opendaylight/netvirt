/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CentralizedSwitchChangeListener adds/removes the switches to scheduler pool when a switch is
 * added/removed.
 */
@Singleton
public class SnatNodeEventListener  extends AsyncDataTreeChangeListenerBase<FlowCapableNode, SnatNodeEventListener>
    implements AutoCloseable {
    private final CentralizedSwitchScheduler  centralizedSwitchScheduler;
    private static final Logger LOG = LoggerFactory.getLogger(SnatNodeEventListener.class);
    private final DataBroker dataBroker;

    @Inject
    public SnatNodeEventListener(final DataBroker dataBroker,
            final CentralizedSwitchScheduler centralizedSwitchScheduler) {
        super(FlowCapableNode.class, SnatNodeEventListener.class);
        this.dataBroker = dataBroker;
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        NodeKey nodeKey = key.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        centralizedSwitchScheduler.removeSwitch(dpnId);
    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModificationBefore,
            FlowCapableNode dataObjectModificationAfter) {
        /*Do Nothing */
    }

    @Override
    protected void add(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        NodeKey nodeKey = key.firstKeyOf(Node.class);
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        centralizedSwitchScheduler.addSwitch(dpnId);

    }

    @Override
    protected org.opendaylight.netvirt.natservice.ha.SnatNodeEventListener getDataTreeChangeListener() {
        return SnatNodeEventListener.this;
    }

}
