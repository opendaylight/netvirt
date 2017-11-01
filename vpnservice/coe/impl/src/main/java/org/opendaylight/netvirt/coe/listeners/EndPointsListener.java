/*
 * Copyright (c) 2017 Kontron Canada. and others. All rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.service.rev170611.EndpointsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.service.rev170611.endpoints.info.Endpoints;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EndPointsListener implements DataTreeChangeListener<Endpoints> {

    private static final Logger LOG = LoggerFactory.getLogger(EndPointsListener.class);
    private ListenerRegistration<EndPointsListener> listenerRegistration;
    private final DataBroker dataBroker;

    @Inject
    public EndPointsListener(final DataBroker dataBroker) {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        this.dataBroker = dataBroker;
    }

    protected InstanceIdentifier<Endpoints> getWildCardPath() {
        return InstanceIdentifier.create(EndpointsInfo.class).child(Endpoints.class);
    }

    public void registerListener(LogicalDatastoreType dsType, final DataBroker db) {
        final DataTreeIdentifier<Endpoints> treeId = new DataTreeIdentifier<>(dsType, getWildCardPath());
        listenerRegistration = db.registerDataTreeChangeListener(treeId, EndPointsListener.this);
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
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Endpoints>> changes) {
        for (DataTreeModification<Endpoints> change : changes) {
            final DataObjectModification<Endpoints> mod = change.getRootNode();

            switch (mod.getModificationType()) {
                case DELETE:
                    LOG.info("EndPoints deleted {}", mod.getDataBefore());
                    break;
                case SUBTREE_MODIFIED:
                    LOG.info("EndPoints updated old : {}, new : {}", mod.getDataBefore(), mod.getDataAfter());
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        LOG.info("EndPoints added {}", mod.getDataAfter());
                    } else {
                        LOG.info("EndPoints updated old : {}, new : {}", mod.getDataBefore(), mod.getDataAfter());
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }
    }
}
