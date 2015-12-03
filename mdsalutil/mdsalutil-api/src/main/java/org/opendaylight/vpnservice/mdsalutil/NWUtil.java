/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.net.InetAddress;

import com.google.common.primitives.UnsignedBytes;

public class NWUtil {

    public static  long convertInetAddressToLong(InetAddress address) {
        byte[] ipAddressRaw = address.getAddress();
        return (((ipAddressRaw[0] & 0xFF) << (3 * 8))
                + ((ipAddressRaw[1] & 0xFF) << (2 * 8))
                + ((ipAddressRaw[2] & 0xFF) << (1 * 8))
                + (ipAddressRaw[3] & 0xFF))
                & 0xffffffffL;
    }

    public static byte[] parseIpAddress(String ipAddress) {
        byte cur;

        String[] addressPart = ipAddress.split(".");
        int size = addressPart.length;

        byte[] part = new byte[size];
        for (int i = 0; i < size; i++) {
            cur = UnsignedBytes.parseUnsignedByte(addressPart[i], 16);
            part[i] = cur;
        }

        return part;
    }

    public static byte[] parseMacAddress(String macAddress) {
        byte cur;

        String[] addressPart = macAddress.split(":");
        int size = addressPart.length;

        byte[] part = new byte[size];
        for (int i = 0; i < size; i++) {
            cur = UnsignedBytes.parseUnsignedByte(addressPart[i], 16);
            part[i] = cur;
        }

        return part;
    }

    public static String toStringIpAddress(byte[] ipAddress)
    {
        if (ipAddress == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(18);

        for (int i = 0; i < ipAddress.length; i++) {
            sb.append(UnsignedBytes.toString(ipAddress[i], 10));
            sb.append(".");
        }

        sb.setLength(17);
        return sb.toString();
    }

    public static String toStringMacAddress(byte[] macAddress)
    {
        if (macAddress == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(18);

        for (int i = 0; i < macAddress.length; i++) {
            String tmp = UnsignedBytes.toString(macAddress[i], 16).toUpperCase();
            if(tmp.length() == 1 || macAddress[i] == (byte)0) {
                sb.append("0");
            }
            sb.append(tmp);
            sb.append(":");
        }

        sb.setLength(17);
        return sb.toString();
    }
}
