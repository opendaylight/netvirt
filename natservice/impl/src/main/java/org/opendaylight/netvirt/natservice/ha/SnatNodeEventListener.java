/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractClusteredAsyncDataTreeChangeListener;
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
public class SnatNodeEventListener  extends AbstractClusteredAsyncDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(SnatNodeEventListener.class);
    private final CentralizedSwitchScheduler  centralizedSwitchScheduler;

    @Inject
    public SnatNodeEventListener(final DataBroker dataBroker,
            final CentralizedSwitchScheduler centralizedSwitchScheduler) {

        super(dataBroker,new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier
                .create(Nodes.class).child(Node.class)),
                Executors.newSingleThreadExecutor());
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
    }

    @Override
    public void remove(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.key();
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        LOG.info("Dpn removed {}", dpnId);
        centralizedSwitchScheduler.removeSwitch(dpnId);
    }

    @Override
    public void update(Node dataObjectModificationBefore,
            Node dataObjectModificationAfter) {
        /*Do Nothing */
    }

    @Override
    public void add(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.key();
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        LOG.info("Dpn added {}", dpnId);
        centralizedSwitchScheduler.addSwitch(dpnId);

    }
}
