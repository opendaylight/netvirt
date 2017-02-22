/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import org.opendaylight.infrautils.counters.api.OccurenceCounter;

public enum FederationPluginCounters {

    egress_steady_data, //
    egress_full_sync, //
    egress_last_full_sync_listener, //
    egress_process_pending_modification, //
    egress_transformation_failed, //
    egress_publish_modification, //
    egress_filter_result_deny, //
    egress_filter_result_accept, //
    egress_filter_result_queue, //
    egress_steady_data_aborted, //
    egress_full_sync_aborted, //
    ingress_begin_tx, //
    ingress_end_tx, //
    ingress_consume_msg, //
    ingress_full_sync_modification, //
    ingress_process_modification, //
    ingress_write_modification, //
    ingress_filter_result_deny, //
    ingress_filter_result_accept, //
    ingress_filter_result_queue, //
    ingress_add_to_tx_modification, //
    ingress_delete_modification, //
    ingress_subnet_vpn_association_changed, //
    ingress_federated_subnet_vpn_association_changed, //
    ingress_consume_msg_aborted, //
    ingress_full_sync_aborted, //
    egress_node_filtered_after_transform, //
    egress_no_existing_data, //
    removed_shadow_elan_interface, //
    removed_shadow_ietf_interface, //
    removed_shadow_inventory_node, //
    removed_shadow_topology_node, //
    removed_shadow_vpn_interface, //
    removed_shadow_vpn_port_ip_to_port;

    private OccurenceCounter counter;

    FederationPluginCounters() {
        counter = new OccurenceCounter(getClass().getSimpleName(), name(), name());
    }

    public void inc() {
        counter.inc();
    }
}
