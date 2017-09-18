/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.utils;

import java.math.BigInteger;
import java.util.Random;
import java.util.UUID;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.DatapathId;

public class RandomUtils {
    public static final String BASE_IP = "192.168.";
    public static final String SUBNET_MASK = "192.168.0.0/16";
    public static final String GATEWAY_IP = "0.0.0.0";
    public static final int DEFAULT_PREFIX_LEN = 24;

    public static Uuid createUuid() {
        return new Uuid(UUID.randomUUID().toString());
    }

    public static String createMac() {
        /*
         * Code taken from: http://stackoverflow.com/questions/24261027/
         */
        Random rand = new Random();
        byte[] macAddr = new byte[6];
        rand.nextBytes(macAddr);

        macAddr[0] = (byte) (macAddr[0] & (byte) 254); //zeroing last 2 bytes to make it unicast and local

        StringBuilder sb = new StringBuilder(18);
        for (byte b : macAddr) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String createMac(Uuid uuid) {
        String macAddress = uuid.getValue().replace("-", "").replaceAll("(.{2})", "$1:").substring(0, 17);
        return macAddress;
    }

    public static String createDatapathId() {
        String datapathId = "00:00:" + createMac();
        return datapathId;
    }

    public static Integer createPortNumber() {
        Random rand = new Random();
        int portNum = rand.nextInt(55001) + 10000;
        return new Integer(portNum);
    }

    public static BigInteger createOpenFlowDpnId(DatapathId datapathId) {
        if (datapathId != null) {
            String dpIdStr = datapathId.getValue().replace(":", "");
            BigInteger dpnId = new BigInteger(dpIdStr, 16);
            return dpnId;
        }
        return null;
    }

    public static String createIpPrefix(int idx) {
        // We only create Subnets of prefix /24
        String ipPrefix = BASE_IP + idx + ".0/" + DEFAULT_PREFIX_LEN;
        return ipPrefix;
    }

    public static String createIp(int seed) {
        String ip = BASE_IP + (seed / 253) + "." + ((seed % 253) + 1);
        return ip;
    }

    public static String createIp(String prefix, int index) {
        if (index >= 254) {
            return null;
        }
        String[] splitter = prefix.split(".0/");
        String ipAddr = splitter[0] + "." + (index + 1);
        return ipAddr;
    }

    public static String createRD(int index) {
        return (((index + 1) * 100) + ":1");
    }

    public static String createRT(int index) {
        return (((index + 1) * 10) + ":1");
    }
}
