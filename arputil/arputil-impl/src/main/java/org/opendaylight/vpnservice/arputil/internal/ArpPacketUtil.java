/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.arputil.internal;

import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.vpnservice.mdsalutil.packet.ARP;
import org.opendaylight.vpnservice.mdsalutil.packet.Ethernet;

public class ArpPacketUtil {

    public static byte[] EthernetDestination_Broadcast = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
    public static byte[] MAC_Broadcast = new byte[] { (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0 };

    public static byte[] getPayload(short opCode, byte[] senderMacAddress, byte[] senderIP, byte[] targetMacAddress,
            byte[] targetIP) throws PacketException {
        ARP arp = createARPPacket(opCode, senderMacAddress, senderIP, targetMacAddress, targetIP);
        Ethernet ethernet = createEthernetPacket(senderMacAddress, targetMacAddress, arp);
        return ethernet.serialize();
    }

    public static ARP createARPPacket(short opCode, byte[] senderMacAddress, byte[] senderIP, byte[] targetMacAddress,
            byte[] targetIP) {
        ARP arp = new ARP();
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        arp.setProtocolType(EtherTypes.IPv4.shortValue());
        arp.setHardwareAddressLength((byte) 6);
        arp.setProtocolAddressLength((byte) 4);
        arp.setOpCode(opCode);
        arp.setSenderHardwareAddress(senderMacAddress);
        arp.setSenderProtocolAddress(senderIP);
        arp.setTargetHardwareAddress(targetMacAddress);
        arp.setTargetProtocolAddress(targetIP);
        return arp;
    }

    public static Ethernet createEthernetPacket(byte[] sourceMAC, byte[] targetMAC, ARP arp)
            throws PacketException {
        Ethernet ethernet = new Ethernet();
        ethernet.setSourceMACAddress(sourceMAC);
        ethernet.setDestinationMACAddress(targetMAC);
        ethernet.setEtherType(EtherTypes.ARP.shortValue());
        ethernet.setPayload(arp);
        return ethernet;
    }
}