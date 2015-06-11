/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice;

public class VpnConstants {
    public static final String VPN_IDPOOL_NAME = "vpnservices";
    public static final long VPN_IDPOOL_START = 1L;
    public static final String VPN_IDPOOL_SIZE = "65535";
    public static final short LPORT_INGRESS_TABLE = 0;
    public static final short LFIB_TABLE = 20;
    public static final short FIB_TABLE = 21;
    public static final short DEFAULT_FLOW_PRIORITY = 10;
    public static final long INVALID_ID = -1;
    public static final String SEPARATOR = ".";
}
