/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.coe.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collection;
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
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Pods>> changes) {
        for (DataTreeModification<Pods> change : changes) {
            final InstanceIdentifier<Pods> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<Pods> mod = change.getRootNode();

            switch (mod.getModificationType()) {
                case DELETE:
                    delete(mod.getDataBefore());
                    break;
                case SUBTREE_MODIFIED:
                    update(mod.getDataBefore(), mod.getDataAfter());
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        add(mod.getDataAfter());
                    } else {
                        update(mod.getDataBefore(), mod.getDataAfter());
                    }
                    break;
                default:
                    LOG.error("Unhandled modification type " + mod.getModificationType());
                    break;
            }
        }
    }

    private void add(Pods podsNew) {
        Interface podInterface = podsNew.getInterface().get(0);

        String network = podInterface.getNetworkId().getValue();
        if (network == null) {
            LOG.warn("pod {} added without a valid network id {}", podInterface.getUid().getValue(), network);
            return;
        }
        // TODO use infrautils caching mechanism to add this info to cache.

        String podInterfaceName = podInterface.getUid().getValue();
        jobCoordinator.enqueueJob(podInterfaceName, new RendererConfigAddWorker(podInterfaceName, podInterface));

    }

    private void update(Pods podsOld, Pods podsNew) {
        // TODO
    }

    private void delete(Pods podsOld) {
         // TODO
    }

    private class RendererConfigAddWorker implements Callable<List<ListenableFuture<Void>>> {
        String podInterfaceName;
        Interface podInterface;

        RendererConfigAddWorker(String podInterfaceName, Interface podInterface) {
            this.podInterfaceName = podInterfaceName;
            this.podInterface = podInterface;
        }

        @Override
        public List<ListenableFuture<Void>> call() {
            LOG.trace("Adding Pod : {}", podInterface);
            WriteTransaction wrtConfigTxn = dataBroker.newWriteOnlyTransaction();
            CoeUtils.createElanInstanceForTheFirstPodInTheNetwork(podInterface, wrtConfigTxn, dataBroker);
            LOG.info("interface creation for pod {}", podInterfaceName);
            String portInterfaceName = CoeUtils.createOfPortInterface(podInterface, wrtConfigTxn, dataBroker);
            LOG.debug("Creating ELAN Interface for pod {}", podInterfaceName);
            CoeUtils.createElanInterface(podInterface, portInterfaceName, wrtConfigTxn);
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(wrtConfigTxn.submit());
            return futures;
        }
    }
}
