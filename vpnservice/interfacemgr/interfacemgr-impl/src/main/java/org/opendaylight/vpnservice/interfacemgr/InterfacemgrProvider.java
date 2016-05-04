/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr;


import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo.InterfaceAdminState;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.interfacemgr.listeners.AlivenessMonitorListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.InterfaceConfigListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.HwVTEPConfigListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.HwVTEPTunnelsStateListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.InterfaceInventoryStateListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.TerminationPointStateListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.InterfaceTopologyStateListener;
import org.opendaylight.vpnservice.interfacemgr.listeners.VlanMemberConfigListener;
import org.opendaylight.vpnservice.interfacemgr.pmcounters.NodeConnectorStatsImpl;
import org.opendaylight.vpnservice.interfacemgr.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.listeners.FlowBasedServicesConfigListener;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.listeners.FlowBasedServicesInterfaceStateListener;
import org.opendaylight.vpnservice.interfacemgr.statusanddiag.InterfaceStatusMonitor;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.table.statistics.rev131215.OpendaylightFlowTableStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.OpendaylightPortStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.*;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


public class InterfacemgrProvider implements BindingAwareProvider, AutoCloseable, IInterfaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(InterfacemgrProvider.class);
    private static final InterfaceStatusMonitor interfaceStatusMonitor = InterfaceStatusMonitor.getInstance();

    private RpcProviderRegistry rpcProviderRegistry;
    private IdManagerService idManager;
    private NotificationService notificationService;
    private AlivenessMonitorService alivenessManager;
    private IMdsalApiManager mdsalManager;
    private InterfaceConfigListener interfaceConfigListener;
    private InterfaceTopologyStateListener topologyStateListener;
    private TerminationPointStateListener terminationPointStateListener;
    private HwVTEPTunnelsStateListener hwVTEPTunnelsStateListener;
    private InterfaceInventoryStateListener interfaceInventoryStateListener;
    private FlowBasedServicesInterfaceStateListener flowBasedServicesInterfaceStateListener;
    private FlowBasedServicesConfigListener flowBasedServicesConfigListener;
    private VlanMemberConfigListener vlanMemberConfigListener;
    private HwVTEPConfigListener hwVTEPConfigListener;
    private AlivenessMonitorListener alivenessMonitorListener;
    private DataBroker dataBroker;
    private InterfaceManagerRpcService interfaceManagerRpcService;
    private BindingAwareBroker.RpcRegistration<OdlInterfaceRpcService> rpcRegistration;
    private NodeConnectorStatsImpl nodeConnectorStatsManager;

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
        interfaceStatusMonitor.registerMbean();
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("InterfacemgrProvider Session Initiated");
        interfaceStatusMonitor.reportStatus("STARTING");
        try {
            dataBroker = session.getSALService(DataBroker.class);
            idManager = rpcProviderRegistry.getRpcService(IdManagerService.class);
            createIdPool();

            alivenessManager = rpcProviderRegistry.getRpcService(AlivenessMonitorService.class);
            interfaceManagerRpcService = new InterfaceManagerRpcService(dataBroker, mdsalManager);
            rpcRegistration = getRpcProviderRegistry().addRpcImplementation(
                    OdlInterfaceRpcService.class, interfaceManagerRpcService);

            interfaceConfigListener = new InterfaceConfigListener(dataBroker, idManager,alivenessManager, mdsalManager);
            interfaceConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);

            interfaceInventoryStateListener = new InterfaceInventoryStateListener(dataBroker, idManager, mdsalManager, alivenessManager);
            interfaceInventoryStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            topologyStateListener = new InterfaceTopologyStateListener(dataBroker);
            topologyStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            hwVTEPTunnelsStateListener = new HwVTEPTunnelsStateListener(dataBroker);
            hwVTEPTunnelsStateListener.registerListener(LogicalDatastoreType.OPERATIONAL,dataBroker);

            terminationPointStateListener = new TerminationPointStateListener(dataBroker);
            terminationPointStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            flowBasedServicesConfigListener = new FlowBasedServicesConfigListener(dataBroker);
            flowBasedServicesConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);

            flowBasedServicesInterfaceStateListener =
                    new FlowBasedServicesInterfaceStateListener(dataBroker);
            flowBasedServicesInterfaceStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

            vlanMemberConfigListener =
                    new VlanMemberConfigListener(dataBroker, idManager, alivenessManager,mdsalManager);
            vlanMemberConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);

            hwVTEPConfigListener = new HwVTEPConfigListener(dataBroker);
            hwVTEPConfigListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);

            alivenessMonitorListener = new org.opendaylight.vpnservice.interfacemgr.listeners.AlivenessMonitorListener(dataBroker);
            notificationService.registerNotificationListener(alivenessMonitorListener);

            //Initialize nodeconnectorstatsimpl
            nodeConnectorStatsManager = new NodeConnectorStatsImpl(dataBroker, notificationService,
                    session.getRpcService(OpendaylightPortStatisticsService.class), session.getRpcService(OpendaylightFlowTableStatisticsService.class));


            interfaceStatusMonitor.reportStatus("OPERATIONAL");
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
            interfaceStatusMonitor.reportStatus("ERROR");
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
    public InterfaceInfo getInterfaceInfo(String interfaceName) {
        //FIXME [ELANBE] This is not working yet, fix this

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                ifState = InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceName,dataBroker);

        if(ifState == null) {
            LOG.error("Interface {} is not present", interfaceName);
            return null;
        }

        Integer lportTag = ifState.getIfIndex();
        Interface intf = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName), dataBroker);
        if (intf == null) {
            LOG.error("Interface {} doesn't exist in config datastore", interfaceName);
            return null;
        }

        NodeConnectorId ncId = IfmUtil.getNodeConnectorIdFromInterface(intf, dataBroker);
        InterfaceInfo.InterfaceType interfaceType = IfmUtil.getInterfaceType(intf);
        InterfaceInfo interfaceInfo = null;
        BigInteger dpId = org.opendaylight.vpnservice.interfacemgr.globals.IfmConstants.INVALID_DPID;
        Integer portNo = org.opendaylight.vpnservice.interfacemgr.globals.IfmConstants.INVALID_PORT_NO;
        if (ncId !=null ) {
            dpId = new BigInteger(IfmUtil.getDpnFromNodeConnectorId(ncId));
            portNo = Integer.parseInt(IfmUtil.getPortNoFromNodeConnectorId(ncId));
        }

        if(interfaceType == InterfaceInfo.InterfaceType.VLAN_INTERFACE){
            interfaceInfo = IfmUtil.getVlanInterfaceInfo(interfaceName, intf, dpId);
        } else if (interfaceType == InterfaceInfo.InterfaceType.VXLAN_TRUNK_INTERFACE || interfaceType == InterfaceInfo.InterfaceType.GRE_TRUNK_INTERFACE) {/*
            trunkInterfaceInfo trunkInterfaceInfo = (TrunkInterfaceInfo) ConfigIfmUtil.getTrunkInterfaceInfo(ifName, ConfigIfmUtil.getInterfaceByIfName(dataBroker, ifName));
            String higherLayerIf = inf.getHigherLayerIf().get(0);
            Interface vlanInterface = ConfigIfmUtil.getInterfaceByIfName(dataBroker, higherLayerIf);
            trunkInterfaceInfo.setPortName(vlanInterface.getAugmentation(BaseConfig.class).getParentInterface());
            trunkInterfaceManager.updateTargetMacAddressInInterfaceInfo(trunkInterfaceInfo, trunkInterface);
            if (trunkInterface.getPhysAddress() != null) {
                trunkInterfaceInfo.setLocalMacAddress(trunkInterface.getPhysAddress().getValue());
            }
            interfaceInfo = trunkInterfaceInfo;
            interfaceInfo.setL2domainGroupId(IfmUtil.getGroupId(OperationalIfmUtil.getInterfaceStateByIfName(dataBroker, higherLayerIf).getIfIndex(), InterfaceType.VLAN_INTERFACE));
        */} else {
            LOG.error("Type of Interface {} is unknown", interfaceName);
            return null;
        }
        interfaceInfo.setDpId(dpId);
        interfaceInfo.setPortNo(portNo);
        interfaceInfo.setAdminState((intf.isEnabled() == true) ? InterfaceAdminState.ENABLED : InterfaceAdminState.DISABLED);
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceTag(lportTag);
        interfaceInfo.setInterfaceType(interfaceType);
        interfaceInfo.setGroupId(IfmUtil.getGroupId(lportTag, interfaceType));
        interfaceInfo.setOpState((ifState.getOperStatus() == OperStatus.Up) ? InterfaceInfo.InterfaceOpState.UP : InterfaceInfo.InterfaceOpState.DOWN);


        return interfaceInfo;

    }

    @Override
    public InterfaceInfo getInterfaceInfoFromOperationalDataStore(String interfaceName, InterfaceInfo.InterfaceType interfaceType) {
        InterfaceInfo interfaceInfo = new InterfaceInfo(interfaceName);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState = InterfaceManagerCommonUtils
                .getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null) {
            LOG.error("Interface {} is not present", interfaceName);
            return null;
        }
        Integer lportTag = ifState.getIfIndex();
        NodeConnectorId ncId = IfmUtil.getNodeConnectorIdFromInterface(ifState);
        if (ncId != null) {
            interfaceInfo.setDpId(new BigInteger(IfmUtil.getDpnFromNodeConnectorId(ncId)));
            interfaceInfo.setPortNo(Integer.parseInt(IfmUtil.getPortNoFromNodeConnectorId(ncId)));
        }
        interfaceInfo.setAdminState((ifState.getAdminStatus() == AdminStatus.Up) ? InterfaceAdminState.ENABLED : InterfaceAdminState.DISABLED);
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceTag(lportTag);
        interfaceInfo.setInterfaceType(interfaceType);
        interfaceInfo.setGroupId(IfmUtil.getGroupId(lportTag, interfaceType));
        interfaceInfo.setOpState((ifState.getOperStatus() == OperStatus.Up) ? InterfaceInfo.InterfaceOpState.UP : InterfaceInfo.InterfaceOpState.DOWN);


        return interfaceInfo;
    }

    @Override
    public InterfaceInfo getInterfaceInfoFromOperationalDataStore(String interfaceName) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState = InterfaceManagerCommonUtils
                .getInterfaceStateFromOperDS(interfaceName, dataBroker);
        if (ifState == null) {
            LOG.error("Interface {} is not present", interfaceName);
            return null;
        }
        Integer lportTag = ifState.getIfIndex();
        InterfaceInfo interfaceInfo = new InterfaceInfo(interfaceName);
        NodeConnectorId ncId = IfmUtil.getNodeConnectorIdFromInterface(ifState);
        if (ncId != null) {
            interfaceInfo.setPortName(IfmUtil.getPortName(dataBroker, ncId));
            interfaceInfo.setDpId(new BigInteger(IfmUtil.getDpnFromNodeConnectorId(ncId)));
            interfaceInfo.setPortNo(Integer.parseInt(IfmUtil.getPortNoFromNodeConnectorId(ncId)));
        }
        interfaceInfo.setAdminState((ifState.getAdminStatus() == AdminStatus.Up) ? InterfaceAdminState.ENABLED : InterfaceAdminState.DISABLED);
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceTag(lportTag);
        interfaceInfo.setOpState((ifState.getOperStatus() == OperStatus.Up) ? InterfaceInfo.InterfaceOpState.UP : InterfaceInfo.InterfaceOpState.DOWN);
        return interfaceInfo;
    }

    public void createVLANInterface(String interfaceName, String portName, BigInteger dpId,  Integer vlanId,
                             String description, IfL2vlan.L2vlanMode l2vlanMode) {
        LOG.info("Create VLAN interface : {}",interfaceName);
        InstanceIdentifier<Interface> interfaceInstanceIdentifier = InterfaceManagerCommonUtils.
                getInterfaceIdentifier(new InterfaceKey(interfaceName));
        IfL2vlanBuilder l2vlanBuilder = new IfL2vlanBuilder().setL2vlanMode(l2vlanMode);
        if(vlanId > 0){
            l2vlanBuilder.setVlanId(new VlanId(vlanId));
        }
        ParentRefs parentRefs = new ParentRefsBuilder().setParentInterface(portName).build();
        Interface inf = new InterfaceBuilder().setEnabled(true).setName(interfaceName).setType(L2vlan.class).
                addAugmentation(IfL2vlan.class, l2vlanBuilder.build()).addAugmentation(ParentRefs.class, parentRefs).
                setDescription(description).build();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        t.put(LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier, inf, true);
    }

    public void bindService(String interfaceName, BoundServices serviceInfo){
        LOG.info("Binding Service : {}",interfaceName);
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<BoundServices> boundServicesInstanceIdentifier = InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, new ServicesInfoKey(interfaceName))
                .child(BoundServices.class, new BoundServicesKey(serviceInfo.getServicePriority())).build();
       // List<BoundServices> services = (List<BoundServices>)serviceInfo.getBoundServices();
        t.put(LogicalDatastoreType.CONFIGURATION, boundServicesInstanceIdentifier, serviceInfo, true);
        t.submit();
    }

    public void unbindService(String interfaceName, BoundServices serviceInfo){
        LOG.info("Unbinding Service  : {}",interfaceName);
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<BoundServices> boundServicesInstanceIdentifier = InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, new ServicesInfoKey(interfaceName))
                .child(BoundServices.class, new BoundServicesKey(serviceInfo.getServicePriority())).build();
        t.delete(LogicalDatastoreType.CONFIGURATION, boundServicesInstanceIdentifier);
        t.submit();
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
        return IfmUtil.getEgressActionInfosForInterface(ifName, 0, dataBroker);
    }

    @Override
    public BigInteger getDpnForInterface(Interface intrf) {
        return getDpnForInterface(intrf.getName());
    }

    @Override
    public List<Interface> getVlanInterfaces() {
        List<Interface> vlanList = new ArrayList<Interface>();
        InstanceIdentifier<Interfaces> interfacesInstanceIdentifier =  InstanceIdentifier.builder(Interfaces.class).build();
        Optional<Interfaces> interfacesOptional  = IfmUtil.read(LogicalDatastoreType.CONFIGURATION, interfacesInstanceIdentifier, dataBroker);
        if (!interfacesOptional.isPresent()) {
            return vlanList;
        }
        Interfaces interfaces = interfacesOptional.get();
        List<Interface> interfacesList = interfaces.getInterface();
        for (Interface iface : interfacesList) {
            if (IfmUtil.getInterfaceType(iface) == InterfaceInfo.InterfaceType.VLAN_INTERFACE) {
                vlanList.add(iface);
            }
        }
        return vlanList;
    }

    @Override
    public List<Interface> getVxlanInterfaces() {
        return InterfaceManagerCommonUtils.getAllTunnelInterfaces(dataBroker,
                InterfaceInfo.InterfaceType.VXLAN_TRUNK_INTERFACE);
    }
}