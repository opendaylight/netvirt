/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netvirt.vpnmanager;


public class TimedResolvedDeferEvent {
    long queuedTimestamp;
    DeferedEvent resolvedEvent;

    public TimedResolvedDeferEvent(long timestamp, DeferedEvent deferedEvent) {
        this.queuedTimestamp = timestamp;
        this.resolvedEvent = deferedEvent;
    }
}
