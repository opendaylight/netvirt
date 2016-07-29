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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngressAclServiceImpl extends AbstractAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(IngressAclServiceImpl.class);
    private final DataBroker dataBroker;

    /**
     * Intilaze the member variables.
     * @param dataBroker the data broker instance.
     * @param interfaceManager the interface manager instance.
     * @param mdsalManager the mdsal manager.
     */
    public IngressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
                                 IMdsalApiManager mdsalManager) {
        super(dataBroker,interfaceManager,mdsalManager);
        this.dataBroker = dataBroker;
    }


    /**
     * Bind service.
     *
     * @param interfaceName the interface name
     */
    protected void bindService(String interfaceName) {
        int flowPriority = AclConstants.INGRESS_ACL_DEFAULT_FLOW_PRIORITY;

        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions
                .add(MDSALUtil.buildAndGetGotoTableInstruction(AclConstants.INGRESS_ACL_TABLE_ID, ++instructionKey));
        BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                String.format("%s.%s.%s", "vpn", "ingressacl", interfaceName),
                AclConstants.INGRESS_ACL_SERVICE_PRIORITY, flowPriority, AclConstants.COOKIE_ACL_BASE, instructions);
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                AclConstants.INGRESS_ACL_SERVICE_PRIORITY, ServiceModeEgress.class);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, path, serviceInfo);
    }

    /**
     * Unbind service.
     *
     * @param interfaceName the interface name
     */
    protected void unbindService(String interfaceName) {
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                AclConstants.INGRESS_ACL_SERVICE_PRIORITY, ServiceModeEgress.class);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
    }

    /**
     * Program the default anti-spoofing rule and the conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param attachMac The vm mac address
     * @param addOrRemove add or remove the flow
     */
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress,
                                        String attachMac, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");
        ingressAclDhcpAllowServerTraffic(dpid, dhcpMacAddress, attachMac,
            addOrRemove, AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
        ingressAclDhcpv6AllowServerTraffic(dpid, dhcpMacAddress, attachMac,
            addOrRemove, AclConstants.PROTO_PREFIX_MATCH_PRIORITY);

        //if (securityServicesManager.isConntrackEnabled()) {
        programIngressAclFixedConntrackRule(dpid, attachMac, addOrRemove);
        //}
        programArpRule(dpid,attachMac, addOrRemove);
    }

    /**
     * Programs the custom flows.
     *
     * @param aclUuidList the list of SG uuid to be applied
     * @param dpId the dpId
     * @param attachMac the attached mac
     * @param addOrRemove whether to delete or add flow
     */
    protected void programAclRules(List<Uuid> aclUuidList, BigInteger dpId, String attachMac,
                                   IpPrefixOrAddress attachIp, int addOrRemove) {
        for (Uuid sgUuid :aclUuidList ) {
            Acl acl = AclServiceUtils.getAcl(dataBroker, sgUuid.getValue());
            if (null == acl) {
                LOG.warn("The ACL is empty");
                continue;
            }
            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace: aceList) {
                programAceRule(dpId, attachMac, attachIp, addOrRemove, ace);
            }
        }

    }

    protected void programAceRule(BigInteger dpId, String attachMac, IpPrefixOrAddress attachIp, int addOrRemove,
                                  Ace ace) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionIngress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String,List<MatchInfoBase>> flowMap = null;
        if (aceType instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
        }
        if (null == flowMap) {
            LOG.error("Failed to apply ACL {} vmMacAddress {}", ace.getKey(), attachMac);
            return;
        }
        for ( String  flowName : flowMap.keySet()) {
            List<MatchInfoBase> flows = flowMap.get(flowName);
            flowName += "Ingress" + attachMac + String.valueOf(attachIp.getValue()) + ace.getKey().getRuleName();
            flows.add(new MatchInfo(MatchFieldType.eth_dst,
                new String[] { attachMac }));
            flows.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[] { AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK}));
            flows.addAll(AclServiceUtils.getAllowedIpMatches(attachIp, MatchFieldType.ipv4_destination));
            List<InstructionInfo> instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                new String[] {"1", "0", "0", "255"}, 2));
            instructions.add(new InstructionInfo(InstructionType.apply_actions,
                actionsInfos));
            instructions.add(new InstructionInfo(InstructionType.goto_table,
                new long[] { AclConstants.INGRESS_ACL_NEXT_TABLE_ID }));
            syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, flows, instructions, addOrRemove);
        }
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpAllowServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                  String attachMac, int addOrRemove, int protoPortMatchPriority) {
        final List<MatchInfoBase> matches =
                AclServiceUtils.buildDhcpDestinationMatches(AclConstants.DHCP_SERVER_PORT_IPV4,
                        AclConstants.DHCP_CLIENT_PORT_IPV4, attachMac);

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"1", "0", "0", "255"}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
            actionsInfos));


        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { AclConstants.INGRESS_ACL_NEXT_TABLE_ID }));
        String flowName = "Ingress_DHCP_Server_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }



    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is allowed.
     *
     * @param dpId the dpid
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpv6AllowServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                    String attachMac, int addOrRemove, Integer protoPortMatchPriority) {
        final List<MatchInfoBase> matches =
                AclServiceUtils.buildDhcpDestinationMatches(AclConstants.DHCP_SERVER_PORT_IPV6,
                        AclConstants.DHCP_CLIENT_PORT_IPV6, attachMac);

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"1", "0", "0", "255"}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
            actionsInfos));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { AclConstants.INGRESS_ACL_NEXT_TABLE_ID }));
        String flowName = "Ingress_DHCP_Server_v6" + "_" + dpId + "_" + attachMac + "_" + "_"
                + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rule to send the packet to the netfilter to check whether it is a known packet.
     * @param dpId the dpId
     * @param attachMac the attached mac address
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be send
     * @param conntrackMask the conntrack mask
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackRecircRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                            int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
            new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] {conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_dst,
            new String[] { attachMac }));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"0", "0", "0", Short.toString(AclConstants.INGRESS_ACL_TABLE_ID)}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
            actionsInfos));
        String flowName = "Ingress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds  the rule to forward the packets known packets .
     * @param dpId the dpId
     * @param attachMac the attached mac address
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be send
     * @param conntrackMask the conntrack mask
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackForwardRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                             int conntrackState,
                                      int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] {conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_dst,
            new String[] { attachMac }));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
                new String[] {}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { AclConstants.INGRESS_ACL_NEXT_TABLE_ID }));
        String flowName = "Ingress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds  the rule to drop the unknown/invalid packets .
     * @param dpId the dpId
     * @param attachMac the attached mac address
     * @param priority the priority of the flow
     * @param flowId the flowId
     * @param conntrackState the conntrack state of the packets thats should be send
     * @param conntrackMask the conntrack mask
     * @param addOrRemove whether to add or remove the flow
     */
    private void programConntrackDropRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                          int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] { conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_dst,
            new String[] { attachMac }));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
                new String[] {}));
        String flowName = "Ingress_Fixed_Conntrk_NewDrop_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, priority, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds  the rule to allow arp packets.
     * @param dpId the dpId
     * @param attachMac the attached mac address
     * @param addOrRemove whether to add or remove the flow
     */
    private void programArpRule(BigInteger dpId, String attachMac, int addOrRemove) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_ARP }));
        matches.add(new MatchInfo(MatchFieldType.arp_tha,
                    new String[] { attachMac }));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
                new String[] {}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { AclConstants.INGRESS_ACL_NEXT_TABLE_ID }));
        String flowName = "Ingress_ARP_" + dpId + "_" + attachMac;
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Programs the default connection tracking rules.
     * @param dpid the dp id
     * @param attachMac the attached mac address
     * @param write whether to add or remove the flow.
     */
    private void programIngressAclFixedConntrackRule(BigInteger dpid, String attachMac, int write) {
        programConntrackRecircRule(dpid, attachMac,AclConstants.CT_STATE_UNTRACKED_PRIORITY,
            "Untracked",AclConstants.UNTRACKED_CT_STATE,AclConstants.UNTRACKED_CT_STATE_MASK, write );
        programConntrackForwardRule(dpid, attachMac, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Established", AclConstants.TRACKED_EST_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK,
            write );
        programConntrackForwardRule(dpid, attachMac, AclConstants.CT_STATE_TRACKED_EXIST_PRIORITY,
            "Tracked_Related", AclConstants.TRACKED_REL_CT_STATE, AclConstants.TRACKED_CT_STATE_MASK, write );
        programConntrackDropRule(dpid, attachMac, AclConstants.CT_STATE_NEW_PRIORITY_DROP,
            "Tracked_New", AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK, write );
        programConntrackDropRule(dpid, attachMac, AclConstants.CT_STATE_NEW_PRIORITY_DROP,
            "Tracked_Invalid",AclConstants.TRACKED_INV_CT_STATE, AclConstants.TRACKED_INV_CT_STATE_MASK,
            write );
        LOG.info("programIngressAclFixedConntrackRule :  default connection tracking rule are added.");
    }
}
