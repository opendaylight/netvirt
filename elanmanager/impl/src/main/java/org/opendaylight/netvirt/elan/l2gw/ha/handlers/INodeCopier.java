/*
 * Copyright (c) 2020 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import java.util.Optional;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public interface INodeCopier<D extends Datastore> {

    <D extends Datastore> void copyGlobalNode(Optional<Node> globalNodeOptional,
                        InstanceIdentifier<Node> srcPath,
                        InstanceIdentifier<Node> dstPath,
                        Class<D> logicalDatastoreType,
                        TypedReadWriteTransaction<D> tx)throws ReadFailedException;

    <D extends Datastore> void copyPSNode(Optional<Node> psNodeOptional,
                    InstanceIdentifier<Node> srcPsPath,
                    InstanceIdentifier<Node> dstPsPath,
                    InstanceIdentifier<Node> dstGlobalPath,
                    Class<D> logicalDatastoreType,
                    TypedReadWriteTransaction<D> tx)throws ReadFailedException;

}
