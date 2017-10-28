/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;


public interface BgpConstants {

    String BGP_SPEAKER_HOST_NAME = "vpnservice.bgpspeaker.host.name";
    String BGP_SPEAKER_THRIFT_PORT = "vpnservice.bgpspeaker.thrift.port";
    String DEFAULT_BGP_HOST_NAME = "localhost";
    int DEFAULT_BGP_THRIFT_PORT = 7644;
    int BGP_NOTIFY_CEASE_CODE = 6;
    String QBGP_VTY_PASSWORD = "sdncbgpc";
    String BGP_COUNTER_NBR_PKTS_RX = "BgpNeighborPacketsReceived";
    String BGP_COUNTER_NBR_PKTS_TX = "BgpNeighborPacketsSent";
    String BGP_COUNTER_RD_ROUTE_COUNT = "BgpRdRouteCount";
    String BGP_COUNTER_TOTAL_PFX = "BgpTotalPrefixes:Bgp_Total_Prefixes";
    String BGP_DEF_LOG_LEVEL = "errors";
    String BGP_DEF_LOG_FILE = "/var/log/bgp_debug.log";
    long DEFAULT_ETH_TAG = 0L;
}
