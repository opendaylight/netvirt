/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import org.opendaylight.controller.sal.binding.api.NotificationProviderService;

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

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("DhcpProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            dhcpPktHandler = new DhcpPktHandler(dataBroker);
            packetListener = notificationService.registerNotificationListener(dhcpPktHandler);
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
        LOG.info("DhcpProvider closed");
    }

    public void setNotificationProviderService(NotificationProviderService notificationServiceDependency) {
        this.notificationService = notificationServiceDependency;
    }

}
