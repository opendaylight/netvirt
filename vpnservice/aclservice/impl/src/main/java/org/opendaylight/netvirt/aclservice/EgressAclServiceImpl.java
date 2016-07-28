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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EgressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(EgressAclServiceImpl.class);
    private final DataBroker dataBroker;

    /**
     * Initialize the member variables.
     *
     * @param dataBroker the data broker instance.
     * @param interfaceManager the interface manager instance.
     * @param mdsalManager the mdsal manager instance.
     */
    public EgressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
                                IMdsalApiManager mdsalManager) {
        // Service mode is w.rt. switch
        super(ServiceModeIngress.class, dataBroker, interfaceManager, mdsalManager);
        this.dataBroker = dataBroker;
    }

    /**
     * Bind service.
     *
     * @param interfaceName the interface name
     */
    @Override
    protected void bindService(String interfaceName) {
        int flowPriority = AclConstants.EGRESS_ACL_DEFAULT_FLOW_PRIORITY;

        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(AclConstants.EGRESS_ACL_TABLE_ID, ++instructionKey));
        BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                String.format("%s.%s.%s", "vpn", "egressacl", interfaceName), AclConstants.EGRESS_ACL_SERVICE_PRIORITY,
                flowPriority, AclConstants.COOKIE_ACL_BASE, instructions);
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                AclConstants.EGRESS_ACL_SERVICE_PRIORITY, ServiceModeIngress.class);
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
                AclConstants.EGRESS_ACL_SERVICE_PRIORITY, ServiceModeIngress.class);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
    }

    /**
     * Program the default anti-spoofing rule and the conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param addOrRemove addorRemove
     */
    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            String attachMac = allowedAddress.getMacAddress().getValue();
            egressAclDhcpAllowClientTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
            egressAclDhcpv6AllowClientTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
            egressAclDhcpDropServerTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
            egressAclDhcpv6DropServerTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
            programArpRule(dpid, attachMac, addOrRemove);
        }

        programEgressAclFixedConntrackRule(dpid, allowedAddresses, lportTag, addOrRemove);
    }

    /**
     * Programs the custom flows.
     *
     * @param aclUuidList the list of acl uuid to be applied
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param addOrRemove whether to delete or add flow
     */
    @Override
    protected void programAclRules(List<Uuid> aclUuidList, BigInteger dpId, int lportTag, int addOrRemove) {
        LOG.trace("Applying custom rules DpId {}, lportTag {}", dpId, lportTag);
        for (Uuid sgUuid :aclUuidList ) {
            Acl acl = AclServiceUtils.getAcl(dataBroker, sgUuid.getValue());
            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace: aceList) {
                programAceRule(dpId, lportTag, addOrRemove, ace);
            }
        }

    }

    @Override
    protected void programAceRule(BigInteger dpId, int lportTag, int addOrRemove, Ace ace) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionEgress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String,List<MatchInfoBase>> flowMap = null;
        if (aceType instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
        }
        if (null == flowMap) {
            LOG.error("Failed to apply ACL {} lportTag {}", ace.getKey(), lportTag);
            return;
        }
        //The flow map contains list of flows if port range is selected.
        for ( String  flowName : flowMap.keySet()) {
            List<MatchInfoBase> flows = flowMap.get(flowName);
            flowName += "Egress" + lportTag + ace.getKey().getRuleName();
            flows.addAll(AclServiceUtils.getLPortTagMatches(lportTag));
            flows.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                    new long[] {AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK}));

            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                new String[] {"1", "0", "0", "255"}, 2));
            List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

            syncFlow(dpId, AclConstants.EGRESS_ACL_FILTER_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, flows, instructions, addOrRemove);
        }
    }

    /**
     * Anti-spoofing rule to block the Ipv4 DHCP server traffic from the port.
     *
     * @param dpId the dpId
     * @param dhcpMacAddress the Dhcp mac address
     * @param attachMac the attached mac address
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclDhcpDropServerTraffic(BigInteger dpId, String dhcpMacAddress, String attachMac,
            int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildDhcpSourceMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                AclConstants.DHCP_CLIENT_PORT_IPV4, attachMac);

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action,
            new String[] {}));
        String flowName = "Egress_DHCP_Server_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Drop_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Anti-spoofing rule to block the Ipv6 DHCP server traffic from the port.
     *
     * @param dpId the dpId
     * @param dhcpMacAddress the Dhcp mac address
     * @param attachMac the attached mac address
     * @param addOrRemove add/remove the flow.
     */
    private void egressAclDhcpv6DropServerTraffic(BigInteger dpId, String dhcpMacAddress, String attachMac,
            int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.buildDhcpSourceMatches(AclConstants.DHCP_SERVER_PORT_IPV6,
                AclConstants.DHCP_CLIENT_PORT_IPV6, attachMac);

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action,
            new String[] {}));
        String flowName = "Egress_DHCP_Server_v6" + "_" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Drop_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of the port
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpAllowClientTraffic(BigInteger dpId, String dhcpMacAddress, String attachMac,
            int addOrRemove) {
        final List<MatchInfoBase> matches =
                AclServiceUtils.buildDhcpSourceMatches(AclConstants.DHCP_CLIENT_PORT_IPV4,
                        AclConstants.DHCP_SERVER_PORT_IPV4, attachMac);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"1", "0", "0", "255"}, 2));
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = "Egress_DHCP_Client_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is
     * allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of the port
     * @param addOrRemove whether to add or remove the flow
     */
    private void egressAclDhcpv6AllowClientTraffic(BigInteger dpId, String dhcpMacAddress, String attachMac,
            int addOrRemove) {
        final List<MatchInfoBase> matches =
                AclServiceUtils.buildDhcpSourceMatches(AclConstants.DHCP_CLIENT_PORT_IPV6,
                        AclConstants.DHCP_SERVER_PORT_IPV6, attachMac);

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"1", "0", "0", "255"}, 2));
        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(actionsInfos);

        String flowName = "Egress_DHCP_Client_v6" + "_" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackRecircRules(BigInteger dpId, List<AllowedAddressPairs> allowedAddresses,
            Integer priority, String flowId, int conntrackState, int conntrackMask, int addOrRemove) {
        for (AllowedAddressPairs allowedAddress : allowedAddresses) {
            IpPrefixOrAddress attachIp = allowedAddress.getIpAddress();
            String attachMac = allowedAddress.getMacAddress().getValue();

            List<MatchInfoBase> matches = new ArrayList<>();
            matches.add(new MatchInfo(MatchFieldType.eth_type, new long[] {NwConstants.ETHTYPE_IPV4}));
            matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));
            matches.add(new MatchInfo(MatchFieldType.eth_src, new String[] {attachMac}));
            matches.addAll(AclServiceUtils.getAllowedIpMatches(attachIp, MatchFieldType.ipv4_source));

            List<InstructionInfo> instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                    new String[] {"0", "0", "0", Short.toString(AclConstants.EGRESS_ACL_FILTER_TABLE_ID)}, 2));
            instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

            String flowName = "Egress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_"
                    + String.valueOf(attachIp.getValue()) + "_" + flowId;
            syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
        }
    }

    /**
     * Adds the rule to forward the packets known packets.
     *
     * @param dpId the dpId
     * @param lportTag the lport tag
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be
     *        send
     * @param conntrackMask the conntrack mask
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackForwardRule(BigInteger dpId, int lportTag, Integer priority, String flowId,
            int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.getLPortTagMatches(lportTag));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(new ArrayList<>());

        String flowName = "Egress_Fixed_Conntrk_Untrk_" + dpId + "_" + lportTag + "_" + flowId;
        syncFlow(dpId, AclConstants.EGRESS_ACL_FILTER_TABLE_ID, flowName, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackDropRule(BigInteger dpId, int lportTag, Integer priority, String flowId,
            int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.addAll(AclServiceUtils.getLPortTagMatches(lportTag));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state, new long[] {conntrackState, conntrackMask}));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        String flowName = "Egress_Fixed_Conntrk_NewDrop_" + dpId + "_" + lportTag + "_" + flowId;
        syncFlow(dpId, AclConstants.EGRESS_ACL_FILTER_TABLE_ID, flowName, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rule to allow arp packets.
     *
     * @param dpId the dpId
     * @param attachMac the attached mac address
     * @param addOrRemove whether to add or remove the flow
     */
    private void programArpRule(BigInteger dpId, String attachMac, int addOrRemove) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
            new long[] { NwConstants.ETHTYPE_ARP }));
        matches.add(new MatchInfo(MatchFieldType.arp_sha,
            new String[] { attachMac }));

        List<InstructionInfo> instructions = getDispatcherTableResubmitInstructions(new ArrayList<>());

        String flowName = "Egress_ARP_" + dpId + "_" + attachMac ;
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Programs the default connection tracking rules.
     *
     * @param dpid the dp id
     * @param allowedAddresses the allowed addresses
     * @param lportTag the lport tag
     * @param write whether to add or remove the flow.
     */
    private void programEgressAclFixedConntrackRule(BigInteger dpid, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, int write) {
        programConntrackRecircRules(dpid, allowedAddresses, AclConstants.CT_STATE_UNTRACKED_PRIORITY,
            "Untracked",AclConstants.UNTRACKED_CT_STATE,AclConstants.UNTRACKED_CT_STATE_MASK, write );
        programConntrackForwardRule(dpid, lportTag, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Established", AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK,
            write );
        programConntrackForwardRule(dpid, lportTag, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Related", AclConstants.TRACKED_REL_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK, write );
        programConntrackDropRule(dpid, lportTag, AclConstants.CT_STATE_NEW_PRIORITY_DROP,
            "Tracked_New", AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK, write );
        programConntrackDropRule(dpid, lportTag, AclConstants.CT_STATE_NEW_PRIORITY_DROP,
            "Tracked_Invalid", AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK,
            write );
        LOG.info("programEgressAclFixedConntrackRule :  default connection tracking rule are added.");
    }
}
