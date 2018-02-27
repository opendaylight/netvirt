package org.opendaylight.netvirt.elan.recovery.registry;

import org.opendaylight.netvirt.elan.recovery.ElanServiceRecoveryInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ElanServiceRecoveryRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceRecoveryRegistry.class);

    private final Map<String, ElanServiceRecoveryInterface> serviceRecoveryRegistry = new ConcurrentHashMap<>();

    public void registerServiceRecoveryRegistry(String entityName,
                                                ElanServiceRecoveryInterface serviceRecoveryHandler) {
        serviceRecoveryRegistry.put(entityName, serviceRecoveryHandler);
        LOG.trace("Registered service recovery handler for {}", entityName);
    }

    public ElanServiceRecoveryInterface getRegisteredServiceRecoveryHandler(String entityName) {
        return serviceRecoveryRegistry.get(entityName);
    }
}