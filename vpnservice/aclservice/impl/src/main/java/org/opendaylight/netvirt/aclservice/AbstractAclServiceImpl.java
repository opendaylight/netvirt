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
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAclServiceImpl implements AclServiceListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAclServiceImpl.class);

    protected final IMdsalApiManager mdsalManager;
    protected final OdlInterfaceRpcService interfaceManager;
    protected final DataBroker dataBroker;
    protected final Class<? extends ServiceModeBase> serviceMode;

    /**
     * Initialize the member variables.
     *
     * @param serviceMode
     *            the service mode
     * @param dataBroker
     *            the data broker instance.
     * @param interfaceManager
     *            the interface manager instance.
     * @param mdsalManager
     *            the mdsal manager instance.
     */
    public AbstractAclServiceImpl(Class<? extends ServiceModeBase> serviceMode, DataBroker dataBroker,
            OdlInterfaceRpcService interfaceManager, IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.mdsalManager = mdsalManager;
        this.serviceMode = serviceMode;
    }

    @Override
    public boolean applyAcl(Interface port) {

        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
            return false;
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        if (interfaceState == null) {
            LOG.warn("Unable to find interface state for port {}", port.getName());
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpIdFromIterfaceState(interfaceState);
        if (dpId == null) {
            LOG.error("Unable to find DP Id from interface state {}", interfaceState.getName());
            return false;
        }

        programAclWithAllowedAddress(dpId, AclServiceUtils.getPortAllowedAddresses(port), interfaceState.getIfIndex(),
                AclServiceUtils.getInterfaceAcls(port), NwConstants.ADD_FLOW);

        return true;

        // TODO: uncomment bindservice() when the acl flow programming is
        // implemented
        // bindService(port.getName());
    }

    @Override
    public boolean updateAcl(Interface portBefore, Interface portAfter) {
        boolean result = false;
        boolean isPortSecurityEnable = AclServiceUtils.isPortSecurityEnabled(portAfter);
        boolean isPortSecurityEnableBefore = AclServiceUtils.isPortSecurityEnabled(portBefore);
        // if port security is changed, apply/remove Acls
        if (isPortSecurityEnableBefore != isPortSecurityEnable) {
            if (isPortSecurityEnable) {
                result = applyAcl(portAfter);
            } else {
                result = removeAcl(portAfter);
            }
        } else if (isPortSecurityEnable) {
            // Acls has been updated, find added/removed Acls and act
            // accordingly.
            this.processInterfaceUpdate(portBefore, portAfter);
        }

        return result;
    }

    private void processInterfaceUpdate(Interface portBefore, Interface portAfter) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, portAfter.getName());
        BigInteger dpId = AclServiceUtils.getDpIdFromIterfaceState(interfaceState);
        List<AllowedAddressPairs> addedAllowedAddressPairs =
                AclServiceUtils.getUpdatedAllowedAddressPairs(portAfter,portBefore);
        List<AllowedAddressPairs> deletedAllowedAddressPairs =
                AclServiceUtils.getUpdatedAllowedAddressPairs(portBefore, portAfter);
        if (addedAllowedAddressPairs != null && !addedAllowedAddressPairs.isEmpty()) {
            programAclWithAllowedAddress(dpId, addedAllowedAddressPairs, interfaceState.getIfIndex(),
                    AclServiceUtils.getInterfaceAcls(portAfter), NwConstants.ADD_FLOW);
        }
        if (deletedAllowedAddressPairs != null && !deletedAllowedAddressPairs.isEmpty()) {
            programAclWithAllowedAddress(dpId, deletedAllowedAddressPairs, interfaceState.getIfIndex(),
                    AclServiceUtils.getInterfaceAcls(portAfter), NwConstants.DEL_FLOW);
        }

        List<Uuid> addedAcls = AclServiceUtils.getUpdatedAclList(portAfter, portBefore);
        List<Uuid> deletedAcls = AclServiceUtils.getUpdatedAclList(portBefore, portAfter);
        if (addedAcls != null && !addedAcls.isEmpty()) {
            updateCustomRules(dpId, interfaceState.getIfIndex(), addedAcls, NwConstants.ADD_FLOW);
        }
        if (deletedAcls != null && !deletedAcls.isEmpty()) {
            updateCustomRules(dpId, interfaceState.getIfIndex(), deletedAcls, NwConstants.DEL_FLOW);
        }
    }

    private void updateCustomRules(BigInteger dpId, int lportTag, List<Uuid> aclUuidList, int action) {
        programAclRules(aclUuidList, dpId, lportTag, action);
    }

    private void programAclWithAllowedAddress(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, List<Uuid> aclUuidList, int addOrRemove) {
        programFixedRules(dpId, "", allowedAddresses, lportTag, addOrRemove);
        programAclRules(aclUuidList, dpId, lportTag, addOrRemove);
    }


    @Override
    public boolean removeAcl(Interface port) {
        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
            interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        programAclWithAllowedAddress(dpId, AclServiceUtils.getPortAllowedAddresses(port),
                interfaceState.getIfIndex(), AclServiceUtils.getInterfaceAcls(port), NwConstants.DEL_FLOW);

        return true;
        // TODO: uncomment unbindService() when the acl flow programming is
        // implemented
        // unbindService(port.getName());
    }

    @Override
    public boolean applyAce(Interface port, Ace ace) {
        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        programAceRule(dpId, interfaceState.getIfIndex(), NwConstants.ADD_FLOW, ace);
        return true;
    }

    @Override
    public boolean removeAce(Interface port, Ace ace) {
        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        programAceRule(dpId, interfaceState.getIfIndex(), NwConstants.DEL_FLOW, ace);
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
     * Program the default anti-spoofing rule and the conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove addorRemove
     */
    protected abstract void programFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, int addOrRemove);

    /**
     * Programs the acl custom rules.
     *
     * @param aclUuidList the list of acl uuid to be applied
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to delete or add flow
     */
    protected abstract boolean programAclRules(List<Uuid> aclUuidList, BigInteger dpId, int lportTag, int addOrRemove);

    /**
     * Programs the ace custom rule.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to delete or add flow
     * @param ace rule to be program
     */
    protected abstract void programAceRule(BigInteger dpId, int lportTag, int addOrRemove, Ace ace);

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
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName, idleTimeOut,
                    hardTimeOut, cookie, matches, null);
            LOG.trace("Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName, idleTimeOut,
                    hardTimeOut, cookie, matches, instructions);
            LOG.trace("Installing DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.installFlow(flowEntity);
        }
    }

    /**
     * Gets the dispatcher table resubmit instructions based on ingress/egress
     * service mode w.r.t switch.
     *
     * @param actionsInfos the actions infos
     * @return the instructions for dispatcher table resubmit
     */
    protected List<InstructionInfo> getDispatcherTableResubmitInstructions(List<ActionInfo> actionsInfos) {
        short dispatcherTableId = NwConstants.LPORT_DISPATCHER_TABLE;
        if (ServiceModeEgress.class.equals(this.serviceMode)) {
            dispatcherTableId = AclConstants.EGRESS_LPORT_DISPATCHER_TABLE;
        }

        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[] {Short.toString(dispatcherTableId)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        return instructions;
    }

    protected void appendExtendingInstructions(List<InstructionInfo> instructions) {
        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack, new String[] { "1", "0", "0", "255" }, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

    }

    protected void appendExtendingMatches(List<MatchInfoBase> flows) {
        flows.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[] { AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK }));
    }

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
}
