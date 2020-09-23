/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.ha.listeners.HAOpClusteredListener;
import org.opendaylight.netvirt.elan.l2gw.recovery.impl.L2GatewayServiceRecoveryHandler;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SrcnodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SrcnodeAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LocalUcastMacListener extends ChildListener<Node, LocalUcastMacs, String>
        implements ClusteredDataTreeChangeListener<Node>, RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(LocalUcastMacListener.class);
    public static final String NODE_CHECK = "physical";

    private static final Predicate<InstanceIdentifier<Node>> IS_PS_NODE_IID =
        (iid) -> iid.firstKeyOf(Node.class).getNodeId().getValue().contains(NODE_CHECK);

    private static final Predicate<InstanceIdentifier<Node>> IS_NOT_HA_CHILD =
        (iid) -> !HwvtepHACache.getInstance().isHAEnabledDevice(iid)
                && !iid.firstKeyOf(Node.class).getNodeId().getValue().contains(HwvtepHAUtil.PHYSICALSWITCH);

    private static final Predicate<InstanceIdentifier<Node>> IS_HA_CHILD =
        (iid) -> HwvtepHACache.getInstance().isHAEnabledDevice(iid);

    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final HAOpClusteredListener haOpClusteredListener;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanClusterUtils elanClusterUtils;
    private final Scheduler scheduler;
    private final ManagedNewTransactionRunner txRunner;
    private final L2GatewayCache l2GatewayCache;
    private InstanceIdDataObjectCache<MacEntry> elanMacEntryConfigCache;
    private Map<InstanceIdentifier<MacEntry>, MacEntry> localMacEntryCache = new ConcurrentHashMap<>();
    private final CacheProvider cacheProvider;
    private final ConcurrentMap<String, ScheduledFuture> localUcastMacDeletedTasks
            = new ConcurrentHashMap<>();
    private static final String STALE_LOCAL_UCAST_CLEANUP_JOB = "stale-local-ucast-clean-up-job";
    private final ElanConfig elanConfig;

    @Inject
    public LocalUcastMacListener(final DataBroker dataBroker,
                                 final HAOpClusteredListener haOpClusteredListener,
                                 final ElanL2GatewayUtils elanL2GatewayUtils,
                                 final JobCoordinator jobCoordinator,
                                 final ElanInstanceCache elanInstanceCache,
                                 final L2GatewayCache l2GatewayCache,
                                 final CacheProvider cacheProvider,
                                 final Scheduler scheduler,
                                 final L2GatewayServiceRecoveryHandler l2GatewayServiceRecoveryHandler,
                                 final ServiceRecoveryRegistry serviceRecoveryRegistry,
                                 final ElanClusterUtils elanClusterUtils,
                                 final ElanConfig elanConfig) {
        super(dataBroker, false);
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.haOpClusteredListener = haOpClusteredListener;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.elanClusterUtils = elanClusterUtils;
        this.scheduler = scheduler;
        this.l2GatewayCache = l2GatewayCache;
        this.cacheProvider = cacheProvider;
        this.elanConfig = elanConfig;
        serviceRecoveryRegistry.addRecoverableListener(l2GatewayServiceRecoveryHandler.buildServiceRegistryKey(), this);
    }

    private void initializeElanMacEntryCache() {
        InstanceIdentifier<MacEntry> iid = InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class)
                .child(MacEntry.class).build();

        elanMacEntryConfigCache = new InstanceIdDataObjectCache(MacEntry.class, dataBroker,
                LogicalDatastoreType.CONFIGURATION, iid, cacheProvider) {
            @Override
            protected void added(InstanceIdentifier path, DataObject dataObject) {
                localMacEntryCache.put(path, (MacEntry)dataObject);
            }

            @Override
            protected void removed(InstanceIdentifier path, DataObject dataObject) {
                localMacEntryCache.remove(path);
            }
        };
    }

    private MacEntry getElanMacEntryFromCache(InstanceIdentifier<MacEntry> iid) {
        if (localMacEntryCache.containsKey(iid)) {
            return localMacEntryCache.get(iid);
        }
        try {
            return elanMacEntryConfigCache.get(iid).orElse(null);
        } catch (ReadFailedException e) {
            LOG.error("Failed to read err iid {}",iid, e);
        }
        return null;
    }

    @Override
    @PostConstruct
    public void init() throws Exception {
        ResourceBatchingManager.getInstance().registerDefaultBatchHandlers(this.dataBroker);
        super.init();
        initializeElanMacEntryCache();
        registerListener();
    }

    @Override
    @SuppressWarnings("all")
    public void registerListener() {
        try {
            LOG.info("Registering LocalUcastMacListener");
            registerListener(LogicalDatastoreType.OPERATIONAL, getParentWildCardPath());
        } catch (Exception e) {
            LOG.error("Local Ucast Mac register listener error");
        }

    }

    public void deregisterListener() {
        LOG.info("Deregistering LocalUcastMacListener");
        super.close();
    }

    @Override
    protected boolean proceed(final InstanceIdentifier<Node> parent) {
        return IS_NOT_HA_CHILD.test(parent);
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

    private boolean isDelayedMacDelete(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs macRemoved) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        //String macAddress = macRemoved.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());
        String elanName = getElanName(macRemoved);

        PhysAddress phyAddress = new PhysAddress(macRemoved.getMacEntryKey().getValue());
        InstanceIdentifier<MacEntry> elanMacEntryIid = ElanUtils.getMacEntryOperationalDataPath(elanName, phyAddress);
        MacEntry elanMacEntry = getElanMacEntryFromCache(elanMacEntryIid);
        if (elanMacEntry != null && !Objects.equals(elanMacEntry.getSrcTorNodeid(), hwvtepNodeId)) {
            LOG.error("Delayed remove event macIid {} oldElanMac {}", identifier, elanMacEntry);
            return true;
        }
        return false;
    }

    private void deleteElanMacEntry(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs macRemoved) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macRemoved.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());
        String elanName = getElanName(macRemoved);

        PhysAddress phyAddress = new PhysAddress(macRemoved.getMacEntryKey().getValue());
        InstanceIdentifier<MacEntry> elanMacEntryIid = ElanUtils.getMacEntryOperationalDataPath(elanName, phyAddress);
        localMacEntryCache.remove(elanMacEntryIid);
        elanClusterUtils.runOnlyInOwnerNode(hwvtepNodeId + ":" + macAddress + HwvtepHAUtil.L2GW_JOB_KEY,
            "remove elan mac entry from config", () -> {
                return Lists.newArrayList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> tx.delete(elanMacEntryIid)));
            });
    }

    public void removed(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs macRemoved) {
        if (IS_HA_CHILD.test(identifier.firstIdentifierOf(Node.class))) {
            return;
        }
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macRemoved.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());
        LOG.trace("LocalUcastMacs {} removed from {}", macAddress, hwvtepNodeId);
        elanClusterUtils.runOnlyInOwnerNode(hwvtepNodeId + ":" + macAddress + HwvtepHAUtil.L2GW_JOB_KEY,
            "delete local ucast mac from ha node", () -> {
                ResourceBatchingManager.getInstance().delete(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                        identifier);
                return null;
            });


        String elanName = getElanName(macRemoved);

        if (isDelayedMacDelete(identifier, macRemoved)) {
            return;
        }
        deleteElanMacEntry(identifier, macRemoved);
        jobCoordinator.enqueueJob(elanName + HwvtepHAUtil.L2GW_JOB_KEY ,
            () -> {
                L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName,
                        hwvtepNodeId);
                if (elanL2GwDevice == null) {
                    LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache",
                            elanName, hwvtepNodeId);
                    return null;
                }

                LocalUcastMacs macWithoutSrcTorNodeId = localUcastWithoutSrcTorNodeId(macRemoved);
                elanL2GwDevice.removeUcastLocalMac(macWithoutSrcTorNodeId);
                ElanInstance elanInstance = elanInstanceCache.get(elanName).orElse(null);
                elanL2GatewayUtils.unInstallL2GwUcastMacFromL2gwDevices(elanName, elanL2GwDevice,
                        Collections.singletonList(new MacAddress(macAddress.toLowerCase(Locale.getDefault()))));
                elanL2GatewayUtils.unInstallL2GwUcastMacFromElanDpns(elanInstance, elanL2GwDevice,
                        Collections.singletonList(new MacAddress(macAddress.toLowerCase(Locale.getDefault()))));
                return null;
            });
    }

    public InstanceIdentifier<LocalUcastMacs> getOldLocalUcastIid(InstanceIdentifier<Node> oldNodeIid,
                                                                  LocalUcastMacs oldLocalUcastMac) {
        return oldNodeIid.augmentation(HwvtepGlobalAugmentation.class)
                .child(LocalUcastMacs.class, oldLocalUcastMac.key());
    }

    private InstanceIdentifier<Node> torNodeIdFromElanMac(MacEntry originalElanMac) {
        InstanceIdentifier<Node> childNodePath = HwvtepHAUtil.convertToInstanceIdentifier(
                originalElanMac.getSrcTorNodeid());
        if (IS_HA_CHILD.test(childNodePath)) {
            return HwvtepHACache.getInstance().getParent(childNodePath);
        } else {
            return childNodePath;
        }
    }

    public HwvtepPhysicalLocatorRef convertLocatorRef(InstanceIdentifier<Node> nodePath) {
        String nodeId = nodePath.firstKeyOf(Node.class).getNodeId().getValue();
        L2GatewayDevice l2GatewayDevice = l2GatewayCache.getByNodeId(nodeId);
        if (l2GatewayDevice != null && l2GatewayDevice.getTunnelIp() != null) {
            InstanceIdentifier<TerminationPoint> tpPath =
                    HwvtepHAUtil.buildTpId(nodePath, l2GatewayDevice.getTunnelIp().getIpv4Address().getValue());
            return new HwvtepPhysicalLocatorRef(tpPath);
        }
        return null;
    }

    public LocalUcastMacs buildPrevLocalUcast(LocalUcastMacs newLocalUcastMac, MacEntry prevElanMac) {
        LocalUcastMacsBuilder builder = new LocalUcastMacsBuilder(newLocalUcastMac);
        InstanceIdentifier<Node> nodePath = torNodeIdFromElanMac(prevElanMac);

        builder.setLocatorRef(convertLocatorRef(nodePath));
        builder.setLogicalSwitchRef(
                HwvtepHAUtil.convertLogicalSwitchRef(newLocalUcastMac.getLogicalSwitchRef(), nodePath));

        SrcnodeAugmentation srcnodeAugmentation = new SrcnodeAugmentationBuilder()
                .setSrcTorNodeid(prevElanMac.getSrcTorNodeid())
                .build();
        builder.addAugmentation(srcnodeAugmentation);
        builder.setMacEntryUuid(HwvtepHAUtil.getUUid(newLocalUcastMac.getMacEntryKey().getValue()));
        LocalUcastMacsKey key = new LocalUcastMacsKey(builder.getLogicalSwitchRef(), builder.getMacEntryKey());
        builder.withKey(key);
        return builder.build();
    }

    private boolean isMacMoved(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs mac) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String elanName = getElanName(mac);
        PhysAddress phyAddress = new PhysAddress(mac.getMacEntryKey().getValue());
        InstanceIdentifier<MacEntry> iid = ElanUtils.getMacEntryOperationalDataPath(elanName, phyAddress);
        MacEntry prevElanMacEntry = getElanMacEntryFromCache(iid);
        if (prevElanMacEntry != null && !Objects.equals(prevElanMacEntry.getSrcTorNodeid(), hwvtepNodeId)) {
            LocalUcastMacs oldLocalUcast = buildPrevLocalUcast(mac, prevElanMacEntry);
            InstanceIdentifier<Node> oldNodePath = torNodeIdFromElanMac(prevElanMacEntry);
            InstanceIdentifier<LocalUcastMacs> oldLocalUcastPath = getOldLocalUcastIid(oldNodePath, oldLocalUcast);
            LOG.error("LocalUcastMacListener Mac moved {} from to {}", prevElanMacEntry, hwvtepNodeId);
            removed(oldLocalUcastPath, oldLocalUcast);
            scheduler.getScheduledExecutorService().schedule(() -> added(identifier, mac), 15, TimeUnit.SECONDS);
            return true;
        } else {
            LOG.trace("No mac movement original elan mac {} proceeding forward", prevElanMacEntry);
        }
        return false;
    }

    private void updateElanMacInConfigDb(InstanceIdentifier<LocalUcastMacs> identifier, LocalUcastMacs macAdded) {
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macAdded.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());
        String elanName = getElanName(macAdded);

        PhysAddress phyAddress = new PhysAddress(macAdded.getMacEntryKey().getValue());
        MacEntry newElanMac = new MacEntryBuilder()
                .setSrcTorNodeid(hwvtepNodeId)
                .setMacAddress(phyAddress).build();
        InstanceIdentifier<MacEntry> iid = ElanUtils.getMacEntryOperationalDataPath(elanName, phyAddress);
        localMacEntryCache.put(iid, newElanMac);
        elanClusterUtils.runOnlyInOwnerNode(hwvtepNodeId + ":" + macAddress + HwvtepHAUtil.L2GW_JOB_KEY,
            "update elan mac entry", () -> {
                return Lists.newArrayList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> tx.mergeParentStructurePut(iid, newElanMac)));
            });
    }

    private LocalUcastMacs localUcastWithoutSrcTorNodeId(LocalUcastMacs localUcast) {
        return new LocalUcastMacsBuilder(localUcast)
                .setLocatorRef(null)
                .removeAugmentation(SrcnodeAugmentation.class)
                .build();
    }

    public void added(final InstanceIdentifier<LocalUcastMacs> identifier, final LocalUcastMacs macAdded) {
        if (IS_HA_CHILD.test(identifier.firstIdentifierOf(Node.class))) {
            return;
        }
        String hwvtepNodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        String macAddress = macAdded.getMacEntryKey().getValue().toLowerCase(Locale.getDefault());
        String elanName = getElanName(macAdded);
        elanClusterUtils.runOnlyInOwnerNode(hwvtepNodeId + ":" + macAddress + HwvtepHAUtil.L2GW_JOB_KEY,
                "add local ucast mac to ha node", () -> {
                ResourceBatchingManager.getInstance().put(ResourceBatchingManager.ShardResource.CONFIG_TOPOLOGY,
                            identifier, macAdded);
                return null;
            });


        LOG.trace("LocalUcastMacs {} added to {}", macAddress, hwvtepNodeId);

        ElanInstance elan = elanInstanceCache.get(elanName).orElse(null);
        if (elan == null) {
            LOG.warn("Could not find ELAN {} for mac {} being added", elanName, macAddress);
            return;
        }
        if (isMacMoved(identifier, macAdded)) {
            return;
        }
        updateElanMacInConfigDb(identifier, macAdded);
        jobCoordinator.enqueueJob(elanName + HwvtepHAUtil.L2GW_JOB_KEY,
            () -> {
                L2GatewayDevice elanL2GwDevice =
                        ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, hwvtepNodeId);
                if (elanL2GwDevice == null) {
                    LOG.warn("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache",
                            elanName, hwvtepNodeId);
                    return null;
                }
                LocalUcastMacs macWithoutSrcTorNodeId = localUcastWithoutSrcTorNodeId(macAdded);
                elanL2GwDevice.addUcastLocalMac(macWithoutSrcTorNodeId);
                elanL2GatewayUtils.installL2GwUcastMacInElan(elan, elanL2GwDevice,
                        macAddress.toLowerCase(Locale.getDefault()), macWithoutSrcTorNodeId, null);
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
            Collection<? extends DataObjectModification<? extends DataObject>> children = aug.getModifiedChildren();
            if (children == null) {
                return result;
            }
            children.stream()
                .filter(childMod -> getModificationType(childMod) != null)
                .filter(childMod -> childMod.getDataType() == LocalUcastMacs.class)
                .forEach(childMod -> {
                    LocalUcastMacs afterMac = (LocalUcastMacs) childMod.getDataAfter();
                    LocalUcastMacs mac = afterMac != null ? afterMac : (LocalUcastMacs)childMod.getDataBefore();
                    InstanceIdentifier<LocalUcastMacs> iid = parentIid
                        .augmentation(HwvtepGlobalAugmentation.class)
                        .child(LocalUcastMacs.class, mac.key());
                    result.put(iid, (DataObjectModification<LocalUcastMacs>) childMod);
                });
        }
        return result;
    }

    private Set<LocalUcastMacsKey> macSetToKeySet(Set<LocalUcastMacs> macs) {
        if (macs == null) {
            return Collections.emptySet();
        }
        return macs.stream().map(mac -> mac.key()).collect(Collectors.toSet());
    }

    @Override
    protected void onParentAdded(final DataTreeModification<Node> modification) {
        InstanceIdentifier<Node> nodeIid = modification.getRootPath().getRootIdentifier();
        if (IS_PS_NODE_IID.test(nodeIid)) {
            return;
        }
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
            haOpClusteredListener.onGlobalNodeAdd(nodeIid, modification.getRootNode().getDataAfter(), tx);
        }), LOG, "Error processing added parent");
        if (!IS_HA_CHILD.test(nodeIid)) {
            LOG.trace("On parent add {}", nodeIid);
            String hwvtepNodeId = nodeIid.firstKeyOf(Node.class).getNodeId().getValue();
            LOG.info("Delaying Scheduling of Stale Local Ucast Macs Job for {}", hwvtepNodeId);
            localUcastMacDeletedTasks.put(hwvtepNodeId,
                scheduler.getScheduledExecutorService().schedule(() -> {
                    elanClusterUtils.runOnlyInOwnerNode(STALE_LOCAL_UCAST_CLEANUP_JOB + hwvtepNodeId, () -> {
                        txRunner.callWithNewReadOnlyTransactionAndClose(CONFIGURATION, configTx -> {
                            LOG.info("Running Stale Local Ucast Macs delete Job for {}", hwvtepNodeId);
                            try {
                                Set<LocalUcastMacsKey> configMacs = macSetToKeySet(
                                    getMacs(configTx.read(nodeIid).get().orElse(null)));
                                txRunner.callWithNewReadOnlyTransactionAndClose(OPERATIONAL, operTx -> {
                                    try {
                                        Set<LocalUcastMacsKey> operMacs =
                                            macSetToKeySet(getMacs(operTx.read(nodeIid).get().orElse(null)));
                                        Set<LocalUcastMacsKey> staleMacs = Sets.difference(configMacs, operMacs);
                                        staleMacs.forEach(
                                            staleMac -> removed(getMacIid(nodeIid, staleMac),
                                                macFromKey(staleMac)));
                                        localUcastMacDeletedTasks.remove(hwvtepNodeId);
                                    } catch (ExecutionException | InterruptedException e) {
                                        LOG.error("Error while reading mac Oper DS for {}", nodeIid, e);
                                    }
                                });
                            } catch (ExecutionException | InterruptedException e) {
                                LOG.error("Error while reading mac config DS for {}", nodeIid, e);
                            }
                        });
                    });
                }, getStaleLocalUCastCleanUpDelaySecs(), TimeUnit.SECONDS));
        }
    }

    private LocalUcastMacs macFromKey(LocalUcastMacsKey key) {
        LOG.error("Removing stale mac {}", key);
        LocalUcastMacsBuilder builder = new LocalUcastMacsBuilder();
        builder.withKey(key);
        builder.setMacEntryKey(key.getMacEntryKey());
        builder.setLogicalSwitchRef(key.getLogicalSwitchRef());
        return builder.build();
    }

    InstanceIdentifier<LocalUcastMacs> getMacIid(InstanceIdentifier<Node> nodeIid, LocalUcastMacsKey mac) {
        return nodeIid.augmentation(HwvtepGlobalAugmentation.class)
                .child(LocalUcastMacs.class, mac);
    }

    private Set<LocalUcastMacs> getMacs(@Nullable Node node) {
        if (node != null) {
            HwvtepGlobalAugmentation augmentation = node.augmentation(HwvtepGlobalAugmentation.class);
            if (augmentation != null && augmentation.nonnullLocalUcastMacs() != null) {
                return new HashSet<>(augmentation.nonnullLocalUcastMacs().values());
            }
        }
        return Collections.emptySet();
    }

    @Override
    protected void onParentRemoved(InstanceIdentifier<Node> parent) {
        String hwvtepNodeId = parent.firstKeyOf(Node.class).getNodeId().getValue();
        ScheduledFuture localUcastMacDeletedTask = localUcastMacDeletedTasks.remove(hwvtepNodeId);
        if (localUcastMacDeletedTask != null) {
            LOG.info("Cancelling Stale Local Ucast Macs delete Job for {}", hwvtepNodeId);
            localUcastMacDeletedTask.cancel(true);
        }
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

    public long getStaleLocalUCastCleanUpDelaySecs() {
        return elanConfig.getL2gwStaleLocalucastmacsCleanupDelaySecs() != null
                ? elanConfig.getL2gwStaleLocalucastmacsCleanupDelaySecs().longValue() : 600;
    }
}
