/*
 * Copyright (c) 2013 Ericsson AB.  All rights reserved.
 *
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

    //Default Port
    public static final int UDP_DEFAULT_PORT = 4789;

    // Table IDs
    public static final short PRECHECK_TABLE          = 0;
    public static final short PORT_VLAN_TABLE         = 0;

    // Table Max Entries
    public static final long INGRESS_TABLE_MAX_ENTRY  = 1000;
    public static final long PRECHECK_TABLE_MAX_ENTRY = 100;

    // Flow Actions
    public static final int ADD_FLOW = 0;
    public static final int DEL_FLOW = 1;
    public static final int MOD_FLOW = 2;

    // Flow Constants
    public static final String FLOWID_SEPARATOR = ".";
    public static final int TABLE_MISS_FLOW = 0;
    public static final int TABLE_MISS_PRIORITY = 0;

    // Misc FIXME: Find new place for this
    public static final String DPN_STATE_CACHE = "dpn.state.cache";
    public static final String DPN_SYNCSTATUS_CACHE = "dpn.resync.status.cache";
    public static final String STATISTICS_LOCK_PREFIX ="scf.statistics.lock";
    public static final String STATISTICS_LOCK_SEPARATOR =".";
    public static final int STATISTICS_LOCK_RETRY_COUNT =1800;
}