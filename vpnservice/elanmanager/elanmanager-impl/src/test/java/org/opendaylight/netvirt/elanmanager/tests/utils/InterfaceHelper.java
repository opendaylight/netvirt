/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import java.math.BigInteger;

import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;

public class InterfaceHelper {

    private InterfaceHelper() {

    }

    public static InterfaceInfo buildVlanInterfaceInfo(final String interfaceName,
                                                       final BigInteger dpId,
                                                       final int portNo,
                                                       final int lportTag,
                                                       final String mac) {
        return InterfaceHelper.buildInterfaceInfo(interfaceName, dpId, portNo, lportTag, mac,
                InterfaceInfo.InterfaceType.VLAN_INTERFACE);
    }

    public static InterfaceInfo buildVxlanInterfaceInfo(final String interfaceName,
                                                        final BigInteger dpId,
                                                        final int portNo,
                                                        final int lportTag,
                                                        final String mac) {
        return InterfaceHelper.buildInterfaceInfo(interfaceName, dpId, portNo, lportTag, mac,
                InterfaceInfo.InterfaceType.VXLAN_TRUNK_INTERFACE);
    }

    public static InterfaceInfo buildInterfaceInfo(final String interfaceName,
                                                   final BigInteger dpId,
                                                   final int portNo,
                                                   final int lportTag,
                                                   final String mac,
                                                   final InterfaceInfo.InterfaceType interfaceType) {
        InterfaceInfo interfaceInfo = new InterfaceInfo(interfaceName);
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setDpId(dpId);
        interfaceInfo.setPortNo(portNo);
        interfaceInfo.setAdminState(InterfaceInfo.InterfaceAdminState.ENABLED);
        interfaceInfo.setOpState(InterfaceInfo.InterfaceOpState.UP);
        interfaceInfo.setInterfaceTag(lportTag);
        interfaceInfo.setInterfaceType(interfaceType);
        interfaceInfo.setGroupId(0);
        interfaceInfo.setMacAddress(mac);
        return interfaceInfo;
    }
}
