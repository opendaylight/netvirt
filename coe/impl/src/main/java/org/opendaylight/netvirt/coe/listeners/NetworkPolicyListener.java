/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.RetryingManagedNewTransactionRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.NetworkPolicies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.rev181205.K8s;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NetworkPolicyListener extends AbstractSyncDataTreeChangeListener<NetworkPolicy> {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkPolicyListener.class);
    private final RetryingManagedNewTransactionRunner txRunner;

    @Inject
    public NetworkPolicyListener(DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(K8s.class).child(NetworkPolicies.class).child(NetworkPolicy.class));
        this.txRunner = new RetryingManagedNewTransactionRunner(dataBroker);
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<NetworkPolicy> instanceIdentifier, @Nonnull NetworkPolicy policy) {
        LOG.info("add: id: {}\npolicy: {}", instanceIdentifier, policy);
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            Ace ace = toAceBuilder(policy, false).build();
            InstanceIdentifier<Ace> aceIid = getAceIid(policy);
            tx.mergeParentStructurePut(aceIid, ace);
        }), LOG, "Failed to add acl from policy: {}", policy);
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

    private InstanceIdentifier<Ace> getAceIid(NetworkPolicy policy) {
        return InstanceIdentifier
            .builder(AccessLists.class)
            .child(Acl.class, new AclKey(policy.getUuid().getValue(), Ipv4Acl.class))
            .child(AccessListEntries.class)
            .child(Ace.class, new AceKey(policy.getUuid().getValue()))
            .build();
    }

    private AceBuilder toAceBuilder(NetworkPolicy policy, boolean isDeleted) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
        DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
        boolean isDirectionIngress = false;
        /*if (securityRule.getDirection() != null) {
            securityRuleAttrBuilder.setDirection(DIRECTION_MAP.get(securityRule.getDirection()));
            isDirectionIngress = securityRule.getDirection().equals(DirectionIngress.class);
        }
        if (securityRule.getPortRangeMax() != null) {
            destinationPortRangeBuilder.setUpperPort(new PortNumber(securityRule.getPortRangeMax()));

        }
        if (securityRule.getPortRangeMin() != null) {
            destinationPortRangeBuilder.setLowerPort(new PortNumber(securityRule.getPortRangeMin()));
            // set destination port range if lower port is specified as it is mandatory parameter in acl model
            aceIpBuilder.setDestinationPortRange(destinationPortRangeBuilder.build());
        }
        aceIpBuilder = handleRemoteIpPrefix(securityRule, aceIpBuilder, isDirectionIngress);
        if (securityRule.getRemoteGroupId() != null) {
            securityRuleAttrBuilder.setRemoteGroupId(securityRule.getRemoteGroupId());
        }
        if (securityRule.getProtocol() != null) {
            SecurityRuleAttributes.Protocol protocol = securityRule.getProtocol();
            if (protocol.getUint8() != null) {
                // uint8
                aceIpBuilder.setProtocol(protocol.getUint8());
            } else {
                // symbolic protocol name
                aceIpBuilder.setProtocol(PROTOCOL_MAP.get(protocol.getIdentityref()));
            }
        }*/
        securityRuleAttrBuilder.setDeleted(isDeleted);

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        // set acl action as permit for the security rule
        ActionsBuilder actionsBuilder = new ActionsBuilder();
        actionsBuilder.setPacketHandling(new PermitBuilder().setPermit(true).build());

        AceBuilder aceBuilder = new AceBuilder();
        aceBuilder.withKey(new AceKey(policy.getUuid().getValue()));
        aceBuilder.setRuleName(policy.getUuid().getValue());
        aceBuilder.setMatches(matchesBuilder.build());
        aceBuilder.setActions(actionsBuilder.build());
        aceBuilder.addAugmentation(SecurityRuleAttr.class, securityRuleAttrBuilder.build());
        return aceBuilder;
    }
}
