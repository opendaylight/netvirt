/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.NetworkPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.rev181205.K8s;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NetworkPolicyListener extends AbstractSyncDataTreeChangeListener<NetworkPolicy> {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkPolicyListener.class);

    @Inject
    public NetworkPolicyListener(DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
            //InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class).child(NetworkPolicy.class));
            InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class).child(NetworkPolicy.class));
        LOG.info("constructor");
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<NetworkPolicy> instanceIdentifier, @Nonnull NetworkPolicy policy) {
        LOG.info("add: id: {}\npolicy: {}", instanceIdentifier, policy);
    }

    @Override
    public void remove(@Nonnull InstanceIdentifier<NetworkPolicy> instanceIdentifier, @Nonnull NetworkPolicy policy) {
        LOG.info("remove: id: {}\npolicy: {}", instanceIdentifier, policy);
    }

    @Override
    public void update(@Nonnull InstanceIdentifier<NetworkPolicy> instanceIdentifier,
                       @Nonnull NetworkPolicy oldPolicy, @Nonnull NetworkPolicy policy) {
        LOG.info("update: id: {}\nold policy: {}\nnew policy: {}", instanceIdentifier, oldPolicy, policy);
    }
}
