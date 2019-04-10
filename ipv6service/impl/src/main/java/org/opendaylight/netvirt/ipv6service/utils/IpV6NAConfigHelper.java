/*
 * Copyright (c) 2019 Alten Calsoft Labs India Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev181010.Ipv6serviceConfig;

@Singleton
public class IpV6NAConfigHelper {
    private Ipv6serviceConfig.NaResponderMode naResponderMode = Ipv6serviceConfig.NaResponderMode.Switch;
    private long nsSlowProtectionTimeOutinMs = 30000L;
    private long ipv6RouterReachableTimeinMS = 120000L;

    @Inject
    public IpV6NAConfigHelper(Ipv6serviceConfig ipv6serviceConfig) {
        this.naResponderMode = ipv6serviceConfig.getNaResponderMode();
        this.ipv6RouterReachableTimeinMS = ipv6serviceConfig.getIpv6RouterReachableTime().longValue();
        this.nsSlowProtectionTimeOutinMs = ipv6serviceConfig.getNsSlowPathProtectionTimeout().longValue();
    }

    public Ipv6serviceConfig.NaResponderMode getNaResponderMode() {
        return naResponderMode;
    }

    public void setNaResponderMode(Ipv6serviceConfig.NaResponderMode naResponderMode) {
        this.naResponderMode = naResponderMode;
    }

    public long getNsSlowProtectionTimeOutinMs() {
        return nsSlowProtectionTimeOutinMs;
    }

    public void setNsSlowProtectionTimeOutinMs(long nsSlowProtectionTimeOutinMs) {
        this.nsSlowProtectionTimeOutinMs = nsSlowProtectionTimeOutinMs;
    }

    public long getIpv6RouterReachableTimeinMS() {
        return ipv6RouterReachableTimeinMS;
    }

    public void setIpv6RouterReachableTimeinMS(long ipv6RouterReachableTimeinMS) {
        this.ipv6RouterReachableTimeinMS = ipv6RouterReachableTimeinMS;
    }
}
