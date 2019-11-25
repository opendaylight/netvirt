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
    String BGP_COUNTER_IPV4_PFX = "BgpIPV4Prefixes:Bgp_IPV4_Prefixes";
    String BGP_COUNTER_IPV6_PFX = "BgpIPV6Prefixes:Bgp_IPV6_Prefixes";
    String BGP_DEF_LOG_LEVEL = "debugging";
    String BGP_DEF_LOG_FILE = "/opt/quagga/var/log/quagga/zrpcd.init.log";
    String BFD_COUNTER_NBR_PKTS_RX = "BfdNeighborPacketsReceived";
    String BFD_COUNTER_NBR_PKTS_TX = "BfdNeighborPacketsSent";
    long DEFAULT_ETH_TAG = 0L;
    int BGP_DEFAULT_MULTIPATH = 2;
    int BFD_DEFAULT_DETECT_MULT = 8;
    String BGP_CONFIG_ENTITY = "bgp";
    int BFD_DEFAULT_MIN_RX = 500;
    int BFD_DEFAULT_MIN_TX = 500000;
    int MIN_RX_MIN = 50;
    int MIN_RX_MAX = 50000;
    int MIN_TX_MIN = 1000;
    int MIN_TX_MAX = 4294001;
    int MIN_DETECT_MULT = 2;
    int MAX_DETECT_MULT = 255;
    int HISTORY_LIMIT = 100000;
    int HISTORY_THRESHOLD = 75000;
    int BFD_DEFAULT_FAILURE_THRESHOLD = 0;
    int BFD_DEFAULT_SUCCESS_THRESHOLD = 0;
}
