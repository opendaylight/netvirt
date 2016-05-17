/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpProvider.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private DhcpPktHandler dhcpPktHandler;
    private Registration packetListener = null;
    private final NotificationProviderService notificationService;
    private DhcpManager dhcpManager;
    private NodeListener dhcpNodeListener;
    private final INeutronVpnManager neutronVpnManager;
    private DhcpConfigListener dhcpConfigListener;
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private DhcpInterfaceEventListener dhcpInterfaceEventListener;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private DhcpNeutronPortListener dhcpNeutronPortListener;
    private DhcpLogicalSwitchListener dhcpLogicalSwitchListener;
    private DhcpUCastMacListener dhcpUCastMacListener;
    private final ItmRpcService itmRpcService;
    private DhcpInterfaceConfigListener dhcpInterfaceConfigListener;
    private final EntityOwnershipService entityOwnershipService;
    private DhcpDesignatedDpnListener dhcpDesignatedDpnListener;
    private DhcpL2GatewayConnectionListener dhcpL2GatewayConnectionListener;
    private final PacketProcessingService pktProcessingService;

    public DhcpProvider(final DataBroker dataBroker,
                        final NotificationProviderService notificationProviderService,
                        final EntityOwnershipService entityOwnershipService,
                        final IMdsalApiManager mdsalApiManager,
                        final INeutronVpnManager neutronVpnManager,
                        final OdlInterfaceRpcService odlInterfaceRpcService,
                        final ItmRpcService itmRpcService,
                        final PacketProcessingService packetProcessingService) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationProviderService;
        this.entityOwnershipService = entityOwnershipService;
        this.mdsalManager = mdsalApiManager;
        this.neutronVpnManager = neutronVpnManager;
        this.interfaceManagerRpc = odlInterfaceRpcService;
        this.itmRpcService = itmRpcService;
        this.pktProcessingService = packetProcessingService;
    }

    public void start() {
        LOG.info("DhcpProvider Session Initiated");
        try {
            dhcpManager = new DhcpManager(dataBroker);
            dhcpManager.setMdsalManager(mdsalManager);
            dhcpManager.setNeutronVpnService(neutronVpnManager);
            dhcpExternalTunnelManager = new DhcpExternalTunnelManager(dataBroker, mdsalManager, itmRpcService, entityOwnershipService);
            dhcpPktHandler = new DhcpPktHandler(dataBroker, dhcpManager, dhcpExternalTunnelManager);
            dhcpPktHandler.setPacketProcessingService(pktProcessingService);
            dhcpPktHandler.setInterfaceManagerRpc(interfaceManagerRpc);
            packetListener = notificationService.registerNotificationListener(dhcpPktHandler);
            dhcpNodeListener = new NodeListener(dataBroker, dhcpManager, dhcpExternalTunnelManager);
            dhcpConfigListener = new DhcpConfigListener(dataBroker, dhcpManager);
            dhcpInterfaceEventListener = new DhcpInterfaceEventListener(dhcpManager, dataBroker, dhcpExternalTunnelManager);
            dhcpInterfaceConfigListener = new DhcpInterfaceConfigListener(dataBroker, dhcpExternalTunnelManager);
            dhcpLogicalSwitchListener = new DhcpLogicalSwitchListener(dhcpExternalTunnelManager, dataBroker);
            dhcpUCastMacListener = new DhcpUCastMacListener(dhcpExternalTunnelManager, dataBroker);
            dhcpUCastMacListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
            dhcpNeutronPortListener = new DhcpNeutronPortListener(dataBroker, dhcpExternalTunnelManager);
            dhcpNeutronPortListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            dhcpDesignatedDpnListener = new DhcpDesignatedDpnListener(dhcpExternalTunnelManager, dataBroker);
            dhcpDesignatedDpnListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            dhcpL2GatewayConnectionListener = new DhcpL2GatewayConnectionListener(dataBroker, dhcpExternalTunnelManager);
            dhcpL2GatewayConnectionListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        } catch (Exception e) {
            LOG.error("Error initializing services {}", e);
        }
    }

    @Override
    public void close() throws Exception {
        if(packetListener != null) {
            packetListener.close();
        }
        if(dhcpPktHandler != null) {
            dhcpPktHandler.close();
        }
        if(dhcpNodeListener != null) {
            dhcpNodeListener.close();
        }
        if (dhcpConfigListener != null) {
            dhcpConfigListener.close();
        }
        if (dhcpInterfaceEventListener != null) {
            dhcpInterfaceEventListener.close();
        }
        if (dhcpInterfaceConfigListener != null) {
            dhcpInterfaceConfigListener.close();
        }
        if (dhcpLogicalSwitchListener != null) {
            dhcpLogicalSwitchListener.close();
        }
        LOG.info("DhcpProvider closed");
    }
}
