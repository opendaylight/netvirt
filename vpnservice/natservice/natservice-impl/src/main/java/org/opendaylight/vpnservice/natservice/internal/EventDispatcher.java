/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.natservice.internal;

import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDispatcher implements Runnable {
    private BlockingQueue<NAPTEntryEvent> eventQueue;
    private NaptEventHandler naptEventHandler;
    private static final Logger LOG = LoggerFactory.getLogger(NaptManager.class);

    EventDispatcher(BlockingQueue<NAPTEntryEvent> eventQueue, NaptEventHandler naptEventHandler){
        this.eventQueue = eventQueue;
        this.naptEventHandler = naptEventHandler;
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
