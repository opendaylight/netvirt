/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.arputil.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.OdlArputilService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpUtilProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger s_logger = LoggerFactory.getLogger(ArpUtilProvider.class);

    RpcProviderRegistry         rpcProviderRegistry;

    NotificationService         notificationService;

    NotificationPublishService  notificationPublishService;

    ArpUtilImpl                  arpManager;

    IMdsalApiManager            mdsalApiManager;

    BindingAwareBroker.RpcRegistration<OdlArputilService> rpcRegistration;

    public ArpUtilProvider(RpcProviderRegistry rpcRegistry,
            NotificationPublishService publishService,
            NotificationService notificationService,
            IMdsalApiManager iMdsalApiManager) {

        this.rpcProviderRegistry        = rpcRegistry;
        this.mdsalApiManager            = iMdsalApiManager;
        this.notificationPublishService = publishService;
        this.notificationService        = notificationService;
    }

    public ArpUtilProvider() {
    }

    @Override
    public void onSessionInitiated(final ProviderContext session){

        s_logger.info( " Session Initiated for Arp Provider") ;

        try {
            DataBroker dataBroker = session.getSALService(DataBroker.class);
            PacketProcessingService packetProcessingService =
                    session.getRpcService(PacketProcessingService.class);

            arpManager = new ArpUtilImpl( dataBroker, packetProcessingService,
                    notificationPublishService, notificationService,
                     mdsalApiManager, rpcProviderRegistry) ;

            rpcRegistration = rpcProviderRegistry.
                    addRpcImplementation(OdlArputilService.class, arpManager);
            s_logger.info( " Session Initialized for Arp Provider") ;
        }catch( Exception e) {
            s_logger.error( "Error initializing Arp " , e );
        }
    }

    @Override
    public void close() throws Exception {
        rpcRegistration.close();
        arpManager.close();
        s_logger.info("ArpManager Manager Closed");
    }
}
