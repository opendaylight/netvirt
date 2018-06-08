/*
 * Copyright (c) 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.primitives.Ints;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.vpnmanager.api.ICentralizedSwitchProvider;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.utilities.VpnManagerCounters;
import org.opendaylight.openflowplugin.libraries.liblldp.BitBufferHelper;
import org.opendaylight.openflowplugin.libraries.liblldp.BufferException;
import org.opendaylight.openflowplugin.libraries.liblldp.HexEncode;
import org.opendaylight.openflowplugin.libraries.liblldp.NetUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.Packet;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.Ipv6NdUtilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
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

    @Inject
    public SubnetRoutePacketInHandler(final DataBroker dataBroker, final PacketProcessingService packetService,
            final OdlInterfaceRpcService odlInterfaceRpcService,
            final ICentralizedSwitchProvider centralizedSwitchProvider, final IInterfaceManager interfaceManager,
            final Ipv6NdUtilService ipv6NdUtilService) {
        this.dataBroker = dataBroker;
        this.packetService = packetService;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.centralizedSwitchProvider = centralizedSwitchProvider;
        this.interfaceManager = interfaceManager;
        this.ipv6NdUtilService = ipv6NdUtilService;
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {

        short tableId = notification.getTableId().getValue();
        LOG.trace("{} onPacketReceived: Packet punted from table {}", LOGGING_PREFIX, tableId);
        byte[] data = notification.getPayload();
        if (notification.getMatch() == null || notification.getMatch().getMetadata() == null) {
            LOG.error("{} onPacketReceived: Received from table {} where the match or metadata are null",
                    LOGGING_PREFIX, tableId);
            return;
        }
        BigInteger metadata = notification.getMatch().getMetadata().getMetadata();
        Ethernet res = new Ethernet();

        if (tableId == NwConstants.L3_SUBNET_ROUTE_TABLE) {
            LOG.trace("{} onPacketReceived: Some packet received as {}", LOGGING_PREFIX, notification);
            try {
                res.deserialize(data, 0, data.length * NetUtils.NUM_BITS_IN_A_BYTE);
            } catch (PacketException e) {
                LOG.error("{} onPacketReceived: Failed to decode Packet ", LOGGING_PREFIX, e);
                VpnManagerCounters.subnet_route_packet_failed.inc();
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
                srcIpStr = NWUtil.toStringIpAddress(srcIpBytes);
                dstIpStr = NWUtil.toStringIpAddress(dstIpBytes);

                handleIpPackets(srcIpBytes, dstIpBytes, srcIpStr, dstIpStr, srcMac, metadata);

            } catch (UnknownHostException | InterruptedException | ExecutionException | BufferException ex) {
                // Failed to handle packet
                VpnManagerCounters.subnet_route_packet_failed.inc();
                LOG.error("{} onPacketReceived: Failed to handle subnetroute packet.", LOGGING_PREFIX, ex);
            } catch (ReadFailedException e) {
                VpnManagerCounters.subnet_route_packet_failed.inc();
                LOG.error("{} onPacketReceived: Failed to read data-store.", LOGGING_PREFIX, e);
            }
            return;
        }
        // All Arp responses learning for invisble IPs is handled by
        // ArpNotificationHandler

    }

    private void handleIpPackets(byte[] srcIp, byte[] dstIp, String srcIpStr, String dstIpStr, String srcMac,
            BigInteger metadata)
            throws UnknownHostException, InterruptedException, ExecutionException, ReadFailedException {
        long vpnId = MetaDataUtil.getVpnIdFromMetadata(metadata);

        LOG.info("{} onPacketReceived: Processing IP Packet received with Source IP {} and Target IP {}"
                + " and vpnId {}", LOGGING_PREFIX, srcIpStr, dstIpStr, vpnId);

        Optional<VpnIds> vpnIdsOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId));

        if (!vpnIdsOptional.isPresent()) {
            // Donot trigger subnetroute logic for packets from
            // unknown VPNs
            VpnManagerCounters.subnet_route_packet_ignored.inc();
            LOG.info("{} onPacketReceived: Ignoring IPv4 packet with destination Ip {} and source Ip {}"
                    + " as it came on unknown VPN with ID {}", LOGGING_PREFIX, dstIpStr, srcIpStr, vpnId);
            return;
        }

        String vpnIdVpnInstanceName = vpnIdsOptional.get().getVpnInstanceName();
        if (VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker, vpnIdVpnInstanceName, dstIpStr) != null) {
            VpnManagerCounters.subnet_route_packet_ignored.inc();
            LOG.info("{} onPacketReceived: IP Packet received with Target IP {} source IP {} vpnId {} "
                    + "is a valid Neutron port,ignoring subnet route processing", LOGGING_PREFIX, dstIpStr,
                    srcIp, vpnId);
            return;
        }

        if (VpnUtil.getLearntVpnVipToPort(dataBroker, vpnIdVpnInstanceName, dstIpStr) != null) {
            VpnManagerCounters.subnet_route_packet_ignored.inc();
            LOG.info("{} onPacketReceived: IP Packet received with Target IP {} source Ip {} vpnId {}"
                    + " is an already discovered IPAddress, ignoring subnet route processing",
                    LOGGING_PREFIX, dstIpStr, srcIp, vpnId);
            return;
        }

        long elanTag = MetaDataUtil.getElanTagFromMetadata(metadata);
        if (elanTag == 0L) {
            VpnManagerCounters.subnet_route_packet_failed.inc();
            LOG.error("{} onPacketReceived: elanTag value from metadata found to be 0, for IP "
                    + " Packet received with Target IP {} src Ip {} vpnId {}",
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
                getTargetSubnetForPacketOut(dataBroker, elanTag, dstIpStr);

        if (targetSubnetForPacketOut != null) {
            // Handle subnet routes ip requests
            transmitArpOrNsPacket(targetSubnetForPacketOut.getNhDpnId(), srcIpStr, srcMac, dstIp, dstIpStr, elanTag);
        } else {
            VpnManagerCounters.subnet_route_packet_failed.inc();
            LOG.debug("{} handleBgpVpnSubnetRoute: Could not find target subnet for packet out {}", LOGGING_PREFIX,
                    dstIpStr);
        }
    }

    private void handleInternalVpnSubnetRoutePacket(BigInteger metadata, byte[] dstIp, String srcIpStr, String dstIpStr,
            String vpnIdVpnInstanceName, long elanTag)
            throws InterruptedException, ExecutionException, UnknownHostException {
        String vmVpnInterfaceName = VpnUtil.getVpnInterfaceName(odlInterfaceRpcService, metadata);
        if (isTunnel(vmVpnInterfaceName)) {
            handlePacketFromTunnelToExternalNetwork(vpnIdVpnInstanceName, srcIpStr, dstIp, dstIpStr, elanTag);
        }
        VpnInterface vmVpnInterface = VpnUtil.getVpnInterface(dataBroker, vmVpnInterfaceName);
        if (vmVpnInterface == null) {
            LOG.error("Vpn interface {} doesn't exist.", vmVpnInterfaceName);
            VpnManagerCounters.subnet_route_packet_failed.inc();
            return;
        }
        if (VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnIdVpnInstanceName,
               vmVpnInterface.getVpnInstanceNames())
               && !VpnUtil.isBgpVpnInternet(dataBroker, vpnIdVpnInstanceName)) {
            LOG.trace("Unknown IP is in internal network");
            handlePacketToInternalNetwork(dstIp, dstIpStr, elanTag);
        } else {
            LOG.trace("Unknown IP is in external network");
            String vpnName =
                    VpnUtil.getInternetVpnFromVpnInstanceList(dataBroker, vmVpnInterface.getVpnInstanceNames());
            if (vpnName != null) {
                handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), vpnName, dstIp, dstIpStr, elanTag);
            } else {
                vpnName = VpnHelper.getFirstVpnNameFromVpnInterface(vmVpnInterface);
                LOG.trace("Unknown IP is in external network, but internet VPN not found." + " fallback to first VPN");
                handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), vpnName, dstIp, dstIpStr, elanTag);

            }
        }
    }

    private void transmitArpOrNsPacket(BigInteger dpnId, String sourceIpAddress, String sourceMac, byte[] dstIpBytes,
            String dstIpAddress, long elanTag) throws UnknownHostException {
        long groupid = VpnUtil.getRemoteBCGroup(elanTag);
        if (NWUtil.isIpv4Address(dstIpAddress)) {
            LOG.debug("Sending ARP: srcIp={}, srcMac={}, dstIp={}, dpId={}, elan-tag={}", sourceIpAddress, sourceMac,
                    dstIpAddress, dpnId, elanTag);
            VpnManagerCounters.subnet_route_packet_arp_sent.inc();

            TransmitPacketInput packetInput =
                    ArpUtils.createArpRequestInput(dpnId, groupid, HexEncode.bytesFromHexString(sourceMac),
                            InetAddress.getByName(sourceIpAddress).getAddress(), dstIpBytes);
            JdkFutures.addErrorLogging(packetService.transmitPacket(packetInput), LOG, "Transmit packet");
        } else {
            // IPv6 case
            LOG.debug("Sending NS: srcIp={}, srcMac={}, dstIp={}, dpId={}, elan-tag={}", sourceIpAddress, sourceMac,
                    dstIpAddress, dpnId, elanTag);
            VpnManagerCounters.subnet_route_packet_ns_sent.inc();

            VpnUtil.sendNeighborSolicationToOfGroup(this.ipv6NdUtilService, new Ipv6Address(sourceIpAddress),
                    new MacAddress(sourceMac), new Ipv6Address(dstIpAddress), groupid, dpnId);
        }
    }

    private void handlePacketToInternalNetwork(byte[] dstIp, String dstIpStr, long elanTag)
            throws UnknownHostException {
        try {
            SubnetOpDataEntry targetSubnetForPacketOut =
                    getTargetSubnetForPacketOut(dataBroker, elanTag, dstIpStr);

            if (targetSubnetForPacketOut == null) {
                LOG.debug("Couldn't find matching subnet for elan tag {} and destination ip {}", elanTag, dstIpStr);
                VpnManagerCounters.subnet_route_packet_failed.inc();
                return;
            }

            Optional<Subnetmap> subnetMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    VpnUtil.buildSubnetmapIdentifier(targetSubnetForPacketOut.getSubnetId()));
            if (!subnetMap.isPresent()) {
                LOG.debug("Couldn't find subnet map for subnet {}", targetSubnetForPacketOut.getSubnetId());
                VpnManagerCounters.subnet_route_packet_failed.inc();
                return;
            }

            String sourceIp = subnetMap.get().getRouterInterfaceFixedIp();
            if (sourceIp == null) {
                LOG.debug("Subnet map {} doesn't have a router interface ip defined", subnetMap.get().getId());
                VpnManagerCounters.subnet_route_packet_failed.inc();
                return;
            }

            String sourceMac = subnetMap.get().getRouterIntfMacAddress();
            if (sourceMac == null) {
                LOG.debug("Subnet map {} doesn't have a router interface mac address defined",
                        subnetMap.get().getId());
                VpnManagerCounters.subnet_route_packet_failed.inc();
                return;
            }

            transmitArpOrNsPacket(targetSubnetForPacketOut.getNhDpnId(), sourceIp, sourceMac, dstIp, dstIpStr, elanTag);
        } catch (ReadFailedException e) {
            LOG.error("handlePacketToInternalNetwork: Failed to read data store for destIp {} elanTag {}", dstIpStr,
                    elanTag);
        }
    }

    private void handlePacketFromTunnelToExternalNetwork(String vpnIdVpnInstanceName, String srcIpStr, byte[] dstIp,
            String dstIpStr, long elanTag) throws UnknownHostException {
        String routerId = VpnUtil.getAssociatedExternalRouter(dataBroker, srcIpStr);
        if (null == routerId) {
            LOG.debug("This ip is not associated with any external router: {}", srcIpStr);
        }
        handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), routerId, dstIp, dstIpStr, elanTag);
    }

    private void handlePacketToExternalNetwork(Uuid vpnInstanceNameUuid, String routerId, byte[] dstIp, String dstIpStr,
            long elanTag) throws UnknownHostException {
        Routers externalRouter = VpnUtil.getExternalRouter(dataBroker, routerId);
        if (externalRouter == null) {
            VpnManagerCounters.subnet_route_packet_failed.inc();
            LOG.debug("{} handlePacketToExternalNetwork: Can't find external router with id {}", LOGGING_PREFIX,
                    routerId);
            return;
        }

        List<ExternalIps> externalIps = externalRouter.getExternalIps();
        if (externalIps == null || externalIps.isEmpty()) {
            VpnManagerCounters.subnet_route_packet_failed.inc();
            LOG.debug("{} handlePacketToExternalNetwork: Router {} doesn't have any external ips.",
                    LOGGING_PREFIX, externalRouter.getRouterName());
            return;
        }

        java.util.Optional<ExternalIps> externalIp = externalRouter.getExternalIps().stream()
                .filter(eip -> vpnInstanceNameUuid.equals(eip.getSubnetId())).findFirst();
        if (!externalIp.isPresent()) {
            VpnManagerCounters.subnet_route_packet_failed.inc();
            LOG.debug("{} handlePacketToExternalNetwork: Router {} doesn't have an external ip for subnet id {}.",
                    LOGGING_PREFIX, externalRouter.getRouterName(), vpnInstanceNameUuid);
            return;
        }

        BigInteger dpnId = centralizedSwitchProvider.getPrimarySwitchForRouter(externalRouter.getRouterName());
        if (BigInteger.ZERO.equals(dpnId)) {
            VpnManagerCounters.subnet_route_packet_failed.inc();
            LOG.debug("{} handlePacketToExternalNetwork: Could not find primary switch for router {}.",
                    LOGGING_PREFIX, externalRouter.getRouterName());
            return;
        }

        transmitArpOrNsPacket(dpnId, externalIp.get().getIpAddress(), externalRouter.getExtGwMacAddress(), dstIp,
                dstIpStr, elanTag);
        return;
    }

    // return only the first VPN subnetopdataentry
    private static SubnetOpDataEntry getTargetSubnetForPacketOut(DataBroker broker, long elanTag, String ipAddress) {
        ElanTagName elanInfo = VpnUtil.getElanInfoByElanTag(broker, elanTag);
        if (elanInfo == null) {
            LOG.error("{} getTargetDpnForPacketOut: Unable to retrieve ElanInfo for elanTag {}", LOGGING_PREFIX,
                    elanTag);
            return null;
        }
        try {
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.CONFIGURATION, VpnUtil.buildNetworkMapIdentifier(new Uuid(
                            elanInfo.getName())));
            if (!optionalNetworkMap.isPresent()) {
                LOG.debug("{} getTargetDpnForPacketOut: No network map found for elan info {}", LOGGING_PREFIX,
                        elanInfo.getName());
                return null;
            }
            List<Uuid> subnetList = optionalNetworkMap.get().getSubnetIdList();
            LOG.debug("{} getTargetDpnForPacketOut: Obtained subnetList as {} for network {}", LOGGING_PREFIX,
                    subnetList, elanInfo.getName());
            for (Uuid subnetId : subnetList) {
                String vpnName = null;
                Subnetmap sn = VpnUtil.getSubnetmapFromItsUuid(broker, subnetId);
                if (sn != null && sn.getVpnId() != null) {
                    vpnName = sn.getVpnId().getValue();
                }
                if (vpnName == null) {
                    continue;
                }
                Optional<SubnetOpDataEntry> optionalSubs;
                optionalSubs = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.buildSubnetOpDataEntryInstanceIdentifier(subnetId));
                if (!optionalSubs.isPresent()) {
                    continue;
                }
                SubnetOpDataEntry subOpEntry = optionalSubs.get();
                if (subOpEntry.getNhDpnId() != null) {
                    LOG.trace("{} getTargetDpnForPacketOut: Viewing Subnet {}", LOGGING_PREFIX, subnetId.getValue());
                    IpPrefix cidr = new IpPrefix(subOpEntry.getSubnetCidr().toCharArray());
                    boolean match = NWUtil.isIpAddressInRange(new IpAddress(ipAddress.toCharArray()), cidr);
                    LOG.trace("{} getTargetDpnForPacketOut: Viewing Subnet {} matching {}", LOGGING_PREFIX,
                            subnetId.getValue(), match);
                    if (match) {
                        return subOpEntry;
                    }
                }
            }
        } catch (ReadFailedException e) {
            LOG.error("{} getTargetDpnForPacketOut: Failed to read data store for elan {}", LOGGING_PREFIX,
                    elanInfo.getName());
        }
        return null;
    }

    public boolean isTunnel(String interfaceName) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.Interface configIface =
            interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
        IfTunnel ifTunnel = configIface.augmentation(IfTunnel.class);
        if (ifTunnel != null) {
            return true;
        }
        return false;
    }
}
