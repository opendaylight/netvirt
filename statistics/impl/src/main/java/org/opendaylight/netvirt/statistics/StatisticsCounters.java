/*
 * Copyright © 2018 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.metrics.Meter;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;

@Singleton
public class StatisticsCounters {
    private final Meter failedGettingNodeCountersMeter;
    private final Meter failedGettingNodeConnectorCountersMeter;
    private final Meter failedGettingAggregatedNodeCountersMeter;
    private final Meter failedGeneratingUniqueRequestIdMeter;
    private final Meter unknownRequestHandlerMeter;
    private final Meter failedCreatingIngressCounterDataConfigMeter;
    private final Meter failedCreatingEgressCounterDataConfigMeter;
    private final Meter failedReadingCounterDataFromConfigMeter;
    private final Meter failedGettingCounterResultsMeter;
    private final Meter failedCreatingCountersConfigMeter;
    private final Meter failedGettingCounterResultsPortRemovalMeter;
    private final Meter ingressCountersServiceBindMeter;
    private final Meter egressCountersServiceBindMeter;
    private final Meter ingressCountersServiceUnbindMeter;
    private final Meter egressCountersServiceUnbindMeter;
    private final Meter failedGettingFlowCountersMeter;
    private final Meter failedGettingRpcResultForNodeConnectorCountersMeter;
    private final Meter failedGettingResultMapForNodeConnectorCountersMeter;

    @Inject
    public StatisticsCounters(MetricProvider metricProvider) {
        this.failedGettingNodeCountersMeter = meter(metricProvider, "failed_getting_node_counters");
        this.failedGettingNodeConnectorCountersMeter = meter(metricProvider, "failed_getting_node_connector_counters");
        this.failedGettingAggregatedNodeCountersMeter =
            meter(metricProvider, "failed_getting_aggregated_node_counters");
        this.failedGeneratingUniqueRequestIdMeter = meter(metricProvider, "failed_generating_unique_request_id");
        this.unknownRequestHandlerMeter = meter(metricProvider, "unknown_request_handler");
        this.failedCreatingIngressCounterDataConfigMeter =
            meter(metricProvider, "failed_creating_ingress_counter_data_config");
        this.failedCreatingEgressCounterDataConfigMeter =
            meter(metricProvider, "failed_creating_egress_counter_data_config");
        this.failedReadingCounterDataFromConfigMeter = meter(metricProvider, "failed_reading_counter_data_from_config");
        this.failedGettingCounterResultsMeter = meter(metricProvider, "failed_getting_counter_results");
        this.failedCreatingCountersConfigMeter = meter(metricProvider, "failed_creating_counters_config");
        this.failedGettingCounterResultsPortRemovalMeter =
            meter(metricProvider, "failed_getting_counter_results_port_removal");
        this.ingressCountersServiceBindMeter = meter(metricProvider, "ingress_counters_service_bind");
        this.egressCountersServiceBindMeter = meter(metricProvider, "egress_counters_service_bind");
        this.ingressCountersServiceUnbindMeter = meter(metricProvider, "ingress_counters_service_unbind");
        this.egressCountersServiceUnbindMeter = meter(metricProvider, "egress_counters_service_unbind");
        this.failedGettingFlowCountersMeter = meter(metricProvider, "failed_getting_flow_counters");
        this.failedGettingRpcResultForNodeConnectorCountersMeter =
            meter(metricProvider, "failed_getting_rpc_result_for_node_connector_counters");
        this.failedGettingResultMapForNodeConnectorCountersMeter =
            meter(metricProvider, "failed_getting_result_map_for_node_connector_counters");
    }

    private Meter meter(MetricProvider metricProvider, String id) {
        return metricProvider.newMeter(
                MetricDescriptor.builder().anchor(this).project("netvirt").module("statistics").id(id).build());
    }

    public void failedGettingNodeCounters() {
        failedGettingNodeCountersMeter.mark();
    }

    public void failedGettingNodeConnectorCounters() {
        failedGettingNodeConnectorCountersMeter.mark();
    }

    public void failedGettingAggregatedNodeCounters() {
        failedGettingAggregatedNodeCountersMeter.mark();
    }

    public void failedGeneratingUniqueRequestId() {
        failedGeneratingUniqueRequestIdMeter.mark();
    }

    public void unknownRequestHandler() {
        unknownRequestHandlerMeter.mark();
    }

    public void failedCreatingIngressCounterDataConfig() {
        failedCreatingIngressCounterDataConfigMeter.mark();
    }

    public void failedCreatingEgressCounterDataConfig() {
        failedCreatingEgressCounterDataConfigMeter.mark();
    }

    public void failedReadingCounterDataFromConfig() {
        failedReadingCounterDataFromConfigMeter.mark();
    }

    public void failedGettingCounterResults() {
        failedGettingCounterResultsMeter.mark();
    }

    public void failedCreatingCountersConfig() {
        failedCreatingCountersConfigMeter.mark();
    }

    public void failedGettingCounterResultsPortRemoval() {
        failedGettingCounterResultsPortRemovalMeter.mark();
    }

    public void ingressCountersServiceBind() {
        ingressCountersServiceBindMeter.mark();
    }

    public void egressCountersServiceBind() {
        egressCountersServiceBindMeter.mark();
    }

    public void ingressCountersServiceUnbind() {
        ingressCountersServiceUnbindMeter.mark();
    }

    public void egressCountersServiceUnbind() {
        egressCountersServiceUnbindMeter.mark();
    }

    public void failedGettingFlowCounters() {
        failedGettingFlowCountersMeter.mark();
    }

    public void failedGettingRpcResultForNodeConnectorCounters() {
        failedGettingRpcResultForNodeConnectorCountersMeter.mark();
    }

    public void failedGettingResultMapForNodeConnectorCounters() {
        failedGettingResultMapForNodeConnectorCountersMeter.mark();
    }
}
