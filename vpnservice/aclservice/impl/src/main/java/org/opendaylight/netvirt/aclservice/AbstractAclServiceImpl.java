/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclConntrackClassifierType;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAclServiceImpl implements AclServiceListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAclServiceImpl.class);

    protected final IMdsalApiManager mdsalManager;
    protected final DataBroker dataBroker;
    protected final Class<? extends ServiceModeBase> serviceMode;
    protected final AclDataUtil aclDataUtil;
    protected final AclServiceUtils aclServiceUtils;
    protected final JobCoordinator jobCoordinator;

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
     */
    public AbstractAclServiceImpl(Class<? extends ServiceModeBase> serviceMode, DataBroker dataBroker,
            IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil, AclServiceUtils aclServiceUtils,
            JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.serviceMode = serviceMode;
        this.aclDataUtil = aclDataUtil;
        this.aclServiceUtils = aclServiceUtils;
        this.jobCoordinator = jobCoordinator;

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
        programAclWithAllowedAddress(port, port.getAllowedAddressPairs(), Action.ADD, NwConstants.ADD_FLOW);
        updateRemoteAclFilterTable(port, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean bindAcl(AclInterface port) {
        if (port == null || port.getSecurityGroups() == null) {
            LOG.error("Port and port security groups cannot be null for binding ACL service, port={}", port);
            return false;
        }
        bindService(port);
        if (port.getDpId() != null) {
            updateRemoteAclFilterTable(port, NwConstants.ADD_FLOW);
        }
        return true;
    }

    @Override
    public boolean unbindAcl(AclInterface port) {
        if (port == null) {
            LOG.error("Port cannot be null for unbinding ACL service");
            return false;
        }
        unbindService(port);
        updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW);
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
        boolean isPortSecurityEnable = portAfter.getPortSecurityEnabled();
        boolean isPortSecurityEnableBefore = portBefore.getPortSecurityEnabled();
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
        BigInteger dpId = portAfter.getDpId();
        List<AllowedAddressPairs> addedAllowedAddressPairs =
                AclServiceUtils.getUpdatedAllowedAddressPairs(portAfter.getAllowedAddressPairs(),
                        portBefore.getAllowedAddressPairs());
        List<AllowedAddressPairs> deletedAllowedAddressPairs =
                AclServiceUtils.getUpdatedAllowedAddressPairs(portBefore.getAllowedAddressPairs(),
                        portAfter.getAllowedAddressPairs());
        if (deletedAllowedAddressPairs != null && !deletedAllowedAddressPairs.isEmpty()) {
            programAclWithAllowedAddress(portAfter, deletedAllowedAddressPairs, Action.UPDATE, NwConstants.DEL_FLOW);
        }
        if (addedAllowedAddressPairs != null && !addedAllowedAddressPairs.isEmpty()) {
            programAclWithAllowedAddress(portAfter, addedAllowedAddressPairs, Action.UPDATE, NwConstants.ADD_FLOW);
        }
        updateArpForAllowedAddressPairs(dpId, portAfter.getLPortTag(), deletedAllowedAddressPairs,
                portAfter.getAllowedAddressPairs());
        if (portAfter.getSubnetIpPrefixes() != null && portBefore.getSubnetIpPrefixes() == null) {
            programBroadcastRules(portAfter, NwConstants.ADD_FLOW);
        }

        updateAclInterfaceInCache(portBefore);
        // Have to delete and add all rules because there can be following scenario: Interface1 with SG1, Interface2
        // with SG2 (which has ACE with remote SG1). Now When we add SG3 to Interface1, the rule for Interface2 which
        // match the IP of Interface1 will not be installed (but it have to be because Interface1 has more than one SG).
        // So we need to remove all rules and install them from 0, and we cannot handle only the delta.
        programAclRules(portBefore, portBefore.getSecurityGroups(), NwConstants.DEL_FLOW);
        updateRemoteAclFilterTable(portBefore, NwConstants.DEL_FLOW);

        updateAclInterfaceInCache(portAfter);

        programAclRules(portAfter, portAfter.getSecurityGroups(), NwConstants.ADD_FLOW);
        updateRemoteAclFilterTable(portAfter, NwConstants.ADD_FLOW);
    }

    private void updateAclInterfaceInCache(AclInterface aclInterfaceNew) {
        AclInterfaceCacheUtil.addAclInterfaceToCache(aclInterfaceNew.getInterfaceId(), aclInterfaceNew);
        aclDataUtil.addOrUpdateAclInterfaceMap(aclInterfaceNew.getSecurityGroups(), aclInterfaceNew);
    }

    protected void programAclDispatcherTable(AclInterface port, int addOrRemove) {
        SortedSet<Integer> remoteAclTags = this.aclServiceUtils.getRemoteAclTags(port, this.direction, this.dataBroker);
        if (remoteAclTags.isEmpty()) {
            LOG.debug("No {} rules with remote group id for port={}", this.directionString, port.getInterfaceId());
            return;
        }
        Integer firstRemoteAclTag = remoteAclTags.first();
        Integer lastRemoteAclTag = remoteAclTags.last();

        programFirstRemoteAclEntryInDispatcherTable(port, firstRemoteAclTag, addOrRemove);
        programLastRemoteAclEntryInDispatcherTable(port, lastRemoteAclTag, addOrRemove);

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
            syncFlow(port.getDpId(), getAclFilterCumDispatcherTable(), flowId,
                    AclConstants.ACE_GOTO_NEXT_REMOTE_ACL_PRIORITY, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                    instructions, addOrRemove);

            previousRemoteAclTag = remoteAclTag;
        }
    }

    protected void programFirstRemoteAclEntryInDispatcherTable(AclInterface port, Integer firstRemoteAclTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(AclServiceUtils.buildLPortTagMatch(port.getLPortTag(), serviceMode));
        String flowId = this.directionString + "_ACL_Dispatcher_First_" + port.getDpId() + "_" + port.getLPortTag()
                + "_" + firstRemoteAclTag;

        List<InstructionInfo> instructions =
                AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclRuleBasedFilterTable());
        instructions.add(AclServiceUtils.getWriteMetadataForRemoteAclTag(firstRemoteAclTag));
        syncFlow(port.getDpId(), getAclFilterCumDispatcherTable(), flowId, AclConstants.ACE_FIRST_REMOTE_ACL_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    protected void programLastRemoteAclEntryInDispatcherTable(AclInterface port, Integer lastRemoteAclTag,
            int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.buildMatchesForLPortTagAndRemoteAclTag(port.getLPortTag(), lastRemoteAclTag,
                serviceMode));
        String flowId = this.directionString + "_ACL_Dispatcher_Last_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                + lastRemoteAclTag;

        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        syncFlow(port.getDpId(), getAclFilterCumDispatcherTable(), flowId, AclConstants.ACE_LAST_REMOTE_ACL_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_DROP_FLOW, matches, instructions, addOrRemove);
    }

    private void programAclWithAllowedAddress(AclInterface port, List<AllowedAddressPairs> allowedAddresses,
            Action action, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        LOG.debug("Applying ACL Allowed Address on DpId {}, lportTag {}, Action {}", dpId, lportTag, action);
        List<Uuid> aclUuidList = port.getSecurityGroups();
        String portId = port.getInterfaceId();
        programGeneralFixedRules(port, "", allowedAddresses, action, addOrRemove);
        programSpecificFixedRules(dpId, "", allowedAddresses, lportTag, portId, action, addOrRemove);
        if (action == Action.ADD || action == Action.REMOVE) {
            programAclRules(port, aclUuidList, addOrRemove);
        }
        programAclDispatcherTable(port, addOrRemove);
    }

    /**
     * Programs the acl custom rules.
     *
     * @param port acl interface
     * @param aclUuidList the list of acl uuid to be applied
     * @param addOrRemove whether to delete or add flow
     * @return program succeeded
     */
    protected boolean programAclRules(AclInterface port, List<Uuid> aclUuidList, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        LOG.debug("Applying custom rules on DpId {}, lportTag {}", dpId, port.getLPortTag());
        if (aclUuidList == null || dpId == null) {
            LOG.warn("{} ACL parameters can not be null. dpId={}, aclUuidList={}", this.directionString, dpId,
                    aclUuidList);
            return false;
        }
        for (Uuid aclUuid : aclUuidList) {
            Acl acl = AclServiceUtils.getAcl(dataBroker, aclUuid.getValue());
            if (null == acl) {
                LOG.warn("The ACL {} not found in config DS", aclUuid.getValue());
                continue;
            }
            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace: aceList) {
                programAceRule(port, addOrRemove, acl.getAclName(), ace, null);
            }
        }
        return true;
    }

    /**
     * Programs the ace specific rule.
     *
     * @param port acl interface
     * @param addOrRemove whether to delete or add flow
     * @param aclName the acl name
     * @param ace rule to be program
     * @param syncAllowedAddresses the allowed addresses
     */
    protected void programAceRule(AclInterface port, int addOrRemove, String aclName, Ace ace,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!isValidDirection(aceAttr.getDirection())) {
            LOG.trace("Ignoring {} direction while processing for {} ACE Rule {}", aceAttr.getDirection(),
                    this.directionString, ace.getRuleName());
            return;
        }
        LOG.debug("Program {} ACE rule for dpId={}, lportTag={}, addOrRemove={}, aclName={}, ace={}, portId={}",
                this.directionString, port.getDpId(), port.getLPortTag(), addOrRemove, aclName, ace.getRuleName(),
                port.getInterfaceId());

        Matches matches = ace.getMatches();
        Map<String, List<MatchInfoBase>> flowMap = null;
        if (matches.getAceType() instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
            if (!AclServiceUtils.doesAceHaveRemoteGroupId(aceAttr)) {
                // programming for ACE which doesn't have any remote group Id
                programForAceNotHavingRemoteAclId(port, ace, flowMap, addOrRemove);
            } else {
                Uuid remoteAclId = aceAttr.getRemoteGroupId();
                // programming for ACE which have remote group Id
                programAceSpecificFlows(port, ace, flowMap, remoteAclId, addOrRemove);
            }
        }
    }

    protected void programForAceNotHavingRemoteAclId(AclInterface port, Ace ace,
            Map<String, List<MatchInfoBase>> flowMap, int addOrRemove) {
        if (null == flowMap) {
            return;
        }

        MatchInfoBase lportTagMatch = AclServiceUtils.buildLPortTagMatch(port.getLPortTag(), serviceMode);
        for (Entry<String, List<MatchInfoBase>> entry : flowMap.entrySet()) {
            String flowName = entry.getKey();
            List<MatchInfoBase> matches = entry.getValue();
            matches.add(lportTagMatch);
            String flowId = flowName + this.directionString + "_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                    + ace.getKey().getRuleName();

            List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclCommitterTable());
            syncFlow(port.getDpId(), getAclFilterCumDispatcherTable(), flowId,
                    AclConstants.ACE_WITHOUT_REMOTE_ACL_PRIORITY, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, matches,
                    instructions, addOrRemove);
        }
    }

    protected void programAceSpecificFlows(AclInterface port, Ace ace, Map<String, List<MatchInfoBase>> flowMap,
            Uuid remoteAclId, int addOrRemove) {
        if (null == flowMap) {
            return;
        }
        Integer remoteAclTag = this.aclServiceUtils.getAclTag(remoteAclId);
        if (remoteAclTag == null || remoteAclTag == AclConstants.INVALID_ACL_TAG) {
            LOG.error("Failed building metadata match for ACL={}. Failed to allocate id", remoteAclId.getValue());
            return;
        }
        MatchInfoBase remoteAclIdMatch = AclServiceUtils.buildAclTagMetadataMatch(remoteAclTag);
        MatchInfoBase lportTagMatch = AclServiceUtils.buildLPortTagMatch(port.getLPortTag(), serviceMode);

        for (Entry<String, List<MatchInfoBase>> entry : flowMap.entrySet()) {
            String flowName = entry.getKey();
            List<MatchInfoBase> matches = entry.getValue();
            matches.add(lportTagMatch);
            matches.add(remoteAclIdMatch);
            String flowId = flowName + this.directionString + "_" + port.getDpId() + "_" + port.getLPortTag() + "_"
                    + ace.getKey().getRuleName();

            List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getGotoInstructionInfo(getAclRemoteAclTable());
            syncFlow(port.getDpId(), getAclRuleBasedFilterTable(), flowId, AclConstants.ACL_DEFAULT_PRIORITY, "ACL", 0,
                    0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    @Override
    public boolean removeAcl(AclInterface port) {
        BigInteger dpId = port.getDpId();
        if (dpId == null) {
            LOG.error("Unable to find DP Id from ACL interface with id {}", port.getInterfaceId());
            return false;
        }
        programAclWithAllowedAddress(port, port.getAllowedAddressPairs(), Action.REMOVE, NwConstants.DEL_FLOW);
        updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW, true);
        return true;
    }

    @Override
    public boolean applyAce(AclInterface port, String aclName, Ace ace) {
        if (!port.isPortSecurityEnabled() || port.getDpId() == null) {
            return false;
        }
        programAceRule(port, NwConstants.ADD_FLOW, aclName, ace, null);
        updateRemoteAclFilterTable(port, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean removeAce(AclInterface port, String aclName, Ace ace) {
        if (!port.isPortSecurityEnabled() || port.getDpId() == null) {
            return false;
        }
        programAceRule(port, NwConstants.DEL_FLOW, aclName, ace, null);

        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (AclServiceUtils.doesAceHaveRemoteGroupId(aceAttr)) {
            updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW);
        }
        return true;
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
     * Program the default anti-spoofing rules.
     *
     * @param port the acl interface
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param action add/modify/remove action
     * @param addOrRemove addorRemove
     */
    protected abstract void programGeneralFixedRules(AclInterface port, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, Action action, int addOrRemove);

    /**
     * Update arp for allowed address pairs.
     *
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param deletedAAP the deleted allowed address pairs
     * @param addedAAP the added allowed address pairs
     */
    protected abstract void updateArpForAllowedAddressPairs(BigInteger dpId, int lportTag,
            List<AllowedAddressPairs> deletedAAP, List<AllowedAddressPairs> addedAAP);

    /**
     * Program the default specific rules.
     *
     * @param dpId the dpId
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param portId the port id
     * @param action add/modify/remove action
     * @param addOrRemove addorRemove
     */
    protected abstract void programSpecificFixedRules(BigInteger dpId, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove);

    /**
     * Programs broadcast rules.
     *
     * @param port the Acl Interface port
     * @param addOrRemove whether to delete or add flow
     */
    protected abstract void programBroadcastRules(AclInterface port, int addOrRemove);

    /**
     * Writes/remove the flow to/from the datastore.
     *
     * @param dpId
     *            the dpId
     * @param tableId
     *            the tableId
     * @param flowId
     *            the flowId
     * @param priority
     *            the priority
     * @param flowName
     *            the flow name
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
    protected void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
            int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase> matches,
            List<InstructionInfo> instructions, int addOrRemove) {
        jobCoordinator.enqueueJob(flowName, () -> {
            if (addOrRemove == NwConstants.DEL_FLOW) {
                FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                        idleTimeOut, hardTimeOut, cookie, matches, null);
                LOG.trace("Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);

                return Collections.singletonList(mdsalManager.removeFlow(dpId, flowEntity));

            } else {
                FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                        idleTimeOut, hardTimeOut, cookie, matches, instructions);
                LOG.trace("Installing DpnId {}, flowId {}", dpId, flowId);
                return Collections.singletonList(mdsalManager.installFlow(dpId, flowEntity));
            }
        });
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

    private void updateRemoteAclFilterTable(AclInterface port, int addOrRemove) {
        updateRemoteAclFilterTable(port, addOrRemove, false);
    }

    private void updateRemoteAclFilterTable(AclInterface port, int addOrRemove, boolean isAclDeleted) {
        if (port.getSecurityGroups() == null) {
            LOG.debug("Port {} without SGs", port.getInterfaceId());
            return;
        }

        Uuid acl = port.getSecurityGroups().get(0);
        Integer aclTag = aclServiceUtils.getAclTag(acl);
        if (aclDataUtil.getRemoteAcl(acl) != null) {
            Map<String, Set<AclInterface>> mapAclWithPortSet = aclDataUtil.getRemoteAclInterfaces(acl);
            Set<BigInteger> dpns = collectDpns(mapAclWithPortSet);

            for (AllowedAddressPairs ip : port.getAllowedAddressPairs()) {
                if (!AclServiceUtils.isNotIpv4AllNetwork(ip)) {
                    continue;
                }
                for (BigInteger dpId : dpns) {
                    programRemoteAclTableFlow(dpId, aclTag, ip, addOrRemove);
                }
            }
            syncRemoteAclTableFromOtherDpns(port, acl, aclTag, addOrRemove);
        } else {
            LOG.debug("Port {} with more than one SG ({}). Don't change ACL filter table", port.getInterfaceId(),
                    port.getSecurityGroups().size());
        }
    }

    private void syncRemoteAclTableFromOtherDpns(AclInterface port, Uuid acl, Integer aclTag, int addOrRemove) {
        Collection<AclInterface> aclInterfaces = aclDataUtil.getInterfaceList(acl);
        BigInteger dpId = port.getDpId();
        boolean isFirstPortInDpn = true;
        if (aclInterfaces != null) {
            for (AclInterface aclInterface : aclInterfaces) {
                if (port.getInterfaceId().equals(aclInterface.getInterfaceId())) {
                    continue;
                }
                if (dpId.equals(aclInterface.getDpId())) {
                    isFirstPortInDpn = false;
                    break;
                }
            }
            if (isFirstPortInDpn) {
                for (AclInterface aclInterface : aclInterfaces) {
                    if (port.getInterfaceId().equals(aclInterface.getInterfaceId())) {
                        continue;
                    }
                    for (AllowedAddressPairs ip : aclInterface.getAllowedAddressPairs()) {
                        programRemoteAclTableFlow(port.getDpId(), aclTag, ip, addOrRemove);
                    }
                }
            }
        }
    }

    protected abstract void programRemoteAclTableFlow(BigInteger dpId, Integer aclTag, AllowedAddressPairs ip,
            int addOrRemove);

    protected String getOperAsString(int flowOper) {
        String oper;
        switch (flowOper) {
            case NwConstants.ADD_FLOW:
                oper = "Add";
                break;
            case NwConstants.DEL_FLOW:
                oper = "Del";
                break;
            case NwConstants.MOD_FLOW:
                oper = "Mod";
                break;
            default:
                oper = "UNKNOWN";
        }
        return oper;
    }

    protected Set<BigInteger> collectDpns(Map<String, Set<AclInterface>> mapAclWithPortSet) {
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
     * @param dpId the dp id
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param portId the portId
     * @param action the action
     * @param write whether to add or remove the flow.
     */
    protected void programAclPortSpecificFixedRules(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, String portId, Action action, int write) {
        programGotoClassifierTableRules(dpId, allowedAddresses, lportTag, write);
        if (action == Action.ADD || action == Action.REMOVE) {
            programConntrackRecircRules(dpId, allowedAddresses, lportTag, portId, write);
            programPortSpecificDropRules(dpId, lportTag, write);
            programAclCommitRules(dpId, lportTag, portId, write);
        }
        LOG.info("programAclPortSpecificFixedRules: flows for dpId={}, lportId={}, action={}, write={}", dpId, lportTag,
                action, write);
    }

    protected abstract void programGotoClassifierTableRules(BigInteger dpId, List<AllowedAddressPairs> aaps,
            int lportTag, int addOrRemove);

    /**
     * Adds the rule to send the packet to the netfilter to check whether it is a known packet.
     *
     * @param dpId the dpId
     * @param aaps the allowed address pairs
     * @param lportTag the lport tag
     * @param portId the portId
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programConntrackRecircRules(BigInteger dpId, List<AllowedAddressPairs> aaps, int lportTag,
            String portId, int addOrRemove) {
        if (AclServiceUtils.doesIpv4AddressExists(aaps)) {
            programConntrackRecircRule(dpId, lportTag, portId, MatchEthernetType.IPV4, addOrRemove);
        }
        if (AclServiceUtils.doesIpv6AddressExists(aaps)) {
            programConntrackRecircRule(dpId, lportTag, portId, MatchEthernetType.IPV6, addOrRemove);
        }
    }

    protected void programConntrackRecircRule(BigInteger dpId, int lportTag, String portId,
            MatchEthernetType matchEtherType, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(matchEtherType);
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));

        List<InstructionInfo> instructions = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            Long elanTag = AclServiceUtils.getElanIdFromAclInterface(portId);
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
        syncFlow(dpId, getAclConntrackSenderTable(), flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rules to drop the unknown/invalid packets .
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programPortSpecificDropRules(BigInteger dpId, int lportTag, int addOrRemove) {
        LOG.debug("Programming Drop Rules: DpId={}, lportTag={}, addOrRemove={}", dpId, lportTag, addOrRemove);
        programConntrackInvalidDropRule(dpId, lportTag, addOrRemove);
        programAclRuleMissDropRule(dpId, lportTag, addOrRemove);
    }

    /**
     * Adds the rule to drop the conntrack invalid packets .
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    protected void programConntrackInvalidDropRule(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceOFFlowBuilder.addLPortTagMatches(lportTag,
                AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK, serviceMode);
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        String flowId = this.directionString + "_Fixed_Conntrk_Drop" + dpId + "_" + lportTag + "_Tracked_Invalid";
        syncFlow(dpId, getAclFilterCumDispatcherTable(), flowId, AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, "ACL",
                0, 0, AclConstants.COOKIE_ACL_DROP_FLOW, matches, instructions, addOrRemove);
    }

    /**
     * Program ACL rule miss drop rule for a port.
     *
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param addOrRemove the add or remove
     */
    protected void programAclRuleMissDropRule(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        String flowId = this.directionString + "_Fixed_Acl_Rule_Miss_Drop_" + dpId + "_" + lportTag;
        syncFlow(dpId, getAclFilterCumDispatcherTable(), flowId, AclConstants.CT_STATE_TRACKED_NEW_DROP_PRIORITY, "ACL",
                0, 0, AclConstants.COOKIE_ACL_DROP_FLOW, matches, instructions, addOrRemove);
    }

    /**
     * Program acl commit rules.
     *
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param portId the port id
     * @param addOrRemove the add or remove
     */
    protected void programAclCommitRules(BigInteger dpId, int lportTag, String portId, int addOrRemove) {
        programAclCommitRuleForConntrack(dpId, lportTag, portId, MatchEthernetType.IPV4, addOrRemove);
        programAclCommitRuleForConntrack(dpId, lportTag, portId, MatchEthernetType.IPV6, addOrRemove);
        programAclCommitRuleForNonConntrack(dpId, lportTag, addOrRemove);
    }

    /**
     * Program acl commit rule for conntrack.
     *
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param portId the port id
     * @param matchEtherType the match ether type
     * @param addOrRemove the add or remove
     */
    protected void programAclCommitRuleForConntrack(BigInteger dpId, int lportTag, String portId,
            MatchEthernetType matchEtherType, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(matchEtherType);
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        matches.add(
                AclServiceUtils.buildAclConntrackClassifierTypeMatch(AclConntrackClassifierType.CONNTRACK_SUPPORTED));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            Long elanId = AclServiceUtils.getElanIdFromAclInterface(portId);
            if (elanId == null) {
                LOG.error("ElanId not found for portId={}; Context: dpId={}, lportTag={}, addOrRemove={}", portId, dpId,
                        lportTag, addOrRemove);
                return;
            }
            actionsInfos.add(new ActionNxConntrack(2, 1, 0, elanId.intValue(), (short) 255));
        }
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = directionString + "_Acl_Commit_Conntrack_" + dpId + "_" + lportTag + "_" + matchEtherType;
        // Flow for conntrack traffic to commit and resubmit to dispatcher
        syncFlow(dpId, getAclCommitterTable(), flowName, AclConstants.ACL_DEFAULT_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Program acl commit rule for non conntrack.
     *
     * @param dpId the dp id
     * @param lportTag the lport tag
     * @param addOrRemove the add or remove
     */
    protected void programAclCommitRuleForNonConntrack(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        matches.add(AclServiceUtils
                .buildAclConntrackClassifierTypeMatch(AclConntrackClassifierType.NON_CONNTRACK_SUPPORTED));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions();
        String flowName = this.directionString + "_Acl_Commit_Non_Conntrack_" + dpId + "_" + lportTag;
        // Flow for non-conntrack traffic to resubmit to dispatcher
        syncFlow(dpId, getAclCommitterTable(), flowName, AclConstants.ACL_DEFAULT_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    protected abstract boolean isValidDirection(Class<? extends DirectionBase> direction);

    protected abstract short getAclAntiSpoofingTable();

    protected abstract short getAclConntrackClassifierTable();

    protected abstract short getAclConntrackSenderTable();

    protected abstract short getAclForExistingTrafficTable();

    protected abstract short getAclFilterCumDispatcherTable();

    protected abstract short getAclRuleBasedFilterTable();

    protected abstract short getAclRemoteAclTable();

    protected abstract short getAclCommitterTable();
}
