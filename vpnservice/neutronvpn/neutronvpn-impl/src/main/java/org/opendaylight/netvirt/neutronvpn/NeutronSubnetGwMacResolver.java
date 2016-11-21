/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.RouterIdName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class NeutronSubnetGwMacResolver {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetGwMacResolver.class);
    private static final long L3_INSTALL_DELAY_MILLIS = 5000;

    private final DataBroker broker;
    private final IVpnManager vpnManager;
    private final OdlArputilService arpUtilService;
    private final IElanService elanService;

    private final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("Gw-Mac-Res").build();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    private ScheduledFuture<?> arpFuture;

    public NeutronSubnetGwMacResolver(final DataBroker broker, final IVpnManager vpnManager,
            final OdlArputilService arputilService, final IElanService elanService) {
        this.broker = broker;
        this.vpnManager = vpnManager;
        this.arpUtilService = arputilService;
        this.elanService = elanService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());

        arpFuture = executorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    sendArpRequestsToExtGateways();
                } catch (Throwable t) {
                    LOG.warn("Failed to send ARP request to GW ips", t);
                }
            }

        }, 0, vpnManager.getArpCacheTimeoutMillis(), TimeUnit.MILLISECONDS);

    }

    public void close() {
        arpFuture.cancel(true);
    }

    public void sendArpRequestsToExtGateways(Router router) {
        // Let the FIB flows a chance to be installed
        // otherwise the ARP response will be routed straight to L2
        // and bypasses L3 arp cache
        executorService.schedule(new Runnable() {

            @Override
            public void run() {
                sendArpRequestsToExtGatewayTask(router);
            }

        }, L3_INSTALL_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void sendArpRequestsToExtGatewayTask(Router router) {
        LOG.trace("Send ARP requests to external GW for router {}", router);
        Port extGwPort = getRouterExtGatewayPort(router);
        if (extGwPort == null) {
            LOG.trace("External GW port for router {} is missing", router);
            return;
        }

        // FIXME is this the way to do it? any service available? is this the dpnId?
        String routerName = router.getName();
        BigInteger naptSwitch = getPrimaryNaptfromRouterId(broker, getVpnId(broker, routerName));

        String extInterface = getExternalInterface(router, naptSwitch);
        if (extInterface == null) {
            LOG.trace("No external interface defined for router {} on dpnId {}",
                    router.getUuid().getValue(), naptSwitch);
            return;
        }

        List<FixedIps> fixedIps = extGwPort.getFixedIps();
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.trace("External GW port {} for router {} has no fixed IPs", extGwPort.getUuid().getValue(),
                    router.getUuid().getValue());
            return;
        }

        for (FixedIps fixIp : fixedIps) {
            Uuid subnetId = fixIp.getSubnetId();
            IpAddress srcIpAddress = fixIp.getIpAddress();
            IpAddress dstIpAddress = getExternalGwIpAddress(subnetId);
            MacAddress extGwMac = extGwPort.getMacAddress();
            sendArpRequest(srcIpAddress, dstIpAddress, extGwMac, extInterface);
        }

    }

    private void sendArpRequestsToExtGateways() {
        LOG.trace("Sending ARP requests to exteral gateways");
        for (Router router : NeutronvpnUtils.routerMap.values()) {
            sendArpRequestsToExtGateways(router);
        }
    }

    private void sendArpRequest(IpAddress srcIpAddress, IpAddress dstIpAddress, MacAddress srcMac, String iface) {
        // FIXME single iface func should probably be main
        Collection<String> interfaces = Collections.emptyList();
        interfaces.add(iface);
        sendArpRequest(srcIpAddress, dstIpAddress, srcMac, interfaces);
    }


    private void sendArpRequest(IpAddress srcIpAddress, IpAddress dstIpAddress, MacAddress srcMac, Collection<String> interfaces) {
        if (srcIpAddress == null || dstIpAddress == null) {
            LOG.trace("Skip sending ARP to external GW srcIp {} dstIp {}", srcIpAddress, dstIpAddress);
            return;
        }

        try {
            List<InterfaceAddress> interfaceAddresses = new ArrayList<>();
            PhysAddress srcMacPhysAddr = new PhysAddress(srcMac.getValue()); // FIXME does this work?
            interfaces.stream().forEach(e -> interfaceAddresses.add(new InterfaceAddressBuilder().
                    setInterface(e).setIpAddress(srcIpAddress).setMacaddress(srcMacPhysAddr).build()));

            // FIXME use router gateway MAC
            SendArpRequestInput sendArpRequestInput = new SendArpRequestInputBuilder().setIpaddress(dstIpAddress)
                    .setInterfaceAddress(interfaceAddresses).build();
            arpUtilService.sendArpRequest(sendArpRequestInput);
        } catch (Exception e) {
            LOG.error("Failed to send ARP request to external GW {} from interfaces {}",
                    dstIpAddress.getIpv4Address().getValue(), interfaces, e);
        }
    }

    private Port getRouterExtGatewayPort(Router router) {
        if (router == null) {
            LOG.trace("Router is null");
            return null;
        }

        Uuid extPortId = router.getGatewayPortId();
        if (extPortId == null) {
            LOG.trace("Router {} is not associated with any external GW port", router.getUuid().getValue());
            return null;
        }

        return NeutronvpnUtils.getNeutronPort(broker, extPortId);
    }

    private Collection<String> getExternalInterfaces(Router router) {
        ExternalGatewayInfo extGatewayInfo = router.getExternalGatewayInfo();
        if (extGatewayInfo == null) {
            LOG.trace("External GW info missing for router {}", router.getUuid().getValue());
            return Collections.emptyList();
        }

        Uuid extNetworkId = extGatewayInfo.getExternalNetworkId();
        if (extNetworkId == null) {
            LOG.trace("External network id missing for router {}", router.getUuid().getValue());
            return Collections.emptyList();
        }

        return elanService.getExternalElanInterfaces(extNetworkId.getValue());
    }

    private String getExternalInterface(Router router, BigInteger dpnId) {
        ExternalGatewayInfo extGatewayInfo = router.getExternalGatewayInfo();
        if (extGatewayInfo == null) {
            LOG.trace("External GW info missing for router {}", router.getUuid().getValue());
            return null;
        }

        Uuid extNetworkId = extGatewayInfo.getExternalNetworkId();
        if (extNetworkId == null) {
            LOG.trace("External network id missing for router {}", router.getUuid().getValue());
            return null;
        }

        return elanService.getExternalElanInterface(extNetworkId.getValue(), dpnId);
    }

    private IpAddress getExternalGwIpAddress(Uuid subnetId) {
        if (subnetId == null) {
            LOG.trace("Subnet id is null");
            return null;
        }

        Subnet subnet = NeutronvpnUtils.getNeutronSubnet(broker, subnetId);
        return subnet != null ? subnet.getGatewayIp() : null;
    }

    // FIXME Copied from NatUtil.java
    public static BigInteger getPrimaryNaptfromRouterId(DataBroker broker, Long routerId) {
        // convert routerId to Name
        String routerName = getRouterName(broker, routerId);
        InstanceIdentifier id = buildNaptSwitchIdentifier(routerName);
        Optional<RouterToNaptSwitch> routerToNaptSwitchData = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (routerToNaptSwitchData.isPresent()) {
            RouterToNaptSwitch routerToNaptSwitchInstance = routerToNaptSwitchData.get();
            return routerToNaptSwitchInstance.getPrimarySwitchId();
        }
        return null;
    }

    private static InstanceIdentifier<RouterToNaptSwitch> buildNaptSwitchIdentifier(String routerId) {
        InstanceIdentifier<RouterToNaptSwitch> rtrNaptSw = InstanceIdentifier.builder(NaptSwitches.class).child
                (RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerId)).build();
        return rtrNaptSw;
    }

    public static String getRouterName(DataBroker broker, Long routerId) {
        InstanceIdentifier id = buildRouterIdentifier(routerId);
        Optional<RouterIds> routerIdsData = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (routerIdsData.isPresent()) {
            RouterIds routerIdsInstance = routerIdsData.get();
            return routerIdsInstance.getRouterName();
        }
        return null;
    }

    private static InstanceIdentifier<RouterIds> buildRouterIdentifier(Long routerId) {
        InstanceIdentifier<RouterIds> routerIds = InstanceIdentifier.builder(RouterIdName.class).child
                (RouterIds.class, new RouterIdsKey(routerId)).build();
        return routerIds;
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();
        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static long getVpnId(DataBroker broker, String vpnName) {
        long natConstantsInvalidId = -1;
        if (vpnName == null) {
            return natConstantsInvalidId;
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(
                vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance = read(
                broker, LogicalDatastoreType.CONFIGURATION, id);

        long vpnId = natConstantsInvalidId;
        if (vpnInstance.isPresent()) {
            Long vpnIdAsLong = vpnInstance.get().getVpnId();
            if (vpnIdAsLong != null) {
                vpnId = vpnIdAsLong;
            }
        }
        return vpnId;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(
            String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey(
                                vpnName))
                .build();
    }

}
