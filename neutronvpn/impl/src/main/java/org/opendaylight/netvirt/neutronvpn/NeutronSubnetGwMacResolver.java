/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.ICentralizedSwitchProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.Ipv6NdUtilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.SendNeighborSolicitationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.SendNeighborSolicitationInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.SendNeighborSolicitationOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.ExternalGatewayInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronSubnetGwMacResolver {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetGwMacResolver.class);
    private static final long L3_INSTALL_DELAY_MILLIS = 5000;

    private final OdlArputilService arpUtilService;
    private final IElanService elanService;
    private final ICentralizedSwitchProvider cswitchProvider;
    private final NeutronvpnUtils neutronvpnUtils;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("Gw-Mac-Res").build());
    private final Ipv6NdUtilService ipv6NdUtilService;

    @Inject
    public NeutronSubnetGwMacResolver(final OdlArputilService arputilService, final IElanService elanService,
            final ICentralizedSwitchProvider cswitchProvider, final NeutronvpnUtils neutronvpnUtils,
            final Ipv6NdUtilService ipv6NdUtilService) {
        this.arpUtilService = arputilService;
        this.elanService = elanService;
        this.cswitchProvider = cswitchProvider;
        this.neutronvpnUtils = neutronvpnUtils;
        this.ipv6NdUtilService = ipv6NdUtilService;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());

        executorService.scheduleAtFixedRate(() -> {
            try {
                sendArpRequestsToExtGateways();
            } catch (Exception e) {
                LOG.warn("Failed to send ARP request to GW ips", e);
            }
        }, 0, ArpConstants.ARP_CACHE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

    }

    @PreDestroy
    public void close() {
        executorService.shutdownNow();
    }

    public void sendArpRequestsToExtGateways(Router router) {
        // Let the FIB flows a chance to be installed
        // otherwise the ARP response will be routed straight to L2
        // and bypasses L3 arp cache
        executorService.schedule(() -> sendArpRequestsToExtGatewayTask(router), L3_INSTALL_DELAY_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public void sendArpRequestsToExtGateways() {
        LOG.trace("Sending ARP requests to external gateways");
        for (Router router : neutronvpnUtils.getAllRouters()) {
            sendArpRequestsToExtGateways(router);
        }
    }

    private void sendArpRequestsToExtGatewayTask(Router router) {
        LOG.trace("Send ARP requests to external GW for router {}", router);
        Port extPort = getRouterExtGatewayPort(router);
        if (extPort == null) {
            LOG.trace("External GW port for router {} is missing", router.getUuid().getValue());
            return;
        }

        String extInterface = getExternalInterface(router);
        if (extInterface == null) {
            LOG.trace("No external interface defined for router {}", router.getUuid().getValue());
            return;
        }

        List<FixedIps> fixedIps = extPort.getFixedIps();
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.trace("External GW port {} for router {} has no fixed IPs", extPort.getUuid().getValue(),
                    router.getUuid().getValue());
            return;
        }

        MacAddress macAddress = extPort.getMacAddress();
        if (macAddress == null) {
            LOG.trace("External GW port {} for router {} has no mac address", extPort.getUuid().getValue(),
                    router.getUuid().getValue());
            return;
        }

        for (FixedIps fixIp : fixedIps) {
            Uuid subnetId = fixIp.getSubnetId();
            IpAddress srcIpAddress = fixIp.getIpAddress();
            IpAddress dstIpAddress = getExternalGwIpAddress(subnetId);
            String srcIpAddressString = srcIpAddress.stringValue();
            String dstIpAddressString = dstIpAddress.stringValue();
            if (NWUtil.isIpv4Address(srcIpAddressString)) {
                sendArpRequest(srcIpAddress, dstIpAddress, macAddress, extInterface);
            } else {
                sendNeighborSolication(new Ipv6Address(srcIpAddressString),macAddress,
                        new Ipv6Address(dstIpAddressString), extInterface);
            }
        }

    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void sendArpRequest(IpAddress srcIpAddress, IpAddress dstIpAddress, MacAddress srcMacAddress,
            String interfaceName) {
        if (srcIpAddress == null || dstIpAddress == null) {
            LOG.trace("Skip sending ARP to external GW srcIp {} dstIp {}", srcIpAddress, dstIpAddress);
            return;
        }

        PhysAddress srcMacPhysAddress = new PhysAddress(srcMacAddress.getValue());
        try {
            InterfaceAddress interfaceAddress = new InterfaceAddressBuilder().setInterface(interfaceName)
                    .setIpAddress(srcIpAddress).setMacaddress(srcMacPhysAddress).build();

            SendArpRequestInput sendArpRequestInput = new SendArpRequestInputBuilder().setIpaddress(dstIpAddress)
                    .setInterfaceAddress(Collections.singletonList(interfaceAddress)).build();

            ListenableFutures.addErrorLogging(JdkFutureAdapters.listenInPoolThread(
                    arpUtilService.sendArpRequest(sendArpRequestInput)), LOG, "Send ARP request");
        } catch (Exception e) {
            LOG.error("Failed to send ARP request to external GW {} from interface {}",
                    dstIpAddress.getIpv4Address().getValue(), interfaceName, e);
        }
    }

    private void sendNeighborSolication(Ipv6Address srcIpv6Address,
            MacAddress srcMac, Ipv6Address dstIpv6Address, String interfaceName) {
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6
            .nd.util.rev170210.interfaces.InterfaceAddress> interfaceAddresses = new ArrayList<>();
        interfaceAddresses.add(new org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6
                .nd.util.rev170210.interfaces.InterfaceAddressBuilder()
            .setInterface(interfaceName)
            .setSrcIpAddress(srcIpv6Address)
            .setSrcMacAddress(new PhysAddress(srcMac.getValue())).build());
        SendNeighborSolicitationInput input = new SendNeighborSolicitationInputBuilder()
                .setInterfaceAddress(interfaceAddresses).setTargetIpAddress(dstIpv6Address)
                .build();
        try {
            Future<RpcResult<SendNeighborSolicitationOutput>> result = ipv6NdUtilService
                    .sendNeighborSolicitation(input);
            RpcResult<SendNeighborSolicitationOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("sendNeighborSolicitationToOfGroup: RPC Call failed for input={} and Errors={}", input,
                        rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to send NS packet to ELAN group, input={}", input, e);
        }
    }

    @Nullable
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

        return neutronvpnUtils.getNeutronPort(extPortId);
    }

    @Nullable
    private String getExternalInterface(Router router) {
        ExternalGatewayInfo extGatewayInfo = router.getExternalGatewayInfo();
        String routerName = router.getUuid().getValue();
        if (extGatewayInfo == null) {
            LOG.warn("External GW info missing for router {}", routerName);
            return null;
        }

        Uuid extNetworkId = extGatewayInfo.getExternalNetworkId();
        if (extNetworkId == null) {
            LOG.warn("External network id missing for router {}", routerName);
            return null;
        }

        BigInteger primarySwitch = cswitchProvider.getPrimarySwitchForRouter(routerName);
        if (primarySwitch == null || BigInteger.ZERO.equals(primarySwitch)) {
            LOG.warn("Primary switch has not been allocated for router {}", routerName);
            return null;
        }

        return elanService.getExternalElanInterface(extNetworkId.getValue(), primarySwitch);
    }

    @Nullable
    private IpAddress getExternalGwIpAddress(Uuid subnetId) {
        if (subnetId == null) {
            LOG.error("getExternalGwIpAddress: Subnet id is null");
            return null;
        }

        Subnet subnet = neutronvpnUtils.getNeutronSubnet(subnetId);
        return subnet != null ? subnet.getGatewayIp() : null;
    }

}
