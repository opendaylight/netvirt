/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.coe.caches.PodsCache;
import org.opendaylight.netvirt.coe.utils.CoeUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.coe.Pods;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TerminationPointStateListener extends
        AbstractSyncDataTreeChangeListener<OvsdbTerminationPointAugmentation> {
    private static final Logger LOG = LoggerFactory.getLogger(TerminationPointStateListener.class);
    private final JobCoordinator coordinator;
    private final PodsCache  podsCache;
    private final DataTreeEventCallbackRegistrar eventCallbacks;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public TerminationPointStateListener(DataBroker dataBroker, final JobCoordinator coordinator,
                                         PodsCache podsCache, DataTreeEventCallbackRegistrar eventCallbacks) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class).child(Node.class)
                        .child(TerminationPoint.class).augmentation(OvsdbTerminationPointAugmentation.class).build());
        this.coordinator = coordinator;
        this.podsCache = podsCache;
        this.eventCallbacks = eventCallbacks;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public void remove(OvsdbTerminationPointAugmentation tpOld) {
       // DO nothing
    }

    @Override
    public void update(@Nullable OvsdbTerminationPointAugmentation tpOld, OvsdbTerminationPointAugmentation tpNew) {
        LOG.debug("Received Update DataChange Notification for ovsdb termination point {}", tpNew.getName());

        Pair<String, String> tpNewDetails = CoeUtils.getAttachedInterfaceAndMac(tpNew);
        Pair<String, String> tpOldDetails = CoeUtils.getAttachedInterfaceAndMac(tpOld);

        if (!Objects.equals(tpNewDetails, tpOldDetails)) {
            String interfaceName = tpNewDetails.getLeft();
            String macAddress = tpNewDetails.getRight();
            if (interfaceName != null && macAddress != null) {
                LOG.debug("Detected external interface-id {} and attached mac address {} for {}", interfaceName,
                        macAddress, tpNew.getName());
                eventCallbacks.onAddOrUpdate(LogicalDatastoreType.OPERATIONAL,
                    CoeUtils.getPodMetaInstanceId(interfaceName), (unused, alsoUnused) -> {
                        LOG.info("Pod configuration {} detected for termination-point {},"
                            + "proceeding with l2 and l3 configurations", interfaceName, tpNew.getName());
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                            InstanceIdentifier<Pods> instanceIdentifier = CoeUtils.getPodUUIDforPodName(interfaceName,
                                    tx);
                            Pods pods = podsCache.get(instanceIdentifier).get();
                            if (pods != null) {
                                IpAddress podIpAddress = pods.getInterface().get(0).getIpAddress();
                                CoeUtils.createVpnInterface(pods.getNetworkNS(), pods, interfaceName, macAddress,
                                        false, tx);
                                CoeUtils.updateElanInterfaceWithStaticMac(macAddress, podIpAddress, interfaceName, tx);
                            }
                        }));
                        return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                    });
            }
        }
    }

    @Override
    public void add(OvsdbTerminationPointAugmentation tpNew) {
        update(null, tpNew);
    }
}
