/*
 * Copyright (c) 2016 Hewlett-Packard Enterprise and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.metrics.Meter;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;

@Singleton
public final class DhcpServiceCounters {
    private final Meter installDhcpDropFlowMeter;
    private final Meter installDhcpFlowMeter;
    private final Meter installDhcpTableMissFlowMeter;
    private final Meter installDhcpTableMissFlowForExternalTableMeter;
    private final Meter removeDhcpDropFlowMeter;
    private final Meter removeDhcpFlowMeter;

    @Inject
    public DhcpServiceCounters(MetricProvider metricProvider) {
        this.installDhcpDropFlowMeter = meter(metricProvider, "install_dhcp_drop_flow");
        this.installDhcpFlowMeter = meter(metricProvider, "install_dhcp_flow");
        this.installDhcpTableMissFlowMeter = meter(metricProvider, "install_dhcp_table_miss_flow");
        this.installDhcpTableMissFlowForExternalTableMeter =
            meter(metricProvider, "install_dhcp_table_miss_flow_for_external_table");
        this.removeDhcpDropFlowMeter = meter(metricProvider, "remove_dhcp_drop_flow");
        this.removeDhcpFlowMeter = meter(metricProvider, "remove_dhcp_flow");
    }

    private Meter meter(MetricProvider metricProvider, String id) {
        return metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("dhcpservice").id(id).build());
    }

    public void installDhcpDropFlow() {
        installDhcpDropFlowMeter.mark();
    }

    public void installDhcpFlow() {
        installDhcpFlowMeter.mark();
    }

    public void installDhcpTableMissFlow() {
        installDhcpTableMissFlowMeter.mark();
    }

    public void installDhcpTableMissFlowForExternalTable() {
        installDhcpTableMissFlowForExternalTableMeter.mark();
    }

    public void removeDhcpDropFlow() {
        removeDhcpDropFlowMeter.mark();
    }

    public void removeDhcpFlow() {
        removeDhcpFlowMeter.mark();
    }
}
