/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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
import org.opendaylight.netvirt.dhcpservice.api.DHCP;
import org.opendaylight.netvirt.dhcpservice.api.DHCPConstants;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.dhcpservice.api.DHCPUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.HostRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpPktHandler implements AutoCloseable, PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpPktHandler.class);
    private final DataBroker dataBroker;
    private final DhcpManager dhcpMgr;
    private OdlInterfaceRpcService interfaceManagerRpc;
    private boolean computeUdpChecksum = true;
    private PacketProcessingService pktService;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private IInterfaceManager interfaceManager;

    public DhcpPktHandler(final DataBroker broker, final DhcpManager dhcpManager, final DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dataBroker = broker;
        dhcpMgr = dhcpManager;
    }

    //TODO: Handle this in a separate thread
    @Override
    public void onPacketReceived(PacketReceived packet) {
        Class<? extends PacketInReason> pktInReason = packet.getPacketInReason();
        if (isPktInReasonSendtoCtrl(pktInReason)) {
            byte[] inPayload = packet.getPayload();
            Ethernet ethPkt = new Ethernet();
            try {
                ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NumBitsInAByte);
            } catch (Exception e) {
                LOG.warn("Failed to decode DHCP Packet {}", e);
                LOG.trace("Received packet {}", packet);
                return;
            }
            try {
                DHCP pktIn;
                pktIn = getDhcpPktIn(ethPkt);
                if (pktIn != null) {
                    LOG.trace("DHCPPkt received: {}", pktIn);
                    LOG.trace("Received Packet: {}", packet);
                    BigInteger metadata = packet.getMatch().getMetadata().getMetadata();
                    long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
                    String macAddress = DHCPUtils.byteArrayToString(ethPkt.getSourceMACAddress());
                    BigInteger tunnelId = packet.getMatch().getTunnel() == null ? null : packet.getMatch().getTunnel().getTunnelId();
                    String interfaceName = getInterfaceNameFromTag(portTag);
                    InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName);
                    if (interfaceInfo == null) {
                        LOG.error("Failed to get interface info for interface name {}", interfaceName);
                        return;
                    }
                    DHCP replyPkt = handleDhcpPacket(pktIn, interfaceName, macAddress, tunnelId);
                    byte[] pktOut = getDhcpPacketOut(replyPkt, ethPkt, interfaceInfo.getMacAddress());
                    sendPacketOut(pktOut, interfaceInfo.getDpId(), interfaceName, tunnelId);
                }
            } catch (Exception e) {
                LOG.warn("Failed to get DHCP Reply");
                LOG.trace("Reason for failure {}", e);
            }
        }
    }

    private void sendPacketOut(byte[] pktOut, BigInteger dpnId, String interfaceName, BigInteger tunnelId) {
        LOG.trace("Sending packet out DpId {}, portId {}, vlanId {}, interfaceName {}", dpnId, interfaceName);
        List<Action> action = getEgressAction(interfaceName, tunnelId);
        TransmitPacketInput output = MDSALUtil.getPacketOut(action, pktOut, dpnId);
        LOG.trace("Transmitting packet: {}",output);
        this.pktService.transmitPacket(output);
    }

    private DHCP handleDhcpPacket(DHCP dhcpPkt, String interfaceName, String macAddress, BigInteger tunnelId) {
        LOG.debug("DHCP pkt rcvd {}", dhcpPkt);
        byte msgType = dhcpPkt.getMsgType();
        if (msgType == DHCPConstants.MSG_DECLINE) {
            LOG.debug("DHCPDECLINE received");
            return null;
        } else if (msgType == DHCPConstants.MSG_RELEASE) {
            LOG.debug("DHCPRELEASE received");
            return null;
        }
        Port nPort;
        if (tunnelId != null) {
            nPort = dhcpExternalTunnelManager.readVniMacToPortCache(tunnelId, macAddress);
        } else {
            nPort = getNeutronPort(interfaceName);
        }
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
                .setCidr(String.valueOf(nSubnet.getCidr().getValue())).setHostRoutes(nSubnet.getHostRoutes())
                .setDnsServersIpAddrs(dnsServers).setGatewayIp(serverIp);
        }
        return dhcpInfo;
    }

    private Subnet getNeutronSubnet(Port nPort) {
        return dhcpMgr.getNeutronSubnet(nPort);
    }

    private Port getNeutronPort(String interfaceName) {
            return dhcpMgr.getNeutronPort(interfaceName);
    }

    private DHCP getDhcpPktIn(Ethernet actualEthernetPacket) {
        Ethernet ethPkt = actualEthernetPacket;
        if (ethPkt.getEtherType() == (short)NwConstants.ETHTYPE_802_1Q) {
            ethPkt = (Ethernet)ethPkt.getPayload();
        }
        if (ethPkt.getPayload() instanceof IPv4) {
            IPv4 ipPkt = (IPv4) ethPkt.getPayload();
            if (ipPkt.getPayload() instanceof UDP) {
                UDP udpPkt = (UDP) ipPkt.getPayload();
                if ((udpPkt.getSourcePort() == DHCPMConstants.dhcpClientPort)
                        && (udpPkt.getDestinationPort() == DHCPMConstants.dhcpServerPort)) {
                    LOG.trace("Matched dhcpClientPort and dhcpServerPort");
                    byte[] rawDhcpPayload = udpPkt.getRawPayload();
                    DHCP reply = new DHCP();
                    try {
                        reply.deserialize(rawDhcpPayload, 0, rawDhcpPayload.length);
                    } catch (PacketException e) {
                        LOG.warn("Failed to deserialize DHCP pkt");
                        LOG.trace("Reason for failure {}", e);
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

    protected byte[] getDhcpPacketOut(DHCP reply, Ethernet etherPkt, String phyAddrees) {
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

    public void setInterfaceManagerRpc(OdlInterfaceRpcService interfaceManagerRpc) {
        LOG.trace("Registered interfaceManager successfully");
        this.interfaceManagerRpc = interfaceManagerRpc;
    }

    private String getInterfaceNameFromTag(long portTag) {
        String interfaceName = null;
        GetInterfaceFromIfIndexInput input = new GetInterfaceFromIfIndexInputBuilder().setIfIndex(new Integer((int)portTag)).build();
        Future<RpcResult<GetInterfaceFromIfIndexOutput>> futureOutput = interfaceManagerRpc.getInterfaceFromIfIndex(input);
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
            GetEgressActionsForInterfaceInputBuilder egressAction = new GetEgressActionsForInterfaceInputBuilder().setIntfName(interfaceName);
            if (tunnelId != null) {
                egressAction.setTunnelKey(tunnelId.longValue());
            }
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    interfaceManagerRpc.getEgressActionsForInterface(egressAction.build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}", interfaceName, rpcResult.getErrors());
            } else {
                actions = rpcResult.getResult().getAction();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", interfaceName, e);
        }
        return actions;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }
}
