/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public interface INodeCopier {

    void copyGlobalNode(Node globalNode,
                        InstanceIdentifier<Node> srcPath,
                        InstanceIdentifier<Node> dstPath,
                        LogicalDatastoreType logicalDatastoreType,
                        ReadWriteTransaction tx) throws ReadFailedException;

    void copyPSNode(Node psNode,
                    InstanceIdentifier<Node> srcPsPath,
                    InstanceIdentifier<Node> dstPsPath,
                    InstanceIdentifier<Node> dstGlobalPath,
                    LogicalDatastoreType logicalDatastoreType,
                    ReadWriteTransaction tx) throws ReadFailedException;
}
