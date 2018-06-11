/*
 * Copyright (c) 2016 Hewlett-Packard Enterprise and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.metrics.Meter;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;

@Singleton
public final class ElanManagerCounters {
    private final Meter unknownSmacPktinForwardingEntriesRemovedMeter;
    private final Meter unknownSmacPktinRcvMeter;
    private final Meter unknownSmacPktinLearnedMeter;
    private final Meter unknownSmacPktinIgnoredDueProtectionMeter;
    private final Meter unknownSmacPktinFlowsRemovedForRelearnedMeter;
    private final Meter unknownSmacPktinRemovedForRelearnedMeter;
    private final Meter unknownSmacPktinMacMigrationIgnoredDueToProtectionMeter;

    @Inject
    public ElanManagerCounters(MetricProvider metricProvider) {
        this.unknownSmacPktinForwardingEntriesRemovedMeter =
            meter(metricProvider, "unknown_smac_pktin_forwarding_entries_removed");
        this.unknownSmacPktinRcvMeter = meter(metricProvider, "unknown_smac_pktin_rcv");
        this.unknownSmacPktinLearnedMeter = meter(metricProvider, "unknown_smac_pktin_learned");
        this.unknownSmacPktinIgnoredDueProtectionMeter =
            meter(metricProvider, "unknown_smac_pktin_ignored_due_protection");
        this.unknownSmacPktinFlowsRemovedForRelearnedMeter =
            meter(metricProvider, "unknown_smac_pktin_flows_removed_for_relearned");
        this.unknownSmacPktinRemovedForRelearnedMeter =
            meter(metricProvider, "unknown_smac_pktin_removed_for_relearned");
        this.unknownSmacPktinMacMigrationIgnoredDueToProtectionMeter =
            meter(metricProvider, "unknown_smac_pktin_mac_migration_ignored_due_to_protection");
    }

    private Meter meter(MetricProvider metricProvider, String id) {
        return metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("elanmanager").id(id).build());
    }

    public void unknownSmacPktinForwardingEntriesRemoved() {
        unknownSmacPktinForwardingEntriesRemovedMeter.mark();
    }

    public void unknownSmacPktinRcv() {
        unknownSmacPktinRcvMeter.mark();
    }

    public void unknownSmacPktinLearned() {
        unknownSmacPktinLearnedMeter.mark();
    }

    public void unknownSmacPktinIgnoredDueProtection() {
        unknownSmacPktinIgnoredDueProtectionMeter.mark();
    }

    public void unknownSmacPktinFlowsRemovedForRelearned() {
        unknownSmacPktinFlowsRemovedForRelearnedMeter.mark();
    }

    public void unknownSmacPktinRemovedForRelearned() {
        unknownSmacPktinRemovedForRelearnedMeter.mark();
    }

    public void unknownSmacPktinMacMigrationIgnoredDueToProtection() {
        unknownSmacPktinMacMigrationIgnoredDueToProtectionMeter.mark();
    }
}
