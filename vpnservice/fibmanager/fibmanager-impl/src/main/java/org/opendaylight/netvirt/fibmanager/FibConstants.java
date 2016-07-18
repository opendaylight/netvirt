/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import java.math.BigInteger;

public class FibConstants {
    static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000002", 16);
    static final BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
    static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    static final int DEFAULT_FIB_FLOW_PRIORITY = 10;
    static final String FLOWID_PREFIX = "L3.";
    static final String VPN_IDPOOL_NAME = "vpnservices";
    static final String SEPARATOR = ".";
    public static final short L3VPN_SERVICE_IDENTIFIER = 2; // TODO : This should be in just on place
}
