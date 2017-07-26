/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EventDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(EventDispatcher.class);
    private final BlockingQueue<NAPTEntryEvent> removeFlowQueue;
    private final NaptEventHandler naptEventHandler;

    @Inject
    public EventDispatcher(final NaptEventHandler naptEventHandler) {
        this.naptEventHandler = naptEventHandler;
        this.removeFlowQueue = new ArrayBlockingQueue<>(NatConstants.EVENT_QUEUE_LENGTH);
    }

    @PostConstruct
    public void init() {
        FlowRemoveThread flowRemoveThread = new FlowRemoveThread();
        new Thread(flowRemoveThread).start();
    }

    public void addFlowRemovedNaptEvent(NAPTEntryEvent naptEntryEvent) {
        LOG.trace("addFlowRemovedNaptEvent : Adding Flow Removed event to eventQueue which is of size {} "
                + "and remaining capacity {}", removeFlowQueue.size(), removeFlowQueue.remainingCapacity());
        this.removeFlowQueue.add(naptEntryEvent);
    }

    private class FlowRemoveThread implements Runnable {
        public void run() {
            while (true) {
                try {
                    LOG.trace("run : Inside FlowRemoveThread");
                    NAPTEntryEvent event = removeFlowQueue.take();
                    naptEventHandler.handleEvent(event);
                } catch (InterruptedException e) {
                    LOG.error("run : EventDispatcher : Error in handling the flow removed event queue: ", e);
                }
            }
        }
    }
}
