/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.federation.plugin.spi.IFederationPluginEgress;
import org.opendaylight.federation.service.api.IFederationProducerMgr;
import org.opendaylight.federation.service.api.federationutil.FederationUtils;
import org.opendaylight.federation.service.common.api.EntityFederationMessage;
import org.opendaylight.federation.service.common.api.ListenerData;
import org.opendaylight.netvirt.federation.plugin.filters.FilterResult;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;


public class FederationPluginEgress implements IFederationPluginEgress {

    private final Logger logger;
    private final IFederationProducerMgr producerMgr;
    private final String queueName;
    private final String contextId;
    private final FederatedMappings federatedMappings;
    private final PendingModificationCache<DataTreeModification<? extends DataObject>> pendingModifications = //
            new PendingModificationCache<>();

    private volatile boolean aborted = false;

    static {
        FederationPluginUtils.initYangModules();
    }

    public FederationPluginEgress(final IFederationProducerMgr producerMgr,
            List<FederatedNetworkPair> federatedNetworkPairs, String queueName, String contextId) {
        this.producerMgr = producerMgr;
        this.queueName = queueName;
        this.contextId = contextId;
        logger = FederationUtils.createLogger(queueName, FederationPluginEgress.class);
        federatedMappings = new FederatedMappings(federatedNetworkPairs);
    }

