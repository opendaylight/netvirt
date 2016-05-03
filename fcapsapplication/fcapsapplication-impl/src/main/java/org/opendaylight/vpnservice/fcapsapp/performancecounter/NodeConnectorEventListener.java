/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fcapsapp.performancecounter;

import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.util.Collection;

public abstract  class NodeConnectorEventListener <T extends DataObject> implements ClusteredDataTreeChangeListener<T>,AutoCloseable,FlowCapableNodeConnectorCommitter<T> {
    NodeConnectorEventListener(Class<FlowCapableNodeConnector> flowCapableNodeConnectorClass) {
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<T>> changes) {
        for (DataTreeModification<T> change : changes) {
            final InstanceIdentifier<T> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<T> mod = change.getRootNode();
            final InstanceIdentifier<FlowCapableNodeConnector> nodeConnIdent =
                    key.firstIdentifierOf(FlowCapableNodeConnector.class);

            switch (mod.getModificationType()) {
                case DELETE:
                    remove(key, mod.getDataBefore(), nodeConnIdent);
                    break;
                case SUBTREE_MODIFIED:
                    update(key, mod.getDataBefore(), mod.getDataAfter(), nodeConnIdent);
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        add(key, mod.getDataAfter(), nodeConnIdent);
                    } else {
                        update(key, mod.getDataBefore(), mod.getDataAfter(), nodeConnIdent);
                    }
                    break;


                default:
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }
    }

    @Override
    public void close() throws Exception {
    }
}
