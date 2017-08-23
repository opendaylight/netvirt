/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
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

    /**
     * Initialize the member variables.
     *
     * @param serviceMode
     *            the service mode
     * @param dataBroker
     *            the data broker instance.
     * @param mdsalManager
     *            the mdsal manager instance.
     * @param aclDataUtil
     *            the acl data util.
     * @param aclServiceUtils
     *            the acl service util.
     */
    public AbstractAclServiceImpl(Class<? extends ServiceModeBase> serviceMode, DataBroker dataBroker,
            IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil, AclServiceUtils aclServiceUtils) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.serviceMode = serviceMode;
        this.aclDataUtil = aclDataUtil;
        this.aclServiceUtils = aclServiceUtils;
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
        if (port == null) {
            LOG.error("port cannot be null");
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
        BigInteger dpId = port.getDpId();
        if (dpId == null) {
            LOG.error("Unable to find DpId from ACL interface with id {}", port.getInterfaceId());
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
                result = applyAcl(portAfter) && bindAcl(portAfter);
            } else {
                result = removeAcl(portBefore) && unbindAcl(portBefore);
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

        updateAclInterfaceInCache(portBefore);
        // Have to delete and add all rules because there can be following scenario: Interface1 with SG1, Interface2
        // with SG2 (which has ACE with remote SG1). Now When we add SG3 to Interface1, the rule for Interface2 which
        // match the IP of Interface1 will not be installed (but it have to be because Interface1 has more than one SG).
        // So we need to remove all rules and install them from 0, and we cannot handle only the delta.
        updateCustomRules(portBefore, portBefore.getSecurityGroups(), NwConstants.DEL_FLOW,
                portAfter.getAllowedAddressPairs());
        updateRemoteAclFilterTable(portBefore, NwConstants.DEL_FLOW);

        updateAclInterfaceInCache(portAfter);

        updateCustomRules(portAfter, portAfter.getSecurityGroups(), NwConstants.ADD_FLOW,
                portAfter.getAllowedAddressPairs());
        updateRemoteAclFilterTable(portAfter, NwConstants.ADD_FLOW);
    }

    private void updateAclInterfaceInCache(AclInterface aclInterfaceNew) {
        AclInterfaceCacheUtil.addAclInterfaceToCache(aclInterfaceNew.getInterfaceId(), aclInterfaceNew);
        aclDataUtil.addOrUpdateAclInterfaceMap(aclInterfaceNew.getSecurityGroups(), aclInterfaceNew);
    }

    private void updateCustomRules(AclInterface port, List<Uuid> aclUuidList, int action,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        programAclRules(port, aclUuidList, action);
        syncRemoteAclRules(aclUuidList, action, port.getInterfaceId(), syncAllowedAddresses);
    }

    private void syncRemoteAclRules(List<Uuid> aclUuidList, int action, String currentPortId,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        if (aclUuidList == null) {
            LOG.warn("security groups are null");
            return;
        }

        for (Uuid remoteAclId : aclUuidList) {
            Map<String, Set<AclInterface>> mapAclWithPortSet = aclDataUtil.getRemoteAclInterfaces(remoteAclId);
            if (mapAclWithPortSet == null) {
                continue;
            }
            for (Entry<String, Set<AclInterface>> entry : mapAclWithPortSet.entrySet()) {
                String aclName = entry.getKey();
                for (AclInterface port : entry.getValue()) {
                    if (currentPortId.equals(port.getInterfaceId())
                            || (port.getSecurityGroups() != null && port.getSecurityGroups().size() == 1)) {
                        continue;
                    }
                    List<Ace> remoteAceList = AclServiceUtils.getAceWithRemoteAclId(dataBroker, port, remoteAclId);
                    for (Ace ace : remoteAceList) {
                        programAceRule(port, action, aclName, ace, syncAllowedAddresses);
                    }
                }
            }
        }
    }

    private void programAclWithAllowedAddress(AclInterface port, List<AllowedAddressPairs> allowedAddresses,
            Action action, int addOrRemove) {
        BigInteger dpId = port.getDpId();
        int lportTag = port.getLPortTag();
        LOG.debug("Applying ACL Allowed Address on DpId {}, lportTag {}, Action {}", dpId, lportTag, action);
        List<Uuid> aclUuidList = port.getSecurityGroups();
        String portId = port.getInterfaceId();
        programGeneralFixedRules(dpId, "", allowedAddresses, lportTag, action, addOrRemove);
        programSpecificFixedRules(dpId, "", allowedAddresses, lportTag, portId, action, addOrRemove);
        if (action == Action.ADD || action == Action.REMOVE) {
            programAclRules(port, aclUuidList, addOrRemove);
        }
        syncRemoteAclRules(aclUuidList, addOrRemove, portId, allowedAddresses);
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
        updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW);
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
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param action add/modify/remove action
     * @param addOrRemove addorRemove
     */
    protected abstract void programGeneralFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, Action action, int addOrRemove);

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
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param portId the port id
     * @param action add/modify/remove action
     * @param addOrRemove addorRemove
     */
    protected abstract void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove);

    /**
     * Programs the acl custom rules.
     *
     * @param port acl interface
     * @param aclUuidList the list of acl uuid to be applied
     * @param addOrRemove whether to delete or add flow
     * @return program succeeded
     */
    protected abstract boolean programAclRules(AclInterface port, List<Uuid> aclUuidList, int addOrRemove);

    /**
     * Programs the ace custom rule.
     *
     * @param port acl interface
     * @param addOrRemove whether to delete or add flow
     * @param aclName the acl name
     * @param ace rule to be program
     * @param syncAllowedAddresses the allowed addresses
     */
    protected abstract void programAceRule(AclInterface port, int addOrRemove, String aclName, Ace ace,
            List<AllowedAddressPairs> syncAllowedAddresses);

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
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(flowName, () -> {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            if (addOrRemove == NwConstants.DEL_FLOW) {
                FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                        idleTimeOut, hardTimeOut, cookie, matches, null);
                LOG.trace("Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);

                futures.add(mdsalManager.removeFlow(dpId, flowEntity));

            } else {
                FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                        idleTimeOut, hardTimeOut, cookie, matches, instructions);
                LOG.trace("Installing DpnId {}, flowId {}", dpId, flowId);
                futures.add(mdsalManager.installFlow(dpId, flowEntity));
            }
            return futures;
        });
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

        if (AclServiceUtils.exactlyOneAcl(port)) {
            Uuid acl = port.getSecurityGroups().get(0);
            BigInteger aclId = aclServiceUtils.buildAclId(acl);
            if (aclDataUtil.getRemoteAcl(acl) != null) {
                Map<String, Set<AclInterface>> mapAclWithPortSet = aclDataUtil.getRemoteAclInterfaces(acl);
                Set<BigInteger> dpns = collectDpns(mapAclWithPortSet);

                for (AllowedAddressPairs ip : port.getAllowedAddressPairs()) {
                    if (!AclServiceUtils.isNotIpv4AllNetwork(ip)) {
                        continue;
                    }
                    for (BigInteger dpId : dpns) {
                        updateRemoteAclTableForPort(port, acl, addOrRemove, ip, aclId, dpId);
                    }
                }
                syncRemoteAclTableFromOtherDpns(port, acl, aclId, addOrRemove);
            } else {
                LOG.debug("Port {} with more than one SG ({}). Don't change ACL filter table", port.getInterfaceId(),
                        port.getSecurityGroups().size());
            }
        } else if (port.getSecurityGroups() != null && port.getSecurityGroups().size() > 1) {
            updateRemoteAclTableForMultipleAcls(port, addOrRemove, port.getInterfaceId());
        }
        syncRemoteAclTable(port, addOrRemove, port.getInterfaceId(), isAclDeleted);
    }

    private void syncRemoteAclTableFromOtherDpns(AclInterface port, Uuid acl, BigInteger aclId, int addOrRemove) {
        List<AclInterface> aclInterfaces = aclDataUtil.getInterfaceList(acl);
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
                        updateRemoteAclTableForPort(aclInterface, acl, addOrRemove, ip, aclId, port.getDpId());
                    }
                }
            }
        }
    }

    private void syncRemoteAclTable(AclInterface port, int addOrRemove, String ignorePort, boolean isAclDeleted) {
        for (Uuid aclUuid : port.getSecurityGroups()) {
            if (aclDataUtil.getRemoteAcl(aclUuid) == null) {
                continue;
            }
            List<AclInterface> aclInterfaces = aclDataUtil.getInterfaceList(aclUuid);
            if (aclInterfaces != null) {
                for (AclInterface aclInterface : aclInterfaces) {
                    if (aclInterface.getInterfaceId().equals(port.getInterfaceId())
                            || AclServiceUtils.exactlyOneAcl(aclInterface)) {
                        continue;
                    }
                    boolean allMultipleAcls = true;
                    List<Uuid> remoteInterfaceRemoteAcls = aclInterface.getSecurityGroups();
                    if (remoteInterfaceRemoteAcls != null) {
                        for (Uuid remoteInterfaceRemoteAcl : remoteInterfaceRemoteAcls) {
                            if (aclDataUtil.getRemoteAcl(remoteInterfaceRemoteAcl) == null) {
                                continue;
                            }
                            List<AclInterface> aclInterfaces2 = aclDataUtil.getInterfaceList(remoteInterfaceRemoteAcl);
                            if (aclInterfaces2 != null) {
                                for (AclInterface aclInterface2 : aclInterfaces2) {
                                    if (aclInterface2.getInterfaceId().equals(aclInterface.getInterfaceId())) {
                                        continue;
                                    }
                                    if (aclInterface2.getSecurityGroups().size() == 1) {
                                        allMultipleAcls = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    int addRremove = (allMultipleAcls) ? NwConstants.DEL_FLOW : NwConstants.ADD_FLOW;
                    addRremove = (isAclDeleted) ? NwConstants.DEL_FLOW : addRremove;
                    for (AllowedAddressPairs ip : aclInterface.getAllowedAddressPairs()) {
                        if (!AclServiceUtils.isNotIpv4AllNetwork(ip)) {
                            continue;
                        }
                        updateRemoteAclTableForPort(aclInterface, aclUuid, addRremove, ip,
                                aclServiceUtils.buildAclId(aclUuid), aclInterface.getDpId());
                    }
                }
            }
        }
    }

    private void updateRemoteAclTableForMultipleAcls(AclInterface port, int addOrRemove, String ignorePort) {
        for (Uuid aclUuid : port.getSecurityGroups()) {
            if (aclDataUtil.getRemoteAcl(aclUuid) == null) {
                continue;
            }
            Acl acl = AclServiceUtils.getAcl(dataBroker, aclUuid.getValue());
            if (null == acl) {
                LOG.debug("The ACL {} is empty", aclUuid);
                return;
            }

            Map<String, Set<AclInterface>> mapAclWithPortSet = aclDataUtil.getRemoteAclInterfaces(aclUuid);
            Set<BigInteger> dpns = collectDpns(mapAclWithPortSet);

            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace : aceList) {
                SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
                if (aceAttr.getRemoteGroupId() == null) {
                    continue;
                }
                List<AclInterface> interfaceList = aclDataUtil.getInterfaceList(aceAttr.getRemoteGroupId());
                if (interfaceList == null) {
                    continue;
                }

                for (AclInterface inter : interfaceList) {
                    if (ignorePort.equals(inter.getInterfaceId())) {
                        continue;
                    }
                    if (inter.getSecurityGroups() != null && inter.getSecurityGroups().size() == 1) {
                        BigInteger aclId = aclServiceUtils.buildAclId(aceAttr.getRemoteGroupId());
                        for (AllowedAddressPairs ip : port.getAllowedAddressPairs()) {
                            if (!AclServiceUtils.isNotIpv4AllNetwork(ip)) {
                                continue;
                            }
                            for (BigInteger dpnId : dpns) {
                                updateRemoteAclTableForPort(port, aceAttr.getRemoteGroupId(), addOrRemove, ip, aclId,
                                        dpnId);
                            }
                        }
                        syncRemoteAclTableFromOtherDpns(port, aclUuid, aclId, addOrRemove);
                    }
                }
            }
        }
    }

    protected abstract void updateRemoteAclTableForPort(AclInterface port, Uuid acl, int addOrRemove,
            AllowedAddressPairs ip, BigInteger aclId, BigInteger dpId);

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

    protected char[] getIpPrefixOrAddress(AllowedAddressPairs ip) {
        if (ip.getIpAddress().getIpAddress() != null) {
            return ip.getIpAddress().getIpAddress().getValue();
        } else if (ip.getIpAddress().getIpPrefix() != null) {
            return ip.getIpAddress().getIpPrefix().getValue();
        }
        return null;
    }

    /**
     * Gets the priority of acl flow which is to be either removed or added.
     *
     * @param poolName
     *            the acl pool name
     * @param flowName
     *            the flow name
     * @param addOrRemove
     *            add or remove the entries.
     * @return the acl flow priority
     */
    protected int getAclFlowPriority(String poolName, String flowName, int addOrRemove) {
        int priority;
        if (addOrRemove == NwConstants.DEL_FLOW) {
            priority = aclServiceUtils.releaseAndRemoveFlowPriorityFromCache(poolName, flowName);
        } else {
            priority = aclServiceUtils.allocateAndSaveFlowPriorityInCache(poolName, flowName);
        }
        return priority;
    }
}
