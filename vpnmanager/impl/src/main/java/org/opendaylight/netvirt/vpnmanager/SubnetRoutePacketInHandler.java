/*
 * Copyright (c) 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.primitives.Ints;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.infrautils.metrics.Counter;
import org.opendaylight.infrautils.metrics.Labeled;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.ICentralizedSwitchProvider;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.iplearn.ipv4.ArpUtils;
import org.opendaylight.netvirt.vpnmanager.utilities.CounterUtility;
import org.opendaylight.openflowplugin.libraries.liblldp.BitBufferHelper;
import org.opendaylight.openflowplugin.libraries.liblldp.BufferException;
import org.opendaylight.openflowplugin.libraries.liblldp.HexEncode;
import org.opendaylight.openflowplugin.libraries.liblldp.Packet;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefixBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.Ipv6NdUtilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetRoutePacketInHandler implements PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRoutePacketInHandler.class);
    private static final String LOGGING_PREFIX = "SUBNETROUTE:";
    private final DataBroker dataBroker;
    private final PacketProcessingService packetService;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final ICentralizedSwitchProvider centralizedSwitchProvider;
    private final IInterfaceManager interfaceManager;
    private final Ipv6NdUtilService ipv6NdUtilService;
    private final Labeled<Labeled<Counter>> packetInCounter;
    private final VpnUtil vpnUtil;

    @Inject
    public SubnetRoutePacketInHandler(final DataBroker dataBroker, final PacketProcessingService packetService,
            final OdlInterfaceRpcService odlInterfaceRpcService,
            final ICentralizedSwitchProvider centralizedSwitchProvider, final IInterfaceManager interfaceManager,
            final Ipv6NdUtilService ipv6NdUtilService, MetricProvider metricProvider, VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.packetService = packetService;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.centralizedSwitchProvider = centralizedSwitchProvider;
        this.interfaceManager = interfaceManager;
        this.ipv6NdUtilService = ipv6NdUtilService;
        packetInCounter =  metricProvider.newCounter(MetricDescriptor.builder().anchor(this)
                .project(CounterUtility.getProject()).module(CounterUtility.getModule())
                .id(CounterUtility.getSubnetRouteId()).build(), "action","sourceIp.destinationIp");
        this.vpnUtil = vpnUtil;
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {

        short tableId = notification.getTableId().getValue().toJava();
        LOG.trace("{} onPacketReceived: Packet punted from table {}", LOGGING_PREFIX, tableId);
        if (!VpnUtil.isArpLearningEnabled()) {
            LOG.trace("Not handling packet as ARP Based Learning is disabled");
            return;
        }
        byte[] data = notification.getPayload();
        if (notification.getMatch() == null || notification.getMatch().getMetadata() == null) {
            LOG.error("{} onPacketReceived: Received from table {} where the match or metadata are null",
                    LOGGING_PREFIX, tableId);
            return;
        }
        Uint64 metadata = notification.getMatch().getMetadata().getMetadata();
        Ethernet res = new Ethernet();

        if (tableId == NwConstants.L3_SUBNET_ROUTE_TABLE) {
            LOG.trace("{} onPacketReceived: Some packet received as {}", LOGGING_PREFIX, notification);
            try {
                res.deserialize(data, 0, data.length * Byte.SIZE);
            } catch (PacketException e) {
                LOG.error("{} onPacketReceived: Failed to decode Packet ", LOGGING_PREFIX, e);
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label(CounterUtility.getSubnetRouteInvalidPacket());
                counter.increment();
                return;
            }
            byte[] srcIpBytes = null;
            byte[] dstIpBytes = null;
            String srcIpStr;
            String dstIpStr;
            String srcMac = NWUtil.toStringMacAddress(res.getSourceMACAddress());
            try {
                Packet pkt = res.getPayload();
                if (pkt instanceof IPv4) {
                    IPv4 ipv4 = (IPv4) pkt;
                    srcIpBytes = Ints.toByteArray(ipv4.getSourceAddress());
                    dstIpBytes = Ints.toByteArray(ipv4.getDestinationAddress());
                    srcIpStr = NWUtil.toStringIpAddress(srcIpBytes);
                    dstIpStr = NWUtil.toStringIpAddress(dstIpBytes);
                    Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_recived.toString())
                            .label(srcIpStr + "." + dstIpStr);
                    counter.increment();
                    // It is an ARP request on a configured VPN. So we must
                    // attempt to respond.
                } else {
                    // IPv6 case
                    // TODO: IPv6 deserializer
                    int ethType = BitBufferHelper.getInt(
                            BitBufferHelper.getBits(data, VpnConstants.ETHTYPE_START, VpnConstants.TWO_BYTES));
                    if (ethType == VpnConstants.IP_V6_ETHTYPE) {
                        srcIpBytes = BitBufferHelper.getBits(data, VpnConstants.IP_V6_HDR_START + 64, 128);
                        dstIpBytes = BitBufferHelper.getBits(data, VpnConstants.IP_V6_HDR_START + 192, 128);
                    }
                }
                if (srcIpBytes == null || dstIpBytes == null) {
                    LOG.trace("{} onPacketReceived: Non-IP packet received as {}", LOGGING_PREFIX, notification);
                    return;
                }
                handleIpPackets(srcIpBytes, dstIpBytes, NWUtil.toStringIpAddress(srcIpBytes),
                        NWUtil.toStringIpAddress(dstIpBytes), srcMac, pkt, metadata);

            } catch (InterruptedException | ExecutionException | BufferException ex) {
                // Failed to handle packet
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label(NWUtil.toStringIpAddress(srcIpBytes) + "." + NWUtil.toStringIpAddress(srcIpBytes));
                counter.increment();
                LOG.error("{} onPacketReceived: Failed to handle subnetroute packet.", LOGGING_PREFIX, ex);
            } catch (UnknownHostException e) {
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label(CounterUtility.getSubnetRouteInvalidPacket());
                counter.increment();
                LOG.error("{} onPacketReceived: Unknown host detected while handling subnetroute", LOGGING_PREFIX, e);
            }
            return;
        }
        // All Arp responses learning for invisble IPs is handled by
        // ArpNotificationHandler

    }

    private void handleIpPackets(byte[] srcIp, byte[] dstIp, String srcIpStr, String dstIpStr, String srcMac,
                                 Packet pkt, Uint64 metadata)
            throws UnknownHostException, InterruptedException, ExecutionException {
        Uint32 vpnId = Uint32.valueOf(MetaDataUtil.getVpnIdFromMetadata(metadata));

        LOG.info("{} onPacketReceived: Processing IP Packet received with Source IP {} and Target IP {}"
                + " and vpnId {}", LOGGING_PREFIX, srcIpStr, dstIpStr, vpnId);

        long elanTag = getElanTagFromSubnetRouteMetadata(metadata);
        if (elanTag == 0) {
            LOG.error("{} onPacketReceived: elanTag value from metadata found to be 0, for IPv4 "
                            + " Packet received with Target IP {} src Ip {}  vpnId {}", LOGGING_PREFIX,
                    dstIpStr, srcIp, vpnId);
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.name())
                    .label(srcIpStr + "." + dstIpStr);
            counter.increment();
            return;
        }
        if (pkt instanceof IPv4)  {
            IPv4 ipv4 = (IPv4) pkt;
            Uint64 dpnId = getTargetDpnForPacketOut(dataBroker, elanTag, ipv4.getDestinationAddress());
            if (Objects.equals(dpnId, Uint64.ZERO)) {
                LOG.error("{} onPacketReceived: Could not retrieve dpnID for elanTag {} destIp {}",
                        LOGGING_PREFIX, elanTag, dstIpStr);
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.name())
                        .label(srcIpStr + "." + dstIpStr);
                counter.increment();
                return;
            }
        }

        Optional<VpnIds> vpnIdsOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId));

        if (!vpnIdsOptional.isPresent()) {
            // Donot trigger subnetroute logic for packets from
            // unknown VPNs
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_drop.toString())
                    .label(srcIpStr + "." + dstIpStr);
            counter.increment();
            LOG.info("{} onPacketReceived: Ignoring IPv4 packet with destination Ip {} and source Ip {}"
                    + " as it came on unknown VPN with ID {}", LOGGING_PREFIX, dstIpStr, srcIpStr, vpnId);
            return;
        }

        String vpnIdVpnInstanceName = vpnIdsOptional.get().getVpnInstanceName();
        VpnPortipToPort persistedIP =
                vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnIdVpnInstanceName, dstIpStr);
        if (persistedIP != null && !persistedIP.isLearntIp()) {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_drop.toString())
                    .label(srcIpStr + "." + dstIpStr);
            counter.increment();
            LOG.info("{} onPacketReceived: IP Packet received with Target IP {} source IP {} vpnId {} "
                    + "is a valid Neutron port,ignoring subnet route processing", LOGGING_PREFIX, dstIpStr,
                    srcIp, vpnId);
            return;
        }

        if (vpnUtil.getLearntVpnVipToPort(vpnIdVpnInstanceName, dstIpStr) != null) {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label(srcIpStr + "." + dstIpStr);
            counter.increment();
            LOG.info("{} onPacketReceived: IP Packet received with Target IP {} source Ip {} vpnId {}"
                    + " is an already discovered IPAddress, ignoring subnet route processing",
                    LOGGING_PREFIX, dstIpStr, srcIp, vpnId);
            return;
        }

        if (!vpnIdsOptional.get().isExternalVpn()) {
            handleInternalVpnSubnetRoutePacket(metadata, dstIp, srcIpStr, dstIpStr, vpnIdVpnInstanceName,
                    elanTag);
            return;
        }

        handleBgpVpnSubnetRoute(srcMac, dstIp, dstIpStr, srcIpStr, elanTag);
    }

    private void handleBgpVpnSubnetRoute(String srcMac, byte[] dstIp, String dstIpStr, String srcIpStr,
            long elanTag) throws UnknownHostException {
        LOG.info("{} handleBgpVpnSubnetRoute: Processing IP Packet received with Source IP {} and Target IP {}"
                + " and elan Tag {}", LOGGING_PREFIX, srcIpStr, dstIpStr, elanTag);
        SubnetOpDataEntry targetSubnetForPacketOut =
                getTargetSubnetForPacketOut(elanTag, dstIpStr);
        if (targetSubnetForPacketOut != null) {
            // Handle subnet routes ip requests
            transmitArpOrNsPacket(targetSubnetForPacketOut.getNhDpnId(),
                                        srcIpStr, srcMac, dstIp, dstIpStr, elanTag);
        } else {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label(srcIpStr + "." + dstIpStr);
            counter.increment();
            LOG.debug("{} handleBgpVpnSubnetRoute: Could not find target subnet for packet out {}", LOGGING_PREFIX,
                    dstIpStr);
        }
    }

    private void handleInternalVpnSubnetRoutePacket(Uint64 metadata, byte[] dstIp, String srcIpStr, String dstIpStr,
            String vpnIdVpnInstanceName, long elanTag)
            throws InterruptedException, ExecutionException, UnknownHostException {
        String vmVpnInterfaceName = vpnUtil.getVpnInterfaceName(metadata);
        if (isTunnel(vmVpnInterfaceName)) {
            handlePacketFromTunnelToExternalNetwork(vpnIdVpnInstanceName, srcIpStr, dstIp, dstIpStr, elanTag);
        }
        VpnInterface vmVpnInterface = vpnUtil.getVpnInterface(vmVpnInterfaceName);
        if (vmVpnInterface == null) {
            LOG.error("Vpn interface {} doesn't exist.", vmVpnInterfaceName);
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label(srcIpStr + "." + dstIpStr);
            counter.increment();
            return;
        }
        if (VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnIdVpnInstanceName,
               new ArrayList<>(vmVpnInterface.nonnullVpnInstanceNames().values()))
               && !vpnUtil.isBgpVpnInternet(vpnIdVpnInstanceName)) {
            LOG.trace("Unknown IP is in internal network");
            handlePacketToInternalNetwork(dstIp, dstIpStr, elanTag, srcIpStr);
        } else {
            LOG.trace("Unknown IP is in external network");
            String vpnName = vpnUtil.getInternetVpnFromVpnInstanceList(
                    new ArrayList<>(vmVpnInterface.nonnullVpnInstanceNames().values()));
            if (vpnName != null) {
                handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), vpnName, dstIp, dstIpStr, elanTag);
            } else {
                vpnName = VpnHelper.getFirstVpnNameFromVpnInterface(vmVpnInterface);
                LOG.trace("Unknown IP is in external network, but internet VPN not found." + " fallback to first VPN");
                handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), vpnName, dstIp, dstIpStr, elanTag);

            }
        }
    }

    private void transmitArpOrNsPacket(Uint64 dpnId, String sourceIpAddress, String sourceMac, byte[] dstIpBytes,
            String dstIpAddress, long elanTag) throws UnknownHostException {
        long groupid = VpnUtil.getRemoteBCGroup(elanTag);
        if (NWUtil.isIpv4Address(dstIpAddress)) {
            LOG.debug("Sending ARP: srcIp={}, srcMac={}, dstIp={}, dpId={}, elan-tag={}, groupid={}", sourceIpAddress,
                    sourceMac, dstIpAddress, dpnId, elanTag, groupid);
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_arp_sent.toString())
                    .label(sourceIpAddress + "." + dstIpAddress);
            counter.increment();

            TransmitPacketInput packetInput =
                    ArpUtils.createArpRequestInput(dpnId, groupid, HexEncode.bytesFromHexString(sourceMac),
                            InetAddress.getByName(sourceIpAddress).getAddress(), dstIpBytes);
            LoggingFutures.addErrorLogging(packetService.transmitPacket(packetInput), LOG, "Transmit packet");
        } else {
            // IPv6 case
            LOG.debug("Sending NS: srcIp={}, srcMac={}, dstIp={}, dpId={}, elan-tag={}, groupid={}", sourceIpAddress,
                    sourceMac, dstIpAddress, dpnId, elanTag, groupid);
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_ns_sent.toString())
                    .label(sourceIpAddress + "." + dstIpAddress);
            counter.increment();

            VpnUtil.sendNeighborSolicationToOfGroup(this.ipv6NdUtilService, new Ipv6Address(sourceIpAddress),
                    new MacAddress(sourceMac), new Ipv6Address(dstIpAddress), groupid, dpnId);
        }
    }

    private void handlePacketToInternalNetwork(byte[] dstIp, String dstIpStr, long elanTag, String srcIpStr)
            throws UnknownHostException {
        try {
            SubnetOpDataEntry targetSubnetForPacketOut =
                    getTargetSubnetForPacketOut(elanTag, dstIpStr);

            if (targetSubnetForPacketOut == null) {
                LOG.debug("Couldn't find matching subnet for elan tag {} and destination ip {}", elanTag, dstIpStr);
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label(srcIpStr + "." + dstIpStr);
                counter.increment();
                return;
            }

            Optional<Subnetmap> subnetMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    VpnUtil.buildSubnetmapIdentifier(targetSubnetForPacketOut.getSubnetId()));
            if (!subnetMap.isPresent()) {
                LOG.debug("Couldn't find subnet map for subnet {}", targetSubnetForPacketOut.getSubnetId());
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label(srcIpStr + "." + dstIpStr);
                counter.increment();
                return;
            }

            String sourceIp = subnetMap.get().getRouterInterfaceFixedIp();
            if (sourceIp == null) {
                LOG.debug("Subnet map {} doesn't have a router interface ip defined", subnetMap.get().getId());
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label("." + dstIpStr);
                counter.increment();
                return;
            }

            String sourceMac = subnetMap.get().getRouterIntfMacAddress();
            if (sourceMac == null) {
                LOG.debug("Subnet map {} doesn't have a router interface mac address defined",
                        subnetMap.get().getId());
                Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                        .label(sourceIp + "." + dstIpStr);
                counter.increment();
                return;
            }

            transmitArpOrNsPacket(targetSubnetForPacketOut.getNhDpnId(),
                                        sourceIp, sourceMac, dstIp, dstIpStr, elanTag);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("handlePacketToInternalNetwork: Failed to read data store for destIp {} elanTag {}", dstIpStr,
                    elanTag);
        }
    }

    private void handlePacketFromTunnelToExternalNetwork(String vpnIdVpnInstanceName, String srcIpStr, byte[] dstIp,
        String dstIpStr, long elanTag) throws UnknownHostException {
        String routerId = vpnUtil.getAssociatedExternalRouter(srcIpStr);
        if (null == routerId) {
            LOG.debug("This ip is not associated with any external router: {}", srcIpStr);
            return;
        }
        handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), routerId, dstIp, dstIpStr, elanTag);
    }

    private void handlePacketToExternalNetwork(Uuid vpnInstanceNameUuid, String routerId, byte[] dstIp, String dstIpStr,
            long elanTag) throws UnknownHostException {
        Routers externalRouter = vpnUtil.getExternalRouter(routerId);
        if (externalRouter == null) {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label("." + dstIpStr);
            counter.increment();
            LOG.debug("{} handlePacketToExternalNetwork: Can't find external router with id {}", LOGGING_PREFIX,
                    routerId);
            return;
        }

        Map<ExternalIpsKey, ExternalIps> externalIpsMap = externalRouter.getExternalIps();
        if (externalIpsMap == null || externalIpsMap.isEmpty()) {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label("." + dstIpStr);
            counter.increment();
            LOG.debug("{} handlePacketToExternalNetwork: Router {} doesn't have any external ips.",
                    LOGGING_PREFIX, externalRouter.getRouterName());
            return;
        }

        java.util.Optional<ExternalIps> externalIp = externalRouter.getExternalIps().values().stream()
                .filter(eip -> vpnInstanceNameUuid.equals(eip.getSubnetId())).findFirst();
        if (!externalIp.isPresent()) {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label(externalIp.get().getIpAddress() + "." + dstIpStr);
            counter.increment();
            LOG.debug("{} handlePacketToExternalNetwork: Router {} doesn't have an external ip for subnet id {}.",
                    LOGGING_PREFIX, externalRouter.getRouterName(), vpnInstanceNameUuid);
            return;
        }

        Uint64 dpnId = centralizedSwitchProvider.getPrimarySwitchForRouter(externalRouter.getRouterName());
        if (Uint64.ZERO.equals(dpnId)) {
            Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_failed.toString())
                    .label(externalIp.get().getIpAddress() + "." + dstIpStr);
            counter.increment();
            LOG.debug("{} handlePacketToExternalNetwork: Could not find primary switch for router {}.",
                    LOGGING_PREFIX, externalRouter.getRouterName());
            return;
        }

        transmitArpOrNsPacket(dpnId, externalIp.get().getIpAddress(), externalRouter.getExtGwMacAddress(), dstIp,
                dstIpStr, elanTag);

        Counter counter = packetInCounter.label(CounterUtility.subnet_route_packet_processed.toString())
                .label(externalIp.get().getIpAddress() + "." + dstIpStr);
        counter.increment();
    }

    // return only the first VPN subnetopdataentry
    @Nullable
    private SubnetOpDataEntry getTargetSubnetForPacketOut(long elanTag, String ipAddress) {
        ElanTagName elanInfo = vpnUtil.getElanInfoByElanTag(elanTag);
        if (elanInfo == null) {
            LOG.error("{} getTargetSubnetForPacketOut: Unable to retrieve ElanInfo for elanTag {}", LOGGING_PREFIX,
                    elanTag);
            return null;
        }
        try {
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, VpnUtil.buildNetworkMapIdentifier(new Uuid(
                            elanInfo.getName())));
            if (!optionalNetworkMap.isPresent()) {
                LOG.debug("{} getTargetSubnetForPacketOut: No network map found for elan info {}", LOGGING_PREFIX,
                        elanInfo.getName());
                return null;
            }
            List<Uuid> subnetList = optionalNetworkMap.get().getSubnetIdList();
            LOG.debug("{} getTargetSubnetForPacketOut: Obtained subnetList as {} for network {}", LOGGING_PREFIX,
                    subnetList, elanInfo.getName());
            if (subnetList != null) {
                for (Uuid subnetId : subnetList) {
                    String vpnName = null;
                    Subnetmap sn = vpnUtil.getSubnetmapFromItsUuid(subnetId);
                    if (sn != null && sn.getVpnId() != null) {
                        vpnName = sn.getVpnId().getValue();
                    }
                    if (vpnName == null) {
                        continue;
                    }
                    Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, VpnUtil.buildSubnetOpDataEntryInstanceIdentifier(subnetId));
                    if (!optionalSubs.isPresent()) {
                        continue;
                    }
                    SubnetOpDataEntry subOpEntry = optionalSubs.get();
                    if (subOpEntry.getNhDpnId() != null) {
                        LOG.trace("{} getTargetSubnetForPacketOut: Viewing Subnet {}", LOGGING_PREFIX,
                                subnetId.getValue());
                        IpPrefix cidr = IpPrefixBuilder.getDefaultInstance(subOpEntry.getSubnetCidr());
                        boolean match = NWUtil.isIpAddressInRange(IpAddressBuilder.getDefaultInstance(ipAddress), cidr);
                        LOG.trace("{} getTargetSubnetForPacketOut: Viewing Subnet {} matching {}", LOGGING_PREFIX,
                            subnetId.getValue(), match);
                        if (match) {
                            return subOpEntry;
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{} getTargetDpnForPacketOut: Failed to read data store for elan {}", LOGGING_PREFIX,
                    elanInfo.getName());
        }
        return null;
    }

    private Uint64 getTargetDpnForPacketOut(DataBroker broker, long elanTag, int ipAddress) {
        Uint64 dpnid = Uint64.ZERO;
        ElanTagName elanInfo = vpnUtil.getElanInfoByElanTag(elanTag);
        if (elanInfo == null) {
            LOG.error("{} getTargetDpnForPacketOut: Unable to retrieve ElanInfo for elanTag {}",
                    LOGGING_PREFIX, elanTag);
            return dpnid;
        }
        try {
            InstanceIdentifier<NetworkMap> networkId = InstanceIdentifier.builder(NetworkMaps.class)
                    .child(NetworkMap.class, new NetworkMapKey(new Uuid(elanInfo.getName()))).build();
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.CONFIGURATION, networkId);

            if (optionalNetworkMap.isPresent()) {
                List<Uuid> subnetList = optionalNetworkMap.get().getSubnetIdList();
                LOG.trace("{} getTargetDpnForPacketOut: Obtained subnetList as {} for network {}", LOGGING_PREFIX,
                        subnetList, networkId);
                for (Uuid subnetId : subnetList) {
                    InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier
                            .builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                                    new SubnetOpDataEntryKey(subnetId)).build();
                    Optional<SubnetOpDataEntry> optionalSubs = SingleTransactionDataBroker.syncReadOptional(broker,
                            LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
                    if (!optionalSubs.isPresent()) {
                        LOG.error("{} getTargetDpnForPacketOut: Unable to fetch subnetOp data for subnet {}."
                                        + " Unable to handle subnet route packet from targetIP {} elanTag {}",
                                LOGGING_PREFIX, subnetId.getValue(), ipAddress, elanTag);
                        continue;
                    }
                    SubnetOpDataEntry subOpEntry = optionalSubs.get();
                    if (subOpEntry.getNhDpnId() != null) {
                        LOG.trace("{} getTargetDpnForPacketOut: Viewing Subnet {}", LOGGING_PREFIX,
                                subnetId.getValue());
                        boolean match = NWUtil.isIpInSubnet(ipAddress, subOpEntry.getSubnetCidr());
                        LOG.trace("{} getTargetDpnForPacketOut: Viewing Subnet {} matching {}", LOGGING_PREFIX,
                                subnetId.getValue(), match);
                        if (match) {
                            dpnid = subOpEntry.getNhDpnId();
                            return dpnid;
                        }
                    }
                }
            } else {
                LOG.warn("getTargetDpnForPacketOut: NetworkMap not present for {} elanTag {}", elanInfo.getName(),
                        elanTag);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getTargetDpnForPacketOut: Read failed for with exception : ", e);
        }
        return dpnid;
    }

    public static long getElanTagFromSubnetRouteMetadata(Uint64 metadata) {
        return ((metadata.toJava().and(MetaDataUtil.METADATA_MASK_ELAN_SUBNET_ROUTE.toJava()))
                .shiftRight(24)).longValue();
    }

    public boolean isTunnel(String interfaceName) {
        return interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName).augmentation(IfTunnel.class) != null;
    }
}
