/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.packet.ARP;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpUtils {
    private static final Logger s_logger = LoggerFactory.getLogger(ArpUtils.class);
    
    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, long groupId, byte[] abySenderMAC,
            byte[] abySenderIpAddress, byte[] abyTargetIpAddress) {
        return createArpRequestInput(dpnId, groupId, abySenderMAC, abySenderIpAddress, abyTargetIpAddress, null);
    }
    
    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, byte[] abySenderMAC,
            byte[] abySenderIpAddress, byte[] abyTargetIpAddress, NodeConnectorRef ingress) {
        return createArpRequestInput(dpnId, null, abySenderMAC, abySenderIpAddress, abyTargetIpAddress, ingress);
    }

    public static byte[] getMacInBytes(String macAddress){
        String[] macAddressParts = macAddress.split(":");

        // convert hex string to byte values
        byte[] macAddressBytes = new byte[6];
        for(int i=0; i<6; i++){
            Integer hex = Integer.parseInt(macAddressParts[i], 16);
            macAddressBytes[i] = hex.byteValue();
        }
        
        return macAddressBytes;
    }
    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, Long groupId, byte[] abySenderMAC,
            byte[] abySenderIpAddress, byte[] abyTargetIpAddress, NodeConnectorRef ingress) {

        s_logger.info(
                "SubnetRoutePacketInHandler: sendArpRequest dpnId {}, groupId {}, senderIPAddress {}, targetIPAddress {}",
                dpnId, groupId, toStringIpAddress(abySenderIpAddress), toStringIpAddress(abyTargetIpAddress));
        if (abySenderIpAddress != null) {
            byte[] arpPacket;
            byte[] ethPacket;
            List<ActionInfo> lstActionInfo;

            arpPacket = createARPPacket(ARP.REQUEST, abySenderMAC, abySenderIpAddress, VpnConstants.MAC_Broadcast,
                    abyTargetIpAddress);
            ethPacket = createEthernetPacket(abySenderMAC, VpnConstants.EthernetDestination_Broadcast, arpPacket);
            lstActionInfo = new ArrayList<ActionInfo>();
            if (groupId != null) {
                lstActionInfo.add(new ActionInfo(ActionType.group, new String[] { String.valueOf(groupId) }));
            }
            if (ingress != null) {
                return MDSALUtil.getPacketOut(lstActionInfo, ethPacket, dpnId.longValue(), ingress);
            } else {
                return MDSALUtil.getPacketOutDefault(lstActionInfo, ethPacket, dpnId);
            }
        } else {
            s_logger.info("SubnetRoutePacketInHandler: Unable to send ARP request because client port has no IP  ");
            return null;
        }
    }

    private static String toStringIpAddress(byte[] ipAddress) {
        String ip = null;
        if (ipAddress == null) {
            return ip;
        }

        try {
            ip = InetAddress.getByAddress(ipAddress).getHostAddress();
        } catch (UnknownHostException e) {
            s_logger.error("SubnetRoutePacketInHandler: Unable to translate byt[] ipAddress to String {}", e);
        }

        return ip;
    }

    private static byte[] createEthernetPacket(byte[] sourceMAC, byte[] targetMAC, byte[] arp) {
        Ethernet ethernet = new Ethernet();
        byte[] rawEthPkt = null;
        try {
            ethernet.setSourceMACAddress(sourceMAC);
            ethernet.setDestinationMACAddress(targetMAC);
            ethernet.setEtherType(EtherTypes.ARP.shortValue());
            ethernet.setRawPayload(arp);
            rawEthPkt = ethernet.serialize();
        } catch (Exception ex) {
            s_logger.error(
                    "VPNUtil:  Serialized Ethernet packet with sourceMacAddress {} targetMacAddress {} exception ",
                    sourceMAC, targetMAC, ex);
        }
        return rawEthPkt;
    }

    private static byte[] createARPPacket(short opCode, byte[] senderMacAddress, byte[] senderIP,
            byte[] targetMacAddress, byte[] targetIP) {
        ARP arp = new ARP();
        byte[] rawArpPkt = null;
        try {
            arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
            arp.setProtocolType(EtherTypes.IPv4.shortValue());
            arp.setHardwareAddressLength((byte) 6);
            arp.setProtocolAddressLength((byte) 4);
            arp.setOpCode(opCode);
            arp.setSenderHardwareAddress(senderMacAddress);
            arp.setSenderProtocolAddress(senderIP);
            arp.setTargetHardwareAddress(targetMacAddress);
            arp.setTargetProtocolAddress(targetIP);
            rawArpPkt = arp.serialize();
        } catch (Exception ex) {
            s_logger.error("VPNUtil:  Serialized ARP packet with senderIp {} targetIP {} exception ", senderIP,
                    targetIP, ex);
        }

        return rawArpPkt;
    }

}
