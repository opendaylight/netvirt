/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yangtools.yang.common.Uint32;

public class IpMonitorStartTask implements Callable<List<ListenableFuture<Void>>> {
    private final MacEntry macEntry;
    private final Uint32 arpMonitorProfileId;
    private final AlivenessMonitorUtils alivenessMonitorUtils;

    public IpMonitorStartTask(MacEntry macEntry, Uint32 profileId, AlivenessMonitorUtils alivenessMonitorUtils) {
        this.macEntry = macEntry;
        this.arpMonitorProfileId = profileId;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        alivenessMonitorUtils.startIpMonitoring(macEntry, arpMonitorProfileId);
        return Collections.emptyList();
    }
}
