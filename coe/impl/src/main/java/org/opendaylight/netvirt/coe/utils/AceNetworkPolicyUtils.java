/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.utils;

import static org.opendaylight.netvirt.coe.utils.AclUtils.DIRECTION_MAP;
import static org.opendaylight.netvirt.coe.utils.AclUtils.buildName;
import static org.opendaylight.netvirt.coe.utils.NetworkPolicyUtils.PROTOCOL_MAP;

import java.util.ArrayList;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntriesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.MatchesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.PermitBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.NetworkPolicyEgressRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.EgressPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.egress.rule.network.policy.egress.rule.To;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.NetworkPolicyIngressRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.From;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.ingress.rule.network.policy.ingress.rule.IngressPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.network.policies.NetworkPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.peer.NetworkPolicyPeer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.port.NetworkPolicyPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.NetworkPolicySpec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.Egress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.k8s.network.policy.rev181205.network.policy.spec.network.policy.spec.Ingress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttrBuilder;
import org.opendaylight.yangtools.yang.common.Empty;

public final class AceNetworkPolicyUtils {
    private AceNetworkPolicyUtils() {}

    @NonNull
    public static String getAclNameFromPolicy(@NonNull NetworkPolicy policy) {
        String aclName = "";
        if (policy.getUuid() != null) {
            aclName = policy.getUuid().getValue();
        }
        return aclName;
    }

    // TODO map empty rules:
    // ingress:empty - no incoming allowed
    // egress:empty - no outgoing allowed
    @NonNull
    public static Acl buildAcl(@NonNull NetworkPolicy policy, boolean isDeleted) {
        String aclName = getAclNameFromPolicy(policy);
        ArrayList<Ace> aceList = new ArrayList<>();

        if (policy.getNetworkPolicySpec() != null) {
            NetworkPolicySpec spec = policy.getNetworkPolicySpec();
            if (spec.getIngress() != null) {
                for (Ingress ingress : spec.getIngress()) {
                    NetworkPolicyIngressRule rule = ingress.getNetworkPolicyIngressRule();
                    if (rule.getIngressPorts() != null) {
                        for (IngressPorts port : rule.getIngressPorts()) {
                            if (port.getNetworkPolicyPort() != null) {
                                Ace ace = buildPortAce(isDeleted, aclName,
                                    DirectionIngress.class, port.getNetworkPolicyPort());
                                aceList.add(ace);
                            }
                        }
                    }
                    if (rule.getFrom() != null) {
                        for (From from: rule.getFrom()) {
                            if (from.getNetworkPolicyPeer() != null) {
                                Ace ace = buildPolicyAce(isDeleted, aclName,
                                    DirectionIngress.class, from.getNetworkPolicyPeer());
                                aceList.add(ace);
                            }
                        }
                    }
                }
            }

            if (spec.getEgress() != null) {
                for (Egress egress : spec.getEgress()) {
                    NetworkPolicyEgressRule rule = egress.getNetworkPolicyEgressRule();
                    if (rule.getEgressPorts() != null) {
                        for (EgressPorts port : rule.getEgressPorts()) {
                            if (port.getNetworkPolicyPort() != null) {
                                Ace ace = buildPortAce(isDeleted, aclName,
                                    DirectionEgress.class, port.getNetworkPolicyPort());
                                aceList.add(ace);
                            }
                        }
                    }
                    if (rule.getTo() != null) {
                        for (To to: rule.getTo()) {
                            if (to.getNetworkPolicyPeer() != null) {
                                Ace ace = buildPolicyAce(isDeleted, aclName,
                                    DirectionEgress.class, to.getNetworkPolicyPeer());
                                aceList.add(ace);
                            }
                        }
                    }
                }
            }
        }

        AccessListEntriesBuilder accessListEntriesBuilder = new AccessListEntriesBuilder();
        accessListEntriesBuilder.setAce(aceList);

        AclBuilder aclBuilder = new AclBuilder();
        aclBuilder.setAclName(aclName);
        aclBuilder.setAclType(Ipv4Acl.class);
        aclBuilder.setAccessListEntries(accessListEntriesBuilder.build());
        aclBuilder.withKey(new AclKey(aclBuilder.getAclName(), aclBuilder.getAclType()));
        return aclBuilder.build();
    }

