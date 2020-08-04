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
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;

@Singleton
public class McastUpdateJobUtil {
    ElanL2GatewayMulticastUtils mcastUtils;
    ElanClusterUtils elanClusterUtils;
    Scheduler scheduler;
    JobCoordinator jobCoordinator;

    @Inject
    public McastUpdateJobUtil(ElanL2GatewayMulticastUtils mcastUtils, ElanClusterUtils elanClusterUtils,
                              Scheduler scheduler, JobCoordinator jobCoordinator) {
        this.mcastUtils = mcastUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.scheduler = scheduler;
        this.jobCoordinator = jobCoordinator;
    }

    public void updateAllMcasts(String elanName) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            new McastUpdateJob(elanName, nodeId, true, mcastUtils,
                    elanClusterUtils, scheduler, jobCoordinator).submit();
        });
    }

    public void removeMcastForNode(String elanName, String nodeId) {
        new McastUpdateJob(elanName, nodeId, false, mcastUtils,
                elanClusterUtils, scheduler, jobCoordinator).submit();
    }

    public void updateMcastForNode(String elanName, String nodeId) {
        new McastUpdateJob(elanName, nodeId, true, mcastUtils,
                elanClusterUtils, scheduler, jobCoordinator).submit();
    }
}
