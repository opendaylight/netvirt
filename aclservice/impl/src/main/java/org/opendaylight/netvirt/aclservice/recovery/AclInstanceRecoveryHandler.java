/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.recovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceListener;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.serviceutils.srm.ServiceRecoveryInterface;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.srm.types.rev180626.NetvirtAclInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Singleton
public class AclInstanceRecoveryHandler implements ServiceRecoveryInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AclInstanceRecoveryHandler.class);
    private final DataBroker dataBroker;
    private final AclDataUtil aclDataUtil;
    private final AclInterfaceListener aclInterfaceListener;

    @Inject
    public AclInstanceRecoveryHandler(ServiceRecoveryRegistry serviceRecoveryRegistry, DataBroker dataBroker,
             AclDataUtil aclDataUtil, AclInterfaceListener aclInterfaceListener) {
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.aclInterfaceListener = aclInterfaceListener;
        serviceRecoveryRegistry.registerServiceRecoveryRegistry(buildServiceRegistryKey(), this);
    }

    @Override
    public void recoverService(String entityId) {
        LOG.info("Recover ACL instance {}", entityId);
        Uuid aclId = new Uuid(entityId);
        Collection<AclInterface> aclInterfaces = aclDataUtil.getInterfaceList(aclId);
        for (AclInterface aclInterface : aclInterfaces) {
            String aclInterfaceId = aclInterface.getInterfaceId();
            Optional<Interface> interfaceOptional = AclServiceUtils.getInterface(dataBroker,
                    aclInterfaceId);
            if (interfaceOptional.isPresent()) {
                Interface interfaceBefore = interfaceOptional.get();
                LOG.debug("Starting Recovery of acl Instance {} for interface {}", entityId, interfaceBefore.getName());
                InterfaceAcl interfaceAclBefore = interfaceBefore.augmentation(InterfaceAcl.class);
                List<Uuid> sgList = interfaceAclBefore.getSecurityGroups() != null ? new ArrayList<>(
                    interfaceAclBefore.getSecurityGroups()) : new ArrayList<>();
                sgList.remove(aclId);
                InterfaceAcl interfaceAclAfter = new InterfaceAclBuilder(interfaceAclBefore).setSecurityGroups(sgList)
                        .build();
                Interface interfaceAfter = new InterfaceBuilder(interfaceBefore)
                        .addAugmentation(interfaceAclAfter).build();
                aclInterfaceListener.update(null, interfaceBefore, interfaceAfter);
                aclInterfaceListener.update(null, interfaceAfter, interfaceBefore);
            } else {
                LOG.error("Interfaces not present for aclInterface {} ", aclInterfaceId);
            }
        }
    }

    private String buildServiceRegistryKey() {
        return NetvirtAclInstance.class.toString();
    }
}
