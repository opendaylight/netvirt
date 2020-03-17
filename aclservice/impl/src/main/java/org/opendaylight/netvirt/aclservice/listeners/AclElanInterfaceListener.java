/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclElanInterfaceListener extends AbstractAsyncDataTreeChangeListener<ElanInterface>
        implements ClusteredDataTreeChangeListener<ElanInterface>, RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(AclElanInterfaceListener.class);

    private final AclServiceManager aclServiceManager;
    private final AclClusterUtil aclClusterUtil;
    private final DataBroker dataBroker;
    private final AclInterfaceCache aclInterfaceCache;

    @Inject
    public AclElanInterfaceListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil,
            DataBroker dataBroker, AclInterfaceCache aclInterfaceCache,
            ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class),
                Executors.newListeningSingleThreadExecutor("AclElanInterfaceListener", LOG));
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclInterfaceCache = aclInterfaceCache;
        serviceRecoveryRegistry.addRecoverableListener(AclServiceUtils.getRecoverServiceRegistryKey(), this);
    }

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void registerListener() {
    }

    @Override
    public void deregisterListener() {
    }

    @Override
    public void remove(InstanceIdentifier<ElanInterface> key, ElanInterface dataObjectModification) {
        // do nothing
    }

    @Override
    public void update(InstanceIdentifier<ElanInterface> key, ElanInterface dataObjectModificationBefore,
            ElanInterface dataObjectModificationAfter) {
        // do nothing
    }

    @Override
    public void add(InstanceIdentifier<ElanInterface> key, ElanInterface elanInterface) {
        String interfaceId = elanInterface.getName();
        AclInterface aclInterface = aclInterfaceCache.updateIfPresent(interfaceId, (prevAclInterface, builder) -> {
            if (prevAclInterface.getElanId() == null) {
                ElanInstance elanInfo = AclServiceUtils.getElanInstanceByName(elanInterface.getElanInstanceName(),
                        dataBroker);
                if (elanInfo != null) {
                    builder.elanId(elanInfo.getElanTag().toJava());
                }
                return true;
            }

            return false;
        });

        if (aclInterface == null) {
            LOG.debug("On Add event, ignore if AclInterface was not found in cache or was not updated");
            return;
        }

        if (aclInterface.getDpId() != null && aclClusterUtil.isEntityOwner()) {
            // Notify ADD flows, if InterfaceStateListener has processed before ELAN-ID getting populated
            LOG.debug("On add event, notify ACL service manager to BIND/ADD ACL for interface: {}", aclInterface);
            aclServiceManager.notify(aclInterface, null, Action.BIND);
            aclServiceManager.notify(aclInterface, null, Action.ADD);
        }
    }
}
