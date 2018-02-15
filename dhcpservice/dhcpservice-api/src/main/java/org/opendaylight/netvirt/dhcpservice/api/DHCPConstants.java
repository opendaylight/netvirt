/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice.api;

public interface DHCPConstants {

    // DHCP BOOTP CODES
    byte BOOTREQUEST    = 1;
    byte BOOTREPLY      = 2;

    // DHCP HTYPE CODES
    byte HTYPE_ETHER    = 1;

    // DHCP MESSAGE CODES
    byte MSG_DISCOVER   =  1;
    byte MSG_OFFER      =  2;
    byte MSG_REQUEST    =  3;
    byte MSG_DECLINE    =  4;
    byte MSG_ACK        =  5;
    byte MSG_NAK        =  6;
    byte MSG_RELEASE    =  7;
    byte MSG_INFORM     =  8;
    byte MSG_FORCERENEW =  9;

    // DHCP OPTIONS CODE
    byte OPT_PAD                          =   0;
    byte OPT_SUBNET_MASK                  =   1;
    byte OPT_TIME_OFFSET                  =   2;
    byte OPT_ROUTERS                      =   3;
    byte OPT_TIME_SERVERS                 =   4;
    byte OPT_NAME_SERVERS                 =   5;
    byte OPT_DOMAIN_NAME_SERVERS          =   6;
    byte OPT_LOG_SERVERS                  =   7;
    byte OPT_COOKIE_SERVERS               =   8;
    byte OPT_LPR_SERVERS                  =   9;
    byte OPT_IMPRESS_SERVERS              =  10;
    byte OPT_RESOURCE_LOCATION_SERVERS    =  11;
    byte OPT_HOST_NAME                    =  12;
    byte OPT_BOOT_SIZE                    =  13;
    byte OPT_MERIT_DUMP                   =  14;
    byte OPT_DOMAIN_NAME                  =  15;
    byte OPT_SWAP_SERVER                  =  16;
    byte OPT_ROOT_PATH                    =  17;
    byte OPT_EXTENSIONS_PATH              =  18;
    byte OPT_IP_FORWARDING                =  19;
    byte OPT_NON_LOCAL_SOURCE_ROUTING     =  20;
    byte OPT_POLICY_FILTER                =  21;
    byte OPT_MAX_DGRAM_REASSEMBLY         =  22;
    byte OPT_DEFAULT_IP_TTL               =  23;
    byte OPT_PATH_MTU_AGING_TIMEOUT       =  24;
    byte OPT_PATH_MTU_PLATEAU_TABLE       =  25;
    byte OPT_INTERFACE_MTU                =  26;
    byte OPT_ALL_SUBNETS_LOCAL            =  27;
    byte OPT_BROADCAST_ADDRESS            =  28;
    byte OPT_PERFORM_MASK_DISCOVERY       =  29;
    byte OPT_MASK_SUPPLIER                =  30;
    byte OPT_ROUTER_DISCOVERY             =  31;
    byte OPT_ROUTER_SOLICITATION_ADDRESS  =  32;
    byte OPT_STATIC_ROUTES                =  33;
    byte OPT_TRAILER_ENCAPSULATION        =  34;
    byte OPT_ARP_CACHE_TIMEOUT            =  35;
    byte OPT_IEEE802_3_ENCAPSULATION      =  36;
    byte OPT_DEFAULT_TCP_TTL              =  37;
    byte OPT_TCP_KEEPALIVE_INTERVAL       =  38;
    byte OPT_TCP_KEEPALIVE_GARBAGE        =  39;
    byte OPT_NIS_SERVERS                  =  41;
    byte OPT_NTP_SERVERS                  =  42;
    byte OPT_VENDOR_ENCAPSULATED_OPTIONS  =  43;
    byte OPT_NETBIOS_NAME_SERVERS         =  44;
    byte OPT_NETBIOS_DD_SERVER            =  45;
    byte OPT_NETBIOS_NODE_TYPE            =  46;
    byte OPT_NETBIOS_SCOPE                =  47;
    byte OPT_FONT_SERVERS                 =  48;
    byte OPT_X_DISPLAY_MANAGER            =  49;
    byte OPT_REQUESTED_ADDRESS            =  50;
    byte OPT_LEASE_TIME                   =  51;
    byte OPT_OPTION_OVERLOAD              =  52;
    byte OPT_MESSAGE_TYPE                 =  53;
    byte OPT_SERVER_IDENTIFIER            =  54;
    byte OPT_PARAMETER_REQUEST_LIST       =  55;
    byte OPT_MESSAGE                      =  56;
    byte OPT_MAX_MESSAGE_SIZE             =  57;
    byte OPT_RENEWAL_TIME                 =  58;
    byte OPT_REBINDING_TIME               =  59;
    byte OPT_VENDOR_CLASS_IDENTIFIER      =  60;
    byte OPT_CLIENT_IDENTIFIER            =  61;
    byte OPT_NWIP_DOMAIN_NAME             =  62;
    byte OPT_NWIP_SUBOPTIONS              =  63;
    byte OPT_NISPLUS_DOMAIN               =  64;
    byte OPT_NISPLUS_SERVER               =  65;
    byte OPT_TFTP_SERVER                  =  66;
    byte OPT_BOOTFILE                     =  67;
    byte OPT_MOBILE_IP_HOME_AGENT         =  68;
    byte OPT_SMTP_SERVER                  =  69;
    byte OPT_POP3_SERVER                  =  70;
    byte OPT_NNTP_SERVER                  =  71;
    byte OPT_WWW_SERVER                   =  72;
    byte OPT_FINGER_SERVER                =  73;
    byte OPT_IRC_SERVER                   =  74;
    byte OPT_STREETTALK_SERVER            =  75;
    byte OPT_STDA_SERVER                  =  76;
    byte OPT_USER_CLASS                   =  77;
    byte OPT_FQDN                         =  81;
    byte OPT_AGENT_OPTIONS                =  82;
    byte OPT_NDS_SERVERS                  =  85;
    byte OPT_NDS_TREE_NAME                =  86;
    byte OPT_NDS_CONTEXT                  =  87;
    byte OPT_CLIENT_LAST_TRANSACTION_TIME =  91;
    byte OPT_ASSOCIATED_IP                =  92;
    byte OPT_USER_AUTHENTICATION_PROTOCOL =  98;
    byte OPT_AUTO_CONFIGURE               = 116;
    byte OPT_NAME_SERVICE_SEARCH          = 117;
    byte OPT_SUBNET_SELECTION             = 118;
    byte OPT_DOMAIN_SEARCH                = 119;
    byte OPT_CLASSLESS_ROUTE              = 121;
    byte OPT_END                          =  -1;

    int MAGIC_COOKIE = 0x63825363;

    int DHCP_MIN_SIZE        = 300;
    int DHCP_MAX_SIZE        = 576;

    int DHCP_NOOPT_HDR_SIZE        = 240;
}
