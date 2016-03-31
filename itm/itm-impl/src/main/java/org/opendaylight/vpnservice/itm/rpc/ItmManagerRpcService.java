/*
 * Copyright (c) 2015, 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.DeviceVteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.DeviceVtepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.subnets.DeviceVtepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.vpnservice.itm.confighelpers.ItmExternalTunnelAddWorker;
import org.opendaylight.vpnservice.itm.confighelpers.ItmExternalTunnelDeleteWorker;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnelKey;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

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
                .child(InternalTunnel.class, new InternalTunnelKey( destinationDpn,sourceDpn));

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
        String sourceNode = input.getSourceNode();
        String dstNode = input.getDestinationNode();
        InstanceIdentifier<ExternalTunnel> path = InstanceIdentifier.create(
                ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(dstNode, sourceNode));

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
       // result.set(RpcResultBuilder.<Void>success().build());
        return result;
    }

    @Override
    public Future<RpcResult<java.lang.Void>> removeTerminatingServiceActions(final RemoveTerminatingServiceActionsInput input) {
        LOG.info("remove terminatingServiceActions called with DpnId = {} and serviceId = {}", input.getDpnId(), input.getServiceId());
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        Flow terminatingServiceTableFlow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE,input.getServiceId()), 5, String.format("%s:%d","ITM Flow Entry ",input.getServiceId()),
                0, 0, ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(input.getServiceId())),getTunnelMatchesForServiceId(input.getServiceId()), null );

        ListenableFuture<Void> installFlowResult = mdsalManager.removeFlow(input.getDpnId(), terminatingServiceTableFlow);
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
        //result.set(RpcResultBuilder.<Void>success().build());

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
                BigInteger.valueOf(serviceId)}));

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
        String srcNode = srcDpn.toString();
        IpAddress dstIp = input.getDestinationIp() ;
        InstanceIdentifier<ExternalTunnel> path1 = InstanceIdentifier.create(
                ExternalTunnelList.class)
                .child(ExternalTunnel.class, new ExternalTunnelKey(String.valueOf(dstIp), srcDpn.toString()));

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
                            .child(InternalTunnel.class, new InternalTunnelKey(teps.getDPNID(),srcDpn));

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

    @Override
    public Future<RpcResult<java.lang.Void>> deleteL2GwDevice(DeleteL2GwDeviceInput input) {
        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        try {
            final IpAddress hwIp = input.getIpAddress();
            final String node_id = input.getNodeId();
            InstanceIdentifier<TransportZones> containerPath = InstanceIdentifier.create(TransportZones.class);
            Optional<TransportZones> tZonesOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, containerPath, dataBroker);
            if (tZonesOptional.isPresent()) {
                TransportZones tZones = tZonesOptional.get();
                if (tZones.getTransportZone() == null || tZones.getTransportZone().isEmpty()) {
                    LOG.error("No teps configured");
                    result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, "No teps Configured").build());
                    return result;
                }
                String transportZone = tZones.getTransportZone().get(0).getZoneName();
                if (tZones.getTransportZone().get(0).getSubnets() == null || tZones.getTransportZone().get(0).getSubnets().isEmpty()) {
                    result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, "No subnets Configured").build());
                    return result;
                }
                SubnetsKey subnetsKey = tZones.getTransportZone().get(0).getSubnets().get(0).getKey();
                DeviceVtepsKey deviceVtepKey = new DeviceVtepsKey(hwIp, node_id);
                InstanceIdentifier<DeviceVteps> path =
                                InstanceIdentifier.builder(TransportZones.class)
                                                .child(TransportZone.class, new TransportZoneKey(transportZone))
                                                .child(Subnets.class, subnetsKey).child(DeviceVteps.class, deviceVtepKey).build();
                WriteTransaction t = dataBroker.newWriteOnlyTransaction();
                //TO DO: add retry if it fails
                t.delete(LogicalDatastoreType.CONFIGURATION, path);

                ListenableFuture<Void> futureCheck = t.submit();
                Futures.addCallback(futureCheck, new FutureCallback<Void>() {

                    @Override
                    public void onSuccess(Void aVoid) {
                        result.set(RpcResultBuilder.<Void>success().build());
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        String msg = String.format("Unable to write HwVtep {} to datastore", node_id);
                        LOG.error("Unable to write HwVtep {}, {} to datastore", node_id , hwIp);
                        result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
                    }
                });
            }
            return result;
        } catch (Exception e) {
            RpcResultBuilder<java.lang.Void> resultBuilder = RpcResultBuilder.<Void>failed().
                            withError(RpcError.ErrorType.APPLICATION, "Deleting l2 Gateway to DS Failed", e);
            return Futures.immediateFuture(resultBuilder.build());
        }
    }

    @Override
    public Future<RpcResult<java.lang.Void>> addL2GwDevice(AddL2GwDeviceInput input) {

        final SettableFuture<RpcResult<Void>> result = SettableFuture.create();
        try {
            final IpAddress hwIp = input.getIpAddress();
            final String node_id = input.getNodeId();
            InstanceIdentifier<TransportZones> containerPath = InstanceIdentifier.create(TransportZones.class);
            Optional<TransportZones> tZonesOptional = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, containerPath, dataBroker);
            if (tZonesOptional.isPresent()) {
                TransportZones tZones = tZonesOptional.get();
                if (tZones.getTransportZone() == null || tZones.getTransportZone().isEmpty()) {
                    LOG.error("No teps configured");
                    result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, "No teps Configured").build());
                    return result;
                }
                String transportZone = tZones.getTransportZone().get(0).getZoneName();
                if (tZones.getTransportZone().get(0).getSubnets() == null || tZones.getTransportZone().get(0).getSubnets().isEmpty()) {
                    result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, "No subnets Configured").build());
                    return result;
                }
                SubnetsKey subnetsKey = tZones.getTransportZone().get(0).getSubnets().get(0).getKey();
                DeviceVtepsKey deviceVtepKey = new DeviceVtepsKey(hwIp, node_id);
                InstanceIdentifier<DeviceVteps> path =
                        InstanceIdentifier.builder(TransportZones.class)
                                .child(TransportZone.class, new TransportZoneKey(transportZone))
                                .child(Subnets.class, subnetsKey).child(DeviceVteps.class, deviceVtepKey).build();
                DeviceVteps deviceVtep = new DeviceVtepsBuilder().setKey(deviceVtepKey).setIpAddress(hwIp).setNodeId(
                                node_id).setTopologyId(input.getTopologyId()).build();
                WriteTransaction t = dataBroker.newWriteOnlyTransaction();
                //TO DO: add retry if it fails
                t.put(LogicalDatastoreType.CONFIGURATION, path, deviceVtep, true);

                ListenableFuture<Void> futureCheck = t.submit();
                Futures.addCallback(futureCheck, new FutureCallback<Void>() {

                    @Override
                    public void onSuccess(Void aVoid) {
                        result.set(RpcResultBuilder.<Void>success().build());
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        String msg = String.format("Unable to write HwVtep {} to datastore", node_id);
                        LOG.error("Unable to write HwVtep {}, {} to datastore", node_id , hwIp);
                        result.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION, msg, error).build());
                    }
                });
            }
            return result;
        } catch (Exception e) {
            RpcResultBuilder<java.lang.Void> resultBuilder = RpcResultBuilder.<Void>failed().
                    withError(RpcError.ErrorType.APPLICATION, "Adding l2 Gateway to DS Failed", e);
            return Futures.immediateFuture(resultBuilder.build());
        }
    }

}
