/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsOnRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsOnRouterOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsOnRouterOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsOnVpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsOnVpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsOnVpnOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.OdlNatRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.dnat.configuration.DnatIpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.dnat.configuration.DnatIpMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.nat.output.RouterNat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.nat.output.RouterNatBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.snat.state.SnatIpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.snat.state.SnatIpMappingBuilder;
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
    public Future<RpcResult<GetNatTranslationsOnVpnOutput>> getNatTranslationsOnVpn(
            GetNatTranslationsOnVpnInput input) {
        RpcResultBuilder<GetNatTranslationsOnVpnOutput> rpcResultBuilder = null;

        List<Uuid> routerUuidList = NatUtil.getRouterUuIdsForVpn(dataBroker, input.getVpnUuid());
        if (routerUuidList == null || routerUuidList.size() == 0) {
            String errMsg = String.format("No Routers associated with vpn-id %s", input.getVpnUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsOnVpnOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        List<RouterNat> natRouterList = new ArrayList<RouterNat>();
        for (Uuid routerUuid : routerUuidList) {
            long routerId = NatUtil.getVpnId(dataBroker, routerUuid.getValue());
            if (routerId == NatConstants.INVALID_ID) {
                LOG.warn("NAT Service: Invalid RouterID found {}", routerId);
            }
            natRouterList.addAll(constuctNatInformation(routerUuid, routerId));
        }
        GetNatTranslationsOnVpnOutputBuilder output = new GetNatTranslationsOnVpnOutputBuilder()
                .setRouterNat(natRouterList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public Future<RpcResult<GetNatTranslationsOnRouterOutput>> getNatTranslationsOnRouter(
            GetNatTranslationsOnRouterInput input) {
        RpcResultBuilder<GetNatTranslationsOnRouterOutput> rpcResultBuilder = null;
        long routerId = NatUtil.getVpnId(dataBroker, input.getRouterUuid().getValue());
        if (routerId == NatConstants.INVALID_ID) {
            String errMsg = String.format("No VpnInstance Mapping found for router-id %s",
                    input.getRouterUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsOnRouterOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }

        List<RouterNat> routerNatList = constuctNatInformation(input.getRouterUuid(), routerId);

        GetNatTranslationsOnRouterOutputBuilder output = new GetNatTranslationsOnRouterOutputBuilder()
                .setRouterNat(routerNatList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    private List<RouterNat> constuctNatInformation(Uuid routerUuid, long routerId) {

        String neutronRouterName = NatUtil.getNeutronRouterNamebyUuid(dataBroker, routerUuid);

        RouterNatBuilder natRouterBuilder = new RouterNatBuilder();
        natRouterBuilder.setRouterUuid(routerUuid);
        natRouterBuilder.setRouterName(neutronRouterName);

        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            LOG.warn("NAT Service: No SNAT IP Mapping found for router-uuid {}", routerUuid.getValue());
        } else {

            // Capturing SNAT information
            List<SnatIpMapping> snatIpMapping = new ArrayList<SnatIpMapping>();

            for (IntextIpProtocolType protocolType : ipPortMapping.getIntextIpProtocolType()) {
                for (IpPortMap ipPortMap : protocolType.getIpPortMap()) {
                    String[] internalPortMap = ipPortMap.getIpPortInternal().split(NwConstants.MACADDR_SEP);
                    SnatIpMappingBuilder natIpMappingBuilder = new SnatIpMappingBuilder()
                            .setInternalIp(internalPortMap[0]).setInternalPort(internalPortMap[1])
                            .setExternalIp(ipPortMap.getIpPortExternal().getIpAddress())
                            .setExternalPort(ipPortMap.getIpPortExternal().getPortNum().toString())
                            .setProtocol(protocolType.getProtocol().getName());
                    snatIpMapping.add(natIpMappingBuilder.build());
                }
            }
            natRouterBuilder.setSnatIpMapping(snatIpMapping);
        }

        // Capturing DNAT information
        List<DnatIpMapping> dnatIpMapping = new ArrayList<DnatIpMapping>();
        List<Ports> fipPorts = NatUtil.getFloatingIpPortsForRouter(dataBroker, routerUuid);
        if (fipPorts == null || fipPorts.size() == 0) {
            LOG.warn("NAT Service: No DNAT IP Mapping found for router-uuid {}", routerUuid.getValue());
        } else {
            for (Ports fipPort : fipPorts) {
                List<InternalToExternalPortMap> ipMapping = fipPort.getInternalToExternalPortMap();
                for (InternalToExternalPortMap fipMap : ipMapping) {
                    DnatIpMappingBuilder natIpMappingBuilder = new DnatIpMappingBuilder()
                            .setExternalIp(fipMap.getExternalIp()).setInternalIp(fipMap.getInternalIp());
                    dnatIpMapping.add(natIpMappingBuilder.build());
                }
            }
            natRouterBuilder.setDnatIpMapping(dnatIpMapping);
        }

        List<RouterNat> natRouterList = new ArrayList<RouterNat>();
        natRouterList.add(natRouterBuilder.build());
        return natRouterList;
    }
}