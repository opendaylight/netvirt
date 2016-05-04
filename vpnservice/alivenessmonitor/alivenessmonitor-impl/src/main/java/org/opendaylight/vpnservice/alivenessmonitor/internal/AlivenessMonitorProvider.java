/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlivenessMonitorProvider implements BindingAwareProvider,
                                        AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AlivenessMonitorProvider.class);
    private AlivenessMonitor alivenessMonitor;
    private RpcProviderRegistry rpcProviderRegistry;
    private OdlInterfaceRpcService interfaceManager;
    private RpcRegistration<AlivenessMonitorService> rpcRegistration;
    private ListenerRegistration<AlivenessMonitor> listenerRegistration;
    private NotificationService notificationService;
    private NotificationPublishService notificationPublishService;

    public AlivenessMonitorProvider(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void close() throws Exception {
        rpcRegistration.close();
        listenerRegistration.close();
        alivenessMonitor.close();
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("AlivenessMonitorProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            PacketProcessingService pktProcessingService = session.getRpcService(PacketProcessingService.class);
            IdManagerService idManager = rpcProviderRegistry.getRpcService(IdManagerService.class);
            OdlInterfaceRpcService interfaceService = rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class);
            alivenessMonitor = new AlivenessMonitor(dataBroker);
            alivenessMonitor.setPacketProcessingService(pktProcessingService);
            alivenessMonitor.setNotificationPublishService(notificationPublishService);
            alivenessMonitor.setIdManager(idManager);
            alivenessMonitor.setInterfaceManager(interfaceService);
            rpcRegistration = getRpcProviderRegistry().addRpcImplementation(AlivenessMonitorService.class, alivenessMonitor);
            listenerRegistration = notificationService.registerNotificationListener(alivenessMonitor);

            //ARP Handler
            AlivenessProtocolHandler arpHandler = new AlivenessProtocolHandlerARP(alivenessMonitor);
            OdlArputilService arpService = rpcProviderRegistry.getRpcService(OdlArputilService.class);
            ((AlivenessProtocolHandlerARP) arpHandler).setArpManagerService(arpService);
            alivenessMonitor.registerHandler(EtherTypes.Arp, arpHandler);

            //LLDP Handler
            AlivenessProtocolHandler lldpHandler = new AlivenessProtocolHandlerLLDP(alivenessMonitor);
            alivenessMonitor.registerHandler(EtherTypes.Lldp, lldpHandler);

            //TODO: Enable Interface Event Listener
            //DelegatingInterfaceEventListener listener = new DelegatingInterfaceEventListener(alivenessMonitor);
            //interfaceListenerRegistration = notificationService.registerNotificationListener(listener);
        } catch (Exception e) {
            LOG.error("Error initializing AlivenessMonitor service", e);
        }
    }

}
