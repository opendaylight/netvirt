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
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.Tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.TunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.AddExternalTunnelEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.BuildExternalTunnelFromDpnsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveExternalTunnelEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveExternalTunnelFromDpnsInput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;

public class ItmManagerRpcService implements ItmRpcService {

   private static final Logger LOG = LoggerFactory.getLogger(ItmManagerRpcService.class);
        DataBroker dataBroker;
        public ItmManagerRpcService(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }
        
     @Override
     public Future<RpcResult<GetTunnelInterfaceNameOutput>> getTunnelInterfaceName(GetTunnelInterfaceNameInput input) {
         RpcResultBuilder<GetTunnelInterfaceNameOutput> resultBld = null;
         BigInteger sourceDpn = input.getSourceDpid() ;
         BigInteger destinationDpn = input.getDestinationDpid() ;
         InstanceIdentifier<Tunnel> path = InstanceIdentifier.create(
                 TunnelList.class)
                     .child(Tunnel.class, new TunnelKey(destinationDpn, sourceDpn));      
         
         Optional<Tunnel> tnl = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, dataBroker);

         if( tnl != null && tnl.isPresent())
         {
              Tunnel tunnel = tnl.get();
              GetTunnelInterfaceNameOutputBuilder output = new GetTunnelInterfaceNameOutputBuilder() ;
              output.setInterfaceName(tunnel.getTunnelInterfaceName()) ;
              resultBld = RpcResultBuilder.success();
              resultBld.withResult(output.build()) ;
         }else {
             resultBld = RpcResultBuilder.failed();
         }
         
         return Futures.immediateFuture(resultBld.build());
     }


    @Override
    public Future<RpcResult<Void>> removeExternalTunnelEndpoint(
            RemoveExternalTunnelEndpointInput input) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<RpcResult<Void>> removeExternalTunnelFromDpns(
            RemoveExternalTunnelFromDpnsInput input) {
        //Ignore the Futures for now
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
//        ItmExternalTunnelDeleteWorker.buildTunnelsFromDpnToExternalEndPoint(dataBroker, input.getDpnId(), null, input.getDestinationIp());
        result.set(RpcResultBuilder.<Void>success().build());
        return result;
    }

    @Override
    public Future<RpcResult<Void>> buildExternalTunnelFromDpns(
            BuildExternalTunnelFromDpnsInput input) {
        //Ignore the Futures for now
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        List<ListenableFuture<Void>> extTunnelResultList = ItmExternalTunnelAddWorker.buildTunnelsFromDpnToExternalEndPoint(dataBroker, input.getDpnId(), input.getDestinationIp(), input.getTunnelType());
        for (ListenableFuture<Void> extTunnelResult : extTunnelResultList) {
            Futures.addCallback(extTunnelResult, new FutureCallback<Void>(){

                @Override
                public void onSuccess(Void aVoid) {
                    result.set(RpcResultBuilder.<Void>success().build());
                }

                @Override
                public void onFailure(Throwable error) {
                    String msg = String.format("Unable to create ext tunnel");
                    LOG.error("create ext tunnel failed. {}. {}", msg, error);
                    result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
                }
            });
        }
        result.set(RpcResultBuilder.<Void>success().build());
        return result;
    }

    @Override
    public Future<RpcResult<Void>> addExternalTunnelEndpoint(
            AddExternalTunnelEndpointInput input) {
        // TODO Auto-generated method stub

        //Ignore the Futures for now
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
    //    ItmExternalTunnelAddWorker.buildTunnelsToExternalEndPoint(dataBroker, null, input.getDestinationIp()) ;
        result.set(RpcResultBuilder.<Void>success().build());
        return result;
    }

    @Override
    public Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> getExternalTunnelInterfaceName(
            GetExternalTunnelInterfaceNameInput input) {
        final SettableFuture<RpcResult<GetExternalTunnelInterfaceNameOutput>> result = SettableFuture.create() ;
        RpcResultBuilder<GetExternalTunnelInterfaceNameOutput> resultBld;
        BigInteger sourceDpn = input.getSourceDpid() ;
        IpAddress destinationIp = input.getDestinationIp() ;
        InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                ExternalTunnelList.class)
                    .child(ExternalTunnel.class, new ExternalTunnelKey(destinationIp, sourceDpn));      
        
        Optional<ExternalTunnel> ext = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, dataBroker);

        if( ext != null && ext.isPresent())
        {
             ExternalTunnel exTunnel = ext.get();
             GetExternalTunnelInterfaceNameOutputBuilder output = new GetExternalTunnelInterfaceNameOutputBuilder() ;
             output.setInterfaceName(exTunnel.getTunnelInterfaceName()) ;
             resultBld = RpcResultBuilder.success();
             resultBld.withResult(output.build()) ;
        }else {
            resultBld = RpcResultBuilder.failed();
        }
        
        return Futures.immediateFuture(resultBld.build());
 
    }
}
