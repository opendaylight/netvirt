package org.opendaylight.netvirt.elan.recovery;

public interface ElanServiceRecoveryInterface {
    /**
     * Initiates recovery mechanism for a particular elan-manager entity.
     *
     * @param entityId
     *            The unique identifier for the service instance.
     */
    void recoverService(String entityId);
}
