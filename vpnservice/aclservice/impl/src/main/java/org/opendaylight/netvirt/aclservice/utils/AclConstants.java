/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.ovsdb.utils.config.ConfigProperties;

/**
 * The class to have ACL related constants.
 */
public final class AclConstants {

    // TODO: To be deleted once it is available in NwConstants
    public static final short EGRESS_LPORT_DISPATCHER_TABLE = 220;
    // TODO: To be moved to NwConstants
    public static final short INGRESS_ACL_SERVICE_PRIORITY = 6;

    public static final short INGRESS_ACL_DEFAULT_FLOW_PRIORITY = 1;
    public static final short EGRESS_ACL_DEFAULT_FLOW_PRIORITY = 11;

    public static final Integer PROTO_MATCH_PRIORITY = 61010;
    public static final Integer PREFIX_MATCH_PRIORITY = 61009;
    public static final Integer PROTO_PREFIX_MATCH_PRIORITY = 61008;
    public static final Integer PROTO_PORT_MATCH_PRIORITY = 61007;
    public static final Integer PROTO_PORT_PREFIX_MATCH_PRIORITY = 61007;
    public static final Integer PROTO_DHCP_SERVER_MATCH_PRIORITY = 61006;
    public static final Integer PROTO_MATCH_SYN_ALLOW_PRIORITY = 61005;
    public static final Integer PROTO_MATCH_SYN_ACK_ALLOW_PRIORITY = 61004;
    public static final Integer PROTO_MATCH_SYN_DROP_PRIORITY = 61003;
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
    public static final int TRACKED_CT_STATE = 0x20;
    public static final int TRACKED_EST_CT_STATE = 0x22;
    public static final int TRACKED_REL_CT_STATE = 0x24;
    public static final int TRACKED_NEW_CT_STATE = 0x21;
    public static final int TRACKED_INV_CT_STATE = 0x30;

    public static final int UNTRACKED_CT_STATE_MASK = 0x20;
    public static final int TRACKED_CT_STATE_MASK = 0x20;
    public static final int TRACKED_EST_CT_STATE_MASK = 0x37;
    public static final int TRACKED_REL_CT_STATE_MASK = 0x37;
    public static final int TRACKED_NEW_CT_STATE_MASK = 0x21;
    public static final int TRACKED_INV_CT_STATE_MASK = 0x30;

    public static final long TCP_FLAG_SYN = 1 << 1;
    public static final long TCP_FLAG_ACK = 1 << 4;
    public static final long TCP_FLAG_SYN_ACK = TCP_FLAG_SYN + TCP_FLAG_ACK;

    public static final short EGRESS_LEARN_TABLE = 42;
    public static final short EGRESS_LEARN2_TABLE = 43;
    public static final short INGRESS_LEARN_TABLE = 253;
    public static final short INGRESS_LEARN2_TABLE = 254;

    public static final BigInteger METADATA_MASK_LEARN_FLAG = new BigInteger("FFFFFFFFFFFFFFFE", 16);

    private static final String SECURITY_GROUP_MODE_KEY = "security-group-mode";
    public static final String SECURITY_GROUP_TCP_IDLE_TO_KEY = "security-group-tcp-idle-timeout";
    public static final String SECURITY_GROUP_TCP_HARD_TO_KEY = "security-group-tcp-hard-timeout";
    public static final String SECURITY_GROUP_TCP_FIN_IDLE_TO_KEY = "security-group-tcp-fin-idle-timeout";
    public static final String SECURITY_GROUP_TCP_FIN_HARD_TO_KEY = "security-group-tcp-fin-hard-timeout";
    public static final String SECURITY_GROUP_UDP_IDLE_TO_KEY = "security-group-udp-idle-timeout";
    public static final String SECURITY_GROUP_UDP_HARD_TO_KEY = "security-group-udp-hard-timeout";

    public static final String LEARN_MATCH_REG_VALUE = "1";

    private static Map<String, Object> globalConf = Collections.synchronizedMap(new HashMap<>());

    private AclConstants() {
    }

    public enum SecurtiyGroupMode {
        STATELESS,
        LEARN,
        STATEFULL;
    }

    public static SecurtiyGroupMode getSecurityGroupMode() {
        SecurtiyGroupMode ret = SecurtiyGroupMode.STATEFULL;
        SecurtiyGroupMode securityMode = (SecurtiyGroupMode)globalConf.get(SECURITY_GROUP_MODE_KEY);

        if (securityMode == null) {
            String modePropertyStr = ConfigProperties.getProperty(AclConstants.class, SECURITY_GROUP_MODE_KEY);
            if (modePropertyStr != null) {
                ret = SecurtiyGroupMode.valueOf(modePropertyStr.toUpperCase());
            }
            globalConf.put(SECURITY_GROUP_MODE_KEY, ret);
        } else {
            ret = securityMode;
        }

        return ret;
    }

    public static String getGlobalConf(String key, String defaultValue) {
        String ret = defaultValue;
        String value = (String)globalConf.get(key);
        if (value == null) {
            String propertyStr = ConfigProperties.getProperty(AclConstants.class, key);
            if (propertyStr != null) {
                ret = propertyStr;
            }
            globalConf.put(key, ret);
        }
        return ret;
    }

}
