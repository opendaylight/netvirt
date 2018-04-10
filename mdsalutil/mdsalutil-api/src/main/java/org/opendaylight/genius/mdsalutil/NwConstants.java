/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.mdsalutil;

import java.math.BigInteger;

public interface NwConstants {

    // EthType Values
    int ETHTYPE_802_1Q            = 0X8100;
    int ETHTYPE_IPV4              = 0X0800;
    int ETHTYPE_IPV6              = 0x86dd;
    int ETHTYPE_ARP               = 0X0806;

    int ETHTYPE_MPLS_UC           = 0X8847;
    int ETHTYPE_PBB               = 0X88E7;

    String MACADDR_SEP = ":";

    String IPV4PREFIX = "/32";
    String IPV6PREFIX = "/128";
    String IPV4_SEP = ".";
    String IPV6_SEP = ":";

    //Protocol Type
    int IP_PROT_ICMP = 1;
    int IP_PROT_TCP = 6;
    int IP_PROT_UDP = 17;
    int IP_PROT_GRE = 47;

    //ARP TYPE
    int ARP_REQUEST = 1;
    int ARP_REPLY = 2;

    //Default Port
    int UDP_DEFAULT_PORT = 4789;


    // Flow Actions
    int ADD_FLOW = 0;
    int DEL_FLOW = 1;
    int MOD_FLOW = 2;

    // Flow Constants
    String FLOWID_SEPARATOR = ".";
    int TABLE_MISS_FLOW = 0;
    int TABLE_MISS_PRIORITY = 0;

    int DEFAULT_ARP_FLOW_PRIORITY = 100;

    // Ingress (w.r.t switch) service indexes
    short DEFAULT_SERVICE_INDEX = 0;
    short SFC_SERVICE_INDEX = 1;
    short SCF_SERVICE_INDEX = 1;
    short ACL_SERVICE_INDEX = 2;
    short INGRESS_COUNTERS_SERVICE_INDEX = 3;
    short SFC_CLASSIFIER_INDEX = 4;
    short DHCP_SERVICE_INDEX = 5;
    short QOS_SERVICE_INDEX = 6;
    short IPV6_SERVICE_INDEX = 7;
    short COE_KUBE_PROXY_SERVICE_INDEX = 8;
    short L3VPN_SERVICE_INDEX = 9;
    short L3_VPNV6_SERVICE_INDEX = 10;
    short ELAN_SERVICE_INDEX = 11;

    String DHCP_SERVICE_NAME = "DHCP_SERVICE";
    String ACL_SERVICE_NAME = "ACL_SERVICE";
    String QOS_SERVICE_NAME = "QOS_SERVICE";
    String IPV6_SERVICE_NAME = "IPV6_SERVICE";
    String SCF_SERVICE_NAME = "SCF_SERVICE";
    String SFC_SERVICE_NAME = "SFC_SERVICE";
    String L3VPN_SERVICE_NAME = "L3VPN_SERVICE";
    String ELAN_SERVICE_NAME = "ELAN_SERVICE";
    String SFC_CLASSIFIER_SERVICE_NAME = "SFC_CLASSIFIER_SERVICE";
    String DEFAULT_EGRESS_SERVICE_NAME = "DEFAULT_EGRESS_SERVICE";
    String L3_VPNV6_SERVICE_NAME = "VPNV6 SERVICE";
    String COE_KUBE_PROXY_SERVICE_NAME = "COE_KUBE_PROXY_SERVICE";

    // Egress (w.r.t switch) service indexes
    short EGRESS_ACL_SERVICE_INDEX = 6;
    short EGRESS_POLICY_SERVICE_INDEX = 6;
    short EGRESS_COUNTERS_SERVICE_INDEX = 7;
    short EGRESS_SFC_CLASSIFIER_SERVICE_INDEX = 8;
    short DEFAULT_EGRESS_SERVICE_INDEX = 9;

    String EGRESS_ACL_SERVICE_NAME = "EGRESS_ACL_SERVICE";
    String EGRESS_POLICY_SERVICE_NAME = "EGRESS_POLICY_SERVICE";
    String EGRESS_SFC_CLASSIFIER_SERVICE_NAME = "EGRESS_SFC_CLASSIFIER_SERVICE";
    String INGRESS_COUNTERS_SERVICE_NAME = "INGRESS_COUNTERS_SERVICE";
    String EGRESS_COUNTERS_SERVICE_NAME = "EGRESS_COUNTERS_SERVICE";

