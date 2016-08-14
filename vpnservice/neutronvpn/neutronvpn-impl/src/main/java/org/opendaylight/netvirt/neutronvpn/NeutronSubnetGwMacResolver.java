/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

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
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class NeutronSubnetGwMacResolver {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetGwMacResolver.class);

    private DataBroker broker;
    private IVpnManager vpnManager;
    private OdlArputilService arpUtilService;
    private IElanService elanService;

    private final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("Gw-Mac-Res").build();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
    private ScheduledFuture<?> arpFuture;

    public NeutronSubnetGwMacResolver(DataBroker broker, IVpnManager vpnManager, OdlArputilService arputilService,
            IElanService elanService) {
        this.broker = broker;
        this.vpnManager = vpnManager;
        this.arpUtilService = arputilService;
        this.elanService = elanService;
    }

    public void start() {
        arpFuture = executorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    sendArpRequestsToExtGateways();
                } catch (Throwable t) {
                    LOG.warn("Failed to send ARP request to GW ips", t);
                }
            }

        }, 0, vpnManager.getArpCacheTimeoutSec(), TimeUnit.SECONDS);

    }

    public void close() {
        arpFuture.cancel(true);
    }

    public void sendArpRequestsToExtGateways(Router router) {
        LOG.trace("Send ARP requests to external GW for router {}", router);
        Port extPort = getRouterExtGatewayPort(router);
        if (extPort == null) {
            LOG.trace("External GW port for router {} is missing", router);
            return;
        }

        Collection<String> extInterfaces = getExternalInterfaces(router);
        if (extInterfaces == null || extInterfaces.isEmpty()) {
            LOG.trace("No external interfaces defined for router {}", router.getUuid().getValue());
            return;
        }

        List<FixedIps> fixedIps = extPort.getFixedIps();
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.trace("External GW port {} for router {} has no fixed IPs", extPort.getUuid().getValue(),
                    router.getUuid().getValue());
            return;
        }

        for (FixedIps fixIp : fixedIps) {
            Uuid subnetId = fixIp.getSubnetId();
            IpAddress srcIpAddress = fixIp.getIpAddress();
            IpAddress dstIpAddress = getExternalGwIpAddress(subnetId);
            sendArpRequest(srcIpAddress, dstIpAddress, extInterfaces);
        }

    }

    private void sendArpRequestsToExtGateways() {
        for (Router router : NeutronvpnUtils.routerMap.values()) {
            sendArpRequestsToExtGateways(router);
        }
    }

    private void sendArpRequest(IpAddress srcIpAddress, IpAddress dstIpAddress, Collection<String> interfaces) {
        if (srcIpAddress == null || dstIpAddress == null) {
            LOG.trace("Skip sending ARP to external GW srcIp {} dstIp {}", srcIpAddress, dstIpAddress);
            return;
        }

        List<InterfaceAddress> interfaceAddresses = new ArrayList<>();
        interfaces.stream().forEach(e -> interfaceAddresses
                .add(new InterfaceAddressBuilder().setInterface(e).setIpAddress(srcIpAddress).build()));

        SendArpRequestInput sendArpRequestInput = new SendArpRequestInputBuilder().setIpaddress(dstIpAddress)
                .setInterfaceAddress(interfaceAddresses).build();
        arpUtilService.sendArpRequest(sendArpRequestInput);
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

    private IpAddress getExternalGwIpAddress(Uuid subnetId) {
        if (subnetId == null) {
            LOG.trace("Subnet id is null");
            return null;
        }

        Subnet subnet = NeutronvpnUtils.getNeutronSubnet(broker, subnetId);
        return subnet != null ? subnet.getGatewayIp() : null;
    }

}
