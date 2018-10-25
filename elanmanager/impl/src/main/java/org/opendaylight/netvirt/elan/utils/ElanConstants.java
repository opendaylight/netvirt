/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.math.BigInteger;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg7;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;

public interface ElanConstants {

    String ELAN_SERVICE_NAME = "ELAN";
    String LEAVES_POSTFIX = "_leaves";
    String ELAN_ID_POOL_NAME = "elan.ids.pool";
    long ELAN_ID_LOW_VALUE = 5000L;
    long ELAN_ID_HIGH_VALUE = 10000L;
    int ELAN_GID_MIN = 200000;
    int ELAN_SERVICE_PRIORITY = 5;
    int STATIC_MAC_TIMEOUT = 0;
    int ELAN_TAG_LENGTH = 16;
    int INTERFACE_TAG_LENGTH = 20;
    int ELAN_TAG_ADDEND = 270000;
    BigInteger INVALID_DPN = BigInteger.valueOf(-1);
    BigInteger COOKIE_ELAN_BASE_SMAC = new BigInteger("8500000", 16);
    BigInteger COOKIE_ELAN_LEARNED_SMAC = new BigInteger("8600000", 16);
    BigInteger COOKIE_ELAN_UNKNOWN_DMAC = new BigInteger("8700000", 16);
    BigInteger COOKIE_ELAN_KNOWN_SMAC = new BigInteger("8050000", 16);
    BigInteger COOKIE_ELAN_KNOWN_DMAC = new BigInteger("8030000", 16);
    long DEFAULT_MAC_TIME_OUT = 300;
    BigInteger COOKIE_ELAN_FILTER_EQUALS = new BigInteger("8800000", 16);
    BigInteger COOKIE_L2VNI_DEMUX = new BigInteger("1080000", 16);
    String L2_CONTROL_PACKETS_DMAC = "01:80:C2:00:00:00";
    String L2_CONTROL_PACKETS_DMAC_MASK = "FF:FF:FF:FF:FF:F0";
    int LLDP_ETH_TYPE = 0x88CC;
    String LLDP_DST_1 = "01:80:C2:00:00:00";
    String LLDP_DST_2 = "01:80:C2:00:00:03";
    String LLDP_DST_3 = "01:80:C2:00:00:0E";
    String L2GATEWAY_DS_JOB_NAME = "L2GW";
    String UNKNOWN_DMAC = "00:00:00:00:00:00";
    int JOB_MAX_RETRIES = 6;
    TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));
    String OVSDB_BRIDGE_URI_PREFIX = "bridge";
    Class<? extends NxmNxReg> ELAN_REG_ID = NxmNxReg7.class;
}
