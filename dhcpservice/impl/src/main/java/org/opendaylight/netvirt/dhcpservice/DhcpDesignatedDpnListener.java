/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.DesignatedSwitchesForExternalTunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpDesignatedDpnListener extends AbstractClusteredAsyncDataTreeChangeListener<DesignatedSwitchForTunnel> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpDesignatedDpnListener.class);
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DataBroker broker;
    private final DhcpserviceConfig config;

    @Inject
    public DhcpDesignatedDpnListener(final DhcpExternalTunnelManager dhcpExternalTunnelManager,
                                     final DataBroker broker,
                                     final DhcpserviceConfig config) {
        super(broker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(DesignatedSwitchesForExternalTunnels.class)
                        .child(DesignatedSwitchForTunnel.class),
                Executors.newListeningSingleThreadExecutor("DhcpDesignatedDpnListener", LOG));
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.broker = broker;
        this.config = config;
    }

    public void init() {
        if (config.isControllerDhcpEnabled()) {
            LOG.info("{} close", getClass().getSimpleName());
        }
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.debug("DhcpDesignatedDpnListener Listener Closed");
    }

    @Override
    public void remove(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel del) {
        LOG.debug("Remove for DesignatedSwitchForTunnel : {}", del);
        dhcpExternalTunnelManager.removeFromLocalCache(Uint64.valueOf(del.getDpId()),
                del.getTunnelRemoteIpAddress(), del.getElanInstanceName());
        dhcpExternalTunnelManager.unInstallDhcpFlowsForVms(del.getElanInstanceName(),
                del.getTunnelRemoteIpAddress(), DhcpServiceUtils.getListOfDpns(broker));
        LOG.trace("Removing designated DPN {} DHCP Arp Flows for Elan {}.", del.getDpId(), del.getElanInstanceName());
        java.util.Optional<SubnetToDhcpPort> subnetDhcpData = dhcpExternalTunnelManager
                .getSubnetDhcpPortData(del.getElanInstanceName());
        if (subnetDhcpData.isPresent()) {
            dhcpExternalTunnelManager.configureDhcpArpRequestResponseFlow(Uint64.valueOf(del.getDpId()),
                    del.getElanInstanceName(), false, del.getTunnelRemoteIpAddress(),
                    subnetDhcpData.get().getPortFixedip(), subnetDhcpData.get().getPortMacaddress());
        }

    }

    @Override
    public void update(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel original,
            DesignatedSwitchForTunnel update) {
        LOG.debug("Update for DesignatedSwitchForTunnel original {}, update {}", original, update);
        dhcpExternalTunnelManager.removeFromLocalCache(Uint64.valueOf(original.getDpId()),
                original.getTunnelRemoteIpAddress(), original.getElanInstanceName());
        Uint64 designatedDpnId = Uint64.valueOf(update.getDpId());
        IpAddress tunnelRemoteIpAddress = update.getTunnelRemoteIpAddress();
        String elanInstanceName = update.getElanInstanceName();
        dhcpExternalTunnelManager.updateLocalCache(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        dhcpExternalTunnelManager.installRemoteMcastMac(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        java.util.Optional<SubnetToDhcpPort> subnetDhcpData = dhcpExternalTunnelManager
                .getSubnetDhcpPortData(elanInstanceName);
        if (subnetDhcpData.isPresent()) {
            LOG.trace("Removing Designated DPN {} DHCP Arp Flows for Elan {}.", original.getDpId(),
                    original.getElanInstanceName());
            dhcpExternalTunnelManager.configureDhcpArpRequestResponseFlow(Uint64.valueOf(original.getDpId()),
                    original.getElanInstanceName(), false, original.getTunnelRemoteIpAddress(),
                    subnetDhcpData.get().getPortFixedip(), subnetDhcpData.get().getPortMacaddress());
            LOG.trace("Configuring DHCP Arp Flows for Designated dpn {} Elan {}", designatedDpnId.toString(),
                elanInstanceName);
            dhcpExternalTunnelManager.configureDhcpArpRequestResponseFlow(designatedDpnId, elanInstanceName,
                    true, tunnelRemoteIpAddress, subnetDhcpData.get().getPortFixedip(),
                    subnetDhcpData.get().getPortMacaddress());
        }
    }

    @Override
    public void add(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel add) {
        LOG.debug("Add for DesignatedSwitchForTunnel : {}", add);
        Uint64 designatedDpnId = Uint64.valueOf(add.getDpId());
        IpAddress tunnelRemoteIpAddress = add.getTunnelRemoteIpAddress();
        String elanInstanceName = add.getElanInstanceName();
        dhcpExternalTunnelManager.updateLocalCache(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        dhcpExternalTunnelManager.installRemoteMcastMac(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        LOG.trace("Configuring DHCP Arp Flows for Designated dpn {} Elan {}", designatedDpnId, elanInstanceName);
        java.util.Optional<SubnetToDhcpPort> subnetDhcpData = dhcpExternalTunnelManager
                .getSubnetDhcpPortData(elanInstanceName);
        if (subnetDhcpData.isPresent()) {
            dhcpExternalTunnelManager.configureDhcpArpRequestResponseFlow(designatedDpnId, elanInstanceName,
                    true, tunnelRemoteIpAddress, subnetDhcpData.get().getPortFixedip(),
                    subnetDhcpData.get().getPortMacaddress());
        }
    }
}
