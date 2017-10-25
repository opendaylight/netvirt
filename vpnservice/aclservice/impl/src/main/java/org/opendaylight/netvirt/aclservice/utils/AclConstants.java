/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The class to have ACL related constants.
 */
public interface AclConstants {

    short INGRESS_ACL_DEFAULT_FLOW_PRIORITY = 1;
    short EGRESS_ACL_DEFAULT_FLOW_PRIORITY = 11;

    Integer PROTO_IPV6_DROP_PRIORITY = 63020;
    Integer PROTO_IPV6_ALLOWED_PRIORITY = 63010;
    Integer PROTO_DHCP_SERVER_MATCH_PRIORITY = 63010;
    Integer PROTO_DHCP_CLIENT_TRAFFIC_MATCH_PRIORITY = 63010;
    Integer PROTO_ARP_TRAFFIC_MATCH_PRIORITY = 63010;
    Integer PROTO_ARP_TRAFFIC_DROP_PRIORITY = 63009;
    Integer PROTO_L2BROADCAST_TRAFFIC_MATCH_PRIORITY = 61005;
    Integer PROTO_MATCH_PRIORITY = 61010;
    Integer PROTO_IP_TRAFFIC_DROP_PRIORITY = 61009;
    Integer PROTO_PREFIX_MATCH_PRIORITY = 61008;
    Integer PROTO_PORT_MATCH_PRIORITY = 61007;
    Integer PROTO_PORT_PREFIX_MATCH_PRIORITY = 61007;
    Integer PROTO_MATCH_SYN_ALLOW_PRIORITY = 61005;
    Integer PROTO_MATCH_SYN_ACK_ALLOW_PRIORITY = 61004;
    Integer PROTO_MATCH_SYN_DROP_PRIORITY = 61003;
    Integer PROTO_VM_IP_MAC_MATCH_PRIORITY = 36001;
    Integer CT_STATE_UNTRACKED_PRIORITY = 62030;
    Integer CT_STATE_TRACKED_EXIST_PRIORITY = 62020;
    Integer CT_STATE_TRACKED_INVALID_PRIORITY = 62015;
    Integer CT_STATE_TRACKED_NEW_PRIORITY = 62010;
    Integer CT_STATE_TRACKED_NEW_DROP_PRIORITY = 50;
    Integer NO_PRIORITY = 50;

    short DHCP_CLIENT_PORT_IPV4 = 68;
    short DHCP_SERVER_PORT_IPV4 = 67;
    short DHCP_CLIENT_PORT_IPV6 = 546;
    short DHCP_SERVER_PORT_IPV6 = 547;

    BigInteger COOKIE_ACL_BASE = new BigInteger("6900000", 16);
    BigInteger COOKIE_ACL_DROP_FLOW = new BigInteger("6900001", 16);

    int TRACKED_EST_CT_STATE = 0x22;
    int TRACKED_REL_CT_STATE = 0x24;
    int TRACKED_NEW_CT_STATE = 0x21;
    int TRACKED_INV_CT_STATE = 0x30;

    int TRACKED_EST_CT_STATE_MASK = 0x37;
    int TRACKED_REL_CT_STATE_MASK = 0x37;
    int TRACKED_NEW_CT_STATE_MASK = 0x21;
    int TRACKED_INV_CT_STATE_MASK = 0x30;

    String IPV4_ALL_NETWORK = "0.0.0.0/0";
    String IPV6_ALL_NETWORK = "::/0";
    String BROADCAST_MAC = "ff:ff:ff:ff:ff:ff";
    String IPV4_ALL_SUBNET_BROADCAST_ADDR = "255.255.255.255";

    long TCP_FLAG_SYN = 1 << 1;
    long TCP_FLAG_ACK = 1 << 4;
    long TCP_FLAG_SYN_ACK = TCP_FLAG_SYN + TCP_FLAG_ACK;
    int ALL_LAYER4_PORT = 65535;
    int ALL_LAYER4_PORT_MASK = 0x0000;

    Short IP_PROT_ICMPV6 = 58;
    int ICMPV6_TYPE_MLD_QUERY = 130;
    int ICMPV6_TYPE_RS = 133;
    int ICMPV6_TYPE_RA = 134;
    int ICMPV6_TYPE_NS = 135;
    int ICMPV6_TYPE_NA = 136;
    int ICMPV6_TYPE_MLD2_REPORT = 143;

    String SECURITY_GROUP_TCP_IDLE_TO_KEY = "security-group-tcp-idle-timeout";
    String SECURITY_GROUP_TCP_HARD_TO_KEY = "security-group-tcp-hard-timeout";
    String SECURITY_GROUP_TCP_FIN_IDLE_TO_KEY = "security-group-tcp-fin-idle-timeout";
    String SECURITY_GROUP_TCP_FIN_HARD_TO_KEY = "security-group-tcp-fin-hard-timeout";
    String SECURITY_GROUP_UDP_IDLE_TO_KEY = "security-group-udp-idle-timeout";
    String SECURITY_GROUP_UDP_HARD_TO_KEY = "security-group-udp-hard-timeout";

    String ACL_FLOW_PRIORITY_POOL_NAME = "acl.flow.priorities.pool";
    long ACL_FLOW_PRIORITY_LOW_POOL_START = 1000L;
    long ACL_FLOW_PRIORITY_LOW_POOL_END = 30000L;
    long ACL_FLOW_PRIORITY_HIGH_POOL_START = 30001L;
    long ACL_FLOW_PRIORITY_HIGH_POOL_END = 60000L;
    long ACL_ID_METADATA_POOL_START = 1L;
    long ACL_ID_METADATA_POOL_END = 10000L;

    int SOURCE_LOWER_PORT_UNSPECIFIED = -1;
    int SOURCE_UPPER_PORT_UNSPECIFIED = -1;
    int DEST_LOWER_PORT_UNSPECIFIED = -1;
    int DEST_UPPER_PORT_UNSPECIFIED = -1;
    int DEST_LOWER_PORT_HTTP = 80;
    int DEST_LOWER_PORT_2 = 2;
    int DEST_UPPER_PORT_3 = 3;
    int DEST_UPPER_PORT_HTTP = 80;
    int SOURCE_REMOTE_IP_PREFIX_SPECIFIED = 1;
    int SOURCE_REMOTE_IP_PREFIX_UNSPECIFIED = -1;
    int DEST_REMOTE_IP_PREFIX_SPECIFIED = 1;
    int DEST_REMOTE_IP_PREFIX_UNSPECIFIED = -1;
    int INVALID_ACL_ID = -1;
    short EGRESS_ACL_DUMMY_TABLE = 239;
    int TRACKED_CT_STATE = 0x20;
    int TRACKED_CT_STATE_MASK = 0x20;

    String ACL_ID_POOL_NAME = "ACL-ID-POOL";
    String ACL_SYNC_KEY_EXT = "-acl";

    enum PacketHandlingType {
        PERMIT,
        DENY
    }

    static List<Integer> allowedIcmpv6NdList() {
        List<Integer> icmpv6NdList = new ArrayList<>();
        icmpv6NdList.add(ICMPV6_TYPE_RS);
        icmpv6NdList.add(ICMPV6_TYPE_NS);
        icmpv6NdList.add(ICMPV6_TYPE_NA);
        return icmpv6NdList;
    }
}
