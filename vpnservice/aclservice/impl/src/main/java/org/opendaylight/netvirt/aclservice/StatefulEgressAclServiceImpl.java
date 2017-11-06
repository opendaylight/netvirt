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
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceOFFlowBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.PacketHandling;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.actions.packet.handling.Permit;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Provides the stateful implementation for egress (w.r.t VM) ACL service.
 *
 * <p>
 * Note: Table names used are w.r.t switch. Hence, switch ingress is VM egress
 * and vice versa.
 */

public class StatefulEgressAclServiceImpl extends AbstractEgressAclServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StatefulEgressAclServiceImpl.class);

    public StatefulEgressAclServiceImpl(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclDataUtil aclDataUtil,
            AclServiceUtils aclServiceUtils) {
        super(dataBroker, mdsalManager, aclDataUtil, aclServiceUtils);
    }

    @Override
    protected void programSpecificFixedRules(BigInteger dpId, String dhcpMacAddress,
            List<AllowedAddressPairs> allowedAddresses, int lportTag, String portId, Action action, int addOrRemove) {
        programAclPortSpecificFixedRules(dpId, allowedAddresses, lportTag, portId, action, addOrRemove);
    }

    @Override
    protected String syncSpecificAclFlow(BigInteger dpId, int lportTag, int addOrRemove, Ace ace, String portId,
            Map<String, List<MatchInfoBase>> flowMap, String flowName) {
        List<MatchInfoBase> matches = flowMap.get(flowName);
        flowName += "Egress" + lportTag + ace.getKey().getRuleName();
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag, serviceMode));
        matches.add(new NxMatchCtState(AclConstants.TRACKED_NEW_CT_STATE, AclConstants.TRACKED_NEW_CT_STATE_MASK));

        Long elanId = AclServiceUtils.getElanIdFromAclInterface(portId);
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions;
        PacketHandling packetHandling = ace.getActions() != null ? ace.getActions().getPacketHandling() : null;
        if (packetHandling instanceof Permit) {
            actionsInfos.add(new ActionNxConntrack(2, 1, 0, elanId.intValue(), (short) 255));
            instructions = getDispatcherTableResubmitInstructions(actionsInfos);
        } else {
            instructions = AclServiceOFFlowBuilder.getDropInstructionInfo();
        }
        String poolName = AclServiceUtils.getAclPoolName(dpId, NwConstants.INGRESS_ACL_FILTER_TABLE, packetHandling);
        // For flows related remote ACL, unique flow priority is used for
        // each flow to avoid overlapping flows
        int priority = getAclFlowPriority(poolName, flowName, addOrRemove);

        syncFlow(dpId, getAclRuleBasedFilterTable(), flowName, priority, "ACL", 0, 0, AclConstants.COOKIE_ACL_BASE,
                matches, instructions, addOrRemove);
        return flowName;
    }
}
