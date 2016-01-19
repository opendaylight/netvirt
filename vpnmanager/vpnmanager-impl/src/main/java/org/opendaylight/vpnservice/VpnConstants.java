/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice;

import java.math.BigInteger;

public class VpnConstants {
    public static final String VPN_IDPOOL_NAME = "vpnservices";
    public static final long VPN_IDPOOL_START = 100L;
    public static final String VPN_IDPOOL_SIZE = "100000";
    public static final short DEFAULT_FLOW_PRIORITY = 10;
    public static final short L3VPN_SERVICE_IDENTIFIER = 2;
    public static final long INVALID_ID = -1;
    public static final String SEPARATOR = ".";
    public static final BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);
    public static final BigInteger COOKIE_L3_BASE = new BigInteger("8000000", 16);
    public static final String FLOWID_PREFIX = "L3.";
    public static final long WAIT_TIME_IN_MILLISECONDS = 5000;
}
