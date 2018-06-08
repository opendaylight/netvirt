/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Callable;

public class ArpMonitorStartTask implements Callable<List<ListenableFuture<Void>>> {
    private final MacEntry macEntry;
    private final Long arpMonitorProfileId;
    private final AlivenessMonitorUtils alivenessMonitorUtils;


    public ArpMonitorStartTask(MacEntry macEntry, Long profileId, AlivenessMonitorUtils alivenessMonitorUtils) {
        this.macEntry = macEntry;
        this.arpMonitorProfileId = profileId;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        alivenessMonitorUtils.startArpMonitoring(macEntry, arpMonitorProfileId);
        return null;
    }
}
