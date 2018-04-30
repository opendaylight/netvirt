/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.Ipv6NdutilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.SendNeighborSolicitationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.SendNeighborSolicitationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.SendNeighborSolicitationToOfGroupInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.SendNeighborSolicitationToOfGroupOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.ipv6util.rev170210.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6NdUtilServiceImpl implements Ipv6NdutilService {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6NdUtilServiceImpl.class);
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final Ipv6NeighborSolicitation ipv6NeighborSolicitation;

    private static final String FAILED_TO_GET_SRC_MAC_FOR_INTERFACE = "Failed to get src mac for interface %s iid %s ";
    private static final String FAILED_TO_SEND_NS_FOR_INTERFACE = "Failed to send Neighbor Solicitation for interface ";
    private static final String DPN_NOT_FOUND_ERROR = "dpn not found for interface %s ";
    private static final String NODE_CONNECTOR_NOT_FOUND_ERROR = "Node connector id not found for interface %s";

    @Inject
    public Ipv6NdUtilServiceImpl(final OdlInterfaceRpcService odlInterfaceRpcService,
            final PacketProcessingService packetService) {
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.ipv6NeighborSolicitation = new Ipv6NeighborSolicitation(packetService);
    }

    @Override
    public ListenableFuture<RpcResult<SendNeighborSolicitationOutput>> sendNeighborSolicitation(
            SendNeighborSolicitationInput ndInput) {
        RpcResultBuilder<SendNeighborSolicitationOutput> failureBuilder = RpcResultBuilder.failed();
        RpcResultBuilder<SendNeighborSolicitationOutput> successBuilder = RpcResultBuilder.success();
        Ipv6Address targetIpv6Address = null;
        Ipv6Address srcIpv6Address;
        String interfaceName = null;
        String macAddr = null;
        BigInteger dpnId;
        int localErrorCount = 0;

        targetIpv6Address = ndInput.getTargetIpAddress();
        for (InterfaceAddress interfaceAddress : ndInput.getInterfaceAddress()) {
            try {
                interfaceName = interfaceAddress.getInterface();
                srcIpv6Address = interfaceAddress.getSrcIpAddress();

                GetPortFromInterfaceOutput portResult = getPortFromInterface(interfaceName);
                checkNotNull(portResult);
                dpnId = portResult.getDpid();
                Long portid = portResult.getPortno();
                checkArgument(null != dpnId && BigInteger.ZERO != dpnId, DPN_NOT_FOUND_ERROR, interfaceName);

                NodeConnectorRef nodeRef = MDSALUtil.getNodeConnRef(dpnId, portid.toString());
                checkNotNull(nodeRef, NODE_CONNECTOR_NOT_FOUND_ERROR, interfaceName);

                if (interfaceAddress.getSrcMacAddress() != null) {
                    macAddr = interfaceAddress.getSrcMacAddress().getValue();
                }
                checkNotNull(macAddr, FAILED_TO_GET_SRC_MAC_FOR_INTERFACE, interfaceName, nodeRef.getValue());
                ipv6NeighborSolicitation.transmitNeighborSolicitation(dpnId, nodeRef, new MacAddress(macAddr),
                                                                      srcIpv6Address, targetIpv6Address);
            } catch (NullPointerException | IllegalArgumentException e) {
                LOG.trace("Failed to send Neighbor Solicitation for {} on interface {}",
                           ndInput.getTargetIpAddress(), interfaceName);
                failureBuilder.withError(RpcError.ErrorType.APPLICATION, FAILED_TO_SEND_NS_FOR_INTERFACE
                        + interfaceName, e);
                successBuilder.withError(RpcError.ErrorType.APPLICATION, FAILED_TO_SEND_NS_FOR_INTERFACE
                        + interfaceName, e);
                localErrorCount++;
            }

        }
        if (localErrorCount == ndInput.getInterfaceAddress().size()) {
            // Failed to send IPv6 Neighbor Solicitation on all the interfaces, return failure.
            return failureBuilder.buildFuture();
        }

        return successBuilder.buildFuture();
    }

    private GetPortFromInterfaceOutput getPortFromInterface(String interfaceName) {
        GetPortFromInterfaceInputBuilder getPortFromInterfaceInputBuilder = new GetPortFromInterfaceInputBuilder();
        getPortFromInterfaceInputBuilder.setIntfName(interfaceName);
        GetPortFromInterfaceOutput result = null;

        Future<RpcResult<GetPortFromInterfaceOutput>> portFromInterface = odlInterfaceRpcService
                .getPortFromInterface(getPortFromInterfaceInputBuilder.build());
        try {
            result = portFromInterface.get().getResult();
            LOG.trace("getPortFromInterface rpc result is {} ", result);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error while retrieving the interfaceName from tag using getInterfaceFromIfIndex RPC");
        }
        return result;
    }

    @Override
    public ListenableFuture<RpcResult<SendNeighborSolicitationToOfGroupOutput>> sendNeighborSolicitationToOfGroup(
            SendNeighborSolicitationToOfGroupInput ndInput) {
        RpcResultBuilder<SendNeighborSolicitationToOfGroupOutput> successBuilder = RpcResultBuilder.success();
        ipv6NeighborSolicitation.transmitNeighborSolicitationToOfGroup(ndInput.getDpId(), ndInput.getSourceLlAddress(),
                ndInput.getSourceIpv6(), ndInput.getTargetIpAddress(), ndInput.getOfGroupId());

        return  successBuilder.buildFuture();
    }

}
