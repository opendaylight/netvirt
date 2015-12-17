/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.interfacemgr.listeners.InterfaceConfigListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.InterfaceInventoryStateListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.InterfaceTopologyStateListener;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.interfacemgr.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.listeners.FlowBasedServicesConfigListener;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.listeners.FlowBasedServicesInterfaceStateListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.VlanMemberConfigListener;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.*;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class InterfacemgrProvider implements BindingAwareProvider, AutoCloseable, IInterfaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(InterfacemgrProvider.class);

    private RpcProviderRegistry rpcProviderRegistry;
    private IdManagerService idManager;
    private IMdsalApiManager mdsalManager;
    private InterfaceConfigListener interfaceConfigListener;
    private InterfaceTopologyStateListener topologyStateListener;
    private InterfaceInventoryStateListener interfaceInventoryStateListener;
    private FlowBasedServicesInterfaceStateListener flowBasedServicesInterfaceStateListener;
    private FlowBasedServicesConfigListener flowBasedServicesConfigListener;
    private VlanMemberConfigListener vlanMemberConfigListener;

    private InterfaceManagerRpcService interfaceManagerRpcService;
    private BindingAwareBroker.RpcRegistration<OdlInterfaceRpcService> rpcRegistration;

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("InterfacemgrProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            idManager = rpcProviderRegistry.getRpcService(IdManagerService.class);
            createIdPool();

            interfaceManagerRpcService = new InterfaceManagerRpcService(dataBroker, mdsalManager);
            rpcRegistration = getRpcProviderRegistry().addRpcImplementation(
                    OdlInterfaceRpcService.class, interfaceManagerRpcService);

            interfaceConfigListener = new InterfaceConfigListener(dataBroker, idManager);
            interfaceConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);

            interfaceInventoryStateListener = new InterfaceInventoryStateListener(dataBroker, idManager);
            interfaceInventoryStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            topologyStateListener = new InterfaceTopologyStateListener(dataBroker);
            topologyStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            flowBasedServicesConfigListener = new FlowBasedServicesConfigListener(dataBroker);
            flowBasedServicesConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);

            flowBasedServicesInterfaceStateListener =
                    new FlowBasedServicesInterfaceStateListener(dataBroker);
            flowBasedServicesInterfaceStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            vlanMemberConfigListener =
                               new VlanMemberConfigListener(dataBroker, idManager);
            vlanMemberConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(IfmConstants.IFM_IDPOOL_NAME)
                .setLow(IfmConstants.IFM_ID_POOL_START)
                .setHigh(IfmConstants.IFM_ID_POOL_END)
            .build();
        //TODO: Error handling
        Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
        try {
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.debug("Created IdPool for InterfaceMgr");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for InterfaceMgr",e);
        }
    }

    @Override
    public void close() throws Exception {
        LOG.info("InterfacemgrProvider Closed");
        interfaceConfigListener.close();
        rpcRegistration.close();
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    @Override
    public Long getPortForInterface(String ifName) {
        GetPortFromInterfaceInput input = new GetPortFromInterfaceInputBuilder().setIntfName(ifName).build();
        Future<RpcResult<GetPortFromInterfaceOutput>> output = interfaceManagerRpcService.getPortFromInterface(input);
        try {
            RpcResult<GetPortFromInterfaceOutput> port = output.get();
            if(port.isSuccessful()){
                return port.getResult().getPortno();
            }
        }catch(NullPointerException | InterruptedException | ExecutionException e){
            LOG.warn("Exception when getting port for interface",e);
        }
        return null;
    }

    @Override
    public Long getPortForInterface(Interface intf) {
        GetPortFromInterfaceInput input = new GetPortFromInterfaceInputBuilder().setIntfName(intf.getName()).build();
        Future<RpcResult<GetPortFromInterfaceOutput>> output = interfaceManagerRpcService.getPortFromInterface(input);
        try {
            RpcResult<GetPortFromInterfaceOutput> port = output.get();
            if(port.isSuccessful()){
                return port.getResult().getPortno();
            }
        }catch(NullPointerException | InterruptedException | ExecutionException e){
            LOG.warn("Exception when getting port for interface",e);
        }
        return null;
    }

    @Override
    public BigInteger getDpnForInterface(String ifName) {
        GetDpidFromInterfaceInput input = new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
        Future<RpcResult<GetDpidFromInterfaceOutput>> output = interfaceManagerRpcService.getDpidFromInterface(input);
        try {
            RpcResult<GetDpidFromInterfaceOutput> dpn = output.get();
            if(dpn.isSuccessful()){
                return dpn.getResult().getDpid();
            }
        }catch(NullPointerException | InterruptedException | ExecutionException e){
            LOG.warn("Exception when getting port for interface",e);
        }
        return null;
    }

    @Override
    public String getEndpointIpForDpn(BigInteger dpnId) {
        GetEndpointIpForDpnInput input = new GetEndpointIpForDpnInputBuilder().setDpid(dpnId).build();
        Future<RpcResult<GetEndpointIpForDpnOutput>> output = interfaceManagerRpcService.getEndpointIpForDpn(input);
        try {
            RpcResult<GetEndpointIpForDpnOutput> ipForDpnOutputRpcResult = output.get();
            if(ipForDpnOutputRpcResult.isSuccessful()){
                List<IpAddress> localIps = ipForDpnOutputRpcResult.getResult().getLocalIps();
                if(!localIps.isEmpty()) {
                    return localIps.get(0).getIpv4Address().getValue();
                }
            }
        }catch(NullPointerException | InterruptedException | ExecutionException e){
            LOG.warn("Exception when getting port for interface",e);
        }
        return null;
    }

    @Override
    public List<ActionInfo> getInterfaceEgressActions(String ifName) {
        return interfaceManagerRpcService.getEgressActionInfosForInterface(ifName);
    }

    @Override
    public BigInteger getDpnForInterface(Interface intrf) {
        return getDpnForInterface(intrf.getName());
    }
}