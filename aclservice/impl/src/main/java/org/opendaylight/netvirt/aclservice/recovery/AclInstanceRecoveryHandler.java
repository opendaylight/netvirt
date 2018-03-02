/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.recovery;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.genius.srm.ServiceRecoveryInterface;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.NetvirtAclInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclInstanceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AclInstanceRecoveryHandler.class);

    @Inject
    public AclInstanceRecoveryHandler(ServiceRecoveryRegistry serviceRecoveryRegistry) {
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    public void recoverService(String entityId) {
        LOG.info("Recover ACL instance {}", entityId);
    }

    private String buildServiceRegistryKey() {
        return NetvirtAclInstance.class.toString();
    }
}
