/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.InterfaceMonitorMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileCreateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileCreateInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileCreateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileGetInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileGetInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProfileGetOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStartInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStartInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStartOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitorStopInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.MonitoringMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411._interface.monitor.map.InterfaceMonitorEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411._interface.monitor.map.InterfaceMonitorEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.endpoint.endpoint.type.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.params.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.params.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.create.input.Profile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.profile.create.input.ProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.monitor.start.input.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
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
    private final VpnConfig vpnConfig;

    @Inject
    public AlivenessMonitorUtils(DataBroker dataBroker, VpnUtil vpnUtil, INeutronVpnManager neutronvpnService,
            AlivenessMonitorService alivenessManager, IInterfaceManager interfaceManager, VpnConfig vpnConfig) {
        this.dataBroker = dataBroker;
        this.vpnUtil = vpnUtil;
        this.neutronvpnService = neutronvpnService;
        this.alivenessManager = alivenessManager;
        this.interfaceManager = interfaceManager;
        this.vpnConfig = vpnConfig;
    }

    void startIpMonitoring(MacEntry macEntry, Long ipMonitorProfileId) {
        if (interfaceManager.isExternalInterface(macEntry.getInterfaceName())) {
            LOG.debug("IP monitoring is currently not supported through external interfaces,"
                    + "skipping IP monitoring from interface {} for IP {} (last known MAC {})",
                macEntry.getInterfaceName(), macEntry.getIpAddress().getHostAddress(), macEntry.getMacAddress());
            return;
        }
        Optional<IpAddress> gatewayIpOptional =
            vpnUtil.getGatewayIpAddressFromInterface(macEntry);
        if (!gatewayIpOptional.isPresent()) {
            LOG.info("Interface{} does not have an GatewayIp", macEntry.getInterfaceName());
            return;
        }
        final IpAddress gatewayIp = gatewayIpOptional.get();
        Optional<String> gatewayMacOptional = vpnUtil.getGWMacAddressFromInterface(macEntry, gatewayIp);
        if (!gatewayMacOptional.isPresent()) {
            LOG.error("Error while retrieving GatewayMac for interface{}", macEntry.getInterfaceName());
            return;
        }

        final IpAddress targetIp = IetfInetUtil.INSTANCE.ipAddressFor(macEntry.getIpAddress());
        if (ipMonitorProfileId == null || ipMonitorProfileId.equals(0L)) {
            Optional<Uint32> profileIdOptional = allocateIpMonitorProfile(targetIp);
            if (!profileIdOptional.isPresent()) {
                LOG.error("startIpMonitoring: Error while allocating Profile Id for IP={}", targetIp);
                return;
            }
            ipMonitorProfileId = profileIdOptional.get().toJava();
        }

        final PhysAddress gatewayMac = new PhysAddress(gatewayMacOptional.get());
        MonitorStartInput ipMonitorInput = new MonitorStartInputBuilder().setConfig(new ConfigBuilder()
            .setSource(new SourceBuilder().setEndpointType(getSourceEndPointType(macEntry.getInterfaceName(),
                gatewayIp, gatewayMac)).build())
            .setDestination(new DestinationBuilder().setEndpointType(getEndPointIpAddress(targetIp)).build())
            .setMode(MonitoringMode.OneOne)
            .setProfileId(ipMonitorProfileId).build()).build();
        try {
            Future<RpcResult<MonitorStartOutput>> result = alivenessManager.monitorStart(ipMonitorInput);
            RpcResult<MonitorStartOutput> rpcResult = result.get();
            long monitorId;
            if (rpcResult.isSuccessful()) {
                monitorId = rpcResult.getResult().getMonitorId().toJava();
                createOrUpdateInterfaceMonitorIdMap(monitorId, macEntry);
                LOG.trace("Started IP monitoring with id {}", monitorId);
            } else {
                LOG.warn("RPC Call to start monitoring returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when starting monitoring", e);
        }
    }

    void stopIpMonitoring(Uint32 monitorId) {
        MonitorStopInput input = new MonitorStopInputBuilder().setMonitorId(monitorId).build();

        JdkFutures.addErrorLogging(alivenessManager.monitorStop(input), LOG, "Stop monitoring");

        alivenessCache.remove(monitorId.toJava());
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

    private Optional<Uint32> allocateIpMonitorProfile(IpAddress targetIp) {
        Optional<Uint32> profileIdOptional = Optional.empty();
        if (targetIp.getIpv4Address() != null) {
            profileIdOptional = allocateArpMonitorProfile();
        } else if (targetIp.getIpv6Address() != null) {
            profileIdOptional = allocateIpv6NaMonitorProfile();
        }
        return profileIdOptional;
    }

    public Optional<Uint32> allocateArpMonitorProfile() {
        return allocateProfile(ArpConstants.FAILURE_THRESHOLD, ArpConstants.ARP_CACHE_TIMEOUT_MILLIS,
                ArpConstants.MONITORING_WINDOW, MonitorProtocolType.Arp);
    }

    public Optional<Uint32> allocateIpv6NaMonitorProfile() {
        Long monitorInterval = vpnConfig.getIpv6NdMonitorInterval().toJava() * 1000; // converting to milliseconds
        return allocateProfile(vpnConfig.getIpv6NdMonitorFailureThreshold().toJava(), monitorInterval,
                vpnConfig.getIpv6NdMonitorWindow().toJava(), MonitorProtocolType.Ipv6Nd);
    }

    public Optional<Uint32> allocateProfile(long failureThreshold, long monitoringInterval, long monitoringWindow,
            MonitorProtocolType protocolType) {
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder()
            .setProfile(new ProfileBuilder().setFailureThreshold(failureThreshold)
                .setMonitorInterval(monitoringInterval).setMonitorWindow(monitoringWindow)
                .setProtocolType(protocolType).build()).build();
        return createMonitorProfile(input);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Optional<Uint32> createMonitorProfile(MonitorProfileCreateInput monitorProfileCreateInput) {
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
                        alivenessManager.monitorProfileGet(buildMonitorGetProfile(
                            createProfile.getMonitorInterval().toJava(),
                            createProfile.getMonitorWindow().toJava(), createProfile.getFailureThreshold().toJava(),
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
        return Optional.empty();
    }

    private MonitorProfileGetInput buildMonitorGetProfile(long monitorInterval, long monitorWindow,
            long failureThreshold, MonitorProtocolType protocolType) {
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

    public java.util.Optional<Uint32> getMonitorIdFromInterface(MacEntry macEntry) {
        String interfaceName = macEntry.getInterfaceName();
        java.util.Optional<Uint32> monitorId = java.util.Optional.empty();
        Optional<InterfaceMonitorEntry> interfaceMonitorEntryOptional = MDSALUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, getInterfaceMonitorMapId(interfaceName));
        if (interfaceMonitorEntryOptional.isPresent()) {
            return java.util.Optional.of(interfaceMonitorEntryOptional.get().getMonitorIds().get(0));
        }
        return monitorId;
    }

    private InstanceIdentifier<InterfaceMonitorEntry> getInterfaceMonitorMapId(String interfaceName) {
        return InstanceIdentifier.builder(InterfaceMonitorMap.class)
                .child(InterfaceMonitorEntry.class, new InterfaceMonitorEntryKey(interfaceName)).build();
    }

}
