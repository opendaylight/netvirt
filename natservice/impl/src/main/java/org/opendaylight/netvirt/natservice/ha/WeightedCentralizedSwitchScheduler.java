/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.api.NatSwitchCache;
import org.opendaylight.netvirt.natservice.api.NatSwitchCacheListener;
import org.opendaylight.netvirt.natservice.api.SwitchInfo;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WeightedCentralizedSwitchScheduler implements CentralizedSwitchScheduler, NatSwitchCacheListener {
    private static final Logger LOG = LoggerFactory.getLogger(WeightedCentralizedSwitchScheduler.class);
    private static final Integer INITIAL_SWITCH_WEIGHT = Integer.valueOf(0);

    private final Map<String, Map<Uint64,Integer>> providerSwitchWeightsMap = new ConcurrentHashMap<>();
    private final Map<String,String> subnetIdToRouterPortMap = new ConcurrentHashMap<>();
    private final Map<String,String> subnetIdToElanInstanceMap = new ConcurrentHashMap<>();
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final OdlInterfaceRpcService interfaceManager;
    private final IVpnFootprintService vpnFootprintService;
    private final NatserviceConfig.NatMode natMode;

    @Inject
    public WeightedCentralizedSwitchScheduler(final DataBroker dataBroker,
            final OdlInterfaceRpcService interfaceManager,
            final IVpnFootprintService vpnFootprintService, final NatserviceConfig config,
            final NatSwitchCache natSwitchCache) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceManager = interfaceManager;
        this.vpnFootprintService = vpnFootprintService;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatserviceConfig.NatMode.Controller;
        }
        natSwitchCache.register(this);
    }

    @Override
    public boolean scheduleCentralizedSwitch(Routers router) {
        String providerNet = NatUtil.getElanInstancePhysicalNetwok(router.getNetworkId().getValue(),dataBroker);
        Uint64 nextSwitchId = getSwitchWithLowestWeight(providerNet);
        if (Uint64.ZERO.equals(nextSwitchId)) {
            LOG.error("In scheduleCentralizedSwitch, unable to schedule the router {} as there is no available switch.",
                    router.getRouterName());
            return false;
        }

        LOG.info("scheduleCentralizedSwitch for router {} on switch {}", router.getRouterName(), nextSwitchId);
        String routerName = router.getRouterName();
        RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
        RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(nextSwitchId)
                .setEnableSnat(router.isEnableSnat()).build();
        addToDpnMaps(routerName, router.getSubnetIds(), nextSwitchId);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName), id);
            Map<Uint64,Integer> switchWeightMap = providerSwitchWeightsMap.get(providerNet);
            switchWeightMap.put(nextSwitchId,switchWeightMap.get(nextSwitchId) + 1);

        } catch (TransactionCommitFailedException e) {
            LOG.error("ScheduleCentralizedSwitch failed for {}", routerName);
        }
        return true;

    }

    @Override
    public boolean updateCentralizedSwitch(Routers oldRouter, Routers newRouter) {
        LOG.info("updateCentralizedSwitch for router {}", newRouter.getRouterName());
        String routerName = newRouter.getRouterName();
        List<Uuid> addedSubnetIds = getUpdatedSubnetIds(newRouter.getSubnetIds(), oldRouter.getSubnetIds());
        List<Uuid> deletedSubnetIds = getUpdatedSubnetIds(oldRouter.getSubnetIds(), newRouter.getSubnetIds());
        Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, newRouter.getRouterName());
        addToDpnMaps(routerName, addedSubnetIds, primarySwitchId);
        deleteFromDpnMaps(routerName, deletedSubnetIds, primarySwitchId);
        try {
            InstanceIdentifier<RouterToNaptSwitch> id  = NatUtil.buildNaptSwitchIdentifier(routerName);
            RouterToNaptSwitch routerToNaptSwitch = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
            boolean isSnatEnabled = newRouter.isEnableSnat();
            List<ExternalIps> updateExternalIps = newRouter.getExternalIps();
            if (updateExternalIps == null || updateExternalIps.isEmpty()) {
                isSnatEnabled = false;
            }
            if (isSnatEnabled != routerToNaptSwitch.isEnableSnat()) {
                RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                        new RouterToNaptSwitchBuilder(routerToNaptSwitch);
                routerToNaptSwitchBuilder.setEnableSnat(isSnatEnabled);
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        getNaptSwitchesIdentifier(routerName), routerToNaptSwitchBuilder.build());
            }
        } catch (ReadFailedException e) {
            LOG.error("updateCentralizedSwitch ReadFailedException for {}", routerName);
        } catch (TransactionCommitFailedException e) {
            LOG.error("updateCentralizedSwitch TransactionCommitFailedException for {}", routerName);
        }
        return true;
    }

    @Override
    public boolean releaseCentralizedSwitch(Routers router) {
        String providerNet = NatUtil.getElanInstancePhysicalNetwok(router.getNetworkId().getValue(),dataBroker);
        String routerName = router.getRouterName();
        Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (primarySwitchId == null || Uint64.ZERO.equals(primarySwitchId)) {
            LOG.info("releaseCentralizedSwitch: NAPT Switch is not allocated for router {}", router.getRouterName());
            return false;
        }

        LOG.info("releaseCentralizedSwitch for router {} from switch {}", router.getRouterName(), primarySwitchId);
        deleteFromDpnMaps(routerName, router.getSubnetIds(), primarySwitchId);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName));
            Map<Uint64,Integer> switchWeightMap = providerSwitchWeightsMap.get(providerNet);
            switchWeightMap.put(primarySwitchId, switchWeightMap.get(primarySwitchId) - 1);
        } catch (TransactionCommitFailedException e) {
            return false;
        }
        return true;
    }

    private void addToDpnMaps(String routerName, List<Uuid> addedSubnetIds, Uint64 primarySwitchId) {
        if (addedSubnetIds == null || addedSubnetIds.isEmpty()) {
            LOG.debug("addToDpnMaps no subnets associated with {}", routerName);
            return;
        }
        Map<Uuid, Subnetmap> subnetMapEntries = new HashMap<>();
        try {
            String primaryRd = txRunner.applyWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                for (Uuid subnetUuid : addedSubnetIds) {
                    Subnetmap subnetMapEntry = tx.read(getSubnetMapIdentifier(subnetUuid)).get().orNull();
                    subnetMapEntries.put(subnetUuid, subnetMapEntry);
                    Uuid routerPortUuid = subnetMapEntry.getRouterInterfacePortId();
                    subnetIdToRouterPortMap.put(subnetUuid.getValue(), routerPortUuid.getValue());
                }
                return NatUtil.getPrimaryRd(routerName, tx);
            }).get();
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
                for (Uuid subnetUuid : addedSubnetIds) {
                    Subnetmap subnetMapEntry = subnetMapEntries.get(subnetUuid);
                    Uuid routerPortUuid = subnetMapEntry.getRouterInterfacePortId();
                    vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, routerName, primaryRd,
                            routerPortUuid.getValue(), null, true);
                    NatUtil.addToNeutronRouterDpnsMap(routerName, routerPortUuid.getValue(), primarySwitchId, tx);
                    NatUtil.addToDpnRoutersMap(routerName, routerPortUuid.getValue(), primarySwitchId, tx);
                    if (subnetMapEntry.getNetworkType().equals(NetworkAttributes.NetworkType.VLAN)) {
                        String elanInstanceName = subnetMapEntry.getNetworkId().getValue();
                        subnetIdToElanInstanceMap.put(subnetUuid.getValue(), elanInstanceName);
                        NatUtil.addPseudoPortToElanDpn(elanInstanceName, elanInstanceName, primarySwitchId, dataBroker);
                    }
                }
            }), LOG, "Error adding subnets to DPN maps for {}", routerName);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error adding subnets to DPN maps for {}", routerName);
        }
    }

    private void deleteFromDpnMaps(String routerName, List<Uuid> deletedSubnetIds, Uint64 primarySwitchId) {
        if (deletedSubnetIds == null || deletedSubnetIds.isEmpty()) {
            LOG.debug("deleteFromDpnMaps no subnets associated with {}", routerName);
            return;
        }
        try {
            String primaryRd = txRunner.applyWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                tx -> NatUtil.getPrimaryRd(routerName, tx)).get();
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
                for (Uuid subnetUuid : deletedSubnetIds) {
                    String routerPort = subnetIdToRouterPortMap.remove(subnetUuid.getValue());
                    if (routerPort == null) {
                        LOG.error("The router port was not found for {}", subnetUuid.getValue());
                        continue;
                    }
                    vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, routerName, primaryRd,
                            routerPort, null, false);
                    NatUtil.removeFromNeutronRouterDpnsMap(routerName, primarySwitchId, tx);
                    NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, routerName,
                            primarySwitchId, interfaceManager, tx);
                    if (subnetIdToElanInstanceMap.containsKey(subnetUuid.getValue())) {
                        String elanInstanceName = subnetIdToElanInstanceMap.remove(subnetUuid.getValue());
                        NatUtil.removePseudoPortFromElanDpn(elanInstanceName, elanInstanceName, primarySwitchId,
                                dataBroker);
                    }
                }
            }), LOG, "Error deleting subnets from DPN maps for {}", routerName);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error deleting subnets from DPN maps for {}", routerName, e);
        }
    }

    @Override
    public void switchAddedToCache(SwitchInfo switchInfo) {
        boolean scheduleRouters = (providerSwitchWeightsMap.size() == 0) ? true : false;
        for (String providerNet : switchInfo.getProviderNets()) {
            Map<Uint64,Integer> switchWeightMap = providerSwitchWeightsMap.get(providerNet);
            if (providerSwitchWeightsMap.get(providerNet) == null) {
                switchWeightMap = new ConcurrentHashMap<>();
                providerSwitchWeightsMap.put(providerNet, switchWeightMap);
            }
            LOG.info("addSwitch: Adding {} dpnId with provider mapping {} to switchWeightsMap",
                    switchInfo.getDpnId(), providerNet);
            switchWeightMap.put(switchInfo.getDpnId(), INITIAL_SWITCH_WEIGHT);
        }
        if (natMode == NatserviceConfig.NatMode.Conntrack && scheduleRouters) {
            Optional<ExtRouters> optRouters;
            try {
                optRouters = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ExtRouters.class));
            } catch (ReadFailedException e) {
                LOG.error("addSwitch: Error reading external routers", e);
                return;
            }

            if (optRouters.isPresent()) {
                // Get the list of routers and verify if any routers do not have primarySwitch allocated.
                for (Routers router : optRouters.get().getRouters()) {
                    List<ExternalIps> externalIps = router.getExternalIps();
                    if (router.isEnableSnat() && externalIps != null && !externalIps.isEmpty()) {
                        // Check if the primarySwitch is allocated for the router.
                        if (!isPrimarySwitchAllocatedForRouter(router.getRouterName())) {
                            scheduleCentralizedSwitch(router);
                        }
                    }
                }
            }
        }
        return;
    }

    private boolean isPrimarySwitchAllocatedForRouter(String routerName) {
        InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch =
                NatUtil.buildNaptSwitchRouterIdentifier(routerName);
        try {
            RouterToNaptSwitch rtrToNapt = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, routerToNaptSwitch);
            Uint64 dpnId = rtrToNapt.getPrimarySwitchId();
            if (dpnId == null || dpnId.equals(Uint64.ZERO)) {
                return false;
            }
        } catch (ReadFailedException e) {
            LOG.error("isPrimarySwitchAllocatedForRouter: Error reading RouterToNaptSwitch model", e);
            return false;
        }
        return true;
    }

    @Override
    public void switchRemovedFromCache(SwitchInfo switchInfo) {
        Uint64 dpnId = switchInfo.getDpnId();
        LOG.info("removeSwitch: Removing {} dpnId to switchWeightsMap", dpnId);
        for (Map.Entry<String,Map<Uint64,Integer>> providerNet : providerSwitchWeightsMap.entrySet()) {
            Map<Uint64,Integer> switchWeightMap = providerNet.getValue();
            if (natMode == NatserviceConfig.NatMode.Conntrack
                    && !INITIAL_SWITCH_WEIGHT.equals(switchWeightMap.get(dpnId))) {
                NaptSwitches naptSwitches = getNaptSwitches();
                for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.getRouterToNaptSwitch()) {
                    if (dpnId.equals(routerToNaptSwitch.getPrimarySwitchId())) {
                        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerToNaptSwitch.getRouterName());
                        releaseCentralizedSwitch(router);
                        scheduleCentralizedSwitch(router);
                        break;
                    }
                }
            }
            switchWeightMap.remove(dpnId);
        }
        return;
    }

    private NaptSwitches getNaptSwitches() {
        InstanceIdentifier<NaptSwitches> id = InstanceIdentifier.builder(NaptSwitches.class).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id).orNull();
    }

    private Uint64 getSwitchWithLowestWeight(String providerNet) {
        int lowestWeight = Integer.MAX_VALUE;
        Uint64 nextSwitchId = Uint64.valueOf(0);
        Map<Uint64,Integer> switchWeightMap = providerSwitchWeightsMap.get(providerNet);
        if (null == switchWeightMap) {
            LOG.error("No switch have the provider mapping {}", providerNet);
            return nextSwitchId;
        }
        for (Entry<Uint64, Integer> entry : switchWeightMap.entrySet()) {
            Uint64 dpnId = entry.getKey();
            Integer weight = entry.getValue();
            if (lowestWeight > weight) {
                lowestWeight = weight;
                nextSwitchId =  dpnId;
            }
        }
        LOG.info("getSwitchWithLowestWeight: switchWeightsMap {}, returning nextSwitchId {} ",
                providerSwitchWeightsMap, nextSwitchId);
        return nextSwitchId;
    }

    private InstanceIdentifier<RouterToNaptSwitch> getNaptSwitchesIdentifier(String routerName) {
        return InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
    }

    private InstanceIdentifier<Subnetmap> getSubnetMapIdentifier(Uuid subnetId) {
        return InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                new SubnetmapKey(subnetId)).build();
    }

    @Nullable
    public Uint64 getCentralizedSwitch(String routerName) {
        try {
            Optional<RouterToNaptSwitch> naptSwitches = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            getNaptSwitchesIdentifier(routerName));
            if (!naptSwitches.isPresent()) {
                LOG.info("No Napt switch is scheduled for {}", routerName);
                return null;
            }
            return naptSwitches.get().getPrimarySwitchId();
        } catch (ReadFailedException e) {
            LOG.error("Error reading RouterToNaptSwitch model", e);
            return null;
        }
    }

    @Nullable
    private static List<Uuid> getUpdatedSubnetIds(List<Uuid> updatedSubnetIds, List<Uuid> currentSubnetIds) {
        if (updatedSubnetIds == null) {
            return null;
        }
        List<Uuid> newSubnetIds = new ArrayList<>(updatedSubnetIds);
        if (currentSubnetIds == null) {
            return newSubnetIds;
        }
        List<Uuid> origSubnetIds = new ArrayList<>(currentSubnetIds);
        for (Iterator<Uuid> iterator = newSubnetIds.iterator(); iterator.hasNext();) {
            Uuid updatedSubnetId = iterator.next();
            for (Uuid currentSubnetId : origSubnetIds) {
                if (updatedSubnetId.getValue().equals(currentSubnetId.getValue())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return newSubnetIds;
    }
}
