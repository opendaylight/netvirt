/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import java.math.BigInteger;

public interface Ipv6ServiceConstants {

    String DHCPV6_OFF = "DHCPV6_OFF";
    String IPV6_SLAAC = "IPV6_SLAAC";
    String IPV6_DHCPV6_STATEFUL = "DHCPV6_STATEFUL";
    String IPV6_DHCPV6_STATELESS = "DHCPV6_STATELESS";
    String IPV6_AUTO_ADDRESS_SUBNETS = IPV6_SLAAC + IPV6_DHCPV6_STATELESS;

    String IP_VERSION_V4 = "IPv4";
    String IP_VERSION_V6 = "IPv6";
    String NETWORK_ROUTER_INTERFACE = "network:router_interface";
    String NETWORK_ROUTER_GATEWAY = "network:router_gateway";

    BigInteger INVALID_DPID = new BigInteger("-1");
    short DEFAULT_FLOW_PRIORITY = 50;
    short PUNT_NA_FLOW_PRIORITY = 40;
    String FLOWID_PREFIX = "IPv6.";
    String FLOWID_SEPARATOR = ".";

    int ADD_FLOW = 0;
    int DEL_FLOW = 1;
    int ADD_ENTRY = 0;
    int DEL_ENTRY = 1;
    int FLOWS_CONFIGURED = 1;
    int FLOWS_NOT_CONFIGURED = 0;
    String OPENFLOW_NODE_PREFIX = "openflow:";
    long DEF_FLOWLABEL = 0;
    //default periodic RA transmission interval. timeunit in sec
    long PERIODIC_RA_INTERVAL = 60;
    int ELAN_GID_MIN = 200000;
}
