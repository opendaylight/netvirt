/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.params.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.params.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.create.input.Profile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.create.input.ProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.start.input.ConfigBuilder;

import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class AlivenessMonitorUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AlivenessMonitorUtils.class);
    private static Map<Long, MacEntry> alivenessCache = new ConcurrentHashMap<>();

    private final DataBroker dataBroker;
    private final INeutronVpnManager neutronvpnService;
    private final AlivenessMonitorService alivenessManager;
    private final IInterfaceManager interfaceManager;
    private final VpnUtil vpnUtil;

    @Inject
    public AlivenessMonitorUtils(DataBroker dataBroker, VpnUtil vpnUtil, INeutronVpnManager neutronvpnService,
                                 AlivenessMonitorService alivenessManager, IInterfaceManager interfaceManager) {
        this.dataBroker = dataBroker;
        this.vpnUtil = vpnUtil;
        this.neutronvpnService = neutronvpnService;
        this.alivenessManager = alivenessManager;
        this.interfaceManager = interfaceManager;
    }

    void startArpMonitoring(MacEntry macEntry, Long arpMonitorProfileId) {
        if (interfaceManager.isExternalInterface(macEntry.getInterfaceName())) {
            LOG.debug("ARP monitoring is currently not supported through external interfaces,"
                    + "skipping ARP monitoring from interface {} for IP {} (last known MAC {})",
                macEntry.getInterfaceName(), macEntry.getIpAddress().getHostAddress(), macEntry.getMacAddress());
            return;
        }
        Optional<IpAddress> gatewayIpOptional =
            vpnUtil.getIpv4GatewayAddressFromInterface(macEntry.getInterfaceName());
        if (!gatewayIpOptional.isPresent()) {
            LOG.info("Interface{} does not have an IPv4 GatewayIp", macEntry.getInterfaceName());
            return;
        }
        final IpAddress gatewayIp = gatewayIpOptional.get();
        Optional<String> gatewayMacOptional = vpnUtil.getGWMacAddressFromInterface(macEntry, gatewayIp);
        if (!gatewayMacOptional.isPresent()) {
            LOG.error("Error while retrieving GatewayMac for interface{}", macEntry.getInterfaceName());
            return;
        }
        final PhysAddress gatewayMac = new PhysAddress(gatewayMacOptional.get());
        if (arpMonitorProfileId == null || arpMonitorProfileId.equals(0L)) {
            Optional<Long> profileIdOptional = allocateProfile(ArpConstants.FAILURE_THRESHOLD,
                    ArpConstants.ARP_CACHE_TIMEOUT_MILLIS, ArpConstants.MONITORING_WINDOW, EtherTypes.Arp);
            if (!profileIdOptional.isPresent()) {
                LOG.error("Error while allocating Profile Id for alivenessMonitorService");
                return;
            }
            arpMonitorProfileId = profileIdOptional.get();
        }

        IpAddress targetIp = new IpAddress(new Ipv4Address(macEntry.getIpAddress().getHostAddress()));
        MonitorStartInput arpMonitorInput = new MonitorStartInputBuilder().setConfig(new ConfigBuilder()
            .setSource(new SourceBuilder().setEndpointType(getSourceEndPointType(macEntry.getInterfaceName(),
                gatewayIp, gatewayMac)).build())
            .setDestination(new DestinationBuilder().setEndpointType(getEndPointIpAddress(targetIp)).build())
            .setMode(MonitoringMode.OneOne)
            .setProfileId(arpMonitorProfileId).build()).build();
        try {
            Future<RpcResult<MonitorStartOutput>> result = alivenessManager.monitorStart(arpMonitorInput);
            RpcResult<MonitorStartOutput> rpcResult = result.get();
            long monitorId;
            if (rpcResult.isSuccessful()) {
                monitorId = rpcResult.getResult().getMonitorId();
                createOrUpdateInterfaceMonitorIdMap(monitorId, macEntry);
                LOG.trace("Started ARP monitoring with id {}", monitorId);
            } else {
                LOG.warn("RPC Call to start monitoring returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when starting monitoring", e);
        }
    }

    void stopArpMonitoring(Long monitorId) {
        MonitorStopInput input = new MonitorStopInputBuilder().setMonitorId(monitorId).build();

        JdkFutures.addErrorLogging(alivenessManager.monitorStop(input), LOG, "Stop monitoring");

        alivenessCache.remove(monitorId);
        return;
    }

    private void createOrUpdateInterfaceMonitorIdMap(long monitorId, MacEntry macEntry) {
        alivenessCache.put(monitorId, macEntry);
    }

    private Interface getSourceEndPointType(String interfaceName, IpAddress ipAddress, PhysAddress gwMac) {
        return new InterfaceBuilder()
            .setInterfaceIp(ipAddress)
            .setInterfaceName(interfaceName)
            .setMacAddress(gwMac)
            .build();
    }

    public Optional<Long> allocateProfile(long failureThreshold, long monitoringInterval, long monitoringWindow,
                                          EtherTypes etherTypes) {
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder()
            .setProfile(new ProfileBuilder().setFailureThreshold(failureThreshold)
                .setMonitorInterval(monitoringInterval).setMonitorWindow(monitoringWindow)
                .setProtocolType(etherTypes).build()).build();
        return createMonitorProfile(input);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Optional<Long> createMonitorProfile(MonitorProfileCreateInput monitorProfileCreateInput) {
        try {
            Future<RpcResult<MonitorProfileCreateOutput>> result =
                alivenessManager.monitorProfileCreate(monitorProfileCreateInput);
            RpcResult<MonitorProfileCreateOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return Optional.of(rpcResult.getResult().getProfileId());
            } else {
                LOG.warn("RPC Call to Get Profile Id Id returned with Errors {}.. Trying to fetch existing profile ID",
                    rpcResult.getErrors());
                try {
                    Profile createProfile = monitorProfileCreateInput.getProfile();
                    Future<RpcResult<MonitorProfileGetOutput>> existingProfile =
                        alivenessManager.monitorProfileGet(buildMonitorGetProfile(createProfile.getMonitorInterval(),
                            createProfile.getMonitorWindow(), createProfile.getFailureThreshold(),
                            createProfile.getProtocolType()));
                    RpcResult<MonitorProfileGetOutput> rpcGetResult = existingProfile.get();
                    if (rpcGetResult.isSuccessful()) {
                        return Optional.of(rpcGetResult.getResult().getProfileId());
                    } else {
                        LOG.warn("RPC Call to Get Existing Profile Id returned with Errors {}",
                            rpcGetResult.getErrors());
                    }
                } catch (Exception e) {
                    LOG.warn("Exception when getting existing profile", e);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when allocating profile Id", e);
        }
        return Optional.absent();
    }

    private MonitorProfileGetInput buildMonitorGetProfile(long monitorInterval, long monitorWindow,
                                                          long failureThreshold, EtherTypes protocolType) {
        MonitorProfileGetInputBuilder buildGetProfile = new MonitorProfileGetInputBuilder();
        org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.get.input
            .ProfileBuilder
            profileBuilder =
            new org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.get
                .input.ProfileBuilder();
        profileBuilder.setFailureThreshold(failureThreshold)
            .setMonitorInterval(monitorInterval)
            .setMonitorWindow(monitorWindow)
            .setProtocolType(protocolType);
        buildGetProfile.setProfile(profileBuilder.build());
        return buildGetProfile.build();
    }

    public static MacEntry getMacEntryFromMonitorId(Long monitorId) {
        return alivenessCache.get(monitorId);
    }

    private static org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411
        .endpoint.endpoint.type.IpAddress getEndPointIpAddress(IpAddress ip) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411
            .endpoint.endpoint.type.IpAddressBuilder().setIpAddress(ip).build();
    }

    public static java.util.Optional<Long> getMonitorIdFromInterface(MacEntry macEntry) {
        java.util.Optional<Long> monitorId = alivenessCache.entrySet().parallelStream()
            .filter(map -> macEntry.equals(map.getValue()))
            .map(Map.Entry::getKey)
            .findFirst();
        return monitorId;
    }

}
