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
import org.opendaylight.netvirt.qosservice.QosPolicyChangeListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev170711.NetvirtQosPolicyInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class QosPolicyInstanceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(QosPolicyInstanceRecoveryHandler.class);

    private final QosPolicyChangeListener qosPolicyChangeListener;

    @Inject
    public QosPolicyInstanceRecoveryHandler(ServiceRecoveryRegistry serviceRecoveryRegistry,
                                            QosPolicyChangeListener qosPolicyChangeListener) {

        LOG.info("Registering for recovery of QosPolicy Instance");
        this.qosPolicyChangeListener = qosPolicyChangeListener;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    public void recoverService(String entityId) {
        LOG.info("Recover Qos Policy instance {}", entityId);
        qosPolicyChangeListener.reapplyPolicy(entityId);
    }

    private String buildServiceRegistryKey() {
        return NetvirtQosPolicyInstance.class.toString();
    }
}
