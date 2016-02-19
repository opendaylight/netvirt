/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.commons;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.params.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profile.create.input.ProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.start.input.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.monitor.id.map.InterfaceMonitorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.monitor.id.map.InterfaceMonitorIdBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.monitor.id.map.InterfaceMonitorIdKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.monitor.id._interface.map.MonitorIdInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.monitor.id._interface.map.MonitorIdInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.monitor.id._interface.map.MonitorIdInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AlivenessMonitorUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AlivenessMonitorUtils.class);
    private static final int FAILURE_THRESHOLD = 4;
    private static final int MONITORING_INTERVAL = 10000;
    private static final int MONITORING_WINDOW = 4;

    public static void startLLDPMonitoring(AlivenessMonitorService alivenessMonitorService, DataBroker dataBroker,
                                            Interface trunkInterface) {
        //LLDP monitoring for the trunk interface
        String trunkInterfaceName = trunkInterface.getName();
        IfTunnel ifTunnel = trunkInterface.getAugmentation(IfTunnel.class);
        if(ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
            MonitorStartInput lldpMonitorInput = new MonitorStartInputBuilder().setConfig(new ConfigBuilder()
                    .setSource(new SourceBuilder().setEndpointType(getInterfaceForMonitoring(trunkInterfaceName,
                            ifTunnel.getTunnelSource())).build())
                    .setMode(MonitoringMode.OneOne)
                    .setProfileId(allocateProfile(alivenessMonitorService, FAILURE_THRESHOLD, MONITORING_INTERVAL, MONITORING_WINDOW,
                            EtherTypes.Lldp)).build()).build();
            try {
                Future<RpcResult<MonitorStartOutput>> result = alivenessMonitorService.monitorStart(lldpMonitorInput);
                RpcResult<MonitorStartOutput> rpcResult = result.get();
                long monitorId;
                if (rpcResult.isSuccessful()) {
                    monitorId = rpcResult.getResult().getMonitorId();
                    createOrUpdateInterfaceMonitorIdMap(dataBroker, trunkInterfaceName, monitorId);
                    createOrUpdateMonitorIdInterfaceMap(dataBroker, trunkInterfaceName, monitorId);
                    LOG.trace("Started LLDP monitoring with id {}", monitorId);
                } else {
                    LOG.warn("RPC Call to start monitoring returned with Errors {}", rpcResult.getErrors());
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.warn("Exception when starting monitoring", e);
            }
        }
    }

    public static void stopLLDPMonitoring(AlivenessMonitorService alivenessMonitorService, DataBroker dataBroker,
                                          Interface trunkInterface) {
        IfTunnel ifTunnel = trunkInterface.getAugmentation(IfTunnel.class);
        if(!ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)){
            return;
        }
        List<Long> monitorIds = getMonitorIdForInterface(dataBroker, trunkInterface.getName());
        if (monitorIds == null) {
            LOG.error("Monitor Id doesn't exist for Interface {}", trunkInterface);
            return;
        }
        for (Long monitorId : monitorIds) {
            String interfaceName = getInterfaceFromMonitorId(dataBroker, monitorId);
            if (interfaceName != null) {
                MonitorStopInput input = new MonitorStopInputBuilder().setMonitorId(monitorId).build();
                alivenessMonitorService.monitorStop(input);
                removeMonitorIdInterfaceMap(dataBroker, monitorId);
                removeMonitorIdFromInterfaceMonitorIdMap(dataBroker, interfaceName, monitorId);
                return;
            }
        }
    }

    public static String getInterfaceFromMonitorId(DataBroker broker, Long monitorId) {
        InstanceIdentifier<MonitorIdInterface> id = InstanceIdentifier.builder(MonitorIdInterfaceMap.class).child(MonitorIdInterface.class, new MonitorIdInterfaceKey(monitorId)).build();
        Optional<MonitorIdInterface> interfaceMonitorIdMap = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if(interfaceMonitorIdMap.isPresent()) {
            return interfaceMonitorIdMap.get().getInterfaceName();
        }
        return null;
    }

    private static void removeMonitorIdInterfaceMap(DataBroker broker, long monitorId) {
        InstanceIdentifier<MonitorIdInterface> id = InstanceIdentifier.builder(MonitorIdInterfaceMap.class).child(MonitorIdInterface.class, new MonitorIdInterfaceKey(monitorId)).build();
        Optional<MonitorIdInterface> monitorIdInterfaceMap = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if(monitorIdInterfaceMap.isPresent()) {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id);
        }
    }

    private static void removeMonitorIdFromInterfaceMonitorIdMap(DataBroker broker, String infName, long monitorId) {
        InstanceIdentifier<InterfaceMonitorId> id = InstanceIdentifier.builder(InterfaceMonitorIdMap.class).child(InterfaceMonitorId.class, new InterfaceMonitorIdKey(infName)).build();
        Optional<InterfaceMonitorId> interfaceMonitorIdMap = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if(interfaceMonitorIdMap.isPresent()) {
            InterfaceMonitorId interfaceMonitorIdInstance = interfaceMonitorIdMap.get();
            List<Long> existingMonitorIds = interfaceMonitorIdInstance.getMonitorId();
            if (existingMonitorIds != null && existingMonitorIds.contains(monitorId)) {
                existingMonitorIds.remove(monitorId);
                InterfaceMonitorIdBuilder interfaceMonitorIdBuilder = new InterfaceMonitorIdBuilder();
                interfaceMonitorIdInstance = interfaceMonitorIdBuilder.setKey(new InterfaceMonitorIdKey(infName)).setMonitorId(existingMonitorIds).build();
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id, interfaceMonitorIdInstance);
            }
        }
    }

    private static org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.Interface getInterfaceForMonitoring(String interfaceName, org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress ipAddress) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.
                endpoint.type.InterfaceBuilder().setInterfaceIp(ipAddress).setInterfaceName(interfaceName).build();
    }

    protected void handleTunnelMonitorEnabledUpdates(AlivenessMonitorService alivenessMonitorService, DataBroker dataBroker,
                                                     List<String> interfaceNames, boolean origMonitorEnabled, boolean updatedMonitorEnabled) {
        for (String interfaceName : interfaceNames) {
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface tunnelInterface =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName), dataBroker);
            IfTunnel ifTunnel = tunnelInterface.getAugmentation(IfTunnel.class);
            InterfaceManagerCommonUtils.updateTunnelMonitorDetailsInConfigDS(dataBroker, interfaceName, updatedMonitorEnabled, 3);
            // Check if monitoring is started already
            if (getMonitorIdForInterface(dataBroker, interfaceName) != null) {
                // Get updated Interface details from Config DS
                if(ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
                    if (updatedMonitorEnabled) {
                        startLLDPMonitoring(alivenessMonitorService, dataBroker, tunnelInterface);
                    } else {
                        stopLLDPMonitoring(alivenessMonitorService, dataBroker, tunnelInterface);
                    }
                }
            }
        }
    }

    protected void handleTunnelMonitorIntervalUpdates(AlivenessMonitorService alivenessMonitorService, DataBroker dataBroker,
                                                      List<String> interfaceNames, long origMonitorInterval, long updatedMonitorInterval) {
        for (String interfaceName : interfaceNames) {
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface tunnelInterface =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(interfaceName), dataBroker);
            IfTunnel ifTunnel = tunnelInterface.getAugmentation(IfTunnel.class);
            InterfaceManagerCommonUtils.updateTunnelMonitorDetailsInConfigDS(dataBroker, interfaceName, ifTunnel.isMonitorEnabled(), updatedMonitorInterval);
            // Restart LLDP monitoring only if it's started already
            List<Long> monitorIds = getMonitorIdForInterface(dataBroker, interfaceName);
            if (monitorIds != null && monitorIds.size() > 1) {
                // Get updated Interface details from Config DS
                if(ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
                    stopLLDPMonitoring(alivenessMonitorService, dataBroker, tunnelInterface);
                    startLLDPMonitoring(alivenessMonitorService, dataBroker,tunnelInterface);
                }
            }
        }
        // Delete old profile from Aliveness Manager
        if (origMonitorInterval > 0) {
            long profileId = allocateProfile(alivenessMonitorService, 4, origMonitorInterval, 4, EtherTypes.Lldp);
            MonitorProfileDeleteInput  profileDeleteInput = new MonitorProfileDeleteInputBuilder().setProfileId(profileId).build();
            alivenessMonitorService.monitorProfileDelete(profileDeleteInput);
        }
    }


    public static void createOrUpdateInterfaceMonitorIdMap(DataBroker broker, String infName, long monitorId) {
        InterfaceMonitorId interfaceMonitorIdInstance;
        List<Long> existingMonitorIds;
        InterfaceMonitorIdBuilder interfaceMonitorIdBuilder = new InterfaceMonitorIdBuilder();
        InstanceIdentifier<InterfaceMonitorId> id = InstanceIdentifier.builder(InterfaceMonitorIdMap.class).child(InterfaceMonitorId.class, new InterfaceMonitorIdKey(infName)).build();
        Optional<InterfaceMonitorId> interfaceMonitorIdMap = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if (interfaceMonitorIdMap.isPresent()) {
            interfaceMonitorIdInstance = interfaceMonitorIdMap.get();
            existingMonitorIds = interfaceMonitorIdInstance.getMonitorId();
            if (existingMonitorIds == null) {
                existingMonitorIds = new ArrayList<>();
            }
            if (!existingMonitorIds.contains(monitorId)) {
                existingMonitorIds.add(monitorId);
                interfaceMonitorIdInstance = interfaceMonitorIdBuilder.setKey(new InterfaceMonitorIdKey(infName)).setMonitorId(existingMonitorIds).build();
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, id, interfaceMonitorIdInstance);
            }
        } else {
            existingMonitorIds = new ArrayList<>();
            existingMonitorIds.add(monitorId);
            interfaceMonitorIdInstance = interfaceMonitorIdBuilder.setMonitorId(existingMonitorIds).setKey(new InterfaceMonitorIdKey(infName)).setInterfaceName(infName).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id, interfaceMonitorIdInstance);
        }
    }

    public static void createOrUpdateMonitorIdInterfaceMap(DataBroker broker,String infName,  long monitorId) {
        MonitorIdInterface monitorIdInterfaceInstance;
        String existinginterfaceName;
        MonitorIdInterfaceBuilder monitorIdInterfaceBuilder = new MonitorIdInterfaceBuilder();
        InstanceIdentifier<MonitorIdInterface> id = InstanceIdentifier.builder(MonitorIdInterfaceMap.class).child(MonitorIdInterface.class, new MonitorIdInterfaceKey(monitorId)).build();
        Optional<MonitorIdInterface> monitorIdInterfaceMap = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if(monitorIdInterfaceMap.isPresent()) {
            monitorIdInterfaceInstance = monitorIdInterfaceMap.get();
            existinginterfaceName = monitorIdInterfaceInstance.getInterfaceName();
            if(!existinginterfaceName.equals(infName)) {
                monitorIdInterfaceInstance = monitorIdInterfaceBuilder.setKey(new MonitorIdInterfaceKey(monitorId)).setInterfaceName(infName).build();
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, id, monitorIdInterfaceInstance);
            }
        } else {
            monitorIdInterfaceInstance = monitorIdInterfaceBuilder.setMonitorId(monitorId).setKey(new MonitorIdInterfaceKey(monitorId)).setInterfaceName(infName).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id, monitorIdInterfaceInstance);
        }
    }

    public static List<Long> getMonitorIdForInterface(DataBroker broker, String infName) {
        InstanceIdentifier<InterfaceMonitorId> id = InstanceIdentifier.builder(InterfaceMonitorIdMap.class).child(InterfaceMonitorId.class, new InterfaceMonitorIdKey(infName)).build();
        Optional<InterfaceMonitorId> interfaceMonitorIdMap = IfmUtil.read(LogicalDatastoreType.OPERATIONAL, id, broker);
        if(interfaceMonitorIdMap.isPresent()) {
            return interfaceMonitorIdMap.get().getMonitorId();
        }
        return null;
    }

    public static long allocateProfile(AlivenessMonitorService alivenessMonitor, long failureThreshold, long interval, long window, EtherTypes etherType ) {
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder().setProfile(new ProfileBuilder().setFailureThreshold(failureThreshold)
                .setMonitorInterval(interval).setMonitorWindow(window).setProtocolType(etherType).build()).build();
        try {
            Future<RpcResult<MonitorProfileCreateOutput>> result = alivenessMonitor.monitorProfileCreate(input);
            RpcResult<MonitorProfileCreateOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getProfileId();
            } else {
                LOG.warn("RPC Call to Get Profile Id Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when allocating profile Id",e);
        }
        return 0;
    }
}