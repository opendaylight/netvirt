/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IPv6DefaultRouteProgrammer {

    private static final Logger LOG = LoggerFactory.getLogger(IPv6DefaultRouteProgrammer.class);
    private final IMdsalApiManager mdsalManager;
    private static IPv6DefaultRouteProgrammer thisInstance;

    @Inject
    public IPv6DefaultRouteProgrammer(final IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
        thisInstance = this;
    }

    private FlowEntity buildIPv6FallbacktoExternalVpn(BigInteger dpId, long bgpVpnId, long routerId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);

        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        int instructionKey = 0;

        instructions.add((InstructionInfo) new InstructionGotoTable(NwConstants.L3_FIB_TABLE)
            .buildInstruction(++instructionKey));
        instructions.add((InstructionInfo)MDSALUtil.buildAndGetWriteMetadaInstruction(
            MetaDataUtil.getVpnIdMetadata(bgpVpnId), MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));

        String flowRef = NatUtil.getIPv6FlowRefL3(dpId, NwConstants.L3_FIB_TABLE, routerId);
        int priority = 0; // set to 0 to be least priority, no DEFAULT_FIB_FLOW_PRIORITY

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
            priority, "L3 FIB resubmit table", 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, instructions);

        return flowEntity;
    }

    /**
     * This method installs in the FIB table the default route for IPv6.
     *
     * @param dpnId of router
     * @param bgpVpnId vpn id as long
     * @param routerId router id as long
     */
    public void installDefaultRouteIpv6InDPN(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        mdsalManager.installFlow(flowEntity);
    }

    public void removeDefaultRouteIpv6InDPN(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        mdsalManager.removeFlow(flowEntity);
    }

    public static IPv6DefaultRouteProgrammer getInstance(final IMdsalApiManager mdsalManager) {
        if (thisInstance == null) {
            thisInstance = new  IPv6DefaultRouteProgrammer(mdsalManager);
        }
        return thisInstance;
    }
}
