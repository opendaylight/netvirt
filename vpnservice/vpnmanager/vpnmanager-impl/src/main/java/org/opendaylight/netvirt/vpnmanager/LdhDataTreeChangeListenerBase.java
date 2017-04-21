/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.dependent_iid_add_resolved_transision;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.dependent_iid_del_resolved_transision;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.event_dependent_iids_resolved_transistion;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_dequeued;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_dequeued_waitingKeyQ;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_enqueued_waitingKeyQ;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_expired_timer;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_freed_listener;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_listener_add;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_listener_delete;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_listenerbased;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_registerd_listener;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_reuse_listener;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.events_unregisterd_listener;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.listener_dependency_resolved;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.nonApplication_queued_events;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.nonApplication_resolved_events;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.timer_dependency_resolved;
import static org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase.Counters.wildcard_listener_add;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.felix.service.command.CommandSession;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.TaskRetryLooper;
import org.opendaylight.infrautils.counters.api.OccurenceCounter;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LdhDataTreeChangeListenerBase<T extends DataObject, K extends DataTreeChangeListener>
        implements DataTreeChangeListener<T>, AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(LdhDataTreeChangeListenerBase.class);
    //default wait time for type of IID, in milliseconds.
    public static final long DEFER_EVENT_IID_ADD_WAIT_TIME = 1000L;
    public static final long DEFER_EVENT_IID_UPDATE_WAIT_TIME = 1000L;
    public static final long DEFER_EVENT_IID_REMOVE_WAIT_TIME = 1000L;
    //Defer DS wait, to avoid re-ordering
    static final int DEFER_EVENT_DS_RETRY_COUNT = 100;

    // MD-SAL event analysis and resultant values
    static final int DEFER_EVENT_HAS_TO_BE_SUPRESSED = 1;
    static final int DEFER_EVENT_HAS_TO_BE_QUEUED = 2;
    static final int DEFER_EVENT_HAS_TO_BE_PROCESSED = 3;

    static final int DEFER_EVENT_WILDCARD_LISTENER_FAIL = -1;
    static final int DEFER_EVENT_WILDCARD_LISTENER_SUCCESS = 1;
    static final int DEFER_EVENT_WILDCARD_LISTENER_RESOLVED = 2;
    private static final int LDH_HANDLER_THREAD_POOL_CORE_SIZE = 1;
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;
    static long DEFER_DELAY_TIME_RESOLVED_EVENT = 2000L;
    // listeners regisstered from application. Used in executing pendingTasks (listenerDependencyHandlerThread)
    static List<LdhDataTreeChangeListenerBase> listeners = Collections.synchronizedList(new ArrayList<>());
    private static ScheduledExecutorService ldhDeferEventExecutor = new ScheduledThreadPoolExecutor(
            LDH_HANDLER_THREAD_POOL_CORE_SIZE);
    // Listener Dependency handler.
    private static Runnable listenerDependencyHandlerThread = new Runnable() {
        public void run() {
            for (LdhDataTreeChangeListenerBase listener : listeners) {
                if ((listener != null) && (listener.hasPendingTasksToRun())) {
                    LOG.trace("LDH: Timer Task, time:{}, listener: {}", new Date(), listener.clazz.getSimpleName());
                    ldhDeferEventExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.runPendingTasks(listener);
                        }
                    });
                }
            }
        }
    };
    protected final Class<T> clazz;
    private final Class<K> eventClazz;
    /* Listener based wait mechanism (wildCard).
     * Key: wildCradPath - InstanceIdentifier, value: WildCardListener, proxy listener for dependent-IID-wildCardPath
     */
    public ConcurrentHashMap<InstanceIdentifier, WildCardListener> wildCardListenerMap = new ConcurrentHashMap<>();
    //Listener Dependency Helper - Events are queued from applications to Listener Infra.
    Queue<DeferedEvent> listenerDependencyHelperQueue;
    /* Events are read from "listenerDependencyHelperQueue" and PUT to map based on actual-IID and event.
     * Key: listenerKEY - InstanceIdentifier, value: deferedEvent List  => IID's the listener processing depends on.
     */
    ConcurrentHashMap<InstanceIdentifier, ConcurrentLinkedQueue<DeferedEvent>> waitingActualIIDMap =
            new ConcurrentHashMap<>();
    /* There is a delay between listener gets trigger onDataTreeChanged and actual availability for READ operation
     * to avoid looping of events between listener and defer-infra, delay of DEFER_DELAY_TIME_RESOLVED_EVENT introduced
     */
    boolean enableDelayInResolvingEvent = true;
    ConcurrentLinkedQueue<TimedResolvedDeferEvent> delayResolvedDeferedEventQueue = new ConcurrentLinkedQueue<>();
    private ListenerRegistration<K> listenerRegistration;

    // instantiation of executor service on class initialization.
    {
        ldhDeferEventExecutor.scheduleAtFixedRate(listenerDependencyHandlerThread, 1000L, 1000L,
                TimeUnit.MILLISECONDS);
    }

    //constructor
    public LdhDataTreeChangeListenerBase(Class<T> clazz, Class<K> eventClazz) {
        this.clazz = Preconditions.checkNotNull(clazz, "Class can not be null!");
        this.eventClazz = Preconditions.checkNotNull(eventClazz, "eventClazz can not be null!");
        this.listenerDependencyHelperQueue = new ConcurrentLinkedQueue<DeferedEvent>();
        listeners.add(this);
    }

    /**
     * Debugging purpose: prints counters and list of events pending in respective queues/maps per listener.
     **/
    public static void ldhDebug(String detail, CommandSession session) {
        session.getConsole().println("-----------------------------------------------------------------------");
        session.getConsole().println(String.format("                      %s   %14s  ", "Counter Name", "Value"));
        session.getConsole().println("-----------------------------------------------------------------------");
        session.getConsole().println(String.format("events dequeued from LDH              : %s",
                events_dequeued.get()));
        session.getConsole().println(String.format("events enqueued to IID Q-Key MAP      : %s",
                events_enqueued_waitingKeyQ.get()));
        session.getConsole().println(String.format("events dequeued from IID Q-Key MAP    : %s",
                events_dequeued_waitingKeyQ.get()));
        session.getConsole().println(String.format("Listener: resolved during transistion : %s",
                event_dependent_iids_resolved_transistion.get()));
        session.getConsole().println(String.format("Listener: based events                : %s",
                events_listenerbased.get()));
        session.getConsole().println(String.format("Listener: registered Listener         : %s",
                events_registerd_listener.get()));
        session.getConsole().println(String.format("Listener: reUsed Listener             : %s",
                events_reuse_listener.get()));
        session.getConsole().println(String.format("Listener: freed Listener              : %s",
                events_freed_listener.get()));
        session.getConsole().println(String.format("Listener: Unregister Listener         : %s",
                events_unregisterd_listener.get()));
        session.getConsole().println(String.format("Listener: Events for Add call back    : %s",
                events_listener_add.get()));
        session.getConsole().println(String.format("Listener: Events for Delete call back : %s",
                events_listener_delete.get()));
        session.getConsole().println(String.format("Listener: Add call back received      : %s",
                wildcard_listener_add.get()));
        session.getConsole().println(String.format("Listener: Delete call back received   : %s",
                events_listener_delete.get()));
        session.getConsole().println(String.format("Timer Expired events                  : %s",
                events_expired_timer.get()));
        session.getConsole().println(String.format("Timer Dependency Resolved events      : %s",
                timer_dependency_resolved.get()));
        session.getConsole().println(String.format("Listener Dependency Resolved events   : %s",
                listener_dependency_resolved.get()));
        session.getConsole().println(String.format("Infra queued (ordering) Events        : %s",
                nonApplication_queued_events.get()));
        session.getConsole().println(String.format("Infra resolved (ordering) Events      : %s",
                nonApplication_resolved_events.get()));
        session.getConsole().println(String.format("Dependent IIDs-ADD resolved transition: %s",
                dependent_iid_add_resolved_transision.get()));
        session.getConsole().println(String.format("Dependent IIDs-DEL resolved transition: %s",
                dependent_iid_del_resolved_transision.get()));

        session.getConsole().println("-----------------------------------------------------------------------");

        if (detail != null) {
            // print detailed contents
            session.getConsole().println("------------------Listener Dependency Helper Current State-------------");
            for (LdhDataTreeChangeListenerBase listener : listeners) {
                ConcurrentHashMap<InstanceIdentifier, ConcurrentLinkedQueue<DeferedEvent>> waitingActualIIDMap =
                        listener.getWaitingActualIIDMap();
                session.getConsole().println(String.format("------------------%s-------------",
                        listener.clazz.getSimpleName()));
                /* IID-List of DeferedEvents */
                session.getConsole().println(String.format("         %s   %14s  ", "LDH Read Queue size",
                        listener.getListenerDependencyHelperQueue().size()));

                session.getConsole().println(String.format("         %s   %14s  ", "Waiting Events size",
                        waitingActualIIDMap.size()));
                session.getConsole().println("Events waiting for depenency Resolution (both listener/timer)");
                if (waitingActualIIDMap.size() > 0) {
                    for (InstanceIdentifier iid : waitingActualIIDMap.keySet()) {
                        ConcurrentLinkedQueue iidDeferedEventsQueue = waitingActualIIDMap.get(iid);
                        Iterator<DeferedEvent> keyWaitEventIt = iidDeferedEventsQueue.iterator();
                        while (keyWaitEventIt.hasNext()) {
                            DeferedEvent keyWaitEventDefer = keyWaitEventIt.next();
                            session.getConsole().println(String.format("Waiting Event: %s, queuedTime: %s", iid,
                                    keyWaitEventDefer.getQueuedTime()));
                            List<DependencyData> keyWaitEventDeferDependentIids = keyWaitEventDefer.getDependentIIDs();
                            for (DependencyData keyWaitEventDependentIid : keyWaitEventDeferDependentIids) {
                                session.getConsole().println(String.format("\tDepedent IID : %s",
                                        keyWaitEventDependentIid.getIid()));
                            }
                        }
                    }

                } else {
                    session.getConsole().println("There are NO Events waiting for depenency Resolution "
                            + "(both listener/timer)");
                }

                /* IID-Listener */
                ConcurrentHashMap<InstanceIdentifier, WildCardListener> wildCardListenerMap
                        = listener.getWildCardListenerMap();
                session.getConsole().println("Events waiting for depenency Resolution in WildCard Listener)");
                if (wildCardListenerMap.size() > 0) {
                    for (InstanceIdentifier wildCardPath : wildCardListenerMap.keySet()) {
                        WildCardListener keyWildListener = wildCardListenerMap.get(wildCardPath);
                        session.getConsole().println(String.format("wildCardPath: %s, listener: %s", wildCardPath,
                                keyWildListener.getClass().getSimpleName()));
                        session.getConsole().println(String.format("wildCard Path: %s, listener: %s",
                                keyWildListener.wildCardPath,
                                keyWildListener.getNumOfDependentIIDs()));
                        /* Waiting Add IIDs in WildCardListener (waitingForAddIids)*/
                        HashMap<InstanceIdentifier, List<DeferedEvent>> waitingForAddIids =
                                keyWildListener.waitingForAddIids;
                        for (InstanceIdentifier iidWaitingAddListener : waitingForAddIids.keySet()) {
                            List<DeferedEvent> keyWaitingAddIidList = waitingForAddIids.get(iidWaitingAddListener);
                            for (DeferedEvent deferedEvent : keyWaitingAddIidList) {
                                session.getConsole().println(String.format("Waiting add Events: %s, queuedTime: %s",
                                        deferedEvent.key, deferedEvent.getQueuedTime()));
                            }
                        }

                        /* Waiting Delete IIDs in WildCardListener (waitingForDeleteIids)*/
                        HashMap<InstanceIdentifier, List<DeferedEvent>> waitingForDeleteIids =
                                keyWildListener.waitingForDeleteIids;
                        for (InstanceIdentifier iidWaitingDeleteListener : waitingForDeleteIids.keySet()) {
                            List<DeferedEvent> keyWaitingDeleteIidList =
                                    waitingForDeleteIids.get(iidWaitingDeleteListener);
                            for (DeferedEvent deferedEvent : keyWaitingDeleteIidList) {
                                session.getConsole().println(String.format("Waiting delete Events: %s, queuedTime: %s",
                                        deferedEvent.key, deferedEvent.getQueuedTime()));
                            }
                        }
                    }

                    /* IID's which are */
                    //TODO: listIID's shall contain bothe ADDed/Deleted List as well... (?)
                } else {
                    session.getConsole().println("There are NO Listeners registered with IID");
                }
            } /* End of Listener loop*/
            session.getConsole().println("-----------------------------------------------------------------------");
        }
    }

    public Queue<DeferedEvent> getListenerDependencyHelperQueue() {
        return listenerDependencyHelperQueue;
    }

    public ConcurrentHashMap<InstanceIdentifier, ConcurrentLinkedQueue<DeferedEvent>> getWaitingActualIIDMap() {
        return waitingActualIIDMap;
    }

    public ConcurrentHashMap<InstanceIdentifier, WildCardListener> getWildCardListenerMap() {
        return wildCardListenerMap;
    }

    //below skip was done intentionally, as registerListener returns Exception
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void registerListener(LogicalDatastoreType dsType, final DataBroker db) {
        final DataTreeIdentifier<T> treeId = new DataTreeIdentifier<>(dsType, getWildCardPath());
        try {
            TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
            listenerRegistration = looper
                    .loopUntilNoException(() -> db.registerDataTreeChangeListener(treeId, getDataTreeChangeListener()));
        } catch (final Exception e) {
            LOG.warn("{}: Data Tree Change listener listenerRegistration failed.", eventClazz.getName());
            LOG.debug("{}: Data Tree Change listener listenerRegistration failed: {}", eventClazz.getName(), e);
            throw new IllegalStateException(eventClazz.getName() + "{}startup failed. System needs restart.", e);
        }
    }

    /**
     * This method is to register listener for each dependent IID using wildCardPath passed along with dependentID.
     * updates mapping with wildCardPathKey and listener. It also maintains, two additional maps "waiting for ADD and
     * DElETE Defer Events" map. These are used in onDataTreeChanged callback processing. In case the wild card listener
     * is already present, increases ref count.
     *
     * @param deferedEvent :: Event enqueud from Listener/Application which had dependedent IID's required for procesing
     * @param listener     :: MD-SAL listeners extending
     * @return int :: returns success or failure based on the listenerRegistration.
     **/
    int deferEventwithRegisterListener(DeferedEvent deferedEvent, LdhDataTreeChangeListenerBase listener) {
        // Listener Based wait mechanism
        List<DependencyData> dependentIIDList = deferedEvent.getDependentIIDs();
        int totalDeferedIIDs = dependentIIDList.size();
        if ((dependentIIDList == null) || (dependentIIDList.isEmpty())) {
            LOG.trace("LDH: dependent IID is empty for listener key:{}, thread yields here", deferedEvent.key);
            return DEFER_EVENT_WILDCARD_LISTENER_FAIL;
        }

        Iterator<DependencyData> dependentIIDListIt = dependentIIDList.iterator();
        while (dependentIIDListIt.hasNext()) {
            DependencyData dependentIID = dependentIIDListIt.next();
            boolean resolvedDuringDefer = false;
            //check whether there is already registered listerner for the dTidWildCardPath
            synchronized (dependentIID.wildCardPath) {
                WildCardListener wildCradListener = wildCardListenerMap.get(dependentIID.wildCardPath);
                if (wildCradListener == null) {
                    events_registerd_listener.inc();
                    wildCradListener = new WildCardListener(dependentIID.wildCardPath, dependentIID.getDsType(),
                            deferedEvent.getDb(), listener);
                    wildCardListenerMap.put(dependentIID.wildCardPath, wildCradListener);
                    LOG.trace("LDH: registered listener for deferEvent with key: {} \t, wildcard path: {}\t, "
                                    + "DS Type: {}\t number of IID's waiting for wildCardListener are:{} ",
                            dependentIID.iid, dependentIID.wildCardPath, dependentIID.getDsType(),
                            wildCradListener.getNumOfDependentIIDs());
                } else {
                    /* WHY BELOW MD-SAL read is required (?)
                      Read MD-SAL and to find whether requested criteria by application is met before re-using
                      This MD-SAL operation is required mainly to avoid race condition (below)
                      At time T,   application requested for DeferEvent (with WildCardPath-w, dependentIID-d).
                      At time T+x, LDH thread wokeUP and saw DeferEvent and noticied WildCardPath-w already registered.
                                   re-use existing Listener. Due to Re-Use of existing Listener: we dont receive the
                                   current snapshot of Data-Tree.
                      Assume, between T and T+x: the requested event was satisfied.
                         In this scenario, as we Re-Use existing Listener and timeGap of 'x' between application & infra
                         there are chances of missing event and we wait till timer expiry and never trigger application
                         call-back.
                      MD-SAL Read is required only if wildCardPath and iid are different.
                     */
                    //dependentIID.wildCardPath and dependentIID.iid are different, verify using read.
                    if (!(dependentIID.wildCardPath.toString().equals(dependentIID.iid.toString()))) {
                        Optional<?> rdOptional = Optional.absent();
                        try (ReadOnlyTransaction tx = deferedEvent.getDb().newReadOnlyTransaction()) {
                            rdOptional = tx.read(dependentIID.getDsType(), dependentIID.iid).get();
                        } catch (final InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                        if (rdOptional.isPresent() && dependentIID.expectData) {
                            // depenedency-add got resolved during LDH queueing
                            resolvedDuringDefer = true;
                            totalDeferedIIDs--;
                            dependent_iid_add_resolved_transision.inc();
                        } else if ((!rdOptional.isPresent()) && (!dependentIID.expectData)) {
                            // dependency-delete got resolved during LDH Queueing
                            resolvedDuringDefer = true;
                            totalDeferedIIDs--;
                            dependent_iid_del_resolved_transision.inc();
                        }
                    }
                    if (!resolvedDuringDefer) {
                        events_reuse_listener.inc();
                        LOG.trace("LDH: reusing listener for deferEvent with key: {} \t, wildcard path: {}\t, DS Type: "
                                        + "{}\t number of IID's waiting for wildCardListener are:{} ", dependentIID.iid,
                                dependentIID.wildCardPath, dependentIID.getDsType(),
                                wildCradListener.getNumOfDependentIIDs());
                    }
                }
                if (!resolvedDuringDefer) {
                    wildCradListener = wildCardListenerMap.get(dependentIID.wildCardPath);
                    wildCradListener.incrementGetNumOfDependentIIDs();
                    if (dependentIID.expectData) {
                        wildCradListener.addToWaitingAddListeners(listener);
                        wildCradListener.addToWaitingForAddQueue(dependentIID.iid, deferedEvent);
                    } else {
                        //add to delete wait queue
                        wildCradListener.addToWaitingForDeleteQueue(dependentIID.iid, deferedEvent);
                        wildCradListener.addToWaitingDeleteListeners(listener);
                    }
                }
            }
        }
        if (totalDeferedIIDs == 0) {
            //defered events resolved during transistion, nothing registered now
            return DEFER_EVENT_WILDCARD_LISTENER_RESOLVED;
        }
        return DEFER_EVENT_WILDCARD_LISTENER_SUCCESS;
    }

    /**
     * This method is to un-register listener for each dependent IID which are registered under wildCard Listener.
     * this can be called from onDataTreeChanged (or) expired timer. In case, if there are multiple IID's are present
     * it will decrement reference count.
     *
     * @param dependentIIDList :: list of dependent IID's for which a listener depends on processing an MD-SAL callback.
     * @param liName           :: listener name
     **/
    //below skip was done intentionally, as expiredEventDecrementUnregisterListerHandling returns Exception
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void expiredEventDecrementUnregisterListerHandling(List<DependencyData> dependentIIDList, String liName) {
        /*  Listener based Timer expiry handling
            close listeners for each dependent IID's wildCardPath (if count is zero)
         */
        if ((dependentIIDList == null) || (dependentIIDList.size() == 0)) {
            LOG.error("LDH: expiredEventUnregisterListerHandling, received null dependentIID list size:{}"
                    + "listener: {}", dependentIIDList.size(), liName);
            return;
        }
        for (DependencyData dependentIID : dependentIIDList) {
            //synchronized : register listener is based wildcardpath, so safely modify map.
            synchronized (dependentIID.wildCardPath) {
                WildCardListener expiredEventListener = wildCardListenerMap.get(dependentIID.wildCardPath);
                if (expiredEventListener != null) {
                    expiredEventListener.decrementGetNumOfDependentIIDs();
                    LOG.trace("LDH: decr/unregister Listener for wildcardPath:{}, listener: {}, "
                                    + "#of pending dependentIID's: {}", dependentIID.wildCardPath, liName,
                            expiredEventListener.getNumOfDependentIIDs());
                    if (expiredEventListener.getNumOfDependentIIDs() == 0) {
                        //unRegister Listener
                        try {
                            LOG.error("LDH: Unregister Listener for wildcardPath:{}, listener: {}",
                                    dependentIID.wildCardPath, liName);
                            //remove wildCardListener from ListenerMap.
                            wildCardListenerMap.remove(dependentIID.wildCardPath);
                            expiredEventListener.close();
                            events_unregisterd_listener.inc();
                        } catch (final Exception e) {
                            LOG.error("LDH: Failed to Unregister Listener for wildcardPath:{}, listener: {}",
                                    dependentIID.wildCardPath, liName, e);
                        }
                    } else {
                        events_freed_listener.inc();
                    }
                } else {
                    LOG.error("LDH: Expired Event: but listener is NULL. listener Name:{}, dependentIID: {}",
                            liName, dependentIID.toString());
                }
            }
        }
    }

    /**
     * This task takes listener as argument, polls ListenerDependencyHelperQueue for any DeferedEvents. If there are
     * dequeues and adds them to listener specific waitingActualIIDEvent map. If the defer is listener based, registers
     * listener. checks if there any resolved events, initiates callback.
     *
     * @param listener :: listener extending {@link LdhDataTreeChangeListenerBase}
     **/
    protected void runPendingTasks(LdhDataTreeChangeListenerBase listener) {
        long currentTime = System.currentTimeMillis();
        String liName = listener.clazz.getSimpleName();

        /** Handling of newly Queued events **/
        while (listenerDependencyHelperQueue.size() > 0) {
            //Dequeue deferedEvent from Queue (listenerDependencyHelperQueue).
            DeferedEvent event = null;
            try {
                LOG.error("LDH: dequeuing event");
                event = listenerDependencyHelperQueue.remove();
                events_dequeued.inc();
            } catch (final NoSuchElementException e) {
                LOG.error("LDH: dequeuing element from listenerDependencyHelperQueue failed", e);
                //if there are no elements exist, just break the loop (may be other thread processed it).
                break;
            }
            LOG.trace("LDH: event key: {} type: {}, eventTime: {}, currentTime: {}, dependentIIDs: {}, listener: {}",
                    event.key, event.getEventType(), new Date(event.getExpiryTime()), new Date(currentTime),
                    event.getDependentIIDs().size(), liName);

            //dependent IID are present ?
            //Event is generated within Listener (not-application), to avoid re-ordering.
            if (((event.getDependentIIDs() != null) || (event.getDependentIIDs().size() > 0))
                    || ((event.getDependentIIDs().size() == 0) && (event.getDb() == null)
                    && (event.isDeferTimerBased()))) {
                // every event dequeued has to be mainted in map
                synchronized (event.key) {
                    ConcurrentLinkedQueue<DeferedEvent> actualIIDQueue = waitingActualIIDMap.get(event.key);
                    if ((actualIIDQueue == null) || (actualIIDQueue.size() == 0)) {
                        actualIIDQueue = new ConcurrentLinkedQueue<>();
                        waitingActualIIDMap.put(event.key, actualIIDQueue);
                    }
                    try {
                        // enqueue the event to IID Queue.
                        actualIIDQueue.add(event);
                        events_enqueued_waitingKeyQ.inc();
                    } catch (final IllegalStateException | NullPointerException e) {
                        LOG.error("LDH: IllegalState/NullPointer while enqueuing the "
                                        + "event key: {} type: {}, eventTime: {}, currentTime: {}, listener: {}",
                                event.key, event.getEventType(), new Date(event.getExpiryTime()), new Date(currentTime),
                                liName, e);
                    }
                }

                if (!event.isDeferTimerBased()) {
                    events_listenerbased.inc();
                    LOG.trace("LDH: Listener based event differing, key:{}, listener: {}", event.key, liName);
                    // Listener based event: Register Listener.
                    int retDeferEventwithRegisterListener = deferEventwithRegisterListener(event, listener);
                    if (retDeferEventwithRegisterListener == DEFER_EVENT_WILDCARD_LISTENER_FAIL) {
                        continue;
                    } else if (retDeferEventwithRegisterListener == DEFER_EVENT_WILDCARD_LISTENER_RESOLVED) {
                        //events resolved during transistion, nothing registered now
                        event_dependent_iids_resolved_transistion.inc();
                        initiateAcutalListenerCallback(event);
                    }
                }
            } else {
                LOG.error("LDH: Event Queued with NULL dependencies event key: {} type: {}, eventTime: {}, "
                                + "currentTime: {}, listener: {}",
                       event.key, event.getEventType(), new Date(event.getExpiryTime()), new Date(currentTime), liName);
            }
        }

    /* check whether delayResolvedDeferedEventQueue has elements which are waiting beyond
     * DEFER_DELAY_TIME_RESOLVED_EVENTif wait time beyond,
     * DEFER_DELAY_TIME_RESOLVED_EVENT -> execute call back for those events.
     */
        if (enableDelayInResolvingEvent) {
            if (!delayResolvedDeferedEventQueue.isEmpty()) {
                TimedResolvedDeferEvent timedResolvedDeferEvent = delayResolvedDeferedEventQueue.peek();
                if ((System.currentTimeMillis() - timedResolvedDeferEvent.queuedTimestamp)
                        > DEFER_DELAY_TIME_RESOLVED_EVENT) {
                    executeListenerCallback(delayResolvedDeferedEventQueue.remove().resolvedEvent);
                }
            }
        }

    /* Handling of existing Timer based events from MAP AND timer-expired events(timer and listener based)*
     * below loop shall be iterator based, as in iterator its allowed to modify working set.
     */
        Iterator<HashMap.Entry<InstanceIdentifier, ConcurrentLinkedQueue<DeferedEvent>>> waitingActualIidMapIt =
                waitingActualIIDMap.entrySet().iterator();
        while (waitingActualIidMapIt.hasNext()) {
            HashMap.Entry<InstanceIdentifier, ConcurrentLinkedQueue<DeferedEvent>> waitingIidMapEntry =
                    waitingActualIidMapIt.next();
            DeferedEvent pendingActualIIDEvent = waitingIidMapEntry.getValue().peek();
            if (pendingActualIIDEvent == null) {
                synchronized (waitingIidMapEntry.getKey()) {
                    waitingIidMapEntry.getValue().remove();
                    events_dequeued_waitingKeyQ.inc();
                }
            } else if ((pendingActualIIDEvent != null) && (pendingActualIIDEvent.isDeferTimerBased())) {
                if (currentTime
                        > (pendingActualIIDEvent.getLastProcessedTime()
                                + pendingActualIIDEvent.getWaitBetweenDependencyCheckTime())) {
                    pendingActualIIDEvent.setLastProcessedTime(System.currentTimeMillis());
                    // Try for dependency resolution.
                    List<DependencyData> unresolvedDependencyDataList = null;
                    unresolvedDependencyDataList =
                            hasDependencyResolved(pendingActualIIDEvent.getDependentIIDs(),
                                    pendingActualIIDEvent.getDb());
                    if (unresolvedDependencyDataList.size() == 0) {
                        // get the event, clear element for IID Trigger actual IID call back
                        DeferedEvent dependencyResolvedEvent = null;
                        synchronized (waitingIidMapEntry.getKey()) {
                            dependencyResolvedEvent = waitingIidMapEntry.getValue().remove();
                            events_dequeued_waitingKeyQ.inc();
                        }
                        if (dependencyResolvedEvent != null) {
                            initiateAcutalListenerCallback(dependencyResolvedEvent);
                            timer_dependency_resolved.inc();
                            LOG.error("Resolved event key: {} type: {}, eventTime: {}, currentTime: {}, listener: {}",
                                    dependencyResolvedEvent.key, dependencyResolvedEvent.getEventType(),
                                    new Date(dependencyResolvedEvent.getExpiryTime()), new Date(currentTime), liName);
                        }
                    } else {
                        synchronized (waitingIidMapEntry.getKey()) {
                            ConcurrentLinkedQueue<DeferedEvent> pendingAcutalIIDQueue =
                                    waitingActualIIDMap.get(waitingIidMapEntry.getKey());
                            if ((pendingAcutalIIDQueue != null) && (pendingAcutalIIDQueue.size() != 0)) {
                                DeferedEvent waitingActualIIDEvent = pendingAcutalIIDQueue.peek();
                                waitingActualIIDEvent.getDependentIIDs().clear();
                                waitingActualIIDEvent.getDependentIIDs().addAll(unresolvedDependencyDataList);
                            }
                        }
                    }
                }
            }
            //expiry check based on waitingTime and retry_count.
            if (currentTime > pendingActualIIDEvent.getExpiryTime()) {
                LOG.trace("LDH: Expired event key: {} type: {}, event Expiry Time: {}, QueuedTime: {}, listener: {}",
                       pendingActualIIDEvent.key, pendingActualIIDEvent.getEventType(),
                       new Date(pendingActualIIDEvent.getExpiryTime()), new Date(pendingActualIIDEvent.getQueuedTime()),
                       liName);
                //Dequeue and supress this event, retry_count/expirytime elapsed
                DeferedEvent expiredActualIIDEvent = null;
                synchronized (waitingIidMapEntry.getKey()) {
                    //TODO: re-analyse: is lock sufficient here?? take a lock at start of FOR loop??
                    ConcurrentLinkedQueue<DeferedEvent> pendingAcutalIIDQueue =
                            waitingActualIIDMap.get(waitingIidMapEntry.getKey());
                    Iterator<DeferedEvent> pendingAcutalIIDQueueIt = pendingAcutalIIDQueue.iterator();
                    // we know, there is one element which is expired. Looking for any more elements which are expired
                    while (pendingAcutalIIDQueueIt.hasNext()) {
                        expiredActualIIDEvent = pendingAcutalIIDQueueIt.next();
                        if (expiredActualIIDEvent == null) {
                            LOG.error("LDH: unexpected null value in expired events iterator key:{}, DataStoreType: {}",
                                    expiredActualIIDEvent.key, expiredActualIIDEvent.getEventType());
                            break;
                        }
                        if (currentTime > expiredActualIIDEvent.getExpiryTime()) {
                            events_dequeued_waitingKeyQ.inc();

                            events_expired_timer.inc();
                            pendingAcutalIIDQueueIt.remove();
                            LOG.error("LDH: Expired event, removing key: {} type: {}, event Queued Time: {}, "
                                            + "currentTime: {}, listener: {}", expiredActualIIDEvent.key,
                                    expiredActualIIDEvent.getEventType(),
                                    new Date(expiredActualIIDEvent.getExpiryTime()),
                                    new Date(expiredActualIIDEvent.getQueuedTime()), liName);
                            if ((expiredActualIIDEvent.isDeferTimerBased())) {
                                //no proessing required (dequeuing is enough)
                                if ((expiredActualIIDEvent.getDb() == null)
                                        && (expiredActualIIDEvent.getDependentIIDs().size() == 0)) {
                                    timer_dependency_resolved.inc();
                                    nonApplication_resolved_events.inc();
                                    initiateAcutalListenerCallback(expiredActualIIDEvent);
                                }
                            } else {
                                //Listener based handling, whether to unregister
                                if (expiredActualIIDEvent.getDependentIIDs() != null) {
                                    expiredEventDecrementUnregisterListerHandling(
                                            expiredActualIIDEvent.getDependentIIDs(), liName);
                                } else {
                                    LOG.error("LDH: expiredActualIIDEvent has null dependency list, key:{}, "
                                            + "listener: {}", expiredActualIIDEvent.key, liName);
                                }
                            }
                        } else {
                            //There are no more elements in Queue which are expired. No need to iterate further
                            break;
                        }
                    }
                    if ((pendingAcutalIIDQueue != null) || (pendingAcutalIIDQueue.size() == 0)) {
                        waitingActualIIDMap.remove(waitingIidMapEntry.getKey(), pendingAcutalIIDQueue);
                    }
                }
            }
        }

    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<T>> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        for (DataTreeModification<T> change : changes) {
            final InstanceIdentifier<T> key = change.getRootPath().getRootIdentifier();
            final DataObjectModification<T> mod = change.getRootNode();
            /*
                isEventToBeProcessed has to be verified here.
             */
            switch (mod.getModificationType()) {
                case DELETE:
                    if (isEventToBeProcessed(key, null, null,
                            DeferedEvent.EventType.REMOVE, DEFER_EVENT_IID_REMOVE_WAIT_TIME,
                            DEFER_EVENT_DS_RETRY_COUNT)) {
                        //process Remove event here.
                        remove(key, mod.getDataBefore());
                    } else {
                        LOG.error("LDH: Event Remove either Queued/Suppressed, key:{}, eventType:{}", key, "REMOVE");
                    }
                    break;
                case SUBTREE_MODIFIED:
                    if (isEventToBeProcessed(key, mod.getDataBefore(), mod.getDataAfter(),
                            DeferedEvent.EventType.UPDATE, DEFER_EVENT_IID_UPDATE_WAIT_TIME,
                            DEFER_EVENT_DS_RETRY_COUNT)) {
                        //process Update event here
                        update(key, mod.getDataBefore(), mod.getDataAfter());
                    } else {
                        LOG.error("LDH: Event Update either Queued/Suppressed, key:{}, eventType:{}", key, "MODIFIED");
                    }
                    break;
                case WRITE:
                    if (mod.getDataBefore() == null) {
                        if (isEventToBeProcessed(key, null, mod.getDataAfter(),
                                DeferedEvent.EventType.ADD, DEFER_EVENT_IID_ADD_WAIT_TIME,
                                DEFER_EVENT_DS_RETRY_COUNT)) {
                            //process ADD event here
                            add(key, mod.getDataAfter());
                        } else {
                            // Queue the event, which is already taken care inside isEventToBeProcessed
                            LOG.error("LDH: Event Add either Queued/Suppressed, key:{}, eventType:{}", key, "WRITE");
                        }
                    } else {
                        if (isEventToBeProcessed(key, mod.getDataBefore(), mod.getDataAfter(),
                                DeferedEvent.EventType.UPDATE, DEFER_EVENT_IID_UPDATE_WAIT_TIME,
                                DEFER_EVENT_DS_RETRY_COUNT)) {
                            //process Update event here
                            update(key, mod.getDataBefore(), mod.getDataAfter());
                        } else {
                            LOG.error("LDH: Event Update either Queued/Suppressed, key:{}, eventType:{}",
                                    key, "UPDATE");
                        }
                    }
                    break;
                default:
                    // FIXME: May be not a good idea to throw.
                    throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
            }
        }
    }

    /**
     * Subclasses override this and place initialization logic here, notably
     * calls to registerListener(). Note that the overriding method MUST repeat
     * the PostConstruct annotation, because JSR 250 specifies that lifecycle
     * methods "are called unless a subclass of the declaring class overrides
     * the method without repeating the annotation".  (The blueprint-maven-plugin
     * would gen. XML which calls PostConstruct annotated methods even if they are
     * in a subclass without repeating the annotation, but this is wrong and not
     * JSR 250 compliant, and while working in BP, then causes issues e.g. when
     * wiring with Guice for tests, so do always repeat it.)
     */
    @PostConstruct
    protected void init() {
    }

    @Override
    @PreDestroy
    //below skip was done intentionally, as close returns Exception
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataTreeChangeListener.", e);
            }
            listenerRegistration = null;
        }
    }

    protected abstract InstanceIdentifier<T> getWildCardPath();

    protected abstract void remove(InstanceIdentifier<T> key, T dataObjectModification);

    protected abstract void update(InstanceIdentifier<T> key,
                                   T dataObjectModificationBefore, T dataObjectModificationAfter);

    protected abstract void add(InstanceIdentifier<T> key, T dataObjectModification);

    protected abstract K getDataTreeChangeListener();

    /**
     * isEventToBeProcessed : deals with only re-ordering. If there a EVENT pending for same IID which  to be processed
     * this api will take action: Queue/Process/Supress.
     * This is verified only when there is a new event add/update/remove called from MD-SAL.
     *
     * @param key:               key received from MD-SAL callback
     * @param oldData:           received from MD-SAL, if required stored as part of deferEvent
     * @param newData:           received from MD-SAL, if required stored as part of deferEvent
     * @param eventType:         Type of Event (Create/Update/Delete) from MD-SAL
     * @param retryTimeInterval: time gap between each verification of data (event expiry time is calculated from this)
     * @param retryCount:       number of times want to retry
     */
    protected boolean isEventToBeProcessed(InstanceIdentifier<T> key, T oldData,
                                           T newData, DeferedEvent.EventType eventType, long retryTimeInterval,
                                           int retryCount) {
        int whatToDoWithEvent = whatToDoWithEventFromDS(key, eventType);
        if (whatToDoWithEvent == DEFER_EVENT_HAS_TO_BE_QUEUED) {
            // DEFERing the event here is mainly due to DEPENDENT EVENT (not IID) is pending to be processed
            // as there are no dependency list, we can set "deferTimerBased is SET to TRUE" and
            // "dependentIidResultList is SET to NULL"
            deferEvent(key, oldData, newData, eventType, retryTimeInterval, null, retryCount, true/*timer*/, null);
            nonApplication_queued_events.inc();
        } else if (whatToDoWithEvent == DEFER_EVENT_HAS_TO_BE_PROCESSED) {
            // Event has to be processed.
            return true;
        } else if (whatToDoWithEvent == DEFER_EVENT_HAS_TO_BE_SUPRESSED) {
            LOG.error("LDH: Event suppressed, key:{}, eventType:{}", key, "UPDATE");
        } else {
            LOG.error("LDH: Unknown result in Event processing, key:{}, eventType:{}", key, eventType);
        }
        return false;
    }

    /**
     * this method, decides whether to Queue the event (or) process  (or) supress event based on
     * existing dered events for the same key/eventType.
     *
     * @param currentKey        :: event just received from MD-SAL
     * @param currentEventType: event type
     */
    protected int whatToDoWithEventFromDS(InstanceIdentifier<T> currentKey, DeferedEvent.EventType currentEventType) {
        List<DeferedEvent> actualIIDQueuedEvents = new ArrayList<DeferedEvent>();
        //TODO: remove below error
        LOG.trace("LDH: entered in whatToDoWithEventFromDS(), key: {}", currentKey);
        /*
        Multiple things to consider before queuing the event:
        -----------------------------------------------------
        CURRENT-EVENT        QUEUED-EVENT           ACTION
        -----------------------------------------------------
        ADD                     ADD                 NOT EXPECTED
        ADD                     REMOVE              QUEUE THE EVENT.
        ADD                     UPDATE              NOT EXPECTED.
        ----
        UPDATE                  ADD                 QUEUE THE EVENT.
        UPDATE                  UPDATE              QUEUE THE EVENT,
        UPDATE                  REMOVE              NOT EXPECTED
        ----
        REMOVE                  ADD                 QUEUE THE EVENT
        REMOVE                  UPDATE              EXECUTE REMOVE SUPPRESS UDPATE
        REMOVE                  REMOVE              NOT EXPECTED.
        */

        if (currentKey == null) {
            LOG.error("LDH: event is supressed as key is null");
            return DEFER_EVENT_HAS_TO_BE_SUPRESSED;
        }

        //every deferEvent call, iterate through the set of deferred events and find out the events which has saem KEY.
        if ((waitingActualIIDMap != null) && (!waitingActualIIDMap.isEmpty())) {
            if (waitingActualIIDMap.contains(currentKey)) {
                ConcurrentLinkedQueue<DeferedEvent> actualIIDpendingQueue = waitingActualIIDMap.get(currentKey);
                if ((actualIIDpendingQueue != null) && (!actualIIDpendingQueue.isEmpty())) {
                    for (DeferedEvent pendingEvents : actualIIDpendingQueue) {
                        //actualIIDQueuedEvents contains, list of events pending for same KEY.
                        //TODO: need to analyze, the need of new Queue here (?)
                        actualIIDQueuedEvents.add(pendingEvents);
                    }
                }
            }
        }

        //Incase there are events pending for the same KEY, apply event-suppression on need basis
        if ((actualIIDQueuedEvents != null) && (!actualIIDQueuedEvents.isEmpty())) {
            //suppress LOGIC
            LOG.error("LDH: there are pending events to process with same key :{}, eventType: {}", currentKey,
                    currentEventType);

            if (currentEventType == DeferedEvent.EventType.ADD) {
                // CURRENT EVENT TYPE == ADD

                for (DeferedEvent pendingEvent : actualIIDQueuedEvents) {
                    if (pendingEvent.getEventType() == DeferedEvent.EventType.ADD) {
                        // QUEUED EVENT TYPE == ADD
                        // Add() followed by Add(): NOT EXPECTED
                        LOG.error("LDH: UNEXPECTED ADD() received when there is pending ADD() in Queue"
                                        + "event key: {} oldEventQueuedTime: {}, entries with SameKey: {}",
                                pendingEvent.key, new Date(pendingEvent.getQueuedTime()), actualIIDQueuedEvents.size());
                        return DEFER_EVENT_HAS_TO_BE_SUPRESSED;
                    } else if (pendingEvent.getEventType() == DeferedEvent.EventType.UPDATE) {
                        // QUEUED EVENT TYPE == UPDATE
                        // Update() followed by an ADD() : Not expected.
                        LOG.error("LDH: UNEXPECTED ADD() received when there is pending UPDATE() in Queue"
                                        + "event key: {} oldEventQueuedTime: {}, entries with SameKey: {}",
                                pendingEvent.key, new Date(pendingEvent.getQueuedTime()), actualIIDQueuedEvents.size());
                        return DEFER_EVENT_HAS_TO_BE_SUPRESSED;
                    } else if (pendingEvent.getEventType() == DeferedEvent.EventType.REMOVE) {
                        // QUEUED EVENT TYPE == REMOVE
                        // let the event to be QUEUED.
                        return DEFER_EVENT_HAS_TO_BE_QUEUED;
                    }
                }
            } else if (currentEventType == DeferedEvent.EventType.UPDATE) {
                // CURRENT EVENT TYPE == UPDATE

                for (DeferedEvent pendingEvent : actualIIDQueuedEvents) {
                    if (pendingEvent.getEventType() == DeferedEvent.EventType.ADD) {
                        // QUEUED EVENT TYPE == ADD
                        // let the event to be QUEUED
                        return DEFER_EVENT_HAS_TO_BE_QUEUED;
                    } else if (pendingEvent.getEventType() == DeferedEvent.EventType.UPDATE) {
                        // QUEUED EVENT TYPE == UPDATE
                        // let the event to be QUEUED
                        return DEFER_EVENT_HAS_TO_BE_QUEUED;
                    } else if (pendingEvent.getEventType() == DeferedEvent.EventType.REMOVE) {
                        // QUEUED EVENT TYPE == REMOVE
                        // Remove() followed by an Update() : Not expected.
                        LOG.trace("LDH: UPDATE() received when there is pending REMOVE() in Queue"
                                        + "event key: {} oldEventQueuedTime: {}, entries with SameKey: {}",
                                pendingEvent.key, new Date(pendingEvent.getQueuedTime()), actualIIDQueuedEvents.size());
                        return DEFER_EVENT_HAS_TO_BE_SUPRESSED;
                    }
                }
            } else if (currentEventType == DeferedEvent.EventType.REMOVE) {
                // CURRENT EVENT TYPE == REMOVE

                for (DeferedEvent pendingEvent : actualIIDQueuedEvents) {
                    if (pendingEvent.getEventType() == DeferedEvent.EventType.ADD) {
                        // QUEUED EVENT TYPE == ADD
                        //  Add() is present in Queue, now we received Remove(): remove pending add(), supress remove()
                        suppressAddFollowedByRemoveEvent(pendingEvent);
                        return DEFER_EVENT_HAS_TO_BE_QUEUED;
                    } else if (pendingEvent.getEventType() == DeferedEvent.EventType.UPDATE) {
                        // QUEUED EVENT TYPE == UPDATE
                        //Remove() followed by Update(): Supress Update and execute Remove().
                        // TODO : for now, decided to process this event, as there can be state changes on UPDATE.
                        return DEFER_EVENT_HAS_TO_BE_QUEUED;
                    } else if (pendingEvent.getEventType() == DeferedEvent.EventType.REMOVE) {
                        // QUEUED EVENT TYPE == REMOVE
                        // Remove() followed by an Remove() : Not expected.
                        LOG.trace("LDH: REMOVE() received when there is pending REMOVE() in Queue"
                                        + "event key: {} oldEventQueuedTime: {}, entries with SameKey: {}",
                                pendingEvent.key, new Date(pendingEvent.getQueuedTime()), actualIIDQueuedEvents.size());
                        return DEFER_EVENT_HAS_TO_BE_SUPRESSED;
                    }
                }
            }
        }
        return DEFER_EVENT_HAS_TO_BE_PROCESSED;
    }

    /**
     * events gets added to DeferEvent queue of the listener.
     */
    private void deferEvent(InstanceIdentifier<T> key, T dataObjectModificationBefore, T dataObjectModificationAfter,
                            DeferedEvent.EventType eventType, long retryTimeInterval,
                            List<DependencyData> dependentIIDs, int retryCount, boolean deferTimerBased,
                            DataBroker db) {
        if (key != null) {
            LOG.trace("LDH: Defer Event called : key:{}, eventTyep:{}, sizeofQueue: {}", key, eventType,
                    listenerDependencyHelperQueue.size());
            DeferedEventBuilder deferedEventBuilder = new DeferedEventBuilder();
            deferedEventBuilder.setKey(key).setOldData(dataObjectModificationBefore)
                    .setNewData(dataObjectModificationAfter)
                    .setEventType(eventType)
                    .setWaitBetweenDependencyCheckTime(retryTimeInterval)
                    .setDependentIIDs(dependentIIDs)
                    .setRetryCount(retryCount)
                    .setDeferTimerBased(deferTimerBased)
                    .setDb(db);
            DeferedEvent deferedEvent = deferedEventBuilder.build();
            getListenerDependencyHelperQueue().offer(deferedEvent);
        } else {
            LOG.error("LDH: Defer Event called with key as NULL.");
        }
    }

    protected boolean genericSuppressEvent(DeferedEvent suppressedEvent, DeferedEvent.EventType eventType) {
        if (suppressedEvent != null) {
            if (eventType != suppressedEvent.getEventType()) {
                return false;
            }
            listenerDependencyHelperQueue.remove(suppressedEvent);
            return true;
        }
        return false;
    }

    protected boolean suppressAddFollowedByRemoveEvent(DeferedEvent suppressedAddEvent) {
        return genericSuppressEvent(suppressedAddEvent, DeferedEvent.EventType.ADD);
    }

    protected boolean hasPendingTasksToRun() {
        DeferedEvent event = listenerDependencyHelperQueue.peek();
        if ((event != null) || (waitingActualIIDMap.size() > 0) || enableDelayInResolvingEvent) {
            return true;
        }
        return false;
    }

    /**
     * Takes dependency List and returns pending dependent IID's as list.
     */
    protected List<DependencyData> hasDependencyResolved(List<DependencyData> dependentList, DataBroker db) {

        //TODO : remove below error
        LOG.error("LDH: hasDependencyResolved() called.");

        List<DependencyData> unresolvedDependentIids = new ArrayList<DependencyData>();

        for (DependencyData dependentIID : dependentList) {
            ReadOnlyTransaction tx = db.newReadOnlyTransaction();
            Optional<?> data = null;//TODO
            try {
                data = tx.read(dependentIID.dsType, (InstanceIdentifier<? extends DataObject>) dependentIID.iid).get();
                if (dependentIID.expectData) {
                    if (!data.isPresent()) {
                        unresolvedDependentIids.add(dependentIID);
                    }
                } else { //dont expect DATA to be present
                    if (data.isPresent()) {
                        unresolvedDependentIids.add(dependentIID);
                    }
                }
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("LDH: Exception while reading the iid: {}, DS-Type: {}",
                        dependentIID.iid, dependentIID.dsType, e);
            }
        }
        return unresolvedDependentIids;
    }

    /**
     * initiates/queues the resolved event callback.
     **/
    void initiateAcutalListenerCallback(final DeferedEvent deferedEvent) {
        /* scenarios like,
         * defered-wild-card listener gets triggered on dataChanged,                at Time T
         * as soon as, dataGetsChanged: defered-event is pushed back to Listener.   at Time dt+T
         * due to some  reason, Listener is unable to see the data (read fails).    at Time dt+T+x
         *    -> again defer event.
         *    -->as the data available, defered-wild-card listener registers and gets all data
         *       observes dependency data exists, resolves the event and this iteration keeps on
         *       happening for few ~300milliseconds (4 iterations).
         * Conclusion: There is delay between Listener PUSH and DATA availability.
         * TODO : Analyze where is the delay in MD-SAL
         * workaround: Introduce a Queue(delayResolvedDeferedEventQueue), enqueue the dependency resolved events.
         * process them, after fixed time interval.
         */
        if (enableDelayInResolvingEvent) {
            delayResolvedDeferedEventQueue.offer(new TimedResolvedDeferEvent(System.currentTimeMillis(), deferedEvent));
        } else {
            executeListenerCallback(deferedEvent);
        }
    }

    /**
     * executes listener call back from executor service.
     **/
    private void executeListenerCallback(final DeferedEvent deferedEvent) {

        ldhDeferEventExecutor.execute(new Runnable() {
            @Override
            public void run() {
                switch (deferedEvent.getEventType()) {
                    case ADD:
                        add(deferedEvent.key,
                                (T) deferedEvent.getNewData());
                        break;
                    case REMOVE:
                        remove(deferedEvent.key,
                                (T) deferedEvent.getNewData());
                        break;
                    case UPDATE:
                        update(deferedEvent.key,
                                (T) deferedEvent.getOldData(),
                                (T) deferedEvent.getNewData());
                        break;
                    default:
                        LOG.error("LDH: unexpected eventTyep: {}, IID: {}",
                                deferedEvent.getEventType(), deferedEvent.getKey());
                }
            }
        });
    }

    //Lister Dependency Helper related counters.
    public enum Counters {
        events_dequeued,
        events_enqueued_waitingKeyQ,
        events_dequeued_waitingKeyQ,
        events_listenerbased,
        events_registerd_listener,
        events_unregisterd_listener,
        events_reuse_listener,
        events_freed_listener,
        events_listener_add,
        events_listener_delete,
        events_expired_timer,
        event_dependent_iids_resolved_transistion,
        wildcard_listener_add,
        wildcard_listener_delete,
        timer_dependency_resolved,
        listener_dependency_resolved,
        nonApplication_queued_events,
        nonApplication_resolved_events,
        dependent_iid_add_resolved_transision,
        dependent_iid_del_resolved_transision;

        private OccurenceCounter counter;

        Counters() {
            counter = new OccurenceCounter(getClass().getSimpleName(), name(), name());
        }

        public void inc() {
            counter.inc();
        }

        public void dec() {
            counter.dec();
        }

        public long get() {
            return counter.get();
        }
    }
}
