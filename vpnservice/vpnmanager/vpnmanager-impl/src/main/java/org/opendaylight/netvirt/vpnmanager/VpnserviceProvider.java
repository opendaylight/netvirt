/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import io.netty.util.concurrent.GlobalEventExecutor;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkListener;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkNodeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnserviceProvider implements BindingAwareProvider, IVpnManager, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnserviceProvider.class);
    private VpnInterfaceManager vpnInterfaceManager;
    private VpnManager vpnManager;
    private ArpScheduler arpscheduler;
    private IBgpManager bgpManager;
    private FibRpcService fibService;
    private IFibManager fibManager;
    private IMdsalApiManager mdsalManager;
    private OdlInterfaceRpcService odlInterfaceRpcService;
    private ItmRpcService itmRpcService;
    private IdManagerService idManager;
    private OdlArputilService arpManager;
    private NeutronvpnService neuService;
    private NotificationService notificationService;
    private RpcProviderRegistry rpcProviderRegistry;
    private BindingAwareBroker.RpcRegistration<VpnRpcService> rpcRegistration;
    private NotificationPublishService notificationPublishService;
    private PacketProcessingService m_packetProcessingService;
    private SubnetRoutePacketInHandler subnetRoutePacketInHandler;
    private InterVpnLinkListener interVpnLinkListener;
    private static DataBroker dataBroker;
    private InterVpnLinkNodeListener interVpnLinkNodeListener;
    private TunnelInterfaceStateListener tunIntfStateListener;
    private BundleContext bundleContext;
    private FloatingIpGarpHandler garpHandler;

    public VpnserviceProvider(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public static DataBroker getDataBroker() {
        return dataBroker;
    }


    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("VpnserviceProvider Session Initiated");

        // TODO: Remove as part of blueprint migration.
        // This is simply here to get the services which have already moved to blueprint.
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
                final WaitingServiceTracker<IBgpManager> tracker = WaitingServiceTracker.create(
                        IBgpManager.class, bundleContext);
                IBgpManager bgpManager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);
                try {
                    dataBroker = session.getSALService(DataBroker.class);
                    vpnManager = new VpnManager(dataBroker, bgpManager);
                    vpnManager.setIdManager(idManager);
                    vpnInterfaceManager = new VpnInterfaceManager(dataBroker, bgpManager, notificationService);
                    tunIntfStateListener = new TunnelInterfaceStateListener(dataBroker, bgpManager);
                    vpnInterfaceManager.setMdsalManager(mdsalManager);
                    vpnInterfaceManager.setIfaceMgrRpcService(odlInterfaceRpcService);
                    vpnInterfaceManager.setIdManager(idManager);
                    vpnInterfaceManager.setArpManager(arpManager);
                    vpnInterfaceManager.setNeutronvpnManager(neuService);
                    vpnInterfaceManager.setNotificationPublishService(notificationPublishService);
                    vpnManager.setVpnInterfaceManager(vpnInterfaceManager);
                    fibService = rpcProviderRegistry.getRpcService(FibRpcService.class);
                    itmRpcService = rpcProviderRegistry.getRpcService(ItmRpcService.class);
                    vpnInterfaceManager.setFibRpcService(fibService);
                    tunIntfStateListener.setITMRpcService(itmRpcService);
                    VpnRpcService vpnRpcService = new VpnRpcServiceImpl(idManager, vpnInterfaceManager, dataBroker);
                    rpcRegistration = getRpcProviderRegistry().addRpcImplementation(VpnRpcService.class, vpnRpcService);
                    //Handles subnet route entries
                    subnetRoutePacketInHandler = new SubnetRoutePacketInHandler(dataBroker, idManager);
                    m_packetProcessingService = session.getRpcService(PacketProcessingService.class);
                    subnetRoutePacketInHandler.setPacketProcessingService(m_packetProcessingService);
                    notificationService.registerNotificationListener(subnetRoutePacketInHandler);
                    interVpnLinkListener = new InterVpnLinkListener(dataBroker, idManager, mdsalManager, bgpManager,
                            notificationPublishService);
                    interVpnLinkNodeListener = new InterVpnLinkNodeListener(dataBroker, mdsalManager);
                    createIdPool();
                    RouterInterfaceListener routerListener = new RouterInterfaceListener(dataBroker);
                    arpscheduler = new ArpScheduler(odlInterfaceRpcService, dataBroker);
                    routerListener.setVpnInterfaceManager(vpnInterfaceManager);
                    garpHandler = new FloatingIpGarpHandler(dataBroker);
                    garpHandler.setPacketService(m_packetProcessingService);

                    //ServiceRegistration serviceRegistration = bundleContext.registerService(IVpnManager.)
                } catch (Exception e) {
                    LOG.error("Error initializing services", e);
                }

                LOG.info("VpnserviceProvider initialized. IBgpManager={}", bgpManager);
            }
        });
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setOdlInterfaceRpcService(OdlInterfaceRpcService odlInterfaceRpcService) {
        this.odlInterfaceRpcService = odlInterfaceRpcService;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    public void setArpManager(OdlArputilService arpManager) {
        this.arpManager = arpManager;
    }

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    private RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
            .setLow(VpnConstants.VPN_IDPOOL_START)
            .setHigh(new BigInteger(VpnConstants.VPN_IDPOOL_SIZE).longValue())
            .build();
        try {
           Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
           if ((result != null) && (result.get().isSuccessful())) {
                LOG.debug("Created IdPool for VPN Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for VPN Service",e);
        }
    }

    @Override
    public void close() throws Exception {
        vpnManager.close();
        vpnInterfaceManager.close();
        tunIntfStateListener.close();
        interVpnLinkListener.close();
        interVpnLinkNodeListener.close();

    }

    @Override
    public void setFibManager(IFibManager fibManager) {
        LOG.debug("Fib service reference is initialized in VPN Manager");
        this.fibManager = fibManager;
        vpnInterfaceManager.setFibManager(fibManager);
        tunIntfStateListener.setFibManager(fibManager);
    }

    @Override
    public void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label) {
        LOG.info("Adding extra route with destination {}, nextHop {} and label{}", destination, nextHop, label);
        vpnInterfaceManager.addExtraRoute(destination, nextHop, rd, routerID, label, /*intfName*/ null);
    }

    @Override
    public void delExtraRoute(String destination, String nextHop, String rd, String routerID) {
        LOG.info("Deleting extra route with destination {} and nextHop {}", destination, nextHop);
        vpnInterfaceManager.delExtraRoute(destination, nextHop, rd, routerID, null);
    }

    @Override
    public boolean existsVpn(String vpnName) {
        return vpnManager.getVpnInstance(vpnName) != null;
    }

    @Override
    public boolean isVPNConfigured() {
        return vpnManager.isVPNConfigured();
    }
}
