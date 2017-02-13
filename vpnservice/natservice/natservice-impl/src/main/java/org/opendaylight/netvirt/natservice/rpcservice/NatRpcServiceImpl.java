/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.rpcservice;

import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.natservice.internal.NatConstants;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetDnatConfigurationForRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetDnatConfigurationForRouterOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetDnatConfigurationForRouterOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetDnatConfigurationForVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetDnatConfigurationForVpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetDnatConfigurationForVpnOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetSnatConfigurationForRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetSnatConfigurationForRouterOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetSnatConfigurationForRouterOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetSnatConfigurationForVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetSnatConfigurationForVpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetSnatConfigurationForVpnOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.OdlNatRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.dnat.config.output.RouterDnat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.dnat.config.output.RouterDnatBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.dnat.config.output.router.dnat.DnatIpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.dnat.config.output.router.dnat.DnatIpMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.snat.config.output.RouterSnat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.snat.config.output.RouterSnatBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.snat.config.output.router.snat.SnatIpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.snat.config.output.router.snat.SnatIpMappingBuilder;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatRpcServiceImpl implements OdlNatRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(NatRpcServiceImpl.class);
    private final DataBroker dataBroker;

    public NatRpcServiceImpl(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public Future<RpcResult<GetSnatConfigurationForVpnOutput>> getSnatConfigurationForVpn(
            GetSnatConfigurationForVpnInput input) {
        RpcResultBuilder<GetSnatConfigurationForVpnOutput> rpcResultBuilder = null;
        List<Uuid> routerUuidList = NatUtil.getRouterUuIdsForVpn(dataBroker, input.getVpnUuid());
        if (routerUuidList == null || routerUuidList.size() == 0) {
            String errMsg = String.format("No Routers associated with vpn-id %s", input.getVpnUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetSnatConfigurationForVpnOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        List<RouterSnat> natRouterList = new ArrayList<RouterSnat>();
        for (Uuid routerUuid : routerUuidList) {
            long routerId = NatUtil.getVpnId(dataBroker, routerUuid.getValue());
            if (routerId == NatConstants.INVALID_ID) {
                LOG.warn("No VpnInstance Mapping found for router-id-{}", routerUuid.getValue());
            }
            IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
            if (ipPortMapping != null) {
                natRouterList.addAll(constuctSnatInformation(routerUuid, ipPortMapping));
            }
        }
        GetSnatConfigurationForVpnOutputBuilder output = new GetSnatConfigurationForVpnOutputBuilder()
                .setRouterSnat(natRouterList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetSnatConfigurationForRouterOutput>> getSnatConfigurationForRouter(
            GetSnatConfigurationForRouterInput input) {
        RpcResultBuilder<GetSnatConfigurationForRouterOutput> rpcResultBuilder = null;
        long routerId = NatUtil.getVpnId(dataBroker, input.getRouterUuid().getValue());
        if (routerId == NatConstants.INVALID_ID) {
            String errMsg = String.format("No VpnInstance Mapping found for router-id %s",
                    input.getRouterUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetSnatConfigurationForRouterOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            String errMsg = String.format("No SNAT Configuration found for router-id %s",
                    input.getRouterUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetSnatConfigurationForRouterOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }

        List<RouterSnat> natRouterList = constuctSnatInformation(input.getRouterUuid(), ipPortMapping);
        GetSnatConfigurationForRouterOutputBuilder output = new GetSnatConfigurationForRouterOutputBuilder()
                .setRouterSnat(natRouterList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetDnatConfigurationForVpnOutput>> getDnatConfigurationForVpn(
            GetDnatConfigurationForVpnInput input) {
        RpcResultBuilder<GetDnatConfigurationForVpnOutput> rpcResultBuilder = null;
        List<Uuid> routerUuidList = NatUtil.getRouterUuIdsForVpn(dataBroker, input.getVpnUuid());

        if (routerUuidList == null || routerUuidList.size() == 0) {
            String errMsg = String.format("No Routers associated with vpn-id %s", input.getVpnUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetDnatConfigurationForVpnOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        List<RouterDnat> natRouterList = new ArrayList<RouterDnat>();
        for (Uuid routerUuid : routerUuidList) {

            long routerId = NatUtil.getVpnId(dataBroker, routerUuid.getValue());
            if (routerId == NatConstants.INVALID_ID) {
                LOG.warn("No VpnInstance Mapping found for router-id-{}", routerUuid.getValue());
            }
            natRouterList.addAll(constructDnatInformation(routerUuid));
        }
        GetDnatConfigurationForVpnOutputBuilder output = new GetDnatConfigurationForVpnOutputBuilder()
                .setRouterDnat(natRouterList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetDnatConfigurationForRouterOutput>> getDnatConfigurationForRouter(
            GetDnatConfigurationForRouterInput input) {
        RpcResultBuilder<GetDnatConfigurationForRouterOutput> rpcResultBuilder = null;
        long routerId = NatUtil.getVpnId(dataBroker, input.getRouterUuid().getValue());
        if (routerId == NatConstants.INVALID_ID) {
            String errMsg = String.format("No VpnInstance Mapping found for router-id %s",
                    input.getRouterUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetDnatConfigurationForRouterOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }

        List<RouterDnat> natRouterList = constructDnatInformation(input.getRouterUuid());
        GetDnatConfigurationForRouterOutputBuilder output = new GetDnatConfigurationForRouterOutputBuilder()
                .setRouterDnat(natRouterList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    private List<RouterSnat> constuctSnatInformation(Uuid uuid, IpPortMapping ipPortMapping) {

        String neutronRouterName = NatUtil.getNeutronRouterNamebyUuid(dataBroker, uuid);
        RouterSnatBuilder natRouterBuilder = new RouterSnatBuilder();
        natRouterBuilder.setRouterUuid(uuid);
        natRouterBuilder.setRouterName(neutronRouterName);
        List<SnatIpMapping> natIpMapping = new ArrayList<SnatIpMapping>();
        List<RouterSnat> natRouterList = new ArrayList<RouterSnat>();
        for (IntextIpProtocolType protocolType : ipPortMapping.getIntextIpProtocolType()) {
            for (IpPortMap ipPortMap : protocolType.getIpPortMap()) {
                String[] internalPortMap = ipPortMap.getIpPortInternal().split(NwConstants.MACADDR_SEP);
                SnatIpMappingBuilder natIpMappingBuilder = new SnatIpMappingBuilder().setInternalIp(internalPortMap[0])
                        .setInternalPort(internalPortMap[1])
                        .setExternalIp(ipPortMap.getIpPortExternal().getIpAddress())
                        .setExternalPort(ipPortMap.getIpPortExternal().getPortNum().toString())
                        .setProtocol(protocolType.getProtocol().getName());
                natIpMapping.add(natIpMappingBuilder.build());
            }
        }
        natRouterBuilder.setSnatIpMapping(natIpMapping);
        natRouterList.add(natRouterBuilder.build());
        return natRouterList;
    }

    private List<RouterDnat> constructDnatInformation(Uuid uuid) {
        String neutronRouterName = NatUtil.getNeutronRouterNamebyUuid(dataBroker, uuid);
        List<RouterDnat> natRouterList = new ArrayList<RouterDnat>();
        RouterDnatBuilder natRouterBuilder = new RouterDnatBuilder();
        natRouterBuilder.setRouterUuid(uuid);
        natRouterBuilder.setRouterName(neutronRouterName);
        List<DnatIpMapping> natIpMapping = new ArrayList<DnatIpMapping>();
        List<Ports> fipPorts = NatUtil.getFloatingIpPortsForRouter(dataBroker, uuid);
        if (fipPorts == null || fipPorts.size() == 0) {
            return natRouterList;
        }

        for (Ports fipPort : fipPorts) {
            List<InternalToExternalPortMap> ipMapping = fipPort.getInternalToExternalPortMap();
            for (InternalToExternalPortMap fipMap : ipMapping) {
                DnatIpMappingBuilder natIpMappingBuilder = new DnatIpMappingBuilder()
                        .setExternalIp(fipMap.getExternalIp()).setInternalIp(fipMap.getInternalIp());
                natIpMapping.add(natIpMappingBuilder.build());
            }
        }
        natRouterBuilder.setDnatIpMapping(natIpMapping);
        natRouterList.add(natRouterBuilder.build());
        return natRouterList;
    }
}