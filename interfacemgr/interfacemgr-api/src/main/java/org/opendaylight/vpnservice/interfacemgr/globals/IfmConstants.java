/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.globals;

import java.math.BigInteger;

public class IfmConstants {
    public static final String OF_URI_PREFIX = "openflow:";
    public static final String OF_URI_SEPARATOR = ":";
    public static final int DEFAULT_IFINDEX = 65536;
    public static final int FLOW_HIGH_PRIORITY = 10;
    public static final int FLOW_PRIORITY_FOR_UNTAGGED_VLAN = 4;
    public static final int FLOW_TABLE_MISS_PRIORITY = 0;
    public static final int DEFAULT_ARP_FLOW_PRIORITY = 100;
    public static final int INVALID_PORT_NO = -1;
    public static final BigInteger INVALID_DPID = new BigInteger("-1");
    //Id pool
    public static final String IFM_IDPOOL_NAME = "interfaces";
    public static final long IFM_ID_POOL_START = 1L;
    public static final long IFM_ID_POOL_END = 65535;
    //Group Prefix
    public static final long VLAN_GROUP_START = 1000;
    public static final long TRUNK_GROUP_START = 20000;
    public static final long LOGICAL_GROUP_START = 100000;
    //Table
    public static final short VLAN_INTERFACE_INGRESS_TABLE = 0;
    public static final short VXLAN_TRUNK_INTERFACE_TABLE = 10;
    public static final short TRUNK_L2_TABLE = 11;
    public static final short GRE_TRUNK_INTERFACE_TABLE = 12;
    public static final short LPORT_DISPATCHER_TABLE = 30;
    public static final short L3_INTERFACE_TABLE = 80;
    public static final long DELAY_TIME_IN_MILLISECOND = 10000;
    //Cookies
    public static final BigInteger COOKIE_VXLAN_TRUNK_L2_TABLE = new BigInteger("1200000", 16);
    public static final BigInteger COOKIE_GRE_TRUNK_L2_TABLE = new BigInteger("1400000", 16);
    public static final BigInteger COOKIE_L3_BASE = new BigInteger("8000000", 16);
    //Tunnel Monitoring
    public static final int DEFAULT_MONITOR_INTERVAL = 10000;
}
