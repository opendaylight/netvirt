/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;

public class IpMonitorStartTask implements Callable<List<ListenableFuture<Void>>> {
    private MacEntry macEntry;
    private Long ipMonitorProfileId;
    private DataBroker databroker;
    private AlivenessMonitorService alivenessManager;
    private INeutronVpnManager neutronVpnService;
    private IInterfaceManager interfaceManager;

    public IpMonitorStartTask(MacEntry macEntry, Long profileId, DataBroker databroker,
            AlivenessMonitorService alivenessManager,
            INeutronVpnManager neutronVpnService, IInterfaceManager interfaceManager) {
        this.macEntry = macEntry;
        this.ipMonitorProfileId = profileId;
        this.databroker = databroker;
        this.alivenessManager = alivenessManager;
        this.neutronVpnService = neutronVpnService;
        this.interfaceManager = interfaceManager;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        AlivenessMonitorUtils.startIpMonitoring(macEntry, ipMonitorProfileId,
            alivenessManager, databroker, neutronVpnService,
            interfaceManager);
        return null;
    }
}