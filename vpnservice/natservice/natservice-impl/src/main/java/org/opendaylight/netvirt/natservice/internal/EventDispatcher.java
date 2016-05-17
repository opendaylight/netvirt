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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDispatcher implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(NaptManager.class);

    private final BlockingQueue<NAPTEntryEvent> eventQueue;
    private final NaptEventHandler naptEventHandler;

    EventDispatcher(NaptEventHandler naptEventHandler){
        this.eventQueue = new ArrayBlockingQueue<>(NatConstants.EVENT_QUEUE_LENGTH);
        this.naptEventHandler = naptEventHandler;
    }

    public void start() {
        new Thread(this).start();
    }

    public void addNaptEvent(NAPTEntryEvent naptEntryEvent){
        this.eventQueue.add(naptEntryEvent);
    }

    public void run(){
        while(true) {
            try {
                NAPTEntryEvent event = eventQueue.take();
                naptEventHandler.handleEvent(event);
            } catch (InterruptedException e) {
                LOG.error("EventDispatcher : Error in handling the event queue : ", e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
