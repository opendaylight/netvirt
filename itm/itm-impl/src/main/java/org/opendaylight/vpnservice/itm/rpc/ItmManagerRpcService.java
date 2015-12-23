/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.itm.confighelpers.ItmExternalTunnelAddWorker;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.BuildTunnelFromDpnToDcgatewayInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.BuildTunnelToDcgatewayInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.GetTunnelInterfaceIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.GetTunnelInterfaceIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.GetTunnelInterfaceIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;

public class ItmManagerRpcService implements ItmRpcService {

   private static final Logger LOG = LoggerFactory.getLogger(ItmRpcService.class);
        DataBroker dataBroker;
        public ItmManagerRpcService(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

     @Override
     public Future<RpcResult<java.lang.Void>> buildTunnelFromDpnToDcgateway(BuildTunnelFromDpnToDcgatewayInput input) {
        //Ignore the Futures for now
         final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
         ItmExternalTunnelAddWorker.buildTunnelsFromDpnToExternalEndPoint(dataBroker, input.getDpid(), null, input.getDcgwyid());
         result.set(RpcResultBuilder.<Void>success().build());
         return result ;
     }

     @Override
     public Future<RpcResult<GetTunnelInterfaceIdOutput>> getTunnelInterfaceId(GetTunnelInterfaceIdInput input) {
         final SettableFuture<RpcResult<GetTunnelInterfaceIdOutput>> result = SettableFuture.create() ;
         RpcResultBuilder<GetTunnelInterfaceIdOutput> resultBld = null;
         BigInteger sourceDpn = input.getSourceDpid() ;
         BigInteger destinationDpn = input.getDestinationDpid() ;
         String parentName = null;
         IpAddress srcIp = null ;
         IpAddress destIp = null ;
         List<DPNTEPsInfo> meshDpnList = ItmUtils.getTunnelMeshInfo(dataBroker);
         for ( DPNTEPsInfo dpn : meshDpnList) {
            if( (dpn.getDPNID()).equals(sourceDpn) ){
                parentName  = dpn.getTunnelEndPoints().get(0).getInterfaceName();
                srcIp = dpn.getTunnelEndPoints().get(0).getIpAddress() ;
            }else if( (dpn.getDPNID()).equals(destinationDpn)) {
                 destIp = dpn.getTunnelEndPoints().get(0).getIpAddress() ;
            }
         }
         if( srcIp != null && destIp != null )
         {
              String trunkInterfaceName = ItmUtils.getTrunkInterfaceName(parentName, srcIp.getIpv4Address().getValue(), destIp.getIpv4Address().getValue()) ;
              InstanceIdentifierBuilder<Interface> idBuilder = InstanceIdentifier.builder(InterfacesState.class)
                      .child(Interface.class, new InterfaceKey(trunkInterfaceName));
              InstanceIdentifier<Interface> id = idBuilder.build();
              Optional<Interface> stateIf = ItmUtils.read(LogicalDatastoreType.OPERATIONAL, id, dataBroker);
              if(stateIf.isPresent()){
                  GetTunnelInterfaceIdOutputBuilder output = new GetTunnelInterfaceIdOutputBuilder() ;
                  output.setInterfaceid(stateIf.get().getIfIndex()) ;
                  resultBld.withResult(output.build()) ;
                  result.set(resultBld.build()) ;
              }else {
                  result.set(RpcResultBuilder.<GetTunnelInterfaceIdOutput>failed().withError(RpcError.ErrorType.APPLICATION, "Interface Not found ").build()) ;
              }
         }else {
              result.set(RpcResultBuilder.<GetTunnelInterfaceIdOutput>failed().withError(RpcError.ErrorType.APPLICATION, "Source or Destination Dpn Id not found ").build()) ;
         }
         return result ;
     }

     @Override
     public Future<RpcResult<java.lang.Void>> buildTunnelToDcgateway(BuildTunnelToDcgatewayInput input) {
         //Ignore the Futures for now
         final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
         ItmExternalTunnelAddWorker.buildTunnelsToExternalEndPoint(dataBroker, null, input.getDcgwyid()) ;
         result.set(RpcResultBuilder.<Void>success().build());
         return result ;
     }
}
