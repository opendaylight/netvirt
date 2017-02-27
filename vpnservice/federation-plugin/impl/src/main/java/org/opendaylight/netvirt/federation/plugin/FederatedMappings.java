/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class FederatedMappings {

    private final Map<String, String> producerToConsumerNetworkMap = Maps.newConcurrentMap();
    private final Map<String, String> producerToConsumerSubnetMap = Maps.newConcurrentMap();
    private final Map<String, String> producerToConsumerTenantMap = Maps.newConcurrentMap();

    public FederatedMappings(List<FederatedNetworkPair> federatedNetworkPairs) {
        federatedNetworkPairs.stream()
                .forEach((pair) -> producerToConsumerNetworkMap.put(pair.producerNetworkId, pair.consumerNetworkId));
        federatedNetworkPairs.stream()
                .forEach((pair) -> producerToConsumerSubnetMap.put(pair.producerSubnetId, pair.consumerSubnetId));
        federatedNetworkPairs.stream()
                .forEach((pair) -> producerToConsumerTenantMap.put(pair.producerTenantId, pair.consumerTenantId));
    }

    public String getConsumerNetworkId(String producerNetworkId) {
        return producerToConsumerNetworkMap.get(producerNetworkId);
    }

    public boolean containsProducerNetworkId(String producerNetworkId) {
        return producerToConsumerNetworkMap.containsKey(producerNetworkId);
    }

    public boolean containsConsumerNetworkId(String consumerNetworkId) {
        return producerToConsumerNetworkMap.containsValue(consumerNetworkId);
    }

    public String getConsumerSubnetId(String producerSubnetId) {
        return producerToConsumerSubnetMap.get(producerSubnetId);
    }

    public boolean containsProducerSubnetId(String producerSubnetId) {
        return producerToConsumerSubnetMap.containsKey(producerSubnetId);
    }

    public boolean containsConsumerSubnetId(String consumerSubnetId) {
        return producerToConsumerSubnetMap.containsValue(consumerSubnetId);
    }

    public String getConsumerTenantId(String producerTenantId) {
        return producerToConsumerTenantMap.get(producerTenantId);
    }

    public boolean containsProducerTenantId(String producerTenantId) {
        return producerToConsumerTenantMap.containsKey(producerTenantId);
    }

    public boolean containsConsumerTenantId(String consumerTenantId) {
        return producerToConsumerTenantMap.containsValue(consumerTenantId);
    }

    @Override
    public String toString() {
        return "FederatedMappings [producerToConsumerNetworkMap=" + producerToConsumerNetworkMap
                + ", producerToConsumerSubnetMap=" + producerToConsumerSubnetMap + ", producerToConsumerTenantMap="
                + producerToConsumerTenantMap + "]";
    }


}
