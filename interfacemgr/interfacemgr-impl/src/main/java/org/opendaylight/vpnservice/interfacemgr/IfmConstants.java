/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr;

import java.math.BigInteger;

public class IfmConstants {
    public static final String IFM_IDPOOL_NAME = "interfaces";
    public static final long IFM_ID_POOL_START = 1L;
    public static final long IFM_ID_POOL_END = 65535;
    public static final String IFM_IDPOOL_SIZE = "65535";
    public static final String OF_URI_PREFIX = "openflow:";
    public static final String OF_URI_SEPARATOR = ":";
    public static final int DEFAULT_IFINDEX = 65536;
    public static final int DEFAULT_FLOW_PRIORITY = 5;
    public static final String IFM_LPORT_TAG_IDPOOL_NAME = "vlaninterfaces.lporttag";
    public static final short VLAN_INTERFACE_INGRESS_TABLE = 0;
    //Group Prefix
    public static final long VLAN_GROUP_START = 1000;
    public static final long TRUNK_GROUP_START = 20000;
    public static final long LOGICAL_GROUP_START = 100000;
    public static final short INTERNAL_TUNNEL_TABLE = 22;
    public static final short EXTERNAL_TUNNEL_TABLE = 23;
    public static final short LFIB_TABLE = 20;
    public static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000002", 16);
    public static final String TUNNEL_TABLE_FLOWID_PREFIX = "TUNNEL.";
    public static final BigInteger TUNNEL_TABLE_COOKIE = new BigInteger("9000000", 16);
    public static final short DEFAULT_SERVICE_INDEX = 0;
    public static final int FLOW_HIGH_PRIORITY = 10;
    public static final int FLOW_PRIORITY_FOR_UNTAGGED_VLAN = 4;
    public static final BigInteger VLAN_TABLE_COOKIE = new BigInteger("8000000", 16);
}
