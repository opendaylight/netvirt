/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;


public class NatConstants {

    public static final short DEFAULT_NAPT_FLOW_PRIORITY = 10;
    public static final String NAPT_FLOW_NAME = "SNAT";
    public static BigInteger COOKIE_NAPT_BASE = new BigInteger("8000000", 16);
    public static final String NAPT_FLOWID_PREFIX = "SNAT.";
    public static final String FLOWID_SEPARATOR = ".";
    public static final String COLON_SEPARATOR = ":";
    public static final int DEFAULT_NAPT_IDLE_TIMEOUT = 300;
    public static int EVENT_QUEUE_LENGTH = 1000000;
    public static final String FLOWID_PREFIX = "L3.";
    public static final int DEFAULT_DNAT_FLOW_PRIORITY = 10;
    public static final long INVALID_ID = -1;
    public static final short SNAT_FIB_FLOW_PRIORITY = 42;
    public static final short SNAT_TRK_FLOW_PRIORITY = 6;
    public static final short SNAT_NEW_FLOW_PRIORITY = 5;
    public static final short DEFAULT_SNAT_FLOW_PRIORITY = 10;
    public static final short DEFAULT_PSNAT_FLOW_PRIORITY = 5;
    public static final String SNAT_FLOW_NAME = "SNAT";
    public static final String SNAT_FLOWID_PREFIX = "SNAT.";
    public static final String SNAT_IDPOOL_NAME = "snatGroupIdPool";
    public static final long SNAT_ID_LOW_VALUE = 225000L;
    public static final long SNAT_ID_HIGH_VALUE = 250000L;
    public static final String ODL_VNI_POOL_NAME = "opendaylight-vni-ranges";
    public static final long VNI_DEFAULT_LOW_VALUE = 70000L;
    public static final long VNI_DEFAULT_HIGH_VALUE = 99999L;
    public static final int DEFAULT_TS_FLOW_PRIORITY = 10;
    public static final short DEFAULT_PREFIX = 32;
    public static final long DEFAULT_L3VNI_VALUE = 0;
    public static final int DEFAULT_LABEL_VALUE = 0;
    public static final String DNAT_FLOW_NAME = "DNAT";
    public static final short DEFAULT_HARD_TIMEOUT = 0;
    public static final short DEFAULT_IDLE_TIMEOUT = 0;
    public static final int SNAT_PACKET_THEADPOOL_SIZE = 25;
    public static final int SNAT_PACKET_RETRY_THEADPOOL_SIZE = 15;
    // Flow Actions
    public static final int ADD_FLOW = 0;
    public static final int DEL_FLOW = 1;

    public enum ITMTunnelLocType {
        Invalid(0), Internal(1), External(2), Hwvtep(3);

        private final int type;

        ITMTunnelLocType(int id) {
            this.type = id;
        }

        public int getValue() {
            return type;
        }
    }

}
