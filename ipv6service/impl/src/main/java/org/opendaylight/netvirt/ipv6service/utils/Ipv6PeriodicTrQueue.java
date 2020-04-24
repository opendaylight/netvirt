/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service.utils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6PeriodicTrQueue implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6PeriodicTrQueue.class);

    private final Consumer<Uuid> onMessage;
    private final ConcurrentLinkedQueue<Uuid> ipv6PeriodicQueue = new ConcurrentLinkedQueue<>();
    private final Thread transmitterThread = new Thread(this::threadRunLoop);
    private final ReentrantLock queueLock = new ReentrantLock();
    private final Condition queueCondition = queueLock.newCondition();
    private volatile boolean closed;

    @GuardedBy("queueLock")
    private boolean isMessageAvailable;

    public Ipv6PeriodicTrQueue(Consumer<Uuid> onMessage) {
        this.onMessage = onMessage;
        init();
    }

    public void init() {
        transmitterThread.start();

        LOG.info("Started the ipv6 periodic RA transmission thread");
    }

    @Override
    public void close() {
        queueLock.lock();
        try {
            closed = true;
            queueCondition.signalAll();
        } finally {
            queueLock.unlock();
        }
    }

    public void addMessage(Uuid portId) {
        ipv6PeriodicQueue.add(portId);

        queueLock.lock();
        try {
            isMessageAvailable = true;
            queueCondition.signalAll();
        } finally {
            queueLock.unlock();
        }
    }

    // Suppress "Exceptional return value of java.util.concurrent.locks.Condition.await" - we really don't care
    // if the Condition was signaled or timed out as we use isMessageAvailable to break or continue waiting.
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private void threadRunLoop() {
        while (!closed) {
            while (!ipv6PeriodicQueue.isEmpty()) {
                Uuid portId = ipv6PeriodicQueue.poll();
                LOG.debug("timeout got for port {}", portId);
                onMessage.accept(portId);
            }

            queueLock.lock();
            try {
                while (!isMessageAvailable && !closed) {
                    queueCondition.await(1, TimeUnit.SECONDS);
                }
                isMessageAvailable = false;
            } catch (InterruptedException e) {
                LOG.debug("threadRunLoop interrupted", e);
            } finally {
                queueLock.unlock();
            }
        }
    }
}
