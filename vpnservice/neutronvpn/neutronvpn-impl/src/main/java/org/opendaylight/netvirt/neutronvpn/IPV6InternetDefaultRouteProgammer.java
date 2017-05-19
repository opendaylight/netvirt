/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    @Inject
    public IPV6InternetDefaultRouteProgammer(final IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    private FlowEntity buildIPv6FallbacktoExternalVpn(BigInteger dpId, long bgpVpnId, long routerId) {
        InetAddress defaultIP = null;
        try {
            defaultIP = java.net.Inet6Address.getByName("::/0");
        } catch (UnknownHostException e) {
            LOG.error("buildIPv6FallbacktoExternalVpn: Failed to build FIB Table Flow for default route to IPV6", e);
            return null;
        }
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);

        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(bgpVpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();

        instructions.add(new InstructionGotoTable(NwConstants.EXTERNAL_TUNNEL_TABLE));

        String flowRef = getIPv6FlowRefL3(dpId, NwConstants.L3_FIB_TABLE, defaultIP, routerId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, flowRef/* "L3 ipv6 internet default route",*/, 0, 0,
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
        LOG.trace("installDefaultRoute: flowEntity: {} ", flowEntity);
        mdsalManager.installFlow(flowEntity);
    }

    public void removeDefaultRoute(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        LOG.trace("removeDefaultRoute: flowEntity: {} ", flowEntity);
        mdsalManager.removeFlow(flowEntity);
    }

    public String getIPv6FlowRefL3(BigInteger dpnId, short tableId, InetAddress destPrefix, long vpnId) {
        return "L3."/*"L3.IPv6"*/ + dpnId.toString() + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + destPrefix.getHostAddress() + NwConstants.FLOWID_SEPARATOR + vpnId;
    }
}