    BigInteger COOKIE_IPV6_TABLE = new BigInteger("4000000", 16);
    BigInteger COOKIE_QOS_TABLE = new BigInteger("4000001", 16);
    BigInteger VLAN_TABLE_COOKIE = new BigInteger("8000000", 16);
    BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);
    BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000002", 16);
    BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
    BigInteger COOKIE_DNAT_TABLE = new BigInteger("8000004", 16);
    BigInteger COOKIE_TS_TABLE = new BigInteger("8000005", 16);
    BigInteger COOKIE_SNAT_TABLE = new BigInteger("8000006", 16);
    BigInteger EGRESS_DISPATCHER_TABLE_COOKIE = new BigInteger("8000007", 16);
    BigInteger COOKIE_OUTBOUND_NAPT_TABLE = new BigInteger("8000008", 16);
    BigInteger COOKIE_L3_GW_MAC_TABLE = new BigInteger("8000009", 16);
    BigInteger EGRESS_POLICY_CLASSIFIER_COOKIE = new BigInteger("8000230", 16);
    BigInteger EGRESS_POLICY_ROUTING_COOKIE = new BigInteger("8000231", 16);
    BigInteger COOKIE_VXLAN_TRUNK_L2_TABLE = new BigInteger("1200000", 16);
    BigInteger COOKIE_GRE_TRUNK_L2_TABLE = new BigInteger("1400000", 16);
    BigInteger COOKIE_ELAN_INGRESS_TABLE = new BigInteger("8040000", 16);
    BigInteger TUNNEL_TABLE_COOKIE = new BigInteger("9000000", 16);
    BigInteger COOKIE_ARP_RESPONDER = new BigInteger("8220000", 16);
    BigInteger COOKIE_COE_KUBE_PROXY_TABLE = new BigInteger("8230000", 16);

    //Table IDs
    short VLAN_INTERFACE_INGRESS_TABLE = 0;
    short VXLAN_TRUNK_INTERFACE_TABLE = 10;
    short TRUNK_L2_TABLE = 11;
    short GRE_TRUNK_INTERFACE_TABLE = 12;
    short LPORT_DISPATCHER_TABLE = 17;
    short DHCP_TABLE_EXTERNAL_TUNNEL = 18;
    short L3_GW_MAC_TABLE = 19;
    short L3_LFIB_TABLE = 20;
    short L3_FIB_TABLE = 21;
    short L3_SUBNET_ROUTE_TABLE = 22;
    short L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE = 23;
    short L2VNI_EXTERNAL_TUNNEL_DEMUX_TABLE = 24;
    short PDNAT_TABLE = 25;
    short PSNAT_TABLE = 26;
    short DNAT_TABLE = 27;
    short SNAT_TABLE = 28;
    short INTERNAL_TUNNEL_TABLE = 36;
    short EXTERNAL_TUNNEL_TABLE = 38;
    short ARP_CHECK_TABLE = 43;
    short INBOUND_NAPT_TABLE = 44;
    short IPV6_TABLE = 45;
    short OUTBOUND_NAPT_TABLE = 46;
    short NAPT_PFIB_TABLE = 47;
    short ELAN_BASE_TABLE = 48;
    short ELAN_SMAC_LEARNED_TABLE = 49;
    short ELAN_SMAC_TABLE = 50;
    short ELAN_DMAC_TABLE = 51;
    short ELAN_UNKNOWN_DMAC_TABLE = 52;
    short ELAN_FILTER_EQUALS_TABLE = 55;
    short DHCP_TABLE = 60;
    short SCF_UP_SUB_FILTER_TCP_BASED_TABLE = 70;
    short SCF_DOWN_SUB_FILTER_TCP_BASED_TABLE = 72;
    short SCF_CHAIN_FWD_TABLE = 75;
    short L3_INTERFACE_TABLE = 80;
    short ARP_RESPONDER_TABLE = 81;
    short SFC_TRANSPORT_CLASSIFIER_TABLE = 82;
    short SFC_TRANSPORT_INGRESS_TABLE = 83;
    short SFC_TRANSPORT_PATH_MAPPER_TABLE = 84;
    short SFC_TRANSPORT_PATH_MAPPER_ACL_TABLE = 85;
    short SFC_TRANSPORT_NEXT_HOP_TABLE = 86;
    short SFC_TRANSPORT_EGRESS_TABLE = 87;
    short QOS_DSCP_TABLE = 90;
    short INGRESS_SFC_CLASSIFIER_FILTER_TABLE = 100;
    short INGRESS_SFC_CLASSIFIER_ACL_TABLE = 101;
    short COE_KUBE_PROXY_TABLE = 180;
    short INGRESS_ACL_ANTI_SPOOFING_TABLE = 210;
    short INGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE = 211;
    short INGRESS_ACL_CONNTRACK_SENDER_TABLE = 212;
    short INGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE = 213;
    short INGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE = 214;
    short INGRESS_ACL_RULE_BASED_FILTER_TABLE = 215;
    short INGRESS_REMOTE_ACL_TABLE = 216;
    short INGRESS_ACL_COMMITTER_TABLE = 217;
    short INGRESS_COUNTERS_TABLE = 219;
    short EGRESS_LPORT_DISPATCHER_TABLE = 220;
    short EGRESS_SFC_CLASSIFIER_FILTER_TABLE = 221;
    short EGRESS_SFC_CLASSIFIER_NEXTHOP_TABLE = 222;
    short EGRESS_SFC_CLASSIFIER_EGRESS_TABLE = 223;
    short EGRESS_POLICY_CLASSIFIER_TABLE = 230;
    short EGRESS_POLICY_ROUTING_TABLE = 231;
    short EGRESS_ACL_DUMMY_TABLE = 239;
    short EGRESS_ACL_ANTI_SPOOFING_TABLE = 240;
    short EGRESS_ACL_CONNTRACK_CLASSIFIER_TABLE = 241;
    short EGRESS_ACL_CONNTRACK_SENDER_TABLE = 242;
    short EGRESS_ACL_FOR_EXISTING_TRAFFIC_TABLE = 243;
    short EGRESS_ACL_FILTER_CUM_DISPATCHER_TABLE = 244;
    short EGRESS_ACL_RULE_BASED_FILTER_TABLE = 245;
    short EGRESS_REMOTE_ACL_TABLE = 246;
    short EGRESS_ACL_COMMITTER_TABLE = 247;
    short EGRESS_COUNTERS_TABLE = 249;

    enum NxmOfFieldType {
        NXM_OF_IN_PORT(0x0000, 0, 2, 16),
        NXM_OF_ETH_DST(0x0000, 1, 6, 48),
        NXM_OF_ETH_SRC(0x0000, 2, 6, 48),
        NXM_OF_ETH_TYPE(0x0000, 3, 2, 16),
        NXM_OF_VLAN_TCI(0x0000, 4, 2, 12),
        NXM_OF_IP_TOS(0x0000, 5, 1, 8),
        NXM_OF_IP_PROTO(0x0000, 6, 1, 8),
        NXM_OF_IP_SRC(0x0000, 7, 4, 32),
        NXM_OF_IP_DST(0x0000, 8, 4, 32),
        NXM_OF_TCP_SRC(0x0000, 9, 2, 16),
        NXM_OF_TCP_DST(0x0000, 10, 2, 16),
        NXM_OF_UDP_SRC(0x0000, 11, 2, 16),
        NXM_OF_UDP_DST(0x0000, 12, 2, 16),
        NXM_OF_ICMP_TYPE(0x0000, 13, 1, 8),
        NXM_OF_ICMP_CODE(0x0000, 14, 1, 8),
        NXM_OF_ARP_OP(0x0000, 15, 2, 16),
        NXM_OF_ARP_SPA(0x0000, 16, 4, 16),
        NXM_OF_ARP_TPA(0x0000, 17, 4, 16),
        NXM_NX_REG0(0x0001, 0, 4, -1),
        NXM_NX_REG1(0x0001, 1, 4, -1),
        NXM_NX_REG2(0x0001, 2, 4, -1),
        NXM_NX_REG3(0x0001, 3, 4, -1),
        NXM_NX_REG4(0x0001, 4, 4, -1),
        NXM_NX_REG5(0x0001, 5, 4, -1),
        NXM_NX_REG6(0x0001, 6, 4, -1),
        NXM_NX_REG7(0x0001, 7, 4, -1);

        long type;
        int flowModHeaderLen;

        NxmOfFieldType(long vendor, long field, long length, int flowModHeaderLen) {
            type = nxmHeader(vendor, field, length);
            this.flowModHeaderLen = flowModHeaderLen;
        }

        private static long nxmHeader(long vendor, long field, long length) {
            return vendor << 16 | field << 9 | length;
        }

        @Deprecated
        public String getHexType() {
            return String.valueOf(type);
        }

        public long getType() {
            return type;
        }

        @Deprecated
        public String getFlowModHeaderLen() {
            return String.valueOf(flowModHeaderLen);
        }

        public int getFlowModHeaderLenInt() {
            return flowModHeaderLen;
        }
    }
}
