/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnelKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

public class ItmInternalTunnelDeleteWorker {
   private static final Logger logger = LoggerFactory.getLogger(ItmInternalTunnelDeleteWorker.class) ;

    public static List<ListenableFuture<Void>> deleteTunnels(DataBroker dataBroker, IdManagerService idManagerService,IMdsalApiManager mdsalManager,
                                                             List<DPNTEPsInfo> dpnTepsList, List<DPNTEPsInfo> meshedDpnList)
    {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        try {
            if (dpnTepsList == null || dpnTepsList.size() == 0) {
                logger.debug("no vtep to delete");
                return null ;
            }

            if (meshedDpnList == null || meshedDpnList.size() == 0) {
                logger.debug("No Meshed Vteps");
                return null ;
            }
            for (DPNTEPsInfo srcDpn : dpnTepsList) {
                logger.trace("Processing srcDpn " + srcDpn);
                for (TunnelEndPoints srcTep : srcDpn.getTunnelEndPoints()) {
                    logger.trace("Processing srcTep " + srcTep);
                    String srcTZone = srcTep.getTransportZone();

                    // run through all other DPNS other than srcDpn
                    for (DPNTEPsInfo dstDpn : meshedDpnList) {
                        if (!(srcDpn.getDPNID().equals(dstDpn.getDPNID()))) {
                            for (TunnelEndPoints dstTep : dstDpn.getTunnelEndPoints()) {
                                logger.trace("Processing dstTep " + dstTep);
                                if (dstTep.getTransportZone().equals(srcTZone)) {
                                    // remove all trunk interfaces
                                    logger.trace("Invoking removeTrunkInterface between source TEP {} , Destination TEP {} " ,srcTep , dstTep);
                                    removeTrunkInterface(dataBroker, idManagerService, srcTep, dstTep, srcDpn.getDPNID(), dstDpn.getDPNID(), t, futures);
                                }
                            }
                        }
                    }

                    // removing vtep / dpn from Tunnels OpDs.
                    InstanceIdentifier<TunnelEndPoints> tepPath =
                                    InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class, srcDpn.getKey())
                                                    .child(TunnelEndPoints.class, srcTep.getKey()).build();

                    logger.trace("Tep Removal from DPNTEPSINFO CONFIG DS " + srcTep);
                    t.delete(LogicalDatastoreType.CONFIGURATION, tepPath);
                    InstanceIdentifier<DPNTEPsInfo> dpnPath =
                                    InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class, srcDpn.getKey())
                                                    .build();
                    Optional<DPNTEPsInfo> dpnOptional =
                                    ItmUtils.read(LogicalDatastoreType.CONFIGURATION, dpnPath, dataBroker);
                    if (dpnOptional.isPresent()) {
                        DPNTEPsInfo dpnRead = dpnOptional.get();
                        // remove dpn if no vteps exist on dpn
                        if (dpnRead.getTunnelEndPoints() == null || dpnRead.getTunnelEndPoints().size() == 0) {
                            logger.debug( "Removing Terminating Service Table Flow ") ;
                           ItmUtils.setUpOrRemoveTerminatingServiceTable(dpnRead.getDPNID(), mdsalManager,false);
                            logger.trace("DPN Removal from DPNTEPSINFO CONFIG DS " + dpnRead);
                            t.delete(LogicalDatastoreType.CONFIGURATION, dpnPath);
                            InstanceIdentifier<DpnEndpoints> tnlContainerPath =
                                            InstanceIdentifier.builder(DpnEndpoints.class).build();
                            Optional<DpnEndpoints> containerOptional =
                                            ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                                                            tnlContainerPath, dataBroker);
                            // remove container if no DPNs are present
                            if (containerOptional.isPresent()) {
                            	DpnEndpoints deps = containerOptional.get();
                                if (deps.getDPNTEPsInfo() == null || deps.getDPNTEPsInfo().isEmpty()) {
                                    logger.trace("Container Removal from DPNTEPSINFO CONFIG DS");
                                    t.delete(LogicalDatastoreType.CONFIGURATION, tnlContainerPath);
                                }
                            }
                        }
                    }
                }
            }
            futures.add( t.submit() );
        } catch (Exception e1) {
            logger.error("exception while deleting tep", e1);
        }
        return futures ;
    }

    private static void removeTrunkInterface(DataBroker dataBroker, IdManagerService idManagerService,
                                             TunnelEndPoints srcTep, TunnelEndPoints dstTep, BigInteger srcDpnId, BigInteger dstDpnId,
                                             WriteTransaction t, List<ListenableFuture<Void>> futures) {
        String trunkfwdIfName =
                        ItmUtils.getTrunkInterfaceName(idManagerService, srcTep.getInterfaceName(), srcTep.getIpAddress()
                                        .getIpv4Address().getValue(), dstTep.getIpAddress().getIpv4Address()
                                        .getValue());
        logger.trace("Removing forward Trunk Interface " + trunkfwdIfName);
        InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkfwdIfName);
        logger.debug(  " Removing Trunk Interface Name - {} , Id - {} from Config DS {}, {} ", trunkfwdIfName, trunkIdentifier ) ;
        t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);

        // also update itm-state ds -- Delete the forward tunnel-interface from the tunnel list
        InstanceIdentifier<InternalTunnel> path = InstanceIdentifier.create(
                TunnelList.class)
                    .child(InternalTunnel.class, new InternalTunnelKey( srcDpnId, dstDpnId));   
        t.delete(LogicalDatastoreType.CONFIGURATION,path) ;
        // Release the Ids for the forward trunk interface Name
        ItmUtils.releaseIdForTrunkInterfaceName(idManagerService,srcTep.getInterfaceName(), srcTep.getIpAddress()
                .getIpv4Address().getValue(), dstTep.getIpAddress().getIpv4Address()
                .getValue() );

        String trunkRevIfName =
                        ItmUtils.getTrunkInterfaceName(idManagerService, dstTep.getInterfaceName(), dstTep.getIpAddress()
                                        .getIpv4Address().getValue(), srcTep.getIpAddress().getIpv4Address()
                                        .getValue());
        logger.trace("Removing Reverse Trunk Interface " + trunkRevIfName);
        trunkIdentifier = ItmUtils.buildId(trunkfwdIfName);
        logger.debug(  " Removing Trunk Interface Name - {} , Id - {} from Config DS {}, {} ", trunkfwdIfName, trunkIdentifier ) ;
        t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);

     // also update itm-state ds -- Delete the reverse tunnel-interface from the tunnel list
        path = InstanceIdentifier.create(
                TunnelList.class)
                    .child(InternalTunnel.class, new InternalTunnelKey(dstDpnId, srcDpnId));   
        t.delete(LogicalDatastoreType.CONFIGURATION,path) ;
        
     // Release the Ids for the reverse trunk interface Name
        ItmUtils.releaseIdForTrunkInterfaceName(idManagerService, dstTep.getInterfaceName(), dstTep.getIpAddress()
                .getIpv4Address().getValue(), srcTep.getIpAddress().getIpv4Address()
                .getValue());
    }
}
