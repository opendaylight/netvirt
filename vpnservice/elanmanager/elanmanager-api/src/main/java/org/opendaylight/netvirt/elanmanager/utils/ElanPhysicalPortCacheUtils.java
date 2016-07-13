/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.utils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ElanPhysicalPortCacheUtils {

    private static final Logger logger = LoggerFactory.getLogger(ElanPhysicalPortCacheUtils.class);
    private static final Map<String, Set<DpnInterface>> networkPortMap = Maps.newConcurrentMap();

    private static final String PATCH_PORT_PREFIX = "patch-";

    public static void addPhysnetPort(String phyNetworkName, String datapathId, String phyPortName) {
        logger.trace("Adding port {} DPN {} to physnet {}", phyPortName, datapathId, phyNetworkName);
        Set<DpnInterface> dpnInterfaces = networkPortMap.get(phyNetworkName);
        if (dpnInterfaces == null) {
            dpnInterfaces = Sets.newConcurrentHashSet();
            networkPortMap.put(phyNetworkName, dpnInterfaces);
        }

        dpnInterfaces.add(new DpnInterface(datapathId, phyPortName));
    }

    public static Iterable<DpnInterface> getPhysnetPorts(String networkName) {
        return networkPortMap.get(networkName);
    }

    public static String buildPatchPortName(String phyPortName) {
        return phyPortName != null ? PATCH_PORT_PREFIX + phyPortName : null;
    }

    public static class DpnInterface {

        private String datapathId;
        private String portName;

        public DpnInterface(String datapathId, String portName) {
            super();
            this.datapathId = datapathId;
            this.portName = portName;
        }

        public String getDatapathId() {
            return datapathId;
        }

        public String getPortName() {
            return portName;
        }

        public String getPatchPortName() {
            return buildPatchPortName(portName);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((datapathId == null) ? 0 : datapathId.hashCode());
            result = prime * result + ((portName == null) ? 0 : portName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            DpnInterface other = (DpnInterface) obj;
            if (datapathId == null) {
                if (other.datapathId != null) {
                    return false;
                }
            } else if (!datapathId.equals(other.datapathId)) {
                return false;
            }
            if (portName == null) {
                if (other.portName != null) {
                    return false;
                }
            } else if (!portName.equals(other.portName)) {
                return false;
            }
            return true;
        }
    }
}
