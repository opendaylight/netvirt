/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.collect.ImmutableBiMap;
import java.util.Collections;
import java.util.Objects;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.ProtocolBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.ProtocolIcmp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.ProtocolIcmpV6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.ProtocolTcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.ProtocolUdp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.SecurityRuleAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.security.rules.attributes.SecurityRules;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.secgroups.rev150712.security.rules.attributes.security.rules.SecurityRule;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronSecurityRuleListener extends AbstractAsyncDataTreeChangeListener<SecurityRule> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSecurityRuleListener.class);
    private static final ImmutableBiMap<Class<? extends DirectionBase>,
        Class<?extends org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase>>
        DIRECTION_MAP = ImmutableBiMap.of(
            DirectionEgress.class, NeutronSecurityGroupConstants.DIRECTION_EGRESS,
            DirectionIngress.class, NeutronSecurityGroupConstants.DIRECTION_INGRESS);
    private static final ImmutableBiMap<Class<? extends ProtocolBase>, Short> PROTOCOL_MAP = ImmutableBiMap.of(
            ProtocolIcmp.class, NeutronSecurityGroupConstants.PROTOCOL_ICMP,
            ProtocolTcp.class, NeutronSecurityGroupConstants.PROTOCOL_TCP,
            ProtocolUdp.class, NeutronSecurityGroupConstants.PROTOCOL_UDP,
            ProtocolIcmpV6.class, NeutronSecurityGroupConstants.PROTOCOL_ICMPV6);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final JobCoordinator jobCoordinator;

    @Inject
    public NeutronSecurityRuleListener(final DataBroker dataBroker, JobCoordinator jobCoordinator) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(SecurityRules.class).child(SecurityRule.class),
                Executors.newSingleThreadExecutor("NeutronSecurityRuleListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.jobCoordinator = jobCoordinator;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void add(InstanceIdentifier<SecurityRule> instanceIdentifier, SecurityRule securityRule) {
        LOG.trace("added securityRule: {}", securityRule);
        try {
            Ace ace = toAceBuilder(securityRule, false).build();
            InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(securityRule);
            String jobKey = securityRule.getSecurityGroupId().getValue();
            jobCoordinator.enqueueJob(jobKey,
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> tx.mergeParentStructurePut(identifier, ace))),
                    NeutronSecurityGroupConstants.DJC_MAX_RETRIES);
        } catch (Exception ex) {
            LOG.error("Exception occured while adding acl for security rule: {}. ", securityRule, ex);
        }
    }

    private InstanceIdentifier<Ace> getAceInstanceIdentifier(SecurityRule securityRule) {
        return InstanceIdentifier
                .builder(AccessLists.class)
                .child(Acl.class,
                        new AclKey(securityRule.getSecurityGroupId().getValue(), NeutronSecurityGroupConstants.ACLTYPE))
                .child(AccessListEntries.class)
                .child(Ace.class,
                        new AceKey(securityRule.getUuid().getValue()))
                .build();
    }

    private AceBuilder toAceBuilder(SecurityRule securityRule, boolean isDeleted) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
        DestinationPortRangeBuilder destinationPortRangeBuilder = new DestinationPortRangeBuilder();
        boolean isDirectionIngress = false;
        if (securityRule.getDirection() != null) {
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
        }
        securityRuleAttrBuilder.setDeleted(isDeleted);

        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        // set acl action as permit for the security rule
        ActionsBuilder actionsBuilder = new ActionsBuilder();
        actionsBuilder.setPacketHandling(new PermitBuilder().setPermit(Empty.getInstance()).build());

        AceBuilder aceBuilder = new AceBuilder();
        aceBuilder.withKey(new AceKey(securityRule.getUuid().getValue()));
        aceBuilder.setRuleName(securityRule.getUuid().getValue());
        aceBuilder.setMatches(matchesBuilder.build());
        aceBuilder.setActions(actionsBuilder.build());
        aceBuilder.addAugmentation(securityRuleAttrBuilder.build());
        return aceBuilder;
    }

    private AceIpBuilder handleEtherType(SecurityRule securityRule, AceIpBuilder aceIpBuilder) {
        if (NeutronSecurityGroupConstants.ETHERTYPE_IPV4.equals(securityRule.getEthertype())) {
            AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
            aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(
                NeutronSecurityGroupConstants.IPV4_ALL_NETWORK));
            aceIpv4Builder.setDestinationIpv4Network(new Ipv4Prefix(
                NeutronSecurityGroupConstants.IPV4_ALL_NETWORK));
            aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());
        } else {
            AceIpv6Builder aceIpv6Builder = new AceIpv6Builder();
            aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(
                NeutronSecurityGroupConstants.IPV6_ALL_NETWORK));
            aceIpv6Builder.setDestinationIpv6Network(new Ipv6Prefix(
                NeutronSecurityGroupConstants.IPV6_ALL_NETWORK));
            aceIpBuilder.setAceIpVersion(aceIpv6Builder.build());

        }
        return aceIpBuilder;
    }

    private AceIpBuilder handleRemoteIpPrefix(SecurityRule securityRule, AceIpBuilder aceIpBuilder,
                                              boolean isDirectionIngress) {
        if (securityRule.getRemoteIpPrefix() != null) {
            if (securityRule.getRemoteIpPrefix().getIpv4Prefix() != null) {
                AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
                if (isDirectionIngress) {
                    aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(securityRule
                        .getRemoteIpPrefix().getIpv4Prefix().getValue()));
                } else {
                    aceIpv4Builder.setDestinationIpv4Network(new Ipv4Prefix(securityRule
                        .getRemoteIpPrefix().getIpv4Prefix().getValue()));
                }
                aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());
            } else {
                AceIpv6Builder aceIpv6Builder = new AceIpv6Builder();
                if (isDirectionIngress) {
                    aceIpv6Builder.setSourceIpv6Network(new Ipv6Prefix(
                        securityRule.getRemoteIpPrefix().getIpv6Prefix().getValue()));
                } else {
                    aceIpv6Builder.setDestinationIpv6Network(new Ipv6Prefix(
                        securityRule.getRemoteIpPrefix().getIpv6Prefix().getValue()));
                }
                aceIpBuilder.setAceIpVersion(aceIpv6Builder.build());
            }
        } else {
            if (securityRule.getEthertype() != null) {
                handleEtherType(securityRule, aceIpBuilder);
            }
        }

        return aceIpBuilder;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void remove(InstanceIdentifier<SecurityRule> instanceIdentifier, SecurityRule securityRule) {
        LOG.trace("removed securityRule: {}", securityRule);
        InstanceIdentifier<Ace> identifier = getAceInstanceIdentifier(securityRule);
        try {
            Ace ace = toAceBuilder(securityRule, true).build();
            String jobKey = securityRule.getSecurityGroupId().getValue();
            jobCoordinator.enqueueJob(jobKey,
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> tx.mergeParentStructureMerge(identifier, ace))),
                    NeutronSecurityGroupConstants.DJC_MAX_RETRIES);
        } catch (Exception ex) {
            /*
            If there are out of sequence events where-in Sg-Rule delete could occur after SecurityGroup delete.
            we would hit with exception here, trying to delete a child-node, after it's parent has got deleted.
            Logging it as Warn, because if such event occur we are handling it in the AclEventListeners' Remove method.
             */
            LOG.warn("Exception occured while removing acl for security rule: {}. ", securityRule, ex);
        }
    }

    @Override
    public void update(InstanceIdentifier<SecurityRule> instanceIdentifier,
                          SecurityRule oldSecurityRule, SecurityRule updatedSecurityRule) {
        // security rule updation is not supported from openstack, so no need to handle update.
        LOG.trace("updates on security rules not supported.");
        if (Objects.equals(oldSecurityRule, updatedSecurityRule)) {
            return;
        }
    }
}
