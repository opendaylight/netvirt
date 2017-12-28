/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LocalUcastMacListener extends ChildListener<Node, LocalUcastMacs, String>
        implements ClusteredDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(LocalUcastMacListener.class);
    public static final String NODE_CHECK = "physical";

    private static final Predicate<InstanceIdentifier<Node>> IS_PS_NODE_IID =
        (iid) -> iid.firstKeyOf(Node.class).getNodeId().getValue().contains(NODE_CHECK);

    private final ManagedNewTransactionRunner txRunner;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final HAOpClusteredListener haOpClusteredListener;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final HwvtepNodeHACache hwvtepNodeHACache;

    @Inject
    public LocalUcastMacListener(final DataBroker dataBroker,
                                 final HAOpClusteredListener haOpClusteredListener,
                                 final ElanL2GatewayUtils elanL2GatewayUtils,
                                 final JobCoordinator jobCoordinator,
                                 final ElanInstanceCache elanInstanceCache,
                                 final HwvtepNodeHACache hwvtepNodeHACache) {
        super(dataBroker, false);
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.haOpClusteredListener = haOpClusteredListener;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.hwvtepNodeHACache = hwvtepNodeHACache;
    }

    @Override
    @PostConstruct
    public void init() throws Exception {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        super.init();
    }

    @Override
    protected boolean proceed(final InstanceIdentifier<Node> parent) {
        return isNotHAChild(parent);
    }

    protected String getElanName(final LocalUcastMacs mac) {
        return mac.getLogicalSwitchRef().getValue().firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }

    @Override
    protected String getGroup(final LocalUcastMacs localUcastMacs) {
        return getElanName(localUcastMacs);
    }

    @Override
    protected void onUpdate(final Map<String, Map<InstanceIdentifier, LocalUcastMacs>> updatedMacsGrouped,
                            final Map<String, Map<InstanceIdentifier, LocalUcastMacs>> deletedMacsGrouped) {
        updatedMacsGrouped.forEach((key, value) -> value.forEach(this::added));
        deletedMacsGrouped.forEach((key, value) -> value.forEach(this::removed));
    }

    public void removed(final InstanceIdentifier<LocalUcastMacs> identifier, final LocalUcastMacs macRemoved) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macRemoved.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());

        LOG.trace("LocalUcastMacs {} removed from {}", macAddress, hwvtepNodeId);

        ResourceBatchingManager.getInstance().delete(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                identifier);

        String elanName = getElanName(macRemoved);

        jobCoordinator.enqueueJob(elanName + HwvtepHAUtil.L2GW_JOB_KEY ,
            () -> {
                L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName,
                        hwvtepNodeId);
                if (elanL2GwDevice == null) {
                    LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache",
                            elanName, hwvtepNodeId);
                    return null;
                }

                elanL2GwDevice.removeUcastLocalMac(macRemoved);
                ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
                elanL2GatewayUtils.unInstallL2GwUcastMacFromL2gwDevices(elanName, elanL2GwDevice,
                        Collections.singletonList(new MacAddress(macAddress.toLowerCase(Locale.getDefault()))));
                elanL2GatewayUtils.unInstallL2GwUcastMacFromElanDpns(elanInstance, elanL2GwDevice,
                        Collections.singletonList(new MacAddress(macAddress.toLowerCase(Locale.getDefault()))));
                return null;
            });
    }

    public void added(final InstanceIdentifier<LocalUcastMacs> identifier, final LocalUcastMacs macAdded) {
        ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                identifier, macAdded);

        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macAdded.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());
        String elanName = getElanName(macAdded);

        LOG.trace("LocalUcastMacs {} added to {}", macAddress, hwvtepNodeId);

        ElanInstance elan = elanInstanceCache.get(elanName).orNull();
        if (elan == null) {
            LOG.warn("Could not find ELAN for mac {} being added", macAddress);
            return;
        }
        jobCoordinator.enqueueJob(elanName + HwvtepHAUtil.L2GW_JOB_KEY,
            () -> {
                L2GatewayDevice elanL2GwDevice =
                        ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, hwvtepNodeId);
                if (elanL2GwDevice == null) {
                    LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache",
                            elanName, hwvtepNodeId);
                    return null;
                }

                elanL2GwDevice.addUcastLocalMac(macAdded);
                elanL2GatewayUtils.installL2GwUcastMacInElan(elan, elanL2GwDevice,
                        macAddress.toLowerCase(), macAdded, null);
                return null;
            });
    }

    @Override
    protected Map<InstanceIdentifier<LocalUcastMacs>, DataObjectModification<LocalUcastMacs>> getChildMod(
            final InstanceIdentifier<Node> parentIid,
            final DataObjectModification<Node> mod) {

        Map<InstanceIdentifier<LocalUcastMacs>, DataObjectModification<LocalUcastMacs>> result = new HashMap<>();
        DataObjectModification<HwvtepGlobalAugmentation> aug = mod.getModifiedAugmentation(
                HwvtepGlobalAugmentation.class);
        if (aug != null && getModificationType(aug) != null) {
            Collection<DataObjectModification<? extends DataObject>> children = aug.getModifiedChildren();
            children.stream()
                .filter(childMod -> getModificationType(childMod) != null)
                .filter(childMod -> childMod.getDataType() == LocalUcastMacs.class)
                .forEach(childMod -> {
                    LocalUcastMacs afterMac = (LocalUcastMacs) childMod.getDataAfter();
                    LocalUcastMacs mac = afterMac != null ? afterMac : (LocalUcastMacs)childMod.getDataBefore();
                    InstanceIdentifier<LocalUcastMacs> iid = parentIid
                        .augmentation(HwvtepGlobalAugmentation.class)
                        .child(LocalUcastMacs.class, mac.getKey());
                    result.put(iid, (DataObjectModification<LocalUcastMacs>) childMod);
                });
        }
        return result;
    }

    @Override
    protected void onParentAdded(final DataTreeModification<Node> modification) {
        InstanceIdentifier<Node> nodeIid = modification.getRootPath().getRootIdentifier();
        if (IS_PS_NODE_IID.test(nodeIid)) {
            return;
        }
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            haOpClusteredListener.onGlobalNodeAdd(nodeIid, modification.getRootNode().getDataAfter(), tx);
            if (!isHAChild(nodeIid)) {
                LOG.trace("On parent add {}", nodeIid);
                Node operNode = modification.getRootNode().getDataAfter();
                Set<LocalUcastMacs> configMacs =
                        getMacs(tx.read(LogicalDatastoreType.CONFIGURATION, nodeIid).checkedGet().orNull());
                Set<LocalUcastMacs> operMacs = getMacs(operNode);
                Set<LocalUcastMacs> staleMacs = Sets.difference(configMacs, operMacs);
                staleMacs.forEach(staleMac -> removed(getMacIid(nodeIid, staleMac), staleMac));
            }
        }), LOG, "Error processing added parent");
    }

    InstanceIdentifier<LocalUcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, LocalUcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class)
                .child(LocalUcastMacs.class, mac.getKey());
    }

    private Set<LocalUcastMacs> getMacs(@Nullable Node node) {
        if (node != null) {
            HwvtepGlobalAugmentation augmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation != null && augmentation.getLocalUcastMacs() != null) {
                return new HashSet<>(augmentation.getLocalUcastMacs());
            }
        }
        return Collections.emptySet();
    }

    @Override
    protected void onParentRemoved(InstanceIdentifier<Node> parent) {
        if (IS_PS_NODE_IID.test(parent)) {
            return;
        }
        LOG.trace("on parent removed {}", parent);
    }

    @Override
    protected InstanceIdentifier<Node> getParentWildCardPath() {
        return HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier()
                .child(Node.class);
    }

    private boolean isNotHAChild(InstanceIdentifier<Node> nodeId) {
        return !hwvtepNodeHACache.isHAEnabledDevice(nodeId)
                && !nodeId.firstKeyOf(Node.class).getNodeId().getValue().contains(HwvtepHAUtil.PHYSICALSWITCH);
    }

    private boolean isHAChild(InstanceIdentifier<Node> nodeId) {
        return hwvtepNodeHACache.isHAEnabledDevice(nodeId);
    }
}
