/*
 * Copyright © 2015, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IEEE8021Q;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.genius.mdsalutil.packet.UDP;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.dhcpservice.api.DHCP;
import org.opendaylight.netvirt.dhcpservice.api.DHCPConstants;
import org.opendaylight.netvirt.dhcpservice.api.DHCPUtils;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.openflowplugin.libraries.liblldp.EtherTypes;
import org.opendaylight.openflowplugin.libraries.liblldp.NetUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.network.AllocationPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.HostRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class DhcpPktHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpPktHandler.class);

    private final DhcpManager dhcpMgr;
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final PacketProcessingService pktService;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final IInterfaceManager interfaceManager;
    private final DhcpserviceConfig config;
    private final DhcpAllocationPoolManager dhcpAllocationPoolMgr;
    private final DataBroker broker;

    @Inject
    public DhcpPktHandler(final DhcpManager dhcpManager,
                          final DhcpExternalTunnelManager dhcpExternalTunnelManager,
                          final OdlInterfaceRpcService interfaceManagerRpc,
                          final PacketProcessingService pktService,
                          final IInterfaceManager interfaceManager,
                          final DhcpserviceConfig config,
                          final DhcpAllocationPoolManager dhcpAllocationPoolMgr,
                          final DataBroker dataBroker) {
        this.interfaceManagerRpc = interfaceManagerRpc;
        this.pktService = pktService;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dhcpMgr = dhcpManager;
        this.interfaceManager = interfaceManager;
        this.config = config;
        this.dhcpAllocationPoolMgr = dhcpAllocationPoolMgr;
        this.broker = dataBroker;
    }

    //TODO: Handle this in a separate thread
    @Override
    public void onPacketReceived(PacketReceived packet) {
        if (!config.isControllerDhcpEnabled()) {
            return;
        }
        Class<? extends PacketInReason> pktInReason = packet.getPacketInReason();
        short tableId = packet.getTableId().getValue();
        if ((tableId == NwConstants.DHCP_TABLE || tableId == NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL)
                && isPktInReasonSendtoCtrl(pktInReason)) {
            byte[] inPayload = packet.getPayload();
            Ethernet ethPkt = new Ethernet();
            try {
                ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NUM_BITS_IN_A_BYTE);
            } catch (PacketException e) {
                LOG.warn("Failed to decode DHCP Packet.", e);
                LOG.trace("Received packet {}", packet);
                return;
            }
            DHCP pktIn;
            pktIn = getDhcpPktIn(ethPkt);
            if (pktIn != null) {
                LOG.trace("DHCPPkt received: {}", pktIn);
                LOG.trace("Received Packet: {}", packet);
                BigInteger metadata = packet.getMatch().getMetadata().getMetadata();
                long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
                String macAddress = DHCPUtils.byteArrayToString(ethPkt.getSourceMACAddress());
                BigInteger tunnelId =
                        packet.getMatch().getTunnel() == null ? null : packet.getMatch().getTunnel().getTunnelId();
                String interfaceName = getInterfaceNameFromTag(portTag);
                InterfaceInfo interfaceInfo =
                        interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName);
                if (interfaceInfo == null) {
                    LOG.error("Failed to get interface info for interface name {}", interfaceName);
                    return;
                }
                Port port;
                if (tunnelId != null) {
                    port = dhcpExternalTunnelManager.readVniMacToPortCache(tunnelId, macAddress);
                } else {
                    port = getNeutronPort(interfaceName);
                }
                Subnet subnet = getNeutronSubnet(port);
                String serverMacAddress = interfaceInfo.getMacAddress();
                String serverIp = null;
                if (subnet != null) {
                    java.util.Optional<SubnetToDhcpPort> dhcpPortData = DhcpServiceUtils
                            .getSubnetDhcpPortData(broker, subnet.getUuid().getValue());
                    /* If enable_dhcp_service flag was enabled and an ODL network DHCP Port data was made available use
                     * the ports Fixed IP as server IP for DHCP communication.
                     */
                    if (dhcpPortData.isPresent()) {
                        serverIp = dhcpPortData.get().getPortFixedip();
                        serverMacAddress = dhcpPortData.get().getPortMacaddress();
                    } else {
                        // DHCP Neutron Port not found for this network
                        LOG.error("Neutron DHCP port is not available for the Subnet {} and port {}.", subnet.getUuid(),
                                port.getUuid());
                        return;
                    }
                }
                DHCP replyPkt = handleDhcpPacket(pktIn, interfaceName, macAddress, port, subnet, serverIp);
                if (replyPkt == null) {
                    LOG.warn("Unable to construct reply packet for interface name {}", interfaceName);
                    return;
                }
                byte[] pktOut = getDhcpPacketOut(replyPkt, ethPkt, serverMacAddress);
                sendPacketOut(pktOut, interfaceInfo.getDpId(), interfaceName, tunnelId);
            }
        }
    }

    private void sendPacketOut(byte[] pktOut, BigInteger dpnId, String interfaceName, BigInteger tunnelId) {
        List<Action> action = getEgressAction(interfaceName, tunnelId);
        TransmitPacketInput output = MDSALUtil.getPacketOut(action, pktOut, dpnId);
        LOG.trace("Transmitting packet: {}", output);
        JdkFutures.addErrorLogging(pktService.transmitPacket(output), LOG, "Transmit packet");
    }

    private DHCP handleDhcpPacket(DHCP dhcpPkt, String interfaceName, String macAddress, Port interfacePort,
                                  Subnet subnet, String serverIp) {
        LOG.trace("DHCP pkt rcvd {}", dhcpPkt);
        byte msgType = dhcpPkt.getMsgType();
        DhcpInfo dhcpInfo = null;
        if (interfacePort != null) {
            dhcpInfo = handleDhcpNeutronPacket(msgType, interfacePort, subnet, serverIp);
        } else if (config.isDhcpDynamicAllocationPoolEnabled()) {
            dhcpInfo = handleDhcpAllocationPoolPacket(msgType, interfaceName, macAddress);
        }
        DHCP reply = null;
        if (dhcpInfo != null) {
            if (msgType == DHCPConstants.MSG_DISCOVER) {
                reply = getReplyToDiscover(dhcpPkt, dhcpInfo);
            } else if (msgType == DHCPConstants.MSG_REQUEST) {
                reply = getReplyToRequest(dhcpPkt, dhcpInfo);
            }
        }

        return reply;
    }

    private DhcpInfo handleDhcpNeutronPacket(byte msgType, Port port, Subnet subnet, String serverIp) {
        if (msgType == DHCPConstants.MSG_DECLINE) {
            LOG.trace("DHCPDECLINE received");
            return null;
        } else if (msgType == DHCPConstants.MSG_RELEASE) {
            LOG.trace("DHCPRELEASE received");
            return null;
        }
        return getDhcpInfoFromNeutronPort(port, subnet, serverIp);
    }


    private DhcpInfo handleDhcpAllocationPoolPacket(byte msgType, String interfaceName, String macAddress) {
        try {
            String networkId = dhcpAllocationPoolMgr.getNetworkByPort(interfaceName);
            AllocationPool pool = networkId != null ? dhcpAllocationPoolMgr.getAllocationPoolByNetwork(networkId)
                    : null;
            if (networkId == null || pool == null) {
                LOG.warn("No Dhcp Allocation Pool was found for interface: {}", interfaceName);
                return null;
            }
            switch (msgType) {
                case DHCPConstants.MSG_DISCOVER:
                case DHCPConstants.MSG_REQUEST:
                    // FIXME: requested ip is currently ignored in moment of allocation
                    return getDhcpInfoFromAllocationPool(networkId, pool, macAddress);
                case DHCPConstants.MSG_RELEASE:
                    dhcpAllocationPoolMgr.releaseIpAllocation(networkId, pool, macAddress);
                    break;
                default:
                    break;
            }
        } catch (ReadFailedException e) {
            LOG.error("Error reading from MD-SAL", e);
        }
        return null;
    }

    private DhcpInfo getDhcpInfoFromNeutronPort(Port port, Subnet subnet, String serverIp) {
        DhcpInfo dhcpInfo = getDhcpInfo(port, subnet, serverIp);
        LOG.trace("NeutronPort: {} \n NeutronSubnet: {}, dhcpInfo{}", port, subnet, dhcpInfo);
        return dhcpInfo;
    }

    private DhcpInfo getDhcpInfoFromAllocationPool(String networkId, AllocationPool pool, String macAddress) {
        IpAddress allocatedIp = dhcpAllocationPoolMgr.getIpAllocation(networkId, pool, macAddress);
        DhcpInfo dhcpInfo = getApDhcpInfo(pool, allocatedIp);
        LOG.info("AllocationPoolNetwork: {}, dhcpInfo {}", networkId, dhcpInfo);
        return dhcpInfo;
    }

    private DhcpInfo getDhcpInfo(Port port, Subnet subnet, String serverIp) {
        DhcpInfo dhcpInfo = null;
        if (port != null && subnet != null) {
            String clientIp = getIpv4Address(port);
            List<IpAddress> dnsServers = subnet.getDnsNameservers();
            dhcpInfo = new DhcpInfo();
            if (isIpv4Address(subnet.getGatewayIp())) {
                dhcpInfo.setGatewayIp(subnet.getGatewayIp().getIpv4Address().getValue());
            }
            if (clientIp != null && serverIp != null) {
                List<HostRoutes> subnetHostRoutes = new ArrayList<>(subnet.getHostRoutes().size());
                for (HostRoutes hostRoute : subnet.getHostRoutes()) {
                    if (!String.valueOf(hostRoute.getNexthop().getValue()).equals(clientIp)) {
                        subnetHostRoutes.add(hostRoute);
                    }
                }
                dhcpInfo.setClientIp(clientIp).setServerIp(serverIp)
                        .setCidr(String.valueOf(subnet.getCidr().getValue())).setHostRoutes(subnetHostRoutes)
                        .setDnsServersIpAddrs(dnsServers);
            }
        }
        return dhcpInfo;
    }

    private DhcpInfo getApDhcpInfo(AllocationPool ap, IpAddress allocatedIp) {
        DhcpInfo dhcpInfo = null;

        String clientIp = String.valueOf(allocatedIp.getValue());
        String serverIp = String.valueOf(ap.getGateway().getValue());
        List<IpAddress> dnsServers = ap.getDnsServers();
        dhcpInfo = new DhcpInfo();
        dhcpInfo.setClientIp(clientIp).setServerIp(serverIp).setCidr(String.valueOf(ap.getSubnet().getValue()))
            .setHostRoutes(Collections.emptyList()).setDnsServersIpAddrs(dnsServers).setGatewayIp(serverIp);

        return dhcpInfo;
    }

    /* TODO:
     * getIpv4Address and isIpv4Address
     * Many other modules use/need similar methods. Should
     * be refactored to a common NeutronUtils module.     *
     */
    private String getIpv4Address(Port port) {

        for (FixedIps fixedIp : port.getFixedIps()) {
            if (isIpv4Address(fixedIp.getIpAddress())) {
                return fixedIp.getIpAddress().getIpv4Address().getValue();
            }
        }
        LOG.error("Could not find ipv4 address for port {}", port);
        return null;
    }

    private boolean isIpv4Address(IpAddress ip) {
        return ip != null && ip.getIpv4Address() != null;
    }

    private Subnet getNeutronSubnet(Port port) {
        return dhcpMgr.getNeutronSubnet(port);
    }

    private Port getNeutronPort(String interfaceName) {
        return dhcpMgr.getNeutronPort(interfaceName);
    }

    private DHCP getDhcpPktIn(Ethernet actualEthernetPacket) {
        Ethernet ethPkt = actualEthernetPacket;
        if (ethPkt.getEtherType() == (short)NwConstants.ETHTYPE_802_1Q) {
            ethPkt = (Ethernet)ethPkt.getPayload();
        }
        // Currently only IPv4 is supported
        if (ethPkt.getPayload() instanceof IPv4) {
            IPv4 ipPkt = (IPv4) ethPkt.getPayload();
            if (ipPkt.getPayload() instanceof UDP) {
                UDP udpPkt = (UDP) ipPkt.getPayload();
                if (udpPkt.getSourcePort() == DhcpMConstants.DHCP_CLIENT_PORT
                        && udpPkt.getDestinationPort() == DhcpMConstants.DHCP_SERVER_PORT) {
                    LOG.trace("Matched DHCP_CLIENT_PORT and DHCP_SERVER_PORT");
                    byte[] rawDhcpPayload = udpPkt.getRawPayload();
                    DHCP reply = new DHCP();
                    try {
                        reply.deserialize(rawDhcpPayload, 0, rawDhcpPayload.length);
                    } catch (PacketException e) {
                        LOG.warn("Failed to deserialize DHCP pkt");
                        LOG.trace("Reason for failure", e);
                        return null;
                    }
                    return reply;
                }
            }
        }
        return null;
    }

    DHCP getReplyToDiscover(DHCP dhcpPkt, DhcpInfo dhcpInfo) {
        DHCP reply = new DHCP();
        reply.setOp(DHCPConstants.BOOTREPLY);
        reply.setHtype(dhcpPkt.getHtype());
        reply.setHlen(dhcpPkt.getHlen());
        reply.setHops((byte) 0);
        reply.setXid(dhcpPkt.getXid());
        reply.setSecs((short) 0);

        reply.setYiaddr(dhcpInfo.getClientIp());
        reply.setSiaddr(dhcpInfo.getServerIp());

        reply.setFlags(dhcpPkt.getFlags());
        reply.setGiaddr(dhcpPkt.getGiaddr());
        reply.setChaddr(dhcpPkt.getChaddr());

        reply.setMsgType(DHCPConstants.MSG_OFFER);
        if (dhcpPkt.containsOption(DHCPConstants.OPT_PARAMETER_REQUEST_LIST)) {
            setParameterListOptions(dhcpPkt, reply, dhcpInfo);
        }
        setCommonOptions(reply, dhcpInfo);
        return reply;
    }

    DHCP getReplyToRequest(DHCP dhcpPkt, DhcpInfo dhcpInfo) {
        boolean sendAck = false;
        byte[] requestedIp = null;
        DHCP reply = new DHCP();
        reply.setOp(DHCPConstants.BOOTREPLY);
        reply.setHtype(dhcpPkt.getHtype());
        reply.setHlen(dhcpPkt.getHlen());
        reply.setHops((byte) 0);
        reply.setXid(dhcpPkt.getXid());
        reply.setSecs((short) 0);

        reply.setFlags(dhcpPkt.getFlags());
        reply.setGiaddr(dhcpPkt.getGiaddr());
        reply.setChaddr(dhcpPkt.getChaddr());
        byte[] allocatedIp;
        try {
            allocatedIp = DHCPUtils.strAddrToByteArray(dhcpInfo.getClientIp());
        } catch (UnknownHostException e) {
            LOG.debug("strAddrToByteArray", e);
            allocatedIp = null;
        }

        if (Arrays.equals(allocatedIp, dhcpPkt.getCiaddr())) {
            //This means a renew request
            sendAck = true;
        } else {
            requestedIp = dhcpPkt.getOptionBytes(DHCPConstants.OPT_REQUESTED_ADDRESS);
            sendAck = Arrays.equals(allocatedIp, requestedIp);
        }

        if (sendAck) {
            reply.setCiaddr(dhcpPkt.getCiaddr());
            reply.setYiaddr(dhcpInfo.getClientIp());
            reply.setSiaddr(dhcpInfo.getServerIp());
            reply.setMsgType(DHCPConstants.MSG_ACK);
            if (dhcpPkt.containsOption(DHCPConstants.OPT_PARAMETER_REQUEST_LIST)) {
                setParameterListOptions(dhcpPkt, reply, dhcpInfo);
            }
        } else {
            reply.setMsgType(DHCPConstants.MSG_NAK);
        }
        setCommonOptions(reply, dhcpInfo);
        return reply;
    }

    // "Consider returning a zero length array rather than null" - the eventual user of the returned byte[] likely
    // expects null and it's unclear what the behavior would be if empty array was returned.
    @SuppressFBWarnings("PZLA_PREFER_ZERO_LENGTH_ARRAYS")
    protected byte[] getDhcpPacketOut(DHCP reply, Ethernet etherPkt, String phyAddrees) {
        if (reply == null) {
            /*
             * DECLINE or RELEASE don't result in reply packet
             */
            return null;
        }
        LOG.trace("Sending DHCP Pkt {}", reply);
        InetAddress serverIp = reply.getOptionInetAddr(DHCPConstants.OPT_SERVER_IDENTIFIER);
        // create UDP pkt
        UDP udpPkt = new UDP();
        byte[] rawPkt;
        try {
            rawPkt = reply.serialize();
        } catch (PacketException e) {
            LOG.warn("Failed to serialize packet", e);
            return null;
        }
        udpPkt.setRawPayload(rawPkt);
        udpPkt.setDestinationPort(DhcpMConstants.DHCP_CLIENT_PORT);
        udpPkt.setSourcePort(DhcpMConstants.DHCP_SERVER_PORT);
        udpPkt.setLength((short) (rawPkt.length + 8));
        //Create IP Pkt
        try {
            rawPkt = udpPkt.serialize();
        } catch (PacketException e) {
            LOG.warn("Failed to serialize packet", e);
            return null;
        }
        short checkSum = 0;
        boolean computeUdpChecksum = true;
        if (computeUdpChecksum) {
            checkSum = computeChecksum(rawPkt, serverIp.getAddress(),
                    NetUtils.intToByteArray4(DhcpMConstants.BCAST_IP));
        }
        udpPkt.setChecksum(checkSum);
        IPv4 ip4Reply = new IPv4();
        ip4Reply.setPayload(udpPkt);
        ip4Reply.setProtocol(IPProtocols.UDP.byteValue());
        ip4Reply.setSourceAddress(serverIp);
        ip4Reply.setDestinationAddress(DhcpMConstants.BCAST_IP);
        ip4Reply.setTotalLength((short) (rawPkt.length + 20));
        ip4Reply.setTtl((byte) 32);
        // create Ethernet Frame
        Ethernet ether = new Ethernet();
        if (etherPkt.getEtherType() == (short)NwConstants.ETHTYPE_802_1Q) {
            IEEE8021Q vlanPacket = (IEEE8021Q) etherPkt.getPayload();
            IEEE8021Q vlanTagged = new IEEE8021Q();
            vlanTagged.setCFI(vlanPacket.getCfi());
            vlanTagged.setPriority(vlanPacket.getPriority());
            vlanTagged.setVlanId(vlanPacket.getVlanId());
            vlanTagged.setPayload(ip4Reply);
            vlanTagged.setEtherType(EtherTypes.IPv4.shortValue());
            ether.setPayload(vlanTagged);
            ether.setEtherType((short) NwConstants.ETHTYPE_802_1Q);
        } else {
            ether.setEtherType(EtherTypes.IPv4.shortValue());
            ether.setPayload(ip4Reply);
        }
        ether.setSourceMACAddress(getServerMacAddress(phyAddrees));
        ether.setDestinationMACAddress(etherPkt.getSourceMACAddress());

        try {
            rawPkt = ether.serialize();
        } catch (PacketException e) {
            LOG.warn("Failed to serialize ethernet reply",e);
            return null;
        }
        return rawPkt;
    }

    private byte[] getServerMacAddress(String phyAddress) {
        // Should we return ControllerMac instead?
        return DHCPUtils.strMacAddrtoByteArray(phyAddress);
    }

    public short computeChecksum(byte[] inData, byte[] srcAddr, byte[] destAddr) {
        int sum = 0;
        int carry = 0;
        int wordData;
        int index;

        for (index = 0; index < inData.length - 1; index = index + 2) {
            // Skip, if the current bytes are checkSum bytes
            wordData = (inData[index] << 8 & 0xFF00) + (inData[index + 1] & 0xFF);
            sum = sum + wordData;
        }

        if (index < inData.length) {
            wordData = (inData[index] << 8 & 0xFF00) + (0 & 0xFF);
            sum = sum + wordData;
        }

        for (index = 0; index < 4; index = index + 2) {
            wordData = (srcAddr[index] << 8 & 0xFF00) + (srcAddr[index + 1] & 0xFF);
            sum = sum + wordData;
        }

        for (index = 0; index < 4; index = index + 2) {
            wordData = (destAddr[index] << 8 & 0xFF00) + (destAddr[index + 1] & 0xFF);
            sum = sum + wordData;
        }
        sum = sum + 17 + inData.length;

        while (sum >> 16 != 0) {
            carry = sum >> 16;
            sum = (sum & 0xFFFF) + carry;
        }
        short checkSum = (short) ~((short) sum & 0xFFFF);
        if (checkSum == 0) {
            checkSum = (short)0xffff;
        }
        return checkSum;
    }

    private void setCommonOptions(DHCP pkt, DhcpInfo dhcpInfo) {
        String serverIp = dhcpInfo.getServerIp();
        if (pkt.getMsgType() != DHCPConstants.MSG_NAK) {
            setNonNakOptions(pkt, dhcpInfo);
        }
        try {
            /*
             * setParameterListOptions may have initialized some of these
             * options to maintain order. If we can't fill them, unset to avoid
             * sending wrong information in reply.
             */
            if (serverIp != null) {
                pkt.setOptionInetAddr(DHCPConstants.OPT_SERVER_IDENTIFIER, serverIp);
            } else {
                pkt.unsetOption(DHCPConstants.OPT_SERVER_IDENTIFIER);
            }
        } catch (UnknownHostException e) {
            LOG.warn("Failed to set option", e);
        }
    }

    private void setNonNakOptions(DHCP pkt, DhcpInfo dhcpInfo) {
        pkt.setOptionInt(DHCPConstants.OPT_LEASE_TIME, dhcpMgr.getDhcpLeaseTime());
        if (dhcpMgr.getDhcpDefDomain() != null) {
            pkt.setOptionString(DHCPConstants.OPT_DOMAIN_NAME, dhcpMgr.getDhcpDefDomain());
        }
        if (dhcpMgr.getDhcpLeaseTime() > 0) {
            pkt.setOptionInt(DHCPConstants.OPT_REBINDING_TIME, dhcpMgr.getDhcpRebindingTime());
            pkt.setOptionInt(DHCPConstants.OPT_RENEWAL_TIME, dhcpMgr.getDhcpRenewalTime());
        }
        SubnetUtils util = null;
        SubnetInfo info = null;
        util = new SubnetUtils(dhcpInfo.getCidr());
        info = util.getInfo();
        String gwIp = dhcpInfo.getGatewayIp();
        List<String> dnServers = dhcpInfo.getDnsServers();
        try {
            /*
             * setParameterListOptions may have initialized some of these
             * options to maintain order. If we can't fill them, unset to avoid
             * sending wrong information in reply.
             */
            if (gwIp != null) {
                pkt.setOptionInetAddr(DHCPConstants.OPT_ROUTERS, gwIp);
            } else {
                pkt.unsetOption(DHCPConstants.OPT_ROUTERS);
            }
            if (info != null) {
                pkt.setOptionInetAddr(DHCPConstants.OPT_SUBNET_MASK, info.getNetmask());
                pkt.setOptionInetAddr(DHCPConstants.OPT_BROADCAST_ADDRESS, info.getBroadcastAddress());
            } else {
                pkt.unsetOption(DHCPConstants.OPT_SUBNET_MASK);
                pkt.unsetOption(DHCPConstants.OPT_BROADCAST_ADDRESS);
            }
            if (dnServers != null && dnServers.size() > 0) {
                pkt.setOptionStrAddrs(DHCPConstants.OPT_DOMAIN_NAME_SERVERS, dnServers);
            } else {
                pkt.unsetOption(DHCPConstants.OPT_DOMAIN_NAME_SERVERS);
            }
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            LOG.warn("Failed to set option", e);
        }
    }

    private void setParameterListOptions(DHCP req, DHCP reply, DhcpInfo dhcpInfo) {
        byte[] paramList = req.getOptionBytes(DHCPConstants.OPT_PARAMETER_REQUEST_LIST);
        for (byte element : paramList) {
            switch (element) {
                case DHCPConstants.OPT_SUBNET_MASK:
                case DHCPConstants.OPT_ROUTERS:
                case DHCPConstants.OPT_SERVER_IDENTIFIER:
                case DHCPConstants.OPT_DOMAIN_NAME_SERVERS:
                case DHCPConstants.OPT_BROADCAST_ADDRESS:
                case DHCPConstants.OPT_LEASE_TIME:
                case DHCPConstants.OPT_RENEWAL_TIME:
                case DHCPConstants.OPT_REBINDING_TIME:
                    /* These values will be filled in setCommonOptions
                     * Setting these just to preserve order as
                     * specified in PARAMETER_REQUEST_LIST.
                     */
                    reply.setOptionInt(element, 0);
                    break;
                case DHCPConstants.OPT_DOMAIN_NAME:
                    reply.setOptionString(element, " ");
                    break;
                case DHCPConstants.OPT_CLASSLESS_ROUTE:
                    setOptionClasslessRoute(reply, dhcpInfo);
                    break;
                default:
                    LOG.trace("DHCP Option code {} not supported yet", element);
                    break;
            }
        }
    }

    private void setOptionClasslessRoute(DHCP reply, DhcpInfo dhcpInfo) {
        List<HostRoutes> hostRoutes = dhcpInfo.getHostRoutes();
        if (hostRoutes == null) {
            //we can't set this option, so return
            return;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (HostRoutes hostRoute : hostRoutes) {
            if (hostRoute.getNexthop().getIpv4Address() == null
                    || hostRoute.getDestination().getIpv4Prefix() == null) {
                // we only deal with IPv4 addresses
                return;
            }
            String router = hostRoute.getNexthop().getIpv4Address().getValue();
            String dest = hostRoute.getDestination().getIpv4Prefix().getValue();
            try {
                result.write(convertToClasslessRouteOption(dest, router));
            } catch (IOException | NullPointerException e) {
                LOG.trace("Exception {}", e.getMessage());
            }
        }
        if (result.size() > 0) {
            reply.setOptionBytes(DHCPConstants.OPT_CLASSLESS_ROUTE , result.toByteArray());
        }
    }

    @Nonnull
    protected byte[] convertToClasslessRouteOption(String dest, String router) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        if (dest == null || router == null) {
            return new byte[0];
        }

        //get prefix
        Short prefix = null;
        String[] parts = dest.split("/");
        if (parts.length < 2) {
            prefix = (short) 0;
        } else {
            prefix = Short.valueOf(parts[1]);
        }

        byteArray.write(prefix.byteValue());
        SubnetUtils util = new SubnetUtils(dest);
        SubnetInfo info = util.getInfo();
        String strNetAddr = info.getNetworkAddress();
        try {
            byte[] netAddr = InetAddress.getByName(strNetAddr).getAddress();
          //Strip any trailing 0s from netAddr
            for (int i = 0; i < netAddr.length;i++) {
                if (netAddr[i] != 0) {
                    byteArray.write(netAddr,i,1);
                }
            }
            byteArray.write(InetAddress.getByName(router).getAddress());
        } catch (IOException e) {
            return new byte[0];
        }
        return byteArray.toByteArray();
    }

    private boolean isPktInReasonSendtoCtrl(Class<? extends PacketInReason> pktInReason) {
        return pktInReason == SendToController.class;
    }

    private String getInterfaceNameFromTag(long portTag) {
        String interfaceName = null;
        GetInterfaceFromIfIndexInput input =
                new GetInterfaceFromIfIndexInputBuilder().setIfIndex((int) portTag).build();
        Future<RpcResult<GetInterfaceFromIfIndexOutput>> futureOutput =
                interfaceManagerRpc.getInterfaceFromIfIndex(input);
        try {
            GetInterfaceFromIfIndexOutput output = futureOutput.get().getResult();
            interfaceName = output.getInterfaceName();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error while retrieving the interfaceName from tag using getInterfaceFromIfIndex RPC");
        }
        LOG.trace("Returning interfaceName {} for tag {} form getInterfaceNameFromTag", interfaceName, portTag);
        return interfaceName;
    }

    private List<Action> getEgressAction(String interfaceName, BigInteger tunnelId) {
        List<Action> actions = null;
        try {
            GetEgressActionsForInterfaceInputBuilder egressAction =
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(interfaceName);
            if (tunnelId != null) {
                egressAction.setTunnelKey(tunnelId.longValue());
            }
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    interfaceManagerRpc.getEgressActionsForInterface(egressAction.build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}",
                        interfaceName, rpcResult.getErrors());
            } else {
                actions = rpcResult.getResult().getAction();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", interfaceName, e);
        }
        return actions;
    }
}
