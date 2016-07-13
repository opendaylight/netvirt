/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.utils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanPhysicalPortCacheUtils {

    private static final Logger logger = LoggerFactory.getLogger(ElanPhysicalPortCacheUtils.class);
    private static final BiMap<String, String> networkPortMap = Maps.synchronizedBiMap(HashBiMap.create());

    private static final String PATCH_PORT_PREFIX = "patch-";

    public static void addPhysicalNetwork(String phyNetworkName, String phyPortName) {
        logger.trace("Adding port {} to physnet {}", phyPortName, phyNetworkName);
        networkPortMap.put(phyNetworkName, phyPortName);
    }

    public static String removePhysicalNetwork(String phyNetworkName) {
        logger.trace("Removing physnet {}", phyNetworkName);
        return networkPortMap.remove(phyNetworkName);
    }

    public static String getPhysicalPort(String networkName) {
        return networkPortMap.get(networkName);
    }

    public static String getPatchPort(String phyNetworkName) {
        String phyPortName = getPhysicalPort(phyNetworkName);
        return buildPatchPortName(phyPortName);
    }

    public boolean isPhysicalPort(String phyPortName) {
        return networkPortMap.inverse().containsKey(phyPortName);
    }

    public static String buildPatchPortName(String phyPortName) {
        return phyPortName != null ? PATCH_PORT_PREFIX + phyPortName : null;
    }

}
