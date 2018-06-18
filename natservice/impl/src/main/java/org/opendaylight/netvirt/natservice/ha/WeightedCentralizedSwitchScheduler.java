/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WeightedCentralizedSwitchScheduler implements CentralizedSwitchScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(WeightedCentralizedSwitchScheduler.class);
    private static final Integer INITIAL_SWITCH_WEIGHT = Integer.valueOf(0);

    private final Map<BigInteger,Integer> switchWeightsMap = new ConcurrentHashMap<>();
    private final Map<String,String> subnetIdToRouterPortMap = new ConcurrentHashMap<>();
    private final Map<String,String> subnetIdToElanInstanceMap = new ConcurrentHashMap<>();
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final OdlInterfaceRpcService interfaceManager;
    private final IVpnFootprintService vpnFootprintService;

    @Inject
    public WeightedCentralizedSwitchScheduler(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
            IVpnFootprintService vpnFootprintService) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interfaceManager = interfaceManager;
        this.vpnFootprintService = vpnFootprintService;
    }

    @Override
    public boolean scheduleCentralizedSwitch(Routers router) {
        BigInteger nextSwitchId = getSwitchWithLowestWeight();
        String routerName = router.getRouterName();
        RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
        RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(nextSwitchId).build();
        addToDpnMaps(routerName, router.getSubnetIds(), nextSwitchId);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName), id);
            switchWeightsMap.put(nextSwitchId,switchWeightsMap.get(nextSwitchId) + 1);

        } catch (TransactionCommitFailedException e) {
            LOG.error("ScheduleCentralizedSwitch failed for {}", routerName);
        }
        return true;

    }

    @Override
    public boolean updateCentralizedSwitch(Routers oldRouter, Routers newRouter) {
        String routerName = newRouter.getRouterName();
        List<Uuid> addedSubnetIds = getUpdatedSubnetIds(newRouter.getSubnetIds(), oldRouter.getSubnetIds());
        List<Uuid> deletedSubnetIds = getUpdatedSubnetIds(oldRouter.getSubnetIds(), newRouter.getSubnetIds());
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, newRouter.getRouterName());
        addToDpnMaps(routerName, addedSubnetIds, primarySwitchId);
        deleteFromDpnMaps(routerName, deletedSubnetIds, primarySwitchId);
        return true;
    }

    @Override
    public boolean releaseCentralizedSwitch(Routers router) {
        String routerName = router.getRouterName();
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        deleteFromDpnMaps(routerName, router.getSubnetIds(), primarySwitchId);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName));
            switchWeightsMap.put(primarySwitchId,switchWeightsMap.get(primarySwitchId) - 1);
        } catch (TransactionCommitFailedException e) {
            return false;
        }
        return true;
    }

    private void addToDpnMaps(String routerName, List<Uuid> addedSubnetIds, BigInteger primarySwitchId) {
        if (addedSubnetIds == null || addedSubnetIds.isEmpty()) {
            LOG.debug("addToDpnMaps no subnets associated with {}", routerName);
            return;
        }
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            for (Uuid subnetUuid : addedSubnetIds) {
                String primaryRd = NatUtil.getPrimaryRd(routerName, tx);
                Subnetmap subnetMapEntry = tx.read(LogicalDatastoreType.CONFIGURATION,
                        getSubnetMapIdentifier(subnetUuid)).checkedGet().orNull();
                Uuid routerPortUuid = subnetMapEntry.getRouterInterfacePortId();
                subnetIdToRouterPortMap.put(subnetUuid.getValue(), routerPortUuid.getValue());
                vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, routerName, primaryRd,
                        routerPortUuid.getValue(), null, true);
                NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, routerPortUuid.getValue(),
                        primarySwitchId, tx);
                NatUtil.addToDpnRoutersMap(dataBroker, routerName, routerPortUuid.getValue(),
                        primarySwitchId, tx);
                if (subnetMapEntry.getNetworkType().equals(NetworkAttributes.NetworkType.VLAN)) {
                    String elanInstanceName = subnetMapEntry.getNetworkId().getValue();
                    subnetIdToElanInstanceMap.put(subnetUuid.getValue(), elanInstanceName);
                    NatUtil.addPseudoPortToElanDpn(elanInstanceName, elanInstanceName, primarySwitchId, dataBroker);
                }
            }
        }), LOG, "Error adding subnets to DPN maps for {}", routerName);
    }



    private void deleteFromDpnMaps(String routerName, List<Uuid> deletedSubnetIds, BigInteger primarySwitchId) {
        if (deletedSubnetIds == null || deletedSubnetIds.isEmpty()) {
            LOG.debug("deleteFromDpnMaps no subnets associated with {}", routerName);
            return;
        }
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            String primaryRd = NatUtil.getPrimaryRd(routerName, tx);
            for (Uuid subnetUuid : deletedSubnetIds) {
                String routerPort = subnetIdToRouterPortMap.remove(subnetUuid.getValue());
                if (routerPort == null) {
                    LOG.error("The router port was not found for {}", subnetUuid.getValue());
                    continue;
                }
                vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, routerName, primaryRd,
                        routerPort, null, false);
                NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, primarySwitchId, tx);
                NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, routerName, interfaceManager, tx);
                if (subnetIdToElanInstanceMap.containsKey(subnetUuid.getValue())) {
                    String elanInstanceName = subnetIdToElanInstanceMap.remove(subnetUuid.getValue());
                    NatUtil.removePseudoPortFromElanDpn(elanInstanceName, elanInstanceName, primarySwitchId,
                            dataBroker);
                }
            }
        }), LOG, "Error deleting subnets from DPN maps for {}", routerName);
    }

    @Override
    public boolean addSwitch(BigInteger dpnId) {
        /* Initialize the switch in the map with weight 0 */
        LOG.info("addSwitch: Adding {} dpnId to switchWeightsMap", dpnId);
        switchWeightsMap.put(dpnId, INITIAL_SWITCH_WEIGHT);
        return true;

    }

    @Override
    public boolean removeSwitch(BigInteger dpnId) {
        LOG.info("removeSwitch: Removing {} dpnId to switchWeightsMap", dpnId);
        if (!INITIAL_SWITCH_WEIGHT.equals(switchWeightsMap.get(dpnId))) {
            NaptSwitches naptSwitches = getNaptSwitches(dataBroker);
            for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.getRouterToNaptSwitch()) {
                if (dpnId.equals(routerToNaptSwitch.getPrimarySwitchId())) {
                    Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerToNaptSwitch.getRouterName());
                    releaseCentralizedSwitch(router);
                    switchWeightsMap.remove(dpnId);
                    scheduleCentralizedSwitch(router);
                    break;
                }
            }
        } else {
            switchWeightsMap.remove(dpnId);
        }
        return true;
    }

    public static NaptSwitches getNaptSwitches(DataBroker dataBroker) {
        InstanceIdentifier<NaptSwitches> id = InstanceIdentifier.builder(NaptSwitches.class).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id).orNull();
    }

    private BigInteger getSwitchWithLowestWeight() {
        int lowestWeight = Integer.MAX_VALUE;
        BigInteger nextSwitchId = BigInteger.valueOf(0);
        for (Entry<BigInteger, Integer> entry : switchWeightsMap.entrySet()) {
            BigInteger dpnId = entry.getKey();
            Integer weight = entry.getValue();
            if (lowestWeight > weight) {
                lowestWeight = weight;
                nextSwitchId =  dpnId;
            }
        }
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

    @Override
    public boolean getCentralizedSwitch(String routerName) {
        // TODO Auto-generated method stub
        return false;
    }

    public static List<Uuid> getUpdatedSubnetIds(
            List<Uuid> updatedSubnetIds,
            List<Uuid> currentSubnetIds) {
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