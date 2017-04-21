/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_dequeued_waitingKeyQ;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_listener_add;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_listener_delete;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.listener_dependency_resolved;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.wildcard_listener_add;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.wildcard_listener_delete;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WildCardListener implements AutoCloseable, ClusteredDataTreeChangeListener<DataObject> {

    private static final Logger LOG = LoggerFactory.getLogger(WildCardListener.class);
    // copied from LdhDataTreeChangeListenerBase class.
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    InstanceIdentifier wildCardPath;

    //to process events in ORDER, list is made as LinkedBlockingQueue.
    HashMap<InstanceIdentifier, List<DeferedEvent>> waitingForAddIids = new HashMap<>();
    HashMap<InstanceIdentifier, List<DeferedEvent>> waitingForDeleteIids = new HashMap<>();

    List<LdhDataTreeChangeListenerBase> waitingAddListeners = new ArrayList<>();
    List<LdhDataTreeChangeListenerBase> waitingDeleteListeners = new ArrayList<>();

    int numOfDependentIIDs;

    LogicalDatastoreType datastoreType;
    ListenerRegistration<LdhDataTreeChangeListenerBase> listenerRegistration;

    /**
     * Listener based wait mechanism (wildCard).
     * Key: wildCradPath - InstanceIdentifier, value: WildCardListener, proxy listener for dependent-IID-wildCardPath
     *
     * @param wildCardPath : depedent IID wildCard path
     * @param dsType       : datastore type (OPERATIONAL/CONFIGURATION)
     * @param db           : datastore
     * @param listener     : listener, which called this.
     */
    public WildCardListener(InstanceIdentifier wildCardPath, LogicalDatastoreType dsType, final DataBroker db,
                            LdhDataTreeChangeListenerBase listener) {
        this.wildCardPath = wildCardPath;
        datastoreType = dsType;
        registerListener(dsType, db);
        numOfDependentIIDs = 0;
    }

    public int getNumOfDependentIIDs() {
        return numOfDependentIIDs;
    }

    int incrementGetNumOfDependentIIDs() {
        //this particular call is in initiated from synchronized block.
        return ++numOfDependentIIDs;
    }

    int decrementGetNumOfDependentIIDs() {
        //this particular call is in initiated from synchronized block.
        --numOfDependentIIDs;
        if (numOfDependentIIDs < 0) {
            LOG.error("LDH: BUG in register/unregister lister, counter goes to -ve: {}", numOfDependentIIDs);
            numOfDependentIIDs = 0;
        }
        return numOfDependentIIDs;
    }

    /**
     * add to waiting queue which waits for CREATE/UPDATE call on IID.
     *
     * @param iid   : InstanceIdentifier waiting for CREATE/UPDATE call in dataStore
     * @param event : Defer Event which got queued from listener
     */
    void addToWaitingForAddQueue(InstanceIdentifier iid, DeferedEvent event) {
        events_listener_add.inc();
        LOG.trace("LDH: iid is added to waiting listener ADD queue, iid:{}, key: {}", iid, event.key);
        if (waitingForAddIids.isEmpty()) {
            waitingForAddIids.put(iid, new ArrayList<>());
        } else if (waitingForAddIids.get(iid) == null) {
            waitingForAddIids.put(iid, new ArrayList<>());
        }
        waitingForAddIids.get(iid).add(event);
    }

    /**
     * add to waiting queue which waits for DELETE call on IID.
     *
     * @param iid   : InstanceIdentifier waiting for CREATE/UPDATE call in dataStore
     * @param event : Defer Event which got queued from listener
     */
    void addToWaitingForDeleteQueue(InstanceIdentifier iid, DeferedEvent event) {
        events_listener_delete.inc();
        LOG.trace("LDH: iid is added to waiting listener DELETE queue, iid:{}, key: {}", iid, event.key);
        if (waitingForDeleteIids.isEmpty()) {
            waitingForDeleteIids.put(iid, new ArrayList<>());
        } else if (waitingForDeleteIids.get(iid) == null) {
            waitingForDeleteIids.put(iid, new ArrayList<>());
        }
        waitingForDeleteIids.get(iid).add(event);
    }

    void addToWaitingAddListeners(LdhDataTreeChangeListenerBase listener) {
        if (listener != null) {
            waitingAddListeners.add(listener);
        }
    }

    void addToWaitingDeleteListeners(LdhDataTreeChangeListenerBase listener) {
        if (listener != null) {
            waitingDeleteListeners.add(listener);
        }
    }

    //below skip was done intentionally, as registerListener returns Exception
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void registerListener(final LogicalDatastoreType dsType, final DataBroker db) {
        final DataTreeIdentifier treeId = new DataTreeIdentifier<>(dsType, wildCardPath);
        LOG.info("LDH: registered wild card listener, path:{}, dataStoreType: {}", wildCardPath, dsType);
        try {
            TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
            listenerRegistration = looper.loopUntilNoException(() -> db.registerDataTreeChangeListener(treeId, this));
        } catch (final Exception e) {
            LOG.error("LDH: Unable to register listener for wildcardpath: {}, exception: {}", wildCardPath, e);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() throws Exception {
        if (this.listenerRegistration != null) {
            try {
                this.listenerRegistration.close();
            } catch (Exception var2) {
                LOG.error("Error when cleaning up DataTreeChangeListener.", var2);
            }
            this.listenerRegistration = null;
        }
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<DataObject>> changes) {
        for (DataTreeModification<DataObject> change : changes) {
            final InstanceIdentifier<DataObject> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<DataObject> mod = change.getRootNode();

            switch (mod.getModificationType()) {
                case SUBTREE_MODIFIED:
                case WRITE:
                    wildcard_listener_add.inc();
                    LOG.trace("LDH: wildcard listener WRITE/ADD called, key: {}", key);
                    wildCardOnDataTreeChangeCallbackHandling(key, waitingForAddIids, waitingAddListeners, "ADD/UPDATE");
                    break;

                case DELETE:
                    wildcard_listener_delete.inc();
                    LOG.trace("LDH: wildcard listener DELETE called, key: {}", key);
                    wildCardOnDataTreeChangeCallbackHandling(key, waitingForDeleteIids,
                            waitingDeleteListeners, "DELETE");
                    break;
                default:
                    LOG.error("LDH: unknown modification event type:{}, IID:{}", mod.getModificationType(), key);
            }
        }
    }

    private void wildCardOnDataTreeChangeCallbackHandling(InstanceIdentifier key,
                                                          HashMap<InstanceIdentifier, List<DeferedEvent>> waitingIids,
                                                          List<LdhDataTreeChangeListenerBase> waitingListeners,
                                                          String eventType) {

        List<DependencyData> dataModifiedDependentIid = new ArrayList<>();
        List<DeferedEvent> dependencyResolvedEventList = new ArrayList<>();
        ConcurrentLinkedQueue<DeferedEvent> pendingAcutalIIDQueue = null;

        if (waitingIids.containsKey(key)) {
            // remove list of pending events from wildCardListener and modify list merge it back
            //  this way you can avoid synchronize on this list TODO please synchronize remove
            List<DeferedEvent> waitingEvents = waitingIids.remove(key);
            for (DeferedEvent deferedEvent : waitingEvents) {
                dataModifiedDependentIid.addAll(deferedEvent.removeAddDependency(key, datastoreType));
                DeferedEvent eventOneDependentIidListener = null;
                synchronized (deferedEvent.key) {
                    // iterate throught waiting listeners.
                    Iterator<LdhDataTreeChangeListenerBase> waitingListenersIt = waitingListeners.iterator();
                    while (waitingListenersIt.hasNext()) {
                        LdhDataTreeChangeListenerBase listener = waitingListenersIt.next();
                        ConcurrentHashMap<InstanceIdentifier, ConcurrentLinkedQueue<DeferedEvent>>
                                waitingActualIIDMap = listener.getWaitingActualIIDMap();
                        pendingAcutalIIDQueue = waitingActualIIDMap.get(deferedEvent.key);
                        if ((pendingAcutalIIDQueue != null) && (!pendingAcutalIIDQueue.isEmpty())) {
                            eventOneDependentIidListener = pendingAcutalIIDQueue.peek();
                            if (deferedEvent.isDeferTimerBased()) {
                                //TODO: incase required, need implementation here
                                LOG.error("LDH: its NOT expected -- for the same key both timer/listener"
                                        + "events get queued key:{}", deferedEvent.key);
                                //continue execution eventhough its timerbased (needs fix), no harm in continueing
                            }
                            if (eventOneDependentIidListener.areDependenciesResolved()) {
                                pendingAcutalIIDQueue.remove();
                                dependencyResolvedEventList.add(eventOneDependentIidListener);
                                if (pendingAcutalIIDQueue.size() == 0) {
                                    listener.getWaitingActualIIDMap().remove(deferedEvent.key);
                                    events_dequeued_waitingKeyQ.inc();
                                }
                            }

                        } else {
                            LOG.error("LDH: WRITE/UPDATE pending Event removal failed, key:{} DataStoreType:{}",
                                    key, datastoreType);
                        }
                        wildCardListenerDecrUnregister(dataModifiedDependentIid, listener);
                        wildCardListenerWaitingListenerCallback(dependencyResolvedEventList, listener);
                        dependencyResolvedEventList.clear();
                    }
                }
            }
        } else {
            LOG.error("LDH: wildcard Listener " + eventType + "triggered but no key in list of "
                    + "waitingForAddIids, key:{}", key);
        }

    }

    private void wildCardListenerWaitingListenerCallback(List<DeferedEvent> dependencyResolvedEventList,
                                                         LdhDataTreeChangeListenerBase listener) {
        if (!dependencyResolvedEventList.isEmpty() && (listener != null)) {
            for (DeferedEvent dependencyResolvedEvent : dependencyResolvedEventList) {
                LOG.info("LDH: Resolved event key: {} type: {}, eventTime: {}, currentTime: {}, listener:{}",
                        dependencyResolvedEvent.key, dependencyResolvedEvent.getEventType(),
                        new Date(dependencyResolvedEvent.getExpiryTime()), new Date(System.currentTimeMillis()),
                        listener.clazz.getSimpleName());
                listener_dependency_resolved.inc();
                listener.initiateAcutalListenerCallback(dependencyResolvedEvent);
            }
        }
    }

    private void wildCardListenerDecrUnregister(List<DependencyData> dataModifiedDependentIid,
                                                LdhDataTreeChangeListenerBase listener) {
        if ((dataModifiedDependentIid != null) && (listener != null)) {
            //synchronized : register listener is based wildcardpath, so safely modify map.
            listener.expiredEventDecrementUnregisterListerHandling(dataModifiedDependentIid,
                    listener.clazz.getSimpleName());
        }
    }
}
