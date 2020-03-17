/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.base.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInterfaceStateChangeListener
        extends AsyncDataTreeChangeListenerBase<Interface, ElanInterfaceStateChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceStateChangeListener.class);

    private final DataBroker broker;
    private final ElanInterfaceManager elanInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanInterfaceCache elanInterfaceCache;

    @Inject
    public ElanInterfaceStateChangeListener(final DataBroker db, final ElanInterfaceManager ifManager,
            final JobCoordinator jobCoordinator, final ElanInstanceCache elanInstanceCache,
            final ElanInterfaceCache elanInterfaceCache) {
        super(Interface.class, ElanInterfaceStateChangeListener.class);
        broker = db;
        elanInterfaceManager = ifManager;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface delIf) {
        if (!L2vlan.class.equals(delIf.getType())) {
            return;
        }
        LOG.trace("Received interface {} Down event", delIf);
        String interfaceName =  delIf.getName();
        Optional<ElanInterface> elanInterface = elanInterfaceCache.get(interfaceName);
        if (!elanInterface.isPresent()) {
            LOG.debug("No Elan Interface is created for the interface:{} ", interfaceName);
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(delIf.getLowerLayerIf().get(0));
        Long dpIdLong = MDSALUtil.getDpnIdFromPortName(nodeConnectorId);
        Uint64 dpId = dpIdLong < 0 ? Uint64.ZERO : Uint64.valueOf(dpIdLong);
        InterfaceInfo interfaceInfo = new InterfaceInfo(dpId, nodeConnectorId.getValue());
        interfaceInfo.setInterfaceName(interfaceName);
        interfaceInfo.setInterfaceType(InterfaceInfo.InterfaceType.VLAN_INTERFACE);
        interfaceInfo.setInterfaceTag(delIf.getIfIndex());
        String elanInstanceName = elanInterface.get().getElanInstanceName();
        ElanInstance elanInstance = elanInstanceCache.get(elanInstanceName).orNull();
        if (elanInstance == null) {
            LOG.debug("No Elan instance is available for the interface:{} ", interfaceName);
            return;
        }
        InterfaceRemoveWorkerOnElan removeWorker = new InterfaceRemoveWorkerOnElan(elanInstanceName, elanInstance,
                interfaceName, interfaceInfo, elanInterfaceManager);
        jobCoordinator.enqueueJob(elanInstanceName, removeWorker, ElanConstants.JOB_MAX_RETRIES);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        if (!L2vlan.class.equals(intrf.getType())) {
            return;
        }
        LOG.trace("Received interface {} up event", intrf);
        String interfaceName =  intrf.getName();
        Optional<ElanInterface> elanInterface = elanInterfaceCache.get(interfaceName);
        if (!elanInterface.isPresent()) {
            LOG.warn("Elan interface {} is not present.", interfaceName);
            return;
        }
        InstanceIdentifier<ElanInterface> elanInterfaceId = ElanUtils
                .getElanInterfaceConfigurationDataPathId(interfaceName);
        elanInterfaceManager.add(elanInterfaceId, elanInterface.get());
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }


    @Override
    protected ElanInterfaceStateChangeListener getDataTreeChangeListener() {
        return this;
    }

}
