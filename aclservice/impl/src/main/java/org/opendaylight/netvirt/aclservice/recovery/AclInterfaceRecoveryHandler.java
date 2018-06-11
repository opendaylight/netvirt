/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.recovery;

import com.google.common.base.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.srm.ServiceRecoveryInterface;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceListener;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.types.rev170711.NetvirtAclInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclInterfaceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceRecoveryHandler.class);

    private final DataBroker dataBroker;
    private final AclInterfaceListener aclInterfaceListener;

    @Inject
    public AclInterfaceRecoveryHandler(ServiceRecoveryRegistry serviceRecoveryRegistry, DataBroker dataBroker,
            AclInterfaceListener aclInterfaceListener) {
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
        this.dataBroker = dataBroker;
        this.aclInterfaceListener = aclInterfaceListener;
    }

    @Override
    public void recoverService(String entityId) {
        LOG.info("Recover ACL interface {}", entityId);
        Optional<Interface> interfaceOp = AclServiceUtils.getInterface(dataBroker, entityId);
        if (interfaceOp.isPresent()) {
            Interface aclInterface = interfaceOp.get();
            LOG.debug("Starting Recovery of acl Interface {} ", aclInterface.getName());
            InstanceIdentifier<Interface> interfaceIdentifier = AclServiceUtils.getInterfaceIdentifier(entityId);
            aclInterfaceListener.remove(interfaceIdentifier, aclInterface);
            aclInterfaceListener.add(interfaceIdentifier, aclInterface);
        } else {
            LOG.warn("{} is not valid ACL interface", entityId);
        }
    }

    private String buildServiceRegistryKey() {
        return NetvirtAclInterface.class.toString();
    }
}
