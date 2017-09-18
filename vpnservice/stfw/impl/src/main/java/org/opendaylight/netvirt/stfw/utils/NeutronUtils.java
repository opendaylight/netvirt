/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.utils;

import org.opendaylight.netvirt.stfw.northbound.NeutronPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronUtils.class);

    public static final String VIF_TYPE_VHOSTUSER = "vhostuser";
    public static final String VIF_TYPE_UNBOUND = "unbound";
    public static final String VIF_TYPE_BINDING_FAILED = "binding_failed";
    public static final String VIF_TYPE_DISTRIBUTED = "distributed";
    public static final String VIF_TYPE_OVS = "ovs";
    public static final String VIF_TYPE_BRIDGE = "bridge";
    public static final String VIF_TYPE_OTHER = "other";
    public static final String VIF_TYPE_MACVTAP = "macvtap";
    public static final String PREFIX_TAP = "tap";
    public static final String PREFIX_VHOSTUSER = "vhu";

    public static String getVifPortName(NeutronPort port) {
        if (port == null || port.getPortId() == null) {
            return null;
        }
        String tapId = port.getPortId().getValue().substring(0, 11);
        String portNamePrefix = getPortNamePrefix(port);
        if (portNamePrefix != null) {
            return new StringBuilder().append(portNamePrefix).append(tapId).toString();
        }
        return null;
    }

    private static String getPortNamePrefix(NeutronPort port) {
        if (port.getVifType() == null) {
            return null;
        }
        switch (port.getVifType()) {
            case VIF_TYPE_VHOSTUSER:
                return PREFIX_VHOSTUSER;
            case VIF_TYPE_OVS:
            case VIF_TYPE_DISTRIBUTED:
            case VIF_TYPE_BRIDGE:
            case VIF_TYPE_OTHER:
            case VIF_TYPE_MACVTAP:
                return PREFIX_TAP;
            case VIF_TYPE_UNBOUND:
            case VIF_TYPE_BINDING_FAILED:
            default:
                return null;
        }
    }

}
