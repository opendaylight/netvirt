/*
 * Copyright (c) 2016 Hewlett-Packard Enterprise and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.infrautils.counters.api.OccurenceCounter;

public enum NatServiceCounters {
    install_default_nat_flow, //
    remove_default_nat_flow;

    private OccurenceCounter counter;

    private NatServiceCounters() {
        counter = new OccurenceCounter(getClass().getEnclosingClass().getSimpleName(), name(), name());
    }

    public void inc() {
        counter.inc();
    }
}
