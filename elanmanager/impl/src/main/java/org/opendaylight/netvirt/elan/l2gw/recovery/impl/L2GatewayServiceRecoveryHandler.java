/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.recovery.impl;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev180626.NetvirtL2gw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class L2GatewayServiceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayServiceRecoveryHandler.class);

    private final ServiceRecoveryRegistry serviceRecoveryRegistry;

    @Inject
    public L2GatewayServiceRecoveryHandler(final ServiceRecoveryRegistry serviceRecoveryRegistry) {
        LOG.info("registering l2gw service recovery handlers");
        this.serviceRecoveryRegistry = serviceRecoveryRegistry;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);

    }

    private void deregisterListeners() {
        serviceRecoveryRegistry.getRecoverableListeners(buildServiceRegistryKey())
                .forEach(recoverableListener -> recoverableListener.deregisterListener());
    }

    private void registerListeners() {
        serviceRecoveryRegistry.getRecoverableListeners(buildServiceRegistryKey())
                .forEach(recoverableListener -> recoverableListener.registerListener());
    }


    @Override
    public void recoverService(String entityId) {
        LOG.info("recover l2gw service by deregistering and re-registering all relavent listeners");
        deregisterListeners();
        registerListeners();
    }

    public String buildServiceRegistryKey() {
        return NetvirtL2gw.class.toString();
    }

}