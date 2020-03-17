/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalNodeMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSNodeMerger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
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
     * @param mod the data object modification
     * @param tx Transaction
     */
    public void copyHAGlobalUpdateToChild(InstanceIdentifier<Node> haChildNodeId,
                                          DataObjectModification<Node> mod,
                                          TypedReadWriteTransaction<Configuration> tx) {
        globalAugmentationMerger.mergeConfigUpdate(haChildNodeId,
                mod.getModifiedAugmentation(HwvtepGlobalAugmentation.class), tx);
        globalNodeMerger.mergeConfigUpdate(haChildNodeId, mod, tx);
    }

    /**
     * Copy HA ps node update to HA child ps node of config data tree.
     *
     * @param haChildNodeId HA child node which needs to be updated
     * @param mod the data object modification
     * @param tx Transaction
     */
    public void copyHAPSUpdateToChild(InstanceIdentifier<Node> haChildNodeId,
                                      DataObjectModification<Node> mod,
                                      TypedReadWriteTransaction<Configuration> tx) {

        psAugmentationMerger.mergeConfigUpdate(haChildNodeId,
                mod.getModifiedAugmentation(PhysicalSwitchAugmentation.class), tx);
        psNodeMerger.mergeConfigUpdate(haChildNodeId, mod, tx);
    }

}
