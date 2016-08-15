/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

public class ArpConstants {

        public static final int THREAD_POOL_SIZE = 5;
        public static final int NO_DELAY = 0;
        public static final int RETRY_COUNT = 5;
        public static final short ARP_REQUEST_OP = (short) 1;
        public static final short ETH_TYPE_ARP = 0x0806;
        public static final String PREFIX = "/32";
        public static final String NODE_CONNECTOR_NOT_FOUND_ERROR = "Node connector id not found for interface %s";
        public static final String FAILED_TO_GET_SRC_IP_FOR_INTERFACE = "Failed to get src ip for %s";
        public static final String FAILED_TO_GET_SRC_MAC_FOR_INTERFACE = "Failed to get src mac for interface %s iid %s ";
        public static final int PERIOD = 10000;
        public static final String ARPJOB = "Arpcache";
        public static final long DEFAULT_ARP_LEARNED_CACHE_TIMEOUT = 300000L; /* 300 seconds = 5 minutes */

        public static final String ARP_CACHE_TIMEOUT_PROP = "arp.cache.timeout";

        public static long ARP_CACHE_TIMEOUT_MILLIS = Long.getLong(ArpConstants.ARP_CACHE_TIMEOUT_PROP,
                ArpConstants.DEFAULT_ARP_LEARNED_CACHE_TIMEOUT);

}
