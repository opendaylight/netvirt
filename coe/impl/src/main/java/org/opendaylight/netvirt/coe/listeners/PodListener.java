/*
 * Copyright (c) 2017 - 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.listeners;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.coe.utils.CoeUtils;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.Coe;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.coe.Pods;
import org.opendaylight.yang.gen.v1.urn.opendaylight.coe.northbound.pod.rev170611.pod_attributes.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PodListener implements DataTreeChangeListener<Pods> {

    private static final Logger LOG = LoggerFactory.getLogger(PodListener.class);
    private ListenerRegistration<PodListener> listenerRegistration;
    private final JobCoordinator jobCoordinator;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public PodListener(final DataBroker dataBroker, JobCoordinator jobCoordinator) {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.jobCoordinator = jobCoordinator;
    }

    protected InstanceIdentifier<Pods> getWildCardPath() {
        return InstanceIdentifier.create(Coe.class).child(Pods.class);
    }

    public void registerListener(LogicalDatastoreType dsType, final DataBroker db) {
        final DataTreeIdentifier<Pods> treeId = new DataTreeIdentifier<>(dsType, getWildCardPath());
        listenerRegistration = db.registerDataTreeChangeListener(treeId, PodListener.this);
    }

    @PreDestroy
    public void close() {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } finally {
                listenerRegistration = null;
            }
        }
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Pods>> collection) {
        collection.forEach(
            podsDataTreeModification -> podsDataTreeModification.getRootNode().getModifiedChildren().stream()
                    .filter(
                        dataObjectModification -> dataObjectModification.getDataType().equals(Interface.class))
                        .forEach(dataObjectModification -> onPodInterfacesChanged(
                                (DataObjectModification<Interface>) dataObjectModification,
                                podsDataTreeModification.getRootPath().getRootIdentifier(),
                                podsDataTreeModification.getRootNode()))
        );
    }

    public void onPodInterfacesChanged(final DataObjectModification<Interface> dataObjectModification,
                                       final InstanceIdentifier<Pods> rootIdentifier,
                                       DataObjectModification<Pods> rootNode) {
        Pods pods = rootNode.getDataAfter();
        Pods podsBefore = rootNode.getDataBefore();
        Interface podInterfaceBefore = dataObjectModification.getDataBefore();
        Interface podInterfaceAfter = dataObjectModification.getDataAfter();
        switch (dataObjectModification.getModificationType()) {
            case DELETE:
                remove(podsBefore, podInterfaceBefore);
                break;
            case SUBTREE_MODIFIED:
                update(rootIdentifier, pods, podsBefore, podInterfaceBefore, podInterfaceAfter);
                break;
            case WRITE:
                if (podInterfaceBefore == null) {
                    add(rootIdentifier, pods, podInterfaceAfter);
                } else {
                    update(rootIdentifier, pods, podsBefore, podInterfaceBefore,
                            podInterfaceAfter);
                }
                break;
            default:
                LOG.error("Unhandled Modificiation Type{} for {}", dataObjectModification.getModificationType(),
                        rootIdentifier);
        }
    }

    private void add(InstanceIdentifier<Pods> instanceIdentifier, Pods pods, Interface podInterface) {
        LOG.trace("Pod added {}",pods);
        if (pods.getNetworkNS() == null || pods.getHostIpAddress() == null) {
            LOG.warn("pod {} added with insufficient information to process", pods.getName());
            return;
        }
        jobCoordinator.enqueueJob(pods.getName(), new PodConfigAddWorker(txRunner, instanceIdentifier,
                pods, podInterface));
    }

    private void update(InstanceIdentifier<Pods> instanceIdentifier, Pods podsAfter, Pods podsBefore,
                        Interface podInterfaceBefore, Interface podInterfaceAfter) {
        LOG.trace("Pod updated before :{}, after :{}",podsBefore, podsAfter);
        if (!Objects.equals(podsAfter.getHostIpAddress(), podsBefore.getHostIpAddress())
                || !Objects.equals(podInterfaceBefore.getIpAddress(), podInterfaceAfter.getIpAddress())) {
            //if (podsBefore.getNetworkNS() != null || podsBefore.getHostIpAddress() != null) {
                // Case where pod is moving from one namespace to another
                // issue a delete of all previous configuration, and add the new one.
                //jobCoordinator.enqueueJob(podsAfter.getName(), new PodConfigRemoveWorker(txRunner, podsBefore));
            //}
            jobCoordinator.enqueueJob(podsAfter.getName(), new PodConfigAddWorker(txRunner, instanceIdentifier,
                    podsAfter, podInterfaceAfter));
        }
    }

    private void remove(Pods pods, Interface podInterface) {
        LOG.trace("Pod removed {}", pods);
        if (pods.getNetworkNS() == null || pods.getHostIpAddress() == null) {
            LOG.warn("pod {} deletion without a valid network id", podInterface.getUid().getValue());
            return;
        }

        jobCoordinator.enqueueJob(pods.getName(), new PodConfigRemoveWorker(txRunner, pods));
    }

    private static class PodConfigAddWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<Pods> podsInstanceIdentifier;
        private final Pods pods;
        private final Interface podInterface;
        private final ManagedNewTransactionRunner txRunner;

        PodConfigAddWorker(ManagedNewTransactionRunner txRunner, InstanceIdentifier<Pods> podsInstanceIdentifier,
                           Pods pods, Interface podInterface) {
            this.pods = pods;
            this.podInterface = podInterface;
            this.txRunner = txRunner;
            this.podsInstanceIdentifier = podsInstanceIdentifier;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            LOG.trace("Adding Pod : {}", podInterface);
            String interfaceName = CoeUtils.buildInterfaceName(pods.getNetworkNS(), pods.getName());
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(tx ->  {
                String nodeIp = String.valueOf(pods.getHostIpAddress().getValue());
                ElanInstance elanInstance = CoeUtils.createElanInstanceForTheFirstPodInTheNetwork(
                        pods.getNetworkNS(), nodeIp, podInterface, tx);
                LOG.info("interface creation for pod {}", interfaceName);
                String portInterfaceName = CoeUtils.createOfPortInterface(interfaceName, tx);
                LOG.debug("Creating ELAN Interface for pod {}", interfaceName);
                CoeUtils.createElanInterface(portInterfaceName,
                        elanInstance.getElanInstanceName(), tx);
                LOG.debug("Creating VPN instance for namespace {}", pods.getNetworkNS());
                List<String> rd = Arrays.asList("100:1");
                CoeUtils.createVpnInstance(pods.getNetworkNS(), rd, null, null,
                        VpnInstance.Type.L3, 0, IpVersionChoice.IPV4, tx);
            }));
            if (podInterface.getIpAddress() != null) {
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    CoeUtils.createPodNameToPodUuidMap(interfaceName, podsInstanceIdentifier, tx);
                }));
            }
            return futures;
        }
    }

    private static class PodConfigRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private final Pods pods;
        private final ManagedNewTransactionRunner txRunner;


        PodConfigRemoveWorker(ManagedNewTransactionRunner txRunner,
                              Pods pods) {
            this.pods = pods;
            this.txRunner = txRunner;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            String podInterfaceName = CoeUtils.buildInterfaceName(pods.getNetworkNS(), pods.getName());
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                LOG.trace("Deleting Pod : {}", podInterfaceName);
                LOG.debug("Deleting VPN Interface for pod {}", podInterfaceName);
                CoeUtils.deleteVpnInterface(podInterfaceName, tx);
                LOG.debug("Deleting ELAN Interface for pod {}", podInterfaceName);
                CoeUtils.deleteElanInterface(podInterfaceName, tx);
                LOG.info("interface deletion for pod {}", podInterfaceName);
                CoeUtils.deleteOfPortInterface(podInterfaceName, tx);
                CoeUtils.unbindKubeProxyService(podInterfaceName, tx);
                // TODO delete elan-instance if this is the last pod in the host
                // TODO delete vpn-instance if this is the last pod in the network
            }));
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                CoeUtils.deletePodNameToPodUuidMap(podInterfaceName, tx);
            }));
            return futures;
        }
    }
}
