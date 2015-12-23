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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.net.util.SubnetUtils;

import com.google.common.util.concurrent.ListenableFuture;

public class ItmExternalTunnelAddWorker {
    private static final Logger logger = LoggerFactory.getLogger(ItmExternalTunnelAddWorker.class ) ;

    public static List<ListenableFuture<Void>> buildTunnelsToExternalEndPoint(DataBroker dataBroker,List<DPNTEPsInfo> meshedDpnList, IpAddress extIp) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        if( null == meshedDpnList)
            ItmUtils.getTunnelMeshInfo(dataBroker) ;
       if( null != meshedDpnList) {
          for( DPNTEPsInfo teps : meshedDpnList ) {
             // CHECK -- Assumption -- Only one End Point / Dpn for GRE/Vxlan Tunnels
              TunnelEndPoints firstEndPt = teps.getTunnelEndPoints().get(0) ;
              String interfaceName = firstEndPt.getInterfaceName() ;
              String trunkInterfaceName = ItmUtils.getTrunkInterfaceName(interfaceName, firstEndPt.getIpAddress().getIpv4Address().getValue(), extIp.getIpv4Address().getValue()) ;
              char[] subnetMaskArray = firstEndPt.getSubnetMask().getValue() ;
              String subnetMaskStr = String.valueOf(subnetMaskArray) ;
              SubnetUtils utils = new SubnetUtils(subnetMaskStr);
              String dcGwyIpStr = String.valueOf(extIp.getValue());
              IpAddress gwyIpAddress = (utils.getInfo().isInRange(dcGwyIpStr) ) ? null : firstEndPt.getGwIpAddress() ;
              Class<? extends TunnelTypeBase> tunType = (teps.getTunnelEndPoints().get(0).getTunnelType().equals("GRE") ) ? TunnelTypeGre.class :TunnelTypeVxlan.class ;
              String ifDescription = (tunType.equals("GRE") ) ? "GRE" : "VxLan" ;
              logger.debug(  " Creating Trunk Interface with parameters trunk I/f Name - {}, parent I/f name - {}, source IP - {}, DC Gateway IP - {} gateway IP - {}",trunkInterfaceName, interfaceName, firstEndPt.getIpAddress(), extIp, gwyIpAddress ) ;
              Interface iface = ItmUtils.buildTunnelInterface(teps.getDPNID(), trunkInterfaceName, String.format( "%s %s",ifDescription, "Trunk Interface"), true, tunType, firstEndPt.getIpAddress(), extIp, gwyIpAddress) ;
              logger.debug(  " Trunk Interface builder - {} ", iface ) ;
              InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkInterfaceName);
              logger.debug(  " Trunk Interface Identifier - {} ", trunkIdentifier ) ;
              logger.trace(  " Writing Trunk Interface to Config DS {}, {} ", trunkIdentifier, iface ) ;
              //ItmUtils.asyncUpdate(LogicalDatastoreType.CONFIGURATION,trunkIdentifier, iface , dataBroker, ItmUtils.DEFAULT_CALLBACK);
              t.merge(LogicalDatastoreType.CONFIGURATION, trunkIdentifier, iface, true);
          }
          futures.add( t.submit()) ;
       }
        return futures ;
    }

    public static List<ListenableFuture<Void>> buildTunnelsFromDpnToExternalEndPoint(DataBroker dataBroker,BigInteger dpnId,List<DPNTEPsInfo> meshedDpnList, IpAddress extIp) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        List<DPNTEPsInfo> cfgDpnList = new ArrayList<DPNTEPsInfo>() ;
       if( null != meshedDpnList) {
          for( DPNTEPsInfo teps : meshedDpnList ) {
             if( teps.getDPNID().equals(dpnId)) {
                cfgDpnList.add(teps) ;
             }
          }
          futures = buildTunnelsToExternalEndPoint( dataBroker, cfgDpnList, extIp) ;
       }
        return futures ;
    }
}
