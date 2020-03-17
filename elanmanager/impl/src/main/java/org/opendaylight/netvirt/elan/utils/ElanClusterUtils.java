/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;

@Singleton
public final class ElanClusterUtils {
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public ElanClusterUtils(EntityOwnershipUtils entityOwnershipUtils, JobCoordinator jobCoordinator) {
        this.entityOwnershipUtils = entityOwnershipUtils;
        this.jobCoordinator = jobCoordinator;
    }

    public void runOnlyInOwnerNode(String jobDesc, Runnable job) {
        entityOwnershipUtils.runOnlyInOwnerNode(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
            HwvtepSouthboundConstants.ELAN_ENTITY_NAME, jobCoordinator, jobDesc, job);
    }

    public void runOnlyInOwnerNode(String jobKey, String jobDesc, Callable<List<? extends ListenableFuture<?>>> job) {
        entityOwnershipUtils.runOnlyInOwnerNode(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
            HwvtepSouthboundConstants.ELAN_ENTITY_NAME, jobCoordinator, jobKey, jobDesc, job);
    }
}
