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
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EgressAclServiceImpl implements AclServiceListener {

    private static final Logger logger = LoggerFactory.getLogger(EgressAclServiceImpl.class);

    private IMdsalApiManager mdsalUtil;
    short tableIdInstall = 22;
    short tableIdNext = 23;
    private OdlInterfaceRpcService interfaceManager;
    private DataBroker dataBroker;

    /**
     * Intilaze the member variables
     * @param dataBroker the data broker instance.
     * @param interfaceManager the interface manager instance.
     * @param mdsalUtil the mdsal util instance.
     */
    public EgressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
                                IMdsalApiManager mdsalUtil) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.mdsalUtil = mdsalUtil;
    }

    @Override
    public boolean applyAcl(Interface port) {

        if (!AclServiceUtils.isPortSecurityEnabled(port, dataBroker)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
            interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        String attachMac = interfaceState.getPhysAddress().getValue();
        programFixedSecurityGroup(dpId, "", attachMac, NwConstants.ADD_FLOW);
        return true;
    }

    @Override
    public boolean updateAcl(Interface port) {
        return false;
    }

    @Override
    public boolean removeAcl(Interface port) {
        if (!AclServiceUtils.isPortSecurityEnabled(port, dataBroker)) {
            return false;
        }
        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
            interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, port.getName());
        String attachMac = interfaceState.getPhysAddress().getValue();
        programFixedSecurityGroup(dpId, "", attachMac, NwConstants.DEL_FLOW);
        return true;
    }

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
        programArpRule(dpid,attachMac, addOrRemove);
    }

    private void egressAclDhcpDropServerTraffic(BigInteger dpId, String dhcpMacAddress,
            String attachMac, int addOrRemove) {
        String flowName = "Egress_DHCP_Server_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Drop_";
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpServerPort_IpV4,
            AclServiceUtils.dhcpClientPort_IpV4);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] { AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
            new String[] {}));

        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    private void egressAclDhcpv6DropServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                  String attachMac, int addOrRemove) {
        String flowName = "Egress_DHCP_Server_v4" + "_" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Drop_";
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpServerPort_Ipv6,
            AclServiceUtils.dhcpClientPort_IpV6);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] { AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
            new String[] {}));

        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param write is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void egressAclDhcpAllowClientTraffic(BigInteger dpId, String dhcpMacAddress,
                                                 String attachMac, int addOrRemove) {
        String flowName = "Egress_DHCP_Client_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpClientPort_IpV4,
            AclServiceUtils.dhcpServerPort_IpV4);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] { AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"1", "0", "0", "255"}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
            actionsInfos));


        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { tableIdNext }));

        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCPv6 server traffic from the specified mac is allowed.
     *
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param write is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void egressAclDhcpv6AllowClientTraffic(BigInteger dpId, String dhcpMacAddress,
                                                   String attachMac, int addOrRemove) {
        String flowName = "Egress_DHCP_Client_v4" + "_" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpClientPort_IpV6,
            AclServiceUtils.dhcpServerPort_Ipv6);
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] { AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK}));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"1", "0", "0", "255"}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
            actionsInfos));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { tableIdNext }));

        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    private void programConntrackRecircRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                             int conntrackState, int conntrackMask, int addOrRemove) {
        String flowName = "Egress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add((MatchInfoBase)new MatchInfo(MatchFieldType.eth_type,
            new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add((MatchInfoBase)new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] {conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.nx_conntrack,
            new String[] {"0", "0", "0", Short.toString(tableIdInstall)}, 2));
        instructions.add(new InstructionInfo(InstructionType.apply_actions,
            actionsInfos));
        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    private void programConntrackForwardRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                             int conntrackState, int conntrackMask, int addOrRemove) {
        String flowName = "Egress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add((MatchInfoBase)new MatchInfo(MatchFieldType.eth_type,
            new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add((MatchInfoBase)new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] {conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
            new String[] {}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { tableIdNext }));
        syncFlow(dpId, tableIdInstall, flowName, priority, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    private void programConntrackDropRule(BigInteger dpId, String attachMac, Integer priority, String flowId,
                                          int conntrackState, int conntrackMask, int addOrRemove) {
        String flowName = "Egress_Fixed_Conntrk_NewDrop_" + dpId + "_" + attachMac + "_" + flowId;
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add((MatchInfoBase)new MatchInfo(MatchFieldType.eth_type,
            new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add((MatchInfoBase)new NxMatchInfo(NxMatchFieldType.ct_state,
            new long[] { conntrackState, conntrackMask}));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
            new String[] { attachMac }));
        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.drop_action,
            new String[] {}));

        syncFlow(dpId, tableIdInstall, flowName, priority, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    private void programArpRule(BigInteger dpId, String attachMac, int addOrRemove) {
        String flowName = "Egress_ARP_" + dpId + "_" + attachMac ;
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
            new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new MatchInfo(MatchFieldType.arp_tpa,
            new String[] { attachMac }));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
                new String[] {}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { tableIdNext }));
        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    private void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
                          int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase>  matches,
                          List<InstructionInfo> instructions, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW) {
            MDSALUtil.buildFlowEntity(dpId, tableIdInstall,
                flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclServiceUtils.COOKIE_ACL_BASE, matches, null);
            logger.trace("Removing Acl Flow DpId {}, vmMacAddress {}", dpId, flowId);
            // TODO Need to be done as a part of genius integration
            //mdsalUtil.removeFlow(flowEntity);
        } else {
            MDSALUtil.buildFlowEntity(dpId, tableId,
                flowId ,priority, flowName, 0, 0, cookie, matches, instructions);
            logger.trace("Installing  DpId {}, flowId {}", dpId, flowId);
            // TODO Need to be done as a part of genius integration
            //mdsalUtil.installFlow(flowEntity);
        }
    }

    private void programEgressAclFixedConntrackRule(BigInteger dpid, String attachMac, int write) {
        try {
            programConntrackRecircRule(dpid, attachMac,AclServiceUtils.CT_STATE_UNTRACKED_PRIORITY,
                "Untracked",AclServiceUtils.UNTRACKED_CT_STATE,AclServiceUtils.UNTRACKED_CT_STATE_MASK, write );
            programConntrackForwardRule(dpid, attachMac, AclServiceUtils.CT_STATE_TRACKED_EXIST_PRIORITY,
                "Tracked_Established", AclServiceUtils.TRACKED_EST_CT_STATE, AclServiceUtils.TRACKED_CT_STATE_MASK,
                write );
            programConntrackForwardRule(dpid, attachMac, AclServiceUtils.CT_STATE_TRACKED_EXIST_PRIORITY,
                "Tracked_Related", AclServiceUtils.TRACKED_REL_CT_STATE, AclServiceUtils.TRACKED_CT_STATE_MASK, write );
            programConntrackDropRule(dpid, attachMac, AclServiceUtils.CT_STATE_NEW_PRIORITY_DROP,
                "Tracked_New", AclServiceUtils.TRACKED_NEW_CT_STATE, AclServiceUtils.TRACKED_NEW_CT_STATE_MASK, write );
            programConntrackForwardRule(dpid, attachMac, AclServiceUtils.CT_STATE_NEW_PRIORITY_DROP,
                "Tracked_Invalid",AclServiceUtils.TRACKED_INV_CT_STATE, AclServiceUtils.TRACKED_INV_CT_STATE_MASK,
                write );
            logger.info("programEgressAclFixedConntrackRule :  default connection tracking rule are added.");
        } catch (Exception e) {
            logger.error("Failed to add default conntrack rules : " , e);
        }
    }

}
