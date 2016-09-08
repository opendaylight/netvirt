/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileCreateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileCreateInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileCreateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileGetInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileGetInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileGetOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStartInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStartInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStartOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStopInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitoringMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.params.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.params.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.create.input.Profile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.create.input.ProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.start.input.ConfigBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AlivenessMonitorUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AlivenessMonitorUtils.class);
    private static Map<Long, MacEntry> alivenessCache = new ConcurrentHashMap<>();

    public static void startArpMonitoring(AlivenessMonitorService alivenessMonitorService, DataBroker dataBroker,
            MacAddress targetMacAddress, InetAddress targetIpAddress, String srcInterfaceName, String vpnName) {
        IpAddress gatewayIp = VpnUtil.getGatewayFromInterface(srcInterfaceName, dataBroker);
        PhysAddress gatewayMac = new PhysAddress(VpnUtil.getMacAddressFromInterface(srcInterfaceName,dataBroker));
        IpAddress targetIp =  new IpAddress(new Ipv4Address(targetIpAddress.getHostAddress()));
        MonitorStartInput arpMonitorInput = new MonitorStartInputBuilder().setConfig(new ConfigBuilder()
                .setSource(new SourceBuilder().setEndpointType(getInterfaceForMonitoring(srcInterfaceName,
                        gatewayIp,gatewayMac)).build())
                .setDestination(new DestinationBuilder().setEndpointType(getInterface(targetIp)).build())
                .setMode(MonitoringMode.OneOne)
                .setProfileId(allocateProfile(alivenessMonitorService, ArpConstants.FAILURE_THRESHOLD, ArpConstants.ARP_CACHE_TIMEOUT_MILLIS, ArpConstants.MONITORING_WINDOW,
                        EtherTypes.Arp)).build()).build();
        try {
            Future<RpcResult<MonitorStartOutput>> result = alivenessMonitorService.monitorStart(arpMonitorInput);
            RpcResult<MonitorStartOutput> rpcResult = result.get();
            long monitorId;
            if (rpcResult.isSuccessful()) {
                monitorId = rpcResult.getResult().getMonitorId();
                MacEntry macEntry = new MacEntry(vpnName, targetMacAddress, targetIpAddress, srcInterfaceName);
                createOrUpdateInterfaceMonitorIdMap(monitorId,macEntry);
                LOG.trace("Started LLDP monitoring with id {}", monitorId);
            } else {
                LOG.warn("RPC Call to start monitoring returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when starting monitoring", e);
        }
    }

    public static void stopArpMonitoring(AlivenessMonitorService alivenessMonitorService,
            Long monitorId) {
        MonitorStopInput input = new MonitorStopInputBuilder().setMonitorId(monitorId).build();
        alivenessMonitorService.monitorStop(input);
        return;
    }

    private static void createOrUpdateInterfaceMonitorIdMap(long monitorId, MacEntry macEntry) {
        alivenessCache.put(monitorId, macEntry);
    }

    private static org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.Interface getInterfaceForMonitoring(String interfaceName, IpAddress ipAddress, PhysAddress gwMac) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.InterfaceBuilder().setInterfaceIp(ipAddress).setInterfaceName(interfaceName).setMacAddress(gwMac).build();
    }

    public static long allocateProfile(AlivenessMonitorService alivenessMonitor, long FAILURE_THRESHOLD, long MONITORING_INTERVAL,
            long MONITORING_WINDOW, EtherTypes etherTypes) {
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder().
                setProfile(new ProfileBuilder().setFailureThreshold(FAILURE_THRESHOLD)
                        .setMonitorInterval(MONITORING_INTERVAL).setMonitorWindow(MONITORING_WINDOW).
                        setProtocolType(etherTypes).build()).build();
        return createMonitorProfile(alivenessMonitor, input);
    }

    public static long createMonitorProfile(AlivenessMonitorService alivenessMonitor, MonitorProfileCreateInput monitorProfileCreateInput) {
        try {
            Future<RpcResult<MonitorProfileCreateOutput>> result = alivenessMonitor.monitorProfileCreate(monitorProfileCreateInput);
            RpcResult<MonitorProfileCreateOutput> rpcResult = result.get();
            if(rpcResult.isSuccessful()) {
                return rpcResult.getResult().getProfileId();
            } else {
                LOG.warn("RPC Call to Get Profile Id Id returned with Errors {}.. Trying to fetch existing profile ID", rpcResult.getErrors());
                try{
                    Profile createProfile = monitorProfileCreateInput.getProfile();
                    Future<RpcResult<MonitorProfileGetOutput>> existingProfile = alivenessMonitor.monitorProfileGet(buildMonitorGetProfile(createProfile.getMonitorInterval(), createProfile.getMonitorWindow(), createProfile.getFailureThreshold(), createProfile.getProtocolType()));
                    RpcResult<MonitorProfileGetOutput> rpcGetResult = existingProfile.get();
                    if(rpcGetResult.isSuccessful()) {
                        return rpcGetResult.getResult().getProfileId();
                    } else {
                        LOG.warn("RPC Call to Get Existing Profile Id returned with Errors {}", rpcGetResult.getErrors());
                    }
                } catch(Exception e) {
                    LOG.warn("Exception when getting existing profile",e);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when allocating profile Id",e);
        }
        return 0;
    }

    private static MonitorProfileGetInput buildMonitorGetProfile(long monitorInterval, long monitorWindow, long failureThreshold, EtherTypes protocolType){
        MonitorProfileGetInputBuilder buildGetProfile = new MonitorProfileGetInputBuilder();
        org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.get.input.ProfileBuilder profileBuilder = new org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.get.input.ProfileBuilder();
        profileBuilder.setFailureThreshold(failureThreshold);
        profileBuilder.setMonitorInterval(monitorInterval);
        profileBuilder.setMonitorWindow(monitorWindow);
        profileBuilder.setProtocolType(protocolType);
        buildGetProfile.setProfile(profileBuilder.build());
        return (buildGetProfile.build());
    }

    public static MacEntry getInterfaceFromMonitorId(Long monitorId) {
        return alivenessCache.get(monitorId);
    }

    private static org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.IpAddress getInterface(IpAddress ip) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.IpAddressBuilder().setIpAddress(ip).build();
    }

    public static Long getMonitorIdFromInterface(MacEntry macEntry) {
        Long monitorId = null ;
        for(Map.Entry<Long, MacEntry> entry:alivenessCache.entrySet())
        {
            if(entry.getValue().equals(macEntry)) {
                monitorId = entry.getKey();
                break;
            }
        }
        return monitorId;
    }
}