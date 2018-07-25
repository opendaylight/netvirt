/*
 * Copyright © 2017 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanDmacUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanDmacUtils.class);

    private static final boolean SH_FLAG_SET = true;
    private static final boolean SH_FLAG_UNSET = false;

    private final DataBroker broker;
    private final ElanItmUtils elanItmUtils;
    private final ElanEtreeUtils elanEtreeUtils;
    private final ElanInterfaceCache elanInterfaceCache;

    @Inject
    public ElanDmacUtils(DataBroker broker, ElanItmUtils elanItmUtils, ElanEtreeUtils elanEtreeUtils,
            ElanInterfaceCache elanInterfaceCache) {
        this.broker = broker;
        this.elanItmUtils = elanItmUtils;
        this.elanEtreeUtils = elanEtreeUtils;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    /**
     * Builds a Flow to be programmed in a DPN's DMAC table. This method must be
     * used when the MAC is located in an External Device (TOR). The flow
     * matches on the specified MAC and 1) sends the packet over the CSS-TOR
     * tunnel if SHFlag is not set, or 2) drops it if SHFlag is set (what means
     * the packet came from an external tunnel)
     *
     * @param dpId
     *            DPN whose DMAC table is going to be modified
     * @param extDeviceNodeId
     *            Hwvtep node where the mac is attached to
     * @param elanTag
     *            ElanId to which the MAC is being added to
     * @param vni
     *            the vni
     * @param dstMacAddress
     *            The mac address to be programmed
     * @param displayName
     *            the display name
     * @return the flow
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Flow buildDmacFlowForExternalRemoteMac(BigInteger dpId, String extDeviceNodeId, long elanTag,
            Long vni, String dstMacAddress, String displayName) {
        List<MatchInfo> mkMatches =
                ElanUtils.buildMatchesForElanTagShFlagAndDstMac(elanTag, /* shFlag */ false, dstMacAddress);
        List<Instruction> mkInstructions = new ArrayList<>();
        try {
            List<Action> actions =
                    elanItmUtils.getExternalTunnelItmEgressAction(dpId, new NodeId(extDeviceNodeId), vni);
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get Egress Actions for DpId {} externalNode {}", dpId, extDeviceNodeId, e);
        }

        return MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, dstMacAddress,
                        elanTag, false),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);
    }

    /**
     * Installs a Flow in the specified DPN's DMAC table. The flow is for a MAC
     * that is connected remotely in an External Device (TOR) and that is
     * accessible through an external tunnel. It also installs the flow for
     * dropping the packet if it came over an ITM tunnel (that is, if the
     * Split-Horizon flag is set)
     *
     * @param dpnId
     *            Id of the DPN where the flow must be installed
     * @param extDeviceNodeId
     *            the ext device node id
     * @param elanTag
     *            the elan tag
     * @param vni
     *            the vni
     * @param macAddress
     *            the mac address
     * @param displayName
     *            the display name
     * @param interfaceName
     *            the interface name
     *
     * @return the dmac flows
     */
    public List<ListenableFuture<Void>> installDmacFlowsToExternalRemoteMac(BigInteger dpnId,
            String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) {
        synchronized (ElanUtils.getElanMacDPNKey(elanTag, macAddress, dpnId)) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress,
                    displayName);
            ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);

            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress);
            ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                    ElanUtils.getFlowIid(dropFlow, dpnId), dropFlow);
            installEtreeDmacFlowsToExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress, displayName,
                    interfaceName);
        }
        return Collections.emptyList();
    }

    /**
     * Installs or removes flows in DMAC table for MACs that are/were located in
     * an external Elan Device.
     *
     * @param dpId
     *            Id of the DPN where the DMAC table is going to be modified
     * @param extNodeId
     *            Id of the External Device where the MAC is located
     * @param elanTag
     *            Id of the ELAN
     * @param vni
     *            VNI of the LogicalSwitch to which the MAC belongs to, and that
     *            is associated with the ELAN
     * @param macAddress
     *            the mac address
     * @param elanInstanceName
     *            the elan instance name
     * @param addOrRemove
     *            Indicates if flows must be installed or removed.
     * @param interfaceName
     *            the interface name
     * @see org.opendaylight.genius.mdsalutil.MDSALUtil.MdsalOp
     */
    public void setupDmacFlowsToExternalRemoteMac(BigInteger dpId, String extNodeId, Long elanTag, Long vni,
            String macAddress, String elanInstanceName, MDSALUtil.MdsalOp addOrRemove, String interfaceName) {
        if (addOrRemove == MDSALUtil.MdsalOp.CREATION_OP) {
            installDmacFlowsToExternalRemoteMac(dpId, extNodeId, elanTag, vni, macAddress, elanInstanceName,
                    interfaceName);
        } else if (addOrRemove == MDSALUtil.MdsalOp.REMOVAL_OP) {
            deleteDmacFlowsToExternalMac(elanTag, dpId, extNodeId, macAddress);
        }
    }

    /**
     * Delete dmac flows to external mac.
     *
     * @param elanTag
     *            the elan tag
     * @param dpId
     *            the dp id
     * @param extDeviceNodeId
     *            the ext device node id
     * @param macToRemove
     *            the mac to remove
     * @return dmac flow
     */
    public List<ListenableFuture<Void>> deleteDmacFlowsToExternalMac(long elanTag, BigInteger dpId,
            String extDeviceNodeId, String macToRemove) {
        List<ListenableFuture<Void>> result = Lists.newArrayList(
            removeFlowThatSendsThePacketOnAnExternalTunnel(elanTag, dpId, extDeviceNodeId, macToRemove),
            removeTheDropFlow(elanTag, dpId, extDeviceNodeId, macToRemove));
        result.addAll(deleteEtreeDmacFlowsToExternalMac(elanTag, dpId, extDeviceNodeId, macToRemove));
        return result;
    }

    private List<ListenableFuture<Void>> deleteEtreeDmacFlowsToExternalMac(
            long elanTag, BigInteger dpId, String extDeviceNodeId, String macToRemove) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            return Lists.newArrayList(
                    removeFlowThatSendsThePacketOnAnExternalTunnel(
                            etreeLeafTag.getEtreeLeafTag().getValue(), dpId, extDeviceNodeId, macToRemove),
                    removeTheDropFlow(etreeLeafTag.getEtreeLeafTag().getValue(), dpId, extDeviceNodeId, macToRemove));
        }
        return Collections.emptyList();
    }

    private ListenableFuture<Void> removeTheDropFlow(
            long elanTag, BigInteger dpId, String extDeviceNodeId, String macToRemove) {
        String flowId =
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, macToRemove,
                        elanTag, true);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        return ResourceBatchingManager.getInstance().delete(
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flowToRemove, dpId));
    }

    private ListenableFuture<Void> removeFlowThatSendsThePacketOnAnExternalTunnel(long elanTag, BigInteger dpId,
            String extDeviceNodeId, String macToRemove) {
        String flowId =
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, macToRemove,
                        elanTag, false);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        return ResourceBatchingManager.getInstance().delete(
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flowToRemove, dpId));
    }

    private List<ListenableFuture<Void>> installEtreeDmacFlowsToExternalRemoteMac(
            BigInteger dpnId, String extDeviceNodeId, Long elanTag,
            Long vni, String macAddress, String displayName, String interfaceName) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            return Lists.newArrayList(
                buildEtreeDmacFlowDropIfPacketComingFromTunnel(
                    dpnId, extDeviceNodeId, macAddress, etreeLeafTag),
                buildEtreeDmacFlowForExternalRemoteMac(
                        dpnId, extDeviceNodeId, vni, macAddress, displayName, interfaceName, etreeLeafTag));
        }
        return Collections.emptyList();
    }

    private ListenableFuture<Void> buildEtreeDmacFlowForExternalRemoteMac(
            BigInteger dpnId, String extDeviceNodeId, Long vni,
            String macAddress, String displayName, String interfaceName,
            EtreeLeafTagName etreeLeafTag) {
        boolean isRoot;
        if (interfaceName == null) {
            isRoot = true;
        } else {
            Optional<EtreeInterface> etreeInterface = elanInterfaceCache.getEtreeInterface(interfaceName);
            isRoot = etreeInterface.isPresent() ? etreeInterface.get().getEtreeInterfaceType()
                    == EtreeInterface.EtreeInterfaceType.Root : false;
        }
        if (isRoot) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), vni, macAddress, displayName);
            return ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);
        }
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<Void> buildEtreeDmacFlowDropIfPacketComingFromTunnel(
            BigInteger dpnId, String extDeviceNodeId, String macAddress, EtreeLeafTagName etreeLeafTag) {
        if (etreeLeafTag != null) {
            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), macAddress);
            return ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(dropFlow, dpnId),
                    dropFlow);
        }
        return Futures.immediateFuture(null);
    }

    /**
     * Builds the flow that drops the packet if it came through an external
     * tunnel, that is, if the Split-Horizon flag is set.
     *
     * @param dpnId
     *            DPN whose DMAC table is going to be modified
     * @param extDeviceNodeId
     *            Hwvtep node where the mac is attached to
     * @param elanTag
     *            ElanId to which the MAC is being added to
     * @param dstMacAddress
     *            The mac address to be programmed
     */
    private static Flow buildDmacFlowDropIfPacketComingFromTunnel(BigInteger dpnId, String extDeviceNodeId,
            Long elanTag, String dstMacAddress) {
        List<MatchInfo> mkMatches =
                ElanUtils.buildMatchesForElanTagShFlagAndDstMac(elanTag, SH_FLAG_SET, dstMacAddress);
        List<Instruction> mkInstructions = MDSALUtil.buildInstructionsDrop();
        String flowId =
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpnId, extDeviceNodeId, dstMacAddress,
                        elanTag, true);

        return MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE, flowId, 20, /* prio */
                "Drop", 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);
    }

    private ListenableFuture<Void> buildEtreeDmacFlowForExternalRemoteMacWithBatch(
            BigInteger dpnId, String extDeviceNodeId, Long vni, String macAddress, String displayName,
            String interfaceName, EtreeLeafTagName etreeLeafTag) {

        boolean isRoot;
        if (interfaceName == null) {
            isRoot = true;
        } else {
            Optional<EtreeInterface> etreeInterface = elanInterfaceCache.getEtreeInterface(interfaceName);
            isRoot = etreeInterface.isPresent() ? etreeInterface.get().getEtreeInterfaceType()
                    == EtreeInterface.EtreeInterfaceType.Root : false;
        }
        if (isRoot) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), vni, macAddress, displayName);
            return ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);
        }
        return Futures.immediateFuture(null);
    }

    private ListenableFuture<Void> buildEtreeDmacFlowDropIfPacketComingFromTunnelwithBatch(
            BigInteger dpnId, String extDeviceNodeId, String macAddress, EtreeLeafTagName etreeLeafTag) {
        if (etreeLeafTag != null) {
            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), macAddress);
            return ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                    ElanUtils.getFlowIid(dropFlow, dpnId),dropFlow);
        }
        return Futures.immediateFuture(null);
    }

    public List<ListenableFuture<Void>> installDmacFlowsToExternalRemoteMacInBatch(
            BigInteger dpnId, String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) {

        Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress,
                displayName);
        Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress);
        List<ListenableFuture<Void>> result = Lists.newArrayList(
                ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow),
                ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(dropFlow, dpnId),
                        dropFlow));
        result.addAll(installEtreeDmacFlowsToExternalRemoteMacInBatch(
                dpnId, extDeviceNodeId, elanTag, vni, macAddress, displayName, interfaceName));
        return result;
    }

    private List<ListenableFuture<Void>> installEtreeDmacFlowsToExternalRemoteMacInBatch(
            BigInteger dpnId, String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) {

        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            return Lists.newArrayList(
                buildEtreeDmacFlowDropIfPacketComingFromTunnelwithBatch(
                        dpnId, extDeviceNodeId, macAddress, etreeLeafTag),
                buildEtreeDmacFlowForExternalRemoteMacWithBatch(
                        dpnId, extDeviceNodeId, vni, macAddress, displayName, interfaceName, etreeLeafTag));
        }
        return Collections.emptyList();
    }
}
