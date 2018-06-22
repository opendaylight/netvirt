/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.recovery.impl;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev180626.NetvirtElan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanServiceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(ElanServiceRecoveryHandler.class);

    private final ServiceRecoveryRegistry serviceRecoveryRegistry;

    @Inject
    public ElanServiceRecoveryHandler(final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        LOG.info("registering ELAN service recovery handlers");
        this.serviceRecoveryRegistry = serviceRecoveryRegistry;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    private void deregisterListeners() {
        serviceRecoveryRegistry.getRecoverableListeners(buildServiceRegistryKey())
                .forEach((recoverableListener -> recoverableListener.deregisterListener()));
    }

    private void registerListeners() {
        serviceRecoveryRegistry.getRecoverableListeners(buildServiceRegistryKey())
                        .forEach((recoverableListener -> recoverableListener.registerListener()));
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

