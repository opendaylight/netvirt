package org.opendaylight.netvirt.elan.recovery.listeners;

public interface ElanRecoverableListener {
    /**
     * register a recoverable listener.
     */
    void registerListener();

    /**
     * Deregister a recoverable listener.
     */
    void deregisterListener();
}
