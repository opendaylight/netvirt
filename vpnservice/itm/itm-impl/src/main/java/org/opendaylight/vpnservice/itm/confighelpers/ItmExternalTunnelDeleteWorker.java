/*
 * Copyright (c) 2015, 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.DeviceVteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class ItmExternalTunnelDeleteWorker {
    private static final Logger logger = LoggerFactory.getLogger(ItmExternalTunnelDeleteWorker.class ) ;

    public static List<ListenableFuture<Void>> deleteTunnels(DataBroker dataBroker, IdManagerService idManagerService, List<DPNTEPsInfo> dpnTepsList,IpAddress extIp, Class<? extends TunnelTypeBase> tunType ) {
        logger.trace( " Delete Tunnels towards DC Gateway with Ip  {}", extIp ) ;
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

            if (dpnTepsList == null || dpnTepsList.size() == 0) {
                logger.debug("no vtep to delete");
                return null ;
            }
            for( DPNTEPsInfo teps : dpnTepsList) {
                TunnelEndPoints firstEndPt = teps.getTunnelEndPoints().get(0) ;
                    String interfaceName = firstEndPt.getInterfaceName() ;
                    String trunkInterfaceName = ItmUtils.getTrunkInterfaceName( idManagerService, interfaceName,
                                                                                firstEndPt.getIpAddress().getIpv4Address().getValue(),
                                                                                extIp.getIpv4Address().getValue(),
                                                                                tunType.getName());
                    InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkInterfaceName);
                    t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);
                    ItmUtils.itmCache.removeInterface(trunkInterfaceName);

                    InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                            ExternalTunnelList.class)
                                .child(ExternalTunnel.class, ItmUtils.getExternalTunnelKey(extIp.toString(),
                                                                                    teps.getDPNID().toString(),
                                                                                    tunType));
                    t.delete(LogicalDatastoreType.CONFIGURATION, path);
                    logger.debug( "Deleting tunnel towards DC gateway, Tunnel interface name {} ",trunkInterfaceName );
                    ItmUtils.itmCache.removeExternalTunnel(trunkInterfaceName);
                    // Release the Ids for the trunk interface Name
                    ItmUtils.releaseIdForTrunkInterfaceName(idManagerService,interfaceName,firstEndPt.getIpAddress().getIpv4Address().getValue(), extIp.getIpv4Address().getValue(),tunType.getName());
                }
            futures.add(t.submit()) ;
        return futures ;
    }

    public static List<ListenableFuture<Void>> deleteHwVtepsTunnels(DataBroker dataBroker, IdManagerService idManagerService, List<DPNTEPsInfo> delDpnList ,List<HwVtep> cfgdHwVteps) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

        if (delDpnList != null || cfgdHwVteps != null)
            tunnelsDeletion(delDpnList, cfgdHwVteps, idManagerService, futures, t, dataBroker);
            futures.add(t.submit());
        return futures;
    }

    private static void tunnelsDeletion(List<DPNTEPsInfo> cfgdDpnList, List<HwVtep> cfgdhwVteps, IdManagerService idManagerService, List<ListenableFuture<Void>> futures, WriteTransaction t, DataBroker dataBroker) {
        if (cfgdDpnList != null && !cfgdDpnList.isEmpty()) {
        for (DPNTEPsInfo dpn : cfgdDpnList) {
            if (dpn.getTunnelEndPoints() != null && !dpn.getTunnelEndPoints().isEmpty())
                for (TunnelEndPoints srcTep : dpn.getTunnelEndPoints()) {
                        InstanceIdentifier<TransportZone> tzonePath = InstanceIdentifier.builder(TransportZones.class)
                                        .child(TransportZone.class, new TransportZoneKey((srcTep.getTransportZone())))
                                        .build();
                    Optional<TransportZone> tZoneOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, tzonePath, dataBroker);
                    if (tZoneOptional.isPresent()) {
                        TransportZone tZone = tZoneOptional.get();
                        //do we need to check tunnel type?
                        if (tZone.getSubnets() != null && !tZone.getSubnets().isEmpty()) {
                            for (Subnets sub : tZone.getSubnets()) {
                                if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                                    for (DeviceVteps hwVtepDS : sub.getDeviceVteps()) {
                                        String cssID = dpn.getDPNID().toString();
                                        //CSS-TOR-CSS
                                            deleteTrunksCSSTOR(dataBroker, idManagerService, dpn.getDPNID(), srcTep.getInterfaceName(), srcTep.getIpAddress(),
                                                            hwVtepDS.getTopologyId(), hwVtepDS.getNodeId(), hwVtepDS.getIpAddress(), tZone.getTunnelType(),
                                                            t, futures);
                                        }
                                    }
                                }
                            }
                            if (cfgdhwVteps != null && !cfgdhwVteps.isEmpty()) {
                                for (HwVtep hwVtep : cfgdhwVteps) {
                                    deleteTrunksCSSTOR(dataBroker, idManagerService, dpn.getDPNID(), srcTep.getInterfaceName(), srcTep.getIpAddress(),
                                                    hwVtep.getTopo_id(), hwVtep.getNode_id(), hwVtep.getHwIp(),
                                                    TunnelTypeVxlan.class, t, futures);
                                }
                            }
                        }
                    }
            }
        }
            if (cfgdhwVteps != null && !cfgdhwVteps.isEmpty()) {
                for (HwVtep hwTep : cfgdhwVteps) {
                    logger.trace("processing hwTep from list {}", hwTep);
                    for (HwVtep hwTepRemote : cfgdhwVteps) {
                        if (!hwTep.getHwIp().equals(hwTepRemote.getHwIp())) {
                            deleteTrunksTORTOR(dataBroker, idManagerService, hwTep.getTopo_id(), hwTep.getNode_id(),
                                    hwTep.getHwIp(), hwTepRemote.getTopo_id(), hwTepRemote.getNode_id(),
                                    hwTepRemote.getHwIp(), TunnelTypeVxlan.class, t, futures);
                        }
                    }
                    InstanceIdentifier<TransportZone> tzonePath = InstanceIdentifier.builder(TransportZones.class)
                            .child(TransportZone.class, new TransportZoneKey((hwTep.getTransportZone()))).build();
                    Optional<TransportZone> tZoneOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, tzonePath, dataBroker);
                    if (tZoneOptional.isPresent()) {
                        TransportZone tZone = tZoneOptional.get();
                        logger.trace("subnets under tz {} are {}", tZone.getZoneName(), tZone.getSubnets());
                        if (tZone.getSubnets() != null && !tZone.getSubnets().isEmpty()) {
                            for (Subnets sub : tZone.getSubnets()) {
                                if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                                    for (DeviceVteps hwVtepDS : sub.getDeviceVteps()) {
                                        logger.trace("hwtepDS exists {}", hwVtepDS);
                                        if (hwVtepDS.getIpAddress().equals(hwTep.getHwIp()))
                                            continue;//dont delete tunnels with self
                                        logger.trace("deleting tor-tor {} and {}", hwTep, hwVtepDS);
                                        deleteTrunksTORTOR(dataBroker, idManagerService, hwTep.getTopo_id(), hwTep.getNode_id(),
                                                hwTep.getHwIp(), hwVtepDS.getTopologyId(), hwVtepDS.getNodeId(),
                                                hwVtepDS.getIpAddress(), tZone.getTunnelType(),
                                                t, futures);

                                    }
                                }
                                if (sub.getVteps() != null && !sub.getVteps().isEmpty()) {
                                    for (Vteps vtep : sub.getVteps()) {
                                        logger.trace("deleting tor-css-tor {} and {}", hwTep, vtep);
                                        String parentIf = ItmUtils.getInterfaceName(vtep.getDpnId(), vtep.getPortname(), sub.getVlanId());
                                        deleteTrunksCSSTOR(dataBroker, idManagerService, vtep.getDpnId(), parentIf, vtep.getIpAddress(),
                                                hwTep.getTopo_id(), hwTep.getNode_id(), hwTep.getHwIp(),
                                                tZone.getTunnelType(), t, futures);
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }





                //do we need to check tunnel type?

                                //do i need to check node-id?
                                //for mlag case and non-m-lag case, isnt it enough to just check ipaddress?
                                //TOR-TOR

                                //TOR-CSS



    private static void deleteTrunksCSSTOR(DataBroker dataBroker, IdManagerService idManagerService, BigInteger dpnid,
                    String interfaceName, IpAddress cssIpAddress, String topologyId, String nodeId, IpAddress hWIpAddress,
                                           Class<? extends TunnelTypeBase> tunType, WriteTransaction t,
                                           List<ListenableFuture<Void>> futures) {
        //CSS-TOR
        if (trunkExists(dpnid.toString(), nodeId, tunType, dataBroker)) {
            logger.trace("deleting tunnel from {} to {} ", dpnid.toString(), nodeId);
            String parentIf = interfaceName;
            String fwdTrunkIf = ItmUtils.getTrunkInterfaceName(idManagerService,parentIf,cssIpAddress.getIpv4Address().getValue(),
                            hWIpAddress.getIpv4Address().getValue(), tunType.getName());
            InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(fwdTrunkIf);
            t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);

            InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                            ExternalTunnelList.class)
                            .child(ExternalTunnel.class, ItmUtils.getExternalTunnelKey(nodeId, dpnid.toString(), tunType));
            t.delete(LogicalDatastoreType.CONFIGURATION, path);
        }
        else {
            logger.trace(" trunk from {} to {} already deleted",dpnid.toString(), nodeId);
        }
        //TOR-CSS
        if (trunkExists( nodeId, dpnid.toString(), tunType, dataBroker)) {
            logger.trace("deleting tunnel from {} to {} ",nodeId, dpnid.toString());

            String parentIf = ItmUtils.getHwParentIf(topologyId,nodeId);
            String revTrunkIf = ItmUtils.getTrunkInterfaceName(idManagerService,parentIf, hWIpAddress.getIpv4Address().getValue(),
                            cssIpAddress.getIpv4Address().getValue(), tunType.getName());
            InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(revTrunkIf);
            t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);

            InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                            ExternalTunnelList.class)
                            .child(ExternalTunnel.class, ItmUtils.getExternalTunnelKey(dpnid.toString(),nodeId, tunType));
            t.delete(LogicalDatastoreType.CONFIGURATION, path);
        }
        else {
            logger.trace(" trunk from {} to {} already deleted",  nodeId, dpnid.toString());
        }
    }

    private static void deleteTrunksTORTOR(DataBroker dataBroker, IdManagerService idManagerService,
                    String topologyId1, String nodeId1, IpAddress hWIpAddress1, String topologyId2, String nodeId2, IpAddress hWIpAddress2,
                                           Class<? extends TunnelTypeBase> tunType, WriteTransaction t, List<ListenableFuture<Void>> futures) {
        //TOR1-TOR2
        if (trunkExists(nodeId1, nodeId2, tunType, dataBroker)) {
            logger.trace("deleting tunnel from {} to {} ", nodeId1, nodeId2);
            String parentIf = ItmUtils.getHwParentIf(topologyId1,nodeId1);
            String fwdTrunkIf = ItmUtils.getTrunkInterfaceName(idManagerService, parentIf,
                                                                hWIpAddress1.getIpv4Address().getValue(),
                                                                hWIpAddress2.getIpv4Address().getValue(),
                                                                tunType.getName());
            InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(fwdTrunkIf);
            t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);

            InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                            ExternalTunnelList.class)
                            .child(ExternalTunnel.class, ItmUtils.getExternalTunnelKey(nodeId2, nodeId1, tunType));
            t.delete(LogicalDatastoreType.CONFIGURATION, path);
        }
        else {
            logger.trace(" trunk from {} to {} already deleted",nodeId1, nodeId2);
        }
        //TOR2-TOR1
        if (trunkExists( nodeId2, nodeId1, tunType, dataBroker)) {
            logger.trace("deleting tunnel from {} to {} ",nodeId2, nodeId1);

            String parentIf = ItmUtils.getHwParentIf(topologyId2,nodeId2);
            String revTrunkIf = ItmUtils.getTrunkInterfaceName(idManagerService,parentIf, hWIpAddress2.getIpv4Address().getValue(),
                                                                hWIpAddress1.getIpv4Address().getValue(), tunType.getName());
            InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(revTrunkIf);
            t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);

            InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                            ExternalTunnelList.class)
                            .child(ExternalTunnel.class, ItmUtils.getExternalTunnelKey(nodeId1,nodeId2, tunType));
            t.delete(LogicalDatastoreType.CONFIGURATION, path);
        }
        else {
            logger.trace(" trunk from {} to {} already deleted",nodeId2, nodeId1);
        }
    }

    private static boolean trunkExists( String srcDpnOrNode,  String dstDpnOrNode,
                                        Class<? extends TunnelTypeBase> tunType,DataBroker dataBroker) {
        boolean existsFlag = false ;
        InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                        ExternalTunnelList.class)
                        .child(ExternalTunnel.class, ItmUtils.getExternalTunnelKey(dstDpnOrNode, srcDpnOrNode, tunType));
        Optional<ExternalTunnel> exTunnels = ItmUtils.read(LogicalDatastoreType.CONFIGURATION,path, dataBroker) ;
        if( exTunnels.isPresent()) {
            existsFlag = true ;
    }
        return existsFlag ;
    }


}
