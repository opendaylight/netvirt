/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg0;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg1;

public interface FibConstants {
    int DEFAULT_FIB_FLOW_PRIORITY = 10;
    int DEFAULT_PREFIX_LENGTH = 32;
    int DEFAULT_IPV6_PREFIX_LENGTH = 128;
    String PREFIX_SEPARATOR = "/";
    String FLOWID_PREFIX = "L3.";
    String VPN_IDPOOL_NAME = "vpnservices";
    String SEPARATOR = ".";
    String DEFAULT_NEXTHOP_IP = "0.0.0.0";
    long INVALID_GROUP_ID = -1;
    int DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY = 8;
    String TST_FLOW_ID_SUFFIX = "TST.";

    Map<Integer, Class<? extends NxmNxReg>> NXM_REG_MAPPING = ImmutableMap.of(0, NxmNxReg0.class, 1, NxmNxReg1.class);
}
