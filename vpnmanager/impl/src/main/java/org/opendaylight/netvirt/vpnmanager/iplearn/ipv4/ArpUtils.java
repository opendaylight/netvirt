/*
 * Copyright © 2016, 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn.ipv4;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.packet.ARP;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.openflowplugin.libraries.liblldp.EtherTypes;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArpUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ArpUtils.class);

    private static final byte[] ETHERNETDESTINATION_BROADCAST = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] MAC_BROADCAST = new byte[] {(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};

    private ArpUtils() { }

    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, long groupId, byte[] abySenderMAC,
        byte[] abySenderIpAddress, byte[] abyTargetIpAddress) {
        return createArpRequestInput(dpnId, groupId, abySenderMAC, abySenderIpAddress, abyTargetIpAddress, null);
    }

    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, byte[] abySenderMAC,
        byte[] abySenderIpAddress, byte[] abyTargetIpAddress, NodeConnectorRef ingress) {
        return createArpRequestInput(dpnId, null, abySenderMAC, (byte[]) null, abySenderIpAddress, abyTargetIpAddress,
            ingress, new ArrayList<>());
    }

    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, Long groupId, byte[] abySenderMAC,
        byte[] abySenderIpAddress, byte[] abyTargetIpAddress, NodeConnectorRef ingress) {
        List<ActionInfo> lstActionInfo = new ArrayList<>();
        return createArpRequestInput(dpnId, groupId, abySenderMAC, null, abySenderIpAddress, abyTargetIpAddress,
            ingress, lstActionInfo);
    }

    public static TransmitPacketInput createArpRequestInput(BigInteger dpnId, Long groupId, byte[] abySenderMAC,
            byte[] abyTargetMAC, byte[] abySenderIpAddress, byte[] abyTargetIpAddress, NodeConnectorRef ingress,
            List<ActionInfo> lstActionInfo) {

        LOG.info("SubnetRoutePacketInHandler: sendArpRequest dpnId {}, actions {},"
                 + " groupId {}, senderIPAddress {}, targetIPAddress {}",
                dpnId, lstActionInfo, groupId, NWUtil.toStringIpAddress(abySenderIpAddress),
                NWUtil.toStringIpAddress(abyTargetIpAddress));
        if (abySenderIpAddress != null) {
            byte[] arpPacket;
            byte[] ethPacket;

            byte[] targetMac = abyTargetMAC != null ? abyTargetMAC : MAC_BROADCAST;
            arpPacket = createARPPacket(ARP.REQUEST, abySenderMAC, abySenderIpAddress, targetMac, abyTargetIpAddress);
            ethPacket = createEthernetPacket(abySenderMAC, ETHERNETDESTINATION_BROADCAST, arpPacket);
            if (groupId != null) {
                lstActionInfo.add(new ActionGroup(groupId));
            }
            if (ingress != null) {
                return MDSALUtil.getPacketOutFromController(lstActionInfo, ethPacket, dpnId.longValue(), ingress);
            } else {
                return MDSALUtil.getPacketOutDefault(lstActionInfo, ethPacket, dpnId);
            }
        } else {
            LOG.info("SubnetRoutePacketInHandler: Unable to send ARP request because client port has no IP  ");
            return null;
        }
    }

    public static byte[] getMacInBytes(String macAddress) {
        String[] macAddressParts = macAddress.split(":");

        // convert hex string to byte values
        byte[] macAddressBytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            Integer hex = Integer.parseInt(macAddressParts[i], 16);
            macAddressBytes[i] = hex.byteValue();
        }

        return macAddressBytes;
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
        } catch (PacketException ex) {
            LOG.error(
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
        } catch (PacketException ex) {
            LOG.error("VPNUtil:  Serialized ARP packet with senderIp {} targetIP {} exception ", senderIP,
                targetIP, ex);
        }

        return rawArpPkt;
    }
}
