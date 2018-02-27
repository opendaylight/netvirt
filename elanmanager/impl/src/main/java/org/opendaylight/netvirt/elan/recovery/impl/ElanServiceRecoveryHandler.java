package org.opendaylight.netvirt.elan.recovery.impl;

import org.opendaylight.netvirt.elan.recovery.ElanServiceRecoveryInterface;
import org.opendaylight.netvirt.elan.recovery.listeners.ElanRecoverableListener;
import org.opendaylight.netvirt.elan.recovery.registry.ElanServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.NetvirtElan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class ElanServiceRecoveryHandler implements ElanServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceRecoveryHandler.class);

    private final List<ElanRecoverableListener> recoverableListeners = Collections.synchronizedList(new ArrayList<>());

    @Inject
    public ElanServiceRecoveryHandler(final ElanServiceRecoveryRegistry serviceRecoveryRegistry) {
        LOG.info("registering ELAN service recovery handlers");
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    public void addRecoverableListener(final ElanRecoverableListener recoverableListener) {
        recoverableListeners.add(recoverableListener);
    }

    public void removeRecoverableListener(final ElanRecoverableListener recoverableListener) {
        recoverableListeners.add(recoverableListener);
    }

    private void deregisterListeners() {
        synchronized (recoverableListeners) {
            recoverableListeners.forEach((recoverableListener -> recoverableListener.deregisterListener()));
        }
    }

    private void registerListeners() {
        synchronized (recoverableListeners) {
            recoverableListeners.forEach((recoverableListener -> recoverableListener.registerListener()));
        }
    }

    @Override
    public void recoverService(final String entityId) {
        LOG.info("recover ELAN service by deregistering and registering all relevant listeners");
        deregisterListeners();
        registerListeners();
    }

    public String buildServiceRegistryKey() {
        return NetvirtElan.class.toString();
    }
}

