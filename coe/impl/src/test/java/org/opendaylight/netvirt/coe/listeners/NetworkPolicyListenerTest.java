/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.binding.util.RetryingManagedNewTransactionRunner;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.PolicyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
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
        networkPolicyListener.register();
    }

    @Test
    public void testNetworkPolicy() throws ExecutionException, InterruptedException {
        String policyUuid = "11111111-1111-1111-1111-111111111111";
        InstanceIdentifier<NetworkPolicy> networkPolicyIid = NetworkPolicyUtils.getNetworkPolicyIid(policyUuid);
        List<PolicyType> policyTypes = Collections.singletonList(PolicyType.Egress);
        NetworkPolicy networkPolicy = NetworkPolicyUtils.buildNetworkPolicy(policyUuid, policyTypes);

        RetryingManagedNewTransactionRunner txRunner =
            new RetryingManagedNewTransactionRunner(dataBroker, 3);

        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            tx.put(networkPolicyIid, networkPolicy);
        }), LOG, "writing network policy").get();

        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<NetworkPolicy> networkPolicyOptional = tx.read(networkPolicyIid).get();
            assertTrue(networkPolicyOptional.isPresent());
            assertEquals(networkPolicyOptional.get().getPolicyTypes(), policyTypes);
        }), LOG, "reading network policy");
    }
}
