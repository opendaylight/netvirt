/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice.api;

import java.math.BigInteger;

public interface DhcpMConstants {

    long DHCP_TABLE_MAX_ENTRY = 10000;

    int DEFAULT_DHCP_FLOW_PRIORITY = 50;
    int DEFAULT_DHCP_ALLOCATION_POOL_FLOW_PRIORITY = DEFAULT_DHCP_FLOW_PRIORITY - 1;
    int ARP_FLOW_PRIORITY = 50;
    short DEFAULT_FLOW_PRIORITY = 100;

    BigInteger COOKIE_DHCP_BASE = new BigInteger("6800000", 16);
    BigInteger METADATA_ALL_CLEAR_MASK = new BigInteger("0000000000000000", 16);
    BigInteger METADATA_ALL_SET_MASK = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    String FLOWID_PREFIX = "DHCP.";
    String VMFLOWID_PREFIX = "DHCP.INTERFACE.";
    String BCAST_DEST_IP = "255.255.255.255";
    int BCAST_IP = 0xffffffff;

    short DHCP_CLIENT_PORT = 68;
    short DHCP_SERVER_PORT = 67;

    int DEFAULT_LEASE_TIME = 86400;
    String DEFAULT_DOMAIN_NAME = "openstacklocal";

    BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("6800001", 16);
    BigInteger INVALID_DPID = new BigInteger("-1");
    String DHCP_JOB_KEY_PREFIX = "DHCP_";
    int RETRY_COUNT = 6;
}
