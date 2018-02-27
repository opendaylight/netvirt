package org.opendaylight.netvirt.elan.recovery.impl;

import org.opendaylight.netvirt.elan.recovery.registry.ElanServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.EntityNameBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.EntityTypeBase;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public final class ElanServiceRecoveryManager {

    private final ElanServiceRecoveryRegistry serviceRecoveryRegistry;

    @Inject
    public ElanServiceRecoveryManager(ElanServiceRecoveryRegistry serviceRecoveryRegistry) {
        this.serviceRecoveryRegistry = serviceRecoveryRegistry;
    }

    private String getServiceRegistryKey(Class<? extends EntityNameBase> entityName) {
        return entityName.toString();
    }

    /**
     * Initiates recovery mechanism for a particular elan-manager entity.
     * This method tries to check whether there is a registered handler for the incoming
     * service recovery request within elan-manager and redirects the call
     * to the respective handler if found.
     *  @param entityType
     *            The type of service recovery. eg :SERVICE or INSTANCE.
     * @param entityName
     *            The type entity for which recovery has to be started. eg : ELAN or ELAN Instance.
     * @param entityId
     *            The unique id to represent the entity to be recovered
     */
    public void recoverService(Class<? extends EntityTypeBase> entityType,
                               Class<? extends EntityNameBase> entityName, String entityId) {
        String serviceRegistryKey = getServiceRegistryKey(entityName);
        serviceRecoveryRegistry.getRegisteredServiceRecoveryHandler(serviceRegistryKey).recoverService(entityId);
    }
}
