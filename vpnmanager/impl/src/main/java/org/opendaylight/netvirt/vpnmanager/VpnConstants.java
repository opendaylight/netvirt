/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg3;

public interface VpnConstants {
    String VPN_IDPOOL_NAME = "vpnservices";
    long VPN_IDPOOL_LOW = 100000L;
    long VPN_IDPOOL_HIGH = 130000L;
    short DEFAULT_FLOW_PRIORITY = 10;
    int DEFAULT_LPORT_DISPATCHER_FLOW_PRIORITY = 1;
    int VPN_ID_LENGTH = 24;
    long INVALID_ID = -1;
    String SEPARATOR = ".";
    BigInteger COOKIE_L3_BASE = new BigInteger("8000000", 16);
    String FLOWID_PREFIX = "L3.";
    long MIN_WAIT_TIME_IN_MILLISECONDS = 10000;
    long MAX_WAIT_TIME_IN_MILLISECONDS = 180000;
    long PER_INTERFACE_MAX_WAIT_TIME_IN_MILLISECONDS = 4000;
    long PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS = 90000;
    long PER_VPN_INSTANCE_OPDATA_MAX_WAIT_TIME_IN_MILLISECONDS = 180000;
    int ELAN_GID_MIN = 200000;
    int INVALID_LABEL = 0;
    String ARP_MONITORING_ENTITY = "arpmonitoring";

    // An IdPool for Pseudo LPort tags, that is, lportTags that are no related to an interface.
    // These lportTags must be higher than 170000 to avoid collision with interface LportTags and
    // also VPN related IDs (vrfTags and labels)
    String PSEUDO_LPORT_TAG_ID_POOL_NAME = System.getProperty("lport.gid.name", "lporttag");
    long LOWER_PSEUDO_LPORT_TAG = Long.getLong("lower.lport.gid", 170001);
    long UPPER_PSEUDO_LPORT_TAG = Long.getLong("upper.lport.gid", 270000);
    BigInteger COOKIE_SUBNETROUTE_TABLE_MISS = new BigInteger("8000004", 16);

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

    enum DCGWPresentStatus {
        Invalid(0), Present(1), Absent(2);

        private final int status;

        DCGWPresentStatus(int id) {
            this.status = id;
        }

        public int getValue() {
            return status;
        }
    }

    String DEFAULT_GATEWAY_MAC_ADDRESS = "de:ad:be:ef:00:01";
    Class<? extends NxmNxReg> VPN_REG_ID = NxmNxReg3.class;
}
