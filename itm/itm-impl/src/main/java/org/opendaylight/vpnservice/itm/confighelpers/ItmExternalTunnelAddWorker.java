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
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.DeviceVteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.*;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.net.util.SubnetUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

public class ItmExternalTunnelAddWorker {
    private static final Logger logger = LoggerFactory.getLogger(ItmExternalTunnelAddWorker.class);

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    logger.debug("Success in Datastore operation");
                }

                public void onFailure(Throwable error) {
                    logger.error("Error in Datastore operation", error);
                }

                ;
            };

    public static List<ListenableFuture<Void>> buildTunnelsToExternalEndPoint(DataBroker dataBroker, IdManagerService idManagerService,
                                                                              List<DPNTEPsInfo> cfgDpnList, IpAddress extIp, Class<? extends TunnelTypeBase> tunType) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        if (null != cfgDpnList) {
            for (DPNTEPsInfo teps : cfgDpnList) {
                // CHECK -- Assumption -- Only one End Point / Dpn for GRE/Vxlan Tunnels
                TunnelEndPoints firstEndPt = teps.getTunnelEndPoints().get(0);
                String interfaceName = firstEndPt.getInterfaceName();
                String trunkInterfaceName = ItmUtils.getTrunkInterfaceName(idManagerService, interfaceName, firstEndPt.getIpAddress().getIpv4Address().getValue(), extIp.getIpv4Address().getValue());
                char[] subnetMaskArray = firstEndPt.getSubnetMask().getValue();
                String subnetMaskStr = String.valueOf(subnetMaskArray);
                SubnetUtils utils = new SubnetUtils(subnetMaskStr);
                String dcGwyIpStr = String.valueOf(extIp.getValue());
                IpAddress gwyIpAddress = (utils.getInfo().isInRange(dcGwyIpStr)) ? null : firstEndPt.getGwIpAddress();
                String ifDescription = tunType.getName();
                logger.debug(" Creating Trunk Interface with parameters trunk I/f Name - {}, parent I/f name - {}, source IP - {}, DC Gateway IP - {} gateway IP - {}", trunkInterfaceName, interfaceName, firstEndPt.getIpAddress(), extIp, gwyIpAddress);
                Interface iface = ItmUtils.buildTunnelInterface(teps.getDPNID(), trunkInterfaceName, String.format("%s %s", ifDescription, "Trunk Interface"), true, tunType, firstEndPt.getIpAddress(), extIp, gwyIpAddress, firstEndPt.getVLANID(), false,false,null);
                logger.debug(" Trunk Interface builder - {} ", iface);
                InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkInterfaceName);
                logger.debug(" Trunk Interface Identifier - {} ", trunkIdentifier);
                logger.trace(" Writing Trunk Interface to Config DS {}, {} ", trunkIdentifier, iface);
                t.merge(LogicalDatastoreType.CONFIGURATION, trunkIdentifier, iface, true);
                // update external_tunnel_list ds
                InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                        ExternalTunnelList.class)
                        .child(ExternalTunnel.class, new ExternalTunnelKey(extIp.toString(), teps.getDPNID().toString()));
                ExternalTunnel tnl = ItmUtils.buildExternalTunnel(teps.getDPNID().toString(), extIp.toString(), trunkInterfaceName);
                t.merge(LogicalDatastoreType.CONFIGURATION, path, tnl, true);
            }
            futures.add(t.submit());
        }
        return futures;
    }

    public static List<ListenableFuture<Void>> buildTunnelsFromDpnToExternalEndPoint(DataBroker dataBroker, IdManagerService idManagerService,
                                                                                     List<BigInteger> dpnId, IpAddress extIp, Class<? extends TunnelTypeBase> tunType) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        List<DPNTEPsInfo> cfgDpnList = (dpnId == null) ? ItmUtils.getTunnelMeshInfo(dataBroker) : ItmUtils.getDPNTEPListFromDPNId(dataBroker, dpnId);
        futures = buildTunnelsToExternalEndPoint(dataBroker, idManagerService, cfgDpnList, extIp, tunType);
        return futures;
    }

    public static List<ListenableFuture<Void>> buildHwVtepsTunnels(DataBroker dataBroker, IdManagerService idManagerService, List<DPNTEPsInfo> cfgdDpnList, List<HwVtep> cfgdHwVteps) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        if (null != cfgdDpnList && !cfgdDpnList.isEmpty()) {
            logger.trace("calling tunnels from css {}",cfgdDpnList);
            tunnelsFromCSS(cfgdDpnList, idManagerService , futures, t , dataBroker);

        }
        if (null != cfgdHwVteps && !cfgdHwVteps.isEmpty() ) {
            logger.trace("calling tunnels from hwTep {}",cfgdHwVteps);
            tunnelsFromhWVtep(cfgdHwVteps, idManagerService, futures, t, dataBroker);
        }

        if ((cfgdDpnList != null && !cfgdDpnList.isEmpty()) || (cfgdHwVteps != null && !cfgdHwVteps.isEmpty()))
            futures.add(t.submit());
        return futures;
    }

    private static void tunnelsFromCSS(List<DPNTEPsInfo> cfgdDpnList, IdManagerService idManagerService, List<ListenableFuture<Void>> futures, WriteTransaction t, DataBroker dataBroker) {
        for (DPNTEPsInfo dpn : cfgdDpnList) {
            logger.trace("processing dpn {}" , dpn);
            if (dpn.getTunnelEndPoints() != null && !dpn.getTunnelEndPoints().isEmpty())
                for (TunnelEndPoints tep : dpn.getTunnelEndPoints()) {
                    InstanceIdentifier<TransportZone> tzonePath = InstanceIdentifier.builder(TransportZones.class).child(TransportZone.class, new TransportZoneKey((tep.getTransportZone()))).build();
                    Optional<TransportZone> tZoneOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, tzonePath, dataBroker);
                    if (tZoneOptional.isPresent()) {
                        TransportZone tZone = tZoneOptional.get();
                        //do we need to check tunnel type?
                        if (tZone.getSubnets() != null && !tZone.getSubnets().isEmpty()) {
                            for (Subnets sub : tZone.getSubnets()) {
                                if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                                    for (DeviceVteps hwVtepDS : sub.getDeviceVteps()) {
                                        String cssID = dpn.getDPNID().toString();
                                        String nodeId = hwVtepDS.getNodeId();
                                        //CSS-TOR
                                        logger.trace("wire up {} and {}",tep, hwVtepDS);
                                        if (!wireUp(dpn.getDPNID(), tep.getPortname(), sub.getVlanId(), tep.getIpAddress(), nodeId, hwVtepDS.getIpAddress(), tep.getSubnetMask(),
                                                sub.getGatewayIp(), sub.getPrefix(), tZone.getTunnelType(), idManagerService, dataBroker, futures, t))
                                            logger.error("Unable to build tunnel {} -- {}", tep.getIpAddress(), hwVtepDS.getIpAddress());
                                        //TOR-CSS
                                        logger.trace("wire up {} and {}", hwVtepDS,tep);
                                        if (!wireUp(hwVtepDS.getTopologyId(), hwVtepDS.getNodeId(), hwVtepDS.getIpAddress(), cssID, tep.getIpAddress(), sub.getPrefix(),
                                                sub.getGatewayIp(), tep.getSubnetMask(), tZone.getTunnelType(), idManagerService, dataBroker, futures, t))
                                            logger.error("Unable to build tunnel {} -- {}", hwVtepDS.getIpAddress(), tep.getIpAddress());

                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private static void tunnelsFromhWVtep(List<HwVtep> cfgdHwVteps, IdManagerService idManagerService, List<ListenableFuture<Void>> futures, WriteTransaction t, DataBroker dataBroker) {
        for (HwVtep hwTep : cfgdHwVteps) {
            InstanceIdentifier<TransportZone> tzonePath = InstanceIdentifier.builder(TransportZones.class).child(TransportZone.class, new TransportZoneKey((hwTep.getTransportZone()))).build();
            Optional<TransportZone> tZoneOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, tzonePath, dataBroker);
            if (tZoneOptional.isPresent()) {
                TransportZone tZone = tZoneOptional.get();
                //do we need to check tunnel type?
                if (tZone.getSubnets() != null && !tZone.getSubnets().isEmpty()) {
                    for (Subnets sub : tZone.getSubnets()) {
                        if (sub.getDeviceVteps() != null && !sub.getDeviceVteps().isEmpty()) {
                            for (DeviceVteps hwVtepDS : sub.getDeviceVteps()) {
                                if (hwVtepDS.getIpAddress().equals(hwTep.getHwIp()))
                                    continue;//dont mesh with self
                                //TOR1-TOR2
                                logger.trace("wire up {} and {}",hwTep, hwVtepDS);
                                if (!wireUp(hwTep.getTopo_id(), hwTep.getNode_id(), hwTep.getHwIp(), hwVtepDS.getNodeId(), hwVtepDS.getIpAddress(),
                                        hwTep.getIpPrefix(), hwTep.getGatewayIP(), sub.getPrefix(), tZone.getTunnelType(), idManagerService, dataBroker, futures, t))
                                    logger.error("Unable to build tunnel {} -- {}", hwTep.getHwIp(), hwVtepDS.getIpAddress());
                                //TOR2-TOR1
                                logger.trace("wire up {} and {}", hwVtepDS,hwTep);
                                if (!wireUp(hwTep.getTopo_id(), hwVtepDS.getNodeId(), hwVtepDS.getIpAddress(), hwTep.getNode_id(), hwTep.getHwIp(),
                                        sub.getPrefix(), sub.getGatewayIp(), hwTep.getIpPrefix(), tZone.getTunnelType(), idManagerService, dataBroker, futures, t))
                                    logger.error("Unable to build tunnel {} -- {}", hwVtepDS.getIpAddress(), hwTep.getHwIp());
                            }
                        }
                        if (sub.getVteps() != null && !sub.getVteps().isEmpty()) {
                            for (Vteps vtep : sub.getVteps()) {
                                //TOR-CSS
                                String cssID = vtep.getDpnId().toString();
                                logger.trace("wire up {} and {}",hwTep, vtep);
                                if(!wireUp(hwTep.getTopo_id(), hwTep.getNode_id(), hwTep.getHwIp(), cssID, vtep.getIpAddress(), hwTep.getIpPrefix(),
                                        hwTep.getGatewayIP(), sub.getPrefix(), tZone.getTunnelType(),idManagerService, dataBroker, futures, t ))
                                    logger.error("Unable to build tunnel {} -- {}", hwTep.getHwIp(), vtep.getIpAddress());
                                    //CSS-TOR
                                logger.trace("wire up {} and {}", vtep,hwTep);
                                if(!wireUp(vtep.getDpnId(), vtep.getPortname(), sub.getVlanId(), vtep.getIpAddress(),
                                                hwTep.getNode_id(),hwTep.getHwIp(),sub.getPrefix(), sub.getGatewayIp(),hwTep.getIpPrefix(),tZone.getTunnelType(),idManagerService, dataBroker, futures, t ));

                            }

                        }
                    }
                }
            }
        }
    }

    //for tunnels from TOR device
    private static boolean wireUp(String topo_id, String srcNodeid, IpAddress srcIp, String dstNodeId, IpAddress dstIp, IpPrefix srcSubnet,
                                  IpAddress gWIp, IpPrefix dstSubnet, Class<? extends TunnelTypeBase> tunType, IdManagerService idManagerService, DataBroker dataBroker, List<ListenableFuture<Void>> futures, WriteTransaction t) {
        IpAddress gwyIpAddress = (srcSubnet.equals(dstSubnet)) ? null : gWIp;
        String parentIf =  ItmUtils.getHwParentIf(topo_id, srcNodeid);
        String tunnelIfName = ItmUtils.getTrunkInterfaceName(idManagerService, parentIf, srcIp.getIpv4Address().getValue(), dstIp.getIpv4Address().getValue());
        logger.debug(" Creating ExternalTrunk Interface with parameters Name - {}, parent I/f name - {}, source IP - {}, destination IP - {} gateway IP - {}", tunnelIfName, parentIf, srcIp, dstIp, gwyIpAddress);
        Interface hwTunnelIf = ItmUtils.buildHwTunnelInterface(tunnelIfName, String.format("%s %s", tunType.getName(), "Trunk Interface"),
                true, topo_id, srcNodeid, tunType, srcIp, dstIp, gwyIpAddress, true);
        InstanceIdentifier<Interface> ifIID = InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(tunnelIfName)).build();
        logger.trace(" Writing Trunk Interface to Config DS {}, {} ", ifIID, hwTunnelIf);
        t.merge(LogicalDatastoreType.CONFIGURATION, ifIID, hwTunnelIf, true);
        // also update itm-state ds?
        InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(getExternalTunnelKey(dstNodeId), getExternalTunnelKey(srcNodeid)));
        ExternalTunnel tnl = ItmUtils.buildExternalTunnel(getExternalTunnelKey(srcNodeid), getExternalTunnelKey(dstNodeId), tunnelIfName);
        t.merge(LogicalDatastoreType.CONFIGURATION, path, tnl, true);
        return true;
    }

    //for tunnels from CSS
    private static boolean wireUp(BigInteger dpnId,String portname, Integer vlanId, IpAddress srcIp, String dstNodeId, IpAddress dstIp, IpPrefix srcSubnet,
                                  IpAddress gWIp, IpPrefix dstSubnet, Class<? extends TunnelTypeBase> tunType, IdManagerService idManagerService, DataBroker dataBroker, List<ListenableFuture<Void>> futures, WriteTransaction t) {
        IpAddress gwyIpAddress = (srcSubnet.equals(dstSubnet)) ? null : gWIp;
        String parentIf = ItmUtils.getInterfaceName(dpnId, portname, vlanId);
        String tunnelIfName = ItmUtils.getTrunkInterfaceName(idManagerService, parentIf, srcIp.getIpv4Address().getValue(), dstIp.getIpv4Address().getValue());
        logger.debug(" Creating ExternalTrunk Interface with parameters Name - {}, parent I/f name - {}, source IP - {}, destination IP - {} gateway IP - {}", tunnelIfName, parentIf, srcIp, dstIp, gwyIpAddress);
        Interface extTunnelIf = ItmUtils.buildTunnelInterface(dpnId, tunnelIfName, String.format("%s %s", tunType.getName(), "Trunk Interface"), true, tunType, srcIp, dstIp, gwyIpAddress, vlanId, false, true, 5L);
        InstanceIdentifier<Interface> ifIID = InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(tunnelIfName)).build();
        logger.trace(" Writing Trunk Interface to Config DS {}, {} ", ifIID, extTunnelIf);
        t.merge(LogicalDatastoreType.CONFIGURATION, ifIID, extTunnelIf, true);
        InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(getExternalTunnelKey(dstNodeId), dpnId.toString()));
        ExternalTunnel tnl = ItmUtils.buildExternalTunnel(dpnId.toString(), getExternalTunnelKey(dstNodeId), tunnelIfName);
        t.merge(LogicalDatastoreType.CONFIGURATION, path, tnl, true);
        return true;
    }
    
    static String getExternalTunnelKey(String nodeid) {
        if (nodeid.indexOf("physicalswitch") > 0) {
            nodeid = nodeid.substring(0, nodeid.indexOf("physicalswitch") - 1);
        }
        return nodeid;
    }
}

