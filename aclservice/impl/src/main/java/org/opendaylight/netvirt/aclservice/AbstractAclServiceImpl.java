/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack.NxCtAction;
import org.opendaylight.genius.mdsalutil.actions.ActionNxCtClear;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConntrackClassifierType;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.SubnetInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAclServiceImpl implements AclServiceListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAclServiceImpl.class);

    protected final IMdsalApiManager mdsalManager;
    protected final ManagedNewTransactionRunner txRunner;
    protected final Class<? extends ServiceModeBase> serviceMode;
    protected final AclDataUtil aclDataUtil;
    protected final AclServiceUtils aclServiceUtils;
    protected final JobCoordinator jobCoordinator;
    protected final AclInterfaceCache aclInterfaceCache;

    protected final Class<? extends DirectionBase> direction;
    protected final String directionString;

    /**
     * Initialize the member variables.
     *
     * @param serviceMode the service mode
     * @param dataBroker the data broker instance.
     * @param mdsalManager the mdsal manager instance.
     * @param aclDataUtil the acl data util.
     * @param aclServiceUtils the acl service util.
     * @param jobCoordinator the job coordinator
     * @param aclInterfaceCache the acl interface cache
     */
    public AbstractAclServiceImpl(Class<? extends ServiceModeBase> serviceMode, DataBroker dataBroker,
            IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil, AclServiceUtils aclServiceUtils,
            JobCoordinator jobCoordinator, AclInterfaceCache aclInterfaceCache) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.serviceMode = serviceMode;
        this.aclDataUtil = aclDataUtil;
        this.aclServiceUtils = aclServiceUtils;
        this.jobCoordinator = jobCoordinator;
        this.aclInterfaceCache = aclInterfaceCache;

        this.direction =
                this.serviceMode.equals(ServiceModeEgress.class) ? DirectionIngress.class : DirectionEgress.class;
        this.directionString = this.direction.equals(DirectionEgress.class) ? "Egress" : "Ingress";
    }

    @Override
    public boolean applyAcl(AclInterface port) {
        if (port == null) {
            LOG.error("port cannot be null");
            return false;
        }
        if (port.getSecurityGroups() == null) {
            LOG.info("Port {} without SGs", port.getInterfaceId());
            return false;
        }
        BigInteger dpId = port.getDpId();
        if (dpId == null || port.getLPortTag() == null) {
            LOG.error("Unable to find DpId from ACL interface with id {}", port.getInterfaceId());
            return false;
        }
        LOG.debug("Applying ACL on port {} with DpId {}", port, dpId);
        List<FlowEntity> flowEntries = new ArrayList<>();
        programAcl(flowEntries, port, Action.ADD, NwConstants.ADD_FLOW);
        updateRemoteAclFilterTable(flowEntries, port, NwConstants.ADD_FLOW);
        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + port.getInterfaceId(), flowEntries, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean bindAcl(AclInterface port) {
        if (port == null || port.getSecurityGroups() == null) {
            LOG.error("Port and port security groups cannot be null for binding ACL service, port={}", port);
            return false;
        }
        bindService(port);
        return true;
    }

    @Override
    public boolean unbindAcl(AclInterface port) {
        if (port == null) {
            LOG.error("Port cannot be null for unbinding ACL service");
            return false;
        }
        if (port.getDpId() != null) {
            unbindService(port);
        }
        return true;
    }

    @Override
    public boolean updateAcl(AclInterface portBefore, AclInterface portAfter) {
        // this check is to avoid situations of port update coming before interface state is up
        if (portAfter.getDpId() == null || portAfter.getLPortTag() == null) {
            LOG.debug("Unable to find DpId from ACL interface with id {} and lport {}", portAfter.getInterfaceId(),
                    portAfter.getLPortTag());
            return false;
        }
        boolean result = true;
        boolean isPortSecurityEnable = portAfter.isPortSecurityEnabled();
        boolean isPortSecurityEnableBefore = portBefore.isPortSecurityEnabled();
        // if port security is changed, apply/remove Acls
        if (isPortSecurityEnableBefore != isPortSecurityEnable) {
            LOG.debug("On ACL update, Port security is {} for {}", isPortSecurityEnable ? "Enabled" :
                    "Disabled", portAfter.getInterfaceId());
            if (isPortSecurityEnable) {
                result = applyAcl(portAfter);
            } else {
                result = removeAcl(portBefore);
            }
        } else if (isPortSecurityEnable) {
            // Acls has been updated, find added/removed Acls and act accordingly.
            processInterfaceUpdate(portBefore, portAfter);
            LOG.debug("On ACL update, ACL has been updated for {}", portAfter.getInterfaceId());
        }

        return result;
    }

    private void processInterfaceUpdate(AclInterface portBefore, AclInterface portAfter) {
        List<FlowEntity> addFlowEntries = new ArrayList<>();
        List<FlowEntity> deleteFlowEntries = new ArrayList<>();
        List<AllowedAddressPairs> addedAaps = AclServiceUtils
                .getUpdatedAllowedAddressPairs(portAfter.getAllowedAddressPairs(), portBefore.getAllowedAddressPairs());
        List<AllowedAddressPairs> deletedAaps = AclServiceUtils
                .getUpdatedAllowedAddressPairs(portBefore.getAllowedAddressPairs(), portAfter.getAllowedAddressPairs());
        if (deletedAaps != null && !deletedAaps.isEmpty()) {
            programAclWithAllowedAddress(deleteFlowEntries, portBefore, deletedAaps, Action.UPDATE,
                    NwConstants.DEL_FLOW);
            updateRemoteAclFilterTable(deleteFlowEntries, portBefore, portBefore.getSecurityGroups(), deletedAaps,
                    NwConstants.DEL_FLOW);
        }
        if (addedAaps != null && !addedAaps.isEmpty()) {
            programAclWithAllowedAddress(addFlowEntries, portAfter, addedAaps, Action.UPDATE, NwConstants.ADD_FLOW);
            updateRemoteAclFilterTable(addFlowEntries, portAfter, portAfter.getSecurityGroups(), addedAaps,
                    NwConstants.ADD_FLOW);
        }
        if (portAfter.getSubnetInfo() != null && portBefore.getSubnetInfo() == null) {
            programBroadcastRules(addFlowEntries, portAfter, Action.UPDATE, NwConstants.ADD_FLOW);
        }
        handleSubnetChange(portBefore, portAfter, addFlowEntries, deleteFlowEntries);

        List<Uuid> addedAcls = AclServiceUtils.getUpdatedAclList(portAfter.getSecurityGroups(),
                portBefore.getSecurityGroups());
        List<Uuid> deletedAcls = AclServiceUtils.getUpdatedAclList(portBefore.getSecurityGroups(),
                portAfter.getSecurityGroups());
        if (!deletedAcls.isEmpty() || !addedAcls.isEmpty()) {
            handleAclChange(deleteFlowEntries, portBefore, deletedAcls, NwConstants.DEL_FLOW);
            handleAclChange(addFlowEntries, portAfter, addedAcls, NwConstants.ADD_FLOW);
        }

        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + portAfter.getInterfaceId(), deleteFlowEntries,
                NwConstants.DEL_FLOW);
        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + portAfter.getInterfaceId(), addFlowEntries,
                NwConstants.ADD_FLOW);
    }

    private void handleSubnetChange(AclInterface portBefore, AclInterface portAfter,
            List<FlowEntity> addFlowEntries, List<FlowEntity> deleteFlowEntries) {
        List<SubnetInfo> deletedSubnets =
                AclServiceUtils.getSubnetDiff(portBefore.getSubnetInfo(), portAfter.getSubnetInfo());
        List<SubnetInfo> addedSubnets =
                AclServiceUtils.getSubnetDiff(portAfter.getSubnetInfo(), portBefore.getSubnetInfo());

        if (deletedSubnets != null && !deletedSubnets.isEmpty()) {
            programIcmpv6RARule(deleteFlowEntries, portAfter, deletedSubnets, NwConstants.DEL_FLOW);
            programSubnetBroadcastRules(deleteFlowEntries, portAfter, deletedSubnets, NwConstants.DEL_FLOW);
        }
        if (addedSubnets != null && !addedSubnets.isEmpty()) {
            programIcmpv6RARule(addFlowEntries, portAfter, addedSubnets, NwConstants.ADD_FLOW);
            programSubnetBroadcastRules(addFlowEntries, portAfter, addedSubnets, NwConstants.ADD_FLOW);
        }
    }

    private void handleAclChange(List<FlowEntity> flowEntries, AclInterface port, List<Uuid> aclList,
            int addOrRemove) {
        int operationForAclRules = addOrRemove == NwConstants.DEL_FLOW ? NwConstants.MOD_FLOW : addOrRemove;
        programAclRules(flowEntries, port, aclList, operationForAclRules);
        updateRemoteAclFilterTable(flowEntries, port, aclList, port.getAllowedAddressPairs(), addOrRemove);
        programAclDispatcherTable(flowEntries, port, addOrRemove);
    }

    protected SortedSet<Integer> getRemoteAclTags(AclInterface port) {
        return this.direction == DirectionIngress.class ? port.getIngressRemoteAclTags()
                : port.getEgressRemoteAclTags();
    }

    protected void programAclDispatcherTable(List<FlowEntity> flowEntries, AclInterface port, int addOrRemove) {
        SortedSet<Integer> remoteAclTags = getRemoteAclTags(port);
        if (remoteAclTags.isEmpty()) {
            LOG.debug("No {} rules with remote group id for port={}", this.directionString, port.getInterfaceId());
            return;
        }
        Integer firstRemoteAclTag = remoteAclTags.first();
        Integer lastRemoteAclTag = remoteAclTags.last();

        programFirstRemoteAclEntryInDispatcherTable(flowEntries, port, firstRemoteAclTag, addOrRemove);
        programLastRemoteAclEntryInDispatcherTable(flowEntries, port, lastRemoteAclTag, addOrRemove);

        Integer previousRemoteAclTag = firstRemoteAclTag;
        for (Integer remoteAclTag : remoteAclTags) {
            if (remoteAclTag.equals(firstRemoteAclTag)) {
                continue;
            }
            List<MatchInfoBase> matches = new ArrayList<>();
            matches.addAll(AclServiceUtils.buildMatchesForLPortTagAndRemoteAclTag(port.getLPortTag(),
                    previousRemoteAclTag, serviceMode));
            String flowId = this.directionString + "_ACL_Dispatcher_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                    + remoteAclTag;

            List<InstructionInfo> instructions =
                    AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclRuleBasedFilterTable());
            instructions.add(AclServiceUtils.getWriteMetadataForRemoteAclTag(remoteAclTag));
            addFlowEntryToList(flowEntries, port.getDpId(), getAclFilterCumDispatcherTable(), flowId,
                    AclConstants.ACE_GOTO_NEXT_REMOTE_ACL_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                    instructions, addOrRemove);

            previousRemoteAclTag = remoteAclTag;
        }
    }

    protected void programFirstRemoteAclEntryInDispatcherTable(List<FlowEntity> flowEntries, AclInterface port,
            Integer firstRemoteAclTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(AclServiceUtils.buildLPortTagMatch(port.getLPortTag(), serviceMode));
        String flowId = this.directionString + "_ACL_Dispatcher_First_" + port.getDpId() + "_" + port.getLPortTag()
                + "_" + firstRemoteAclTag;

        List<InstructionInfo> instructions =
                AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclRuleBasedFilterTable());
        instructions.add(AclServiceUtils.getWriteMetadataForRemoteAclTag(firstRemoteAclTag));
        addFlowEntryToList(flowEntries, port.getDpId(), getAclFilterCumDispatcherTable(), flowId,
                AclConstants.ACE_FIRST_REMOTE_ACL_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions,
                addOrRemove);
    }

    protected void programLastRemoteAclEntryInDispatcherTable(List<FlowEntity> flowEntries, AclInterface port,
            Integer lastRemoteAclTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.buildMatchesForLPortTagAndRemoteAclTag(port.getLPortTag(), lastRemoteAclTag,
                serviceMode));
        String flowId = this.directionString + "_ACL_Dispatcher_Last_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                + lastRemoteAclTag;

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        addFlowEntryToList(flowEntries, port.getDpId(), getAclFilterCumDispatcherTable(), flowId,
                AclConstants.ACE_LAST_REMOTE_ACL_PRIORITY, 0, 0, AclServiceUtils.getDropFlowCookie(port.getLPortTag()),
                matches, instructions, addOrRemove);
    }

    private void programAcl(List<FlowEntity> flowEntries, AclInterface port, Action action, int addOrRemove) {
        programAclWithAllowedAddress(flowEntries, port, port.getAllowedAddressPairs(), action, addOrRemove);
    }

    private void programAclWithAllowedAddress(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        LOG.debug("Applying ACL Allowed Address on DpId {}, lportTag {}, Action {}", dpId, lportTag, action);
        String portId = port.getInterfaceId();
        programAntiSpoofingRules(flowEntries, port, allowedAddresses, action, addOrRemove);
        programAclPortSpecificFixedRules(flowEntries, dpId, allowedAddresses, lportTag, portId, action, addOrRemove);
        if (action == Action.ADD || action == Action.REMOVE) {
            programAclRules(flowEntries, port, port.getSecurityGroups(), addOrRemove);
            programAclDispatcherTable(flowEntries, port, addOrRemove);
        }
    }

    /**
     * Programs the acl custom rules.
     *
     * @param flowEntries the flow entries
     * @param port acl interface
     * @param aclUuidList the list of acl uuid to be applied
     * @param addOrRemove whether to delete or add flow
     * @return program succeeded
     */
    protected boolean programAclRules(List<FlowEntity> flowEntries, AclInterface port, List<Uuid> aclUuidList,
            int addOrRemove) {
        BigInteger dpId = port.getDpId();
        LOG.debug("Applying custom rules on DpId {}, lportTag {}", dpId, port.getLPortTag());
        if (aclUuidList == null || dpId == null) {
            LOG.warn("{} ACL parameters can not be null. dpId={}, aclUuidList={}", this.directionString, dpId,
                    aclUuidList);
            return false;
        }
        for (Uuid aclUuid : aclUuidList) {
            Acl acl = this.aclDataUtil.getAcl(aclUuid.getValue());
            if (null == acl) {
                LOG.warn("The ACL {} not found in cache", aclUuid.getValue());
                continue;
            }
            for (Ace ace : AclServiceUtils.aceList(acl)) {
                programAceRule(flowEntries, port, aclUuid.getValue(), ace, addOrRemove);
            }
        }
        return true;
    }

    /**
     * Programs the ace specific rule.
     *
     * @param flowEntries flow entries
     * @param port acl interface
     * @param aclName the acl name
     * @param ace rule to be program
     * @param addOrRemove whether to delete or add flow
     */
    protected void programAceRule(List<FlowEntity> flowEntries, AclInterface port, String aclName, Ace ace,
            int addOrRemove) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccessListAttributes(ace);
        if (aceAttr == null) {
            LOG.error("Ace {} of Acl {} is either null or not having SecurityRuleAttr",
                    ace == null ? null : ace.getRuleName(), aclName);
            return;
        }
        if (addOrRemove == NwConstants.ADD_FLOW && aceAttr.isDeleted()) {
            LOG.trace("Ignoring {} rule which is already deleted", ace.getRuleName());
            return;
        }
        if (!isValidDirection(aceAttr.getDirection())) {
            LOG.trace("Ignoring {} direction while processing for {} ACE Rule {}", aceAttr.getDirection(),
                    this.directionString, ace.getRuleName());
            return;
        }
        LOG.debug("Program {} ACE rule for dpId={}, lportTag={}, addOrRemove={}, ace={}, portId={}",
                this.directionString, port.getDpId(), port.getLPortTag(), addOrRemove, ace.getRuleName(),
                port.getInterfaceId());

        Matches matches = ace.getMatches();
        if (matches != null && matches.getAceType() instanceof AceIp) {
            Map<String, List<MatchInfoBase>> flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
            if (!AclServiceUtils.doesAceHaveRemoteGroupId(aceAttr)) {
                // programming for ACE which doesn't have any remote group Id
                programForAceNotHavingRemoteAclId(flowEntries, port, aclName, ace, flowMap, addOrRemove);
            } else {
                Uuid remoteAclId = aceAttr.getRemoteGroupId();
                // programming for ACE which have remote group Id
                programAceSpecificFlows(flowEntries, port, aclName, ace, flowMap, remoteAclId, addOrRemove);
            }
        }
    }

    protected void programForAceNotHavingRemoteAclId(List<FlowEntity> flowEntries, AclInterface port, String aclName,
            Ace ace, @Nullable Map<String, List<MatchInfoBase>> flowMap, int addOrRemove) {
        if (null == flowMap) {
            return;
        }
        MatchInfoBase lportTagMatch = AclServiceUtils.buildLPortTagMatch(port.getLPortTag(), serviceMode);
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable());
        Integer flowPriority = this.aclServiceUtils.getAceFlowPriority(aclName);

        for (Entry<String, List<MatchInfoBase>> entry : flowMap.entrySet()) {
            String flowName = entry.getKey();
            List<MatchInfoBase> matches = entry.getValue();
            matches.add(lportTagMatch);
            String flowId = flowName + this.directionString + "_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                    + ace.key().getRuleName();

            int operation = addOrRemove == NwConstants.MOD_FLOW ? NwConstants.DEL_FLOW : addOrRemove;
            addFlowEntryToList(flowEntries, port.getDpId(), getAclFilterCumDispatcherTable(), flowId, flowPriority,
                    0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, operation);

            if (addOrRemove != NwConstants.DEL_FLOW) {
                programAclForExistingTrafficTable(port, ace, addOrRemove, flowName, matches, flowPriority);
            }
        }
    }

    protected void programAceSpecificFlows(List<FlowEntity> flowEntries, AclInterface port, String aclName, Ace ace,
            @Nullable Map<String, List<MatchInfoBase>> flowMap, Uuid remoteAclId, int addOrRemove) {
        if (null == flowMap) {
            return;
        }
        Integer remoteAclTag = this.aclServiceUtils.getAclTag(remoteAclId);
        if (remoteAclTag == null || remoteAclTag == AclConstants.INVALID_ACL_TAG) {
            LOG.error("remoteAclTag={} is null or invalid for remoteAclId={}", remoteAclTag, remoteAclId);
            return;
        }
        List<MatchInfoBase> lportAndAclMatches =
                AclServiceUtils.buildMatchesForLPortTagAndRemoteAclTag(port.getLPortTag(), remoteAclTag, serviceMode);
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclRemoteAclTable());
        Integer flowPriority = this.aclServiceUtils.getAceFlowPriority(aclName);

        for (Entry<String, List<MatchInfoBase>> entry : flowMap.entrySet()) {
            String flowName = entry.getKey();
            List<MatchInfoBase> matches = entry.getValue();
            matches.addAll(lportAndAclMatches);
            String flowId = flowName + this.directionString + "_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                    + ace.key().getRuleName();

            int operation = addOrRemove == NwConstants.MOD_FLOW ? NwConstants.DEL_FLOW : addOrRemove;
            addFlowEntryToList(flowEntries, port.getDpId(), getAclRuleBasedFilterTable(), flowId, flowPriority, 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, operation);

            if (addOrRemove != NwConstants.DEL_FLOW) {
                programAclForExistingTrafficTable(port, ace, addOrRemove, flowName, matches, flowPriority);
            }
        }
    }

    private void programAclForExistingTrafficTable(AclInterface port, Ace ace, int addOrRemove, String flowName,
            List<MatchInfoBase> matches, Integer priority) {
        AceIp acl = (AceIp) ace.getMatches().getAceType();
        final String newFlowName = flowName + this.directionString + "_" + port.getDpId() + "_" + port.getLPortTag()
                + "_" + (acl.getAceIpVersion() instanceof AceIpv4 ? "_IPv4" : "_IPv6") + "_FlowAfterRuleDeleted";

        final List<MatchInfoBase> newMatches =
                matches.stream().filter(obj -> !(obj instanceof NxMatchCtState || obj instanceof MatchMetadata))
                        .collect(Collectors.toList());
        newMatches.add(AclServiceUtils.buildLPortTagMatch(port.getLPortTag(), serviceMode));
        newMatches.add(new NxMatchCtState(AclConstants.TRACKED_RPL_CT_STATE, AclConstants.TRACKED_RPL_CT_STATE_MASK));

        List<InstructionInfo> instructions =
                AclServiceUtils.createCtMarkInstructionForNewState(getAclFilterCumDispatcherTable(), port.getElanId());
        // Reversing the flow add/delete operation for this table.
        List<FlowEntity> flowEntries = new ArrayList<>();
        int operation = addOrRemove == NwConstants.ADD_FLOW ? NwConstants.DEL_FLOW : NwConstants.ADD_FLOW;
        addFlowEntryToList(flowEntries, port.getDpId(), getAclForExistingTrafficTable(), newFlowName, priority, 0,
                AclServiceUtils.getHardTimoutForApplyStatefulChangeOnExistingTraffic(ace, aclServiceUtils),
                AclConstants.COOKIE_ACL_BASE, newMatches, instructions, operation);
        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + port.getInterfaceId(), flowEntries, operation);
    }

    @Override
    public boolean removeAcl(AclInterface port) {
        if (port.getDpId() == null) {
            LOG.warn("Unable to find DP Id from ACL interface with id {}", port.getInterfaceId());
            return false;
        }
        List<FlowEntity> flowEntries = new ArrayList<>();
        programAcl(flowEntries, port, Action.REMOVE, NwConstants.DEL_FLOW);
        updateRemoteAclFilterTable(flowEntries, port, NwConstants.DEL_FLOW);
        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + port.getInterfaceId(), flowEntries, NwConstants.DEL_FLOW);
        return true;
    }

    @Override
    public boolean applyAce(AclInterface port, String aclName, Ace ace) {
        if (!port.isPortSecurityEnabled() || port.getDpId() == null) {
            return false;
        }
        List<FlowEntity> flowEntries = new ArrayList<>();
        programAceRule(flowEntries, port, aclName, ace, NwConstants.ADD_FLOW);
        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + port.getInterfaceId(), flowEntries, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean removeAce(AclInterface port, String aclName, Ace ace) {
        if (!port.isPortSecurityEnabled() || port.getDpId() == null) {
            return false;
        }
        List<FlowEntity> flowEntries = new ArrayList<>();
        programAceRule(flowEntries, port, aclName, ace, NwConstants.MOD_FLOW);
        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + port.getInterfaceId(), flowEntries, NwConstants.DEL_FLOW);
        return true;
    }

    @Override
    public void updateRemoteAcl(Acl aclBefore, Acl aclAfter, Collection<AclInterface> portsBefore) {
        handleRemoteAclUpdate(aclBefore, aclAfter, portsBefore);
    }

    /**
     * Bind service.
     *
     * @param aclInterface the acl interface
     */
    public abstract void bindService(AclInterface aclInterface);

    /**
     * Unbind service.
     *
     * @param aclInterface the acl interface
     */
    protected abstract void unbindService(AclInterface aclInterface);

    /**
     * Programs the anti-spoofing rules.
     *
     * @param flowEntries the flow entries
     * @param port the acl interface
     * @param allowedAddresses the allowed addresses
     * @param action add/modify/remove action
     * @param addOrRemove addorRemove
     */
    protected abstract void programAntiSpoofingRules(List<FlowEntity> flowEntries, AclInterface port,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove);

    /**
     * Programs broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    protected abstract void programBroadcastRules(List<FlowEntity> flowEntries, AclInterface port, Action action,
            int addOrRemove);

    /**
     * Programs broadcast rules.
     *
     * @param flowEntries the flow entries
     * @param port the Acl Interface port
     * @param subnetInfoList the port subnet info list
     * @param addOrRemove whether to delete or add flow
     */
    protected abstract void programSubnetBroadcastRules(List<FlowEntity> flowEntries, AclInterface port,
            List<SubnetInfo> subnetInfoList, int addOrRemove);

    protected abstract void programIcmpv6RARule(List<FlowEntity> flowEntries, AclInterface port,
            List<SubnetInfo> subnets, int addOrRemove);

    /**
     * Add Flow to list.
     *
     * @param dpId
     *            the dpId
     * @param tableId
     *            the tableId
     * @param flowId
     *            the flowId
     * @param priority
     *            the priority
     * @param idleTimeOut
     *            the idle timeout
     * @param hardTimeOut
     *            the hard timeout
     * @param cookie
     *            the cookie
     * @param matches
     *            the list of matches to be writted
     * @param instructions
     *            the list of instruction to be written.
     * @param addOrRemove
     *            add or remove the entries.
     */
    protected void addFlowEntryToList(List<FlowEntity> flowEntries, BigInteger dpId, short tableId, String flowId,
            int priority, int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase> matches,
            List<InstructionInfo> instructions, int addOrRemove) {
        List<InstructionInfo> instructionInfos = null;
        if (addOrRemove == NwConstants.ADD_FLOW) {
            instructionInfos = instructions;
        }
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority,
                flowId, idleTimeOut, hardTimeOut, cookie, matches, instructionInfos);
        LOG.trace("Adding flow to list: DpnId {}, flowId {}", dpId, flowId);
        flowEntries.add(flowEntity);
    }

    protected void programFlows(String jobName, List<FlowEntity> flowEntries, int addOrRemove) {
        List<List<FlowEntity>> flowEntityParts = Lists.partition(flowEntries, AclConstants.FLOWS_PER_TRANSACTION);
        for (List<FlowEntity> part : flowEntityParts) {
            jobCoordinator.enqueueJob(jobName,
                () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    tx -> {
                        if (addOrRemove == NwConstants.ADD_FLOW) {
                            for (FlowEntity flowEntity: part) {
                                mdsalManager.addFlow(tx, flowEntity);
                            }
                        } else {
                            for (FlowEntity flowEntity: part) {
                                mdsalManager.removeFlow(tx, flowEntity);
                            }
                        }
                    })), AclConstants.JOB_MAX_RETRIES);
        }
    }

    protected List<InstructionInfo> getDispatcherTableResubmitInstructions() {
        return getDispatcherTableResubmitInstructions(new ArrayList<>());
    }

    /**
     * Gets the dispatcher table resubmit instructions based on ingress/egress service mode w.r.t switch.
     *
     * @param actionsInfos
     *            the actions infos
     * @return the instructions for dispatcher table resubmit
     */
    protected List<InstructionInfo> getDispatcherTableResubmitInstructions(List<ActionInfo> actionsInfos) {
        short dispatcherTableId = NwConstants.LPORT_DISPATCHER_TABLE;
        if (ServiceModeEgress.class.equals(this.serviceMode)) {
            dispatcherTableId = NwConstants.EGRESS_LPORT_DISPATCHER_TABLE;
        }

        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(dispatcherTableId));
        instructions.add(new InstructionApplyActions(actionsInfos));
        return instructions;
    }

    protected void handleRemoteAclUpdate(Acl aclBefore, Acl aclAfter, Collection<AclInterface> portsBefore) {
        String aclName = aclAfter.getAclName();
        Collection<AclInterface> interfaceList = aclDataUtil.getInterfaceList(new Uuid(aclName));
        if (interfaceList.isEmpty()) {
            LOG.trace("handleRemoteAclUpdate: No interfaces found with ACL={}", aclName);
            return;
        }
        Set<Uuid> remoteAclsBefore = AclServiceUtils.getRemoteAclIdsByDirection(aclBefore, this.direction);
        Set<Uuid> remoteAclsAfter = AclServiceUtils.getRemoteAclIdsByDirection(aclAfter, this.direction);

        Set<Uuid> remoteAclsAdded = new HashSet<>(remoteAclsAfter);
        remoteAclsAdded.removeAll(remoteAclsBefore);

        Set<Uuid> remoteAclsDeleted = new HashSet<>(remoteAclsBefore);
        remoteAclsDeleted.removeAll(remoteAclsAfter);

        List<FlowEntity> addFlowEntries = new ArrayList<>();
        List<FlowEntity> deleteFlowEntries = new ArrayList<>();
        if (!remoteAclsAdded.isEmpty() || !remoteAclsDeleted.isEmpty()) {
            // delete and add flows in ACL dispatcher table for all applicable ports
            for (AclInterface portBefore : portsBefore) {
                if (portBefore.getDpId() != null) {
                    programAclDispatcherTable(deleteFlowEntries, portBefore, NwConstants.DEL_FLOW);
                } else {
                    LOG.debug("Skip ACL dispatcher table update as DP ID for interface {} is not present.",
                            portBefore.getInterfaceId());
                }
            }
            for (AclInterface port : interfaceList) {
                programAclDispatcherTable(addFlowEntries, port, NwConstants.ADD_FLOW);
            }
        }
        Set<BigInteger> dpns = interfaceList.stream().filter(port -> {
            if (port.getDpId() == null) {
                LOG.debug("Skip remote ACL table update as DP ID for interface {} is not present.",
                        port.getInterfaceId());
                return false;
            }
            return true;
        }).map(AclInterface::getDpId).collect(Collectors.toSet());

        programRemoteAclTable(deleteFlowEntries, aclName, remoteAclsDeleted, dpns, NwConstants.DEL_FLOW);
        programRemoteAclTable(addFlowEntries, aclName, remoteAclsAdded, dpns, NwConstants.ADD_FLOW);

        programFlows(aclName, deleteFlowEntries, NwConstants.DEL_FLOW);
        programFlows(aclName, addFlowEntries, NwConstants.ADD_FLOW);
    }

    private void programRemoteAclTable(List<FlowEntity> flowEntries, String aclName, Set<Uuid> remoteAclIds,
            Set<BigInteger> dpns, int addOrRemove) {
        for (Uuid remoteAclId : remoteAclIds) {
            Collection<AclInterface> remoteAclInterfaces = aclDataUtil.getInterfaceList(remoteAclId);
            if (remoteAclInterfaces.isEmpty()) {
                continue;
            }
            Set<AllowedAddressPairs> aaps =
                    remoteAclInterfaces.stream().map(AclInterface::getAllowedAddressPairs).flatMap(List::stream)
                            .filter(AclServiceUtils::isNotIpAllNetwork).collect(Collectors.toSet());

            Integer aclTag = aclServiceUtils.getAclTag(remoteAclId);
            if (addOrRemove == NwConstants.ADD_FLOW) {
                for (BigInteger dpn : dpns) {
                    for (AllowedAddressPairs aap : aaps) {
                        programRemoteAclTableFlow(flowEntries, dpn, aclTag, aap, addOrRemove);
                    }
                }
            } else if (addOrRemove == NwConstants.DEL_FLOW) {
                Set<BigInteger> remoteAclDpns = new HashSet<>();
                Map<String, Set<AclInterface>> mapAclWithPortSet =
                        aclDataUtil.getRemoteAclInterfaces(remoteAclId, this.direction);
                if (mapAclWithPortSet != null) {
                    Map<String, Set<AclInterface>> copyOfMapAclWithPortSet = new HashMap<>(mapAclWithPortSet);
                    copyOfMapAclWithPortSet.remove(aclName);
                    remoteAclDpns = collectDpns(copyOfMapAclWithPortSet);
                }
                Set<BigInteger> dpnsToOperate = new HashSet<>(dpns);
                dpnsToOperate.removeAll(remoteAclDpns);
                LOG.debug(
                        "Deleting flows in Remote ACL table for remoteAclId={}, direction={}, dpnsToOperate={}, "
                                + "remoteAclDpns={}, dpns={}",
                        remoteAclId.getValue(), directionString, dpnsToOperate, remoteAclDpns, dpns);

                for (BigInteger dpn : dpnsToOperate) {
                    for (AllowedAddressPairs aap : aaps) {
                        programRemoteAclTableFlow(flowEntries, dpn, aclTag, aap, addOrRemove);
                    }
                }
            }
        }
    }

    private void updateRemoteAclFilterTable(List<FlowEntity> flowEntries, AclInterface port, int addOrRemove) {
        updateRemoteAclFilterTable(flowEntries, port, port.getSecurityGroups(), port.getAllowedAddressPairs(),
                addOrRemove);
    }

    private void updateRemoteAclFilterTable(List<FlowEntity> flowEntries, AclInterface port, List<Uuid> aclList,
            List<AllowedAddressPairs> aaps, int addOrRemove) {
        if (aclList == null) {
            LOG.debug("Port {} without SGs", port.getInterfaceId());
            return;
        }
        String portId = port.getInterfaceId();
        LOG.trace("updateRemoteAclFilterTable for portId={}, aclList={}, aaps={}, addOrRemove={}", portId, aclList,
                aaps, addOrRemove);
        for (Uuid aclId : aclList) {
            if (aclDataUtil.getRemoteAcl(aclId, this.direction) != null) {
                Integer aclTag = aclServiceUtils.getAclTag(aclId);
                if (addOrRemove == NwConstants.ADD_FLOW) {
                    syncRemoteAclTable(flowEntries, portId, aclId, aclTag, aaps, addOrRemove);
                }
                else if (addOrRemove == NwConstants.DEL_FLOW) {
                    jobCoordinator.enqueueJob(aclId.getValue(), () -> {
                        List<FlowEntity> remoteTableFlowEntries = new ArrayList<>();
                        syncRemoteAclTable(remoteTableFlowEntries, portId, aclId, aclTag, aaps, addOrRemove);
                        programFlows(AclConstants.ACL_JOB_KEY_PREFIX + aclId.getValue(),
                                remoteTableFlowEntries, NwConstants.DEL_FLOW);
                        return Collections.emptyList();
                    });
                }
            }
        }
        Set<Uuid> remoteAclIds = aclServiceUtils.getRemoteAclIdsByDirection(aclList, direction);
        for (Uuid remoteAclId : remoteAclIds) {
            syncRemoteAclTableFromOtherDpns(flowEntries, port, remoteAclId, addOrRemove);
        }
    }

    private void syncRemoteAclTable(List<FlowEntity> flowEntries, String portId, Uuid acl, Integer aclTag,
            List<AllowedAddressPairs> aaps, int addOrRemove) {
        Map<String, Set<AclInterface>> mapAclWithPortSet = aclDataUtil.getRemoteAclInterfaces(acl, this.direction);
        Set<BigInteger> dpns = collectDpns(mapAclWithPortSet);
        for (AllowedAddressPairs aap : aaps) {
            if (!AclServiceUtils.isNotIpAllNetwork(aap)) {
                continue;
            }
            if (aclServiceUtils.skipDeleteInCaseOfOverlappingIP(portId, acl, aap.getIpAddress(),
                    addOrRemove)) {
                LOG.debug("Skipping delete of IP={} in remote ACL table for remoteAclId={}, portId={}",
                        aap.getIpAddress(), portId, acl.getValue());
                continue;
            }
            for (BigInteger dpId : dpns) {
                programRemoteAclTableFlow(flowEntries, dpId, aclTag, aap, addOrRemove);
            }
        }
    }

    private void syncRemoteAclTableFromOtherDpns(List<FlowEntity> flowEntries, AclInterface port, Uuid remoteAclId,
            int addOrRemove) {
        Collection<AclInterface> aclInterfaces = aclDataUtil.getInterfaceList(remoteAclId);

        if (!aclInterfaces.isEmpty() && isFirstPortInDpnWithRemoteAclId(port, remoteAclId)) {
            Integer aclTag = aclServiceUtils.getAclTag(remoteAclId);
            for (AclInterface aclInterface : aclInterfaces) {
                if (port.getInterfaceId().equals(aclInterface.getInterfaceId())) {
                    continue;
                }
                for (AllowedAddressPairs aap : aclInterface.getAllowedAddressPairs()) {
                    if (AclServiceUtils.isNotIpAllNetwork(aap)) {
                        programRemoteAclTableFlow(flowEntries, port.getDpId(), aclTag, aap, addOrRemove);
                    }
                }
            }
        }
    }

    private boolean isFirstPortInDpnWithRemoteAclId(AclInterface port, Uuid remoteAclId) {
        String portId = port.getInterfaceId();
        BigInteger dpId = port.getDpId();
        Map<String, Set<AclInterface>> remoteAclInterfacesMap =
                aclDataUtil.getRemoteAclInterfaces(remoteAclId, direction);
        if (remoteAclInterfacesMap != null) {
            for (Set<AclInterface> interfaceSet : remoteAclInterfacesMap.values()) {
                for (AclInterface aclInterface : interfaceSet) {
                    if (portId.equals(aclInterface.getInterfaceId())) {
                        continue;
                    }
                    if (dpId.equals(aclInterface.getDpId())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected abstract void programRemoteAclTableFlow(List<FlowEntity> flowEntries, BigInteger dpId, Integer aclTag,
            AllowedAddressPairs aap, int addOrRemove);

    protected Set<BigInteger> collectDpns(@Nullable Map<String, Set<AclInterface>> mapAclWithPortSet) {
        Set<BigInteger> dpns = new HashSet<>();
        if (mapAclWithPortSet == null) {
            return dpns;
        }
        for (Set<AclInterface> innerSet : mapAclWithPortSet.values()) {
            if (innerSet == null) {
                continue;
            }
            for (AclInterface inter : innerSet) {
                dpns.add(inter.getDpId());
            }
        }
        return dpns;
    }

    /**
     * Programs the port specific fixed rules.
     *
     * @param flowEntries the flow entries
     * @param dpId the dp id
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param portId the portId
     * @param action the action
     * @param write whether to add or remove the flow.
     */
    protected void programAclPortSpecificFixedRules(List<FlowEntity> flowEntries, BigInteger dpId,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int write) {
        programGotoClassifierTableRules(flowEntries, dpId, allowedAddresses, lportTag, write);
        if (action == Action.ADD || action == Action.REMOVE) {
            programConntrackRecircRules(flowEntries, dpId, allowedAddresses, lportTag, portId, write);
            programPortSpecificDropRules(flowEntries, dpId, lportTag, write);
            programAclCommitRules(flowEntries, dpId, lportTag, portId, write);
        }
        LOG.info("programAclPortSpecificFixedRules: flows for dpId={}, lportId={}, action={}, write={}", dpId, lportTag,
                action, write);
    }

    protected abstract void programGotoClassifierTableRules(List<FlowEntity> flowEntries, BigInteger dpId,
            List<AllowedAddressPairs> aaps, int lportTag, int addOrRemove);

    /**
     * Adds the rule to send the packet to the netfilter to check whether it is a known packet.
     *
     * @param flowEntries the flow entries
     * @param dpId the dpId
     * @param aaps the allowed address pairs
     * @param lportTag the lport tag
     * @param portId the portId
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programConntrackRecircRules(List<FlowEntity> flowEntries, BigInteger dpId,
            List<AllowedAddressPairs> aaps, int lportTag, String portId, int addOrRemove) {
        if (AclServiceUtils.doesIpv4AddressExists(aaps)) {
            programConntrackRecircRule(flowEntries, dpId, lportTag, portId, MatchEthernetType.IPV4, addOrRemove);
        }
        if (AclServiceUtils.doesIpv6AddressExists(aaps)) {
            programConntrackRecircRule(flowEntries, dpId, lportTag, portId, MatchEthernetType.IPV6, addOrRemove);
        }
    }

    protected void programConntrackRecircRule(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            String portId, MatchEthernetType matchEtherType, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(matchEtherType);
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));

        List<InstructionInfo> instructions = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            Long elanTag = getElanIdFromAclInterface(portId);
            if (elanTag == null) {
                LOG.error("ElanId not found for portId={}; Context: dpId={}, lportTag={}, addOrRemove={},", portId,
                        dpId, lportTag, addOrRemove);
                return;
            }
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionNxConntrack(2, 0, 0, elanTag.intValue(), getAclForExistingTrafficTable()));
            instructions.add(new InstructionApplyActions(actionsInfos));
        }

        String flowName =
                this.directionString + "_Fixed_Conntrk_" + dpId + "_" + lportTag + "_" + matchEtherType + "_Recirc";
        addFlowEntryToList(flowEntries, dpId, getAclConntrackSenderTable(), flowName,
                AclConstants.ACL_DEFAULT_PRIORITY, 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions,
                addOrRemove);
    }

    /**
     * Adds the rules to drop the unknown/invalid packets .
     *
     * @param flowEntries the flow entries
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programPortSpecificDropRules(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            int addOrRemove) {
        LOG.debug("Programming Drop Rules: DpId={}, lportTag={}, addOrRemove={}", dpId, lportTag, addOrRemove);
        programConntrackInvalidDropRule(flowEntries, dpId, lportTag, addOrRemove);
        programAclRuleMissDropRule(flowEntries, dpId, lportTag, addOrRemove);
    }

    /**
     * Adds the rule to drop the conntrack invalid packets .
     *
     * @param flowEntries the flow entries
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programConntrackInvalidDropRule(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceOFFlowBuilder.addLPortTagMatches(lportTag,
                AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK, serviceMode);
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        String flowId = this.directionString + "_Fixed_Conntrk_Drop" + dpId + "_" + lportTag + "_Tracked_Invalid";
        addFlowEntryToList(flowEntries, dpId, getAclFilterCumDispatcherTable(), flowId,
                AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, 0, 0, AclServiceUtils.getDropFlowCookie(lportTag),
                matches, instructions, addOrRemove);
    }

    /**
     * Program ACL rule miss drop rule for a port.
     *
     * @param flowEntries the flow entries
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param addOrRemove the add or remove
     */
    protected void programAclRuleMissDropRule(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        String flowId = this.directionString + "_Fixed_Acl_Rule_Miss_Drop_" + dpId + "_" + lportTag;
        addFlowEntryToList(flowEntries, dpId, getAclFilterCumDispatcherTable(), flowId,
                AclConstants.ACL_PORT_SPECIFIC_DROP_PRIORITY, 0, 0, AclServiceUtils.getDropFlowCookie(lportTag),
                matches, instructions, addOrRemove);
    }

    /**
     * Program acl commit rules.
     *
     * @param flowEntries the flow entries
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param portId the port id
     * @param addOrRemove the add or remove
     */
    protected void programAclCommitRules(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag, String portId,
            int addOrRemove) {
        programAclCommitRuleForConntrack(flowEntries, dpId, lportTag, portId, MatchEthernetType.IPV4, addOrRemove);
        programAclCommitRuleForConntrack(flowEntries, dpId, lportTag, portId, MatchEthernetType.IPV6, addOrRemove);
        programAclCommitRuleForNonConntrack(flowEntries, dpId, lportTag, addOrRemove);
    }

    /**
     * Program acl commit rule for conntrack.
     *
     * @param flowEntries the flow entries
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param portId the port id
     * @param matchEtherType the match ether type
     * @param addOrRemove the add or remove
     */
    protected void programAclCommitRuleForConntrack(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            String portId, MatchEthernetType matchEtherType, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(matchEtherType);
        matches.addAll(AclServiceUtils.buildMatchesForLPortTagAndConntrackClassifierType(lportTag,
                AclConntrackClassifierType.CONNTRACK_SUPPORTED, serviceMode));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            Long elanId = getElanIdFromAclInterface(portId);
            if (elanId == null) {
                LOG.error("ElanId not found for portId={}; Context: dpId={}, lportTag={}, addOrRemove={}", portId, dpId,
                        lportTag, addOrRemove);
                return;
            }
            List<NxCtAction> ctActionsList =
                    Lists.newArrayList(new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_EST_STATE));
            actionsInfos.add(new ActionNxConntrack(2, 1, 0, elanId.intValue(), (short) 255, ctActionsList));
            actionsInfos.add(new ActionNxCtClear());
        }
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = directionString + "_Acl_Commit_Conntrack_" + dpId + "_" + lportTag + "_" + matchEtherType;
        // Flow for conntrack traffic to commit and resubmit to dispatcher
        addFlowEntryToList(flowEntries, dpId, getAclCommitterTable(), flowName, AclConstants.ACL_DEFAULT_PRIORITY,
                0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Program acl commit rule for non conntrack.
     *
     * @param flowEntries the flow entries
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param addOrRemove the add or remove
     */
    protected void programAclCommitRuleForNonConntrack(List<FlowEntity> flowEntries, BigInteger dpId, int lportTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.buildMatchesForLPortTagAndConntrackClassifierType(lportTag,
                AclConntrackClassifierType.NON_CONNTRACK_SUPPORTED, serviceMode));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        String flowName = this.directionString + "_Acl_Commit_Non_Conntrack_" + dpId + "_" + lportTag;
        // Flow for non-conntrack traffic to resubmit to dispatcher
        addFlowEntryToList(flowEntries, dpId, getAclCommitterTable(), flowName, AclConstants.ACL_DEFAULT_PRIORITY,
                0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    @Nullable
    protected Long getElanIdFromAclInterface(String elanInterfaceName) {
        AclInterface aclInterface = aclInterfaceCache.get(elanInterfaceName);
        if (null != aclInterface) {
            return aclInterface.getElanId();
        }
        return null;
    }

    protected abstract boolean isValidDirection(Class<? extends DirectionBase> direction);

    protected abstract short getAclConntrackSenderTable();

    protected abstract short getAclForExistingTrafficTable();

    protected abstract short getAclFilterCumDispatcherTable();

    protected abstract short getAclRuleBasedFilterTable();

    protected abstract short getAclRemoteAclTable();

    protected abstract short getAclCommitterTable();
}
