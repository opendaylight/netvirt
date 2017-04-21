/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeferedEvent<T extends DataObject> implements Comparable<DeferedEvent<T>> {

    public static final Logger LOG = LoggerFactory.getLogger(LdhDataTreeChangeListenerBase.class);
    //key = Instance Identified
    protected InstanceIdentifier<?> key;
    //Type of event
    private EventType eventType;    // OLD Data :: valid only in case of UPDATE event.
    private T oldData;
    // Data: going to be modified/updated
    private T newData;
    // TimeStamp: time at which WAITING FOR THE DEPENDECIES is insignificant.
    private long expiryTime;
    // TimeStamp: latest time at which CHECKING FOR THE DEPENDECIES happened.
    private long lastProcessedTime = 0L;
    // TimeStamp: at which the event is queued to LDH.
    private long queuedTime;
    // TimeStamp: wait time between each dependency check
    private long waitBetweenDependencyCheckTime;
    // Dependent IID's
    private List<DependencyData> dependentIIDs;
    // how many retries = expiry Time
    private int retryCount;
    // data broker to register listener/md-sal read
    private DataBroker db;
    //kind-of-wait-flag to choose between (timer-based or listenerRegistration based).
    private boolean deferTimerBased;

    /**
     * constructor for creating DeferedEvent.
     *
     * @param key                            :: InstanceIdentifier notified by MD-SAL (ADD/UPDATE/DELETE) operation
     * @param oldData                        :: applicable only in case UPDATE only (otherwise null)
     * @param newData                        :: modified data in DS
     * @param eventType                      :: type of event trigger from DS
     * @param waitBetweenDependencyCheckTime :: interval to wait between next verification
     * @param dependentIIDs                  :: list of dependent IID's for the DS triggered IID (actual IID)
     * @param retryCount                     :: number of times it can retry.
     * @param deferTimerBased                :: true-> poll mechanism after waitBetweenDependencyCheckTime, false:
     *                                          Register Listened
     * @param db                             :: data broker to query for data
     * @param listenerDependencyHelperQueue .
     **/
    private DeferedEvent(InstanceIdentifier<?> key, T oldData, T newData, EventType eventType,
                        long waitBetweenDependencyCheckTime, List<DependencyData> dependentIIDs,
                        int retryCount, boolean deferTimerBased, final DataBroker db,
                        Queue<DeferedEvent> listenerDependencyHelperQueue) {
        this.key = key;
        if ((eventType == EventType.UPDATE) && (oldData != null)) {
            this.oldData = oldData;
        } else if ((eventType != EventType.UPDATE) && (oldData != null)) {
            this.oldData = null;
            LOG.error("LDH: oldData is not expected for EventType: ADD/DELETE, received for key: {} ", key);
        } else {
            this.oldData = null;
        }
        this.newData = newData;
        this.eventType = eventType;
        this.expiryTime = System.currentTimeMillis() + (retryCount * waitBetweenDependencyCheckTime);
        this.queuedTime = this.lastProcessedTime = System.currentTimeMillis();
        this.dependentIIDs = new ArrayList<>(Collections.synchronizedList(dependentIIDs));
        this.retryCount = retryCount;
        this.deferTimerBased = deferTimerBased;
        this.waitBetweenDependencyCheckTime = waitBetweenDependencyCheckTime;
        this.db = db;
        listenerDependencyHelperQueue.offer(this);
    }

    private DeferedEvent(InstanceIdentifier<?> key, T oldData, T newData, EventType eventType,
                        long waitBetweenDependencyCheckTime, List<DependencyData> dependentIIDs,
                        int retryCount, boolean deferTimerBased, final DataBroker db) {
        this.key = key;
        if ((eventType == EventType.UPDATE) && (oldData != null)) {
            this.oldData = oldData;
        } else if ((eventType != EventType.UPDATE) && (oldData != null)) {
            this.oldData = null;
            LOG.error("LDH: oldData is not expected for EventType: ADD/DELETE, received for key: {} ", key);
        } else {
            this.oldData = null;
        }
        this.newData = newData;
        this.eventType = eventType;
        this.expiryTime = System.currentTimeMillis() + (retryCount * waitBetweenDependencyCheckTime);
        this.queuedTime = this.lastProcessedTime = System.currentTimeMillis();
        this.dependentIIDs = new ArrayList<>(Collections.synchronizedList(dependentIIDs));
        this.retryCount = retryCount;
        this.deferTimerBased = deferTimerBased;
        this.waitBetweenDependencyCheckTime = waitBetweenDependencyCheckTime;
        this.db = db;
        LOG.error(this.toString());
    }

    public DeferedEvent(DeferedEventBuilder deferedEventBuilder) {
        this.key = deferedEventBuilder.getKey();
        this.oldData = (T) deferedEventBuilder.getOldData();
        this.newData = (T) deferedEventBuilder.getNewData();
        this.eventType = deferedEventBuilder.getEventType();
        this.waitBetweenDependencyCheckTime = deferedEventBuilder.getWaitBetweenDependencyCheckTime();
        this.dependentIIDs = new ArrayList<>(deferedEventBuilder.getDependentIIDs());
        this.retryCount = deferedEventBuilder.getRetryCount();
        this.deferTimerBased = deferedEventBuilder.isDeferTimerBased();
        this.db = deferedEventBuilder.getDb();
        this.expiryTime = deferedEventBuilder.getExpiryTime();
        this.queuedTime = deferedEventBuilder.getQueuedTime();
        this.lastProcessedTime = deferedEventBuilder.getLastProcessedTime();
        LOG.error(this.toString());
    }

    public InstanceIdentifier<?> getKey() {
        return key;
    }

    public EventType getEventType() {
        return eventType;
    }

    public T getOldData() {
        return oldData;
    }

    public T getNewData() {
        return newData;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public long getLastProcessedTime() {
        return lastProcessedTime;
    }

    public void setLastProcessedTime(long lastProcessedTime) {
        this.lastProcessedTime = lastProcessedTime;
    }

    public long getQueuedTime() {
        return queuedTime;
    }

    public long getWaitBetweenDependencyCheckTime() {
        return waitBetweenDependencyCheckTime;
    }

    public List<DependencyData> getDependentIIDs() {
        return dependentIIDs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public DataBroker getDb() {
        return db;
    }

    public boolean isDeferTimerBased() {
        return deferTimerBased;
    }

    /**
     * remove add dependency from the existing dependecny list of the DeferedEvent.
     */
    public List<DependencyData> removeAddDependency(InstanceIdentifier<?> key, LogicalDatastoreType datastoreType) {
        // as we are using iterator below, it should be under synchronized.
        LOG.trace("LDH: remove iid, which is waiting for data ADDITION iid: {}", key);
        List<DependencyData> removedDependentIidData = null;
        synchronized (dependentIIDs) {
            //get removed element, which is used to unregister listener
            removedDependentIidData = dependentIIDs.stream()
                    .filter(p -> (p.getDsType().equals(datastoreType)) && (p.getIid().equals(key)) && p.isExpectData())
                    .collect(Collectors.toList());

            // keep only remaining dependencies which are yet to be resolved
            List<DependencyData> modifiedDependentIIDs = dependentIIDs.stream()
                    .filter(p -> (!(p.getDsType().equals(datastoreType))
                            && (p.getIid().equals(key)) && p.isExpectData()))
                    .collect(Collectors.toList());
            dependentIIDs = Collections.synchronizedList(modifiedDependentIIDs);
        }
        if ((removedDependentIidData != null) && (!removedDependentIidData.isEmpty())) {
            return removedDependentIidData;
        }
        return null;
    }

    /**
     * remove delete dependency from the existing dependecny list of the DeferedEvent.
     */
    public List<DependencyData> removeDeleteDependency(InstanceIdentifier<?> key, LogicalDatastoreType datastoreType) {
        // as we are using iterator below, it should be under synchronized.
        LOG.trace("LDH: remove iid, which is waiting for data REMOVAL iid: {}", key);
        List<DependencyData> removedDependentIidData = null;
        synchronized (dependentIIDs) {
            //get removed element, which is used to unregister listener
            removedDependentIidData = dependentIIDs.stream()
                    .filter(p -> (!(p.getDsType().equals(datastoreType))
                            && (p.getIid().equals(key)) && !p.isExpectData()))
                    .collect(Collectors.toList());

            //keep only remaining dependencies in list
            List<DependencyData> modifiedDependentIIDs = dependentIIDs.stream()
                    .filter(p -> (!(p.getDsType().equals(datastoreType))
                            && (p.getIid().equals(key)) && !p.isExpectData()))
                    .collect(Collectors.toList());
            dependentIIDs = Collections.synchronizedList(modifiedDependentIIDs);
        }
        if ((removedDependentIidData != null) && (!removedDependentIidData.isEmpty())) {
            return removedDependentIidData;
        }
        return null;
    }

    boolean areDependenciesResolved() {
        //once dependentIIDs is filled, only removal from list happens (no further addition).
        if ((dependentIIDs == null) || (dependentIIDs.isEmpty())) {
            return true;
        }
        return false;
    }

    @Override
    public int compareTo(DeferedEvent<T> other) {
        return (int) (this.queuedTime - other.queuedTime);
    }

    //eventTYPE: to be executed as part of listener
    public enum EventType {
        ADD, REMOVE, UPDATE, INVALID
    }

    @Override
    public String toString() {
        return "DeferedEvent: key: " + key + ", eventType: " + eventType + ", newData: "
                + (newData == null ? "null" : "NOT null") + ", oldData: " + (oldData == null ? "null" : "NOT null")
                + ", expiryTime: " + new Date(expiryTime) + ", queuedTime: " + new Date(queuedTime)
                + ", waitBetweenDependencyCheckTime: " + waitBetweenDependencyCheckTime + "retryCount: "
                + retryCount + ", dependencyList size: " + dependentIIDs.size() + ", db: " + db
                + ", deferTimerbased: " + deferTimerBased + ", lastprocessingTime: " + new Date(lastProcessedTime);
    }
}
