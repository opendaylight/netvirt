/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnserviceProvider implements IVpnManager, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnserviceProvider.class);
    private VpnInterfaceManager vpnInterfaceManager;
    private VpnManager vpnManager;
    private final IBgpManager bgpManager;
    private IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final OdlInterfaceRpcService interfaceManager;
    private final ItmRpcService itmProvider;
    private final IdManagerService idManager;
    private final OdlArputilService arpManager;
    private NeutronvpnService neuService;
    private PacketProcessingService m_packetProcessingService;
    private SubnetRoutePacketInHandler subnetRoutePacketInHandler;
    private final NotificationService notificationService;
    private final DataBroker dataBroker;
    private final RpcProviderRegistry rpcProviderRegistry;

    public VpnserviceProvider(final DataBroker dataBroker,
                              final RpcProviderRegistry rpcProviderRegistry,
                              final NotificationService notificationService,
                              final IMdsalApiManager mdsalApiManager,
                              final IBgpManager bgpManager) {
        this.dataBroker = dataBroker;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.notificationService = notificationService;
        this.mdsalManager = mdsalApiManager;
        this.bgpManager = bgpManager;

        this.idManager = rpcProviderRegistry.getRpcService(IdManagerService.class);
        this.arpManager =  rpcProviderRegistry.getRpcService(OdlArputilService.class);
        this.interfaceManager = rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class);
        this.itmProvider = rpcProviderRegistry.getRpcService(ItmRpcService.class);
    }

    public void start() {
        LOG.info("VpnserviceProvider Session Initiated");
        try {
            vpnManager = new VpnManager(dataBroker, bgpManager);
            vpnManager.setIdManager(idManager);
            vpnInterfaceManager = new VpnInterfaceManager(dataBroker, bgpManager, notificationService);
            vpnInterfaceManager.setMdsalManager(mdsalManager);
            vpnInterfaceManager.setInterfaceManager(interfaceManager);
            vpnInterfaceManager.setITMProvider(itmProvider);
            vpnInterfaceManager.setIdManager(idManager);
            vpnInterfaceManager.setArpManager(arpManager);
            vpnInterfaceManager.setNeutronvpnManager(neuService);
            //Handles subnet route entries
            subnetRoutePacketInHandler = new SubnetRoutePacketInHandler(dataBroker, idManager);
            m_packetProcessingService = rpcProviderRegistry.getRpcService(PacketProcessingService.class);
            subnetRoutePacketInHandler.setPacketProcessingService(m_packetProcessingService);
            notificationService.registerNotificationListener(subnetRoutePacketInHandler);
            vpnManager.setVpnInterfaceManager(vpnInterfaceManager);
            createIdPool();

            RouterInterfaceListener routerListener = new RouterInterfaceListener(dataBroker);
            routerListener.setVpnInterfaceManager(vpnInterfaceManager);
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
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
        if (vpnManager != null) {
            vpnManager.close();
        }
        if (vpnInterfaceManager != null) {
            vpnInterfaceManager.close();
        }
    }

    @Override
    public void setFibService(IFibManager fibManager) {
        LOG.debug("Fib service reference is initialized in VPN Manager");
        this.fibManager = fibManager;
        vpnInterfaceManager.setFibManager(fibManager);
    }

    @Override
    public void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label) {
        LOG.info("Adding extra route with destination {} and nexthop {}", destination, nextHop);
        vpnInterfaceManager.addExtraRoute(destination, nextHop, rd, routerID, label, null);
    }

    @Override
    public void delExtraRoute(String destination, String rd, String routerID) {
        LOG.info("Deleting extra route with destination {}", destination);
        vpnInterfaceManager.delExtraRoute(destination, rd, routerID);
    }

    @Override
    public boolean isVPNConfigured() {
        return vpnManager.isVPNConfigured();
    }
}
