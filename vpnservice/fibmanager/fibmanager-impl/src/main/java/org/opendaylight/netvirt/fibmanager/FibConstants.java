/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg0;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg1;

public final class FibConstants {
    static final int DEFAULT_FIB_FLOW_PRIORITY = 10;
    static final int DEFAULT_PREFIX_LENGTH = 32;
    static final int DEFAULT_IPV6_PREFIX_LENGTH = 128;
    static final String PREFIX_SEPARATOR = "/";
    static final String FLOWID_PREFIX = "L3.";
    static final String VPN_IDPOOL_NAME = "vpnservices";
    static final String SEPARATOR = ".";
    static final String DEFAULT_NEXTHOP_IP = "0.0.0.0";
    public static final long INVALID_GROUP_ID = -1;
    public static final Map<Integer, Class<? extends NxmNxReg>> NXM_REG_MAPPING = new ConcurrentHashMap<>();

    static {
        NXM_REG_MAPPING.put(0, NxmNxReg0.class);
        NXM_REG_MAPPING.put(1, NxmNxReg1.class);
    }
}
