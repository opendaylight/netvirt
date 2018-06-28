/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.coe.listeners;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.coe.caches.PodsCache;
import org.opendaylight.netvirt.coe.utils.CoeUtils;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.coe.Pods;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
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
                        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                            InstanceIdentifier<Pods> instanceIdentifier = CoeUtils.getPodUUIDforPodName(
                                        interfaceName, tx);
                            Pods pods = podsCache.get(instanceIdentifier).get();
                            if (pods != null) {
                                IpAddress podIpAddress = pods.getInterface().get(0).getIpAddress();
                                CoeUtils.createVpnInterface(pods.getNetworkNS(), pods, interfaceName, macAddress,
                                        false, tx);
                                CoeUtils.updateElanInterfaceWithStaticMac(macAddress, podIpAddress,
                                        interfaceName, tx);
                                LOG.debug("Bind Kube Proxy Service for {}", interfaceName);
                                bindKubeProxyService(tx, interfaceName);
                            }
                        }), LOG, "Error handling pod configuration for termination-point");
                        return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                    });
            }
        }
    }

    @Override
    public void add(OvsdbTerminationPointAugmentation tpNew) {
        update(null, tpNew);
    }

    private static void bindKubeProxyService(ReadWriteTransaction tx, String interfaceName) {
        int priority = ServiceIndex.getIndex(NwConstants.COE_KUBE_PROXY_SERVICE_NAME,
                NwConstants.COE_KUBE_PROXY_SERVICE_INDEX);
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(
                NwConstants.COE_KUBE_PROXY_TABLE, ++instructionKey));
        BoundServices serviceInfo =
                getBoundServices(String.format("%s.%s", NwConstants.COE_KUBE_PROXY_SERVICE_NAME, interfaceName),
                        ServiceIndex.getIndex(NwConstants.COE_KUBE_PROXY_SERVICE_NAME,
                                NwConstants.COE_KUBE_PROXY_SERVICE_INDEX),
                        priority, NwConstants.COOKIE_COE_KUBE_PROXY_TABLE, instructions);
        InstanceIdentifier<BoundServices> boundServicesInstanceIdentifier =
                CoeUtils.buildKubeProxyServicesIId(interfaceName);
        tx.put(LogicalDatastoreType.CONFIGURATION, boundServicesInstanceIdentifier, serviceInfo,true);
    }

    private static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                                 BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().withKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }
}
