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
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngressAclServiceImpl implements AclServiceListener {

    private static final Logger logger = LoggerFactory.getLogger(IngressAclServiceImpl.class);

    private IMdsalApiManager mdsalUtil;
    short tableIdInstall = 20;
    short tableIdNext = 21;
    private OdlInterfaceRpcService interfaceManager;
    private DataBroker dataBroker;

    /**
     * Intilaze the member variables.
     * @param dataBroker the data broker instance.
     * @param interfaceManager the interface manager instance.
     * @param mdsalUtil the mdsal util instance.
     */
    public IngressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
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
        // TODO Auto-generated method stub
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

    /**
     * Program the default anti-spoofing rule and the conntrack rules.
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param attachMac The vm mac address
     * @param addOrRemove add or remove the flow
     */
    private void programFixedSecurityGroup(BigInteger dpid, String dhcpMacAddress,
                                        String attachMac, int addOrRemove) {
        logger.info("programFixedSecurityGroup :  adding default security group rules.");
        ingressAclDhcpAllowServerTraffic(dpid, dhcpMacAddress, attachMac,
            addOrRemove, AclServiceUtils.PROTO_PREFIX_MATCH_PRIORITY);
        ingressAclDhcpv6AllowServerTraffic(dpid, dhcpMacAddress, attachMac,
            addOrRemove, AclServiceUtils.PROTO_PREFIX_MATCH_PRIORITY);

        //if (securityServicesManager.isConntrackEnabled()) {
        programIngressAclFixedConntrackRule(dpid, attachMac, addOrRemove);
        //}
        programArpRule(dpid,attachMac, addOrRemove);
    }

    /**
     * Add rule to ensure only DHCP server traffic from the specified mac is allowed.
     *
     * @param dpidLong the dpid
     * @param segmentationId the segmentation id
     * @param dhcpMacAddress the DHCP server mac address
     * @param attachMac the mac address of  the port
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpAllowServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                  String attachMac, int addOrRemove, int protoPortMatchPriority) {
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpServerPort_IpV4,
            AclServiceUtils.dhcpClientPort_IpV4);
        matches.add(new MatchInfo(MatchFieldType.eth_dst,
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
        String flowName = "Ingress_DHCP_Server_v4" + dpId + "_" + attachMac + "_" + dhcpMacAddress + "_Permit_";
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
     * @param addOrRemove is write or delete
     * @param protoPortMatchPriority the priority
     */
    private void ingressAclDhcpv6AllowServerTraffic(BigInteger dpId, String dhcpMacAddress,
                                                    String attachMac, int addOrRemove, Integer protoPortMatchPriority) {
        List<MatchInfoBase> matches = AclServiceUtils.programDhcpMatches(AclServiceUtils.dhcpServerPort_Ipv6,
            AclServiceUtils.dhcpClientPort_IpV6);
        matches.add(new MatchInfo(MatchFieldType.eth_dst, new String[] { attachMac }));
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
        String flowName = "Ingress_DHCP_Server_v6" + "_" + dpId + "_" + attachMac + "_" + "_"
                + dhcpMacAddress + "_Permit_";
        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
        String flowName = "Ingress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
        String flowName = "Ingress_Fixed_Conntrk_Untrk_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, tableIdInstall, flowName, priority, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
        String flowName = "Ingress_Fixed_Conntrk_NewDrop_" + dpId + "_" + attachMac + "_" + flowId;
        syncFlow(dpId, tableIdInstall, flowName, priority, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
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
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new MatchInfo(MatchFieldType.arp_tpa,
                    new String[] { attachMac }));

        List<InstructionInfo> instructions = new ArrayList<>();

        List<ActionInfo> actionsInfos = new ArrayList<>();

        actionsInfos.add(new ActionInfo(ActionType.goto_table,
                new String[] {}));

        instructions.add(new InstructionInfo(InstructionType.goto_table,
            new long[] { tableIdNext }));
        String flowName = "Ingress_ARP_" + dpId + "_" + attachMac;
        syncFlow(dpId, tableIdInstall, flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
            AclServiceUtils.COOKIE_ACL_BASE, matches, instructions, addOrRemove);
    }

    /**
     * Writes/remove the flow to/from the datastore.
     * @param dpId the dpId
     * @param tableId the tableId
     * @param flowId the flowId
     * @param priority the priority
     * @param flowName the flow name
     * @param idleTimeOut the idle timeout
     * @param hardTimeOut the hard timeout
     * @param cookie the cookie
     * @param matches the list of matches to be written
     * @param instructions the list of instruction to be written.
     * @param addOrRemove add or remove the entries.
     */
    private void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
            int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase>  matches,
            List<InstructionInfo> instructions, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW) {
            MDSALUtil.buildFlowEntity(dpId, tableIdInstall,
                flowName, AclServiceUtils.PROTO_MATCH_PRIORITY, "ACL", idleTimeOut, hardTimeOut,
                AclServiceUtils.COOKIE_ACL_BASE, matches, null);
            logger.trace("Removing Acl Flow DpId {}, vmMacAddress {}", dpId, flowId);
            // TODO Need to be done as a part of genius integration
            // mdsalUtil.removeFlow(flowEntity);
        } else {
            MDSALUtil.buildFlowEntity(dpId, tableId,
                flowId ,priority, flowName, idleTimeOut, hardTimeOut, cookie, matches, instructions);
            logger.trace("Installing  DpId {}, flowId {}", dpId, flowId);
            // TODO Need to be done as a part of genius integration
            //mdsalUtil.installFlow(flowEntity);
        }
    }

    /**
     * Programs the default connection tracking rules.
     * @param dpid the dp id
     * @param attachMac the attached mac address
     * @param write whether to add or remove the flow.
     */
    private void programIngressAclFixedConntrackRule(BigInteger dpid, String attachMac, int write) {
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
            logger.info("programIngressAclFixedConntrackRule :  default connection tracking rule are added.");
        } catch (Exception e) {
            logger.error("Failed to add default conntrack rules : " , e);
        }
    }
}
