/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.math.BigInteger;

/**
 * The class to have ACL related constants.
 */
public final class AclConstants {

    // TODO: Move all service related constants across all modules to a common
    // place
    public static final short EGRESS_ACL_TABLE_ID = 40;
    public static final short EGRESS_ACL_FILTER_TABLE_ID = 41;
    public static final short EGRESS_ACL_SERVICE_PRIORITY = 2;
    public static final short EGRESS_ACL_DEFAULT_FLOW_PRIORITY = 11;

    public static final short INGRESS_ACL_TABLE_ID = 251;
    public static final short INGRESS_ACL_FILTER_TABLE_ID = 252;
    public static final short INGRESS_ACL_SERVICE_PRIORITY = 6;
    public static final short INGRESS_ACL_DEFAULT_FLOW_PRIORITY = 1;

    // TODO: To be deleted once it is available in genius
    public static final short EGRESS_LPORT_DISPATCHER_TABLE = 220;

    public static final Integer PROTO_MATCH_PRIORITY = 61010;
    public static final Integer PREFIX_MATCH_PRIORITY = 61009;
    public static final Integer PROTO_PREFIX_MATCH_PRIORITY = 61008;
    public static final Integer PROTO_PORT_MATCH_PRIORITY = 61007;
    public static final Integer PROTO_PORT_PREFIX_MATCH_PRIORITY = 61007;
    public static final Integer PROTO_DHCP_SERVER_MATCH_PRIORITY = 61006;
    public static final Integer PROTO_VM_IP_MAC_MATCH_PRIORITY = 36001;
    public static final Integer CT_STATE_UNTRACKED_PRIORITY = 62030;
    public static final Integer CT_STATE_TRACKED_EXIST_PRIORITY = 62020;
    public static final Integer CT_STATE_TRACKED_NEW_PRIORITY = 62010;
    public static final Integer CT_STATE_NEW_PRIORITY_DROP = 36007;
    public static final short DHCP_CLIENT_PORT_IPV4 = 68;
    public static final short DHCP_SERVER_PORT_IPV4 = 67;
    public static final short DHCP_CLIENT_PORT_IPV6 = 568;
    public static final short DHCP_SERVER_PORT_IPV6 = 567;
    public static final BigInteger COOKIE_ACL_BASE = new BigInteger("6900000", 16);
    public static final int UNTRACKED_CT_STATE = 0x00;
    public static final int UNTRACKED_CT_STATE_MASK = 0x20;
    public static final int TRACKED_EST_CT_STATE = 0x22;
    public static final int TRACKED_REL_CT_STATE = 0x24;
    public static final int TRACKED_NEW_CT_STATE = 0x21;
    public static final int TRACKED_INV_CT_STATE = 0x30;
    public static final int TRACKED_INV_CT_STATE_MASK = 0x30;
    public static final int TRACKED_CT_STATE_MASK = 0x37;
    public static final int TRACKED_NEW_CT_STATE_MASK = 0x21;

    private AclConstants() {
    }
}
