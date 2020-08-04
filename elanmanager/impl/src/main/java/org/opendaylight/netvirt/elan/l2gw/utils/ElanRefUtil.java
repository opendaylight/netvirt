/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.internal.ElanGroupCache;
import org.opendaylight.netvirt.elan.l2gw.listeners.ElanMacTableCache;
import org.opendaylight.netvirt.elan.l2gw.listeners.HwvtepConfigNodeCache;
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
    private final ElanMacTableCache elanMacTableCache;
    private final ElanGroupCache elanGroupCache;
    private final HwvtepConfigNodeCache hwvtepConfigNodeCache;
    private final ElanUtils elanUtils;
    private final ElanItmUtils elanItmUtils;

    @Inject
    public ElanRefUtil(DataBroker dataBroker,
                       ElanClusterUtils elanClusterUtils,
                       ElanGroupCache elanGroupCache,
                       ElanInstanceCache elanInstanceCache,
                       ElanInstanceDpnsCache elanInstanceDpnsCache,
                       ElanInterfaceCache elanInterfaceCache,
                       ElanItmUtils elanItmUtils,
                       ElanMacTableCache elanMacTableCache,
                       ElanUtils elanUtils,
                       HwvtepConfigNodeCache hwvtepConfigNodeCache,
                       JobCoordinator jobCoordinator,
                       Scheduler scheduler) {
        this.dataBroker = dataBroker;
        this.elanClusterUtils = elanClusterUtils;
        this.elanGroupCache = elanGroupCache;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
        this.elanInterfaceCache = elanInterfaceCache;
        this.elanItmUtils = elanItmUtils;
        this.elanMacTableCache = elanMacTableCache;
        this.elanUtils = elanUtils;
        this.hwvtepConfigNodeCache = hwvtepConfigNodeCache;
        this.jobCoordinator = jobCoordinator;
        this.scheduler = scheduler;
    }

    public DataBroker getDataBroker() {
        return dataBroker;
    }

    public ElanClusterUtils getElanClusterUtils() {
        return elanClusterUtils;
    }

    public ElanGroupCache getElanGroupCache() {
        return elanGroupCache;
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

    public ElanMacTableCache getElanMacTableCache() {
        return elanMacTableCache;
    }

    public ElanUtils getElanUtils() {
        return elanUtils;
    }

    public HwvtepConfigNodeCache getHwvtepConfigNodeCache() {
        return hwvtepConfigNodeCache;
    }

    public JobCoordinator getJobCoordinator() {
        return jobCoordinator;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}