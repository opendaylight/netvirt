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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.MatchCriteria;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.PacketHandling;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.Permit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the stateful implementation for ingress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */
public class StatefulIngressAclServiceImpl extends AbstractIngressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatefulIngressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     *
     * @param dataBroker the data broker instance.
     * @param mdsalManager the mdsal manager.
     * @param aclDataUtil
     *            the acl data util.
     * @param aclServiceUtils
     *            the acl service util.
     */
    public StatefulIngressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator) {
        // Service mode is w.rt. switch
        super(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils, jobCoordinator);
    }

    /**
     * Program conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove add or remove the flow
     */
    @Override
    protected void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove) {
        programIngressAclFixedConntrackRule(dpid, lportTag, allowedAddresses, portId, action, addOrRemove);
    }

    @Override
    protected String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
            Map<String, List<MatchInfoBase>> flowMap, String flowName) {
        List<MatchInfoBase> matches = flowMap.get(flowName);
        flowName += "Ingress" + lportTag + ace.getKey().getRuleName();
        matches.add(buildLPortTagMatch(lportTag));
        matches.add(new NxMatchCtState(AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK));

        Long elanTag = AclServiceUtils.getElanIdFromAclInterface(portId);
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions;
        PacketHandling packetHandling = ace.getActions() != null ? ace.getActions().getPacketHandling() : null;
        if (packetHandling instanceof Permit) {
            actionsInfos.add(new ActionNxConntrack(2, 1, 0, elanTag.intValue(), (short) 255));
            instructions = getDispatcherTableResubmitInstructions(actionsInfos);
        } else {
            instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        }

        String poolName = AclServiceUtils.getAclPoolName(dpId, NwConstants.EGRESS_ACL_FILTER_TABLE, packetHandling);
        // For flows related remote ACL, unique flow priority is used for
        // each flow to avoid overlapping flows
        int priority = getAclFlowPriority(poolName, flowName, addOrRemove);

        syncFlow(dpId, NwConstants.EGRESS_ACL_FILTER_TABLE, flowName, priority, "ACL", 0, 0,
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
            matches.add(MatchEthernetType.IPV4);
            matches.add(new MatchEthernetDestination(attachMac));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchCriteria.MATCH_DESTINATION));

            List<InstructionInfo> instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();

            Long elanTag = AclServiceUtils.getElanIdFromAclInterface(portId);
            actionsInfos.add(new ActionNxConntrack(2, 0, 0, elanTag.intValue(),
                    NwConstants.EGRESS_ACL_REMOTE_ACL_TABLE));
            instructions.add(new InstructionApplyActions(actionsInfos));
            String flowName = "Ingress_Fixed_Conntrk_" + dpId + "_" + attachMac.getValue() + "_"
                    + String.valueOf(attachIp.getValue()) + "_" + flowId;
            syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Programs the default connection tracking rules.
     *
     * @param dpid the dp id
     * @param lportTag the lport tag
     * @param allowedAddresses the allowed addresses
     * @param portId the portId
     * @param write whether to add or remove the flow.
     */
    private void programIngressAclFixedConntrackRule(BigInteger dpid, int lportTag,
            List<AllowedAddressPairs> allowedAddresses, String portId, Action action, int write) {
        programConntrackRecircRules(dpid, allowedAddresses, AclConstants.CT_STATE_UNTRACKED_PRIORITY,
            "Recirc",portId, write);
        programIngressConntrackDropRules(dpid, lportTag, write);
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
                conntrackMask, ServiceModeIngress.class);
        List<InstructionInfo> instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();

        flowId = "Egress_Fixed_Conntrk_Drop" + dpId + "_" + lportTag + "_" + flowId;
        syncFlow(dpId, NwConstants.EGRESS_ACL_FILTER_TABLE, flowId, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_DROP_FLOW, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rules to drop the unknown/invalid packets .
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void programIngressConntrackDropRules(BigInteger dpId, int lportTag, int addOrRemove) {
        LOG.debug("Applying Egress ConnTrack Drop Rules on DpId {}, lportTag {}", dpId, lportTag);
        programConntrackDropRule(dpId, lportTag, AclConstants.CT_STATE_TRACKED_NEW_DROP_PRIORITY, "Tracked_New",
                AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK, addOrRemove);
        programConntrackDropRule(dpId, lportTag, AclConstants.CT_STATE_TRACKED_INVALID_PRIORITY, "Tracked_Invalid",
                AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK, addOrRemove);
    }

    /**
     * Bind service.
     *
     * @param aclInterface the acl interface
     */
    @Override
    public void bindService(AclInterface aclInterface) {
        String interfaceName = aclInterface.getInterfaceId();
        jobCoordinator.enqueueJob(interfaceName,
            () -> {
                int instructionKey = 0;
                List<Instruction> instructions = new ArrayList<>();
                Long vpnId = aclInterface.getVpnId();
                if (vpnId != null) {
                    instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
                        MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
                    LOG.debug("Binding ACL service for interface {} with vpnId {}", interfaceName, vpnId);
                } else {
                    Long elanTag = aclInterface.getElanId();
                    instructions.add(
                            MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getElanTagMetadata(elanTag),
                            MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
                    LOG.debug("Binding ACL service for interface {} with ElanTag {}", interfaceName, elanTag);
                }
                instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(AclConstants
                        .EGRESS_ACL_DUMMY_TABLE, ++instructionKey));
                int flowPriority = AclConstants.INGRESS_ACL_DEFAULT_FLOW_PRIORITY;
                short serviceIndex = ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME,
                        NwConstants.EGRESS_ACL_SERVICE_INDEX);
                BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                        String.format("%s.%s.%s", "acl", "ingressacl", interfaceName), serviceIndex, flowPriority,
                        AclConstants.COOKIE_ACL_BASE, instructions);
                InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                        ServiceIndex.getIndex(NwConstants.EGRESS_ACL_SERVICE_NAME,
                        NwConstants.EGRESS_ACL_SERVICE_INDEX), ServiceModeEgress.class);

                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                writeTxn.put(LogicalDatastoreType.CONFIGURATION, path, serviceInfo,
                        WriteTransaction.CREATE_MISSING_PARENTS);

                return Collections.singletonList(writeTxn.submit());
            });
    }
}
