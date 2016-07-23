/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;

public class VpnConstants {
    public static final String VPN_IDPOOL_NAME = "vpnservices";
    public static final long VPN_IDPOOL_START = 70000L;
    public static final String VPN_IDPOOL_SIZE = "100000";
    public static final short DEFAULT_FLOW_PRIORITY = 10;
    public static final int DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY = 1;
    public static final short L3VPN_SERVICE_IDENTIFIER = 2;
    public static final long INVALID_ID = -1;
    public static final String SEPARATOR = ".";
    public static final BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);
    public static final BigInteger COOKIE_L3_BASE = new BigInteger("8000000", 16);
    public static final String FLOWID_PREFIX = "L3.";
    public static final long MIN_WAIT_TIME_IN_MILLISECONDS = 5000;
    public static final long MAX_WAIT_TIME_IN_MILLISECONDS = 90000;
    public static final long PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS = 10000;
    public static final int ELAN_GID_MIN = 200000;
    public static final short ELAN_SMAC_TABLE = 50;
    public static final short LPORT_DISPATCHER_TABLE = 17;
    public static final short FIB_TABLE = 21;
    public static byte[] EthernetDestination_Broadcast = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
    public static byte[] MAC_Broadcast = new byte[] { (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0 };
    public enum ITMTunnelLocType {
        Invalid(0), Internal(1), External(2), Hwvtep(3);

        private final int type;
        ITMTunnelLocType(int id) { this.type = id; }
        public int getValue() { return type; }
    }
    public enum DCGWPresentStatus {
        Invalid(0), Present(1), Absent(2);

        private final int status;
        DCGWPresentStatus(int id) { this.status = id; }
        public int getValue() { return status; }
    }
}