    @Override
    public synchronized void steadyData(String listenerKey,
            Collection<DataTreeModification<? extends DataObject>> dataTreeModifications) {
        if (!aborted) {
            FederationPluginCounters.egress_steady_data.inc();
            processDataTreeModifications(listenerKey, dataTreeModifications, false);
        } else {
            FederationPluginCounters.egress_steady_data_aborted.inc();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public synchronized void fullSyncData(String listenerKey, Optional existingData) {
        if (aborted) {
            FederationPluginCounters.egress_full_sync_aborted.inc();
            return;
        }

        FederationPluginCounters.egress_full_sync.inc();
        Collection dataTreeModifications = createModifications(listenerKey, existingData);
        processDataTreeModifications(listenerKey, dataTreeModifications, true);
    }

    @Override
    public List<ListenerData> getListenersData() {
        List<ListenerData> listenersData = new ArrayList<>();
        for (String listenerKey : FederationPluginUtils.getOrderedListenerKeys()) {
            LogicalDatastoreType datastoreType = FederationPluginUtils.getListenerDatastoreType(listenerKey);
            if (datastoreType == null) {
                logger.error("Failed to get datastore type for listener {}. Ignoring listener key", listenerKey);
                continue;
            }

            InstanceIdentifier<?> instanceIdentifierForListener = FederationPluginUtils
                    .getInstanceIdentifier(listenerKey);
            if (instanceIdentifierForListener == null) {
                logger.error("Failed to get instance identifier of listener for listener key {}. Ignoring listener key",
                        listenerKey);
                continue;
            }

            InstanceIdentifier<?> instanceIdentifierForExistingData = FederationPluginUtils
                    .getParentInstanceIdentifier(listenerKey);
            if (instanceIdentifierForExistingData == null) {
                logger.error(
                        "Failed to get instance identifier of existing data for listener key {}. Ignoring listener key",
                        listenerKey);
                continue;
            }

            ListenerData listenerData = new ListenerData(listenerKey,
                    new DataTreeIdentifier<>(datastoreType, instanceIdentifierForListener),
                    new DataTreeIdentifier<>(datastoreType, instanceIdentifierForExistingData));
            listenersData.add(listenerData);
        }

        logger.debug("Listener keys {}", listenersData);
        return listenersData;
    }

    @Override
    public void cleanup() {
        pendingModifications.cleanup();
    }

    private void processDataTreeModifications(String listenerKey,
            Collection<DataTreeModification<? extends DataObject>> dataTreeModifications, boolean isFullSync) {
        if (dataTreeModifications == null) {
            return;
        }

        for (DataTreeModification<? extends DataObject> dataTreeModification : dataTreeModifications) {
            if (isSpuriousModification(dataTreeModification)) {
                continue;
            }
            processDataTreeModification(listenerKey, dataTreeModification, isFullSync);
        }
    }

    private boolean isSpuriousModification(DataTreeModification<? extends DataObject> dataTreeModification) {
        if (dataTreeModification == null) {
            return true;
        }
        DataObjectModification<? extends DataObject> rootNode = dataTreeModification.getRootNode();
        if (rootNode.getDataBefore() != null && rootNode.getDataAfter() != null
                && rootNode.getDataBefore().equals(rootNode.getDataAfter())) {
            return true;
        }
        return false;
    }

    private <T extends DataObject> void processDataTreeModification(String listenerKey,
            DataTreeModification<T> dataTreeModification, boolean publishInTx) {
        T dataObject = FederationPluginUtils.getDataObjectFromModification(dataTreeModification);
        if (dataObject == null) {
            logger.warn("Failed to get DataObject from {}", dataObject);
            return;
        }

        if (!applyFilter(listenerKey, dataObject, dataTreeModification)) {
            logger.trace("listener {} filtered out", listenerKey);
            return;
        }

        // process queued modifications associated with this modification
        processPendingDataTreeModifications(listenerKey, dataObject, publishInTx);
        // queue deleted modification for future use if required
        if (ModificationType.DELETE.equals(dataTreeModification.getRootNode().getModificationType())
                && PendingModificationCache.isLiberatorKey(listenerKey)) {
            addPendingModification(listenerKey, dataObject, dataTreeModification);
        }
        // publish the modification to the federation
        publishDataTreeModification(listenerKey, dataObject, dataTreeModification, publishInTx);
    }

    private <T extends DataObject> void processPendingDataTreeModifications(String listenerKey, T dataObject,
            boolean publishInTx) {
        Map<String, Collection<DataTreeModification<? extends DataObject>>>
            associatedModifications = removePendingModifications(listenerKey, dataObject);
        if (associatedModifications != null) {
            for (Entry<String, Collection<DataTreeModification<? extends DataObject>>> entry : associatedModifications
                    .entrySet()) {
                for (DataTreeModification<? extends DataObject> modification : entry.getValue()) {
                    processPendingDataTreeModification(entry.getKey(), modification, publishInTx);
                }
            }
        }
    }

    private <T extends DataObject> void processPendingDataTreeModification(String listenerKey,
            DataTreeModification<T> dataTreeModification, boolean publishInTx) {
        T dataObject = FederationPluginUtils.getDataObjectFromModification(dataTreeModification);
        if (dataObject == null) {
            logger.warn("Failed to get DataObject from {}", dataObject);
            return;
        }

        FederationPluginCounters.egress_process_pending_modification.inc();
        publishDataTreeModification(listenerKey, dataObject, dataTreeModification, publishInTx);
    }

    private <T extends DataObject, S extends DataObject> void publishDataTreeModification(String listenerKey,
            S dataObject, DataTreeModification<S> dataTreeModification, boolean publishInTx) {
        T transformedObject = FederationPluginUtils.applyEgressTransformation(listenerKey, dataObject,
                federatedMappings, pendingModifications);
        if (transformedObject == null) {
            FederationPluginCounters.egress_transformation_failed.inc();
            logger.error("Failed to transform {} for listener {}", dataObject, listenerKey);
            return;
        }

        EntityFederationMessage<T> msg = createEntityFederationMsgFromDataObject(listenerKey, transformedObject,
                dataTreeModification);
        FederationPluginCounters.egress_publish_modification.inc();
        logger.trace("Publishing {} for listener {}", transformedObject, listenerKey);
        producerMgr.publishMessage(msg, queueName, contextId);
    }

    private <T extends DataObject> boolean applyFilter(String listenerKey, T dataObject,
            DataTreeModification<T> dataTreeModification) {
        FilterResult filterResult = FederationPluginUtils.applyEgressFilter(listenerKey, dataObject, federatedMappings,
                pendingModifications, dataTreeModification);
        if (filterResult == null) {
            logger.warn("Failed to get FilterResult for {} {}", listenerKey, dataObject);
            return false;
        }

        logger.trace("{} filter result {}", listenerKey, filterResult);
        switch (filterResult) {
            case DENY:
                FederationPluginCounters.egress_filter_result_deny.inc();
                return false;
            case ACCEPT:
                FederationPluginCounters.egress_filter_result_accept.inc();
                return true;
            case QUEUE:
                FederationPluginCounters.egress_filter_result_queue.inc();
                addPendingModification(listenerKey, dataObject, dataTreeModification);
                return false;
            default:
                logger.error("Didn't find a match for the filter result {}", filterResult.toString());
                return false;
        }
    }

    private <T extends DataObject> void addPendingModification(String listenerKey, T dataObject,
            DataTreeModification<? extends DataObject> dataTreeModification) {
        logger.trace("Add pending modification {} listener {}", dataObject, listenerKey);
        pendingModifications.add(dataObject, listenerKey, dataTreeModification);
    }

    private <T extends DataObject> Map<String, Collection<DataTreeModification<? extends DataObject>>>
        removePendingModifications(String listenerKey, T dataObject) {
        if (!PendingModificationCache.isLiberatorKey(listenerKey)) {
            return null;
        }

        logger.trace("Remove pending modifications for listener {}", listenerKey);
        return pendingModifications.remove(dataObject);
    }

    @SuppressWarnings({ "unchecked" })
    private <T extends DataObject, S extends DataObject> EntityFederationMessage<T>
        createEntityFederationMsgFromDataObject(String listenerKey, T dataObject,
                DataTreeModification<S> dataTreeModification) {
        DataObjectModification<S> dataObjectModification = dataTreeModification.getRootNode();
        ModificationType modificationType = dataObjectModification.getModificationType();
        InstanceIdentifier<T> instanceIdentifier = (InstanceIdentifier<T>) FederationPluginUtils
                .getListenerSubtreeInstanceIdentifier(listenerKey);
        LogicalDatastoreType datastoreType = FederationPluginUtils.getListenerDatastoreType(listenerKey);
        EntityFederationMessage<T> msg = createMsgWithRetriesMechanism(dataObject, modificationType, instanceIdentifier,
                datastoreType);
        return msg;
    }

    /**
     * This attempts to workaround
     * https://bugs.opendaylight.org/show_bug.cgi?id=7420.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" , "checkstyle:emptyblock"})
    private <T extends DataObject, S extends DataObject> EntityFederationMessage<T> createMsgWithRetriesMechanism(
            T dataObject, ModificationType modificationType, InstanceIdentifier<T> instanceIdentifier,
            LogicalDatastoreType datastoreType) {
        try {
            EntityFederationMessage msg = new EntityFederationMessage(datastoreType.toString(),
                    modificationType.toString(), null, queueName, instanceIdentifier, dataObject);
            return msg;
        } catch (UncheckedExecutionException t) {
            logger.warn(
                    "Create EntityFederationMessage failed because of frozen class, trying to sleep before recreation",
                    t);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            try {
                EntityFederationMessage msg = new EntityFederationMessage(datastoreType.toString(),
                        modificationType.toString(), null, queueName, instanceIdentifier, dataObject);
                return msg;
            } catch (UncheckedExecutionException t2) {
                logger.warn("Create EntityFederationMessage failed again, "
                        + "trying to sleep for the last time before recreation", t2);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                EntityFederationMessage msg = new EntityFederationMessage(datastoreType.toString(),
                        modificationType.toString(), null, queueName, instanceIdentifier, dataObject);
                return msg;
            }
        }
    }

    @Override
    public synchronized CompletableFuture<Void> abort() {
        aborted = true;
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("rawtypes")
    private <T extends DataObject> Collection<DataTreeModification<T>> createModifications(String listenerKey,
            Optional existingData) {
        if (existingData.isPresent()) {
            return FederationPluginUtils.createModifications(listenerKey, (DataObject) existingData.get());
        }

        FederationPluginCounters.egress_no_existing_data.inc();
        return Collections.emptyList();
    }

}
