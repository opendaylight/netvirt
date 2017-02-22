/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.federation.plugin.spi.IFederationPluginIngress;
import org.opendaylight.federation.service.api.federationutil.FederationUtils;
import org.opendaylight.federation.service.common.api.EntityFederationMessage;
import org.opendaylight.netvirt.federation.plugin.filters.FilterResult;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federation.generations.RemoteSiteGenerationInfo;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;


public class FederationPluginIngress implements IFederationPluginIngress {

    private static final int MAX_TRANSACTION_SUBMIT_RETRIES = 2;

    private enum State {
        IDLE, COLLECTING;
    }

    private final Logger logger;
    private final IFederationSubscriptionMgr subscriptionMgr;
    private final DataBroker dataBroker;
    private final FederatedMappings federatedMappings;
    private volatile State state = State.IDLE;
    private volatile boolean aborted = false;

    private final Map<String, Collection<? extends DataObject>> fullSyncModifications = Maps.newConcurrentMap();
    private final String remoteIp;

    static {
        FederationPluginUtils.initYangModules();
    }

    public FederationPluginIngress(final IFederationSubscriptionMgr subscriptionMgr, final DataBroker dataBroker,
            String remoteId, List<FederatedNetworkPair> pairs) {
        this.subscriptionMgr = subscriptionMgr;
        this.dataBroker = dataBroker;
        this.remoteIp = remoteId;
        this.federatedMappings = new FederatedMappings(pairs);
        logger = FederationUtils.createLogger(remoteIp, FederationPluginIngress.class);
        logger.info("Created new NetvirtPluginIngress instance for remoteIp {}", remoteId);
    }

