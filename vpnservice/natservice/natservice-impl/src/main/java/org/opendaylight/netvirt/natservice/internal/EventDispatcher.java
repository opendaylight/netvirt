/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.concurrent.ExecutorService;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EventDispatcher implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EventDispatcher.class);

    private final NaptEventHandler naptEventHandler;
    private final ExecutorService executor = SpecialExecutors.newBoundedSingleThreadExecutor(
            NatConstants.EVENT_QUEUE_LENGTH, "NatServiceEventDispatcher");

    @Inject
    public EventDispatcher(final NaptEventHandler naptEventHandler) {
        this.naptEventHandler = naptEventHandler;
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdown();
    }

    public void addFlowRemovedNaptEvent(NAPTEntryEvent naptEntryEvent) {
        LOG.trace("addFlowRemovedNaptEvent : Adding Flow Removed event {}", naptEntryEvent);

        executor.execute(() -> naptEventHandler.handleEvent(naptEntryEvent));
    }
}
