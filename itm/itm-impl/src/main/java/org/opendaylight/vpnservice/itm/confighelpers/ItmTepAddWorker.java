/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.util.concurrent.ListenableFuture;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class ItmTepAddWorker implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger logger = LoggerFactory.getLogger(ItmTepAddWorker.class ) ;
    private DataBroker dataBroker;
    private IdManagerService idManagerService;
    private List<DPNTEPsInfo> meshedDpnList;
    private List<DPNTEPsInfo> cfgdDpnList ;
    private IMdsalApiManager mdsalManager;

    public ItmTepAddWorker( List<DPNTEPsInfo> cfgdDpnList,  DataBroker broker, IdManagerService idManagerService, IMdsalApiManager mdsalManager) {
        this.cfgdDpnList = cfgdDpnList ;
        this.dataBroker = broker ;
        this.idManagerService = idManagerService;
        this.mdsalManager = mdsalManager;
        logger.trace("ItmTepAddWorker initialized with  DpnList {}",cfgdDpnList );
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>() ;
        this.meshedDpnList = ItmUtils.getTunnelMeshInfo(dataBroker) ;
        logger.debug("Invoking Internal Tunnel build method with Configured DpnList {} ; Meshed DpnList {} ",cfgdDpnList, meshedDpnList );
        futures.addAll( ItmInternalTunnelAddWorker.build_all_tunnels(dataBroker, idManagerService,mdsalManager, cfgdDpnList, meshedDpnList) ) ;
        // IF EXTERNAL TUNNELS NEEDS TO BE BUILT, DO IT HERE. IT COULD BE TO DC GATEWAY OR TOR SWITCH
        //futures.addAll(ItmExternalTunnelAddWorker.buildTunnelsToExternalEndPoint(dataBroker,meshedDpnList, extIp) ;
        return futures ;
    }

    @Override
    public String toString() {
        return "ItmTepAddWorker  { " +
        "Configured Dpn List : " + cfgdDpnList +
        "  Meshed Dpn List : " + meshedDpnList + " }" ;
    }
}
