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
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.AceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatelessIngressAclServiceImpl extends IngressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatelessIngressAclServiceImpl.class);

    public StatelessIngressAclServiceImpl(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
            IMdsalApiManager mdsalManager) {
        super(dataBroker, interfaceManager, mdsalManager);
    }

    @Override
    protected void programFixedRules(BigInteger dpid, String dhcpMacAddress, List<AllowedAddressPairs> allowedAddresses,
            int lportTag, int addOrRemove) {
        // don't create anti-spoof rules
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
            LOG.error("Failed to apply ACL {} lPortTag {}", ace.getKey(), lportTag);
            return;
        }
        for (Map.Entry<String, List<MatchInfoBase>> flow : flowMap.entrySet()) {
            List<MatchInfoBase> flowMatches = flow.getValue();
            boolean hasTcpDstMatch = AclServiceUtils.containsMatchFieldType(flowMatches, MatchFieldType.tcp_dst);
            if (hasTcpDstMatch || flowMatches.isEmpty()) {
                String flowName = flow.getKey() + "Ingress" + lportTag + ace.getKey().getRuleName();
                flowMatches.add(AclServiceUtils.buildLPortTagMatch(lportTag));
                programAllowSynRules(dpId, flowName, flowMatches, addOrRemove);
            }
        }
    }

    private void programAllowSynRules(BigInteger dpId, String origFlowName,
            List<MatchInfoBase> origFlowMatches, int addOrRemove) {
        List<MatchInfoBase> flowMatches = new ArrayList<MatchInfoBase>();
        flowMatches.addAll(origFlowMatches);
        flowMatches.add(new MatchInfo(MatchFieldType.tcp_flags, new long[] { AclConstants.TCP_FLAG_SYN }));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.goto_table,
                new long[] {NwConstants.INGRESS_ACL_NEXT_TABLE_ID}));

        String flowName = "SYN_ACK_" + origFlowName;
        syncFlow(dpId, NwConstants.INGRESS_ACL_TABLE_ID, flowName, AclConstants.PROTO_MATCH_SYN_ACK_PRIORITY,
                "ACL_SYN_ACK", 0, 0, AclConstants.COOKIE_ACL_BASE, flowMatches, instructions, addOrRemove);
        String oper = getOperAsString(addOrRemove);
        LOG.debug("{} allow syn ack packet flow {}", oper, flowName);
    }
}
