/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.nodehandlertest;

import static org.junit.Assert.assertEquals;

import com.google.common.base.Optional;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Created by eaksahu on 8/12/2016.
 */
final class TestUtil {

    private TestUtil() {

    }

    static void verifyHAOpNode(Node d1GlobalOpNode, Node haGlobalOpNode, Node d1PsOpNode, Node haPsOpNode,
            InstanceIdentifier<Node> haId, InstanceIdentifier<Node> d1PsId, InstanceIdentifier<Node> haPsId,
            DataBroker dataBroker) throws ReadFailedException {
        ReadWriteTransaction transaction = dataBroker.newReadWriteTransaction();
        TestComparators.compareLogicalSwitches(d1GlobalOpNode, haGlobalOpNode, haId);
        //TestComparators.compareRemoteUcastMacs(d1GlobalOpNode, haGlobalOpNode, haId);
        //TestComparators.compareRemoteMcastMacs(d1GlobalOpNode, haGlobalOpNode, haId);
        TestComparators.compareLocalUcastMacs(d1GlobalOpNode, haGlobalOpNode, haId);
        //TestComparators.compareLocalMcastMacs(d1GlobalOpNode, haGlobalOpNode, haId);
        TestComparators.verifySwitches(haGlobalOpNode, haPsOpNode);
        TestComparators.verifySwitches(d1GlobalOpNode, d1PsOpNode);
        //TestComparators.comparePhysicalSwitches(d1PsOpNode, haPsOpNode, d1PsId, haPsId, transaction, "s3",
        //        d1GlobalOpNode, haGlobalOpNode);
    }

    static Optional<Node> readNode(LogicalDatastoreType datastoreType, InstanceIdentifier<Node> id,
        ReadOnlyTransaction tx) throws Exception {
        return tx.read(datastoreType, id).checkedGet();
    }


    static void verifyHAconfigNode(Node haConfig, Node d1Node) {
        String haid = haConfig.augmentation(HwvtepGlobalAugmentation.class).getManagers()
                .get(0).getManagerOtherConfigs().get(0).getOtherConfigValue();
        String d1id = d1Node.getNodeId().getValue();
        assertEquals("Other config should contain D1 as child manager", haid, d1id);
    }
}
