/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheElanInstanceListener implements ClusteredDataTreeChangeListener<ElanInstance> {

    private  ElanServiceProvider elanServiceProvider;
    private ListenerRegistration<CacheElanInstanceListener> registration;
    private static final Logger logger = LoggerFactory.getLogger(CacheElanInstanceListener.class);

    public CacheElanInstanceListener(ElanServiceProvider elanServiceProvider) {
        this.elanServiceProvider = elanServiceProvider;
        registerListener();
    }

    private void registerListener() {
        final DataTreeIdentifier<ElanInstance> treeId =
                new DataTreeIdentifier<ElanInstance>(LogicalDatastoreType.CONFIGURATION, getWildcardPath());
        try {
            logger.trace("Registering on path: {}", treeId);
            registration = elanServiceProvider.getBroker().registerDataTreeChangeListener(treeId, CacheElanInstanceListener.this);
        } catch (final Exception e) {
            logger.warn("CacheInterfaceConfigListener registration failed", e);
        }
    }
    protected InstanceIdentifier<ElanInstance> getWildcardPath() {
        return InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class);
    }

    public void close() throws Exception {
        if(registration != null) {
            registration.close();
        }
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<ElanInstance>> changes) {
        for (DataTreeModification<ElanInstance> change : changes) {
            DataObjectModification<ElanInstance> mod = change.getRootNode();
            switch (mod.getModificationType()) {
            case DELETE:
                ElanUtils.removeElanInstanceFromCache(mod.getDataBefore().getElanInstanceName());
                break;
            case SUBTREE_MODIFIED:
            case WRITE:
                ElanInstance elanInstance = mod.getDataAfter();
                ElanUtils.addElanInstanceIntoCache(elanInstance.getElanInstanceName(), elanInstance);
                break;
            default:
                throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }		
    }

}
