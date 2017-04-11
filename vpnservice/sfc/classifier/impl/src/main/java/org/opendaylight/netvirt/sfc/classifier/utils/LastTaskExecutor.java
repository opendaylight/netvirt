/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An executor that only executes the last submitted task. Ongoing tasks wont
 * be cancelled.
 */
public class LastTaskExecutor implements Executor {

    private final Executor executor;
    private final AtomicReference<Runnable> lastTask = new AtomicReference<>();

    public LastTaskExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(final Runnable newTask) {
        if (newTask == null) {
            throw new NullPointerException();
        }

        final Runnable oldTask = lastTask.getAndSet(newTask);
        if (oldTask != null) {
            return;
        }

        try {
            executor.execute(() -> {
                final Runnable runTask = lastTask.getAndSet(null);
                if (runTask != null) {
                    runTask.run();
                }
            });
        } catch (RejectedExecutionException e) {
            final Runnable retryTask  = lastTask.getAndSet(null);
            if (retryTask != newTask) {
                execute(retryTask);
            }
            throw e;
        }
    }
}
