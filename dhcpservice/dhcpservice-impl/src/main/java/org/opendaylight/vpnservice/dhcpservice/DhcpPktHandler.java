/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.controller.liblldp.HexEncode;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.dhcpservice.api.DHCP;
import org.opendaylight.vpnservice.dhcpservice.api.DHCPConstants;
import org.opendaylight.vpnservice.dhcpservice.api.DHCPMConstants;
import org.opendaylight.vpnservice.dhcpservice.api.DHCPUtils;
import org.opendaylight.vpnservice.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.vpnservice.mdsalutil.packet.Ethernet;
import org.opendaylight.vpnservice.mdsalutil.packet.IPProtocols;
import org.opendaylight.vpnservice.mdsalutil.packet.IPv4;
import org.opendaylight.vpnservice.mdsalutil.packet.UDP;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.HostRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.base.Optional;

public class DhcpPktHandler implements AutoCloseable, PacketProcessingListener {
    
    private static final Logger LOG = LoggerFactory.getLogger(DhcpPktHandler.class);
    private final DataBroker dataBroker;
    private final DhcpManager dhcpMgr;

    private boolean computeUdpChecksum = true;
    private PacketProcessingService pktService;

    public DhcpPktHandler(final DataBroker broker, final DhcpManager dhcpManager) {
        this.dataBroker = broker;
        dhcpMgr = dhcpManager;
    }

