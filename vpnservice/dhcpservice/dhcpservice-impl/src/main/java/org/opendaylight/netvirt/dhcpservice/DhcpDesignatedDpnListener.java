/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.DesignatedSwitchesForExternalTunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpDesignatedDpnListener
        extends AsyncClusteredDataTreeChangeListenerBase<DesignatedSwitchForTunnel, DhcpDesignatedDpnListener> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpDesignatedDpnListener.class);
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DataBroker broker;
    private final DhcpserviceConfig config;

    @Inject
    public DhcpDesignatedDpnListener(final DhcpExternalTunnelManager dhcpExternalTunnelManager,
                                     final DataBroker broker,
                                     final DhcpserviceConfig config) {
        super(DesignatedSwitchForTunnel.class, DhcpDesignatedDpnListener.class);
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.broker = broker;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (config.isControllerDhcpEnabled()) {
            registerListener(LogicalDatastoreType.CONFIGURATION, broker);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.debug("DhcpDesignatedDpnListener Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel del) {
        LOG.debug("Remove for DesignatedSwitchForTunnel : {}", del);
        dhcpExternalTunnelManager.removeFromLocalCache(BigInteger.valueOf(del.getDpId()),
                del.getTunnelRemoteIpAddress(), del.getElanInstanceName());
        dhcpExternalTunnelManager.unInstallDhcpFlowsForVms(del.getElanInstanceName(),
                del.getTunnelRemoteIpAddress(), DhcpServiceUtils.getListOfDpns(broker));
    }

    @Override
    protected void update(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel original,
            DesignatedSwitchForTunnel update) {
        LOG.debug("Update for DesignatedSwitchForTunnel original {}, update {}", original, update);
        BigInteger designatedDpnId = BigInteger.valueOf(update.getDpId());
        IpAddress tunnelRemoteIpAddress = update.getTunnelRemoteIpAddress();
        String elanInstanceName = update.getElanInstanceName();
        dhcpExternalTunnelManager.removeFromLocalCache(BigInteger.valueOf(original.getDpId()),
                original.getTunnelRemoteIpAddress(), original.getElanInstanceName());
        dhcpExternalTunnelManager.updateLocalCache(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        dhcpExternalTunnelManager.installRemoteMcastMac(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
    }

    @Override
    protected void add(InstanceIdentifier<DesignatedSwitchForTunnel> identifier, DesignatedSwitchForTunnel add) {
        LOG.debug("Add for DesignatedSwitchForTunnel : {}", add);
        BigInteger designatedDpnId = BigInteger.valueOf(add.getDpId());
        IpAddress tunnelRemoteIpAddress = add.getTunnelRemoteIpAddress();
        String elanInstanceName = add.getElanInstanceName();
        dhcpExternalTunnelManager.updateLocalCache(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
        dhcpExternalTunnelManager.installRemoteMcastMac(designatedDpnId, tunnelRemoteIpAddress, elanInstanceName);
    }

    @Override
    protected InstanceIdentifier<DesignatedSwitchForTunnel> getWildCardPath() {
        return InstanceIdentifier.create(DesignatedSwitchesForExternalTunnels.class)
                .child(DesignatedSwitchForTunnel.class);
    }

    @Override
    protected DhcpDesignatedDpnListener getDataTreeChangeListener() {
        return DhcpDesignatedDpnListener.this;
    }
}
