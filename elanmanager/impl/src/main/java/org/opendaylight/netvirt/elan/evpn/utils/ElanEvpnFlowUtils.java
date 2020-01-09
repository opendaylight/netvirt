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
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.Acquired;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanEtreeUtils;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.opendaylight.yangtools.yang.common.Uint64;


@Singleton
public class ElanEvpnFlowUtils {
    private final IMdsalApiManager mdsalManager;
    private final ElanItmUtils elanItmUtils;
    private final ElanEtreeUtils elanEtreeUtils;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public ElanEvpnFlowUtils(final IMdsalApiManager mdsalManager, final ElanItmUtils elanItmUtils,
            final ElanEtreeUtils elanEtreeUtils, final DataBroker dataBroker) {
        this.mdsalManager = mdsalManager;
        this.elanItmUtils = elanItmUtils;
        this.elanEtreeUtils = elanEtreeUtils;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    public Flow evpnBuildDmacFlowForExternalRemoteMac(EvpnDmacFlow evpnDmacFlow) {
        List<MatchInfo> mkMatches = ElanUtils.buildMatchesForElanTagShFlagAndDstMac(evpnDmacFlow.getElanTag(), false,
                evpnDmacFlow.getDstMacAddress());
        List<Instruction> mkInstructions = new ArrayList<>();
        List<Action> actions = elanItmUtils.getExternalTunnelItmEgressAction(evpnDmacFlow.getDpId(),
                evpnDmacFlow.getNexthopIP(), evpnDmacFlow.getVni());
        mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, evpnDmacFlow.getDpId(),
                        evpnDmacFlow.getNexthopIP(), evpnDmacFlow.getDstMacAddress(), evpnDmacFlow.getElanTag(), false),
                ElanConstants.ELAN_MAC_PRIORITY, evpnDmacFlow.getElanName(), 0, 0,
                Uint64.valueOf(ElanConstants.COOKIE_ELAN_KNOWN_DMAC.toJava()
                    .add(BigInteger.valueOf(evpnDmacFlow.getElanTag()))),
                mkMatches, mkInstructions);

        return flow;
    }

    public List<ListenableFuture<Void>> evpnDeleteDmacFlowsToExternalMac(EvpnDmacFlow evpnDmacFlow) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        try (Acquired lock = ElanUtils.lockElanMacDPN(evpnDmacFlow.getElanTag(), evpnDmacFlow.getDstMacAddress(),
                evpnDmacFlow.getDpId())) {
            futures.addAll(
                    evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(evpnDmacFlow.getElanTag(), evpnDmacFlow.dpId,
                            evpnDmacFlow.getNexthopIP(), evpnDmacFlow.getDstMacAddress()));
            futures.addAll(evpnDeleteEtreeDmacFlowsToExternalMac(evpnDmacFlow.getElanTag(), evpnDmacFlow.getDpId(),
                    evpnDmacFlow.getNexthopIP(), evpnDmacFlow.getDstMacAddress()));
        }
        return futures;
    }

    private List<ListenableFuture<Void>> evpnDeleteEtreeDmacFlowsToExternalMac(long elanTag, Uint64 dpId,
            String nexthopIp, String macToRemove) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            futures.addAll(
                    evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(
                            etreeLeafTag.getEtreeLeafTag().getValue().toJava(), dpId,
                            nexthopIp, macToRemove));
            futures.addAll(
                    evpnRemoveTheDropFlow(etreeLeafTag.getEtreeLeafTag().getValue().toJava(),
                            dpId, nexthopIp, macToRemove));
        }
        return futures;
    }

    static String evpnGetKnownDynamicmacFlowRef(short elanDmacTable, Uint64 dpId, String nexthopIp,
                                                String dstMacAddress, long elanTag, boolean shFlag) {
        return String.valueOf(elanDmacTable) + elanTag + dpId + nexthopIp + dstMacAddress + shFlag;
    }

    private List<ListenableFuture<Void>> evpnRemoveTheDropFlow(long elanTag, Uint64 dpId, String nexthopIp,
            String macToRemove) {
        String flowId = ElanEvpnFlowUtils.evpnGetKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIp,
                macToRemove, elanTag, true);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION,
            tx -> mdsalManager.removeFlow(tx, dpId, flowToRemove)));
    }

    private List<ListenableFuture<Void>> evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(long elanTag,
            Uint64 dpId, String nexthopIp, String macToRemove) {
        String flowId = ElanEvpnFlowUtils.evpnGetKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIp,
                macToRemove, elanTag, false);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(Datastore.CONFIGURATION,
            tx -> mdsalManager.removeFlow(tx, dpId, flowToRemove)));
    }

    public static class EvpnDmacFlowBuilder {
        private Uint64 dpId;
        private String nexthopIP;
        private long elanTag;
        private Long vni;
        private String dstMacAddress;
        private String elanName;

        public EvpnDmacFlowBuilder() {
        }

        public EvpnDmacFlowBuilder setDpId(Uint64 dpId) {
            this.dpId = dpId;
            return this;
        }

        public EvpnDmacFlowBuilder setNexthopIP(String nexthopIP) {
            this.nexthopIP = nexthopIP;
            return this;
        }

        public EvpnDmacFlowBuilder setElanTag(long elanTag) {
            this.elanTag = elanTag;
            return this;
        }

        public EvpnDmacFlowBuilder setVni(Long vni) {
            this.vni = vni;
            return this;
        }

        public EvpnDmacFlowBuilder setDstMacAddress(String dstMacAddress) {
            this.dstMacAddress = dstMacAddress;
            return this;
        }

        public EvpnDmacFlowBuilder setElanName(String elanName) {
            this.elanName = elanName;
            return this;
        }

        public EvpnDmacFlow build() {
            return new EvpnDmacFlow(dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);
        }
    }

    static class EvpnDmacFlow {
        private final Uint64 dpId;
        private final String nexthopIP;
        private final long elanTag;
        private final Long vni;
        private final String dstMacAddress;
        private final String elanName;

        EvpnDmacFlow(Uint64 dpId, String nexthopIP, long elanTag, Long vni, String dstMacAddress,
                            String elanName) {
            this.dpId = dpId;
            this.nexthopIP = nexthopIP;
            this.elanTag = elanTag;
            this.vni = vni;
            this.dstMacAddress = dstMacAddress;
            this.elanName = elanName;
        }

        public Uint64 getDpId() {
            return dpId;
        }

        public String getNexthopIP() {
            return nexthopIP;
        }

        public long getElanTag() {
            return elanTag;
        }

        public Long getVni() {
            return vni;
        }

        public String getDstMacAddress() {
            return dstMacAddress;
        }

        public String getElanName() {
            return elanName;
        }
    }
}
