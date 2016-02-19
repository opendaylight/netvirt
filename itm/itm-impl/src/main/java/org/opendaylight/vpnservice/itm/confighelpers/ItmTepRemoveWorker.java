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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItmTepRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger logger = LoggerFactory.getLogger(ItmTepRemoveWorker.class ) ;
    private DataBroker dataBroker;
    private List<DPNTEPsInfo> delDpnList ;
    private List<DPNTEPsInfo> meshedDpnList ;
    private IdManagerService idManagerService;
    private IMdsalApiManager mdsalManager;

    public ItmTepRemoveWorker( List<DPNTEPsInfo> delDpnList,  DataBroker broker, IdManagerService idManagerService, IMdsalApiManager mdsalManager) {
        this.delDpnList = delDpnList ;
        this.dataBroker = broker ;
        this.idManagerService = idManagerService;
        this.mdsalManager = mdsalManager;
        logger.trace("ItmTepRemoveWorker initialized with  DpnList {}",delDpnList );
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>() ;
        this.meshedDpnList = ItmUtils.getTunnelMeshInfo(dataBroker) ;
        futures.addAll( ItmInternalTunnelDeleteWorker.deleteTunnels(dataBroker, idManagerService, mdsalManager, delDpnList, meshedDpnList));
        logger.debug("Invoking Internal Tunnel delete method with DpnList to be deleted {} ; Meshed DpnList {} ",delDpnList, meshedDpnList );
        // IF EXTERNAL TUNNELS NEEDS TO BE DELETED, DO IT HERE, IT COULD BE TO DC GATEWAY OR TOR SWITCH
        return futures ;
    }

    @Override
    public String toString() {
        return "ItmTepRemoveWorker  { " +
        "Delete Dpn List : " + delDpnList + " }" ;
    }
}
