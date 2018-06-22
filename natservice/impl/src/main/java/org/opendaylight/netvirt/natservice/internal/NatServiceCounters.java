/*
 * Copyright (c) 2016 Hewlett-Packard Enterprise and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.metrics.Meter;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;

@Singleton
public class NatServiceCounters {
    private final Meter installDefaultNatFlowMeter;
    private final Meter removeDefaultNatFlowMeter;
    private final Meter removeExternalNetworkGroupMeter;
    private final Meter subnetmapAddMeter;
    private final Meter subnetmapRemoveMeter;
    private final Meter subnetmapUpdateMeter;
    private final Meter garpSentMeter;
    private final Meter garpFailedIpv6Meter;
    private final Meter garpFailedMissingInterfaceMeter;
    private final Meter garpFailedSendMeter;

    @Inject
    public NatServiceCounters(MetricProvider metricProvider) {
        this.installDefaultNatFlowMeter = meter(metricProvider, "install_default_nat_flow");
        this.removeDefaultNatFlowMeter = meter(metricProvider, "remove_default_nat_flow");
        this.removeExternalNetworkGroupMeter = meter(metricProvider, "remove_external_network_group");
        this.subnetmapAddMeter = meter(metricProvider, "subnetmap_add");
        this.subnetmapRemoveMeter = meter(metricProvider, "subnetmap_remove");
        this.subnetmapUpdateMeter = meter(metricProvider, "subnetmap_update");
        this.garpSentMeter = meter(metricProvider, "garp_sent");
        this.garpFailedIpv6Meter = meter(metricProvider, "garp_failed_ipv6");
        this.garpFailedMissingInterfaceMeter = meter(metricProvider, "garp_failed_missing_interface");
        this.garpFailedSendMeter = meter(metricProvider, "garp_failed_send");
    }

    private Meter meter(MetricProvider metricProvider, String id) {
        return metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("natservice").id(id).build());
    }

    public void installDefaultNatFlow() {
        installDefaultNatFlowMeter.mark();
    }

    public void removeDefaultNatFlow() {
        removeDefaultNatFlowMeter.mark();
    }

    public void removeExternalNetworkGroup() {
        removeExternalNetworkGroupMeter.mark();
    }

    public void subnetmapAdd() {
        subnetmapAddMeter.mark();
    }

    public void subnetmapRemove() {
        subnetmapRemoveMeter.mark();
    }

    public void subnetmapUpdate() {
        subnetmapUpdateMeter.mark();
    }

    public void garpSent() {
        garpSentMeter.mark();
    }

    public void garpFailedIpv6() {
        garpFailedIpv6Meter.mark();
    }

    public void garpFailedMissingInterface() {
        garpFailedMissingInterfaceMeter.mark();
    }

    public void garpFailedSend() {
        garpFailedSendMeter.mark();
    }
}
