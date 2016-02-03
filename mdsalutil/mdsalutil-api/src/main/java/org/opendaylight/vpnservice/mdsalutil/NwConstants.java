/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

public class NwConstants {

    // EthType Values
    public static final int ETHTYPE_802_1Q            = 0X8100;
    public static final int ETHTYPE_IPV4              = 0X0800;
    public static final int ETHTYPE_ARP               = 0X0806;

    public static final int ETHTYPE_MPLS_UC           = 0X8847;
    public static final int ETHTYPE_PBB               = 0X88E7;
    
    //Protocol Type
    public static final int IP_PROT_UDP = 17;
    public static final int IP_PROT_GRE = 47;

    //ARP TYPE
    public static final int ARP_REQUEST = 1;
    public static final int ARP_REPLY = 2;

    //Default Port
    public static final int UDP_DEFAULT_PORT = 4789;

    
    // Flow Actions
    public static final int ADD_FLOW = 0;
    public static final int DEL_FLOW = 1;
    public static final int MOD_FLOW = 2;

    // Flow Constants
    public static final String FLOWID_SEPARATOR = ".";
    public static final int TABLE_MISS_FLOW = 0;
    public static final int TABLE_MISS_PRIORITY = 0;

    public static final int DEFAULT_ARP_FLOW_PRIORITY = 100;

    //Table IDs
    public static final short L3_FIB_TABLE = 21;
    public static final short L3_LFIB_TABLE = 20;

    public static final short L3_PROTOCOL_TABLE = 36;
    public static final short L3_INTERFACE_TABLE = 80;
    public static final short LPORT_DISPATCHER_TABLE = 30;
    public static final short VLAN_INTERFACE_INGRESS_TABLE = 0;
    public static final short INTERNAL_TUNNEL_TABLE = 36;
    public static final short EXTERNAL_TUNNEL_TABLE = 36;

 }