/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AclBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.SetPolicyClassifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.UnderlayNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.acl.rules.PolicyAclRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.acl.rules.PolicyAclRuleKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.acl.rules.policy.acl.rule.AceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.acl.rules.policy.acl.rule.AceRuleBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.acl.rules.policy.acl.rule.AceRuleKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfileKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.route.Route;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.route.route.BasicRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyServiceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyServiceUtil.class);

    private final DataBroker dataBroker;

    @Inject
    public PolicyServiceUtil(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public static boolean isPolicyAcl(Class<? extends AclBase> aclType) {
        return aclType != null && aclType.isAssignableFrom(PolicyAcl.class);
    }

    public String getAcePolicyClassifier(Ace ace) {
        Actions actions = ace.getActions();
        SetPolicyClassifier setPolicyClassifier = actions.getAugmentation(SetPolicyClassifier.class);
        if (setPolicyClassifier == null) {
            LOG.warn("No valid policy action found for ACE rule {}", ace.getRuleName());
            return null;
        }

        if (setPolicyClassifier.getDirection() == null
                || setPolicyClassifier.getDirection().isAssignableFrom(DirectionEgress.class)) {
            LOG.trace("Ignoring non egress policy ACE rule {}", ace.getRuleName());
            return null;
        }

        return setPolicyClassifier.getPolicyClassifier();

    }

    public Ace getPolicyAce(String aclName, String ruleName) {
        InstanceIdentifier<Ace> identifier = InstanceIdentifier.create(AccessLists.class)
                .child(Acl.class, new AclKey(aclName, PolicyAcl.class)).child(AccessListEntries.class)
                .child(Ace.class, new AceKey(ruleName));
        try {
            return SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.CONFIGURATION, identifier);
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy ACE rule {} for ACL {}", ruleName, aclName);
            return null;
        }

    }

    public List<String> getUnderlayNetworksForClassifier(String policyClassifierName) {
        InstanceIdentifier<PolicyProfile> identifier = InstanceIdentifier.create(PolicyProfiles.class)
                .child(PolicyProfile.class, new PolicyProfileKey(policyClassifierName));
        PolicyProfile policyProfile;
        try {
            policyProfile = SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    identifier);
            return policyProfile != null ? getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute())
                    : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy routes for classifier {}", policyClassifierName);
            return Collections.emptyList();
        }
    }

    public List<PolicyAclRule> getUnderlayNetworkAclRules(String underlayNetworkName) {
        InstanceIdentifier<UnderlayNetwork> identifier = InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetworkName));
        try {
            UnderlayNetwork underlayNetwork = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return underlayNetwork != null ? underlayNetwork.getPolicyAclRule() : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy rules for underlay network {}", underlayNetworkName);
            return Collections.emptyList();
        }
    }

    public void updateUnderlayNetworksAceRule(List<String> underlayNetworks, String aclName, String ruleName) {
        if (underlayNetworks == null) {
            return;
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        underlayNetworks.forEach(underlayNetwork -> {
            InstanceIdentifier<AceRule> identifier = InstanceIdentifier.create(UnderlayNetworks.class)
                    .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork))
                    .child(PolicyAclRule.class, new PolicyAclRuleKey(aclName))
                    .child(AceRule.class, new AceRuleKey(ruleName));
            tx.put(LogicalDatastoreType.OPERATIONAL, identifier, new AceRuleBuilder().setRuleName(ruleName).build());
        });

        tx.submit();
    }

    public List<BigInteger> getUnderlayNetworksDpns(List<String> underlayNetworks) {
        if (underlayNetworks == null) {
            return Collections.emptyList();
        }

        return underlayNetworks.stream().map(t -> getUnderlayNetworkDpns(t)).flatMap(t -> t.stream()).distinct()
                .collect(Collectors.toList());
    }

    private List<BigInteger> getUnderlayNetworkDpns(String underlayNetworkName) {
        InstanceIdentifier<UnderlayNetwork> identifier = InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetworkName));
        try {
            UnderlayNetwork underlayNetwork = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return getDpnsFromDpnToInterfaces(underlayNetwork.getDpnToInterface());
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get DPNs for underlay network {}", underlayNetworkName);
            return Collections.emptyList();
        }
    }

    static List<BigInteger> getDpnsFromDpnToInterfaces(List<DpnToInterface> dpnToInterfaces) {
        if (dpnToInterfaces == null) {
            return Collections.emptyList();
        }

        return dpnToInterfaces.stream().map(t -> t.getDpId()).collect(Collectors.toList());
    }

    static List<String> getUnderlayNetworksFromPolicyRoutes(List<PolicyRoute> policyRoutes) {
        if (policyRoutes == null) {
            return Collections.emptyList();
        }

        List<String> underlayNetworkNames = new ArrayList<>();
        for (PolicyRoute policyRoute : policyRoutes) {
            Route route = policyRoute.getRoute();
            if (route instanceof BasicRoute) {
                underlayNetworkNames.add(((BasicRoute) route).getNetworkName());
            }
        }

        return underlayNetworkNames;
    }

}
