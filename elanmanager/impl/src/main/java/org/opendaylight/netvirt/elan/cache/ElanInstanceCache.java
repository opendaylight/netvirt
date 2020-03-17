/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.cache;

import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.elan.utils.Scheduler;
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
public class ElanInstanceCache extends InstanceIdDataObjectCache<ElanInstance> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanInstanceCache.class);

    //TODO: Make it configurable
    private static final int ELAN_CLEANUP_DELAY_IN_MINS = 30;

    private final Map<InstanceIdentifier<ElanInstance>, Collection<Runnable>> waitingJobs = new HashMap<>();

    private final Scheduler scheduler;

    @Inject
    public ElanInstanceCache(DataBroker dataBroker, CacheProvider cacheProvider, Scheduler scheduler) {
        super(ElanInstance.class, dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(ElanInstances.class).child(ElanInstance.class), cacheProvider);
        this.scheduler = scheduler;
    }

    @Override
    protected void removed(InstanceIdentifier<ElanInstance> path, ElanInstance dataObject) {
        scheduler.getScheduledExecutorService().schedule(() -> {
            super.removed(path, dataObject);
        }, ELAN_CLEANUP_DELAY_IN_MINS, TimeUnit.MINUTES);
    }

    public Optional<ElanInstance> get(String elanInstanceName) {
        try {
            return get(ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName));
        } catch (ReadFailedException e) {
            LOG.warn("Error reading ElanInstance {}", elanInstanceName, e);
            return Optional.empty();
        }
    }

    public Optional<ElanInstance> get(String elanInstanceName, Runnable runAfterElanIsAvailable) {
        Optional<ElanInstance> possibleInstance = get(elanInstanceName);
        if (!possibleInstance.isPresent()) {
            synchronized (waitingJobs) {
                possibleInstance = get(elanInstanceName);
                if (!possibleInstance.isPresent()) {
                    waitingJobs.computeIfAbsent(ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName),
                        key -> new ArrayList<>()).add(runAfterElanIsAvailable);
                }
            }
        }

        return possibleInstance;
    }

    @Override
    protected void added(InstanceIdentifier<ElanInstance> path, ElanInstance elanInstance) {
        Collection<Runnable> jobsToRun;
        synchronized (waitingJobs) {
            jobsToRun = waitingJobs.remove(path);
        }

        if (jobsToRun != null) {
            jobsToRun.forEach(Runnable::run);
        }
    }
}
