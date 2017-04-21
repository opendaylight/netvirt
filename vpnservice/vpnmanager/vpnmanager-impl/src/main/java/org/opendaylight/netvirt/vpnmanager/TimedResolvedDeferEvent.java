package org.opendaylight.netvirt.vpnmanager;


public class TimedResolvedDeferEvent {
    long queuedTimestamp;
    DeferedEvent resolvedEvent;

    public TimedResolvedDeferEvent(long timestamp, DeferedEvent deferedEvent) {
        this.queuedTimestamp = timestamp;
        this.resolvedEvent = deferedEvent;
    }
}
