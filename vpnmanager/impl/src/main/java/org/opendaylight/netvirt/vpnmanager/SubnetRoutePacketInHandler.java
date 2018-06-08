/*
 * Copyright © 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.openflowplugin.libraries.liblldp.HexEncode;
import org.opendaylight.openflowplugin.libraries.liblldp.NetUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.Packet;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
    private final VpnManagerCounters vpnManagerCounters;
    private final VpnUtil vpnUtil;

    @Inject
    public SubnetRoutePacketInHandler(final DataBroker dataBroker, final PacketProcessingService packetService,
            final OdlInterfaceRpcService odlInterfaceRpcService,
            final ICentralizedSwitchProvider centralizedSwitchProvider,
            final IInterfaceManager interfaceManager, VpnManagerCounters vpnManagerCounters, VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.packetService = packetService;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.centralizedSwitchProvider = centralizedSwitchProvider;
        this.interfaceManager = interfaceManager;
        this.vpnManagerCounters = vpnManagerCounters;
        this.vpnUtil = vpnUtil;
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
                vpnManagerCounters.subnetRoutePacketFailed();
                return;
            }
            try {
                Packet pkt = res.getPayload();
                if (pkt instanceof IPv4) {
                    IPv4 ipv4 = (IPv4) pkt;
                    byte[] srcIp = Ints.toByteArray(ipv4.getSourceAddress());
                    byte[] dstIp = Ints.toByteArray(ipv4.getDestinationAddress());
                    String dstIpStr = NWUtil.toStringIpAddress(dstIp);
                    String srcIpStr = NWUtil.toStringIpAddress(srcIp);
                    // It is an ARP request on a configured VPN. So we must
                    // attempt to respond.
                    long vpnId = MetaDataUtil.getVpnIdFromMetadata(metadata);

                    LOG.info("{} onPacketReceived: Processing IPv4 Packet received with Source IP {} and Target IP {}"
                            + " and vpnId {}", LOGGING_PREFIX, srcIpStr, dstIpStr, vpnId);

                    Optional<VpnIds> vpnIdsOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId));

                    if (!vpnIdsOptional.isPresent()) {
                        // Donot trigger subnetroute logic for packets from
                        // unknown VPNs
                        vpnManagerCounters.subnetRoutePacketIgnored();
                        LOG.info("{} onPacketReceived: Ignoring IPv4 packet with destination Ip {} and source Ip {}"
                                + " as it came on unknown VPN with ID {}", LOGGING_PREFIX, dstIpStr, srcIpStr, vpnId);
                        return;
                    }

                    String vpnIdVpnInstanceName = vpnIdsOptional.get().getVpnInstanceName();
                    if (vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnIdVpnInstanceName, dstIpStr) != null) {
                        vpnManagerCounters.subnetRoutePacketIgnored();
                        LOG.info("{} onPacketReceived: IPv4 Packet received with Target IP {} source IP {} vpnId {} "
                                + "is a valid Neutron port,ignoring subnet route processing", LOGGING_PREFIX, dstIpStr,
                                srcIp, vpnId);
                        return;
                    }

                    if (vpnUtil.getLearntVpnVipToPort(vpnIdVpnInstanceName, dstIpStr) != null) {
                        vpnManagerCounters.subnetRoutePacketIgnored();
                        LOG.info("{} onPacketReceived: IPv4 Packet received with Target IP {} source Ip {} vpnId {}"
                                + " is an already discovered IPAddress, ignoring subnet route processing",
                                LOGGING_PREFIX, dstIpStr, srcIp, vpnId);
                        return;
                    }

                    long elanTag = MetaDataUtil.getElanTagFromMetadata(metadata);
                    if (elanTag == 0L) {
                        vpnManagerCounters.subnetRoutePacketFailed();
                        LOG.error("{} onPacketReceived: elanTag value from metadata found to be 0, for IPv4 "
                                + " Packet received with Target IP {} src Ip {} vpnId {}",
                                LOGGING_PREFIX, dstIpStr, srcIp, vpnId);
                        return;
                    }

                    if (!vpnIdsOptional.get().isExternalVpn()) {
                        handleInternalVpnSubnetRoutePacket(metadata, dstIp, srcIpStr, dstIpStr,
                                ipv4.getDestinationAddress(), vpnIdVpnInstanceName, elanTag);
                        return;
                    }

                    byte[] srcMac = res.getSourceMACAddress();
                    handleBgpVpnSubnetRoute(ipv4, srcMac, dstIp, dstIpStr, srcIpStr, elanTag);
                }
            } catch (UnknownHostException | InterruptedException | ExecutionException ex) {
                // Failed to handle packet
                vpnManagerCounters.subnetRoutePacketFailed();
                LOG.error("{} onPacketReceived: Failed to handle subnetroute packet.", LOGGING_PREFIX, ex);
            } catch (ReadFailedException e) {
                vpnManagerCounters.subnetRoutePacketFailed();
                LOG.error("{} onPacketReceived: Failed to read data-store.", LOGGING_PREFIX, e);
            }
            return;
        }
        // All Arp responses learning for invisble IPs is handled by
        // ArpNotificationHandler

    }

    private void handleBgpVpnSubnetRoute(IPv4 ipv4, byte[] srcMac, byte[] dstIp, String dstIpStr, String srcIpStr,
            long elanTag) throws UnknownHostException {
        LOG.info("{} handleBgpVpnSubnetRoute: Processing IPv4 Packet received with Source IP {} and Target IP {}"
                + " and elan Tag {}", LOGGING_PREFIX, srcIpStr, dstIpStr, elanTag);
        SubnetOpDataEntry targetSubnetForPacketOut =
                getTargetSubnetForPacketOut(elanTag, ipv4.getDestinationAddress());

        if (targetSubnetForPacketOut != null) {
            // Handle subnet routes ip requests
            transmitArpPacket(targetSubnetForPacketOut.getNhDpnId(), srcIpStr, NWUtil.toStringMacAddress(srcMac), dstIp,
                    elanTag);
        } else {
            vpnManagerCounters.subnetRoutePacketFailed();
            LOG.debug("{} handleBgpVpnSubnetRoute: Could not find target subnet for packet out {}", LOGGING_PREFIX,
                    dstIpStr);
        }
    }

    private void handleInternalVpnSubnetRoutePacket(BigInteger metadata, byte[] dstIp, String srcIpStr, String dstIpStr,
            int destinationAddress, String vpnIdVpnInstanceName, long elanTag)
            throws InterruptedException, ExecutionException, UnknownHostException {
        String vmVpnInterfaceName = vpnUtil.getVpnInterfaceName(metadata);
        if (isTunnel(vmVpnInterfaceName)) {
            handlePacketFromTunnelToExternalNetwork(vpnIdVpnInstanceName,
                    srcIpStr, dstIp, elanTag);
        }
        VpnInterface vmVpnInterface = vpnUtil.getVpnInterface(vmVpnInterfaceName);
        if (vmVpnInterface == null) {
            LOG.error("Vpn interface {} doesn't exist.", vmVpnInterfaceName);
            vpnManagerCounters.subnetRoutePacketFailed();
            return;
        }
        if (VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnIdVpnInstanceName,
               vmVpnInterface.getVpnInstanceNames())
               && !vpnUtil.isBgpVpnInternet(vpnIdVpnInstanceName)) {
            LOG.trace("Unknown IP is in internal network");
            handlePacketToInternalNetwork(dstIp, dstIpStr, destinationAddress, elanTag);
        } else {
            LOG.trace("Unknown IP is in external network");
            String vpnName = vpnUtil.getInternetVpnFromVpnInstanceList(vmVpnInterface.getVpnInstanceNames());
            if (vpnName != null) {
                handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName),
                                   vpnName, dstIp, elanTag);
            } else {
                vpnName = VpnHelper.getFirstVpnNameFromVpnInterface(vmVpnInterface);
                LOG.trace("Unknown IP is in external network, but internet VPN not found."
                         + " fallback to first VPN");
                handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName),
                                   vpnName, dstIp, elanTag);

            }
        }
    }

    private void transmitArpPacket(BigInteger dpnId, String sourceIpAddress, String sourceMac, byte[] dstIp,
            long elanTag) throws UnknownHostException {
        LOG.debug("Sending arp with elan tag {}", elanTag);
        vpnManagerCounters.subnetRoutePacketArpSent();
        long groupid = VpnUtil.getRemoteBCGroup(elanTag);
        TransmitPacketInput arpRequestInput = ArpUtils.createArpRequestInput(dpnId, groupid,
                HexEncode.bytesFromHexString(sourceMac), InetAddress.getByName(sourceIpAddress).getAddress(), dstIp);

        JdkFutures.addErrorLogging(packetService.transmitPacket(arpRequestInput), LOG, "Transmit packet");
    }

    private void handlePacketToInternalNetwork(byte[] dstIp, String dstIpStr, int destinationAddress, long elanTag)
            throws UnknownHostException {
        try {
            SubnetOpDataEntry targetSubnetForPacketOut =
                    getTargetSubnetForPacketOut(elanTag, destinationAddress);

            if (targetSubnetForPacketOut == null) {
                LOG.debug("Couldn't find matching subnet for elan tag {} and destination ip {}", elanTag, dstIpStr);
                vpnManagerCounters.subnetRoutePacketFailed();
                return;
            }

            Optional<Subnetmap> subnetMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION,
                    VpnUtil.buildSubnetmapIdentifier(targetSubnetForPacketOut.getSubnetId()));
            if (!subnetMap.isPresent()) {
                LOG.debug("Couldn't find subnet map for subnet {}", targetSubnetForPacketOut.getSubnetId());
                vpnManagerCounters.subnetRoutePacketFailed();
                return;
            }

            String sourceIp = subnetMap.get().getRouterInterfaceFixedIp();
            if (sourceIp == null) {
                LOG.debug("Subnet map {} doesn't have a router interface ip defined", subnetMap.get().getId());
                vpnManagerCounters.subnetRoutePacketFailed();
                return;
            }

            String sourceMac = subnetMap.get().getRouterIntfMacAddress();
            if (sourceMac == null) {
                LOG.debug("Subnet map {} doesn't have a router interface mac address defined",
                        subnetMap.get().getId());
                vpnManagerCounters.subnetRoutePacketFailed();
                return;
            }

            transmitArpPacket(targetSubnetForPacketOut.getNhDpnId(), sourceIp, sourceMac, dstIp, elanTag);
        } catch (ReadFailedException e) {
            LOG.error("handlePacketToInternalNetwork: Failed to read data store for destIp {} elanTag {}", dstIpStr,
                    elanTag);
        }
    }

    private void handlePacketFromTunnelToExternalNetwork(String vpnIdVpnInstanceName,
                        String srcIpStr, byte[] dstIp, long elanTag)
                                throws UnknownHostException {
        String routerId = vpnUtil.getAssociatedExternalRouter(srcIpStr);
        if (null == routerId) {
            LOG.debug("This ip is not associated with any external router: {}", srcIpStr);
        }
        handlePacketToExternalNetwork(new Uuid(vpnIdVpnInstanceName), routerId, dstIp, elanTag);
    }

    private void handlePacketToExternalNetwork(Uuid vpnInstanceNameUuid, String routerId, byte[] dstIp,
            long elanTag) throws UnknownHostException {
        Routers externalRouter = vpnUtil.getExternalRouter(routerId);
        if (externalRouter == null) {
            vpnManagerCounters.subnetRoutePacketFailed();
            LOG.debug("{} handlePacketToExternalNetwork: Can't find external router with id {}", LOGGING_PREFIX,
                    routerId);
            return;
        }

        List<ExternalIps> externalIps = externalRouter.getExternalIps();
        if (externalIps == null || externalIps.isEmpty()) {
            vpnManagerCounters.subnetRoutePacketFailed();
            LOG.debug("{} handlePacketToExternalNetwork: Router {} doesn't have any external ips.",
                    LOGGING_PREFIX, externalRouter.getRouterName());
            return;
        }

        java.util.Optional<ExternalIps> externalIp = externalRouter.getExternalIps().stream()
                .filter(eip -> vpnInstanceNameUuid.equals(eip.getSubnetId())).findFirst();
        if (!externalIp.isPresent()) {
            vpnManagerCounters.subnetRoutePacketFailed();
            LOG.debug("{} handlePacketToExternalNetwork: Router {} doesn't have an external ip for subnet id {}.",
                    LOGGING_PREFIX, externalRouter.getRouterName(), vpnInstanceNameUuid);
            return;
        }

        BigInteger dpnId = centralizedSwitchProvider.getPrimarySwitchForRouter(externalRouter.getRouterName());
        if (BigInteger.ZERO.equals(dpnId)) {
            vpnManagerCounters.subnetRoutePacketFailed();
            LOG.debug("{} handlePacketToExternalNetwork: Could not find primary switch for router {}.",
                    LOGGING_PREFIX, externalRouter.getRouterName());
            return;
        }

        transmitArpPacket(dpnId, externalIp.get().getIpAddress(), externalRouter.getExtGwMacAddress(), dstIp, elanTag);
    }

    // return only the first VPN subnetopdataentry
    private SubnetOpDataEntry getTargetSubnetForPacketOut(long elanTag, int ipAddress) {
        ElanTagName elanInfo = vpnUtil.getElanInfoByElanTag(elanTag);
        if (elanInfo == null) {
            LOG.error("{} getTargetDpnForPacketOut: Unable to retrieve ElanInfo for elanTag {}", LOGGING_PREFIX,
                    elanTag);
            return null;
        }
        try {
            Optional<NetworkMap> optionalNetworkMap = SingleTransactionDataBroker.syncReadOptional(dataBroker,
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
                Subnetmap sn = vpnUtil.getSubnetmapFromItsUuid(subnetId);
                if (sn != null && sn.getVpnId() != null) {
                    vpnName = sn.getVpnId().getValue();
                }
                if (vpnName == null) {
                    continue;
                }
                Optional<SubnetOpDataEntry> optionalSubs;
                optionalSubs = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, VpnUtil.buildSubnetOpDataEntryInstanceIdentifier(subnetId));
                if (!optionalSubs.isPresent()) {
                    continue;
                }
                SubnetOpDataEntry subOpEntry = optionalSubs.get();
                if (subOpEntry.getNhDpnId() != null) {
                    LOG.trace("{} getTargetDpnForPacketOut: Viewing Subnet {}", LOGGING_PREFIX, subnetId.getValue());
                    boolean match = NWUtil.isIpInSubnet(ipAddress, subOpEntry.getSubnetCidr());
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
        return configIface.augmentation(IfTunnel.class) != null;
    }
}
