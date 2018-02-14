/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import com.google.common.util.concurrent.Futures;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.mockito.Mockito;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public abstract class TestOdlInterfaceRpcService implements OdlInterfaceRpcService {
    public static TestOdlInterfaceRpcService newInstance() {
        return Mockito.mock(TestOdlInterfaceRpcService.class, realOrException());
    }

    @Override
    public Future<RpcResult<GetEndpointIpForDpnOutput>> getEndpointIpForDpn(GetEndpointIpForDpnInput input) {
        BigInteger dpnId = input.getDpid();

        // if the dpnId is DPN_ID_NO_EXIST, then an empty response will be returned
        GetEndpointIpForDpnOutputBuilder builder = new GetEndpointIpForDpnOutputBuilder();
        if (dpnId == GeniusProviderTestParams.DPN_ID) {
            List<IpAddress> localIpList = new ArrayList<>();
            localIpList.add(new IpAddress(new Ipv4Address(GeniusProviderTestParams.IPV4_ADDRESS_STR)));
            builder.setLocalIps(localIpList);
        } else if (dpnId == GeniusProviderTestParams.DPN_ID_INVALID) {
            return Futures.immediateFuture(RpcResultBuilder.<GetEndpointIpForDpnOutput>failed()
                    .withError(ErrorType.APPLICATION, "Invalid data.").build());
        }

        return Futures.immediateFuture(RpcResultBuilder
                .<GetEndpointIpForDpnOutput>success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<GetDpidFromInterfaceOutput>> getDpidFromInterface(GetDpidFromInterfaceInput input) {
        String ifName = input.getIntfName();

        // if the ifName is INTERFACE_NAME_NO_EXIST, then an empty response will be returned
        GetDpidFromInterfaceOutputBuilder builder = new GetDpidFromInterfaceOutputBuilder();
        if (ifName == GeniusProviderTestParams.INTERFACE_NAME) {
            builder.setDpid(GeniusProviderTestParams.DPN_ID);
        } else if (ifName == GeniusProviderTestParams.INTERFACE_NAME_INVALID) {
            return Futures.immediateFuture(RpcResultBuilder.<GetDpidFromInterfaceOutput>failed()
                    .withError(ErrorType.APPLICATION, "Invalid data.").build());
        }

        return Futures.immediateFuture(RpcResultBuilder
                .<GetDpidFromInterfaceOutput>success(builder.build()).build());
    }

    @Override
    public Future<RpcResult<GetNodeconnectorIdFromInterfaceOutput>>
        getNodeconnectorIdFromInterface(GetNodeconnectorIdFromInterfaceInput input)  {
        String ifName = input.getIntfName();

        // if the ifName is INTERFACE_NAME_NO_EXIST, then an empty response will be returned
        GetNodeconnectorIdFromInterfaceOutputBuilder builder = new GetNodeconnectorIdFromInterfaceOutputBuilder();
        if (ifName == GeniusProviderTestParams.INTERFACE_NAME) {
            builder.setNodeconnectorId(new NodeConnectorId(
                    GeniusProviderTestParams.NODE_CONNECTOR_ID_PREFIX
                    + GeniusProviderTestParams.INTERFACE_NAME));
        } else if (ifName == GeniusProviderTestParams.INTERFACE_NAME_INVALID) {
            return Futures.immediateFuture(RpcResultBuilder.<GetNodeconnectorIdFromInterfaceOutput>failed()
                    .withError(ErrorType.APPLICATION, "Invalid data.").build());
        }

        return Futures.immediateFuture(RpcResultBuilder
                .<GetNodeconnectorIdFromInterfaceOutput>success(builder.build()).build());
    }

}
