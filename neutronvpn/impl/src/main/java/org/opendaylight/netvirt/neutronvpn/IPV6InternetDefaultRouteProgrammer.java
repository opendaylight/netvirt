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

import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
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
public class IPV6InternetDefaultRouteProgrammer {

    private static final Logger LOG = LoggerFactory.getLogger(IPV6InternetDefaultRouteProgrammer.class);
    private final IMdsalApiManager mdsalManager;

    @Inject
    public IPV6InternetDefaultRouteProgrammer(final IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    private FlowEntity buildIPv6FallbacktoExternalVpn(BigInteger dpId, long bgpVpnId, long routerId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);

        //add match for vrfid
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(bgpVpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();

        instructions.add(new InstructionGotoTable(NwConstants.EXTERNAL_TUNNEL_TABLE));

        String defaultIPv6 = "0:0:0:0:0:0:0:0";
        String flowRef = getIPv6FlowRefL3(dpId, NwConstants.L3_FIB_TABLE, defaultIPv6, routerId);

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
    void installDefaultRoute(TypedWriteTransaction<Configuration> tx, BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        LOG.trace("installDefaultRoute: flowEntity: {} ", flowEntity);
        mdsalManager.addFlow(tx, flowEntity);
    }

    void removeDefaultRoute(TypedReadWriteTransaction<Configuration> tx, BigInteger dpnId, long bgpVpnId, long routerId)
            throws ExecutionException, InterruptedException {
        FlowEntity flowEntity = buildIPv6FallbacktoExternalVpn(dpnId, bgpVpnId, routerId);
        LOG.trace("removeDefaultRoute: flowEntity: {} ", flowEntity);
        mdsalManager.removeFlow(tx, flowEntity);
    }

    public String getIPv6FlowRefL3(BigInteger dpnId, short tableId, String destPrefix, long vpnId) {
        return "L3." + dpnId.toString() + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + destPrefix + NwConstants.FLOWID_SEPARATOR + vpnId;
    }
}
