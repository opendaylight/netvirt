/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDispatcher implements Runnable {
    private BlockingQueue<NAPTEntryEvent> eventQueue;
    private NaptEventHandler naptEventHandler;
    private static final Logger LOG = LoggerFactory.getLogger(EventDispatcher.class);

    EventDispatcher(BlockingQueue<NAPTEntryEvent> eventQueue, NaptEventHandler naptEventHandler){
        this.eventQueue = eventQueue;
        this.naptEventHandler = naptEventHandler;
    }

    public void addNaptEvent(NAPTEntryEvent naptEntryEvent){
        LOG.trace("NAT Service : Adding event to eventQueue which is of size {} and remaining capacity {}",
                eventQueue.size(), eventQueue.remainingCapacity());
        this.eventQueue.add(naptEntryEvent);
    }

    public void run(){
        while(true) {
            try {
                NAPTEntryEvent event = eventQueue.take();
                naptEventHandler.handleEvent(event);
            } catch (InterruptedException e) {
                LOG.error("NAT Service : EventDispatcher : Error in handling the event queue : ", e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
