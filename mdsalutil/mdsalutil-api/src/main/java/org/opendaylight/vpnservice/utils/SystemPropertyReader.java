/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.utils;

import org.opendaylight.yangtools.util.PropertyUtils;

public class SystemPropertyReader {
    public static class Cluster {
        // Sleep time to be used between successive EntityOwnershipState calls
        // Returned time is in milliseconds
        public static long getSleepTimeBetweenRetries() {
            return Long.getLong("cluster.entity_owner.sleep_time_between_retries", 1000);
        }

        // Returns max. retries to be tried with EntityOwnershipState calls
        public static int getMaxRetries() {
            return Integer.getInteger("cluster.entity_owner.max_retries", 5);
        }
    }

    // Returns max retries to be tried with DataStoreJobCoordinator calls
    public static int getDataStoreJobCoordinatorMaxRetries() {
        return Integer.getInteger("datastore.job_coordinator.max_retries", 5);
    }
}
