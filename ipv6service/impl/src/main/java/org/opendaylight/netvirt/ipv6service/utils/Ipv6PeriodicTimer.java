/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class Ipv6PeriodicTimer implements TimerTask {
    private final Ipv6PeriodicTrQueue ipv6Queue;
    private final Uuid portId;

    public Ipv6PeriodicTimer(Uuid portId, Ipv6PeriodicTrQueue ipv6Queue) {
        this.portId = portId;
        this.ipv6Queue = ipv6Queue;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        ipv6Queue.addMessage(portId);
    }
}
