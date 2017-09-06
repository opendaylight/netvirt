/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Singleton
public class AclElanInterfaceListener extends AsyncDataTreeChangeListenerBase<ElanInterface, AclElanInterfaceListener>
        implements ClusteredDataTreeChangeListener<ElanInterface> {
    private static final Logger LOG = LoggerFactory.getLogger(AclElanInterfaceListener.class);

    private final AclServiceManager aclServiceManager;
    private final AclClusterUtil aclClusterUtil;
    private final DataBroker dataBroker;

    @Inject
    public AclElanInterfaceListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil,
            DataBroker dataBroker) {
        super(ElanInterface.class, AclElanInterfaceListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<ElanInterface> getWildCardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> key, ElanInterface dataObjectModification) {
        // do nothing
    }

    @Override
    protected void update(InstanceIdentifier<ElanInterface> key, ElanInterface dataObjectModificationBefore,
            ElanInterface dataObjectModificationAfter) {
        // do nothing
    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> key, ElanInterface elanInterface) {
        String interfaceId = elanInterface.getName();
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(interfaceId);
        if (aclInterface == null) {
            LOG.debug("On Add event, ignore if AclInterface is not found in cache");
            return;
        }
        if (aclInterface != null && aclInterface.getElanId() == null) {
            ElanInstance elanInfo = AclServiceUtils.getElanInstanceByName(elanInterface.getElanInstanceName(),
                    dataBroker);
            Long elanId = elanInfo.getElanTag();
            synchronized (AclServiceUtils.getAclKeyForSynchronization(interfaceId).intern()) {
                // update ElanId
                aclInterface.setElanId(elanId);
            }
            if (aclClusterUtil.isEntityOwner()) {
                LOG.debug("On add event, notify ACL service manager to BIND ACL for interface: {}", aclInterface);
                aclServiceManager.notify(aclInterface, null, Action.BIND);
                // Notify ADD flows, if InterfaceStateListener has processed
                // before ELanID getting populated
                if (aclInterface.getDpId() != null) {
                    LOG.debug("On add event, notify ACL service manager to ADD ACL for interface: {}", aclInterface);
                    aclServiceManager.notify(aclInterface, null, Action.ADD);
                }
            }
        }
    }

    @Override
    protected AclElanInterfaceListener getDataTreeChangeListener() {
        return this;
    }
}
