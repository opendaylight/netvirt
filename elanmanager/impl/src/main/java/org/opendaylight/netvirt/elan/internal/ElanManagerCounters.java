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
    private final MetricProvider metricProvider;
    private final Meter unknownSmacPktinForwardingEntriesRemovedMeter;
    private final Meter unknownSmacPktinRcvMeter;
    private final Meter unknownSmacPktinLearnedMeter;
    private final Meter unknownSmacPktinIgnoredDueProtectionMeter;
    private final Meter unknownSmacPktinFlowsRemovedForRelearnedMeter;
    private final Meter unknownSmacPktinRemovedForRelearnedMeter;
    private final Meter unknownSmacPktinMacMigrationIgnoredDueToProtectionMeter;

    @Inject
    public ElanManagerCounters(MetricProvider metricProvider) {
        this.metricProvider = metricProvider;
        this.unknownSmacPktinForwardingEntriesRemovedMeter =
                meter("unknown_smac_pktin_forwarding_entries_removed_meter");
        this.unknownSmacPktinRcvMeter = meter("unknown_smac_pktin_rcv_meter");
        this.unknownSmacPktinLearnedMeter = meter("unknown_smac_pktin_learned_meter");
        this.unknownSmacPktinIgnoredDueProtectionMeter = meter("unknown_smac_pktin_ignored_due_protection_meter");
        this.unknownSmacPktinFlowsRemovedForRelearnedMeter =
                meter("unknown_smac_pktin_flows_removed_for_relearned_meter");
        this.unknownSmacPktinRemovedForRelearnedMeter = meter("unknown_smac_pktin_removed_for_relearned");
        this.unknownSmacPktinMacMigrationIgnoredDueToProtectionMeter =
                meter("unknown_smac_pktin_mac_migration_ignored_due_to_protection");
    }

    private Meter meter(String id) {
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
