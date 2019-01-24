/*
 * Copyright (c) 2017 - 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.genius.infra.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
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
    private final CoeUtils coeUtils;

    @Inject
    public PodListener(final DataBroker dataBroker, JobCoordinator jobCoordinator, CoeUtils coeUtils) {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.jobCoordinator = jobCoordinator;
        this.coeUtils = coeUtils;
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
        jobCoordinator.enqueueJob(pods.getName(), new PodConfigAddWorker(txRunner, coeUtils,
                instanceIdentifier, pods, podInterface));
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
            jobCoordinator.enqueueJob(podsAfter.getName(), new PodConfigAddWorker(txRunner, coeUtils,
                    instanceIdentifier, podsAfter, podInterfaceAfter));
        }
    }

    private void remove(Pods pods, Interface podInterface) {
        LOG.trace("Pod removed {}", pods);
        if (pods.getNetworkNS() == null || pods.getHostIpAddress() == null) {
            LOG.warn("pod {} deletion without a valid network id", podInterface.getUid().getValue());
            return;
        }

        jobCoordinator.enqueueJob(pods.getName(), new PodConfigRemoveWorker(txRunner, coeUtils, pods));
    }

    private static class PodConfigAddWorker implements Callable<List<ListenableFuture<Void>>> {
        InstanceIdentifier<Pods> podsInstanceIdentifier;
        private final Pods pods;
        private final Interface podInterface;
        private final ManagedNewTransactionRunner txRunner;
        private final CoeUtils coeUtils;

        PodConfigAddWorker(ManagedNewTransactionRunner txRunner, CoeUtils coeUtils,
                           InstanceIdentifier<Pods> podsInstanceIdentifier,
                           Pods pods, Interface podInterface) {
            this.pods = pods;
            this.podInterface = podInterface;
            this.txRunner = txRunner;
            this.podsInstanceIdentifier = podsInstanceIdentifier;
            this.coeUtils = coeUtils;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            LOG.trace("Adding Pod : {}", podInterface);
            String interfaceName = coeUtils.buildInterfaceName(pods.getClusterId().getValue(), pods.getName());
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx ->  {
                String nodeIp = pods.getHostIpAddress().stringValue();
                ElanInstance elanInstance = coeUtils.createElanInstanceForTheFirstPodInTheNetwork(
                        pods.getClusterId().getValue(), nodeIp, podInterface, tx);
                LOG.info("interface creation for pod {}", interfaceName);
                String portInterfaceName = coeUtils.createOfPortInterface(interfaceName, tx);
                LOG.debug("Creating ELAN Interface for pod {}", interfaceName);
                coeUtils.createElanInterface(portInterfaceName,
                        elanInstance.getElanInstanceName(), tx);
            }));
            if (podInterface.getIpAddress() != null) {
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
                    coeUtils.createPodNameToPodUuidMap(interfaceName, podsInstanceIdentifier, tx);
                }));
            }
            return futures;
        }
    }

    private static class PodConfigRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private final Pods pods;
        private final ManagedNewTransactionRunner txRunner;
        private final CoeUtils coeUtils;

        PodConfigRemoveWorker(ManagedNewTransactionRunner txRunner, CoeUtils coeUtils,
                              Pods pods) {
            this.pods = pods;
            this.txRunner = txRunner;
            this.coeUtils = coeUtils;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            String podInterfaceName = coeUtils.buildInterfaceName(pods.getClusterId().getValue(), pods.getName());
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                LOG.trace("Deleting Pod : {}", podInterfaceName);
                LOG.debug("Deleting VPN Interface for pod {}", podInterfaceName);
                coeUtils.deleteVpnInterface(podInterfaceName, tx);
                LOG.debug("Deleting ELAN Interface for pod {}", podInterfaceName);
                coeUtils.deleteElanInterface(podInterfaceName, tx);
                LOG.info("interface deletion for pod {}", podInterfaceName);
                coeUtils.deleteOfPortInterface(podInterfaceName, tx);
                coeUtils.unbindKubeProxyService(podInterfaceName, tx);
                // TODO delete elan-instance if this is the last pod in the host
                // TODO delete vpn-instance if this is the last pod in the network
            }));
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
                coeUtils.deletePodNameToPodUuidMap(podInterfaceName, tx);
            }));
            return futures;
        }
    }
}
