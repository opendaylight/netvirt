/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatServiceProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NatServiceProvider.class);
    private IMdsalApiManager mdsalManager;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final NotificationService notificationService;
    private final ItmRpcService itmRpcService;
    private FloatingIPListener floatingIpListener;
    private ExternalNetworkListener extNwListener;
    private NaptManager naptManager;
    private NaptEventHandler naptEventHandler;
    private BlockingQueue<NAPTEntryEvent> naptEventQueue;
    private ExternalNetworksChangeListener externalNetworksChangeListener;
    private ExternalRoutersListener externalRouterListener;
    private NaptPacketInHandler naptPacketInHandler;
    private EventDispatcher eventDispatcher;
    private final IBgpManager bgpManager;
    private NaptFlowRemovedEventHandler naptFlowRemovedEventHandler;
    private InterfaceStateEventListener interfaceStateEventListener;
    private NatNodeEventListener natNodeEventListener;
    private NAPTSwitchSelector naptSwitchSelector;
    private RouterPortsListener routerPortsListener;
    private final DataBroker dataBroker;
    private final PacketProcessingService pktProcessingService;
    private final IdManagerService idManager;
    private final OdlInterfaceRpcService interfaceService;
    private final NeutronvpnService neutronvpnService;
    private final VpnRpcService vpnService;
    private final FibRpcService fibService;

    public NatServiceProvider(final DataBroker dataBroker,
                              final RpcProviderRegistry rpcProviderRegistry,
                              final NotificationService notificationService,
                              final IMdsalApiManager mdsalManager,
                              final IBgpManager bgpMananger,
                              final IdManagerService idManager,
                              final OdlInterfaceRpcService interfaceService,
                              final ItmRpcService itmRpcService,
                              final PacketProcessingService pktProcessingService,
                              final NeutronvpnService neutronvpnService,
                              final VpnRpcService vpnService,
                              final FibRpcService fibService) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.notificationService = notificationService;
        this.bgpManager = bgpMananger;
        this.pktProcessingService = pktProcessingService;
        this.idManager = idManager;
        this.interfaceService = interfaceService;
        this.neutronvpnService = neutronvpnService;
        this.vpnService = vpnService;
        this.fibService = fibService;
        this.itmRpcService = itmRpcService;
    }

    @Override
    public void close() throws Exception {
        if (floatingIpListener != null) {
            floatingIpListener.close();
        }
        if (extNwListener != null) {
            extNwListener.close();
        }
        if (externalNetworksChangeListener != null) {
            externalNetworksChangeListener.close();
        }
        if (externalRouterListener != null) {
            externalRouterListener.close();
        }
        if (routerPortsListener != null) {
            routerPortsListener.close();
        }
    }

    public void start() {
        LOG.info("NAT Manager Provider Session Initiated");
        try {

            //Instantiate FloatingIPListener and set the MdsalManager and OdlInterfaceRpcService in it.
            floatingIpListener = new FloatingIPListener(dataBroker);
            floatingIpListener.setInterfaceManager(interfaceService);
            floatingIpListener.setMdsalManager(mdsalManager);

            //Instantiate ExternalNetworkListener and set the MdsalManager in it.
            extNwListener = new ExternalNetworkListener(dataBroker);
            extNwListener.setMdsalManager(mdsalManager);

            //Instantiate NaptManager and set the IdManagerService in it.
            naptManager = new NaptManager(dataBroker);
            naptManager.setIdManager(idManager);

            //Instantiate NaptEventHandler and start it as a Thread.
            naptEventHandler = new NaptEventHandler(dataBroker);
            naptEventHandler.setMdsalManager(mdsalManager);
            naptEventHandler.setNaptManager(naptManager);
            naptEventQueue = new ArrayBlockingQueue<>(NatConstants.EVENT_QUEUE_LENGTH);
            eventDispatcher = new EventDispatcher(naptEventQueue, naptEventHandler);
            new Thread(eventDispatcher).start();

            //Instantiate NaptPacketInHandler and register it in the notification service.
            naptPacketInHandler = new NaptPacketInHandler(eventDispatcher);
            notificationService.registerNotificationListener(naptPacketInHandler);

            //Floating ip Handler
            VpnFloatingIpHandler handler = new VpnFloatingIpHandler(vpnService, bgpManager, fibService);
            handler.setBroker(dataBroker);
            handler.setMdsalManager(mdsalManager);
            handler.setListener(floatingIpListener);
            floatingIpListener.setFloatingIpHandler(handler);

            //Instantiate NaptSwitchSelector and set the dataBroker in it.
            naptSwitchSelector = new NAPTSwitchSelector( dataBroker );

            //Instantiate ExternalRouterListener and set the dataBroker in it.
            externalRouterListener = new ExternalRoutersListener( dataBroker );
            externalRouterListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker );
            externalRouterListener.setMdsalManager(mdsalManager);
            externalRouterListener.setItmManager(itmRpcService);
            externalRouterListener.setInterfaceManager(interfaceService);
            externalRouterListener.setIdManager(idManager);
            externalRouterListener.setNaptManager(naptManager);
            externalRouterListener.setBgpManager(bgpManager);
            externalRouterListener.setFibService(fibService);
            externalRouterListener.setVpnService(vpnService);
            externalRouterListener.setNaptSwitchSelector(naptSwitchSelector);
            externalRouterListener.setNaptEventHandler(naptEventHandler);
            externalRouterListener.setNaptPacketInHandler(naptPacketInHandler);

            //Instantiate ExternalNetworksChangeListener and set the dataBroker in it.
            externalNetworksChangeListener = new ExternalNetworksChangeListener( dataBroker );
            externalNetworksChangeListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            externalNetworksChangeListener.setMdsalManager(mdsalManager);
            externalNetworksChangeListener.setInterfaceManager(interfaceService);
            externalNetworksChangeListener.setFloatingIpListener(floatingIpListener);
            externalNetworksChangeListener.setBgpManager(bgpManager);
            externalNetworksChangeListener.setFibService(fibService);
            externalNetworksChangeListener.setVpnService(vpnService);
            externalNetworksChangeListener.setExternalRoutersListener(externalRouterListener);
            externalNetworksChangeListener.setNaptManager(naptManager);
            externalNetworksChangeListener.setExternalRoutersListener(externalRouterListener);

            //Instantiate NaptFlowRemovedHandler and register it in the notification service.
            naptFlowRemovedEventHandler = new NaptFlowRemovedEventHandler(eventDispatcher, dataBroker, naptPacketInHandler, mdsalManager, naptManager);
            notificationService.registerNotificationListener(naptFlowRemovedEventHandler);

            //Instantiate interfaceStateEventListener and set the MdsalManager in it.
            interfaceStateEventListener = new InterfaceStateEventListener(dataBroker);
            interfaceStateEventListener.setMdsalManager(mdsalManager);
            interfaceStateEventListener.setFloatingIpListener(floatingIpListener);
            interfaceStateEventListener.setNeutronVpnService(neutronvpnService);
            interfaceStateEventListener.setNaptManager(naptManager);

            SNATDefaultRouteProgrammer defaultRouteProgrammer = new SNATDefaultRouteProgrammer(mdsalManager);
            DpnInVpnListener dpnInVpnListener = new DpnInVpnListener(dataBroker);
            dpnInVpnListener.setDefaultProgrammer(defaultRouteProgrammer);
            notificationService.registerNotificationListener(dpnInVpnListener);

            externalRouterListener.setDefaultProgrammer(defaultRouteProgrammer);

            NaptSwitchHA naptSwitchHA = new NaptSwitchHA(dataBroker,naptSwitchSelector);
            naptSwitchHA.setIdManager(idManager);
            naptSwitchHA.setInterfaceManager(interfaceService);
            naptSwitchHA.setMdsalManager(mdsalManager);
            naptSwitchHA.setItmManager(itmRpcService);
            naptSwitchHA.setBgpManager(bgpManager);
            naptSwitchHA.setFibService(fibService);
            naptSwitchHA.setVpnService(vpnService);
            naptSwitchHA.setExternalRoutersListener(externalRouterListener);

            natNodeEventListener = new NatNodeEventListener(dataBroker,naptSwitchHA);

            dpnInVpnListener.setNaptSwitchHA(naptSwitchHA);
            dpnInVpnListener.setMdsalManager(mdsalManager);
            dpnInVpnListener.setIdManager(idManager);

            routerPortsListener = new RouterPortsListener(dataBroker);

            RouterDpnChangeListener routerDpnChangeListener = new RouterDpnChangeListener(dataBroker);
            routerDpnChangeListener.setDefaultProgrammer(defaultRouteProgrammer);
            routerDpnChangeListener.setIdManager(idManager);
            routerDpnChangeListener.setMdsalManager(mdsalManager);
            routerDpnChangeListener.setNaptSwitchHA(naptSwitchHA);

            RouterToVpnListener routerToVpnListener = new RouterToVpnListener(dataBroker);
            routerToVpnListener.setFloatingIpListener(floatingIpListener);
            routerToVpnListener.setInterfaceManager(interfaceService);
            routerToVpnListener.setExternalRoutersListener(externalRouterListener);
            notificationService.registerNotificationListener(routerToVpnListener);
        } catch (Exception e) {
            LOG.error("Error initializing NAT Manager service", e);
        }
    }
}
