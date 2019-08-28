/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;

@Singleton
public class ElanRefUtil {

    private final DataBroker dataBroker;
    private final ElanClusterUtils elanClusterUtils;
    private final Scheduler scheduler;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanInstanceDpnsCache elanInstanceDpnsCache;
    private final ElanInterfaceCache elanInterfaceCache;
    private final ElanUtils elanUtils;
    private final ElanItmUtils elanItmUtils;

    @Inject
    public ElanRefUtil(DataBroker dataBroker,
                       ElanClusterUtils elanClusterUtils,
                       ElanInstanceCache elanInstanceCache,
                       ElanInstanceDpnsCache elanInstanceDpnsCache,
                       ElanInterfaceCache elanInterfaceCache,
                       ElanItmUtils elanItmUtils,
                       ElanUtils elanUtils,
                       JobCoordinator jobCoordinator,
                       Scheduler scheduler) {
        this.dataBroker = dataBroker;
        this.elanClusterUtils = elanClusterUtils;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
        this.elanInterfaceCache = elanInterfaceCache;
        this.elanItmUtils = elanItmUtils;
        this.elanUtils = elanUtils;
        this.jobCoordinator = jobCoordinator;
        this.scheduler = scheduler;
    }

    public DataBroker getDataBroker() {
        return dataBroker;
    }

    public ElanClusterUtils getElanClusterUtils() {
        return elanClusterUtils;
    }

    public ElanInstanceCache getElanInstanceCache() {
        return elanInstanceCache;
    }

    public ElanInstanceDpnsCache getElanInstanceDpnsCache() {
        return elanInstanceDpnsCache;
    }

    public ElanInterfaceCache getElanInterfaceCache() {
        return elanInterfaceCache;
    }

    public ElanItmUtils getElanItmUtils() {
        return elanItmUtils;
    }

    public ElanUtils getElanUtils() {
        return elanUtils;
    }

    public JobCoordinator getJobCoordinator() {
        return jobCoordinator;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}