    @NonNull
    public static AceBuilder getAceBuilder(boolean isDeleted, String ruleName,
                                           @NonNull Class<? extends DirectionBase> direction,
                                           @NonNull AceIpBuilder aceIpBuilder) {
        MatchesBuilder matchesBuilder = new MatchesBuilder();
        matchesBuilder.setAceType(aceIpBuilder.build());
        ActionsBuilder actionsBuilder = new ActionsBuilder();
        actionsBuilder.setPacketHandling(new PermitBuilder().setPermit(Empty.getInstance()).build());

        AceBuilder aceBuilder = new AceBuilder();
        aceBuilder.setRuleName(ruleName);
        aceBuilder.withKey(new AceKey(aceBuilder.getRuleName()));
        aceBuilder.setMatches(matchesBuilder.build());
        aceBuilder.setActions(actionsBuilder.build());

        SecurityRuleAttrBuilder securityRuleAttrBuilder = new SecurityRuleAttrBuilder();
        securityRuleAttrBuilder.setDeleted(isDeleted);
        securityRuleAttrBuilder.setDirection(direction);
        aceBuilder.addAugmentation(SecurityRuleAttr.class, securityRuleAttrBuilder.build());
        return aceBuilder;
    }

    @NonNull
    public static Ace buildPortAce(boolean isDeleted, @NonNull String aclName,
                                   @NonNull Class<? extends DirectionBase> direction,
                                   @NonNull NetworkPolicyPort port) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        String ruleName = AclUtils.buildName(aclName, DIRECTION_MAP.get(direction), "port");
        if (port.getProtocol() != null) {
            aceIpBuilder.setProtocol(PROTOCOL_MAP.get(port.getProtocol()));
            ruleName = buildName(ruleName, port.getProtocol().toString());
        }

        // TODO: map a named port
        if (port.getPort() != null) {
            DestinationPortRangeBuilder portRangeBuilder = new DestinationPortRangeBuilder();
            PortNumber portNumber = new PortNumber(Integer.parseInt(port.getPort()));
            portRangeBuilder.setLowerPort(portNumber);
            portRangeBuilder.setUpperPort(portNumber);
            aceIpBuilder.setDestinationPortRange(portRangeBuilder.build());
            ruleName = buildName(ruleName, portNumber.getValue().toString());
        }

        AceBuilder aceBuilder = getAceBuilder(isDeleted, ruleName, direction, aceIpBuilder);

        return aceBuilder.build();
    }

    @NonNull
    public static Ace buildPolicyAce(boolean isDeleted, @NonNull String aclName,
                                     @NonNull Class<? extends DirectionBase> direction,
                                     @NonNull NetworkPolicyPeer peer) {
        AceIpBuilder aceIpBuilder = new AceIpBuilder();
        String ruleName = AclUtils.buildName(aclName, DIRECTION_MAP.get(direction), "peer");

        if (peer.getIpBlock() != null) {
            // TODO handle except
            String  cidr = peer.getIpBlock().getCidr();
            ruleName = AclUtils.buildName(ruleName, "cidr", cidr);
            AceIpv4Builder aceIpv4Builder = new AceIpv4Builder();
            if (direction == DirectionIngress.class) {
                aceIpv4Builder.setSourceIpv4Network(new Ipv4Prefix(cidr));
            } else {
                aceIpv4Builder.setDestinationIpv4Network(new Ipv4Prefix(cidr));
            }
            aceIpBuilder.setAceIpVersion(aceIpv4Builder.build());
        }
        // TODO handle pod-selector and namespace-selector

        AceBuilder aceBuilder = getAceBuilder(isDeleted, ruleName, direction, aceIpBuilder);

        return aceBuilder.build();
    }
}
