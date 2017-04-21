package org.opendaylight.netvirt.vpnmanager;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.util.Builder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DeferedEventBuilder<T extends DataObject>  implements Builder<DeferedEvent> {

    public DeferedEventBuilder() {
    }
    //eventTYPE: to be executed as part of listener. TODO: define at comman place
    public enum EventType {
        ADD, REMOVE, UPDATE, INVALID
    }

    public DeferedEventBuilder setLastProcessedTime(long lastProcessedTime) {
        this.lastProcessedTime = lastProcessedTime;
        return this;
    }

    public DeferedEventBuilder setDependentIIDs(List<DependencyData> dependentIIDs) {
        this.dependentIIDs = new ArrayList<>(Collections.synchronizedList(dependentIIDs));
        return this;
    }

    public DeferedEventBuilder setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public DeferedEventBuilder setKey(InstanceIdentifier<?> key) {
        this.key = key;
        return this;
    }

    public DeferedEventBuilder setEventType(DeferedEvent.EventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public DeferedEventBuilder setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
        return this;
    }

    public DeferedEventBuilder setQueuedTime(long queuedTime) {
        this.queuedTime = queuedTime;
        return this;
    }

    public DeferedEventBuilder setWaitBetweenDependencyCheckTime(long waitBetweenDependencyCheckTime) {
        this.waitBetweenDependencyCheckTime = waitBetweenDependencyCheckTime;
        return this;
    }

    public DeferedEventBuilder setDb(DataBroker db) {
        this.db = db;
        return this;
    }

    public DeferedEventBuilder setDeferTimerBased(boolean deferTimerBased) {
        this.deferTimerBased = deferTimerBased;
        return this;
    }

    public DeferedEventBuilder setOldData(T oldData) {
        this.oldData = oldData;
        return this;
    }

    public DeferedEventBuilder setNewData(T newData) {
        this.newData = newData;
        return this;
    }

    public InstanceIdentifier<?> getKey() {
        return key;
    }

    public DeferedEvent.EventType getEventType() {
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
    //key = Instance Identified
    private InstanceIdentifier<?> key;

    //Type of event
    private DeferedEvent.EventType eventType = DeferedEvent.EventType.INVALID;

    private T oldData ;
    // Data: going to be modified/updated
    private T newData ;

    // TimeStamp: time at which WAITING FOR THE DEPENDECIES is insignificant.
    private long expiryTime = (VpnConstants.DEFER_EVENT_WAIT_RETRY_COUNT *
            VpnConstants.DEFER_EVENT_MIN_WAIT_TIME_IN_MILLISECONDS);
    // TimeStamp: latest time at which CHECKING FOR THE DEPENDECIES happened.
    private long lastProcessedTime = 0L;
    // TimeStamp: at which the event is queued to LDH.
    private long queuedTime = System.currentTimeMillis();
    // TimeStamp: wait time between each dependency check
    private long waitBetweenDependencyCheckTime = VpnConstants.DEFER_EVENT_MIN_WAIT_TIME_IN_MILLISECONDS;
    // Dependent IID's
    private List<DependencyData> dependentIIDs = null;
    // how many retries = expiry Time
    private int retryCount = VpnConstants.DEFER_EVENT_WAIT_RETRY_COUNT;
    // data broker to register listener/md-sal read
    private DataBroker db = null;
    //kind-of-wait-flag to choose between (timer-based or registration based).
    private boolean deferTimerBased = false;

    @Override
    public DeferedEvent build() {
        if (key == null) {
            throw new IllegalStateException("LDH: key value set to null");
        }
        if (newData == null) {
            throw new IllegalStateException("LDH: new Data cant be null");
        }
        if ((dependentIIDs == null) || (dependentIIDs.size() == 0)) {
            //TODO: this check can be removed, when we start allowing to defer event just for some time
            // (without dependent list)
            throw new IllegalStateException("LDH: dependent IID can't be null");
        }
        if(eventType == DeferedEvent.EventType.INVALID){
            throw new IllegalStateException("LDH: EventType Shall be valid");
        }
        if (db == null) {
            throw new IllegalStateException("LDH: data broker shall be supplied");
        }
        return new DeferedEvent(this);
    }
}
