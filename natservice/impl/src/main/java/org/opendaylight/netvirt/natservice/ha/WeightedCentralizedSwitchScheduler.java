/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchExternalIds;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WeightedCentralizedSwitchScheduler implements CentralizedSwitchScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(WeightedCentralizedSwitchScheduler.class);
    private static final String ODL_BASE_WEIGHT = "odl_base_weight";

    private final Map<BigInteger,Integer> switchWeightsMap = new ConcurrentHashMap<>();
    private final Map<String,String> subnetIdToRouterPortMap = new ConcurrentHashMap<>();
    private final Map<String,String> subnetIdToElanInstanceMap = new ConcurrentHashMap<>();
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService interfaceManager;
    private final IVpnFootprintService vpnFootprintService;

    @Inject
    public WeightedCentralizedSwitchScheduler(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
            IVpnFootprintService vpnFootprintService) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.vpnFootprintService = vpnFootprintService;
    }

    @Override
    public boolean scheduleCentralizedSwitch(Routers router) {
        BigInteger nextSwitchId = getSwitchWithHighestWeight();
        if (nextSwitchId == BigInteger.valueOf(0)) {
            LOG.error("In scheduleCentralizedSwitch, unable to schedule the router {} as there is no available switch.",
                    router.getRouterName());
            return false;
        }

        LOG.info("scheduleCentralizedSwitch for router {} on switch {}", router.getRouterName(), nextSwitchId);
        String routerName = router.getRouterName();
        RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
        RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(nextSwitchId).build();
        addToDpnMaps(routerName, router.getSubnetIds(), nextSwitchId);
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName), id);
            switchWeightsMap.put(nextSwitchId,switchWeightsMap.get(nextSwitchId) - 1);

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
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, newRouter.getRouterName());
        addToDpnMaps(routerName, addedSubnetIds, primarySwitchId);
        deleteFromDpnMaps(routerName, deletedSubnetIds, primarySwitchId);
        return true;
    }

    @Override
    public boolean releaseCentralizedSwitch(Routers router) {
        String routerName = router.getRouterName();
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        LOG.info("releaseCentralizedSwitch for router {} from switch {}", router.getRouterName(), primarySwitchId);
        deleteFromDpnMaps(routerName, router.getSubnetIds(), primarySwitchId);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName));
            switchWeightsMap.put(primarySwitchId,switchWeightsMap.get(primarySwitchId) + 1);
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
        String primaryRd = NatUtil.getPrimaryRd(dataBroker, routerName);
        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
        for (Uuid subnetUuid : addedSubnetIds) {
            try {
                Subnetmap subnetMapEntry = SingleTransactionDataBroker.syncRead(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, getSubnetMapIdentifier(subnetUuid));
                Uuid routerPortUuid = subnetMapEntry.getRouterInterfacePortId();
                subnetIdToRouterPortMap.put(subnetUuid.getValue(), routerPortUuid.getValue());
                vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, routerName, primaryRd,
                        routerPortUuid.getValue(), null, true);
                NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, routerPortUuid.getValue(),
                        primarySwitchId, writeOperTxn);
                NatUtil.addToDpnRoutersMap(dataBroker, routerName, routerPortUuid.getValue(),
                        primarySwitchId, writeOperTxn);
                if (subnetMapEntry.getNetworkType().equals(NetworkAttributes.NetworkType.VLAN)) {
                    String elanInstanceName = subnetMapEntry.getNetworkId().getValue();
                    subnetIdToElanInstanceMap.put(subnetUuid.getValue(), elanInstanceName);
                    NatUtil.addPseudoPortToElanDpn(elanInstanceName, elanInstanceName, primarySwitchId, dataBroker);
                }
            } catch (ReadFailedException e) {
                LOG.error("addToDpnMaps failed for {}", routerName);
            }
        }
        writeOperTxn.submit();
    }



    private void deleteFromDpnMaps(String routerName, List<Uuid> deletedSubnetIds, BigInteger primarySwitchId) {
        if (deletedSubnetIds == null || deletedSubnetIds.isEmpty()) {
            LOG.debug("deleteFromDpnMaps no subnets associated with {}", routerName);
            return;
        }
        WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
        String primaryRd = NatUtil.getPrimaryRd(dataBroker, routerName);
        for (Uuid subnetUuid :deletedSubnetIds) {
            String routerPort = subnetIdToRouterPortMap.remove(subnetUuid.getValue());
            if (routerPort == null) {
                LOG.error("The router port was not found for {}", subnetUuid.getValue());
                continue;
            }
            vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, routerName, primaryRd,
                    routerPort, null, false);
            NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, primarySwitchId, writeOperTxn);
            NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, routerName, interfaceManager,
                    writeOperTxn);
            if (subnetIdToElanInstanceMap.containsKey(subnetUuid.getValue())) {
                String elanInstanceName = subnetIdToElanInstanceMap.remove(subnetUuid.getValue());
                NatUtil.removePseudoPortFromElanDpn(elanInstanceName, elanInstanceName, primarySwitchId, dataBroker);
            }
        }
        writeOperTxn.submit();
    }

    @Override
    public boolean addSwitch(BigInteger dpnId) {
        /* Initialize the switch in the map with weight 0 */
        LOG.info("addSwitch: Adding {} dpnId to switchWeightsMap", dpnId);
        boolean scheduleRouters = (switchWeightsMap.size() == 0) ? true : false;
        switchWeightsMap.put(dpnId, getDpnBaseWeight(dpnId));

        if (scheduleRouters) {
            ExtRouters routers;
            try {
                routers = SingleTransactionDataBroker.syncRead(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        InstanceIdentifier.create(ExtRouters.class));
            } catch (ReadFailedException e) {
                LOG.error("addSwitch: Error reading external routers", e);
                return false;
            }

            // Get the list of routers and verify if any routers do not have primarySwitch allocated.
            for (Routers router : routers.getRouters()) {
                List<ExternalIps> externalIps = router.getExternalIps();
                if (router.isEnableSnat() && externalIps != null && !externalIps.isEmpty()) {
                    // Check if the primarySwitch is allocated for the router.
                    if (!isPrimarySwitchAllocatedForRouter(router.getRouterName())) {
                        scheduleCentralizedSwitch(router);
                    }
                }
            }
        }
        return true;
    }

    private boolean isPrimarySwitchAllocatedForRouter(String routerName) {
        InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch =
                NatUtil.buildNaptSwitchRouterIdentifier(routerName);
        try {
            RouterToNaptSwitch rtrToNapt = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, routerToNaptSwitch);
            BigInteger dpnId = rtrToNapt.getPrimarySwitchId();
            if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
                return false;
            }
        } catch (ReadFailedException e) {
            LOG.error("isPrimarySwitchAllocatedForRouter: Error reading RouterToNaptSwitch model", e);
            return false;
        }
        return true;
    }

    @Override
    public boolean removeSwitch(BigInteger dpnId) {
        LOG.info("removeSwitch: Removing {} dpnId to switchWeightsMap", dpnId);
        NaptSwitches naptSwitches = getNaptSwitches(dataBroker);
        for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.getRouterToNaptSwitch()) {
            if (dpnId.equals(routerToNaptSwitch.getPrimarySwitchId())) {
                Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerToNaptSwitch.getRouterName());
                releaseCentralizedSwitch(router);
                switchWeightsMap.remove(dpnId);
                scheduleCentralizedSwitch(router);
            }
        }
        switchWeightsMap.remove(dpnId);
        return true;
    }

    public static NaptSwitches getNaptSwitches(DataBroker dataBroker) {
        InstanceIdentifier<NaptSwitches> id = InstanceIdentifier.builder(NaptSwitches.class).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id).orNull();
    }

    private BigInteger getSwitchWithHighestWeight() {
        int highestWeight = Integer.MIN_VALUE;
        BigInteger nextSwitchId = BigInteger.valueOf(0);
        for (Entry<BigInteger, Integer> entry : switchWeightsMap.entrySet()) {
            BigInteger dpnId = entry.getKey();
            Integer weight = entry.getValue();
            if (highestWeight < weight) {
                highestWeight = weight;
                nextSwitchId =  dpnId;
            }
        }
        LOG.info("getSwitchWithHighestWeight: switchWeightsMap {}, returning nextSwitchId {} ",
                switchWeightsMap, nextSwitchId);
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

    private Optional<Node> getPortsNode(BigInteger dpnId) {
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));

        Optional<BridgeRefEntry> bridgeRefEntry =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath);
        if (!bridgeRefEntry.isPresent()) {
            return Optional.absent();
        }

        InstanceIdentifier<Node> nodeId =
                bridgeRefEntry.get().getBridgeReference().getValue().firstIdentifierOf(Node.class);

        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, nodeId);
    }

    private Integer getDpnBaseWeight(BigInteger dpId) {
        return getPortsNode(dpId).toJavaUtil().map(node -> getOpenvswitchExternalIds(node, ODL_BASE_WEIGHT))
                .orElse(Integer.valueOf(0));
    }

    private Integer getOpenvswitchExternalIds(Node node, String key) {
        OvsdbNodeAugmentation ovsdbNode = node.getAugmentation(OvsdbNodeAugmentation.class);
        if (ovsdbNode == null) {
            Optional<Node> nodeFromReadOvsdbNode = readOvsdbNode(node);
            if (nodeFromReadOvsdbNode.isPresent()) {
                ovsdbNode = nodeFromReadOvsdbNode.get().getAugmentation(OvsdbNodeAugmentation.class);
            }
        }

        if (ovsdbNode != null && ovsdbNode.getOpenvswitchOtherConfigs() != null) {
            for (OpenvswitchExternalIds openvswitchExternalIds : ovsdbNode.getOpenvswitchExternalIds()) {
                if (openvswitchExternalIds.getExternalIdKey().equals(key)) {
                    return Integer.valueOf(openvswitchExternalIds.getExternalIdValue());
                }
            }
        }

        return Integer.valueOf(0);
    }

    @Nonnull
    private Optional<Node> readOvsdbNode(Node bridgeNode) {
        OvsdbBridgeAugmentation bridgeAugmentation = extractBridgeAugmentation(bridgeNode);
        if (bridgeAugmentation != null) {
            InstanceIdentifier<Node> ovsdbNodeIid =
                    (InstanceIdentifier<Node>) bridgeAugmentation.getManagedBy().getValue();
            return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, ovsdbNodeIid);
        }
        return Optional.absent();

    }

    private OvsdbBridgeAugmentation extractBridgeAugmentation(Node node) {
        if (node == null) {
            return null;
        }
        return node.getAugmentation(OvsdbBridgeAugmentation.class);
    }
}
