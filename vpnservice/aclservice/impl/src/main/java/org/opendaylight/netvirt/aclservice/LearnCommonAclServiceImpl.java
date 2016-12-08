/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;

public class LearnCommonAclServiceImpl {
    protected static List<ActionLearn.FlowMod> getOtherProtocolsLearnActionMatches() {
        List<ActionLearn.FlowMod> flowMods = new ArrayList<>();

        flowMods.add(new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV4,
            NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()));
        flowMods.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLenInt()));
        flowMods.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLenInt()));
        flowMods.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()));
        flowMods.add(new ActionLearn.CopyFromValue(
            AclConstants.LEARN_MATCH_REG_VALUE,
            NwConstants.NxmOfFieldType.NXM_NX_REG5.getType(), 8));

        return flowMods;
    }

    protected static List<ActionLearn.FlowMod> getTcpLearnActionMatches() {
        List<ActionLearn.FlowMod> learnActionMatches = new ArrayList<>();

        learnActionMatches.add(new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV4,
            NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromValue(NwConstants.IP_PROT_TCP,
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_TCP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_TCP_SRC.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.CopyFromValue(
            AclConstants.LEARN_MATCH_REG_VALUE,
            NwConstants.NxmOfFieldType.NXM_NX_REG5.getType(), 8));

        return learnActionMatches;
    }

    protected static List<ActionLearn.FlowMod> getUdpLearnActionMatches() {
        List<ActionLearn.FlowMod> learnActionMatches = new ArrayList<>();

        learnActionMatches.add(new ActionLearn.MatchFromValue(NwConstants.ETHTYPE_IPV4,
            NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromValue(NwConstants.IP_PROT_UDP,
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_UDP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_IP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.MatchFromField(
            NwConstants.NxmOfFieldType.NXM_OF_UDP_DST.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getType(),
            NwConstants.NxmOfFieldType.NXM_OF_UDP_SRC.getFlowModHeaderLenInt()));
        learnActionMatches.add(new ActionLearn.CopyFromValue(
            AclConstants.LEARN_MATCH_REG_VALUE,
            NwConstants.NxmOfFieldType.NXM_NX_REG5.getType(), 8));

        return learnActionMatches;
    }
}
