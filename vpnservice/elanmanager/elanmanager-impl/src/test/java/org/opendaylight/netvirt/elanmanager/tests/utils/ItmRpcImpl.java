/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.AddExternalTunnelEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.AddL2GwDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.AddL2GwMlagDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.BuildExternalTunnelFromDpnsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.CreateTerminatingServiceActionsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.DeleteL2GwDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.DeleteL2GwMlagDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetDpnEndpointIpsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetInternalOrExternalInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetInternalOrExternalInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsTunnelInternalOrExternalInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsTunnelInternalOrExternalOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveExternalTunnelEndpointInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveExternalTunnelFromDpnsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.RemoveTerminatingServiceActionsInput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;




public class ItmRpcImpl implements ItmRpcService {

    Map<BigInteger, IpAddress> tepIps = new ConcurrentHashMap<>();

    Map<BigInteger, Map<String, String>> interfaceNames = new ConcurrentHashMap<>();

    Map<BigInteger, Map<String, String>> externalInterfaceNames = new ConcurrentHashMap<>();

    public void addDpn(BigInteger dpnId, String tepIp) {
        tepIps.put(dpnId, new IpAddress(new Ipv4Address(tepIp)));
    }

    public void addInterface(BigInteger dpnId, String dstTep, String interfaceName) {
        interfaceNames.putIfAbsent(dpnId, new ConcurrentHashMap<>());
        interfaceNames.get(dpnId).put(dstTep, interfaceName);
    }

    public void addExternalInterface(BigInteger dpnId, String dstTep, String interfaceName) {
        dstTep = new IpAddress(new Ipv4Address(dstTep)).toString();
        externalInterfaceNames.putIfAbsent(dpnId, new ConcurrentHashMap<>());
        externalInterfaceNames.get(dpnId).put(dstTep, interfaceName);
    }

    @Override
    public Future<RpcResult<Void>> buildExternalTunnelFromDpns(BuildExternalTunnelFromDpnsInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> removeExternalTunnelEndpoint(RemoveExternalTunnelEndpointInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> addL2GwMlagDevice(AddL2GwMlagDeviceInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> removeExternalTunnelFromDpns(RemoveExternalTunnelFromDpnsInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> deleteL2GwDevice(DeleteL2GwDeviceInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> addL2GwDevice(AddL2GwDeviceInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<IsTunnelInternalOrExternalOutput>> isTunnelInternalOrExternal(
            IsTunnelInternalOrExternalInput input) {
        IsTunnelInternalOrExternalOutput output = null;
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public Future<RpcResult<GetTunnelInterfaceNameOutput>> getTunnelInterfaceName(GetTunnelInterfaceNameInput input) {
        String interfaceName = interfaceNames.get(input.getSourceDpid())
                .get(new String(tepIps.get(input.getDestinationDpid()).getValue()));
        GetTunnelInterfaceNameOutput output =
                new GetTunnelInterfaceNameOutputBuilder().setInterfaceName(interfaceName).build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public Future<RpcResult<IsDcgwPresentOutput>> isDcgwPresent(IsDcgwPresentInput input) {
        IsDcgwPresentOutput output = new IsDcgwPresentOutputBuilder().setRetVal(0L).build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> getExternalTunnelInterfaceName(
            GetExternalTunnelInterfaceNameInput input) {
        String interfaceName = externalInterfaceNames.get(new BigInteger(input.getSourceNode(), 10))
                .get(input.getDestinationNode());
        GetExternalTunnelInterfaceNameOutput output = new GetExternalTunnelInterfaceNameOutputBuilder()
                .setInterfaceName(interfaceName)
                .build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> createTerminatingServiceActions(CreateTerminatingServiceActionsInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<GetDpnEndpointIpsOutput>> getDpnEndpointIps(GetDpnEndpointIpsInput input) {
        GetDpnEndpointIpsOutput output = new GetDpnEndpointIpsOutputBuilder()
                .setNexthopipList(Lists.newArrayList(tepIps.get(input.getSourceDpid()))).build();
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> deleteL2GwMlagDevice(DeleteL2GwMlagDeviceInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<GetInternalOrExternalInterfaceNameOutput>> getInternalOrExternalInterfaceName(
            GetInternalOrExternalInterfaceNameInput input) {
        GetInternalOrExternalInterfaceNameOutput output = null;
        return RpcResultBuilder.success(output).buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> removeTerminatingServiceActions(RemoveTerminatingServiceActionsInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }

    @Override
    public Future<RpcResult<Void>> addExternalTunnelEndpoint(AddExternalTunnelEndpointInput input) {
        return RpcResultBuilder.<Void>success().buildFuture();
    }
}
