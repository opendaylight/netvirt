/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.GlobalNodeMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSAugmentationMerger;
import org.opendaylight.netvirt.elan.l2gw.ha.merge.PSNodeMerger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class OpNodeUpdatedHandler {

    GlobalAugmentationMerger globalAugmentationMerger = GlobalAugmentationMerger.getInstance();
    PSAugmentationMerger psAugmentationMerger     = PSAugmentationMerger.getInstance();
    GlobalNodeMerger globalNodeMerger         = GlobalNodeMerger.getInstance();
    PSNodeMerger psNodeMerger             = PSNodeMerger.getInstance();

    /**
     * Copy HA ps node update to HA child ps node of operational data tree.
     *
     * @param updatedSrcPSNode Updated HA child ps node
     * @param haPath HA node path
     * @param mod the data object modification
     * @param tx Transaction
     */
    public void copyChildPsOpUpdateToHAParent(Node updatedSrcPSNode,
                                              InstanceIdentifier<Node> haPath,
                                              DataObjectModification<Node> mod,
                                              TypedReadWriteTransaction<Operational> tx,
                                              ManagedNewTransactionRunner txRunner) {

        InstanceIdentifier<Node> haPSPath = HwvtepHAUtil.convertPsPath(updatedSrcPSNode, haPath);

        psAugmentationMerger.mergeOpUpdate(haPSPath,
                mod.getModifiedAugmentation(PhysicalSwitchAugmentation.class), tx, txRunner);
        psNodeMerger.mergeOpUpdate(haPSPath, mod, tx, txRunner);
    }

    /**
     * Copy updated data from HA node to child node of operational data tree.
     *
     * @param haPath HA node path
     * @param mod the data object modification
     * @param tx Transaction
     */
    public void copyChildGlobalOpUpdateToHAParent(InstanceIdentifier<Node> haPath,
                                                  DataObjectModification<Node> mod,
                                                  TypedReadWriteTransaction<Operational> tx,
                                                   ManagedNewTransactionRunner txRunner) {

        globalAugmentationMerger.mergeOpUpdate(haPath,
                mod.getModifiedAugmentation(HwvtepGlobalAugmentation.class), tx, txRunner);
        globalNodeMerger.mergeOpUpdate(haPath, mod, tx, txRunner);
    }

}
