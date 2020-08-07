/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.jobs.AddL2GwDevicesToTransportZoneJob;
import org.opendaylight.netvirt.elan.l2gw.utils.L2gwZeroDayConfigUtil;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The listener class for ITM transport zone updates.
 */
@Singleton
public class L2GwTransportZoneListener extends AbstractClusteredAsyncDataTreeChangeListener<TransportZone> {
    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");
    private final DataBroker dataBroker;
    private final ItmRpcService itmRpcService;
    private final L2GatewayCache l2GatewayCache;
    private final Map<InstanceIdentifier<TransportZone>, TransportZone> transportZoneMap = new ConcurrentHashMap<>();
    private final HwvtepConfigNodeCache hwvtepConfigNodeCache;
    private final ElanClusterUtils elanClusterUtils;
    private final L2gwZeroDayConfigUtil l2gwZeroDayConfigUtil;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public L2GwTransportZoneListener(final DataBroker dataBroker, final ItmRpcService itmRpcService,
                                     final L2GatewayCache l2GatewayCache,
                                     final HwvtepConfigNodeCache hwvtepConfigNodeCache,
                                     final ElanClusterUtils elanClusterUtils,
                                     final L2gwZeroDayConfigUtil l2gwZeroDayConfigUtil) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(TransportZones.class)
            .child(TransportZone.class), Executors.newListeningSingleThreadExecutor("L2GwTransportZoneListener", LOG));
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.dataBroker = dataBroker;
        this.itmRpcService = itmRpcService;
        this.l2GatewayCache = l2GatewayCache;
        this.hwvtepConfigNodeCache = hwvtepConfigNodeCache;
        this.elanClusterUtils = elanClusterUtils;
        this.l2gwZeroDayConfigUtil = l2gwZeroDayConfigUtil;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    public Collection<TransportZone> getZones() {
        return transportZoneMap.values();
    }

    @Override
    public void remove(InstanceIdentifier<TransportZone> key, TransportZone dataObjectModification) {
        transportZoneMap.remove(key);
        createL2gwZeroDayConfig();
        // do nothing
    }

    @Override
    public void update(InstanceIdentifier<TransportZone> key, TransportZone dataObjectModificationBefore,
                          TransportZone dataObjectModificationAfter) {
        transportZoneMap.put(key, dataObjectModificationAfter);
        createL2gwZeroDayConfig();
        // do nothing
    }

    @Override
    public void add(InstanceIdentifier<TransportZone> key, TransportZone tzNew) {
        transportZoneMap.put(key, tzNew);
        LOG.trace("Received Transport Zone Add Event: {}", tzNew);
        if (tzNew.getTunnelType().equals(TunnelTypeVxlan.class)) {
            AddL2GwDevicesToTransportZoneJob job =
                    new AddL2GwDevicesToTransportZoneJob(itmRpcService, tzNew, l2GatewayCache);
//            jobCoordinator.enqueueJob(job.getJobKey(), job);
            elanClusterUtils.runOnlyInOwnerNode(job.getJobKey(),"Adding L2GW Transport Zone", job);
        }
        createL2gwZeroDayConfig();
    }

    public void createL2gwZeroDayConfig() {
        l2GatewayCache.getAll().stream().forEach(l2GwDevice -> {
            createZeroDayForL2Device(l2GwDevice);
        });
    }

    public void createZeroDayForL2Device(L2GatewayDevice l2GwDevice) {
        if (l2GwDevice.getL2GatewayIds() == null || l2GwDevice.getL2GatewayIds().isEmpty()) {
            LOG.error("Skipping zero day config for {}", l2GwDevice.getHwvtepNodeId());
            return;
        }
        LOG.error("Creating zero day config for {}", l2GwDevice.getHwvtepNodeId());
        InstanceIdentifier<Node> globalIid = HwvtepHAUtil.convertToInstanceIdentifier(
                l2GwDevice.getHwvtepNodeId());
        hwvtepConfigNodeCache.runAfterNodeAvailable(globalIid, () -> {
            elanClusterUtils.runOnlyInOwnerNode(l2GwDevice.getDeviceName(),"Zero day config",() -> {
                return Lists.newArrayList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    l2gwZeroDayConfigUtil.createZeroDayConfig(tx, globalIid, l2GwDevice, getZones());
                }));

            });
        });
    }
}