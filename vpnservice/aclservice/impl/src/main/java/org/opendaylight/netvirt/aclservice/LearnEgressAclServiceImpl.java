/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
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
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LearnEgressAclServiceImpl extends AbstractEgressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(LearnEgressAclServiceImpl.class);

    /**
     * Initialize the member variables.
     *
     * @param dataBroker
     *            the data broker instance.
     * @param mdsalManager
     *            the mdsal manager instance.
     * @param aclDataUtil
     *            the acl data util.
     * @param aclServiceUtils
     *            the acl service util.
     */
    public LearnEgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils) {
        super(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
    }

    @Override
    protected void programSpecificFixedRules(BigInteger dpid, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove) {
    }

    @Override
    protected String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, String aclName, Ace ace,
            String portId, Map<String, List<MatchInfoBase>> flowMap, String flowName) {
        List<MatchInfoBase> flowMatches = flowMap.get(flowName);
        flowMatches.add(AclServiceUtils.buildLPortTagMatch(lportTag));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        addLearnActions(flowMatches, actionsInfos);

        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit,
                new String[] {Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        String flowNameAdded = flowName + "Egress" + lportTag + ace.getKey().getRuleName();
        syncFlow(dpId, NwConstants.INGRESS_LEARN2_TABLE, flowNameAdded, AclConstants.PROTO_MATCH_PRIORITY, "ACL", 0, 0,
                AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
        return flowName;
    }

    /*
     * learn header
     *
     * 0 1 2 3 4 5 6 7 idleTO hardTO prio cook flags table finidle finhrad
     *
     * learn flowmod learnFlowModType srcField dstField FlowModNumBits 0 1 2 3
     */
    private void addLearnActions(List<MatchInfoBase> flows, List<ActionInfo> actionsInfos) {
        boolean isTcp = AclServiceUtils.containsMatchFieldType(flows, NxMatchFieldType.nx_tcp_src_with_mask)
                || AclServiceUtils.containsMatchFieldType(flows, NxMatchFieldType.nx_tcp_dst_with_mask);
        boolean isUdp = AclServiceUtils.containsMatchFieldType(flows, NxMatchFieldType.nx_udp_src_with_mask)
                || AclServiceUtils.containsMatchFieldType(flows, NxMatchFieldType.nx_udp_dst_with_mask);
        if (isTcp) {
            addTcpLearnActions(actionsInfos);
        } else if (isUdp) {
            addUdpLearnActions(actionsInfos);
        } else {
            addOtherProtocolsLearnActions(actionsInfos);
        }
    }

    private void addOtherProtocolsLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[5][];

        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_VALUE.name(),
                Integer.toString(NwConstants.ETHTYPE_IPV4),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLen() };
        flowMod[3] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[4] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG5.getHexType(), "8" };

        String[] header = new String[] {
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupDefaultIdleTimeout()),
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupDefaultHardTimeout()),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(NwConstants.EGRESS_LEARN_TABLE), "0", "0"};
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }

    private void addTcpLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[7][];

        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_VALUE.name(),
                Integer.toString(NwConstants.ETHTYPE_IPV4),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_VALUE.name(),
                Integer.toString(NwConstants.IP_PROT_TCP),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[3] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getFlowModHeaderLen() };
        flowMod[4] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLen() };
        flowMod[5] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getFlowModHeaderLen() };
        flowMod[6] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG5.getHexType(), "8" };

        String[] header = new String[] {
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupTcpIdleTimeout()),
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupTcpHardTimeout()),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(NwConstants.EGRESS_LEARN_TABLE),
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupTcpFinIdleTimeout()),
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupTcpFinHardTimeout())};
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }

    private void addUdpLearnActions(List<ActionInfo> actionsInfos) {
        String[][] flowMod = new String[7][];

        flowMod[0] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_VALUE.name(),
                Integer.toString(NwConstants.ETHTYPE_IPV4),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLen() };
        flowMod[1] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_VALUE.name(),
                Integer.toString(NwConstants.IP_PROT_UDP),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLen() };
        flowMod[2] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLen() };
        flowMod[3] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getFlowModHeaderLen() };
        flowMod[4] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLen() };
        flowMod[5] = new String[] { NwConstants.LearnFlowModsType.MATCH_FROM_FIELD.name(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_DST.getHexType(),
                NwConstants.NxmOfFieldType.NXM_OF_UDP_DST.getFlowModHeaderLen() };
        flowMod[6] = new String[] {
                NwConstants.LearnFlowModsType.COPY_FROM_VALUE.name(), AclConstants.LEARN_MATCH_REG_VALUE,
                NwConstants.NxmOfFieldType.NXM_NX_REG5.getHexType(), "8" };

        String[] header = new String[] {
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupUdpIdleTimeout()),
                String.valueOf(this.aclServiceUtils.getConfig().getSecurityGroupUdpHardTimeout()),
                AclConstants.PROTO_MATCH_PRIORITY.toString(),
                AclConstants.COOKIE_ACL_BASE.toString(), "0",
                Short.toString(NwConstants.EGRESS_LEARN_TABLE), "0", "0" };
        actionsInfos.add(new ActionInfo(ActionType.learn, header, flowMod));
    }
}
