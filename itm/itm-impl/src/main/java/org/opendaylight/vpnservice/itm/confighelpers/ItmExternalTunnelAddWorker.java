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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.net.util.SubnetUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

public class ItmExternalTunnelAddWorker {
    private static final Logger logger = LoggerFactory.getLogger(ItmExternalTunnelAddWorker.class ) ;

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    logger.debug("Success in Datastore operation");
                }

                public void onFailure(Throwable error) {
                    logger.error("Error in Datastore operation", error);
                };
            };

    public static List<ListenableFuture<Void>> buildTunnelsToExternalEndPoint(DataBroker dataBroker, IdManagerService idManagerService,
                                                                              List<DPNTEPsInfo> cfgDpnList, IpAddress extIp, Class<? extends TunnelTypeBase> tunType) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
       if( null != cfgDpnList) {
          for( DPNTEPsInfo teps : cfgDpnList ) {
             // CHECK -- Assumption -- Only one End Point / Dpn for GRE/Vxlan Tunnels
              TunnelEndPoints firstEndPt = teps.getTunnelEndPoints().get(0) ;
              String interfaceName = firstEndPt.getInterfaceName() ;
              String trunkInterfaceName = ItmUtils.getTrunkInterfaceName(idManagerService, interfaceName, firstEndPt.getIpAddress().getIpv4Address().getValue(), extIp.getIpv4Address().getValue()) ;
              char[] subnetMaskArray = firstEndPt.getSubnetMask().getValue() ;
              String subnetMaskStr = String.valueOf(subnetMaskArray) ;
              SubnetUtils utils = new SubnetUtils(subnetMaskStr);
              String dcGwyIpStr = String.valueOf(extIp.getValue());
              IpAddress gwyIpAddress = (utils.getInfo().isInRange(dcGwyIpStr) ) ? null : firstEndPt.getGwIpAddress() ;
              String ifDescription = tunType.getName();
              logger.debug(  " Creating Trunk Interface with parameters trunk I/f Name - {}, parent I/f name - {}, source IP - {}, DC Gateway IP - {} gateway IP - {}",trunkInterfaceName, interfaceName, firstEndPt.getIpAddress(), extIp, gwyIpAddress ) ;
              Interface iface = ItmUtils.buildTunnelInterface(teps.getDPNID(), trunkInterfaceName, String.format( "%s %s",ifDescription, "Trunk Interface"), true, tunType, firstEndPt.getIpAddress(), extIp, gwyIpAddress, false) ;
              logger.debug(  " Trunk Interface builder - {} ", iface ) ;
              InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkInterfaceName);
              logger.debug(  " Trunk Interface Identifier - {} ", trunkIdentifier ) ;
              logger.trace(  " Writing Trunk Interface to Config DS {}, {} ", trunkIdentifier, iface ) ;
              t.merge(LogicalDatastoreType.CONFIGURATION, trunkIdentifier, iface, true);
              // update external_tunnel_list ds  
              InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                      ExternalTunnelList.class)
                          .child(ExternalTunnel.class, new ExternalTunnelKey(extIp, teps.getDPNID()));
              ExternalTunnel tnl = ItmUtils.buildExternalTunnel(teps.getDPNID(), extIp, trunkInterfaceName);
              t.merge(LogicalDatastoreType.CONFIGURATION,path, tnl, true) ;
          }
          futures.add( t.submit()) ;
       }
        return futures ;
    }

    public static List<ListenableFuture<Void>> buildTunnelsFromDpnToExternalEndPoint(DataBroker dataBroker, IdManagerService idManagerService,
                                                                                     List<BigInteger> dpnId, IpAddress extIp, Class<? extends TunnelTypeBase> tunType) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        List<DPNTEPsInfo> cfgDpnList =( dpnId == null ) ? ItmUtils.getTunnelMeshInfo(dataBroker) :ItmUtils.getDPNTEPListFromDPNId(dataBroker, dpnId) ;
          futures = buildTunnelsToExternalEndPoint( dataBroker, idManagerService, cfgDpnList, extIp, tunType) ;
        return futures ;
    }
}
