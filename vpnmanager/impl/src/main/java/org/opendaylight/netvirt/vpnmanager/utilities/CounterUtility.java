/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.utilities;

public enum CounterUtility {

    subnet_route_packet_ignored("subnet.route.packet.ignored"),
    subnet_route_packet_failed("subnet.route.packet.failed"),
    subnet_route_packet_arp_sent("subnet.route.packet.arp.sent"),
    subnet_route_packet_ns_sent("subnet.route.packet.ns.sent"),
    subnet_route_packet_recived("subnet.route.packet.recived"),
    subnet_route_packet_drop("subnet.route.packet.drop"),
    subnet_route_packet_processed("subnet.route.packet.processed"),;

    private static final String PROJECT = "netvirt";
    private static final String MODULE = "vpnmanager";
    private static final String SUBNET_ROUTE_ID = "subnetroute";
    private static final String SUBNET_ROUTE_INVALID_PACKET = "invalid packet";

    public static String getSubnetRouteInvalidPacket() {
        return SUBNET_ROUTE_INVALID_PACKET;
    }

    public static String getProject() {
        return PROJECT;
    }

    public static String getModule() {
        return MODULE;
    }

    public static String getSubnetRouteId() {
        return SUBNET_ROUTE_ID;
    }



    String label;
    CounterUtility(String label) {
        this.label = label;

    }

    @Override
    public String toString() {
        return label;
    }
}
