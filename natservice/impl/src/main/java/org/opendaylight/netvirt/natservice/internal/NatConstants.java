/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;


public interface NatConstants {

    short DEFAULT_NAPT_FLOW_PRIORITY = 10;
    String NAPT_FLOW_NAME = "SNAT";
    BigInteger COOKIE_NAPT_BASE = new BigInteger("8000000", 16);
    String NAPT_FLOWID_PREFIX = "SNAT.";
    String IPV6_FLOWID_PREFIX = "IPv6.";
    String FLOWID_SEPARATOR = ".";
    String COLON_SEPARATOR = ":";
    int DEFAULT_NAPT_IDLE_TIMEOUT = 300;
    int EVENT_QUEUE_LENGTH = 1000000;
    String FLOWID_PREFIX = "L3.";
    int DEFAULT_DNAT_FLOW_PRIORITY = 10;
    long INVALID_ID = -1;
    short SNAT_FIB_FLOW_PRIORITY = 42;
    short SNAT_TRK_FLOW_PRIORITY = 6;
    short SNAT_NEW_FLOW_PRIORITY = 5;
    short DEFAULT_SNAT_FLOW_PRIORITY = 10;
    short DEFAULT_PSNAT_FLOW_PRIORITY = 5;
    String SNAT_FLOW_NAME = "SNAT";
    String SNAT_FLOWID_PREFIX = "SNAT.";
    String SNAT_IDPOOL_NAME = "snatGroupIdPool";
    long SNAT_ID_LOW_VALUE = 225000L;
    long SNAT_ID_HIGH_VALUE = 250000L;
    String ODL_VNI_POOL_NAME = "opendaylight-vni-ranges";
    long VNI_DEFAULT_LOW_VALUE = 70000L;
    long VNI_DEFAULT_HIGH_VALUE = 99999L;
    int DEFAULT_TS_FLOW_PRIORITY = 10;
    short DEFAULT_PREFIX = 32;
    long DEFAULT_L3VNI_VALUE = 0;
    int DEFAULT_LABEL_VALUE = 0;
    String DNAT_FLOW_NAME = "DNAT";
    short DEFAULT_HARD_TIMEOUT = 0;
    short DEFAULT_IDLE_TIMEOUT = 0;
    int SNAT_PACKET_THEADPOOL_SIZE = 25;
    int SNAT_PACKET_RETRY_THEADPOOL_SIZE = 15;
    String NAT_DJC_PREFIX = "NAT-";
    int NAT_DJC_MAX_RETRIES = 3;
    // Flow Actions
    int ADD_FLOW = 0;
    int DEL_FLOW = 1;

    enum ITMTunnelLocType {
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
