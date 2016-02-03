/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.rpc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.vpnservice.itm.confighelpers.ItmExternalTunnelAddWorker;
import org.opendaylight.vpnservice.itm.confighelpers.ItmExternalTunnelDeleteWorker;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.AddExternalTunnelEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.BuildExternalTunnelFromDpnsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.CreateTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetInternalOrExternalInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetInternalOrExternalInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetInternalOrExternalInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveExternalTunnelEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveExternalTunnelFromDpnsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.RemoveTerminatingServiceActionsInput;
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
        private IMdsalApiManager mdsalManager;


    public void setMdsalManager(IMdsalApiManager mdsalManager) {
            this.mdsalManager = mdsalManager;
    }

        IdManagerService idManagerService;

        public ItmManagerRpcService(DataBroker dataBroker, IdManagerService idManagerService) {
          this.dataBroker = dataBroker;
          this.idManagerService = idManagerService;
        }

     @Override
     public Future<RpcResult<GetTunnelInterfaceNameOutput>> getTunnelInterfaceName(GetTunnelInterfaceNameInput input) {
         RpcResultBuilder<GetTunnelInterfaceNameOutput> resultBld = null;
         BigInteger sourceDpn = input.getSourceDpid() ;
         BigInteger destinationDpn = input.getDestinationDpid() ;
         InstanceIdentifier<InternalTunnel> path = InstanceIdentifier.create(
                 TunnelList.class)
                     .child(InternalTunnel.class, new InternalTunnelKey(destinationDpn, sourceDpn));      
         
         Optional<InternalTunnel> tnl = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, dataBroker);

         if( tnl != null && tnl.isPresent())
         {
              InternalTunnel tunnel = tnl.get();
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
         //Ignore the Futures for now
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        List<DPNTEPsInfo> meshedDpnList = ItmUtils.getTunnelMeshInfo(dataBroker) ;
        ItmExternalTunnelDeleteWorker.deleteTunnels(dataBroker, idManagerService,meshedDpnList , input.getDestinationIp(), input.getTunnelType());
        result.set(RpcResultBuilder.<Void>success().build());
        return result;
    }

    @Override
    public Future<RpcResult<Void>> removeExternalTunnelFromDpns(
            RemoveExternalTunnelFromDpnsInput input) {
        //Ignore the Futures for now
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        List<DPNTEPsInfo> cfgDpnList = ItmUtils.getDPNTEPListFromDPNId(dataBroker, input.getDpnId()) ;
        ItmExternalTunnelDeleteWorker.deleteTunnels(dataBroker, idManagerService, cfgDpnList, input.getDestinationIp(), input.getTunnelType());
        result.set(RpcResultBuilder.<Void>success().build());
        return result;
    }

    @Override
    public Future<RpcResult<Void>> buildExternalTunnelFromDpns(
            BuildExternalTunnelFromDpnsInput input) {
        //Ignore the Futures for now
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        List<ListenableFuture<Void>> extTunnelResultList = ItmExternalTunnelAddWorker.buildTunnelsFromDpnToExternalEndPoint(dataBroker, idManagerService,input.getDpnId(), input.getDestinationIp(), input.getTunnelType());
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
        List<DPNTEPsInfo> meshedDpnList = ItmUtils.getTunnelMeshInfo(dataBroker) ;
        ItmExternalTunnelAddWorker.buildTunnelsToExternalEndPoint(dataBroker, idManagerService,meshedDpnList, input.getDestinationIp(), input.getTunnelType()) ;
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

    @Override
    public Future<RpcResult<java.lang.Void>> createTerminatingServiceActions(final CreateTerminatingServiceActionsInput input) {
    	LOG.info("create terminatingServiceAction on DpnId = {} for service id {} and instructions {}", input.getDpnId() , input.getServiceId(), input.getInstruction());
       final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
       int serviceId = input.getServiceId() ;
       List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
       byte[] vxLANHeader = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    // Flags Byte
       byte Flags = (byte) 0x08;
       vxLANHeader[0] = Flags;

       // Extract the serviceId details and imprint on the VxLAN Header
       vxLANHeader[4] = (byte) (serviceId >> 16);
       vxLANHeader[5] = (byte) (serviceId >> 8);
       vxLANHeader[6] = (byte) (serviceId >> 0);

       // Matching metadata
       mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {
                               new BigInteger(1, vxLANHeader),
                               MetaDataUtil.METADA_MASK_VALID_TUNNEL_ID_BIT_AND_TUNNEL_ID }));

       Flow terminatingServiceTableFlow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                       getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE,serviceId), 5, String.format("%s:%d","ITM Flow Entry ",serviceId),
                       0, 0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(serviceId)),mkMatches, input.getInstruction());

       ListenableFuture<Void> installFlowResult = mdsalManager.installFlow(input.getDpnId(), terminatingServiceTableFlow);
       Futures.addCallback(installFlowResult, new FutureCallback<Void>(){

           @Override
           public void onSuccess(Void aVoid) {
               result.set(RpcResultBuilder.<Void>success().build());
           }

           @Override
           public void onFailure(Throwable error) {
               String msg = String.format("Unable to install terminating service flow for %s", input.getDpnId());
               LOG.error("create terminating service actions failed. {}. {}", msg, error);
               result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
           }
       });
       result.set(RpcResultBuilder.<Void>success().build());
       return result;
    }

    @Override
    public Future<RpcResult<java.lang.Void>> removeTerminatingServiceActions(final RemoveTerminatingServiceActionsInput input) {
        LOG.info("remove terminatingServiceActions called with DpnId = {} and serviceId = {}", input.getDpnId(), input.getServiceId());
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        Flow terminatingServiceTableFlow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE,input.getServiceId()), 5, String.format("%s:%d","ITM Flow Entry ",input.getServiceId()),
                0, 0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(input.getServiceId())),getTunnelMatchesForServiceId(input.getServiceId()), null );

        ListenableFuture<Void> installFlowResult = mdsalManager.installFlow(input.getDpnId(), terminatingServiceTableFlow);
        Futures.addCallback(installFlowResult, new FutureCallback<Void>(){

            @Override
            public void onSuccess(Void aVoid) {
                result.set(RpcResultBuilder.<Void>success().build());
            }

            @Override
            public void onFailure(Throwable error) {
                String msg = String.format("Unable to remove terminating service flow for %s", input.getDpnId());
                LOG.error("remove terminating service actions failed. {}. {}", msg, error);
                result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
            }
        });
        result.set(RpcResultBuilder.<Void>success().build());

       return result ;
    }

    public List<MatchInfo> getTunnelMatchesForServiceId(int serviceId) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        byte[] vxLANHeader = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        // Flags Byte
        byte Flags = (byte) 0x08;
        vxLANHeader[0] = Flags;

        // Extract the serviceId details and imprint on the VxLAN Header
        vxLANHeader[4] = (byte) (serviceId >> 16);
        vxLANHeader[5] = (byte) (serviceId >> 8);
        vxLANHeader[6] = (byte) (serviceId >> 0);

        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[]{
                new BigInteger(1, vxLANHeader),
                MetaDataUtil.METADA_MASK_VALID_TUNNEL_ID_BIT_AND_TUNNEL_ID}));

        return mkMatches;
    }

    private String getFlowRef(long termSvcTable, int svcId) {
        return new StringBuffer().append(termSvcTable).append(svcId).toString();
    }

    @Override
    public Future<RpcResult<GetInternalOrExternalInterfaceNameOutput>> getInternalOrExternalInterfaceName(
       GetInternalOrExternalInterfaceNameInput input) {
       RpcResultBuilder<GetInternalOrExternalInterfaceNameOutput> resultBld = RpcResultBuilder.failed();
       BigInteger srcDpn = input.getSourceDpid() ;
       IpAddress dstIp = input.getDestinationIp() ;
      InstanceIdentifier<ExternalTunnel> path1 = InstanceIdentifier.create(
          ExternalTunnelList.class)
          .child(ExternalTunnel.class, new ExternalTunnelKey(dstIp, srcDpn));

      Optional<ExternalTunnel> ext = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path1, dataBroker);

      if( ext != null && ext.isPresent())
      {
        ExternalTunnel extTunnel = ext.get();
        GetInternalOrExternalInterfaceNameOutputBuilder output = new GetInternalOrExternalInterfaceNameOutputBuilder().setInterfaceName(extTunnel.getTunnelInterfaceName() );
        resultBld = RpcResultBuilder.success();
        resultBld.withResult(output.build()) ;
      } else {
        List<DPNTEPsInfo> meshedDpnList = ItmUtils.getTunnelMeshInfo(dataBroker);
        // Look for external tunnels if not look for internal tunnel
        for (DPNTEPsInfo teps : meshedDpnList) {
          TunnelEndPoints firstEndPt = teps.getTunnelEndPoints().get(0);
          if (dstIp.equals(firstEndPt.getIpAddress())) {
            InstanceIdentifier<InternalTunnel> path = InstanceIdentifier.create(
                TunnelList.class)
                .child(InternalTunnel.class, new InternalTunnelKey(srcDpn, teps.getDPNID()));

            Optional<InternalTunnel>
                tnl =
                ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, dataBroker);
            if (tnl != null && tnl.isPresent()) {
              InternalTunnel tunnel = tnl.get();
              GetInternalOrExternalInterfaceNameOutputBuilder
                  output =
                  new GetInternalOrExternalInterfaceNameOutputBuilder()
                      .setInterfaceName(tunnel.getTunnelInterfaceName());
              resultBld = RpcResultBuilder.success();
              resultBld.withResult(output.build());
              break;
            }
          }
        }
      }
       return Futures.immediateFuture(resultBld.build());
    }

}
