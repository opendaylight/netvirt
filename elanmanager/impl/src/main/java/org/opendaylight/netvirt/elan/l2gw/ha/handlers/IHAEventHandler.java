/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.handlers;

import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.Datastore.Operational;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


public interface IHAEventHandler {


    void copyChildGlobalOpUpdateToHAParent(InstanceIdentifier<Node> haPath,
                                           DataObjectModification<Node> mod,
                                           TypedReadWriteTransaction<Operational> tx);

    void copyChildPsOpUpdateToHAParent(Node updatedSrcPSNode,
                                       InstanceIdentifier<Node> haPath,
                                       DataObjectModification<Node> mod,
                                       TypedReadWriteTransaction<Operational> tx);

    void copyHAPSUpdateToChild(InstanceIdentifier<Node> haChildPath,
                               DataObjectModification<Node> mod,
                               TypedReadWriteTransaction<Configuration> tx)
            ;

    void copyHAGlobalUpdateToChild(InstanceIdentifier<Node> haChildPath,
                                   DataObjectModification<Node> mod,
                                   TypedReadWriteTransaction<Configuration> tx)
            ;
}
