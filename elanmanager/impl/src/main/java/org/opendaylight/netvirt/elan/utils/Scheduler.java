/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.inject.Singleton;

@Singleton
public class Scheduler implements AutoCloseable {

    private final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("elan-sched-%d").build();

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1,
            namedThreadFactory);

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    @Override
    public void close() {
        scheduledExecutorService.shutdown();
    }
}
