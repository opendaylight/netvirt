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
            LOG.error("port security groups cannot be null");
            return false;
        }
        BigInteger dpId = port.getDpId();
        if (dpId == null || port.getLPortTag() == null) {
            LOG.error("Unable to find DP Id from ACL interface with id {}", port.getInterfaceId());
            return false;
        }
        programAclWithAllowedAddress(dpId, port.getAllowedAddressPairs(), port.getLPortTag(), port.getSecurityGroups(),
                Action.ADD, NwConstants.ADD_FLOW, port.getInterfaceId());
        updateRemoteAclFilterTable(port, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean bindAcl(AclInterface port) {
        if (port == null || port.getSecurityGroups() == null) {
            LOG.error("port and port security groups cannot be null");
            return false;
        }
        bindService(port.getInterfaceId());
        updateRemoteAclFilterTable(port, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean unbindAcl(AclInterface port) {
        BigInteger dpId = port.getDpId();
        if (dpId == null) {
            LOG.error("Unable to find DP Id from ACL interface with id {}", port.getInterfaceId());
            return false;
        }
        unbindService(port.getInterfaceId());
        updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW);
        return true;
    }

    @Override
    public boolean updateAcl(AclInterface portBefore, AclInterface portAfter) {
        boolean result = true;
        boolean isPortSecurityEnable = portAfter.getPortSecurityEnabled();
        boolean isPortSecurityEnableBefore = portBefore.getPortSecurityEnabled();
        // if port security is changed, apply/remove Acls
        if (isPortSecurityEnableBefore != isPortSecurityEnable) {
            if (isPortSecurityEnable) {
                result = applyAcl(portAfter) && bindAcl(portAfter);
            } else {
                result = removeAcl(portAfter) && unbindAcl(portAfter);
            }
        } else if (isPortSecurityEnable) {
            // Acls has been updated, find added/removed Acls and act accordingly.
            processInterfaceUpdate(portBefore, portAfter);
            updateRemoteAclFilterTable(portBefore, NwConstants.DEL_FLOW);
            updateRemoteAclFilterTable(portAfter, NwConstants.ADD_FLOW);
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
            programAclWithAllowedAddress(dpId, deletedAllowedAddressPairs, portAfter.getLPortTag(),
                    portAfter.getSecurityGroups(), Action.UPDATE, NwConstants.DEL_FLOW, portAfter.getInterfaceId());
        }
        if (addedAllowedAddressPairs != null && !addedAllowedAddressPairs.isEmpty()) {
            programAclWithAllowedAddress(dpId, addedAllowedAddressPairs, portAfter.getLPortTag(),
                    portAfter.getSecurityGroups(), Action.UPDATE, NwConstants.ADD_FLOW, portAfter.getInterfaceId());
        }
        updateArpForAllowedAddressPairs(dpId, portAfter.getLPortTag(), deletedAllowedAddressPairs,
                portAfter.getAllowedAddressPairs());

        updateAclInterfaceInCache(portBefore);
        // Have to delete and add all rules because there can be following scenario: Interface1 with SG1, Interface2
        // with SG2 (which has ACE with remote SG1). Now When we add SG3 to Interface1, the rule for Interface2 which
        // match the IP of Interface1 will not be installed (but it have to be because Interface1 has more than one SG).
        // So we need to remove all rules and install them from 0, and we cannot handle only the delta.
        updateCustomRules(dpId, portAfter.getLPortTag(), portBefore.getSecurityGroups(), NwConstants.DEL_FLOW,
                portAfter.getInterfaceId(), portAfter.getAllowedAddressPairs());

        updateAclInterfaceInCache(portAfter);

        updateCustomRules(dpId, portAfter.getLPortTag(), portAfter.getSecurityGroups(), NwConstants.ADD_FLOW,
                portAfter.getInterfaceId(), portAfter.getAllowedAddressPairs());
    }

    private void updateAclInterfaceInCache(AclInterface aclInterfaceNew) {
        AclInterfaceCacheUtil.addAclInterfaceToCache(aclInterfaceNew.getInterfaceId(), aclInterfaceNew);
        aclDataUtil.addOrUpdateAclInterfaceMap(aclInterfaceNew.getSecurityGroups(), aclInterfaceNew);
    }

    private void updateCustomRules(BigInteger dpId, int lportTag, List<Uuid> aclUuidList, int action, String portId,
            List<AllowedAddressPairs> syncAllowedAddresses) {
        programAclRules(aclUuidList, dpId, lportTag, action, portId);
        syncRemoteAclRules(aclUuidList, action, portId, syncAllowedAddresses);
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
                    if (currentPortId.equals(port.getInterfaceId())) {
                        continue;
                    }
                    List<Ace> remoteAceList = AclServiceUtils.getAceWithRemoteAclId(dataBroker, port, remoteAclId);
                    for (Ace ace : remoteAceList) {
                        programAceRule(port.getDpId(), port.getLPortTag(), action, aclName, ace, port.getInterfaceId(),
                                syncAllowedAddresses);
                    }
                }
            }
        }
    }

    private void programAclWithAllowedAddress(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, List<Uuid> aclUuidList, Action action, int addOrRemove,
            String portId) {
        programGeneralFixedRules(dpId, "", allowedAddresses, lportTag, action, addOrRemove);
        programSpecificFixedRules(dpId, "", allowedAddresses, lportTag, portId, action, addOrRemove);
        if (action == Action.ADD || action == Action.REMOVE) {
            programAclRules(aclUuidList, dpId, lportTag, addOrRemove, portId);
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
        programAclWithAllowedAddress(dpId, port.getAllowedAddressPairs(), port.getLPortTag(), port.getSecurityGroups(),
                Action.REMOVE, NwConstants.DEL_FLOW, port.getInterfaceId());
        updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW);
        return true;
    }

    @Override
    public boolean applyAce(AclInterface port, String aclName, Ace ace) {
        if (!port.isPortSecurityEnabled()) {
            return false;
        }
        programAceRule(port.getDpId(), port.getLPortTag(), NwConstants.ADD_FLOW, aclName, ace, port.getInterfaceId(),
                null);
        updateRemoteAclFilterTable(port, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean removeAce(AclInterface port, String aclName, Ace ace) {
        if (!port.isPortSecurityEnabled()) {
            return false;
        }
        programAceRule(port.getDpId(), port.getLPortTag(), NwConstants.DEL_FLOW, aclName, ace, port.getInterfaceId(),
                null);
        updateRemoteAclFilterTable(port, NwConstants.DEL_FLOW);
        return true;
    }

    /**
     * Bind service.
     *
     * @param interfaceName
     *            the interface name
     */
    protected abstract void bindService(String interfaceName);

    /**
     * Unbind service.
     *
     * @param interfaceName
     *            the interface name
     */
    protected abstract void unbindService(String interfaceName);

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
     * @param aclUuidList the list of acl uuid to be applied
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to delete or add flow
     * @param portId the port id
     * @return program succeeded
     */
    protected abstract boolean programAclRules(List<Uuid> aclUuidList, BigInteger dpId, int lportTag, int addOrRemove,
                                            String portId);

    /**
     * Programs the ace custom rule.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to delete or add flow
     * @param aclName the acl name
     * @param ace rule to be program
     * @param portId the port id
     * @param syncAllowedAddresses the allowed addresses
     */
    protected abstract void programAceRule(BigInteger dpId, int lportTag, int addOrRemove, String aclName, Ace ace,
            String portId, List<AllowedAddressPairs> syncAllowedAddresses);

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

    protected void updateRemoteAclFilterTable(AclInterface port, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW && AclServiceUtils.isMoreThanOneAcl(port)) {
            LOG.debug("Port {} with more than one SG ({}). Don't remove from ACL filter table", port.getInterfaceId(),
                    port.getSecurityGroups().size());
        } else if (port.getSecurityGroups().isEmpty()) {
            LOG.debug("Port {} with no SG.", port.getInterfaceId(),
                    port.getSecurityGroups().size());
        } else {
            Uuid sg = port.getSecurityGroups().get(0);
            updateRemoteAclFilterTableForSg(port, sg, addOrRemove);
        }
    }

    protected void updateRemoteAclFilterTableForSg(AclInterface port, Uuid acl, int addOrRemove) {
        BigInteger aclId = aclServiceUtils.buildAclId(acl);

        Long elanTag = AclServiceUtils.getElanIdFromInterface(port.getInterfaceId(), dataBroker);
        if (elanTag == null) {
            LOG.debug("Can't find elan id for port {} ", port.getInterfaceId());
            return;
        }
        for (AllowedAddressPairs ip : port.getAllowedAddressPairs()) {
            if (!AclServiceUtils.isNotIpv4AllNetwork(ip)) {
                continue;
            }
            writeCurrentAclForRemoteAcls(acl, addOrRemove, elanTag, ip, aclId);
        }
        writeRemoteAclsForCurrentAcl(acl, port.getDpId(), addOrRemove);
    }

    private void writeRemoteAclsForCurrentAcl(Uuid sgUuid, BigInteger dpId, int addOrRemove) {
        if (dpId == null) {
            LOG.warn("trying to write to null dpnId");
            return;
        }
        Acl acl = AclServiceUtils.getAcl(dataBroker, sgUuid.getValue());
        if (null == acl) {
            LOG.debug("The ACL {} is empty", sgUuid);
            return;
        }

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
                BigInteger aclId = aclServiceUtils.buildAclId(aceAttr.getRemoteGroupId());

                Long elanTag = AclServiceUtils.getElanIdFromInterface(inter.getInterfaceId(), dataBroker);
                if (elanTag == null) {
                    LOG.warn("Can't find elan id for port {} ", inter.getInterfaceId());
                    continue;
                }
                writeRemoteAclForCurrentAclForInterface(dpId, addOrRemove, inter, aclId, elanTag);
            }
        }
    }

    protected abstract void writeCurrentAclForRemoteAcls(Uuid acl, int addOrRemove, Long elanTag,
            AllowedAddressPairs ip, BigInteger aclId);

    protected abstract void writeRemoteAclForCurrentAclForInterface(BigInteger dpId, int addOrRemove,
            AclInterface inter, BigInteger aclId, Long elanTag);

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

}
