/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import java.math.BigInteger;

import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
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

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("DhcpProvider Session Initiated");
        try {
            final DataBroker dataBroker = session.getSALService(DataBroker.class);
            final PacketProcessingService pktProcessingService = session.getRpcService(PacketProcessingService.class);
            dhcpManager = new DhcpManager(dataBroker);
            dhcpManager.setMdsalManager(mdsalManager);
            dhcpPktHandler = new DhcpPktHandler(dataBroker, dhcpManager);
            dhcpPktHandler.setPacketProcessingService(pktProcessingService);
            packetListener = notificationService.registerNotificationListener(dhcpPktHandler);
            dhcpNodeListener = new NodeListener(dataBroker, dhcpManager);
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }


    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
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

}
