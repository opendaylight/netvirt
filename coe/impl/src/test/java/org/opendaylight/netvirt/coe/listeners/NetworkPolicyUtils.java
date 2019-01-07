/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.NetworkPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.network.policies.NetworkPolicyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.network.policies.NetworkPolicyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.rev181205.K8s;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class NetworkPolicyUtils {
    private NetworkPolicyUtils() {}

    public static InstanceIdentifier<NetworkPolicy> getNetworkPolicyId(String uuid) {
        return InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class)
            .child(NetworkPolicy.class, new NetworkPolicyKey(new Uuid(uuid)));
    }

    public static NetworkPolicy buildNetworkPolicy() {
        return new NetworkPolicyBuilder().build();
    }
}
