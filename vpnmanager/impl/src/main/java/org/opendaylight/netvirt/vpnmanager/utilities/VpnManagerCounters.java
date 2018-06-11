/*
 * Copyright (c) 2016 Hewlett-Packard Enterprise and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.metrics.Meter;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;

@Singleton
public class VpnManagerCounters {
    private final MetricProvider metricProvider;
    private final Meter subnetRoutePacketIgnoredMeter;
    private final Meter subnetRoutePacketFailedMeter;
    private final Meter subnetRoutePacketArpSentMeter;
    private final Meter garpAddNotificationMeter;
    private final Meter garpUpdateNotificationMeter;
    private final Meter garpSentMeter;
    private final Meter garpSentIpv6Meter;
    private final Meter garpSentFailedMeter;
    private final Meter garpInterfaceRpcFailedMeter;

    @Inject
    public VpnManagerCounters(MetricProvider metricProvider) {
        this.metricProvider = metricProvider;
        this.subnetRoutePacketIgnoredMeter = meter("subnet_route_packet_ignored");
        this.subnetRoutePacketFailedMeter = meter("subnet_route_packet_failed");
        this.subnetRoutePacketArpSentMeter = meter("subnet_route_packet_arp_sent");
        this.garpAddNotificationMeter = meter("garp_add_notification");
        this.garpUpdateNotificationMeter = meter("garp_update_notification");
        this.garpSentMeter = meter("garp_sent");
        this.garpSentIpv6Meter = meter("garp_sent_ipv6");
        this.garpSentFailedMeter = meter("garp_sent_failed");
        this.garpInterfaceRpcFailedMeter = meter("garp_interface_rpc_failed");
    }

    private Meter meter(String id) {
        return metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("vpnmanager").id(id).build());
    }

    public void subnetRoutePacketIgnored() {
        subnetRoutePacketIgnoredMeter.mark();
    }

    public void subnetRoutePacketFailed() {
        subnetRoutePacketFailedMeter.mark();
    }

    public void subnetRoutePacketArpSent() {
        subnetRoutePacketArpSentMeter.mark();
    }

    public void garpAddNotification() {
        garpAddNotificationMeter.mark();
    }

    public void garpUpdateNotification() {
        garpUpdateNotificationMeter.mark();
    }

    public void garpSent() {
        garpSentMeter.mark();
    }

    public void garpSentIpv6() {
        garpSentIpv6Meter.mark();
    }

    public void garpSentFailed() {
        garpSentFailedMeter.mark();
    }

    public void garpInterfaceRpcFailed() {
        garpInterfaceRpcFailedMeter.mark();
    }
}
