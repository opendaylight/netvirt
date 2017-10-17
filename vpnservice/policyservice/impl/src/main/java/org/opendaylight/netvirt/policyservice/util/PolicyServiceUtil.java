/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.util;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanBridgeManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AclBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.AceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Actions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan.L2vlanMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeLogicalGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile.policy.route.route.BasicRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.PolicyProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
@Singleton
public class PolicyServiceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyServiceUtil.class);

    public static final String LOCAL_IPS = "local_ips";

    private final DataBroker dataBroker;
    private final IElanBridgeManager bridgeManager;
    private final ItmRpcService itmRpcService;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator coordinator;

    @Inject
    public PolicyServiceUtil(final DataBroker dataBroker, final IElanBridgeManager bridgeManager,
            final ItmRpcService itmRpcService, final IInterfaceManager interfaceManager,
            final JobCoordinator coordinator) {
        this.dataBroker = dataBroker;
        this.bridgeManager = bridgeManager;
        this.itmRpcService = itmRpcService;
        this.interfaceManager = interfaceManager;
        this.coordinator = coordinator;
    }

    public Optional<String> getAcePolicyClassifier(Ace ace) {
        Actions actions = ace.getActions();
        SetPolicyClassifier setPolicyClassifier = actions.getAugmentation(SetPolicyClassifier.class);
        if (setPolicyClassifier == null) {
            LOG.warn("No valid policy action found for ACE rule {}", ace.getRuleName());
            return Optional.absent();
        }

        Class<? extends DirectionBase> direction;
        try {
            direction = setPolicyClassifier.getDirection();
        } catch (IllegalArgumentException e) {
            LOG.warn("Failed to parse policy classifier direction");
            return Optional.absent();
        }

        if (direction == null || !direction.isAssignableFrom(DirectionEgress.class)) {
            LOG.trace("Ignoring non egress policy ACE rule {}", ace.getRuleName());
            return Optional.absent();
        }

        return Optional.of(setPolicyClassifier.getPolicyClassifier());
    }

    public Optional<Ace> getPolicyAce(String aclName, String ruleName) {
        InstanceIdentifier<Ace> identifier = getAceIdentifier(aclName, ruleName);
        try {
            return SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    identifier);
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy ACE rule {} for ACL {}", ruleName, aclName);
            return Optional.absent();
        }
    }

    public List<PolicyProfile> getAllPolicyProfiles() {
        InstanceIdentifier<PolicyProfiles> identifier = InstanceIdentifier.create(PolicyProfiles.class);
        try {
            Optional<PolicyProfiles> optProfiles = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, identifier);
            return optProfiles.isPresent() ? optProfiles.get().getPolicyProfile() : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy profiles");
            return Collections.emptyList();
        }
    }

    public List<String> getUnderlayNetworksForClassifier(String policyClassifier) {
        InstanceIdentifier<PolicyProfile> identifier = getPolicyClassifierIdentifier(policyClassifier);
        try {
            Optional<PolicyProfile> optProfile = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, identifier);
            return optProfile.isPresent() ? getUnderlayNetworksFromPolicyRoutes(optProfile.get().getPolicyRoute())
                    : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy routes for classifier {}", policyClassifier);
            return Collections.emptyList();
        }
    }

    public List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay
        .network.PolicyProfile> getUnderlayNetworkPolicyProfiles(String underlayNetwork) {
        InstanceIdentifier<UnderlayNetwork> identifier = getUnderlayNetworkIdentifier(underlayNetwork);
        try {
            Optional<UnderlayNetwork> optUnderlayNet = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return optUnderlayNet.isPresent() ? optUnderlayNet.get().getPolicyProfile() : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy classifiers for underlay network {}", underlayNetwork);
            return Collections.emptyList();
        }
    }

    public List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.policy.profile
        .PolicyAclRule> getPolicyClassifierAclRules(String policyClassifier) {
        InstanceIdentifier<PolicyProfile> identifier = getPolicyClassifierIdentifier(policyClassifier);
        try {
            Optional<PolicyProfile> optProfile = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return optProfile.isPresent() ? optProfile.get().getPolicyAclRule() : Collections.emptyList();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get policy rules for policy classifier {}", policyClassifier);
            return Collections.emptyList();
        }
    }

    public void updateTunnelInterfaceForUnderlayNetwork(String underlayNetwork, BigInteger srcDpId, BigInteger dstDpId,
            String tunnelInterfaceName, boolean isAdded) {
        coordinator.enqueueJob(underlayNetwork, () -> {
            InstanceIdentifier<TunnelInterface> identifier = getUnderlayNetworkTunnelIdentifier(underlayNetwork,
                    srcDpId, tunnelInterfaceName);
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            if (isAdded) {
                TunnelInterface tunnelInterface = new TunnelInterfaceBuilder().setInterfaceName(tunnelInterfaceName)
                        .setRemoteDpId(dstDpId).build();
                tx.merge(LogicalDatastoreType.OPERATIONAL, identifier, tunnelInterface, true);
                LOG.info("Add tunnel {} on DPN {} to underlay network {}", tunnelInterfaceName, srcDpId,
                        underlayNetwork);
            } else {
                tx.delete(LogicalDatastoreType.OPERATIONAL, identifier);
                LOG.info("Remove tunnel {} from DPN {} on underlay network {}", tunnelInterfaceName, srcDpId,
                        underlayNetwork);
            }
            return Collections.singletonList(tx.submit());
        });
    }

    public void updateTunnelInterfacesForUnderlayNetwork(String underlayNetwork, BigInteger srcDpId,
            List<TunnelInterface> tunnelInterfaces, boolean isAdded) {
        coordinator.enqueueJob(underlayNetwork, () -> {
            InstanceIdentifier<DpnToInterface> identifier = getUnderlayNetworkDpnIdentifier(underlayNetwork, srcDpId);
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            if (isAdded) {
                DpnToInterface dpnToInterface = new DpnToInterfaceBuilder().setDpId(srcDpId)
                        .setTunnelInterface(tunnelInterfaces).build();
                tx.merge(LogicalDatastoreType.OPERATIONAL, identifier, dpnToInterface, true);
                LOG.info("Add tunnel interfaces {} on DPN {} to underlay network {}", tunnelInterfaces, srcDpId,
                        underlayNetwork);
            } else {
                tx.delete(LogicalDatastoreType.OPERATIONAL, identifier);
                LOG.info("Remove tunnel interfaces {} from DPN {} on underlay network {}", tunnelInterfaces, srcDpId,
                        underlayNetwork);
            }
            return Collections.singletonList(tx.submit());
        });
    }

    public void updatePolicyClassifierForUnderlayNetworks(List<String> underlayNetworks, String policyClassifier,
            boolean isAdded) {
        if (underlayNetworks == null || underlayNetworks.isEmpty()) {
            LOG.debug("No underlay networks found for policy classifier {}", policyClassifier);
        }

        underlayNetworks.forEach(underlayNetwork -> coordinator.enqueueJob(underlayNetwork, () -> {
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            InstanceIdentifier<
                    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks
                    .underlay.network.PolicyProfile> identifier = getUnderlayNetworkPolicyClassifierIdentifier(
                            policyClassifier, underlayNetwork);

            if (isAdded) {
                tx.merge(LogicalDatastoreType.OPERATIONAL, identifier,
                        new PolicyProfileBuilder().setPolicyClassifier(policyClassifier).build(), true);
                LOG.info("Add policy classifier {} to underlay network {}", policyClassifier, underlayNetwork);
            } else {
                tx.delete(LogicalDatastoreType.OPERATIONAL, identifier);
                LOG.info("Remove policy classifier {} from underlay network {}", policyClassifier, underlayNetwork);
            }
            return Collections.singletonList(tx.submit());
        }));
    }

    public void updateAclRuleForPolicyClassifier(String policyClassifier, String aclName, String ruleName,
            boolean isAdded) {
        coordinator.enqueueJob(policyClassifier, () -> {
            InstanceIdentifier<
                    AceRule> identifier = getPolicyClassifierAceIdentifier(policyClassifier, aclName, ruleName);
            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            if (isAdded) {
                tx.merge(LogicalDatastoreType.OPERATIONAL, identifier,
                        new AceRuleBuilder().setRuleName(ruleName).build(), true);
                LOG.info("Add ACL {} rule {} to policy classifier {}", aclName, ruleName, policyClassifier);
            } else {
                tx.delete(LogicalDatastoreType.OPERATIONAL, identifier);
                LOG.info("Remove ACL {} rule {} from policy classifier {}", aclName, ruleName, policyClassifier);
            }
            return Collections.singletonList(tx.submit());
        });
    }

    public List<BigInteger> getUnderlayNetworksDpns(List<String> underlayNetworks) {
        if (underlayNetworks == null) {
            return Collections.emptyList();
        }

        return underlayNetworks.stream().flatMap(t -> getUnderlayNetworkDpns(t).stream()).distinct()
                .collect(Collectors.toList());
    }

    public List<BigInteger> getUnderlayNetworksRemoteDpns(List<String> underlayNetworks) {
        if (underlayNetworks == null) {
            return Collections.emptyList();
        }

        return underlayNetworks.stream().map(this::getUnderlayNetworkRemoteDpns).flatMap(Collection::stream).distinct()
                .collect(Collectors.toList());
    }

    public boolean underlayNetworkContainsDpn(String underlayNetwork, BigInteger dpId) {
        return dpnToInterfacesContainsDpn(getUnderlayNetworkDpnToInterfaces(underlayNetwork), dpId);
    }

    public boolean underlayNetworkContainsRemoteDpn(String underlayNetwork, BigInteger dpId) {
        return dpnToInterfacesContainsRemoteDpn(getUnderlayNetworkDpnToInterfaces(underlayNetwork), dpId);
    }

    public static boolean dpnToInterfacesContainsDpn(List<DpnToInterface> dpnToInterfaces, BigInteger dpId) {
        return dpnToInterfaces != null && dpnToInterfaces.stream().anyMatch(
            dpnToInterface -> dpnToInterface.getDpId().equals(dpId));
    }

    public static boolean dpnToInterfacesContainsRemoteDpn(List<DpnToInterface> dpnToInterfaces, BigInteger dpId) {
        return dpnToInterfaces != null && dpnToInterfaces.stream().anyMatch(
            dpnToInterface -> dpnToInterfaceContainsRemoteDpn(dpnToInterface, dpId));
    }

    public static boolean dpnToInterfaceContainsRemoteDpn(DpnToInterface dpnToInterface, BigInteger dpId) {
        List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
        return tunnelInterfaces != null && tunnelInterfaces.stream().anyMatch(
            tunnelInterface -> tunnelInterface.getRemoteDpId().equals(dpId));
    }

    public String getTunnelUnderlayNetwork(BigInteger dpId, IpAddress tunnelIp) {
        Node ovsdbNode = bridgeManager.getBridgeNode(dpId);
        if (ovsdbNode == null) {
            LOG.error("Failed to get OVSDB node for DPN {}", dpId);
            return null;
        }

        Map<String, String> localIpMap = bridgeManager.getOpenvswitchOtherConfigMap(ovsdbNode, LOCAL_IPS);
        return localIpMap.get(String.valueOf(tunnelIp.getValue()));
    }

    public static List<BigInteger> getDpnsFromDpnToInterfaces(List<DpnToInterface> dpnToInterfaces) {
        if (dpnToInterfaces == null) {
            return Collections.emptyList();
        }

        return dpnToInterfaces.stream().map(DpnToInterface::getDpId).collect(Collectors.toList());
    }

    public static List<BigInteger> getRemoteDpnsFromDpnToInterfaces(List<DpnToInterface> dpnToInterfaces) {
        if (dpnToInterfaces == null) {
            return Collections.emptyList();
        }

        return dpnToInterfaces.stream().map(PolicyServiceUtil::getRemoteDpnsFromDpnToInterface)
                .flatMap(Collection::stream).distinct().collect(Collectors.toList());
    }

    public static List<BigInteger> getRemoteDpnsFromDpnToInterface(DpnToInterface dpnToInterface) {
        List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
        if (tunnelInterfaces == null) {
            return Collections.emptyList();
        }

        return tunnelInterfaces.stream().map(TunnelInterface::getRemoteDpId)
                .collect(Collectors.toList());
    }

    public static List<String> getUnderlayNetworksFromPolicyRoutes(List<PolicyRoute> policyRoutes) {
        if (policyRoutes == null) {
            return Collections.emptyList();
        }

        return policyRoutes.stream().map(PolicyRoute::getRoute)
                .filter(route -> route instanceof BasicRoute).map(route -> ((BasicRoute) route).getNetworkName())
                .collect(Collectors.toList());
    }

    public static boolean isPolicyAcl(Class<? extends AclBase> aclType) {
        return aclType != null && aclType.isAssignableFrom(PolicyAcl.class);
    }

    @Nonnull
    public List<DpnToInterface> getUnderlayNetworkDpnToInterfaces(String underlayNetwork) {
        InstanceIdentifier<UnderlayNetwork> identifier = InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork));
        try {
            return SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier)
                    .toJavaUtil().map(UnderlayNetwork::getDpnToInterface)
                    .orElse(Collections.emptyList());
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get DPNs for underlay network {}", underlayNetwork);
            return Collections.emptyList();
        }
    }

    public Optional<DpnToInterface> getUnderlayNetworkDpnToInterfaces(String underlayNetwork, BigInteger dpId) {
        InstanceIdentifier<DpnToInterface> identifier = getUnderlayNetworkDpnIdentifier(underlayNetwork, dpId);
        try {
            Optional<DpnToInterface> dpnToInterfaceOpt = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, identifier);
            return dpnToInterfaceOpt;
        } catch (ReadFailedException e) {
            LOG.warn("Failed to get DPN {} for underlay network {}", dpId, underlayNetwork);
            return Optional.absent();
        }
    }

    private InstanceIdentifier<UnderlayNetwork> getUnderlayNetworkIdentifier(String underlayNetwork) {
        return InstanceIdentifier.create(UnderlayNetworks.class).child(UnderlayNetwork.class,
                new UnderlayNetworkKey(underlayNetwork));
    }

    private InstanceIdentifier<DpnToInterface> getUnderlayNetworkDpnIdentifier(String underlayNetwork,
            BigInteger dpId) {
        return InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork))
                .child(DpnToInterface.class, new DpnToInterfaceKey(dpId));
    }

    private InstanceIdentifier<TunnelInterface> getUnderlayNetworkTunnelIdentifier(String underlayNetwork,
            BigInteger dpId, String tunnelInterface) {
        return InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork))
                .child(DpnToInterface.class, new DpnToInterfaceKey(dpId))
                .child(TunnelInterface.class, new TunnelInterfaceKey(tunnelInterface));
    }

    private InstanceIdentifier<PolicyProfile> getPolicyClassifierIdentifier(String policyClassifier) {
        return InstanceIdentifier.create(PolicyProfiles.class).child(PolicyProfile.class,
                new PolicyProfileKey(policyClassifier));
    }

    private InstanceIdentifier<Ace> getAceIdentifier(String aclName, String ruleName) {
        return InstanceIdentifier.create(AccessLists.class).child(Acl.class, new AclKey(aclName, PolicyAcl.class))
                .child(AccessListEntries.class).child(Ace.class, new AceKey(ruleName));
    }

    private KeyedInstanceIdentifier<AceRule, AceRuleKey> getPolicyClassifierAceIdentifier(String policyClassifier,
            String aclName, String ruleName) {
        return InstanceIdentifier.create(PolicyProfiles.class)
                .child(PolicyProfile.class, new PolicyProfileKey(policyClassifier))
                .child(PolicyAclRule.class, new PolicyAclRuleKey(aclName))
                .child(AceRule.class, new AceRuleKey(ruleName));
    }

    private InstanceIdentifier<
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network
                    .PolicyProfile> getUnderlayNetworkPolicyClassifierIdentifier(String policyClassifier,
                            String underlayNetwork) {
        return InstanceIdentifier.create(UnderlayNetworks.class)
                .child(UnderlayNetwork.class, new UnderlayNetworkKey(underlayNetwork))
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks
                        .underlay.network.PolicyProfile.class, new org.opendaylight.yang.gen.v1.urn.opendaylight
                        .netvirt.policy.rev170207.underlay.networks.underlay.network
                        .PolicyProfileKey(policyClassifier));
    }

    public List<BigInteger> getUnderlayNetworkDpns(String underlayNetwork) {
        return getDpnsFromDpnToInterfaces(getUnderlayNetworkDpnToInterfaces(underlayNetwork));
    }

    public List<BigInteger> getUnderlayNetworkRemoteDpns(String underlayNetwork) {
        return getRemoteDpnsFromDpnToInterfaces(getUnderlayNetworkDpnToInterfaces(underlayNetwork));
    }


    public Optional<Integer> getLogicalTunnelLportTag(BigInteger srcDpId, BigInteger dstDpId) {
        Optional<String> logicalTunnelNameOpt = getLogicalTunnelName(srcDpId, dstDpId);
        if (!logicalTunnelNameOpt.isPresent()) {
            LOG.debug("Failed to get logical tunnel for source DPN {} dst DPN {}", srcDpId, dstDpId);
            return Optional.absent();
        }

        String logicalTunnelName = logicalTunnelNameOpt.get();
        InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(logicalTunnelName);
        if (interfaceInfo == null) {
            LOG.debug("Failed to get interface info for logical tunnel {}", logicalTunnelName);
            return Optional.absent();
        }

        return Optional.of(interfaceInfo.getInterfaceTag());
    }

    public Optional<String> getLogicalTunnelName(BigInteger srcDpId, BigInteger dstDpId) {
        Future<RpcResult<GetTunnelInterfaceNameOutput>> tunnelInterfaceOutput = itmRpcService
                .getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder().setSourceDpid(srcDpId)
                        .setDestinationDpid(dstDpId).setTunnelType(TunnelTypeLogicalGroup.class).build());
        try {
            if (tunnelInterfaceOutput.get().isSuccessful()) {
                return Optional.of(tunnelInterfaceOutput.get().getResult().getInterfaceName());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {} for source DPN {} dst DPN {}", srcDpId, dstDpId);
        }

        return Optional.absent();
    }


    public Optional<String> getVlanMemberInterface(String trunkInterface, VlanId vlanId) {
        List<Interface> vlanMemberInterfaces = interfaceManager.getChildInterfaces(trunkInterface);
        if (vlanMemberInterfaces == null || vlanMemberInterfaces.isEmpty()) {
            LOG.debug("No child interfaces found for trunk {}", trunkInterface);
            return Optional.absent();
        }

        return vlanMemberInterfaces.stream()
                .filter(iface -> isVlanMemberInterface(iface, vlanId))
                .findFirst()
                .map(Interface::getName)
                .map(Optional::of)
                .orElseGet(Optional::absent);
    }

    private boolean isVlanMemberInterface(Interface iface, VlanId vlanId) {
        IfL2vlan l2vlan = iface.getAugmentation(IfL2vlan.class);
        if (l2vlan == null || !L2vlanMode.TrunkMember.equals(l2vlan.getL2vlanMode())) {
            LOG.warn("Interface {} is not VLAN member", iface.getName());
            return false;
        }

        return Objects.equals(vlanId, l2vlan.getVlanId());
    }
}
