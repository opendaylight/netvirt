/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice.recovery;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev170711.NetvirtQos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class QosServiceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(QosServiceRecoveryHandler.class);
    private final ServiceRecoveryRegistry serviceRecoveryRegistry;

    @Inject
    public QosServiceRecoveryHandler(final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        LOG.info("Registering Qos for service recovery");
        this.serviceRecoveryRegistry = serviceRecoveryRegistry;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    public void recoverService(final String entityId) {
        LOG.info("recover QOS service by de-registering and registering all relevant listeners");
        deregisterListeners();
        registerListeners();
    }

    private void deregisterListeners() {
        LOG.trace("De-Registering QOS Listeners for recovery");
        serviceRecoveryRegistry.getRecoverableListeners(buildServiceRegistryKey())
            .forEach((RecoverableListener::deregisterListener));
    }

    private void registerListeners() {
        LOG.trace("Re-Registering QOS Listeners for recovery");
        serviceRecoveryRegistry.getRecoverableListeners(buildServiceRegistryKey())
            .forEach((RecoverableListener::registerListener));
    }

    public String buildServiceRegistryKey() {
        return NetvirtQos.class.toString();
    }
}
