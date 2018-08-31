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

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldMeta;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IPV6InternetDefaultRouteProgrammer {

    private static final Logger LOG = LoggerFactory.getLogger(IPV6InternetDefaultRouteProgrammer.class);
    private final IMdsalApiManager mdsalManager;

    @Inject
    public IPV6InternetDefaultRouteProgrammer(final IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    private FlowEntity buildIPv6FallbacktoExternalVpn(BigInteger dpId, long bgpVpnId, long routerId, boolean add) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);

        //add match for router vpnId
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        if (add) {
            ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil.getVpnIdMetadata(bgpVpnId));
            listActionInfo.add(actionSetFieldMeta);
            listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
            instructionInfo.add(new InstructionApplyActions(listActionInfo));
        }
        String defaultIPv6 = "0:0:0:0:0:0:0:0";
        String flowRef = getIPv6FlowRefL3(dpId, NwConstants.L3_FIB_TABLE, defaultIPv6, routerId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, flowRef/* "L3 ipv6 internet default route",*/, 0, 0,
            NwConstants.COOKIE_VM_FIB_TABLE, matches, instructionInfo);

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
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId, true);
        LOG.trace("installDefaultRoute: flowEntity: {} ", flowEntity);
        mdsalManager.installFlow(flowEntity);
    }

    public void removeDefaultRoute(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId, false);
        LOG.trace("removeDefaultRoute: flowEntity: {} ", flowEntity);
        mdsalManager.removeFlow(flowEntity);
    }

    public String getIPv6FlowRefL3(BigInteger dpnId, short tableId, String destPrefix, long vpnId) {
        return "L3." + dpnId.toString() + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + destPrefix + NwConstants.FLOWID_SEPARATOR + vpnId;
    }
}
