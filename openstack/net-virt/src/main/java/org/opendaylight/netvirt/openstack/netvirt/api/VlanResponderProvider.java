/*
 * Copyright (c) 2016 NEC Corporation and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.api;

import java.util.Map;
import java.util.Set;

/**
 *  This interface allows Vlan flows to be written to devices
 */
public interface VlanResponderProvider {
    /**
     * Creates provider network flows for internal bridge.
     *
     * @param dpidLong dp Id
     * @param segmentationId segmentation id
     * @param patchPort patch port of internal bridge
     * @param ofPort of port value
     * @param macAddress mac address
     * @param vlanProviderCache Initial VLAN cache with processing cache
     * @param write - flag to indicate the operation
     */
    void programProviderNetworkRulesInternal(Long dpidLong, String segmentationId, Long ofPort, Long patchPort,
            String macAddress, Map<String, Set<String>> vlanProviderCache, boolean write);

    /**
     * Creates provider network flows for external bridge.
     *
     * @param dpidLong dp id
     * @param segmentationId segmentation id
     * @param patchExtPort patch port of external bridge
     * @param macAddress mac address
     * @param vlanProviderCache Initial VLAN cache with processing cache
     * @param write - flag indicate the operation
     */
    void programProviderNetworkRulesExternal(Long dpidLong,  String segmentationId, Long patchExtPort,
            String macAddress, Map<String, Set<String>> vlanProviderCache, boolean write);
}
