/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
    private final DataBroker dataBroker;
    private final JobCoordinator jobCoordinator;

    @Inject
    public PodListener(final DataBroker dataBroker, JobCoordinator jobCoordinator) {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        this.dataBroker = dataBroker;
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
        collection.stream().forEach(podsDataTreeModification -> {
            podsDataTreeModification.getRootNode().getModifiedChildren().stream().filter(
                dataObjectModification -> dataObjectModification.getDataType().equals(Interface.class))
                    .forEach(dataObjectModification -> onPodInterfacesChanged(
                            (DataObjectModification<Interface>) dataObjectModification,
                            podsDataTreeModification.getRootPath().getRootIdentifier(),
                            podsDataTreeModification.getRootNode()));
        }
        );
    }

    public void onPodInterfacesChanged(final DataObjectModification<Interface> dataObjectModification,
                                       final InstanceIdentifier<Pods> rootIdentifier,
                                       DataObjectModification<Pods> rootNode) {
        {
            Pods pods = rootNode.getDataAfter();
            Interface podInterfaceBefore = dataObjectModification.getDataBefore();
            Interface podInterfaceAfter = dataObjectModification.getDataAfter();
            switch (dataObjectModification.getModificationType()) {
                case DELETE:
                    remove(pods, podInterfaceBefore);
                    break;
                case SUBTREE_MODIFIED:
                    update(pods, podInterfaceBefore, podInterfaceAfter);
                    break;
                case WRITE:
                    if (podInterfaceBefore == null) {
                        add(pods, podInterfaceAfter);
                    } else {
                        update(pods, podInterfaceBefore, podInterfaceAfter);
                    }
                    break;
                default:
                    LOG.error("Unhandled Modificiation Type{} for {}", dataObjectModification.getModificationType(),
                            rootIdentifier);
            }
        }
    }

    private void add(Pods pods, Interface podInterface) {
        if (pods.getNetworkNS() == null) {
            LOG.warn("pod {} added without a valid network id", podInterface.getUid().getValue());
            return;
        }
        // TODO use infrautils caching mechanism to add this info to cache.

        jobCoordinator.enqueueJob(pods.getName(), new RendererConfigAddWorker(pods, podInterface));
    }

    private void update(Pods pods, Interface podInterfaceBefore, Interface podInterfaceAfter) {
        // TODO
    }

    private void remove(Pods pods, Interface podInterface) {
         // TODO
    }

    private class RendererConfigAddWorker implements Callable<List<ListenableFuture<Void>>> {
        Pods pods;
        Interface podInterface;

        RendererConfigAddWorker(Pods pods, Interface podInterface) {
            this.pods = pods;
            this.podInterface = podInterface;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            LOG.trace("Adding Pod : {}", podInterface);
            String interfaceName = CoeUtils.buildInterfaceName(pods.getNetworkNS(), pods.getName());
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            String nodeIp = String.valueOf(pods.getHostIpAddress().getValue());
            ElanInstance elanInstance = CoeUtils.createElanInstanceForTheFirstPodInTheNetwork(
                    pods.getNetworkNS(), nodeIp, podInterface, wrtConfigTxn, dataBroker);
            LOG.info("interface creation for pod {}", interfaceName);
            String portInterfaceName = CoeUtils.createOfPortInterface(interfaceName, podInterface, wrtConfigTxn);
            LOG.debug("Creating ELAN Interface for pod {}", interfaceName);
            CoeUtils.createElanInterface(podInterface, portInterfaceName,
                    elanInstance.getElanInstanceName(), wrtConfigTxn);
            return Collections.singletonList(wrtConfigTxn.submit());
        }
    }
}
