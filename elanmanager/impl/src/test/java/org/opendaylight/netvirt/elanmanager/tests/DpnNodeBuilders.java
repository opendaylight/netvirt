/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class DpnNodeBuilders {

    private DpnNodeBuilders() {
    }

    public static org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node
        buildDpnNode(BigInteger dpnId) {

        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId nodeId =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId("openflow:" + dpnId);
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node nodeDpn =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder().setId(nodeId)
                        .setKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes
                                .NodeKey(nodeId)).build();
        return nodeDpn;
    }

    public static InstanceIdentifier<Group> createGroupIid(Group group, BigInteger dpId) {
        long groupId = group.getGroupId().getValue().longValue();
        return buildGroupInstanceIdentifier(groupId, buildDpnNode(dpId));
    }

    public static InstanceIdentifier<Group>
        buildGroupInstanceIdentifier(long groupId,
                                     org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node
                                             nodeDpn) {
        InstanceIdentifier groupInstanceId =
                InstanceIdentifier.builder(Nodes.class)
                        .child(org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node.class,
                                nodeDpn.getKey()).augmentation(FlowCapableNode.class).child(Group.class,
                        new GroupKey(new GroupId(Long.valueOf(groupId)))).build();
        return groupInstanceId;
    }
}
