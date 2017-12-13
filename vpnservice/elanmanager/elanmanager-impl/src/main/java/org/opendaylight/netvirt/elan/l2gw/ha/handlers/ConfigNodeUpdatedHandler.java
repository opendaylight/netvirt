/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.elan.l2gw.ha.DataUpdates;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalNodeMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSNodeMerger;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class ConfigNodeUpdatedHandler {
    GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    PSAugmentationMerger psAugmentationMerger = PSAugmentationMerger.getInstance();
    GlobalNodeMerger globalNodeMerger = GlobalNodeMerger.getInstance();
    PSNodeMerger psNodeMerger = PSNodeMerger.getInstance();

    /**
     * Copy updated data from HA node to child node of config data tree.
     *
     * @param haChildNodeId HA child node which needs to be updated
     * @param dataUpdates updated and deleted data.
     * @param tx Transaction
     * @throws ReadFailedException  Exception thrown if read fails
     * @throws ExecutionException  Exception thrown if Execution fail
     * @throws InterruptedException Thread interrupted Exception
     */
    public void copyHAGlobalUpdateToChild(InstanceIdentifier<Node> haChildNodeId,
                                          DataUpdates dataUpdates,
                                          ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {

        globalAugmentationMerger.mergeConfigUpdate(haChildNodeId, dataUpdates, tx);
        globalNodeMerger.mergeConfigUpdate(haChildNodeId, dataUpdates, tx);
    }

    /**
     * Copy HA ps node update to HA child ps node of config data tree.
     *
     * @param haChildNodeId HA child node which needs to be updated
     * @param dataUpdates updated and deleted data
     * @param tx Transaction
     * @throws ReadFailedException  Exception thrown if read fails
     * @throws ExecutionException  Exception thrown if Execution fail
     * @throws InterruptedException Thread interrupted Exception
     */
    public void copyHAPSUpdateToChild(InstanceIdentifier<Node> haChildNodeId,
                                      DataUpdates dataUpdates,
                                      ReadWriteTransaction tx)
            throws InterruptedException, ExecutionException, ReadFailedException {
        psAugmentationMerger.mergeConfigUpdate(haChildNodeId, dataUpdates, tx);
        psNodeMerger.mergeConfigUpdate(haChildNodeId, dataUpdates, tx);
    }

}
