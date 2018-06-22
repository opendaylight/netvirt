/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.recovery;

import java.util.Queue;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclServiceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceRecoveryHandler.class);
    private final ServiceRecoveryRegistry serviceRecoveryRegistry;

    @Inject
    public AclServiceRecoveryHandler(final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        LOG.info("Registering IFM service recovery handlers");
        this.serviceRecoveryRegistry = serviceRecoveryRegistry;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(AclServiceUtils.getRecoverServiceRegistryKey(), this);
    }

    private void deregisterListeners() {
        Queue<RecoverableListener> recoverableListeners =
                serviceRecoveryRegistry.getRecoverableListeners(AclServiceUtils.getRecoverServiceRegistryKey());
        recoverableListeners.forEach((RecoverableListener::deregisterListener));
    }

    private void registerListeners() {
        Queue<RecoverableListener> recoverableListeners =
                serviceRecoveryRegistry.getRecoverableListeners(AclServiceUtils.getRecoverServiceRegistryKey());
        recoverableListeners.forEach((RecoverableListener::registerListener));
    }

    @Override
    public void recoverService(final String entityId) {
        LOG.info("Recover IFM service by deregistering and registering all relevant listeners");
        deregisterListeners();
        registerListeners();
    }
}
