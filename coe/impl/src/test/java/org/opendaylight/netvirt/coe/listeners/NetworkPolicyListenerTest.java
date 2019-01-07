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
import static org.opendaylight.netvirt.coe.utils.AclUtils.getAceIid;
import static org.opendaylight.netvirt.coe.utils.AclUtils.getAclIid;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildEgress;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildEgressPorts;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildFrom;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildIngress;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildIngressPorts;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildIpBlock;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildMatchLabels;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildNetworkPolicy;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildNetworkPolicyEgressRule;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildNetworkPolicyIngressRule;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildNetworkPolicyPeer;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildNetworkPolicyPort;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildNetworkPolicySpec;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildPodSelector;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.buildTo;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.getNetworkPolicyIid;

import java.util.Arrays;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.core.rev181205.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.meta.v1.rev181205.label.selector.MatchLabels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.PolicyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.NetworkPolicyEgressRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.EgressPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.To;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.NetworkPolicyIngressRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.From;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.IngressPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.peer.NetworkPolicyPeer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.NetworkPolicySpec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.Egress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.Ingress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.PodSelector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkPolicyListenerTest extends AbstractConcurrentDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkPolicyListenerTest.class);
    private RetryingManagedNewTransactionRunner txRunner;
    private Verifications verifications;

    @Before
    public void setUp() {
        DataBroker dataBroker = getDataBroker();
        NetworkPolicyListener networkPolicyListener = new NetworkPolicyListener(dataBroker);
        networkPolicyListener.register();
        verifications = new Verifications(dataBroker);
        txRunner = new RetryingManagedNewTransactionRunner(dataBroker, 3);
    }

    @Test
    public void testNetworkPolicy() throws ExecutionException, InterruptedException {
        String policyUuid = "11111111-1111-1111-1111-111111111111";
        String policyName = "network-policy-1";

        NetworkPolicy networkPolicy1 = buildNetworkPolicy1(policyUuid, policyName);
        InstanceIdentifier<NetworkPolicy> networkPolicyIid = getNetworkPolicyIid(policyUuid);

        // write and verify NetworkPolicy to ds
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            tx.mergeParentStructureMerge(networkPolicyIid, networkPolicy1);
        }), LOG, "writing network policy").get();

        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<NetworkPolicy> networkPolicyOptional = tx.read(networkPolicyIid).get();
            assertTrue(networkPolicyOptional.isPresent());
            assertEquals(networkPolicyOptional.get().getName(), policyName);
        }), LOG, "reading network policy {}", networkPolicyIid);

        // wait and verify for the NetworkPolicyListener to write an acl from the policy just written
        InstanceIdentifier<Acl> aclIid = getAclIid(policyUuid);
        verifications.awaitForData(CONFIGURATION, aclIid);
        verifyAcl(policyUuid);

        // verify children Ace's have been written
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_port_TCP_53", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_port_UDP_53", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_peer_cidr_10.1.1.1/16", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_egress_port_TCP_53", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_egress_port_UDP_53", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_egress_peer_cidr_10.1.1.1/16", false);

        // Update the policy and verify the acl is updated

        NetworkPolicy networkPolicy2 = buildNetworkPolicy2(policyUuid, policyName + "updated");
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            tx.mergeParentStructureMerge(networkPolicyIid, networkPolicy2);
        }), LOG, "writing network policy").get();

        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<NetworkPolicy> networkPolicyOptional = tx.read(networkPolicyIid).get();
            assertTrue(networkPolicyOptional.isPresent());
            assertEquals(networkPolicyOptional.get().getName(), policyName + "updated");
        }), LOG, "reading network policy {}", networkPolicyIid);

        // wait and verify for the NetworkPolicyListener to write an acl from the policy just written
        verifications.awaitForData(CONFIGURATION, aclIid);
        verifyAcl(policyUuid);
        // verify children Ace's have been written
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_port_TCP_69", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_port_UDP_69", false);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_peer_cidr_10.2.1.1/16", false);

        // wait and verify a deleted policy will updates acls to deleted
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            tx.delete(networkPolicyIid);
        }), LOG, "deleting network policy").get();
        verifications.awaitForData(CONFIGURATION, aclIid);
        verifyAce(policyUuid, "11111111-1111-1111-1111-111111111111_ingress_port_TCP_69", true);
    }

    private void verifyAcl(String policyUuid) {
        InstanceIdentifier<Acl> aclIid = getAclIid(policyUuid);
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<Acl> aclOptional = tx.read(aclIid).get();
            assertTrue(aclOptional.isPresent());
            LOG.info("acl: {}", aclOptional.get());
        }), LOG, "reading acl {}", aclIid);
    }

    private void verifyAce(String policyUuid, String ruleName, boolean isDeleted) {
        InstanceIdentifier<Ace> aceIid = getAceIid(policyUuid, ruleName);
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<Ace> aceOptional = tx.read(aceIid).get();
            assertTrue(aceOptional.isPresent());
            assertEquals(aceOptional.get().augmentation(SecurityRuleAttr.class).isDeleted(), isDeleted);
            LOG.info("ace: {}", aceOptional.get());
        }), LOG, "reading ace {}", aceIid);
    }

    private NetworkPolicy buildNetworkPolicy1(String policyUuid, String policyName) {
        List<IngressPorts> ingressPorts = Arrays.asList(
            buildIngressPorts(buildNetworkPolicyPort("53", Protocol.TCP)),
            buildIngressPorts(buildNetworkPolicyPort("53", Protocol.UDP)));
        List<String> except = Arrays.asList("10.1.2.1/24", "10.1.3.1/24");
        NetworkPolicyPeer ingressNetworkPolicyPeer = buildNetworkPolicyPeer(buildIpBlock("10.1.1.1/16", except));
        List<From> fromList = Collections.singletonList(buildFrom(ingressNetworkPolicyPeer));
        NetworkPolicyIngressRule networkPolicyIngressRule = buildNetworkPolicyIngressRule(ingressPorts, fromList);
        List<Ingress> ingressList = Collections.singletonList(buildIngress(networkPolicyIngressRule));

        List<EgressPorts> egressPorts = Arrays.asList(
            buildEgressPorts(buildNetworkPolicyPort("53", Protocol.TCP)),
            buildEgressPorts(buildNetworkPolicyPort("53", Protocol.UDP)));
        List<String> exceptEgress = Arrays.asList("10.1.2.1/24", "10.1.3.1/24");
        NetworkPolicyPeer egressNetworkPolicyPeer = buildNetworkPolicyPeer(buildIpBlock("10.1.1.1/16", exceptEgress));
        List<To> toList = Collections.singletonList(buildTo(egressNetworkPolicyPeer));
        NetworkPolicyEgressRule networkPolicyEgressRule = buildNetworkPolicyEgressRule(egressPorts, toList);
        List<Egress> egressList = Collections.singletonList(buildEgress(networkPolicyEgressRule));

        List<PolicyType> policyTypes = Collections.singletonList(PolicyType.Egress);
        MatchLabels matchLabels = buildMatchLabels("app", "db");
        PodSelector podSelector = buildPodSelector(Collections.singletonList(matchLabels));
        NetworkPolicySpec networkPolicySpec = buildNetworkPolicySpec(podSelector, ingressList, egressList, policyTypes);
        return buildNetworkPolicy(policyUuid, policyName, networkPolicySpec);
    }

    private NetworkPolicy buildNetworkPolicy2(String policyUuid, String policyName) {
        List<IngressPorts> ingressPorts = Arrays.asList(
            buildIngressPorts(buildNetworkPolicyPort("69", Protocol.TCP)),
            buildIngressPorts(buildNetworkPolicyPort("69", Protocol.UDP)));
        List<String> except = Arrays.asList("10.2.2.1/24", "10.2.3.1/24");
        NetworkPolicyPeer ingressNetworkPolicyPeer = buildNetworkPolicyPeer(buildIpBlock("10.2.1.1/16", except));
        List<From> fromList = Collections.singletonList(buildFrom(ingressNetworkPolicyPeer));
        NetworkPolicyIngressRule networkPolicyIngressRule = buildNetworkPolicyIngressRule(ingressPorts, fromList);
        List<Ingress> ingressList = Collections.singletonList(buildIngress(networkPolicyIngressRule));

        List<EgressPorts> egressPorts = Arrays.asList(
            buildEgressPorts(buildNetworkPolicyPort("53", Protocol.TCP)),
            buildEgressPorts(buildNetworkPolicyPort("53", Protocol.UDP)));
        List<String> exceptEgress = Arrays.asList("10.1.2.1/24", "10.1.3.1/24");
        NetworkPolicyPeer egressNetworkPolicyPeer = buildNetworkPolicyPeer(buildIpBlock("10.1.1.1/16", exceptEgress));
        List<To> toList = Collections.singletonList(buildTo(egressNetworkPolicyPeer));
        NetworkPolicyEgressRule networkPolicyEgressRule = buildNetworkPolicyEgressRule(egressPorts, toList);
        List<Egress> egressList = Collections.singletonList(buildEgress(networkPolicyEgressRule));

        List<PolicyType> policyTypes = Collections.singletonList(PolicyType.Egress);
        MatchLabels matchLabels = buildMatchLabels("app", "db");
        PodSelector podSelector = buildPodSelector(Collections.singletonList(matchLabels));
        NetworkPolicySpec networkPolicySpec = buildNetworkPolicySpec(podSelector, ingressList, egressList, policyTypes);
        return buildNetworkPolicy(policyUuid, policyName, networkPolicySpec);
    }
}
