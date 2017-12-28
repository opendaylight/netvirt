/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

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
public class IPV6InternetDefaultRouteProgammer {

    private static final Logger LOG = LoggerFactory.getLogger(IPV6InternetDefaultRouteProgammer.class);
    private final IMdsalApiManager mdsalManager;
    private final NeutronvpnUtils nvpnUtils;

    @Inject
    public IPV6InternetDefaultRouteProgammer(final IMdsalApiManager mdsalManager, final NeutronvpnUtils nvpnUtils) {
        this.mdsalManager = mdsalManager;
        this.nvpnUtils = nvpnUtils;
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

        String flowRef = nvpnUtils.getIPv6FlowRefL3(dpId, NwConstants.L3_FIB_TABLE, routerId);
        int priority = NwConstants.TABLE_MISS_PRIORITY;

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
            priority, "L3 ipv6 internet default route", 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, instructions);

        return flowEntity;
    }

    /**
     * This method installs in the FIB table the default route for IPv6.
     *
     * @param dpnId of the compute node
     * @param bgpVpnId internetVpn id as long
     * @param routerId id of router associated to internet bgpvpn as long
     */
    public void installDefaultRoute(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        mdsalManager.installFlow(flowEntity);
    }

    public void removeDefaultRoute(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        mdsalManager.removeFlow(flowEntity);
    }
}
