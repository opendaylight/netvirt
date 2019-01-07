/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import java.util.List;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.PolicyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.NetworkPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.rev181205.K8s;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

final class NetworkPolicyUtils {
    private NetworkPolicyUtils() {}

    static InstanceIdentifier<NetworkPolicy> getNetworkPolicyIid(String uuid) {
        return InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class)
            .child(NetworkPolicy.class, new NetworkPolicyKey(new Uuid(uuid)));
    }

    static NetworkPolicy buildNetworkPolicy(String uuid, List<PolicyType> policyTypes) {
        NetworkPolicyBuilder networkPolicyBuilder = new NetworkPolicyBuilder();

        if (uuid != null) {
            networkPolicyBuilder.setUuid(new Uuid(uuid));
        }

        if (policyTypes != null) {
            networkPolicyBuilder.setPolicyTypes(policyTypes);
        }

        return networkPolicyBuilder.build();
    }
}
