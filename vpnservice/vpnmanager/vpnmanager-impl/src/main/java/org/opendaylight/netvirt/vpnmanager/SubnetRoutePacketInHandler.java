/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.liblldp.HexEncode;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.Packet;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.netvirt.vpnmanager.api.ICentralizedSwitchProvider;
import org.opendaylight.netvirt.vpnmanager.utilities.VpnManagerCounters;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubnetRoutePacketInHandler implements PacketProcessingListener {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRoutePacketInHandler.class);
    private final DataBroker dataBroker;
    private final PacketProcessingService packetService;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final ICentralizedSwitchProvider centralizedSwitchProvider;

    public SubnetRoutePacketInHandler(final DataBroker dataBroker, final PacketProcessingService packetService,
            final OdlInterfaceRpcService odlInterfaceRpcService,
            final ICentralizedSwitchProvider centralizedSwitchProvider) {
        this.dataBroker = dataBroker;
        this.packetService = packetService;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.centralizedSwitchProvider = centralizedSwitchProvider;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onPacketReceived(PacketReceived notification) {
        LOG.trace("SubnetRoutePacketInHandler: PacketReceived invoked...");

        short tableId = notification.getTableId().getValue();
        byte[] data = notification.getPayload();
        if (notification.getMatch() == null || notification.getMatch().getMetadata() == null) {
            LOG.debug("on packet received where the match or metadata are null");
            return;
        }
        BigInteger metadata = notification.getMatch().getMetadata().getMetadata();
        Ethernet res = new Ethernet();

        if (tableId == NwConstants.L3_SUBNET_ROUTE_TABLE) {
            LOG.trace("SubnetRoutePacketInHandler: Some packet received as {}", notification);
            try {
                res.deserialize(data, 0, data.length * NetUtils.NumBitsInAByte);
            } catch (PacketException e) {
                LOG.warn("SubnetRoutePacketInHandler: Failed to decode Packet ", e);
                return;
            }
            try {
                Packet pkt = res.getPayload();
                if (pkt instanceof IPv4) {
                    IPv4 ipv4 = (IPv4) pkt;
                    byte[] srcMac = res.getSourceMACAddress();
                    byte[] dstMac = res.getDestinationMACAddress();
                    byte[] srcIp = Ints.toByteArray(ipv4.getSourceAddress());
                    byte[] dstIp = Ints.toByteArray(ipv4.getDestinationAddress());
                    String dstIpStr = NWUtil.toStringIpAddress(dstIp);
                    String srcIpStr = NWUtil.toStringIpAddress(srcIp);
                    /*
                     * if (VpnUtil.getNeutronPortNamefromPortFixedIp(dataBroker,
                     * dstIpStr) != null) { LOG.debug(
                     * "SubnetRoutePacketInHandler: IPv4 Packet received with "
                     * +
                     * "Target IP {} is a valid Neutron port, ignoring subnet route processing"
                     * , dstIpStr); return; }
                     */
                    // It is an ARP request on a configured VPN. So we must
                    // attempt to respond.
                    long vpnId = MetaDataUtil.getVpnIdFromMetadata(metadata);

                    LOG.info("SubnetRoutePacketInHandler: Processing IPv4 Packet received with Source IP {} "
                            + "and Target IP {} and vpnId {}", srcIpStr, dstIpStr, vpnId);

                    InstanceIdentifier<VpnIds> vpnIdsInstanceIdentifier = getVpnIdToVpnInstanceIdentifier(vpnId);
                    Optional<VpnIds> vpnIdsOptional =
                            VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);

                    if (!vpnIdsOptional.isPresent()) {
                        // Donot trigger subnetroute logic for packets from
                        // unknown VPNs
                        VpnManagerCounters.subnet_route_packet_ignored.inc();
                        SubnetRoutePacketInHandler.LOG
                                .debug("Ignoring IPv4 packet with destination Ip {} and source Ip {} as it came on "
                                        + "unknown VPN with ID {}", new Object[] { dstIpStr, srcIpStr, vpnId });
                        return;
                    }

                    String vpnIdVpnInstanceName = ((VpnIds) vpnIdsOptional.get()).getVpnInstanceName();
                    if (VpnUtil.getNeutronPortFromVpnPortFixedIp(this.dataBroker, vpnIdVpnInstanceName,
                            dstIpStr) != null) {
                        VpnManagerCounters.subnet_route_packet_ignored.inc();
                        SubnetRoutePacketInHandler.LOG
                                .debug("IPv4 Packet received with Target IP {} is a valid Neutron port, "
                                        + "ignoring subnet route processing", (Object) dstIpStr);
                        return;
                    }

                    if (VpnUtil.getLearntVpnVipToPort(this.dataBroker, vpnIdVpnInstanceName, dstIpStr) != null) {
                        VpnManagerCounters.subnet_route_packet_ignored.inc();
                        SubnetRoutePacketInHandler.LOG
                                .debug("IPv4 Packet received with Target IP {} is an already discovered IPAddress, "
                                        + "ignoring subnet route processing", (Object) dstIpStr);
                        return;
                    }

                    long elanTag = getElanTagFromSubnetRouteMetadata(metadata);
                    if (elanTag == 0L) {
                        VpnManagerCounters.subnet_route_packet_arp_failed.inc();
                        SubnetRoutePacketInHandler.LOG.debug(
                                "elanTag value from metadata found to be 0, for IPv4 Packet received with Target IP {}",
                                (Object) dstIpStr);
                        return;
                    }

                    if (!((VpnIds) vpnIdsOptional.get()).isExternalVpn()) {
                        this.handleInternalVpnSubnetRoutePacket(metadata, dstIp, dstIpStr, ipv4.getDestinationAddress(),
                                vpnIdVpnInstanceName, elanTag);
                        return;
                    }

                    LOG.info("SubnetRoutePacketInHandler: Processing IPv4 Packet received with Source IP {} "
                            + "and Target IP {} and elan Tag {}", srcIpStr, dstIpStr, elanTag);
                    SubnetOpDataEntry targetSubnetForPacketOut =
                            getTargetSubnetForPacketOut(dataBroker, elanTag, ipv4.getDestinationAddress());

                    if (targetSubnetForPacketOut != null) {
                        // Handle subnet routes ip requests
                        if (!Objects.equals(targetSubnetForPacketOut.getNhDpnId(), BigInteger.ZERO)) {
                            long groupid = VpnUtil.getRemoteBCGroup(elanTag);
                            String key = srcIpStr + dstIpStr;
                            TransmitPacketInput arpRequestInput = ArpUtils.createArpRequestInput(
                                    targetSubnetForPacketOut.getNhDpnId(), groupid, srcMac, srcIp, dstIp);
                            packetService.transmitPacket(arpRequestInput);
                        }
                    }
                }
            } catch (Exception ex) {
                // Failed to handle packet
                LOG.error("SubnetRoutePacketInHandler: Failed to handle subnetroute packets ", ex);
            }
            return;
        }
        // All Arp responses learning for invisble IPs is handled by
        // ArpNotificationHandler

    }

    private void handleInternalVpnSubnetRoutePacket(BigInteger metadata, byte[] dstIp, String dstIpStr,
            int destinationAddress, String vpnIdVpnInstanceName, long elanTag)
            throws InterruptedException, ExecutionException, UnknownHostException {
        String vpnInterfaceName = this.getVpnInterfaceName(metadata);
        VpnInterface vpnInterface = VpnUtil.getVpnInterface(this.dataBroker, vpnInterfaceName);
        if (vpnInterface == null) {
            SubnetRoutePacketInHandler.LOG.error("Vpn interface {} doesn't exist.", (Object) vpnInterfaceName);
        }
        Uuid vpnInstanceNameUuid = new Uuid(vpnIdVpnInstanceName);
        if (vpnInterface.getVpnInstanceName().equals(vpnIdVpnInstanceName)) {
            this.handleInternalNetwork(dstIp, dstIpStr, destinationAddress,
                    elanTag);
        } else {
            this.handleExternalNetwork(vpnInstanceNameUuid, vpnInterface, dstIp, elanTag);
        }
    }

    private void transmitArpPacket(BigInteger dpnId, String sourceIpAddress, String sourceMac, byte[] dstIp,
            long elanTag) throws UnknownHostException {
        SubnetRoutePacketInHandler.LOG.info("Sending arp with elan tag {}", (Object) elanTag);
        VpnManagerCounters.subnet_route_packet_arp_sent.inc();
        long groupid = VpnUtil.getRemoteBCGroup(elanTag);
        TransmitPacketInput arpRequestInput = ArpUtils.createArpRequestInput(dpnId, groupid,
                HexEncode.bytesFromHexString(sourceMac), InetAddress.getByName(sourceIpAddress).getAddress(), dstIp);
        this.packetService.transmitPacket(arpRequestInput);
    }

    private void handleInternalNetwork(byte[] dstIp, String dstIpStr,
            int destinationAddress, long elanTag) throws UnknownHostException {

        SubnetOpDataEntry targetSubnetForPacketOut =
                getTargetSubnetForPacketOut(dataBroker, elanTag, destinationAddress);

        if (targetSubnetForPacketOut == null) {
            SubnetRoutePacketInHandler.LOG.debug("Couldn't find matching subnet for elan tag {} and destination ip {}",
                    elanTag, dstIpStr);
            VpnManagerCounters.subnet_route_packet_arp_failed.inc();
            return;
        }

        InstanceIdentifier<Subnetmap> id =
                (InstanceIdentifier<Subnetmap>) InstanceIdentifier.builder((Class) Subnetmaps.class)
                        .child((Class) Subnetmap.class, new SubnetmapKey(targetSubnetForPacketOut.getSubnetId())).build();
        Optional<Subnetmap> subnetMap = VpnUtil.read(this.dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (!subnetMap.isPresent()) {
            SubnetRoutePacketInHandler.LOG.debug("Couldn't find subnet map for subnet {}", targetSubnetForPacketOut.getSubnetId());
            VpnManagerCounters.subnet_route_packet_arp_failed.inc();
            return;
        }
        String sourceIp = ((Subnetmap) subnetMap.get()).getRouterInterfaceFixedIp();
        String sourceMac = ((Subnetmap) subnetMap.get()).getRouterIntfMacAddress();
        this.transmitArpPacket(targetSubnetForPacketOut.getNhDpnId(), sourceIp, sourceMac, dstIp, elanTag);
    }

    private void handleExternalNetwork(Uuid vpnInstanceNameUuid, VpnInterface vpnInterface, byte[] dstIp, long elanTag)
            throws UnknownHostException {
        String routerId = vpnInterface.getVpnInstanceName();
        Routers externalRouter = VpnUtil.getExternalRouter(this.dataBroker, routerId);
        if (externalRouter == null) {
            VpnManagerCounters.subnet_route_packet_arp_failed.inc();
            SubnetRoutePacketInHandler.LOG.debug("Can't find external router with id {}", (Object) routerId);
            return;
        }
        List<ExternalIps> externalIps = (List<ExternalIps>) externalRouter.getExternalIps();
        if (externalIps == null || externalIps.isEmpty()) {
            VpnManagerCounters.subnet_route_packet_arp_failed.inc();
            SubnetRoutePacketInHandler.LOG.debug("Router {} doesn't have any external ips.",
                    (Object) externalRouter.getRouterName());
            return;
        }
        java.util.Optional<ExternalIps> externalIp = (java.util.Optional<ExternalIps>) externalRouter.getExternalIps()
                .stream().filter(eip -> vpnInstanceNameUuid.equals((Object) eip.getSubnetId())).findFirst();
        if (!externalIp.isPresent()) {
            VpnManagerCounters.subnet_route_packet_arp_failed.inc();
            SubnetRoutePacketInHandler.LOG.debug("Router {} doesn't have an external ip for subnet id {}.",
                    (Object) externalRouter.getRouterName(), (Object) vpnInstanceNameUuid);
            return;
        }
        BigInteger dpnId = this.centralizedSwitchProvider.getPrimarySwitchForRouter(externalRouter.getRouterName());
        if (Objects.equals(dpnId, BigInteger.ZERO)) {
            VpnManagerCounters.subnet_route_packet_arp_failed.inc();
            SubnetRoutePacketInHandler.LOG.debug("Could not find primary switch for router {}.",
                    (Object) externalRouter.getRouterName());
            return;
        }
        this.transmitArpPacket(dpnId, externalIp.get().getIpAddress(), externalRouter.getExtGwMacAddress(), dstIp,
                elanTag);
    }

    private String getVpnInterfaceName(BigInteger metadata) throws InterruptedException, ExecutionException {
        GetInterfaceFromIfIndexInputBuilder ifIndexInputBuilder = new GetInterfaceFromIfIndexInputBuilder();
        BigInteger lportTag = MetaDataUtil.getLportFromMetadata(metadata);

        ifIndexInputBuilder.setIfIndex(lportTag.intValue());
        GetInterfaceFromIfIndexInput input = ifIndexInputBuilder.build();

        Future<RpcResult<GetInterfaceFromIfIndexOutput>> interfaceFromIfIndex =
                odlInterfaceRpcService.getInterfaceFromIfIndex(input);
        GetInterfaceFromIfIndexOutput interfaceFromIfIndexOutput;
        interfaceFromIfIndexOutput = interfaceFromIfIndex.get().getResult();
        String interfaceName = interfaceFromIfIndexOutput.getInterfaceName();
        return interfaceName;
    }

    private Optional<Uuid> getSubnetId(String vpnInterfaceName) {
        List<Adjacency> adjacencies = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, vpnInterfaceName);
        if (adjacencies != null) {
            java.util.Optional<Adjacency> primaryAdjacency =
                    adjacencies.stream().filter(f -> f.isPrimaryAdjacency()).findFirst();

            if (primaryAdjacency.isPresent()) {
                return Optional.fromNullable(primaryAdjacency.get().getSubnetId());
            }
        }

        return Optional.absent();
    }

    private static SubnetOpDataEntry getTargetSubnetForPacketOut(DataBroker broker, long elanTag, int ipAddress) {
        ElanTagName elanInfo = VpnUtil.getElanInfoByElanTag(broker, elanTag);
        if (elanInfo == null) {
            LOG.trace("SubnetRoutePacketInHandler: Unable to retrieve ElanInfo for elanTag {}", elanTag);
            return null;
        }
        InstanceIdentifier<NetworkMap> networkId = InstanceIdentifier.builder(NetworkMaps.class)
                .child(NetworkMap.class, new NetworkMapKey(new Uuid(elanInfo.getName()))).build();

        Optional<NetworkMap> optionalNetworkMap = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, networkId);
        if (optionalNetworkMap.isPresent()) {
            List<Uuid> subnetList = optionalNetworkMap.get().getSubnetIdList();
            LOG.trace("SubnetRoutePacketInHandler: Obtained subnetList as " + subnetList);
            for (Uuid subnetId : subnetList) {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class)
                        .child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs =
                        VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    continue;
                }
                SubnetOpDataEntry subOpEntry = optionalSubs.get();
                if (subOpEntry.getNhDpnId() != null) {
                    LOG.trace("SubnetRoutePacketInHandler: Viewing Subnet " + subnetId);
                    boolean match = NWUtil.isIpInSubnet(ipAddress, subOpEntry.getSubnetCidr());
                    LOG.trace("SubnetRoutePacketInHandler: Viewing Subnet " + subnetId + " matching " + match);
                    if (match) {
                        return subOpEntry;
                    }
                }
            }
        }

        return null;
    }

    public static long getElanTagFromSubnetRouteMetadata(BigInteger metadata) {
        return ((metadata.and(MetaDataUtil.METADATA_MASK_ELAN_SUBNET_ROUTE)).shiftRight(32)).longValue();
    }

    static InstanceIdentifier<VpnIds> getVpnIdToVpnInstanceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(VpnIdToVpnInstance.class).child(VpnIds.class, new VpnIdsKey(vpnId)).build();
    }
}
