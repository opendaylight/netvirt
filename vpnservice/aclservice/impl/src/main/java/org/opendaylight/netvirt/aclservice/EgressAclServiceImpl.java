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
import org.opendaylight.genius.mdsalutil.FlowEntity;
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
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EgressAclServiceImpl implements AclServiceListener {

    private static final Logger logger = LoggerFactory.getLogger(EgressAclServiceImpl.class);

    private final IMdsalApiManager mdsalManager;
    private final OdlInterfaceRpcService interfaceManager;
    private final DataBroker dataBroker;

    /**
     * Initialize the member variables.
     *
     * @param dataBroker       the data broker instance.
     * @param interfaceManager the interface manager instance.
     * @param mdsalManager     the mdsal manager instance.
     */
    public EgressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
                                IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.mdsalManager = mdsalManager;
    }

    @Override
    public boolean applyAcl(Interface port) {

        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        String attachMac = interfaceState.getPhysAddress().getValue();
        programFixedSecurityGroup(dpId, "", attachMac, NwConstants.ADD_FLOW);
        List<Uuid> securityGroupsUuid = AclServiceUtils.getPortSecurityGroups(port);
        programCustomRules(port, securityGroupsUuid, dpId, attachMac, NwConstants.ADD_FLOW);
        // TODO: uncomment bindservice() when the acl flow programming is
        // implemented
        // bindService(port.getName());
        return true;
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
            // Acls has been updated, find added/removed Acls and act accordingly.
            this.processInterfaceUpdate(portBefore, portAfter);
        }

        return result;
    }

    private void processInterfaceUpdate(Interface portBefore, Interface portAfter) {
        List<Uuid> addedGroup = AclServiceUtils.getUpdatedAclList(portAfter, portBefore);
        List<Uuid> deletedGroup = AclServiceUtils.getUpdatedAclList(portBefore, portAfter);
        if (addedGroup != null && !addedGroup.isEmpty()) {
            updateCustomRules(portAfter, deletedGroup, NwConstants.ADD_FLOW);
        }
        if (deletedGroup != null && !deletedGroup.isEmpty()) {
            updateCustomRules(portAfter, deletedGroup, NwConstants.DEL_FLOW);
        }
    }

    private void updateCustomRules(Interface portAfter, List<Uuid> deletedGroup, int action) {
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, portAfter.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, portAfter.getName());
        String attachMac = interfaceState.getPhysAddress().getValue();
        programCustomRules(portAfter, deletedGroup, dpId, attachMac, action);
    }

    @Override
    public boolean removeAcl(Interface port) {
        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
                interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        String attachMac = interfaceState.getPhysAddress().getValue();
        programFixedSecurityGroup(dpId, "", attachMac, NwConstants.DEL_FLOW);
        List<Uuid> securityGroupsUuid = AclServiceUtils.getPortSecurityGroups(port);
        programCustomRules(port, securityGroupsUuid, dpId, attachMac, NwConstants.DEL_FLOW);

        // TODO: uncomment unbindService() when the acl flow programming is
        // implemented
        // unbindService(port.getName());
        return true;
    }

    @Override
    public boolean applyAce(Interface port, Ace ace) {
        return false;
    }

    @Override
    public boolean removeAce(Interface port, Ace ace) {
        return false;
    }

    /**
     * Bind service.
     *
     * @param interfaceName the interface name
     */
    private void bindService(String interfaceName) {
        int flowPriority = AclConstants.EGRESS_ACL_DEFAULT_FLOW_PRIORITY;

        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(AclConstants.EGRESS_ACL_TABLE_ID, ++instructionKey));
        BoundServices serviceInfo = AclServiceUtils.getBoundServices(
                String.format("%s.%s.%s", "vpn", "egressacl", interfaceName), AclConstants.EGRESS_ACL_SERVICE_PRIORITY,
                flowPriority, AclServiceUtils.COOKIE_ACL_BASE, instructions);
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                AclConstants.EGRESS_ACL_SERVICE_PRIORITY, ServiceModeIngress.class);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, path, serviceInfo);
    }

    /**
     * Unbind service.
     *
     * @param interfaceName the interface name
     */
    private void unbindService(String interfaceName) {
        InstanceIdentifier<BoundServices> path = AclServiceUtils.buildServiceId(interfaceName,
                AclConstants.EGRESS_ACL_SERVICE_PRIORITY, ServiceModeIngress.class);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, path);
    }

    /**
     * Gets the instructions for dispatcher table resubmit.
     *
     * @return the instructions for dispatcher table resubmit
     */
    private List<InstructionInfo> getInstructionsForDispatcherTableResubmit() {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit,
                new String[]{Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        return instructions;
    }

    /**
     * Program the default anti-spoofing rule and the conntrack rules.
     *
     * @param dpid           the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param attachMac      The vm mac address
     * @param addOrRemove    addorRemove
     */
    private void programFixedSecurityGroup(BigInteger dpid, String dhcpMacAddress,
                                           String attachMac, int addOrRemove) {
        logger.info("programFixedSecurityGroup :  adding default security group rules.");
        egressAclDhcpAllowClientTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
        egressAclDhcpv6AllowClientTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
        egressAclDhcpDropServerTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
        egressAclDhcpv6DropServerTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);

        //if (securityServicesManager.isConntrackEnabled()) {
        programEgressAclFixedConntrackRule(dpid, attachMac, addOrRemove);
        //}
        programArpRule(dpid, attachMac, addOrRemove);
    }

    /**
     * Programs the custom flows.
     *
     * @param port               the interface
     * @param securityGroupsUuid the list of SG uuid to be applied
     * @param dpId               the dpId
     * @param attachMac          the attached mac
     * @param addOrRemove        whether to delete or add flow
     */
    private void programCustomRules(Interface port, List<Uuid> securityGroupsUuid, BigInteger dpId, String attachMac,
                                    int addOrRemove) {
        logger.trace("Applying custom rules DpId {}, vmMacAddress {}", dpId, attachMac);
        for (Uuid sgUuid : securityGroupsUuid) {
            Acl acl = AclServiceUtils.getAcl(dataBroker, sgUuid.getValue());
            AccessListEntries accessListEntries = acl.getAccessListEntries();
            List<Ace> aceList = accessListEntries.getAce();
            for (Ace ace : aceList) {
                SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);

                if (!aceAttr.getDirection().equals(DirectionEgress.class)) {
                    continue;
                }

                Matches matches = ace.getMatches();
                AceType aceType = matches.getAceType();
                Map<String, List<MatchInfoBase>> flowMap = null;
                if (aceType instanceof AceIp) {
                    flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
                }

                if (null == flowMap) {
                    logger.error("Failed to apply ACL {} vmMacAddress {}", ace.getKey(), attachMac);
                    continue;
                }
                //The flow map contains list of flows if port range is selected.
                for (String flowName : flowMap.keySet()) {
                    List<MatchInfoBase> flows = flowMap.get(flowName);
                    flowName = flowName + "Egress" + attachMac;
                    flows.add(new MatchInfo(MatchFieldType.eth_src,
                            new String[]{attachMac}));
                    /*flows.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                        new long[] { AclServiceUtils.TRACKED_NEW_CT_STATE,
                                     AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));*/
                    List<InstructionInfo> instructions = new ArrayList<>();
                    List<ActionInfo> actionsInfos = new ArrayList<>();
                    actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                            new String[]{"1", "0", "0", "255"}, 2));
                    instructions.add(new InstructionInfo(InstructionType.apply_actions,
                            actionsInfos));
                    instructions.add(new InstructionInfo(InstructionType.goto_table,
                            new long[]{AclConstants.EGRESS_ACL_NEXT_TABLE_ID}));
                    syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY,
                            "ACL", 0, 0, AclServiceUtils.COOKIE_ACL_BASE, flows, instructions, addOrRemove);
                }
            }
        }

    }

    /**
     * Anti-spoofing rule to block the Ipv4 DHCP server traffic from the port.
     *
     * @param dpId           the dpId
     * @param dhcpMacAddress the Dhcp mac address
     * @param attachMac      the attached mac address
     * @param addOrRemove    add/remove the flow.
     */
    private void egressAclDhcpDropServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                String attachMac, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpServerPort_IpV4,
                AclServiceUtils.dhcpClientPort_IpV4);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
                new String[]{}));
        String flowName = "Egress_DHCP_Server_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Drop_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Anti-spoofing rule to block the Ipv6 DHCP server traffic from the port.
     *
     * @param dpId           the dpId
     * @param dhcpMacAddress the Dhcp mac address
     * @param attachMac      the attached mac address
     * @param addOrRemove    add/remove the flow.
     */
    private void egressAclDhcpv6DropServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                  String attachMac, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpServerPort_Ipv6,
                AclServiceUtils.dhcpClientPort_IpV6);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
                new String[]{}));
        String flowName = "Egress_DHCP_Server_v4" + "_" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Drop_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param dpidLong               the dpid
     * @param segmentationId         the segmentation id
     * @param dhcpMacAddress         the DHCP server mac address
     * @param attachMac              the mac address of  the port
     * @param write                  is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void egressAclDhcpAllowClientTraffic(BigInteger dpId, String dhcpMacAddress,
                                                 String attachMac, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpClientPort_IpV4,
                AclServiceUtils.dhcpServerPort_IpV4);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                new String[]{"1", "0", "0", "255"}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
                actionsInfos));


        instructions.add(new InstructionInfo(InstructionType.goto_table,
                new long[]{AclConstants.EGRESS_ACL_NEXT_TABLE_ID}));
        String flowName = "Egress_DHCP_Client_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is allowed.
     *
     * @param dpidLong               the dpid
     * @param segmentationId         the segmentation id
     * @param dhcpMacAddress         the DHCP server mac address
     * @param attachMac              the mac address of  the port
     * @param write                  is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void egressAclDhcpv6AllowClientTraffic(BigInteger dpId, String dhcpMacAddress,
                                                   String attachMac, int addOrRemove) {
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpClientPort_IpV6,
                AclServiceUtils.dhcpServerPort_Ipv6);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                new String[]{"1", "0", "0", "255"}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
                actionsInfos));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
                new long[]{AclConstants.EGRESS_ACL_NEXT_TABLE_ID}));
        String flowName = "Egress_DHCP_Client_v4" + "_" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds the rule to send the packet to the netfilter to check whether it is a known packet.
     *
     * @param dpId           the dpId
     * @param attachMac      the attached mac address
     * @param priority       the priority of the flow
     * @param flowId         the flowId
     * @param conntrackState the conntrack state of the packets thats should be send
     * @param conntrackMask  the conntrack mask
     * @param addOrRemove    whether to add or remove the flow
     */
    private void programConntrackRecircRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                            int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[]{NwConstants.ETHTYPE_IPV4}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
                new String[]{"0", "0", "0", Short.toString(AclConstants.EGRESS_ACL_TABLE_ID)}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
                actionsInfos));
        String flowName = "Egress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds  the rule to forward the packets known packets .
     *
     * @param dpId           the dpId
     * @param attachMac      the attached mac address
     * @param priority       the priority of the flow
     * @param flowId         the flowId
     * @param conntrackState the conntrack state of the packets thats should be send
     * @param conntrackMask  the conntrack mask
     * @param addOrRemove    whether to add or remove the flow
     */
    private void programConntrackForwardRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                             int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[]{NwConstants.ETHTYPE_IPV4}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
                new String[]{}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
                new long[]{AclConstants.EGRESS_ACL_NEXT_TABLE_ID}));
        String flowName = "Egress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, priority, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds  the rule to drop the unknown/invalid packets .
     *
     * @param dpId           the dpId
     * @param attachMac      the attached mac address
     * @param priority       the priority of the flow
     * @param flowId         the flowId
     * @param conntrackState the conntrack state of the packets thats should be send
     * @param conntrackMask  the conntrack mask
     * @param addOrRemove    whether to add or remove the flow
     */
    private void programConntrackDropRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                          int conntrackState, int conntrackMask, int addOrRemove) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[]{NwConstants.ETHTYPE_IPV4}));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[]{conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[]{attachMac}));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
                new String[]{}));
        String flowName = "Egress_Fixed_Conntrk_NewDrop_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, priority, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Adds  the rule to allow arp packets.
     *
     * @param dpId        the dpId
     * @param attachMac   the attached mac address
     * @param addOrRemove whether to add or remove the flow
     */
    private void programArpRule(BigInteger dpId, String attachMac, int addOrRemove) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[]{NwConstants.ETHTYPE_ARP}));
        matches.add(new MatchInfo(MatchFieldType.arp_sha,
                new String[]{attachMac}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
                new String[]{}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
                new long[]{AclConstants.EGRESS_ACL_NEXT_TABLE_ID}));
        String flowName = "Egress_ARP_" + dpId + "_" + attachMac;
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Writes/remove the flow to/from the datastore.
     *
     * @param dpId         the dpId
     * @param tableId      the tableId
     * @param flowId       the flowId
     * @param priority     the priority
     * @param flowName     the flow name
     * @param idleTimeOut  the idle timeout
     * @param hardTimeOut  the hard timeout
     * @param cookie       the cookie
     * @param matches      the list of matches to be writted
     * @param instructions the list of instruction to be written.
     * @param addOrRemove  add or remove the entries.
     */
    private void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
                          int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase> matches,
                          List<InstructionInfo> instructions, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId,
                    priority, flowName, idleTimeOut, hardTimeOut, cookie, matches, null);
            logger.trace("Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId,
                    priority, flowName, idleTimeOut, hardTimeOut, cookie, matches, instructions);
            logger.trace("Installing DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.installFlow(flowEntity);
        }
    }

    /**
     * Programs the default connection tracking rules.
     *
     * @param dpid      the dp id
     * @param attachMac the attached mac address
     * @param write     whether to add or remove the flow.
     */
    private void programEgressAclFixedConntrackRule(BigInteger dpid, String attachMac, int write) {
        programConntrackRecircRule(dpid, attachMac, AclServiceUtils.CT_STATE_UNTRACKED_PRIORITY,
                "Untracked", AclServiceUtils.UNTRACKED_CT_STATE, AclServiceUtils.UNTRACKED_CT_STATE_MASK, write);
        programConntrackForwardRule(dpid, attachMac, AclServiceUtils.CT_STATE_TRACKED_EXIST_PRIORITY,
                "Tracked_Established", AclServiceUtils.TRACKED_EST_CT_STATE, AclServiceUtils.TRACKED_CT_STATE_MASK,
                write);
        programConntrackForwardRule(dpid, attachMac, AclServiceUtils.CT_STATE_TRACKED_EXIST_PRIORITY,
                "Tracked_Related", AclServiceUtils.TRACKED_REL_CT_STATE, AclServiceUtils.TRACKED_CT_STATE_MASK, write);
        programConntrackDropRule(dpid, attachMac, AclServiceUtils.CT_STATE_NEW_PRIORITY_DROP,
                "Tracked_New", AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK, write);
        programConntrackDropRule(dpid, attachMac, AclServiceUtils.CT_STATE_NEW_PRIORITY_DROP,
                "Tracked_Invalid", AclServiceUtils.TRACKED_INV_CT_STATE, AclServiceUtils.TRACKED_INV_CT_STATE_MASK,
                write);
        logger.info("programEgressAclFixedConntrackRule :  default connection tracking rule are added.");
    }
}
