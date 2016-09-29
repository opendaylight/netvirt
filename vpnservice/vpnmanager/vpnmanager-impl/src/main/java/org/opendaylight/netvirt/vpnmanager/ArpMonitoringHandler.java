/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.net.InetAddress;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.clustering.EntityOwnerUtils;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnVipipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.vipip.port.data.VpnVipipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class ArpMonitoringHandler extends AsyncClusteredDataTreeChangeListenerBase<VpnVipipToPort, ArpMonitoringHandler> {
    private static final Logger LOG = LoggerFactory.getLogger(ArpMonitoringHandler.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService interfaceRpc;
    private final IMdsalApiManager mdsalManager;
    private final AlivenessMonitorService alivenessManager;
    private final INeutronVpnManager neutronVpnService;
    private final EntityOwnershipService entityOwnershipService;
    private Long arpMonitorProfileId = 0L;

    public ArpMonitoringHandler(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceRpc,
            IMdsalApiManager mdsalManager, AlivenessMonitorService alivenessManager, INeutronVpnManager neutronVpnService, EntityOwnershipService entityOwnershipService) {
        super(VpnVipipToPort.class, ArpMonitoringHandler.class);
        this.dataBroker = dataBroker;
        this.interfaceRpc = interfaceRpc;
        this.mdsalManager = mdsalManager;
        this.alivenessManager = alivenessManager;
        this.neutronVpnService = neutronVpnService;
        this.entityOwnershipService = entityOwnershipService;
    }

    public void start() {
        Optional <Long> profileIdOptional = AlivenessMonitorUtils.allocateProfile(alivenessManager,
                ArpConstants.FAILURE_THRESHOLD, ArpConstants.ARP_CACHE_TIMEOUT_MILLIS, ArpConstants.MONITORING_WINDOW,
                EtherTypes.Arp);
        if(profileIdOptional.isPresent()) {
            arpMonitorProfileId = profileIdOptional.get();
        } else {
            LOG.error("Error while allocating Profile Id", profileIdOptional);
        }
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
        try {
            EntityOwnerUtils.registerEntityCandidateForOwnerShip(entityOwnershipService,
                    VpnConstants.ARP_MONITORING_ENTITY, VpnConstants.ARP_MONITORING_ENTITY,
                    null/*listener*/);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("failed to register the entity");
        }
    }

    @Override
    protected InstanceIdentifier<VpnVipipToPort> getWildCardPath() {
        return InstanceIdentifier.create(VpnVipipPortData.class).child(VpnVipipToPort.class);
    }

    public static InstanceIdentifier<VpnPortipToPort> getVpnPortipToPortInstanceOpDataIdentifier(String ip, String vpnName) {
        return InstanceIdentifier.builder(NeutronVpnPortipPortData.class)
                .child(VpnPortipToPort.class, new VpnPortipToPortKey(ip, vpnName)).build();
    }

    @Override
    protected ArpMonitoringHandler getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void update(InstanceIdentifier<VpnVipipToPort> id, VpnVipipToPort value,
            VpnVipipToPort dataObjectModificationAfter) {
        VpnUtil.runOnlyInLeaderNode(entityOwnershipService, () -> {
            try {
                if(value.getMacAddress() == null || dataObjectModificationAfter.getMacAddress() == null) {
                    LOG.warn("The Macaddress received is null for VpnPortipToPort {}, ignoring the DTCN", dataObjectModificationAfter);
                    return;
                }
                remove(id, value);
                add(id, dataObjectModificationAfter);
            } catch (Exception e) {
                LOG.error("Error in handling update to vpnPortIpToPort for vpnName {} and IP Address {}", value.getVpnName() , value.getPortFixedip());
                e.printStackTrace();
            }});
    }

    @Override
    protected void add(InstanceIdentifier<VpnVipipToPort> identifier, VpnVipipToPort value) {
        VpnUtil.runOnlyInLeaderNode(entityOwnershipService, () -> {
            try {
                InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
                if(value.getMacAddress() == null) {
                    LOG.warn("The Macaddress received is null for VpnPortipToPort {}, ignoring the DTCN", value);
                    return;
                }
                MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
                String vpnName =  value.getVpnName();
                String interfaceName =  value.getPortName();
                MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName);
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(), vpnName),
                        new ArpMonitorStartTask(macEntry, arpMonitorProfileId, dataBroker, alivenessManager,
                                interfaceRpc, neutronVpnService));
            } catch (Exception e) {
                LOG.error("Error in deserializing packet {} with exception {}", value, e);
            }});
    }

    @Override
    protected void remove(InstanceIdentifier<VpnVipipToPort> key, VpnVipipToPort value) {
        VpnUtil.runOnlyInLeaderNode(entityOwnershipService, () -> {
            try {
                InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
                if(value.getMacAddress() == null) {
                    LOG.warn("The Macaddress received is null for VpnPortipToPort {}, ignoring the DTCN", value);
                    return;
                }
                MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
                String vpnName =  value.getVpnName();
                String interfaceName =  value.getPortName();
                MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName);
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(), vpnName),
                        new ArpMonitorStopTask(macEntry, dataBroker, alivenessManager));
            } catch (Exception e) {
                LOG.error("Error in deserializing packet {} with exception {}", value, e);
            }});

    }

    static String buildJobKey(String ip, String vpnName) {
        return new StringBuilder(ArpConstants.ARPJOB).append(ip).append(vpnName).toString();
    }
}