    @Override
    public synchronized void beginFullSync() {
        FederationPluginCounters.ingress_begin_tx.inc();
        logger.info("Changing state to COLLECTING for remoteIP {}", remoteIp);
        state = State.COLLECTING;
        fullSyncModifications.clear();
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public synchronized void endFullSync() {
        if (aborted) {
            FederationPluginCounters.ingress_full_sync_aborted.inc();
            return;
        }

        int generationNumber = 1;
        RemoteSiteGenerationInfo currentGenerationNumber =
                FederationPluginUtils.getGenerationInfoForRemoteSite(dataBroker, remoteIp);
        if (currentGenerationNumber != null) {
            generationNumber = currentGenerationNumber.getGenerationNumber() + 1;
        }
        FederationPluginUtils.updateGenerationInfo(dataBroker, remoteIp, generationNumber);

        FederationPluginCounters.ingress_end_tx.inc();
        try {
            processFullSyncModifications(generationNumber);
            logger.info("Changing state to IDLE for remoteIP {}", remoteIp);
            state = State.IDLE;
        } catch (Throwable t) {
            logger.error("Deciding to call Full Sync again because failed in processing pending modifications", t);
            subscriptionMgr.resubscribe(remoteIp);
        }
    }

    @Override
    public synchronized CompletableFuture<Void> abort() {
        logger.info("Abort Netvirt ingress plugin for remoteIp {}", remoteIp);
        aborted = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public synchronized void consumeMsg(EntityFederationMessage msg) {
        if (aborted) {
            FederationPluginCounters.ingress_consume_msg_aborted.inc();
            return;
        }

        FederationPluginCounters.ingress_consume_msg.inc();
        LogicalDatastoreType datastoreType;
        try {
            datastoreType = LogicalDatastoreType.valueOf(msg.getDataStoreType());
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get datastore type for {}", msg.getDataStoreType());
            return;
        }

        String listenerKey = FederationPluginUtils.getClassListener(msg.getInputClassType(), datastoreType);
        if (listenerKey == null) {
            logger.error("Failed to get listener key for {}", msg.getInputClassType());
            return;
        }

        ModificationType modificationType;
        try {
            modificationType = ModificationType.valueOf(msg.getModificationType());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid modification type {}", msg.getModificationType());
            return;
        }

        DataObject dataObject = msg.getInput();
        if (dataObject == null) {
            logger.error("Failed to create DataObject from msg {}", msg);
            return;
        }

        if (State.COLLECTING.equals(state)) {
            addFullSyncModification(listenerKey, dataObject, modificationType);
        } else {
            try {
                RemoteSiteGenerationInfo currentGenerationNumber =
                        FederationPluginUtils.getGenerationInfoForRemoteSite(dataBroker, remoteIp);
                if (currentGenerationNumber != null && currentGenerationNumber.getGenerationNumber() != null) {
                    processModification(listenerKey, dataObject, modificationType,
                            currentGenerationNumber.getGenerationNumber());
                } else {
                    logger.error("Will call Full Sync again because there is no generation number set");
                    subscriptionMgr.resubscribe(remoteIp);
                }
            } catch (FederationCorruptedStateException e) {
                logger.error("Deciding to call Full Sync again because transactions failed too many times");
                subscriptionMgr.resubscribe(remoteIp);
            }
        }
    }

    @Override
    public void resubscribe() {
        subscriptionMgr.resubscribe(remoteIp);
    }

    @Override
    public String getPluginType() {
        return FederationPluginConstants.PLUGIN_TYPE;
    }

    public synchronized void cleanShadowData() {
        logger.info("Removing all shadow entities for Netvirt ingress plugin for remoteIp {}", remoteIp);
        FederationPluginCleaner.removeOldGenerationFederatedEntities(dataBroker, Integer.MAX_VALUE, remoteIp);
        FederationPluginUtils.deleteGenerationInfo(dataBroker, remoteIp);
    }

    void subnetVpnAssociationUpdated(String subnetId, String vpnId) {
        FederationPluginCounters.ingress_subnet_vpn_association_changed.inc();
        if (federatedMappings.containsConsumerSubnetId(subnetId)) {
            FederationPluginCounters.ingress_federated_subnet_vpn_association_changed.inc();
            logger.info("Deciding to call Full Sync on subnet <-> vpn mapping change for subnet-id {} vpn-id {}",
                    subnetId, vpnId);
            subscriptionMgr.resubscribe(remoteIp);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized <T extends DataObject> void addFullSyncModification(String listenerKey, T modification,
            ModificationType modificationType) {
        Collection<T> listenerModifications = (Collection<T>) fullSyncModifications.get(listenerKey);
        if (listenerModifications == null) {
            listenerModifications = new ArrayList<>();
            fullSyncModifications.put(listenerKey, listenerModifications);
        }

        FederationPluginCounters.ingress_full_sync_modification.inc();
        logger.trace("Add modification type {} listener {} data {}", modificationType, listenerKey, modification);
        listenerModifications.add(modification);
    }

    private void processFullSyncModifications(int generationNumber) throws FederationCorruptedStateException {
        for (String listenerKey : FederationPluginUtils.getOrderedListenerKeys()) {
            Collection<? extends DataObject> listenerModifications = fullSyncModifications.get(listenerKey);
            if (listenerModifications != null) {
                processModifications(listenerKey, listenerModifications, ModificationType.WRITE, generationNumber);
            }
        }

        logger.info("Full sync process finished - generation number {} and remoteIp {}", generationNumber, remoteIp);
        FederationPluginCleaner.removeOldGenerationFederatedEntities(dataBroker, generationNumber, remoteIp);
    }

    private <T extends DataObject> void processModifications(String listenerKey,
            Collection<? extends DataObject> modifications, ModificationType modificationType, int generationNumber)
            throws FederationCorruptedStateException {
        attemptProcessModifications(listenerKey, modifications, modificationType, MAX_TRANSACTION_SUBMIT_RETRIES,
                generationNumber);
    }

    private void attemptProcessModifications(String listenerKey, Collection<? extends DataObject> modifications,
            ModificationType modificationType, int remainingRetries, int generationNumber)
            throws FederationCorruptedStateException {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        for (DataObject modification : modifications) {
            processModification(listenerKey, modification, modificationType, tx, generationNumber);
        }

        try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            if (remainingRetries > 0) {
                logger.error("Process modification failed, retrying.");
                attemptProcessModifications(listenerKey, modifications, modificationType, --remainingRetries,
                        generationNumber);
            } else {
                throw new FederationCorruptedStateException("Failed to commit modification for listener " + listenerKey,
                        e);
            }
        }
    }

    private <T extends DataObject, S extends DataObject> void processModification(String listenerKey, S modification,
            ModificationType modificationType, int generationNumber) throws FederationCorruptedStateException {
        processModification(listenerKey, modification, modificationType, null, generationNumber);
    }

    private <T extends DataObject, S extends DataObject> void processModification(String listenerKey, S modification,
            ModificationType modificationType, WriteTransaction tx, int generationNumber)
            throws FederationCorruptedStateException {
        FederationPluginCounters.ingress_process_modification.inc();
        LogicalDatastoreType datastoreType = FederationPluginUtils.getListenerDatastoreType(listenerKey);
        if (datastoreType == null) {
            logger.error("Failed to get datastore type for {}", listenerKey);
            return;
        }
        if (!applyFilter(listenerKey, modification, modificationType)) {
            logger.trace("listener {} {} filtered out", listenerKey, modification);
            return;
        }

        Pair<InstanceIdentifier<T>, T> transformedModification = FederationPluginUtils
                .applyIngressTransformation(listenerKey, modification, modificationType, generationNumber, remoteIp);
        if (transformedModification == null) {
            logger.error("Failed to apply ingress transformation for {} {}", listenerKey, modification);
            return;
        }
        if (ModificationType.DELETE.equals(modificationType)) {
            logger.trace("Delete modification listener {} identifier {}", listenerKey,
                    transformedModification.getKey());
            deleteModification(datastoreType, transformedModification.getKey(), MAX_TRANSACTION_SUBMIT_RETRIES);
            return;
        }

        logger.trace("Write modification type {} listener {} data {}", modificationType, listenerKey,
                transformedModification);
        if (tx == null) {
            writeModification(datastoreType, transformedModification.getKey(), transformedModification.getValue(),
                    MAX_TRANSACTION_SUBMIT_RETRIES);
        } else {
            writeModification(listenerKey, datastoreType, transformedModification.getKey(),
                    transformedModification.getValue(), tx);
        }
    }

    private <T extends DataObject> boolean applyFilter(String listenerKey, T dataObject,
            ModificationType modificationType) {
        FilterResult filterResult = FederationPluginUtils.applyIngressFilter(listenerKey, dataObject);
        if (filterResult == null) {
            logger.warn("Failed to get FilterResult for {} {}", listenerKey, dataObject);
            return false;
        }

        logger.trace("{} filter result {}", listenerKey, filterResult);
        switch (filterResult) {
            case DENY:
                FederationPluginCounters.ingress_filter_result_deny.inc();
                return false;
            case ACCEPT:
                FederationPluginCounters.ingress_filter_result_accept.inc();
                return true;
            case QUEUE:
                FederationPluginCounters.ingress_filter_result_queue.inc();
                logger.error("Ingress queue not supported");
                return false;
            default:
                break;
        }

        return false;
    }

    // This is a workaround for bug https://bugs.opendaylight.org/show_bug.cgi?id=7420
    @SuppressWarnings("checkstyle:emptyblock")
    private <T extends DataObject> void retryingMerge(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> instanceIdentifier, T dataObject, WriteTransaction tx) {
        try {
            tx.merge(datastoreType, instanceIdentifier, dataObject);
        } catch (UncheckedExecutionException t) {
            logger.warn("Merge failed due to frozen class bug, sleeping and retrying", t);
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
            try {
                tx.merge(datastoreType, instanceIdentifier, dataObject);
            } catch (UncheckedExecutionException t2) {
                logger.warn("Merge failed again due to frozen class bug, sleeping again and retrying", t);
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                }
                tx.merge(datastoreType, instanceIdentifier, dataObject);
            }
        }
    }

    private <T extends DataObject> void writeModification(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> instanceIdentifier, T dataObject, int remainingRetries)
            throws FederationCorruptedStateException {
        FederationPluginCounters.ingress_write_modification.inc();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        retryingMerge(datastoreType, instanceIdentifier, dataObject, tx);

        try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            if (remainingRetries > 0) {
                writeModification(datastoreType, instanceIdentifier, dataObject, --remainingRetries);
            } else {
                throw new FederationCorruptedStateException(
                        "Failed to write modification for " + instanceIdentifier.toString(), e);
            }
        }
    }

    private <T extends DataObject> void writeModification(String listenerKey, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> instanceIdentifier, T dataObject, WriteTransaction tx) {
        FederationPluginCounters.ingress_add_to_tx_modification.inc();
        retryingMerge(datastoreType, instanceIdentifier, dataObject, tx);
    }

    private <T extends DataObject> void deleteModification(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> instanceIdentifier, int remainingRetries) throws FederationCorruptedStateException {
        FederationPluginCounters.ingress_delete_modification.inc();
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(datastoreType, instanceIdentifier);

        try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            if (remainingRetries > 0) {
                deleteModification(datastoreType, instanceIdentifier, --remainingRetries);
            } else {
                throw new FederationCorruptedStateException(
                        "Failed to delete modification for " + instanceIdentifier.toString(), e);
            }
        }
    }
}
