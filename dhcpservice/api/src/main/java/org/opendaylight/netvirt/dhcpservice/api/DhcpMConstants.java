/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice.api;

import org.opendaylight.yangtools.yang.common.Uint64;

public interface DhcpMConstants {

    long DHCP_TABLE_MAX_ENTRY = 10000;

    int DEFAULT_DHCP_FLOW_PRIORITY = 50;
    int DEFAULT_DHCP_ALLOCATION_POOL_FLOW_PRIORITY = DEFAULT_DHCP_FLOW_PRIORITY - 1;
    int DEFAULT_DHCP_ARP_FLOW_PRIORITY = 10;
    int ARP_FLOW_PRIORITY = 50;
    short DEFAULT_FLOW_PRIORITY = 100;

    Uint64 COOKIE_DHCP_BASE = Uint64.valueOf("6800000", 16).intern();
    Uint64 METADATA_ALL_CLEAR_MASK = Uint64.valueOf("0000000000000000", 16).intern();
    Uint64 METADATA_ALL_SET_MASK = Uint64.valueOf("FFFFFFFFFFFFFFFF", 16).intern();

    String FLOWID_PREFIX = "DHCP.";
    String VMFLOWID_PREFIX = "DHCP.INTERFACE.";
    String BCAST_DEST_IP = "255.255.255.255";
    int BCAST_IP = 0xffffffff;

    short DHCP_CLIENT_PORT = 68;
    short DHCP_SERVER_PORT = 67;

    int DEFAULT_LEASE_TIME = 86400;
    String DEFAULT_DOMAIN_NAME = "openstacklocal";

    Uint64 COOKIE_VM_INGRESS_TABLE = Uint64.valueOf("6800001", 16).intern();
    Uint64 INVALID_DPID = Uint64.valueOf("-1").intern();
    String DHCP_JOB_KEY_PREFIX = "DHCP_";
    int RETRY_COUNT = 6;
}
