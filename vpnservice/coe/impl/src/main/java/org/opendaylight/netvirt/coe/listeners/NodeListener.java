/*
 * Copyright (c) 2017 Kontron Canada and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.listeners;

import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.k8s.node.rev170829.K8sNodesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.k8s.node.rev170829.k8s.nodes.info.K8sNodes;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NodeListener implements DataTreeChangeListener<K8sNodes> {

    private static final Logger LOG = LoggerFactory.getLogger(NodeListener.class);
    private ListenerRegistration<NodeListener> listenerRegistration;
    private final DataBroker dataBroker;

    @Inject
    public NodeListener(final DataBroker dataBroker) {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        this.dataBroker = dataBroker;
    }

    protected InstanceIdentifier<K8sNodes> getWildCardPath() {
        return InstanceIdentifier.create(K8sNodesInfo.class).child(K8sNodes.class);
    }

    public void registerListener(LogicalDatastoreType dsType, final DataBroker db) {
        final DataTreeIdentifier<K8sNodes> treeId = new DataTreeIdentifier<>(dsType, getWildCardPath());
        listenerRegistration = db.registerDataTreeChangeListener(treeId, NodeListener.this);
    }

    @PreDestroy
    public void close() {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } finally {
                listenerRegistration = null;
            }
        }
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<K8sNodes>> changes) {
        for (DataTreeModification<K8sNodes> change : changes) {
            final DataObjectModification<K8sNodes> mod = change.getRootNode();

            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.info("Node deleted {}", mod.getDataBefore());
                    break;
                case SUBTREE_MODIFIED:
                    LOG.info("Node updated old : {}, new : {}", mod.getDataBefore(), mod.getDataAfter());
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        LOG.info("Node added {}", mod.getDataAfter());
                    } else {
                        LOG.info("Node updated old : {}, new : {}", mod.getDataBefore(), mod.getDataAfter());
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }
    }
}
