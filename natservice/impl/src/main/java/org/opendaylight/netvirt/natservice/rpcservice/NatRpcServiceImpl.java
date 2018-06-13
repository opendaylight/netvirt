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

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.natservice.internal.NatConstants;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsForNetworkAndIpaddressInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsForNetworkAndIpaddressOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rpc.rev170209.GetNatTranslationsForNetworkAndIpaddressOutputBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.AllocationPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatRpcServiceImpl implements OdlNatRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(NatRpcServiceImpl.class);
    private final DataBroker dataBroker;
    private final INeutronVpnManager nvpnManager;

    @Inject
    public NatRpcServiceImpl(final DataBroker dataBroker, final INeutronVpnManager nvpnManager) {
        this.dataBroker = dataBroker;
        this.nvpnManager = nvpnManager;
    }

    @Override
    public Future<RpcResult<GetNatTranslationsOnVpnOutput>> getNatTranslationsOnVpn(
            GetNatTranslationsOnVpnInput input) {
        RpcResultBuilder<GetNatTranslationsOnVpnOutput> rpcResultBuilder = null;

        List<Uuid> routerUuidList = NatUtil.getRouterUuIdsForVpn(dataBroker, input.getVpnUuid());
        if (routerUuidList.isEmpty()) {
            String errMsg = String.format("404 Not Found - Invalid external vpn {%s} provided",
                    input.getVpnUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsOnVpnOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        List<RouterNat> natRouterList = new ArrayList<RouterNat>();
        for (Uuid routerUuid : routerUuidList) {
            long routerId = NatUtil.getVpnId(dataBroker, routerUuid.getValue());
            if (routerId == NatConstants.INVALID_ID) {
                LOG.warn("getNatTranslationsOnVpn : Invalid RouterID found {}", routerId);
                continue;
            }
            natRouterList.addAll(constructNatInformation(routerUuid, routerId));
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
            String errMsg = String.format("404 Not Found - No Router found with UUID {%s}",
                    input.getRouterUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsOnRouterOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }

        List<RouterNat> routerNatList = constructNatInformation(input.getRouterUuid(), routerId);

        GetNatTranslationsOnRouterOutputBuilder output = new GetNatTranslationsOnRouterOutputBuilder()
                .setRouterNat(routerNatList);
        rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    public Future<RpcResult<GetNatTranslationsForNetworkAndIpaddressOutput>> getNatTranslationsForNetworkAndIpaddress(
            GetNatTranslationsForNetworkAndIpaddressInput input) {

        String ipAddress = String.valueOf(input.getIpAddress().getValue());
        RpcResultBuilder<GetNatTranslationsForNetworkAndIpaddressOutput> rpcResultBuilder = null;
        GetNatTranslationsForNetworkAndIpaddressOutputBuilder output = null;

        List<Uuid> subnetUuidList = NatUtil.getSubnetIdsFromNetworkId(dataBroker, input.getNetworkUuid());
        if (subnetUuidList.isEmpty()) {
            String errMsg = String.format("404 Not Found - Invalid Network UUID {%s} provided as no Subnetworks found",
                    input.getNetworkUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsForNetworkAndIpaddressOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }
        Subnet subNet = null;
        Boolean isIpInSubnet = Boolean.FALSE;
        outerloop:
        for (Uuid subnetUuid: subnetUuidList) {
            subNet = nvpnManager.getNeutronSubnet(subnetUuid);
            for (AllocationPools allocationPool : subNet.getAllocationPools()) {
                if (NatUtil.isIpInSubnet(ipAddress,
                        String.valueOf(allocationPool.getStart().getValue()),
                        String.valueOf(allocationPool.getEnd().getValue()))) {
                    LOG.debug("getNatTranslationsForNetworkAndIpaddress : IP Adderess {} falls within the Subnet {}",
                            ipAddress, subNet.getUuid().getValue());
                    isIpInSubnet = Boolean.TRUE;
                    break outerloop;
                }
            }
        }

        if (!isIpInSubnet) {
            String errMsg = String.format("404 Not Found - IP Adress {%s} does not fall within the Subnet IP range"
                    + " of Network {%s}", ipAddress, input.getNetworkUuid().getValue());
            rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsForNetworkAndIpaddressOutput>failed()
                    .withError(RpcError.ErrorType.APPLICATION, errMsg);
            return Futures.immediateFuture(rpcResultBuilder.build());
        }

        Subnetmap subnetMap = NatUtil.getSubnetMap(dataBroker, subNet.getUuid());
        long routerId = NatUtil.getVpnId(dataBroker, subnetMap.getRouterId().getValue());

        List<Ports> fipPorts = NatUtil.getFloatingIpPortsForRouter(dataBroker, subnetMap.getRouterId());
        if (fipPorts.isEmpty()) {
            LOG.warn("getNatTranslationsForNetworkAndIpaddress : No DNAT IP Mapping found for IP {}", ipAddress);
        } else {
            for (Ports fipPort : fipPorts) {
                List<InternalToExternalPortMap> ipMapping = fipPort.getInternalToExternalPortMap();
                for (InternalToExternalPortMap fipMap : ipMapping) {
                    if (fipMap.getInternalIp().equals(ipAddress)) {
                        output = new GetNatTranslationsForNetworkAndIpaddressOutputBuilder()
                                    .setExternalIp(fipMap.getExternalIp())
                                    .setNatTranslation("DNAT");
                        rpcResultBuilder = RpcResultBuilder.success();
                        rpcResultBuilder.withResult(output.build());
                        return Futures.immediateFuture(rpcResultBuilder.build());
                    }
                }
            }
        }

        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            LOG.warn("getNatTranslationsForNetworkAndIpaddress : No SNAT IP Mapping found for IP {}", ipAddress);
        } else {
            for (IntextIpProtocolType protocolType : ipPortMapping.getIntextIpProtocolType()) {
                for (IpPortMap ipPortMap : protocolType.getIpPortMap()) {
                    String[] internalIpPort = ipPortMap.getIpPortInternal().split(NwConstants.MACADDR_SEP);
                    if (ipAddress.equals(internalIpPort[0])) {

                        output = new GetNatTranslationsForNetworkAndIpaddressOutputBuilder()
                                .setExternalIp(ipPortMap.getIpPortExternal().getIpAddress())
                                .setInternalIp(internalIpPort[0])
                                .setNatTranslation("SNAT")
                                .setInternalPort(internalIpPort[1])
                                .setExternalPort(ipPortMap.getIpPortExternal().getPortNum().toString())
                                .setProtocol(protocolType.getProtocol().getName());
                        rpcResultBuilder = RpcResultBuilder.success();
                        rpcResultBuilder.withResult(output.build());
                        return Futures.immediateFuture(rpcResultBuilder.build());
                    }
                }
            }
        }

        String errMsg = String.format("404 Not Found - No NAT Translation found for IP {%s}", ipAddress);
        rpcResultBuilder = RpcResultBuilder.<GetNatTranslationsForNetworkAndIpaddressOutput>failed()
                .withError(RpcError.ErrorType.APPLICATION, errMsg);
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    private List<RouterNat> constructNatInformation(Uuid routerUuid, long routerId) {

        String neutronRouterName = NatUtil.getNeutronRouterNamebyUuid(dataBroker, routerUuid);

        RouterNatBuilder natRouterBuilder = new RouterNatBuilder();
        natRouterBuilder.setRouterUuid(routerUuid);
        natRouterBuilder.setRouterName(neutronRouterName);

        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            LOG.warn("constructNatInformation : No SNAT IP Mapping found for router-uuid {}", routerUuid.getValue());
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
        if (fipPorts.isEmpty()) {
            LOG.warn("constructNatInformation : No DNAT IP Mapping found for router-uuid {}", routerUuid.getValue());
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
