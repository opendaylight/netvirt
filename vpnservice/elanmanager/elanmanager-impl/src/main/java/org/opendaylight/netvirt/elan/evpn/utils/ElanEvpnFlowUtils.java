/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class ElanEvpnFlowUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanEvpnFlowUtils.class);
    private final IMdsalApiManager mdsalManager;
    private final ElanUtils elanUtils;

    @Inject
    public ElanEvpnFlowUtils(IMdsalApiManager mdsalManager, ElanUtils elanUtils) {
        this.mdsalManager = mdsalManager;
        this.elanUtils = elanUtils;
    }

    public Flow evpnBuildDmacFlowForExternalRemoteMac(BigInteger dpId, String nexthopIP, long elanTag, Long vni,
                                                      String dstMacAddress, String displayName) throws ElanException {
        List<MatchInfo> mkMatches = elanUtils.buildMatchesForElanTagShFlagAndDstMac(elanTag, false, dstMacAddress);
        List<Instruction> mkInstructions = new ArrayList<>();
        List<Action> actions = elanUtils.getExternalTunnelItmEgressAction(dpId, nexthopIP, vni);
        mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                elanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIP, dstMacAddress,
                        elanTag, false), 20, displayName, 0, 0,
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;
    }

    public List<ListenableFuture<Void>> evpnDeleteDmacFlowsToExternalMac(long elanTag, BigInteger dpId,
                                                                         String nexthopIp, String macToRemove) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        synchronized (elanUtils.getElanMacDPNKey(elanTag, macToRemove, dpId)) {
            evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(elanTag, dpId, nexthopIp, macToRemove, futures);
            evpnDeleteEtreeDmacFlowsToExternalMac(elanTag, dpId, nexthopIp, macToRemove, futures);
        }
        return futures;
    }

    private void evpnDeleteEtreeDmacFlowsToExternalMac(long elanTag, BigInteger dpId, String nexthopIp,
                                                       String macToRemove, List<ListenableFuture<Void>> futures) {
        EtreeLeafTagName etreeLeafTag = elanUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(etreeLeafTag.getEtreeLeafTag().getValue(), dpId,
                    nexthopIp, macToRemove, futures);
            evpnRemoveTheDropFlow(etreeLeafTag.getEtreeLeafTag().getValue(), dpId, nexthopIp, macToRemove, futures);
        }
    }

    static String evpnGetKnownDynamicmacFlowRef(short elanDmacTable, BigInteger dpId, String nexthopIp,
                                                String dstMacAddress, long elanTag, boolean shFlag) {
        return new StringBuffer().append(elanDmacTable).append(elanTag).append(dpId).append(nexthopIp)
                .append(dstMacAddress).append(shFlag).toString();
    }

    private void evpnRemoveTheDropFlow(long elanTag, BigInteger dpId, String nexthopIp, String macToRemove,
                                       List<ListenableFuture<Void>> futures) {
        String flowId = ElanEvpnFlowUtils.evpnGetKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIp,
                macToRemove, elanTag, true);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        futures.add(mdsalManager.removeFlow(dpId, flowToRemove));
    }

    private void evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(long elanTag, BigInteger dpId, String nexthopIp,
                                                                    String macToRemove,
                                                                    List<ListenableFuture<Void>> futures) {
        String flowId = ElanEvpnFlowUtils.evpnGetKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIp,
                macToRemove, elanTag, false);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        futures.add(mdsalManager.removeFlow(dpId, flowToRemove));
    }

}
