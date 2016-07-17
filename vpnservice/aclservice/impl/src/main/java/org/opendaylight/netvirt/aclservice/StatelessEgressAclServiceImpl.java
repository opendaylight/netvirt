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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatelessEgressAclServiceImpl extends EgressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatelessEgressAclServiceImpl.class);

    public StatelessEgressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
            IMdsalApiManager mdsalManager) {
        super(dataBroker, interfaceManager, mdsalManager);
    }

    @Override
    public boolean updateAcl(Interface portBefore, Interface portAfter) {
        LOG.info("Updating egress acl. portBefore {}, port after {}", portBefore, portAfter);
        return super.updateAcl(portBefore, portAfter);
    }

//    @Override
//    public boolean applyAcl(Interface port) {
//        LOG.info("Applying egress acl. port {}", port);
//        if (!AclServiceUtils.isPortSecurityEnabled(port)) {
//            return false;
//        }
//        BigInteger dpId = AclServiceUtils.getDpnForInterface(interfaceManager, port.getName());
//        List<Uuid> securityGroupsUuid = AclServiceUtils.getInterfaceAcls(port);
//        return updateCustomRules(port, dpId, securityGroupsUuid, NwConstants.ADD_FLOW);
//    }

    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress, String attachMac, int addOrRemove) {
        LOG.info("programFixedRules :  adding default rules.");
        egressAclDhcpAllowClientTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
        egressAclDhcpv6AllowClientTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
        egressAclDhcpDropServerTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);
        egressAclDhcpv6DropServerTraffic(dpid, dhcpMacAddress, attachMac, addOrRemove);

        programArpRule(dpid, attachMac, addOrRemove);
    }

    @Override
    protected void appendExtendingInstructions(List<InstructionInfo> instructions) {
        // override appending contrak instructions
    }

    protected void appendExtendingMatches(List<MatchInfoBase> flows) {
        // override appending contrak matches
    }

    @Override
    protected void appendExtendingRules(BigInteger dpId, String flowName, List<MatchInfoBase> flowMatches,
            int addOrRemove) {
        boolean hasTcpDstMatch = AclServiceUtils.containsMatchFieldType(flowMatches,
                MatchFieldType.tcp_dst);
        if (hasTcpDstMatch) {
            programDenyIncomingSynRules(dpId, flowName, flowMatches, addOrRemove);
        }
    }

    private void programDenyIncomingSynRules(BigInteger dpId, String origFlowName,
            List<MatchInfoBase> origFlowMatches, int addFlow) {
        List<MatchInfoBase> flowMatches = new ArrayList<MatchInfoBase>();
        flowMatches.addAll(origFlowMatches);
        MatchInfoBase ethSrc = AclServiceUtils.popMatchInfoByType(flowMatches, MatchFieldType.eth_src);
        if (ethSrc != null) {
            flowMatches.add(new MatchInfo(MatchFieldType.eth_dst, ((MatchInfo)ethSrc).getStringMatchValues()));
        }
        MatchInfoBase tcpSrc = AclServiceUtils.popMatchInfoByType(flowMatches, MatchFieldType.tcp_dst);
        if (tcpSrc != null) {
            flowMatches.add(new MatchInfo(MatchFieldType.tcp_src, ((MatchInfo)tcpSrc).getMatchValues()));
        }
        flowMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { 0x002 }));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.drop_action, new String[] {}));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));

        String flowName = "SYN_" + origFlowName;
        syncFlow(dpId, AclConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_SYN_PRIORITY,
                "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addFlow);
    }

}
