/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.utils;

import org.apache.commons.lang3.StringUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.EthernetHeader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.routemgr.nd.packet.rev160302.Ipv6Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class RoutemgrUtil {
    public static final int ICMPv6_TYPE = 58;
    public static final short ICMPv6_RS_CODE = 133;
    public static final short ICMPv6_RA_CODE = 134;
    public static final short ICMPv6_NS_CODE = 135;
    public static final short ICMPv6_NA_CODE = 136;
    public static final short ICMPv6_MAX_HOP_LIMIT = 255;
    public static final int ICMPV6_OFFSET = 54;
    public static final RoutemgrUtil instance = new RoutemgrUtil();
    public static Ipv6Address UNSPECIFIED_ADDR;
    public static Ipv6Address ALL_NODES_MCAST_ADDR;
    private static final Logger LOG = LoggerFactory.getLogger(RoutemgrUtil.class);

    private RoutemgrUtil() {
        try {
            UNSPECIFIED_ADDR = Ipv6Address.getDefaultInstance(InetAddress.getByName("0:0:0:0:0:0:0:0").getHostAddress());
            ALL_NODES_MCAST_ADDR = Ipv6Address.getDefaultInstance(InetAddress.getByName("FF02::1").getHostAddress());
        } catch (UnknownHostException e) {
            LOG.error("RoutemgrUtil: Failed to instantiate the ipv6 address", e);
        }
    }

    public static RoutemgrUtil getInstance() {
        return instance;
    }

    public String bytesToHexString(byte[] bytes) {
        if(bytes == null) {
            return "null";
        }
        StringBuffer buf = new StringBuffer();
        for(int i = 0; i < bytes.length; i++) {
            if(i > 0) {
                buf.append(":");
            }
            short u8byte = (short) (bytes[i] & 0xff);
            String tmp = Integer.toHexString(u8byte);
            if(tmp.length() == 1) {
                buf.append("0");
            }
            buf.append(tmp);
        }
        return buf.toString();
    }

    public byte[] bytesFromHexString(String values) {
        String target = "";
        if(values != null) {
            target = values;
        }
        String[] octets = target.split(":");

        byte[] ret = new byte[octets.length];
        for(int i = 0; i < octets.length; i++) {
            ret[i] = Integer.valueOf(octets[i], 16).byteValue();
        }
        return ret;
    }

    public int calcIcmpv6Checksum(byte[] packet, Ipv6Header ip6Hdr) {
        long checksum = getSummation(ip6Hdr.getSourceIpv6());
        checksum += getSummation(ip6Hdr.getDestinationIpv6());
        checksum = normalizeChecksum(checksum);

        checksum += ip6Hdr.getIpv6Length();
        checksum += ip6Hdr.getNextHeader();

        int icmp6Offset = ICMPV6_OFFSET;
        long value = (((packet[icmp6Offset] & 0xff) << 8) | (packet[icmp6Offset + 1] & 0xff));
        checksum += value;
        checksum = normalizeChecksum(checksum);
        icmp6Offset += 2;

        //move to icmp6 payload skipping the checksum field
        icmp6Offset += 2;
        int length = packet.length - icmp6Offset;
        while (length > 1) {
            value = (((packet[icmp6Offset] & 0xff) << 8) | (packet[icmp6Offset + 1] & 0xff));
            checksum += value;
            checksum = normalizeChecksum(checksum);
            icmp6Offset += 2;
            length -= 2;
        }

        if (length > 0) {
            checksum += packet[icmp6Offset];
            checksum = normalizeChecksum(checksum);
        }

        int cSum = (int)(~checksum & 0xffff);
        return cSum;
    }

    public boolean validateChecksum(byte[] packet, Ipv6Header ip6Hdr, int recvChecksum) {
        int checksum = calcIcmpv6Checksum(packet, ip6Hdr);

        if (checksum == recvChecksum) {
            return true;
        }
        return false;
    }

    private long getSummation(Ipv6Address addr) {
        byte[] bAddr = null;
        try {
            bAddr = InetAddress.getByName(addr.getValue()).getAddress();
        } catch (UnknownHostException e) {
            LOG.error("getSummation: Failed to deserialize address {}", addr.getValue(), e);
        }

        long sum = 0;
        int i = 0;
        long value = 0;
        while (i < bAddr.length) {
            value = (((bAddr[i] & 0xff) << 8) | (bAddr[i+1] & 0xff));
            sum += value;
            sum = normalizeChecksum(sum);
            i+=2;
        }
        return sum;
    }

    private long normalizeChecksum(long value) {
        if((value & 0xffff0000) > 0) {
            value = (value & 0xffff);
            value += 1;
        }
        return value;
    }

    public byte[] convertEthernetHeaderToByte(EthernetHeader ethPdu) {
        byte[] data = new byte[16];
        Arrays.fill(data, (byte)0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put(bytesFromHexString(ethPdu.getDestinationMac().getValue().toString()));
        buf.put(bytesFromHexString(ethPdu.getSourceMac().getValue().toString()));
        buf.putShort((short)ethPdu.getEthertype().intValue());
        return data;
    }

    public byte[] convertIpv6HeaderToByte(Ipv6Header ip6Pdu) {
        byte[] data = new byte[128];
        Arrays.fill(data, (byte)0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        long fLabel = (((long)(ip6Pdu.getVersion().shortValue() & 0x0f) << 28)
                | (ip6Pdu.getFlowLabel().longValue() & 0x0fffffff));
        buf.putInt((int)fLabel);
        buf.putShort((short)ip6Pdu.getIpv6Length().intValue());
        buf.put((byte)ip6Pdu.getNextHeader().shortValue());
        buf.put((byte)ip6Pdu.getHopLimit().shortValue());
        try {
            byte[] bAddr = InetAddress.getByName(ip6Pdu.getSourceIpv6().getValue()).getAddress();
            buf.put(bAddr);
            bAddr = InetAddress.getByName(ip6Pdu.getDestinationIpv6().getValue()).getAddress();
            buf.put(bAddr);
        } catch (UnknownHostException e) {
            LOG.error("convertIpv6HeaderToByte: Failed to serialize src, dest address", e);
        }
        return data;
    }

    public Ipv6Address getIpv6LinkLocalAddressFromMac(MacAddress mac) {
        byte[] octets = bytesFromHexString(mac.getValue());

        /* As per the RFC2373, steps involved to generate a LLA include
           1. Convert the 48 bit MAC address to 64 bit value by inserting 0xFFFE
              between OUI and NIC Specific part.
           2. Invert the Universal/Local flag in the OUI portion of the address.
           3. Use the prefix "FE80::/10" along with the above 64 bit Interface
              identifier to generate the IPv6 LLA. */

        StringBuffer interfaceID = new StringBuffer();
        short u8byte = (short) (octets[0] & 0xff);
        u8byte ^= 1 << 1;
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & u8byte), 2, "0"));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[1]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[2]), 2, "0"));
        interfaceID.append("ff:fe");
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[3]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[4]), 2, "0"));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[5]), 2, "0"));

        Ipv6Address ipv6LLA = new Ipv6Address("fe80:0:0:0:"+interfaceID.toString());
        return ipv6LLA;
    }
}
