/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalUcastMacListener extends ChildListener<Node, LocalUcastMacs, String>
        implements ClusteredDataTreeChangeListener<Node> {

    public static final Logger LOG = LoggerFactory.getLogger(LocalUcastMacListener.class);
    public static final String NODE_CHECK = "physical";

    private static final Predicate<InstanceIdentifier<Node>> IS_PS_NODE_IID = (iid) -> {
        return iid.firstKeyOf(Node.class).getNodeId().getValue().contains(NODE_CHECK);
    };

    private static final Predicate<InstanceIdentifier<Node>> IS_NOT_HA_CHILD = (iid) -> {
        return !HwvtepHACache.getInstance().isHAEnabledDevice(iid)
                && !iid.firstKeyOf(Node.class).getNodeId().getValue().contains(HwvtepHAUtil.PHYSICALSWITCH);
    };

    private static final Predicate<InstanceIdentifier<Node>> IS_HA_CHILD = (iid) -> {
        return HwvtepHACache.getInstance().isHAEnabledDevice(iid);
    };

    private ListenerRegistration<LocalUcastMacListener> registration;
    private ElanL2GatewayUtils elanL2GatewayUtils;
    private ElanUtils elanUtils;
    private Map<InstanceIdentifier<Node>, ScheduledFuture> staleCleanupJobsByNodeId = new ConcurrentHashMap<>();
    private Map<InstanceIdentifier<Node>, List<InstanceIdentifier<LocalUcastMacs>>> staleCandidateMacsByNodeId
            = new ConcurrentHashMap<>();
    private HAOpClusteredListener haOpClusteredListener;


    public LocalUcastMacListener(final DataBroker dataBroker,
                                 final ElanUtils elanUtils,
                                 final HAOpClusteredListener haOpClusteredListener,
                                 final ElanL2GatewayUtils elanL2GatewayUtils) {
        super(dataBroker, false);
        this.elanUtils = elanUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.haOpClusteredListener = haOpClusteredListener;
    }

    @Override
    public void init() throws Exception {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        super.init();
    }

    @Override
    protected boolean proceed(final InstanceIdentifier<Node> parent) {
        return IS_NOT_HA_CHILD.test(parent);
    }

    protected String getElanName(final LocalUcastMacs mac) {
        return ((InstanceIdentifier<LogicalSwitches>) mac.getLogicalSwitchRef().getValue())
                .firstKeyOf(LogicalSwitches.class).getHwvtepNodeName().getValue();
    }

    @Override
    protected String getGroup(final InstanceIdentifier<LocalUcastMacs> childIid,
                              final LocalUcastMacs localUcastMacs) {
        return getElanName(localUcastMacs);
    }

    @Override
    protected void onUpdate(final Map<String, Map<InstanceIdentifier, LocalUcastMacs>> updatedMacsGrouped,
                            final Map<String, Map<InstanceIdentifier, LocalUcastMacs>> deletedMacsGrouped) {
        updatedMacsGrouped.entrySet().forEach((entry) -> {
            entry.getValue().entrySet().forEach((entry2) -> {
                added(entry2.getKey(), entry2.getValue());
            });
        });
        deletedMacsGrouped.entrySet().forEach((entry) -> {
            entry.getValue().entrySet().forEach((entry2) -> {
                removed(entry2.getKey(), entry2.getValue());
            });
        });
    }

    public void removed(final InstanceIdentifier<LocalUcastMacs> identifier, final LocalUcastMacs macRemoved) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macRemoved.getMacEntryKey().getValue().toLowerCase();

        LOG.trace("LocalUcastMacs {} removed from {}", macAddress, hwvtepNodeId);

        ResourceBatchingManager.getInstance().delete(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                identifier);

        String elanName = getElanName(macRemoved);

        DataStoreJobCoordinator.getInstance().enqueueJob(elanName + HwvtepHAUtil.L2GW_JOB_KEY ,
            () -> {
                L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName,
                        hwvtepNodeId);
                if (elanL2GwDevice == null) {
                    LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache",
                            elanName, hwvtepNodeId);
                    return null;
                }

                elanL2GwDevice.removeUcastLocalMac(macRemoved);
                ElanInstance elanInstance = ElanUtils.getElanInstanceByName(dataBroker, elanName);
                elanL2GatewayUtils.unInstallL2GwUcastMacFromElan(elanInstance, elanL2GwDevice,
                        Collections.singletonList(new MacAddress(macAddress.toLowerCase())));
                return null;
            });
    }

    public void added(final InstanceIdentifier<LocalUcastMacs> identifier, final LocalUcastMacs macAdded) {
        InstanceIdentifier<Node> nodeIid = identifier.firstIdentifierOf(Node.class);
        if (staleCandidateMacsByNodeId.get(nodeIid) != null) {
            LOG.trace("Clearing from candidate stale mac {}", identifier);
            staleCandidateMacsByNodeId.get(nodeIid).remove(identifier);
        }
        ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                identifier, macAdded);

        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macAdded.getMacEntryKey().getValue().toLowerCase();
        String elanName = getElanName(macAdded);

        LOG.trace("LocalUcastMacs {} added to {}", macAddress, hwvtepNodeId);

        ElanInstance elan = ElanUtils.getElanInstanceByName(dataBroker, elanName);
        if (elan == null) {
            LOG.warn("Could not find ELAN for mac {} being added", macAddress);
            return;
        }
        DataStoreJobCoordinator.getInstance().enqueueJob(elanName + HwvtepHAUtil.L2GW_JOB_KEY,
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
            if (children != null) {
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
        }
        return result;
    }

    @Override
    protected void onParentAdded(final DataTreeModification<Node> modification) {
        InstanceIdentifier<Node> nodeIid = modification.getRootPath().getRootIdentifier();
        if (IS_PS_NODE_IID.test(nodeIid)) {
            return;
        }
        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        haOpClusteredListener.onGlobalNodeAdd(nodeIid, modification.getRootNode().getDataAfter(), tx);
        tx.submit();
        if (IS_HA_CHILD.test(nodeIid)) {
            return;
        }

        LOG.trace("On parent add {}", nodeIid);
        Node operNode = modification.getRootNode().getDataAfter();
        Optional<Node> configNode = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, nodeIid);
        Set<LocalUcastMacs> configMacs = getMacs(configNode);
        Set<LocalUcastMacs> operMacs = getMacs(Optional.of(operNode));
        Set<LocalUcastMacs> staleMacs = Sets.difference(configMacs, operMacs);
        staleMacs.forEach(staleMac -> removed(getMacIid(nodeIid, staleMac), staleMac));
    }

    InstanceIdentifier<LocalUcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, LocalUcastMacs mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class)
                .child(LocalUcastMacs.class, mac.getKey());
    }

    Set<LocalUcastMacs> getMacs(Optional<Node> node) {
        if (node.isPresent()) {
            HwvtepGlobalAugmentation augmentation = node.get().getAugmentation(HwvtepGlobalAugmentation.class);
            if (augmentation != null && augmentation.getLocalUcastMacs() != null) {
                return new HashSet(augmentation.getLocalUcastMacs());
            }
        }
        return Collections.EMPTY_SET;
    }

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
}
