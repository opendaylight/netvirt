/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanDmacUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DpnDmacJobUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DpnDmacJobUtil.class);
    private ElanL2GatewayUtils elanL2GatewayUtils;
    private ElanClusterUtils elanClusterUtils;
    private ElanInstanceCache elanInstanceCache;
    private ElanDmacUtils elanDmacUtils;
    private Scheduler scheduler;
    private JobCoordinator jobCoordinator;

    @Inject
    public DpnDmacJobUtil(ElanL2GatewayUtils elanL2GatewayUtils, ElanClusterUtils elanClusterUtils,
                          ElanInstanceCache elanInstanceCache, ElanDmacUtils elanDmacUtils,
                          Scheduler scheduler, JobCoordinator jobCoordinator) {
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.elanInstanceCache = elanInstanceCache;
        this.elanDmacUtils = elanDmacUtils;
        this.scheduler = scheduler;
        this.jobCoordinator = jobCoordinator;
    }

    public void installDmacFromL2gws(String elanName, DpnInterfaces dpnInterfaces) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            new DpnDmacJob(elanName, dpnInterfaces, nodeId, true, elanL2GatewayUtils, elanClusterUtils,
                    elanInstanceCache, elanDmacUtils, scheduler, jobCoordinator).submit();
        });
    }

    public void uninstallDmacFromL2gws(String elanName, DpnInterfaces dpnInterfaces) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            new DpnDmacJob(elanName, dpnInterfaces, nodeId, false, elanL2GatewayUtils, elanClusterUtils,
                    elanInstanceCache, elanDmacUtils, scheduler, jobCoordinator).submit();
        });
    }
}
