/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.netvirt.coe.listeners.NetworkPolicyUtils.getNetworkPolicyId;

import com.google.common.util.concurrent.FluentFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
//import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.binding.util.RetryingManagedNewTransactionRunner;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.PolicyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.attrs.network.policies.NetworkPolicyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkPolicyListenerTest extends AbstractConcurrentDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkPolicyListenerTest.class);
    private DataBroker dataBroker;

    @Before
    public void setUp() {
        dataBroker = getDataBroker();
        NetworkPolicyListener networkPolicyListener = new NetworkPolicyListener(dataBroker);
        K8sListener k8sListener = new K8sListener(dataBroker);
    }

    @Test
    public void testFoo() throws ExecutionException, InterruptedException {
        LOG.info("Here I am!");
        assertTrue(TRUE);

        String uuid = "11111111-1111-1111-1111-111111111111";
        List<PolicyType> policyTypes = new ArrayList<>();
        policyTypes.add(PolicyType.Egress);
        NetworkPolicy networkPolicy =
            new NetworkPolicyBuilder().setUuid(new Uuid(uuid)).setPolicyTypes(policyTypes).build();
        InstanceIdentifier<NetworkPolicy> networkPolicyId = getNetworkPolicyId(uuid);
        RetryingManagedNewTransactionRunner txRunner =
            new RetryingManagedNewTransactionRunner(dataBroker, 3);
        FluentFuture<?> future = txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            tx.put(networkPolicyId, networkPolicy);
        });
        LOG.info("future: {}", future);
        future.get();
        LOG.info("future: {}", future);
        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<NetworkPolicy> networkPolicyOptional = tx.read(networkPolicyId).get();
            assertTrue(networkPolicyOptional.isPresent());
            assertEquals(networkPolicyOptional.get().getPolicyTypes(), policyTypes);
        });
    }
}
