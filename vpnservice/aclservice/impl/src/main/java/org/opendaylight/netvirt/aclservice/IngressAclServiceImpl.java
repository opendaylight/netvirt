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
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
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
public class IngressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(IngressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     *
     * @param dataBroker the data broker instance.
     * @param mdsalManager the mdsal manager.
     */
    public IngressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        // Service mode is w.rt. switch
        super(ServiceModeEgress.class, dataBroker, mdsalManager);
    }

    /**
     * Bind service.
     *
     * @param interfaceName the interface name
     */
    @Override
    protected void bindService(String interfaceName) {
        int flowPriority = AclConstants.INGRESS_ACL_DEFAULT_FLOW_PRIORITY;

        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.EGRESS_ACL_TABLE, ++instructionKey));
        BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                String.format("%s.%s.%s", "vpn", "ingressacl", interfaceName), NwConstants.EGRESS_ACL_SERVICE_INDEX,
                flowPriority, AclConstants.COOKIE_ACL_BASE, instructions);
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                NwConstants.EGRESS_ACL_SERVICE_INDEX, ServiceModeEgress.class);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, path, serviceInfo);
    }

    /**
     * Unbind service.
     *
     * @param interfaceName the interface name
     */
    @Override
    protected void unbindService(String interfaceName) {
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                NwConstants.EGRESS_ACL_SERVICE_INDEX, ServiceModeEgress.class);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
    }

    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, String portId, Action action, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");

        if (action == Action.ADD || action == Action.REMOVE) {
            ingressAclDhcpAllowServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove,
                    AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
            ingressAclDhcpv6AllowServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove,
                    AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
        }
        programArpRule(dpid, lportTag, addOrRemove);
        programIngressAclFixedConntrackRule(dpid, allowedAddresses, portId, action, addOrRemove);
    }

    @Override
    protected boolean programAclRules(List<Uuid> aclUuidList, BigInteger dpId, int lportTag, int addOrRemove, String
            portId) {
        if (aclUuidList == null || dpId == null) {
            LOG.warn("one of the ingress acl parameters can not be null. sg {}, dpId {}",
                    aclUuidList, dpId);
            return false;
        }

        for (Uuid sgUuid :aclUuidList ) {
            Acl acl = AclServiceUtils.getAcl(dataBroker, sgUuid.getValue());
            if (null == acl) {
                LOG.warn("The ACL is empty");
                continue;
            }
            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace : aceList) {
                programAceRule(dpId, lportTag, addOrRemove, ace, portId, null);
            }
        }
        return true;
    }

    @Override
    protected void programAceRule(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
                                  List<AllowedAddressPairs> syncAllowedAddresses) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionIngress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String,List<MatchInfoBase>> flowMap = null;
        if (aceType instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
            if (syncAllowedAddresses != null) {
                flowMap = AclServiceUtils.getFlowForAllowedAddresses(syncAllowedAddresses, flowMap, true);
            } else if (aceAttr.getRemoteGroupId() != null) {
                flowMap = AclServiceUtils.getFlowForRemoteAcl(aceAttr.getRemoteGroupId(), portId, flowMap,
                        true);
            }
        }
        if (null == flowMap) {
            LOG.error("Failed to apply ACL {} lportTag {}", ace.getKey(), lportTag);
            return;
        }
        for ( String  flowName : flowMap.keySet()) {
            List<MatchInfoBase> flows = flowMap.get(flowName);
            flowName += "Ingress" + lportTag + ace.getKey().getRuleName();
            flows.add(AclServiceUtils.buildLPortTagMatch(lportTag));
            flows.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                    new long[] {AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK}));

            Long elanTag = AclServiceUtils.getElanIdFromInterface(portId, dataBroker);
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                new String[] {"1", "0", elanTag.toString(), "255"}, 2));
            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

            syncFlow(dpId, NwConstants.EGRESS_ACL_FILTER_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, flows, instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpAllowServerTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag, int addOrRemove,
            int protoPortMatchPriority) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, lportTag);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = "Ingress_DHCP_Server_v4" + dpId + "_" + lportTag + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param lportTag the lport tag
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpv6AllowServerTraffic(BigInteger dpId, String dhcpMacAddress, int lportTag,
            int addOrRemove, Integer protoPortMatchPriority) {
        final List<MatchInfoBase> matches = AclServiceUtils.buildDhcpMatches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, lportTag);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName =
                "Ingress_DHCP_Server_v6" + "_" + dpId + "_" + lportTag + "_" + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_DHCP_SERVER_MATCH_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
            Integer priority, String flowId, int conntrackState, int conntrackMask, String portId, int addOrRemove) {
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            IpPrefixOrAddress attachIp = allowedAddress.getIpAddress();
            String attachMac = allowedAddress.getMacAddress().getValue();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] { NwConstants.ETHTYPE_IPV4 }));
            matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));
            matches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { attachMac }));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchFieldType.ipv4_destination));

            List<InstructionInfo> instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();

            Long elanTag = AclServiceUtils.getElanIdFromInterface(portId, dataBroker);
            actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                    new String[] {"0", "0", elanTag.toString(), Short.toString(
                        NwConstants.EGRESS_ACL_FILTER_TABLE)}, 2));
            instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
            String flowName = "Ingress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_"
                    + String.valueOf(attachIp.getValue()) + "_" + flowId;
            syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Program conntrack tracked rule.
     *
     * @param dpId the dp id
     * @param allowedAddresses the allowed addresses
     * @param priority the priority
     * @param flowId the flow id
     * @param conntrackState the conntrack state
     * @param conntrackMask the conntrack mask
     * @param addOrRemove the add or remove
     */
    private void programConntrackTrackedRule(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            Integer priority, String flowId, int conntrackState, int conntrackMask, int addOrRemove) {
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            IpPrefixOrAddress attachIp = allowedAddress.getIpAddress();
            String attachMac = allowedAddress.getMacAddress().getValue();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] {NwConstants.ETHTYPE_IPV4}));
            matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));
            matches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] {attachMac}));
            matches.addAll(AclServiceUtils.buildIpMatches(attachIp, MatchFieldType.ipv4_destination));

            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionInfo(ActionType.goto_table, new String[] {}));

            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionInfo(InstructionType.goto_table,
                    new long[] {NwConstants.EGRESS_ACL_FILTER_TABLE}));

            String flowName = "Ingress_Fixed_Conntrk_Trk_" + dpId + "_" + attachMac + "_"
                    + String.valueOf(attachIp.getValue()) + "_" + flowId;
            syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, priority, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to add or remove the flow
     */
    private void programArpRule(BigInteger dpId, int lportTag, int addOrRemove) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] {NwConstants.ETHTYPE_ARP}));
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(new ArrayList<>());
        String flowName = "Ingress_ARP_" + dpId + "_" + lportTag;
        syncFlow(dpId, NwConstants.EGRESS_ACL_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Programs the default connection tracking rules.
     *
     * @param dpid the dp id
     * @param allowedAddresses the allowed addresses
     * @param portId the portId
     * @param write whether to add or remove the flow.
     */
    private void programIngressAclFixedConntrackRule(BigInteger dpid, List<AllowedAddressPairs> allowedAddresses,
            String portId, Action action, int write) {
        programConntrackRecircRules(dpid, allowedAddresses, AclConstants.CT_STATE_UNTRACKED_PRIORITY,
            "Untracked", AclConstants.UNTRACKED_CT_STATE, AclConstants.UNTRACKED_CT_STATE_MASK, portId, write);
        programConntrackTrackedRule(dpid, allowedAddresses, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY, "Tracked",
                AclConstants.TRACKED_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK, write);
        LOG.info("programIngressAclFixedConntrackRule :  default connection tracking rule are added.");
    }
}
