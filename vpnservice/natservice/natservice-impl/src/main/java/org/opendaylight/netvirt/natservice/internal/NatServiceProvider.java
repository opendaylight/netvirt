/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import io.netty.util.concurrent.GlobalEventExecutor;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class NatServiceProvider implements BindingAwareProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NatServiceProvider.class);
    private IMdsalApiManager mdsalManager;
    private RpcProviderRegistry rpcProviderRegistry;
    private NotificationService notificationService;
    private ItmRpcService itmManager;
    private FloatingIPListener floatingIpListener;
    private ExternalNetworkListener extNwListener;
    private NaptManager naptManager;
    private NaptEventHandler naptEventHandler;
    private BlockingQueue<NAPTEntryEvent> naptEventQueue;
    private ExternalNetworksChangeListener externalNetworksChangeListener;
    private ExternalRoutersListener externalRouterListener;
    private NaptPacketInHandler naptPacketInHandler;
    private EventDispatcher eventDispatcher;
    private IBgpManager bgpManager;
    private NaptFlowRemovedEventHandler naptFlowRemovedEventHandler;
    private InterfaceStateEventListener interfaceStateEventListener;
    private NatNodeEventListener natNodeEventListener;
    private NAPTSwitchSelector naptSwitchSelector;
    private RouterPortsListener routerPortsListener;
    private IInterfaceManager interfaceManager;
    private IFibManager fibManager;
    private VpnFloatingIpHandler handler;

    public NatServiceProvider(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setBgpManager(IBgpManager bgpManager) {
        LOG.debug("BGP Manager reference initialized");
        this.bgpManager = bgpManager;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    @Override
    public void close() throws Exception {
        floatingIpListener.close();
        extNwListener.close();
        externalNetworksChangeListener.close();
        externalRouterListener.close();
        routerPortsListener.close();
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("NAT Manager Provider Session Initiated");
        try {
            //Get the DataBroker, PacketProcessingService, IdManagerService and the OdlInterfaceRpcService instances
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            PacketProcessingService pktProcessingService = session.getRpcService(PacketProcessingService.class);
            IdManagerService idManager = rpcProviderRegistry.getRpcService(IdManagerService.class);
            OdlInterfaceRpcService interfaceService = rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class);
            NeutronvpnService neutronvpnService = rpcProviderRegistry.getRpcService(NeutronvpnService.class);
            itmManager = rpcProviderRegistry.getRpcService(ItmRpcService.class);

            //Instantiate FloatingIPListener and set the MdsalManager and OdlInterfaceRpcService in it.
            floatingIpListener = new FloatingIPListener(dataBroker);
            floatingIpListener.setInterfaceManager(interfaceService);
            floatingIpListener.setMdsalManager(mdsalManager);
            floatingIpListener.setIdManager(idManager);

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
            naptEventHandler.setPacketProcessingService(pktProcessingService);
            naptEventHandler.setInterfaceManagerRpc(interfaceService);
            naptEventHandler.setInterfaceManager(interfaceManager);
            naptEventQueue = new ArrayBlockingQueue<>(NatConstants.EVENT_QUEUE_LENGTH);
            eventDispatcher = new EventDispatcher(naptEventQueue, naptEventHandler);
            new Thread(eventDispatcher, "NaptEvents").start();

            //Instantiate NaptPacketInHandler and register it in the notification service.
            naptPacketInHandler = new NaptPacketInHandler(eventDispatcher);
            notificationService.registerNotificationListener(naptPacketInHandler);

            //Floating ip Handler
            VpnRpcService vpnService = rpcProviderRegistry.getRpcService(VpnRpcService.class);
            FibRpcService fibService = rpcProviderRegistry.getRpcService(FibRpcService.class);
            handler = new VpnFloatingIpHandler(vpnService, bgpManager, fibService);
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
            externalRouterListener.setItmManager(itmManager);
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

            externalRouterListener.setDefaultProgrammer(defaultRouteProgrammer);

            NaptSwitchHA naptSwitchHA = new NaptSwitchHA(dataBroker,naptSwitchSelector);
            naptSwitchHA.setIdManager(idManager);
            naptSwitchHA.setInterfaceManager(interfaceService);
            naptSwitchHA.setMdsalManager(mdsalManager);
            naptSwitchHA.setItmManager(itmManager);
            naptSwitchHA.setBgpManager(bgpManager);
            naptSwitchHA.setFibService(fibService);
            naptSwitchHA.setVpnService(vpnService);
            naptSwitchHA.setExternalRoutersListener(externalRouterListener);

            natNodeEventListener = new NatNodeEventListener(dataBroker,naptSwitchHA);

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
        // TODO: Remove as part of blueprint fix.
        // This is simply here to get the INeutronVPNManager service which is already
        // moved to blueprint.
        GlobalEventExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
                Bundle b = FrameworkUtil.getBundle(this.getClass());
                if (b == null) {
                    return;
                }
                BundleContext bundleContext = b.getBundleContext();
                if (bundleContext == null) {
                    return;
                }
                final WaitingServiceTracker<IFibManager> tracker = WaitingServiceTracker.create(
                        IFibManager.class, bundleContext);
                IFibManager fibManager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);
                LOG.info("NatServiceProvider initialized. INeutronVpnManager={}", fibManager);
                handler.setFibManager(fibManager);
                externalRouterListener.setFibManager(fibManager);
            }
        });
    }
}
