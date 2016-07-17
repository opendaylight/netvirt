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
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatelessIngressAclServiceImpl extends IngressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatelessIngressAclServiceImpl.class);

    public StatelessIngressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
            IMdsalApiManager mdsalManager) {
        super(dataBroker, interfaceManager, mdsalManager);
    }

    /**
     * Program the default anti-spoofing rule and the conntrack rules.
     *
     * @param dpid the dpid
     * @param dhcpMacAddress the dhcp mac address.
     * @param attachMac The vm mac address
     * @param addOrRemove add or remove the flow
     */
    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress,
                                        String attachMac, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");
        ingressAclDhcpAllowServerTraffic(dpid, dhcpMacAddress, attachMac,
            addOrRemove, AclConstants.PROTO_PREFIX_MATCH_PRIORITY);
        ingressAclDhcpv6AllowServerTraffic(dpid, dhcpMacAddress, attachMac,
            addOrRemove, AclConstants.PROTO_PREFIX_MATCH_PRIORITY);

        programArpRule(dpid,attachMac, addOrRemove);
    }

    @Override
    protected void appendExtendingMatches(List<MatchInfoBase> flows) {
        // do nothing
    }

    @Override
    protected void appendExtendingRules(BigInteger dpId, String flowName, List<MatchInfoBase> flowMatches,
            int addOrRemove) {
        boolean hasTcpSrcMatch = AclServiceUtils.containsMatchFieldType(flowMatches,
                MatchFieldType.tcp_src);
        if (hasTcpSrcMatch) {
            programDenyOutgoingSynRules(dpId, flowName, flowMatches, addOrRemove);
        }
    }

    private void programDenyOutgoingSynRules(BigInteger dpId, String origFlowName,
            List<MatchInfoBase> origFlowMatches, int addOrRemove) {
        List<MatchInfoBase> flowMatches = new ArrayList<MatchInfoBase>();
        flowMatches.addAll(origFlowMatches);
        MatchInfoBase ethDst = AclServiceUtils.popMatchInfoByType(flowMatches, MatchFieldType.eth_dst);
        if (ethDst != null) {
            flowMatches.add(new MatchInfo(MatchFieldType.eth_src, ((MatchInfo) ethDst).getStringMatchValues()));
        }
        MatchInfoBase tcpDst = AclServiceUtils.popMatchInfoByType(flowMatches, MatchFieldType.tcp_src);
        if (tcpDst != null) {
            flowMatches.add(new MatchInfo(MatchFieldType.tcp_dst, ((MatchInfo) tcpDst).getMatchValues()));
        }
        flowMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { 0x002 }));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        String synFlowName = "SYN_" + origFlowName;
        syncFlow(dpId, AclConstants.EGRESS_ACL_TABLE_ID, synFlowName, AclConstants.PROTO_MATCH_SYN_PRIORITY, "ACL", 0,
                0, AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
    }
}