    //TODO: Handle this in a separate thread
    @Override
    public void onPacketReceived(PacketReceived packet) {
        LOG.trace("Pkt received: {}", packet);
        Class<? extends PacketInReason> pktInReason = packet.getPacketInReason();
        short tableId = packet.getTableId().getValue();
        if (isPktInReasonSendtoCtrl(pktInReason) && ((DHCPMConstants.DHCP_TABLE == tableId))) {
            byte[] inPayload = packet.getPayload();
            Ethernet ethPkt = new Ethernet();
            try {
                ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NumBitsInAByte);
            } catch (Exception e) {
                LOG.warn("Failed to decode DHCP Packet", e);
                return;
            }
            try {
                DHCP pktIn = getDhcpPktIn(ethPkt);
                LOG.trace("DHCPPkt received: {}", pktIn);
                if (pktIn != null) {
                    NodeConnectorRef inNcRef = packet.getIngress();
                    FlowCapableNodeConnector fcNc = this.getFlowCapableNodeConnector(inNcRef);
                    DHCP replyPkt = handleDhcpPacket(pktIn, fcNc);
                    byte[] pktOut = getDhcpPacketOut(replyPkt, ethPkt, fcNc);
                    sendPacketOut(pktOut, inNcRef);
                }
            } catch (Exception e) {
                LOG.warn("Failed to get DHCP Reply", e);
            }
        }
    }

    private void sendPacketOut(byte[] pktOut, NodeConnectorRef ingress) {
        // We go out the same port we came in on
        InstanceIdentifier<Node> egressNodePath = getNodePath(ingress.getValue());
        TransmitPacketInput input = new TransmitPacketInputBuilder()
            .setPayload(pktOut).setNode(new NodeRef(egressNodePath))
            .setEgress(ingress).build();
        LOG.trace("Transmitting packet: {}",input);
        this.pktService.transmitPacket(input);
    }

    private InstanceIdentifier<Node> getNodePath(InstanceIdentifier<?> nodeInstanceId) {
        return nodeInstanceId.firstIdentifierOf(Node.class);
    }

    private DHCP handleDhcpPacket(DHCP dhcpPkt, FlowCapableNodeConnector fcNc) {
        LOG.debug("DHCP pkt rcvd {}", dhcpPkt);
        byte msgType = dhcpPkt.getMsgType();
        if (msgType == DHCPConstants.MSG_DECLINE) {
            LOG.debug("DHCPDECLINE received");
            return null;
        } else if (msgType == DHCPConstants.MSG_RELEASE) {
            LOG.debug("DHCPRELEASE received");
            return null;
        }

        Port nPort = getNeutronPort(fcNc);
        Subnet nSubnet = getNeutronSubnet(nPort);
        DhcpInfo dhcpInfo = getDhcpInfo(nPort, nSubnet);
        LOG.trace("NeutronPort: {} \n NeutronSubnet: {}, dhcpInfo{}",nPort, nSubnet, dhcpInfo);
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

    private DhcpInfo getDhcpInfo(Port nPort, Subnet nSubnet) {
        DhcpInfo dhcpInfo = null;
        if( (nPort != null) && (nSubnet != null) ) {
            String clientIp = nPort.getFixedIps().get(0).getIpAddress().getIpv4Address().getValue();
            String serverIp = nSubnet.getGatewayIp().getIpv4Address().getValue();
            List<IpAddress> dnsServers = nSubnet.getDnsNameservers();
            dhcpInfo = new DhcpInfo();
            dhcpInfo.setClientIp(clientIp).setServerIp(serverIp)
                .setCidr(nSubnet.getCidr()).setHostRoutes(nSubnet.getHostRoutes())
                .setDnsServersIpAddrs(dnsServers).setGatewayIp(serverIp);
        } else {
            //FIXME: Delete this test code
            LOG.error("TestOnly Code");
            dhcpInfo = new DhcpInfo();
            dhcpInfo.setClientIp("1.1.1.3").setServerIp("1.1.1.1")
                .setCidr("1.1.1.0/24").addDnsServer("1.1.1.1");
            LOG.warn("Failed to get Subnet info for DHCP reply");
        }
        return dhcpInfo;
    }

    private Subnet getNeutronSubnet(Port nPort) {
        return dhcpMgr.getNeutronSubnet(nPort);
    }

    private Port getNeutronPort(FlowCapableNodeConnector fcNc) {
            return dhcpMgr.getNeutronPort(fcNc.getName());
    }

    private FlowCapableNodeConnector getFlowCapableNodeConnector(NodeConnectorRef inNcRef) {
        InstanceIdentifier<NodeConnector> ncId = inNcRef.getValue().firstIdentifierOf(NodeConnector.class);
        Optional<NodeConnector> nodeConnector = 
                        MDSALDataStoreUtils.read(dataBroker, LogicalDatastoreType.OPERATIONAL, ncId);
        if(nodeConnector.isPresent()) {
            NodeConnector nc = nodeConnector.get();
            LOG.trace("Incoming pkt's NodeConnector: {}", nc);
            FlowCapableNodeConnector fcnc = nc.getAugmentation(FlowCapableNodeConnector.class);
            return fcnc;
        }
        return null;
    }

    private DHCP getDhcpPktIn(Ethernet ethPkt) {
        if (ethPkt.getPayload() instanceof IPv4) {
            IPv4 ipPkt = (IPv4) ethPkt.getPayload();
            if (ipPkt.getPayload() instanceof UDP) {
                UDP udpPkt = (UDP) ipPkt.getPayload();
                if ((udpPkt.getSourcePort() == DHCPMConstants.dhcpClientPort)
                        && (udpPkt.getDestinationPort() == DHCPMConstants.dhcpServerPort)) {
                    byte[] rawDhcpPayload = udpPkt.getRawPayload();
                    DHCP reply = new DHCP();
                    try {
                        reply.deserialize(rawDhcpPayload, 0, rawDhcpPayload.length);
                    } catch (PacketException e) {
                        LOG.warn("Failed to deserialize DHCP pkt", e);
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
        if(dhcpPkt.containsOption(DHCPConstants.OPT_PARAMETER_REQUEST_LIST)) {
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
        byte[] allocatedIp = DHCPUtils.strAddrToByteArray(dhcpInfo.getClientIp());
        if(Arrays.equals(allocatedIp, dhcpPkt.getCiaddr())) {
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
            if(dhcpPkt.containsOption(DHCPConstants.OPT_PARAMETER_REQUEST_LIST)) {
                setParameterListOptions(dhcpPkt, reply, dhcpInfo);
            }
            setCommonOptions(reply, dhcpInfo);
        }
        else {
            reply.setMsgType(DHCPConstants.MSG_NAK);
        }
        return reply;
    }

    protected byte[] getDhcpPacketOut(DHCP reply, Ethernet etherPkt, FlowCapableNodeConnector fcNc) {
        if (reply == null) {
            /*
             * DECLINE or RELEASE don't result in reply packet
             */
            return null;
        }
        LOG.debug("Sending DHCP Pkt {}", reply);
        // create UDP pkt
        UDP udpPkt = new UDP();
        byte[] rawPkt;
        try {
            rawPkt = reply.serialize();
        } catch (PacketException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
            return null;
        }
        udpPkt.setRawPayload(rawPkt);
        udpPkt.setDestinationPort(DHCPMConstants.dhcpClientPort);
        udpPkt.setSourcePort(DHCPMConstants.dhcpServerPort);
        udpPkt.setLength((short) (rawPkt.length + 8));
        //Create IP Pkt
        IPv4 ip4Reply = new IPv4();
        try {
            rawPkt = udpPkt.serialize();
        } catch (PacketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
        short checkSum = 0;
        if(this.computeUdpChecksum) {
         checkSum = computeChecksum(rawPkt, reply.getSiaddr(),
                NetUtils.intToByteArray4(DHCPMConstants.BCAST_IP));
        }
        udpPkt.setChecksum(checkSum);
        ip4Reply.setPayload(udpPkt);
        ip4Reply.setProtocol(IPProtocols.UDP.byteValue());
        ip4Reply.setSourceAddress(reply.getSiaddrAsInetAddr());
        ip4Reply.setDestinationAddress(DHCPMConstants.BCAST_IP);
        ip4Reply.setTotalLength((short) (rawPkt.length+20));
        ip4Reply.setTtl((byte) 32);
        // create Ethernet Frame
        Ethernet ether = new Ethernet();
        //TODO: 
        ether.setSourceMACAddress(getServerMacAddress(fcNc));
        ether.setDestinationMACAddress(etherPkt.getSourceMACAddress());
        ether.setEtherType(EtherTypes.IPv4.shortValue());
        ether.setPayload(ip4Reply);
        try {
            rawPkt = ether.serialize();
        } catch (PacketException e) {
            LOG.warn("Failed to serialize ethernet reply",e);
            return null;
        }
        return rawPkt;
    }

    private byte[] getServerMacAddress(FlowCapableNodeConnector fcNc) {
        // Should we return ControllerMac instead?
        MacAddress macAddress = fcNc.getHardwareAddress();
        return DHCPUtils.strMacAddrtoByteArray(macAddress.getValue());
    }

    public short computeChecksum(byte[] inData, byte[] srcAddr, byte[] destAddr) {
        short checkSum = (short) 0;
        int sum = 0, carry = 0;
        int wordData, i;

        for (i = 0; i < inData.length - 1; i = i + 2) {
            // Skip, if the current bytes are checkSum bytes
            wordData = ((inData[i] << 8) & 0xFF00) + (inData[i + 1] & 0xFF);
            sum = sum + wordData;
        }

        if (i < inData.length) {
            wordData = ((inData[i] << 8) & 0xFF00) + (0 & 0xFF);
            sum = sum + wordData;
        }

        for (i = 0; i < 4; i = i + 2) {
            wordData = ((srcAddr[i] << 8) & 0xFF00) + (srcAddr[i + 1] & 0xFF);
            sum = sum + wordData;
        }

        for (i = 0; i < 4; i = i + 2) {
            wordData = ((destAddr[i] << 8) & 0xFF00) + (destAddr[i + 1] & 0xFF);
            sum = sum + wordData;
        }
        sum = sum + 17 + inData.length;

        while((sum >> 16) != 0) {
            carry = (sum >> 16);
            sum = (sum & 0xFFFF)+ carry;
        }
        checkSum = (short) ~((short) sum & 0xFFFF);
        if(checkSum == 0) {
            checkSum = (short)0xffff;
        }
        return checkSum;
    }

    private void setCommonOptions(DHCP pkt, DhcpInfo dhcpInfo) {
        pkt.setOptionInt(DHCPConstants.OPT_LEASE_TIME, dhcpMgr.getDhcpLeaseTime());
        if (dhcpMgr.getDhcpDefDomain() != null) {
            pkt.setOptionString(DHCPConstants.OPT_DOMAIN_NAME, dhcpMgr.getDhcpDefDomain());
        }
        if(dhcpMgr.getDhcpLeaseTime() > 0) {
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
                pkt.setOptionInetAddr(DHCPConstants.OPT_SERVER_IDENTIFIER, gwIp);
                pkt.setOptionInetAddr(DHCPConstants.OPT_ROUTERS, gwIp);
            } else {
                pkt.unsetOption(DHCPConstants.OPT_SERVER_IDENTIFIER);
                pkt.unsetOption(DHCPConstants.OPT_ROUTERS);
            }
            if (info != null) {
                pkt.setOptionInetAddr(DHCPConstants.OPT_SUBNET_MASK, info.getNetmask());
                pkt.setOptionInetAddr(DHCPConstants.OPT_BROADCAST_ADDRESS, info.getBroadcastAddress());
            } else {
                pkt.unsetOption(DHCPConstants.OPT_SUBNET_MASK);
                pkt.unsetOption(DHCPConstants.OPT_BROADCAST_ADDRESS);
            }
            if ((dnServers != null) && (dnServers.size() > 0)) {
                pkt.setOptionStrAddrs(DHCPConstants.OPT_DOMAIN_NAME_SERVERS, dnServers);
            } else {
                pkt.unsetOption(DHCPConstants.OPT_DOMAIN_NAME_SERVERS);
            }
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void setParameterListOptions(DHCP req, DHCP reply, DhcpInfo dhcpInfo) {
        byte[] paramList = req.getOptionBytes(DHCPConstants.OPT_PARAMETER_REQUEST_LIST);
        for(int i = 0; i < paramList.length; i++) {
            switch (paramList[i]) {
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
                reply.setOptionInt(paramList[i], 0);
                break;
            case DHCPConstants.OPT_DOMAIN_NAME:
                reply.setOptionString(paramList[i], " ");
                break;
            case DHCPConstants.OPT_CLASSLESS_ROUTE:
                setOptionClasslessRoute(reply, dhcpInfo);
                break;
            default:
                LOG.debug("DHCP Option code {} not supported yet", paramList[i]);
                break;
            }
        }
    }
    private void setOptionClasslessRoute(DHCP reply, DhcpInfo dhcpInfo) {
        List<HostRoutes> hostRoutes = dhcpInfo.getHostRoutes();
        if(hostRoutes == null) {
            //we can't set this option, so return
            return;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Iterator<HostRoutes> iter = hostRoutes.iterator();
        while(iter.hasNext()) {
            HostRoutes hostRoute = iter.next();
            if(hostRoute.getNexthop().getIpv4Address() == null ||
                hostRoute.getDestination().getIpv4Prefix() == null ) {
                // we only deal with IPv4 addresses
                return;
            }
            String router = hostRoute.getNexthop().getIpv4Address().getValue();
            String dest = hostRoute.getDestination().getIpv4Prefix().getValue();
            try {
                result.write(convertToClasslessRouteOption(dest, router));
            } catch (IOException | NullPointerException e) {
                LOG.debug("Exception {}",e.getMessage());
            }
        }
        if (result.size() > 0) {
            reply.setOptionBytes(DHCPConstants.OPT_CLASSLESS_ROUTE , result.toByteArray());
        }
    }

    protected byte[] convertToClasslessRouteOption(String dest, String router) {
        ByteArrayOutputStream bArr = new ByteArrayOutputStream();
        if((dest == null ||
                router == null)) {
            return null;
        }

        //get prefix
        Short prefix = null;
        String[] parts = dest.split("/");
        if (parts.length < 2) {
            prefix = new Short((short)0);
        } else {
            prefix = Short.valueOf(parts[1]);
        }

        bArr.write(prefix.byteValue());
        SubnetUtils util = new SubnetUtils(dest);
        SubnetInfo info = util.getInfo();
        String strNetAddr = info.getNetworkAddress();
        try {
            byte[] netAddr = InetAddress.getByName(strNetAddr).getAddress();
          //Strip any trailing 0s from netAddr
            for(int i = 0; i < netAddr.length;i++) {
                if(netAddr[i] != 0) {
                    bArr.write(netAddr,i,1);
                }
            }
            bArr.write(InetAddress.getByName(router).getAddress());
        } catch (IOException e) {
            return null;
        }
        return bArr.toByteArray();
    }

    private boolean isPktInReasonSendtoCtrl(Class<? extends PacketInReason> pktInReason) {
        return (pktInReason == SendToController.class);
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
    }

    public void setPacketProcessingService(PacketProcessingService packetService) {
        this.pktService = packetService;
    }

}
