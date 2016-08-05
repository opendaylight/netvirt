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
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpProvider.class);
    private IMdsalApiManager mdsalManager;
    private DhcpPktHandler dhcpPktHandler;
    private Registration packetListener = null;
    private NotificationProviderService notificationService;
    private DhcpManager dhcpManager;
    private NodeListener dhcpNodeListener;
    private INeutronVpnManager neutronVpnManager;
    private DhcpConfigListener dhcpConfigListener;
    private OdlInterfaceRpcService interfaceManagerRpc;
    private DhcpInterfaceEventListener dhcpInterfaceEventListener;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private DhcpNeutronPortListener dhcpNeutronPortListener;
    private DhcpLogicalSwitchListener dhcpLogicalSwitchListener;
    private DhcpUCastMacListener dhcpUCastMacListener;
    private ItmRpcService itmRpcService;
    private DhcpInterfaceConfigListener dhcpInterfaceConfigListener;
    private EntityOwnershipService entityOwnershipService;
    private DhcpDesignatedDpnListener dhcpDesignatedDpnListener;
    private boolean controllerDhcpEnabled = true;
    private DhcpSubnetListener dhcpSubnetListener;
    private DhcpHwvtepListener dhcpHwvtepListener;
    private IInterfaceManager interfaceManager;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("DhcpProvider Session Initiated");
        try {
            final DataBroker dataBroker = session.getSALService(DataBroker.class);
            final PacketProcessingService pktProcessingService = session.getRpcService(PacketProcessingService.class);
            dhcpManager = new DhcpManager(dataBroker);
            dhcpManager.setMdsalManager(mdsalManager);
            dhcpManager.setNeutronVpnService(neutronVpnManager);
            dhcpExternalTunnelManager = new DhcpExternalTunnelManager(dataBroker, mdsalManager, itmRpcService, entityOwnershipService);
            dhcpPktHandler = new DhcpPktHandler(dataBroker, dhcpManager, dhcpExternalTunnelManager);
            dhcpPktHandler.setPacketProcessingService(pktProcessingService);
            dhcpPktHandler.setInterfaceManagerRpc(interfaceManagerRpc);
            dhcpPktHandler.setInterfaceManager(interfaceManager);
            packetListener = notificationService.registerNotificationListener(dhcpPktHandler);
            dhcpNodeListener = new NodeListener(dataBroker, dhcpManager, dhcpExternalTunnelManager);
            dhcpConfigListener = new DhcpConfigListener(dataBroker, dhcpManager);
            dhcpLogicalSwitchListener = new DhcpLogicalSwitchListener(dhcpExternalTunnelManager, dataBroker);
            dhcpUCastMacListener = new DhcpUCastMacListener(dhcpManager,dhcpExternalTunnelManager, dataBroker);
            dhcpUCastMacListener.registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
            dhcpNeutronPortListener = new DhcpNeutronPortListener(dataBroker, dhcpExternalTunnelManager);
            dhcpNeutronPortListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            dhcpDesignatedDpnListener = new DhcpDesignatedDpnListener(dhcpExternalTunnelManager, dataBroker);
            dhcpDesignatedDpnListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            if (controllerDhcpEnabled) {
                dhcpInterfaceEventListener = new DhcpInterfaceEventListener(dhcpManager, dataBroker, dhcpExternalTunnelManager);
                dhcpInterfaceConfigListener = new DhcpInterfaceConfigListener(dataBroker, dhcpExternalTunnelManager);
            }
            dhcpSubnetListener = new DhcpSubnetListener(dhcpManager,dhcpExternalTunnelManager,dataBroker);
            dhcpSubnetListener.registerListener(LogicalDatastoreType.CONFIGURATION,dataBroker);
            dhcpHwvtepListener = new DhcpHwvtepListener(dataBroker, dhcpExternalTunnelManager);
        } catch (Exception e) {
            LOG.error("Error initializing services {}", e);
        }
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setNeutronVpnManager(INeutronVpnManager neutronVpnManager) {
        this.neutronVpnManager = neutronVpnManager;
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
        LOG.info("DhcpProvider closed");
    }

    public void setNotificationProviderService(NotificationProviderService notificationServiceDependency) {
        this.notificationService = notificationServiceDependency;
    }

    public void setInterfaceManagerRpc(OdlInterfaceRpcService interfaceManagerRpc) {
        this.interfaceManagerRpc = interfaceManagerRpc;
    }

    public void setItmRpcService(ItmRpcService itmRpcService) {
        this.itmRpcService = itmRpcService;
    }

    public void setEntityOwnershipService(EntityOwnershipService entityOwnershipService) {
        this.entityOwnershipService = entityOwnershipService;
    }

    public void setControllerDhcpEnabled(boolean controllerDhcpEnabled) {
        this.controllerDhcpEnabled = controllerDhcpEnabled;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }
}
