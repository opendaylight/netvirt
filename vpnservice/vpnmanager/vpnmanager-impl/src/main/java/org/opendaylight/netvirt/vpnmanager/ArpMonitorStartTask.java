/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.util.concurrent.ListenableFuture;

public class ArpMonitorStartTask implements Callable<List<ListenableFuture<Void>>> {
    private MacEntry macEntry;
    private Long profileId;
    private DataBroker databroker;
    private AlivenessMonitorService alivenessManager;
    private OdlInterfaceRpcService interfaceRpc;
    private static final Logger LOG = LoggerFactory.getLogger(ArpMonitorStartTask.class);

    public ArpMonitorStartTask(MacEntry macEntry, Long profileId, DataBroker databroker,AlivenessMonitorService alivenessManager, OdlInterfaceRpcService interfaceRpc) {
        super();
        this.macEntry = macEntry;
        this.profileId = profileId;
        this.databroker = databroker;
        this.alivenessManager = alivenessManager;
        this.interfaceRpc = interfaceRpc;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        AlivenessMonitorUtils.startArpMonitoring(macEntry, profileId, alivenessManager, databroker, interfaceRpc);
        return futures;
    }
}