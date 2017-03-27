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
    private final Map<Uuid, Uuid> producerToConsumerAclsMap = Maps.newConcurrentMap();

    public FederatedMappings(List<FederatedNetworkPair> federatedNetworkPairs,
        List<FederatedAclPair> aclsPairs) {
        federatedNetworkPairs.stream()
            .forEach((pair) -> producerToConsumerNetworkMap.put(pair.producerNetworkId, pair.consumerNetworkId));
        federatedNetworkPairs.stream()
            .forEach((pair) -> producerToConsumerSubnetMap.put(pair.producerSubnetId, pair.consumerSubnetId));
        if (aclsPairs != null) {
            aclsPairs.stream().forEach(
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

    @Override
    public String toString() {
        return "FederatedMappings [producerToConsumerNetworkMap=" + producerToConsumerNetworkMap
            + ", producerToConsumerSubnetMap=" + producerToConsumerSubnetMap + ", producerToConsumerSecurityGroupsMap="
            + producerToConsumerAclsMap + "]";
    }
}
