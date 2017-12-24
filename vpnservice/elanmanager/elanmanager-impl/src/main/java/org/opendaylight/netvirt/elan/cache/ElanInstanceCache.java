/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.cache;

import com.google.common.base.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.cache.DataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches ElanInstances.
 *
 * @author Thomas Pantelis
 */
@Singleton
public class ElanInstanceCache extends DataObjectCache<ElanInstance> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceCache.class);

    @Inject
    public ElanInstanceCache(DataBroker dataBroker, CacheProvider cacheProvider) {
        super(ElanInstance.class, dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class), cacheProvider);
    }

    public Optional<ElanInstance> get(String elanInstanceName) {
        try {
            return get(ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName));
        } catch (ReadFailedException e) {
            LOG.warn("Error reading ElanInstance {}", elanInstanceName, e);
            return Optional.absent();
        }
    }
}
