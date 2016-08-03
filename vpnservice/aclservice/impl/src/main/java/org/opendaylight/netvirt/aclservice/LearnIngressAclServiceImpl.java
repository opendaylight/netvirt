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
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LearnIngressAclServiceImpl extends IngressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(LearnIngressAclServiceImpl.class);

    public LearnIngressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        super(dataBroker, mdsalManager);
    }

    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, Action action, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");

        ingressAclDhcpAllowServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove,
                AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
        ingressAclDhcpv6AllowServerTraffic(dpid, dhcpMacAddress, lportTag, addOrRemove,
                AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
        programArpRule(dpid, allowedAddresses, addOrRemove);
    }

    @Override
    protected void programAceRule(BigInteger dpId, int lportTag, int addOrRemove, Ace ace) {
        SecurityRuleAttr aceAttr = AclServiceUtils.getAccesssListAttributes(ace);
        if (!aceAttr.getDirection().equals(DirectionIngress.class)) {
            return;
        }
        Matches matches = ace.getMatches();
        AceType aceType = matches.getAceType();
        Map<String, List<MatchInfoBase>> flowMap = null;
        if (aceType instanceof AceIp) {
            flowMap = AclServiceOFFlowBuilder.programIpFlow(matches);
        }
        if (null == flowMap) {
            LOG.error("Failed to apply ACL {} lportTag {}", ace.getKey(), lportTag);
            return;
        }

        // The flow map contains list of flows if port range is selected.
        for (Map.Entry<String, List<MatchInfoBase>> flow : flowMap.entrySet()) {
            List<MatchInfoBase> flowMatches = flow.getValue();
            flowMatches.add(AclServiceUtils.buildLPortTagMatch(lportTag));
            List<ActionInfo> actionsInfos = new ArrayList<>();
            addLearnActions(flowMatches, actionsInfos);

            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

            String flowName = flow.getKey() + "Ingress" + lportTag + ace.getKey().getRuleName();
            syncFlow(dpId, AclConstants.INGRESS_LEARN_TABLE, flowName, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                    AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
        }
    }

    /*
     * learn header
     *
     * 0 1 2 3 4 5 6 7 idleTO hardTO prio cook flags table finidle finhrad
     *
     * learn flowmod learnFlowModType srcField dstField FlowModNumBits 0 1 2 3
     */
    private void addLearnActions(List<MatchInfoBase> flows, List<ActionInfo> actionsInfos) {
        boolean isTcp = AclServiceUtils.containsMatchFieldType(flows, NxMatchFieldType.nx_tcp_dst_with_mask);
        boolean isUdp = AclServiceUtils.containsMatchFieldType(flows, NxMatchFieldType.nx_udp_dst_with_mask);
        if (isTcp) {
            addTcpLearnActions(actionsInfos);
        } else if (isUdp) {
            addUdpLearnActions(actionsInfos);
        } else if (actionsInfos.isEmpty()) {
            addAllowAllLearnActions(actionsInfos);
        } else {
            addOtherProtocolsLearnActions(actionsInfos);
        }
    }

    private void addOtherProtocolsLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[4][];

        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[3] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG0.getHexType(), "8" };

        String[] header = new String[] {
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_IDLE_TO_KEY, "60"),
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_HARD_TO_KEY, "60"),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(AclConstants.INGRESS_LEARN_TABLE), "0", "0" };
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }

    private void addAllowAllLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[4][];

        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[3] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG0.getHexType(), "8" };

        String[] header = new String[] {
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_IDLE_TO_KEY, "60"),
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_HARD_TO_KEY, "60"),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(AclConstants.INGRESS_LEARN_TABLE), "0", "0" };
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }

    private void addTcpLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[5][];

        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getFlowModHeaderLen() };
        flowMod[3] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[4] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG0.getHexType(), "8" };

        String[] header = new String[] {
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_IDLE_TO_KEY, "3600"),
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_HARD_TO_KEY, "3600"),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(AclConstants.INGRESS_LEARN_TABLE), "60", "60" };
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }

    private void addUdpLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[5][];
        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_SRC.getFlowModHeaderLen() };
        flowMod[3] = new String[] { NwConstants.LearnFlowModsType.ADD_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[4] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG0.getHexType(), "8" };

        String[] header = new String[] {
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_IDLE_TO_KEY, "60"),
                AclConstants.getGlobalConf(AclConstants.SECURITY_GROUP_UDP_HARD_TO_KEY, "60"),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(AclConstants.INGRESS_LEARN_TABLE), "0", "0" };
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }
}
