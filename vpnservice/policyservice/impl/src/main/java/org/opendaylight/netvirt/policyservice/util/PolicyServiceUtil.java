/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.util;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AclBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.PolicyProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.SetPolicyClassifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.UnderlayNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfileKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyAclRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyAclRuleKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.PolicyRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.acl.rule.AceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.acl.rule.AceRuleBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.acl.rule.AceRuleKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.route.Route;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.route.route.BasicRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.PolicyProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PolicyServiceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyServiceUtil.class);

    private static final String LOCAL_IP = "local_ip";

    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southboundUtils;

    @Inject
    public PolicyServiceUtil(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        this.mdsalUtils = new MdsalUtils(dataBroker);
        this.southboundUtils = new SouthboundUtils(mdsalUtils);
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

    public List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay
        .network.PolicyProfile> getUnderlayNetworkPolicyProfiles(String underlayNetworkName) {
        InstanceIdentifier<UnderlayNetwork> identifier = InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetworkName));
        try {
            UnderlayNetwork underlayNetwork = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return underlayNetwork != null ? underlayNetwork.getPolicyProfile() : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy classifiers for underlay network {}", underlayNetworkName);
            return Collections.emptyList();
        }
    }

    public List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile
        .PolicyAclRule> getPolicyClassifierAclRules(String policyClassifierName) {
        InstanceIdentifier<PolicyProfile> identifier = InstanceIdentifier.create(PolicyProfiles.class)
                .child(PolicyProfile.class, new PolicyProfileKey(policyClassifierName));
        try {
            PolicyProfile policyProfile = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return policyProfile != null ? policyProfile.getPolicyAclRule() : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy rules for policy classifier {}", policyClassifierName);
            return Collections.emptyList();
        }
    }

    public void updateUnderlayNetworkTunnelInterface(String underlayNetwork, BigInteger dpnId, String tunnelInterface,
            boolean isAdded) {
        InstanceIdentifier<TunnelInterface> identifier = InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork))
                .child(DpnToInterface.class, new DpnToInterfaceKey(dpnId))
                .child(TunnelInterface.class, new TunnelInterfaceKey(tunnelInterface));

        try {
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier,
                    new TunnelInterfaceBuilder().setInterfaceName(tunnelInterface).build());
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to update tunnel interface {} DPN {} for underlay network {}", tunnelInterface, dpnId,
                    underlayNetwork);
        }
    }

    public void updateUnderlayNetworksPolicyClassifier(List<String> underlayNetworks, String policyClassifierName,
            boolean isAdded) {
        if (underlayNetworks == null) {
            return;
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        underlayNetworks.forEach(underlayNetwork -> {
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks
                .underlay.network.PolicyProfile> identifier = InstanceIdentifier
                    .create(UnderlayNetworks.class)
                    .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork))
                    .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks
                            .underlay.network.PolicyProfile.class,
                            new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks
                                .underlay.network.PolicyProfileKey(policyClassifierName));
            if (isAdded) {
                tx.merge(LogicalDatastoreType.OPERATIONAL, identifier,
                        new PolicyProfileBuilder().setPolicyClassifier(policyClassifierName).build(), true);
            } else {
                tx.delete(LogicalDatastoreType.OPERATIONAL, identifier);
            }
        });

        tx.submit();
    }

    public void updatePolicyClassifierAclRule(String policyClassifierName, String aclName, String ruleName,
            boolean isAdded) {
        InstanceIdentifier<AceRule> identifier = InstanceIdentifier.create(PolicyProfiles.class)
                .child(PolicyProfile.class, new PolicyProfileKey(policyClassifierName))
                .child(PolicyAclRule.class, new PolicyAclRuleKey(aclName))
                .child(AceRule.class, new AceRuleKey(ruleName));

        try {
            if (isAdded) {
                SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier,
                        new AceRuleBuilder().setRuleName(ruleName).build());
            } else {
                SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier);
            }
        } catch (TransactionCommitFailedException e) {
            LOG.error("Failed to update ACL {} rule {} for policy classifier {}", aclName, ruleName,
                    policyClassifierName);
        }
    }

    public List<BigInteger> getUnderlayNetworksDpns(List<String> underlayNetworks) {
        if (underlayNetworks == null) {
            return Collections.emptyList();
        }

        return underlayNetworks.stream().map(t -> getUnderlayNetworkDpns(t)).flatMap(t -> t.stream()).distinct()
                .collect(Collectors.toList());
    }

    public String getTunnelUnderlayNetwork(BigInteger dpnId, IpAddress tunnelIp) {
        Node ovsdbNode = getOvsdbNode(dpnId);
        if (ovsdbNode == null) {
            LOG.error("Failed to get OVSDB node for DPN {}", dpnId);
            return null;
        }

        Map<String, String> localIpMap = getOpenvswitchOtherConfigMap(ovsdbNode);
        return localIpMap.get(String.valueOf(tunnelIp.getValue()));
    }

    public static List<BigInteger> getDpnsFromDpnToInterfaces(List<DpnToInterface> dpnToInterfaces) {
        if (dpnToInterfaces == null) {
            return Collections.emptyList();
        }

        return dpnToInterfaces.stream().map(t -> t.getDpId()).collect(Collectors.toList());
    }

    public static List<String> getUnderlayNetworksFromPolicyRoutes(List<PolicyRoute> policyRoutes) {
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

    public static boolean isPolicyAcl(Class<? extends AclBase> aclType) {
        return aclType != null && aclType.isAssignableFrom(PolicyAcl.class);
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

    @SuppressWarnings("unchecked")
    private Node getOvsdbNode(BigInteger dpnId) {
        // FIXME use genius caches to get the Node InstanceIdentifier
        InstanceIdentifier<BridgeRefEntry> bridgeRefIdentifier = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));

        BridgeRefEntry bridgeRefEntry;
        try {
            bridgeRefEntry = SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    bridgeRefIdentifier);
            if (bridgeRefEntry == null) {
                LOG.error("No bridge ref entry found for DPN {} ", dpnId);
                return null;
            }

            InstanceIdentifier<Node> nodeId = ((InstanceIdentifier<OvsdbBridgeAugmentation>) bridgeRefEntry
                    .getBridgeReference().getValue()).firstIdentifierOf(Node.class);

            return SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.OPERATIONAL, nodeId);
        } catch (ReadFailedException e) {
            LOG.error("Failed to get OVS node for DPN {}", dpnId);
            return null;
        }
    }

    private Map<String, String> getOpenvswitchOtherConfigMap(Node node) {
        String localIp = southboundUtils.getOpenvswitchOtherConfig(node, LOCAL_IP);
        return extractMultiKeyValueToMap(localIp);
    }

    private static Map<String, String> extractMultiKeyValueToMap(String multiKeyValueStr) {
        if (Strings.isNullOrEmpty(multiKeyValueStr)) {
            return Collections.emptyMap();
        }

        Map<String, String> valueMap = new HashMap<>();
        Splitter splitter = Splitter.on(",");
        for (String keyValue : splitter.split(multiKeyValueStr)) {
            String[] split = keyValue.split(":", 2);
            if (split.length == 2) {
                valueMap.put(split[0], split[1]);
            }
        }

        return valueMap;
    }
}
