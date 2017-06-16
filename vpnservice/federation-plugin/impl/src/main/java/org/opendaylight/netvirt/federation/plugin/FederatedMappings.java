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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class FederatedMappings {

    private final Map<String, String> producerToConsumerNetworkMap = Maps.newConcurrentMap();
    private final Map<String, String> producerToConsumerSubnetMap = Maps.newConcurrentMap();
    private final Map<String, String> producerToConsumerTenantMap = Maps.newConcurrentMap();
    private final Map<Uuid, Uuid> producerToConsumerAclsMap = Maps.newConcurrentMap();

    public FederatedMappings(List<FederatedNetworkPair> federatedNetworkPairs,
        List<FederatedAclPair> aclsPairs) {
        federatedNetworkPairs.forEach(
            (pair) -> producerToConsumerNetworkMap.put(pair.getProducerNetworkId(), pair.getConsumerNetworkId()));
        federatedNetworkPairs.forEach(
            (pair) -> producerToConsumerSubnetMap.put(pair.getProducerSubnetId(), pair.getConsumerSubnetId()));
        federatedNetworkPairs.forEach(
            (pair) -> producerToConsumerTenantMap.put(pair.getProducerTenantId(), pair.getConsumerTenantId()));
        if (aclsPairs != null) {
            aclsPairs.forEach(
                (pair) -> producerToConsumerAclsMap.put(pair.producerAclId, pair.consumerAclId));
        }
    }

    public Uuid getConsumerAclId(Uuid producerAclId) {
        return producerToConsumerAclsMap.get(producerAclId);
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
            + producerToConsumerTenantMap + ", producerToConsumerAclsMap=" + producerToConsumerAclsMap + "]";
    }
}
