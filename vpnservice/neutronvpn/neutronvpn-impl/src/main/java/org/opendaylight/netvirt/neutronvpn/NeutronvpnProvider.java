/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.neutronvpn.l2gw.L2GatewayProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronvpnProvider implements INeutronVpnManager, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnProvider.class);
    private NeutronvpnManager nvManager;
    private NeutronvpnNatManager nvNatManager;
    private final IMdsalApiManager mdsalManager;
    private final LockManagerService lockManager;
    private NeutronBgpvpnChangeListener bgpvpnListener;
    private NeutronNetworkChangeListener networkListener;
    private NeutronSubnetChangeListener subnetListener;
    private NeutronRouterChangeListener routerListener;
    private NeutronPortChangeListener portListener;
    private NeutronFloatingToFixedIpMappingChangeListener floatingIpMapListener;

    private final RpcProviderRegistry rpcProviderRegistry;
    private L2GatewayProvider l2GatewayProvider;
    private final NotificationPublishService notificationPublishService;
    private final NotificationService notificationService;
    private final EntityOwnershipService entityOwnershipService;
    private RpcRegistration<NeutronvpnService> rpcRegistration;
    private final DataBroker dbx;

    public NeutronvpnProvider(final DataBroker dataBroker,
                              final RpcProviderRegistry rpcRegistry,
                              final NotificationPublishService notificationPublishService,
                              final NotificationService notificationService,
                              final EntityOwnershipService eos,
                              final IMdsalApiManager mdsalApiManager) {
        this.dbx = dataBroker;
        this.rpcProviderRegistry = rpcRegistry;
        this.notificationPublishService = notificationPublishService;
        this.notificationService = notificationService;
        this.entityOwnershipService = eos;
        this.mdsalManager = mdsalApiManager;

        this.lockManager = rpcProviderRegistry.getRpcService(LockManagerService.class);
    }

    public void start() {
        try {
            nvNatManager = new NeutronvpnNatManager(dbx, mdsalManager);
            nvManager = new NeutronvpnManager(dbx, mdsalManager,notificationPublishService,notificationService, nvNatManager);
            rpcRegistration = rpcProviderRegistry.addRpcImplementation(NeutronvpnService.class, nvManager);
            bgpvpnListener = new NeutronBgpvpnChangeListener(dbx, nvManager);
            networkListener = new NeutronNetworkChangeListener(dbx, nvManager, nvNatManager);
            subnetListener = new NeutronSubnetChangeListener(dbx, nvManager);
            routerListener = new NeutronRouterChangeListener(dbx, nvManager, nvNatManager);
            portListener = new NeutronPortChangeListener(dbx, nvManager, nvNatManager,
                    notificationPublishService,notificationService);
            portListener.setLockManager(lockManager);
            portListener.setLockManager(lockManager);
            floatingIpMapListener = new NeutronFloatingToFixedIpMappingChangeListener(dbx);
            nvManager.setLockManager(lockManager);
            portListener.setLockManager(lockManager);
            floatingIpMapListener.setLockManager(lockManager);
            l2GatewayProvider = new L2GatewayProvider(dbx, rpcProviderRegistry, entityOwnershipService);

            LOG.info("NeutronvpnProvider Session Initiated");
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (portListener != null) {
            portListener.close();
        }
        if (subnetListener != null) {
            subnetListener.close();
        }
        if (routerListener != null ) {
            routerListener.close();
        }
        if (networkListener != null) {
            networkListener.close();
        }
        if (bgpvpnListener != null) {
            bgpvpnListener.close();
        }
        if (nvManager != null) {
            nvManager.close();
        }
        if (l2GatewayProvider != null) {
            l2GatewayProvider.close();
        }
        if (rpcRegistration != null) {
            rpcRegistration.close();
        }
        LOG.info("NeutronvpnProvider Closed");
    }

    @Override
    public List<String> showNeutronPortsCLI() {
        return nvManager.showNeutronPortsCLI();
    }

    @Override
    public List<String> showVpnConfigCLI(Uuid vuuid) {
        return nvManager.showVpnConfigCLI(vuuid);
    }

    @Override
    public void addSubnetToVpn(Uuid vpnId, Uuid subnet) {
        nvManager.addSubnetToVpn(vpnId, subnet);
    }

    @Override
    public List<Uuid> getSubnetsforVpn(Uuid vpnid) {
        return nvManager.getSubnetsforVpn(vpnid);
    }

    @Override
    public void removeSubnetFromVpn(Uuid vpnId, Uuid subnet) {
        nvManager.removeSubnetFromVpn(vpnId, subnet);
    }

    @Override
    public Port getNeutronPort(String name) {
        return nvManager.getNeutronPort(name);
    }

    @Override
    public Port getNeutronPort(Uuid portId) {
        return nvManager.getNeutronPort(portId);
    }

    @Override
    public Subnet getNeutronSubnet(Uuid subnetId) {
        return NeutronvpnUtils.getNeutronSubnet(broker, subnetId);
    }

    @Override
    public String uuidToTapPortName(Uuid id) {
        return NeutronvpnUtils.uuidToTapPortName(id);
    }

    @Override
    public IpAddress getNeutronSubnetGateway(Uuid subnetId) {
        return nvManager.getNeutronSubnetGateway(subnetId);
    }
}