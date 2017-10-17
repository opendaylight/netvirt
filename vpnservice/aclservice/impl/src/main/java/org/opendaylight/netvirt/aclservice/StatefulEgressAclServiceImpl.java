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
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.MatchCriteria;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.PacketHandling;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.Permit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Provides the stateful implementation for egress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */

public class StatefulEgressAclServiceImpl extends AbstractEgressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatefulEgressAclServiceImpl.class);

    public StatefulEgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator) {
        super(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils, jobCoordinator);
    }

    /**
     * Program conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove addorRemove
     */
    @Override
    protected void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove) {
        programEgressAclFixedConntrackRule(dpid, allowedAddresses, lportTag, portId, action, addOrRemove);
    }

    @Override
    protected String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
            Map<String, List<MatchInfoBase>> flowMap, String flowName) {
        List<MatchInfoBase> matches = flowMap.get(flowName);
        flowName += "Egress" + lportTag + ace.getKey().getRuleName();
        matches.add(buildLPortTagMatch(lportTag));
        matches.add(new NxMatchCtState(AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK));

        Long elanId = AclServiceUtils.getElanIdFromAclInterface(portId);
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions;
        PacketHandling packetHandling = ace.getActions() != null ? ace.getActions().getPacketHandling() : null;
        if (packetHandling instanceof Permit) {
            actionsInfos.add(new ActionNxConntrack(2, 1, 0, elanId.intValue(), (short) 255));
            instructions = getDispatcherTableResubmitInstructions(actionsInfos);
        } else {
            instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        }
        String poolName = AclServiceUtils.getAclPoolName(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE, packetHandling);
        // For flows related remote ACL, unique flow priority is used for
        // each flow to avoid overlapping flows
        int priority = getAclFlowPriority(poolName, flowName, addOrRemove);

        syncFlow(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE, flowName, priority, "ACL", 0, 0,
            AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        return flowName;
    }

    /**
     * Adds the rule to send the packet to the netfilter to check whether it is
     * a known packet.
     *
     * @param dpId the dpId
     * @param allowedAddresses the allowed addresses
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param portId the portId
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackRecircRules(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            Integer priority, String flowId, String portId, int addOrRemove) {
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            IpPrefixOrAddress attachIp = allowedAddress.getIpAddress();
            MacAddress attachMac = allowedAddress.getMacAddress();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(new MatchEthernetSource(attachMac));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchCriteria.MATCH_SOURCE));

            Long elanTag = AclServiceUtils.getElanIdFromAclInterface(portId);
            List<InstructionInfo> instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionNxConntrack(2, 0, 0, elanTag.intValue(),
                    NwConstants.INGRESS_ACL_REMOTE_ACL_TABLE));
            instructions.add(new InstructionApplyActions(actionsInfos));

            String flowName = "Egress_Fixed_Conntrk_" + dpId + "_" + attachMac.getValue() + "_"
                    + String.valueOf(attachIp.getValue()) + "_" + flowId;
            syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Programs the default connection tracking rules.
     *
     * @param dpid the dp id
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param portId the portId
     * @param action the action
     * @param write whether to add or remove the flow.
     */
    private void programEgressAclFixedConntrackRule(BigInteger dpid, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, String portId, Action action, int write) {
        programConntrackRecircRules(dpid, allowedAddresses, AclConstants.CT_STATE_UNTRACKED_PRIORITY,
            "Recirc", portId, write);
        programEgressConntrackDropRules(dpid, lportTag, write);
        LOG.info("programEgressAclFixedConntrackRule :  default connection tracking rule are {} on DpId {}"
                + "lportTag {}.", write == NwConstants.ADD_FLOW ? "added" : "removed", dpid, lportTag);
    }

    /**
     * Adds the rule to drop the unknown/invalid packets .
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param tableId table id
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackDropRule(BigInteger dpId, int lportTag, Integer priority, String flowId,
            int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceOFFlowBuilder.addLPortTagMatches(lportTag, conntrackState,
                conntrackMask, ServiceModeEgress.class);
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        flowId = "Ingress_Fixed_Conntrk_Drop" + dpId + "_" + lportTag + "_" + flowId;
        syncFlow(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE, flowId, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_DROP_FLOW, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rules to drop the unknown/invalid packets .
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void programEgressConntrackDropRules(BigInteger dpId, int lportTag, int addOrRemove) {
        LOG.debug("Applying Egress ConnTrack Drop Rules on DpId {}, lportTag {}", dpId, lportTag);
        programConntrackDropRule(dpId, lportTag, AclConstants.CT_STATE_TRACKED_NEW_DROP_PRIORITY, "Tracked_New",
                AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK, addOrRemove);
        programConntrackDropRule(dpId, lportTag, AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, "Tracked_Invalid",
                AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK, addOrRemove);
    }
}
