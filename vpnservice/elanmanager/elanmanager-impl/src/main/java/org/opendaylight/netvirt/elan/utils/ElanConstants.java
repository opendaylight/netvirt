/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;

public class ElanConstants {

    public static final String ELAN_ID_POOL_NAME = "elan.ids.pool";
    public static final long ELAN_ID_LOW_VALUE = 5000L;
    public static final long ELAN_ID_HIGH_VALUE = 10000L;
    public static final int ELAN_GID_MIN = 200000;
    public static final short ELAN_SMAC_TABLE = 50;
    public static final short ELAN_DMAC_TABLE = 51;
    public static final short ELAN_UNKNOWN_DMAC_TABLE = 52;
    public static final short ELAN_SERVICE_INDEX = 4;
    public static final int ELAN_SERVICE_PRIORITY = 5;
    public static final int STATIC_MAC_TIMEOUT = 0;
    public static final long DELAY_TIME_IN_MILLISECOND = 5000;
    public static final BigInteger INVALID_DPN = BigInteger.valueOf(-1);
    public static final BigInteger COOKIE_ELAN_UNKNOWN_DMAC = new BigInteger("8700000", 16);
    public static final BigInteger COOKIE_ELAN_KNOWN_SMAC = new BigInteger("8050000", 16);
    public static final BigInteger COOKIE_ELAN_KNOWN_DMAC = new BigInteger("8030000", 16);
    public static final BigInteger COOKIE_ELAN_INGRESS_TABLE = new BigInteger("8040000", 16);
	public static final long DEFAULT_MAC_TIME_OUT = 30;
    public static final short ELAN_FILTER_EQUALS_TABLE = 55;
    public static final BigInteger COOKIE_ELAN_FILTER_EQUALS = new BigInteger("8800000", 16);

    public static final String L2GATEWAY_DS_JOB_NAME = "L2GW";
    public static final String UNKNOWN_DMAC = "00:00:00:00:00:00";
    public static final int JOB_MAX_RETRIES = 3;
    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));
    public static final String OVSDB_BRIDGE_URI_PREFIX = "bridge";
}
