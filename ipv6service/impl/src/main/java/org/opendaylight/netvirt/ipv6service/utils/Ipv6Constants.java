/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import java.math.BigInteger;

public interface Ipv6Constants {

    short IP_VERSION_6 = 6;
    int IP_V6_ETHTYPE = 0x86DD;
    int ICMP_V6 = 1;

    int ETHTYPE_START = 96;
    int ONE_BYTE  = 8;
    int TWO_BYTES = 16;
    int IP_V6_HDR_START = 112;
    int IP_V6_NEXT_HDR = 48;
    int ICMPV6_HDR_START = 432;

    int ICMPV6_RA_LENGTH_WO_OPTIONS = 16;
    int ICMPV6_OPTION_SOURCE_LLA_LENGTH = 8;
    int ICMPV6_OPTION_PREFIX_LENGTH = 32;

    int IPV6_DEFAULT_HOP_LIMIT = 64;
    int IPV6_ROUTER_LIFETIME = 4500;
    int IPV6_RA_VALID_LIFETIME = 2592000;
    int IPV6_RA_PREFERRED_LIFETIME = 604800;
    int IPV6_RA_REACHABLE_TIME = 120000;

    short ICMP_V6_TYPE = 58;
    short ICMP_V6_RS_CODE = 133;
    short ICMP_V6_RA_CODE = 134;
    short ICMP_V6_NS_CODE = 135;
    short ICMP_V6_NA_CODE = 136;
    short ICMP_V6_MAX_HOP_LIMIT = 255;
    int ICMPV6_OFFSET = 54;

    short ICMP_V6_OPTION_SOURCE_LLA = 1;
    short ICMP_V6_OPTION_TARGET_LLA = 2;

    String DHCPV6_OFF = "DHCPV6_OFF";
    String IPV6_SLAAC = "IPV6_SLAAC";
    String IPV6_DHCPV6_STATEFUL = "DHCPV6_STATEFUL";
    String IPV6_DHCPV6_STATELESS = "DHCPV6_STATELESS";
    String IPV6_AUTO_ADDRESS_SUBNETS = IPV6_SLAAC + IPV6_DHCPV6_STATELESS;

    String IP_VERSION_V4 = "IPv4";
    String IP_VERSION_V6 = "IPv6";
    String NETWORK_ROUTER_INTERFACE = "network:router_interface";
    String NETWORK_ROUTER_GATEWAY = "network:router_gateway";
    String DEVICE_OWNER_DHCP = "network:dhcp";

    BigInteger INVALID_DPID = new BigInteger("-1");
    short DEFAULT_FLOW_PRIORITY = 50;
    String FLOWID_PREFIX = "IPv6.";
    String FLOWID_SEPARATOR = ".";

    int ADD_FLOW = 0;
    int DEL_FLOW = 1;
    int ADD_ENTRY = 0;
    int DEL_ENTRY = 1;
    int FLOWS_CONFIGURED = 1;
    int FLOWS_NOT_CONFIGURED = 0;
    String OPENFLOW_NODE_PREFIX = "openflow:";
    short IPV6_VERSION = 6;
    short ICMP6_NHEADER = 58;
    long DEF_FLOWLABEL = 0;
    String DEF_MCAST_MAC = "33:33:00:00:00:01";
    //default periodic RA transmission interval. timeunit in sec
    long PERIODIC_RA_INTERVAL = 60;
    int ELAN_GID_MIN = 200000;

    enum Ipv6RtrAdvertType {
        UNSOLICITED_ADVERTISEMENT,
        SOLICITED_ADVERTISEMENT,
        CEASE_ADVERTISEMENT
    }
}
