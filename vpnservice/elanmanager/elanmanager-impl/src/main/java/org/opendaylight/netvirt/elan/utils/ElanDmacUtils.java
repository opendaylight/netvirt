/*
 * Copyright © 2017 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.netvirt.elan.ElanException;
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

    @Inject
    public ElanDmacUtils(DataBroker broker, ElanItmUtils elanItmUtils, ElanEtreeUtils elanEtreeUtils) {
        this.broker = broker;
        this.elanItmUtils = elanItmUtils;
        this.elanEtreeUtils = elanEtreeUtils;
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
     * @throws ElanException in case of issues creating the flow objects
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Flow buildDmacFlowForExternalRemoteMac(BigInteger dpId, String extDeviceNodeId, long elanTag,
            Long vni, String dstMacAddress, String displayName) throws ElanException {
        List<MatchInfo> mkMatches =
                ElanUtils.buildMatchesForElanTagShFlagAndDstMac(elanTag, /* shFlag */ false, dstMacAddress);
        List<Instruction> mkInstructions = new ArrayList<>();
        try {
            List<Action> actions =
                    elanItmUtils.getExternalTunnelItmEgressAction(dpId, new NodeId(extDeviceNodeId), vni);
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get Egress Actions for DpId=" + dpId + ", externalNode=" + extDeviceNodeId, e);
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
     * @throws ElanException in case of issues creating the flow objects
     */
    public List<ListenableFuture<Void>> installDmacFlowsToExternalRemoteMac(BigInteger dpnId,
            String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) throws ElanException {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        synchronized (ElanUtils.getElanMacDPNKey(elanTag, macAddress, dpnId)) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress,
                    displayName);
            ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);

            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress);
            ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                    ElanUtils.getFlowIid(dropFlow, dpnId), dropFlow);
            installEtreeDmacFlowsToExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress, displayName,
                    interfaceName, futures);
        }
        return futures;
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
     * @throws ElanException in case of issues creating the flow objects
     * @see org.opendaylight.genius.mdsalutil.MDSALUtil.MdsalOp
     */
    public void setupDmacFlowsToExternalRemoteMac(BigInteger dpId, String extNodeId, Long elanTag, Long vni,
            String macAddress, String elanInstanceName, MDSALUtil.MdsalOp addOrRemove, String interfaceName)
            throws ElanException {
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
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        synchronized (ElanUtils.getElanMacDPNKey(elanTag, macToRemove, dpId)) {
            // Removing the flows that sends the packet on an external tunnel
            removeFlowThatSendsThePacketOnAnExternalTunnel(elanTag, dpId, extDeviceNodeId, macToRemove, futures);

            // And now removing the drop flow
            removeTheDropFlow(elanTag, dpId, extDeviceNodeId, macToRemove, futures);

            deleteEtreeDmacFlowsToExternalMac(elanTag, dpId, extDeviceNodeId, macToRemove, futures);
        }
        return futures;
    }

    private void deleteEtreeDmacFlowsToExternalMac(long elanTag, BigInteger dpId, String extDeviceNodeId,
            String macToRemove, List<ListenableFuture<Void>> futures) {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            removeFlowThatSendsThePacketOnAnExternalTunnel(etreeLeafTag.getEtreeLeafTag().getValue(), dpId,
                    extDeviceNodeId, macToRemove, futures);
            removeTheDropFlow(etreeLeafTag.getEtreeLeafTag().getValue(), dpId, extDeviceNodeId, macToRemove, futures);
        }
    }

    private void removeTheDropFlow(long elanTag, BigInteger dpId, String extDeviceNodeId, String macToRemove,
            List<ListenableFuture<Void>> futures) {
        String flowId =
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, macToRemove,
                        elanTag, true);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        ResourceBatchingManager.getInstance().delete(
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flowToRemove, dpId));
    }

    private void removeFlowThatSendsThePacketOnAnExternalTunnel(long elanTag, BigInteger dpId,
            String extDeviceNodeId, String macToRemove, List<ListenableFuture<Void>> futures) {
        String flowId =
                ElanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, extDeviceNodeId, macToRemove,
                        elanTag, false);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        ResourceBatchingManager.getInstance().delete(
                ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flowToRemove, dpId));
    }

    private void installEtreeDmacFlowsToExternalRemoteMac(BigInteger dpnId, String extDeviceNodeId, Long elanTag,
            Long vni, String macAddress, String displayName, String interfaceName,
            List<ListenableFuture<Void>> futures) throws ElanException {
        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            buildEtreeDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress, futures,
                    etreeLeafTag);
            buildEtreeDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, vni, macAddress, displayName, interfaceName,
                    futures, etreeLeafTag);
        }
    }

    private void buildEtreeDmacFlowForExternalRemoteMac(BigInteger dpnId, String extDeviceNodeId, Long vni,
            String macAddress, String displayName, String interfaceName, List<ListenableFuture<Void>> futures,
            EtreeLeafTagName etreeLeafTag) throws ElanException {
        boolean isRoot = false;
        if (interfaceName == null) {
            isRoot = true;
        } else {
            EtreeInterface etreeInterface = ElanUtils.getEtreeInterfaceByElanInterfaceName(broker, interfaceName);
            if (etreeInterface != null) {
                if (etreeInterface.getEtreeInterfaceType() == EtreeInterface.EtreeInterfaceType.Root) {
                    isRoot = true;
                }
            }
        }
        if (isRoot) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), vni, macAddress, displayName);
            ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);
        }
    }

    private void buildEtreeDmacFlowDropIfPacketComingFromTunnel(BigInteger dpnId, String extDeviceNodeId,
            Long elanTag, String macAddress, List<ListenableFuture<Void>> futures, EtreeLeafTagName etreeLeafTag) {
        if (etreeLeafTag != null) {
            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), macAddress);
            ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(dropFlow, dpnId),
                    dropFlow);
        }
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

    private static String getDmacDropFlowId(Long elanTag, String dstMacAddress) {
        return NwConstants.ELAN_DMAC_TABLE + elanTag + dstMacAddress + "Drop";
    }

    private void buildEtreeDmacFlowForExternalRemoteMacWithBatch(
            BigInteger dpnId, String extDeviceNodeId, Long vni, String macAddress, String displayName,
            String interfaceName, EtreeLeafTagName etreeLeafTag)throws ElanException {

        boolean isRoot = false;
        if (interfaceName == null) {
            isRoot = true;
        } else {
            EtreeInterface etreeInterface = ElanUtils.getEtreeInterfaceByElanInterfaceName(broker, interfaceName);
            if (etreeInterface != null) {
                if (etreeInterface.getEtreeInterfaceType() == EtreeInterface.EtreeInterfaceType.Root) {
                    isRoot = true;
                }
            }
        }
        if (isRoot) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), vni, macAddress, displayName);
            ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);
        }
    }

    private void buildEtreeDmacFlowDropIfPacketComingFromTunnelwithBatch(
            BigInteger dpnId, String extDeviceNodeId, Long elanTag, String macAddress,EtreeLeafTagName etreeLeafTag) {
        if (etreeLeafTag != null) {
            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId,
                    etreeLeafTag.getEtreeLeafTag().getValue(), macAddress);
            ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                    ElanUtils.getFlowIid(dropFlow, dpnId),dropFlow);
        }
    }

    public void installDmacFlowsToExternalRemoteMacInBatch(
            BigInteger dpnId, String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) throws ElanException {

        synchronized (ElanUtils.getElanMacDPNKey(elanTag, macAddress, dpnId)) {
            Flow flow = buildDmacFlowForExternalRemoteMac(dpnId, extDeviceNodeId, elanTag, vni, macAddress,
                    displayName);
            ResourceBatchingManager.getInstance().put(
                    ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY, ElanUtils.getFlowIid(flow, dpnId), flow);
            Flow dropFlow = buildDmacFlowDropIfPacketComingFromTunnel(dpnId, extDeviceNodeId, elanTag, macAddress);
            ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                    ElanUtils.getFlowIid(dropFlow, dpnId), dropFlow);
            installEtreeDmacFlowsToExternalRemoteMacInBatch(dpnId, extDeviceNodeId, elanTag, vni, macAddress,
                    displayName, interfaceName);
        }
    }

    private void installEtreeDmacFlowsToExternalRemoteMacInBatch(
            BigInteger dpnId, String extDeviceNodeId, Long elanTag, Long vni, String macAddress, String displayName,
            String interfaceName) throws ElanException {

        EtreeLeafTagName etreeLeafTag = elanEtreeUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            buildEtreeDmacFlowDropIfPacketComingFromTunnelwithBatch(dpnId, extDeviceNodeId, elanTag, macAddress,
                    etreeLeafTag);
            buildEtreeDmacFlowForExternalRemoteMacWithBatch(dpnId, extDeviceNodeId, vni, macAddress, displayName,
                    interfaceName, etreeLeafTag);
        }
    }
}
