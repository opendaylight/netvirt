/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Optional;
import java.util.Collections;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInterfaceCache;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanInterfaceConfigListener
    extends AsyncDataTreeChangeListenerBase<Interface, ElanInterfaceConfigListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceConfigListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final ElanInterfaceManager elanInterfaceManager;
    private final JobCoordinator jobCoordinator;
    private final ElanInterfaceCache elanInterfaceCache;

    @Inject
    public ElanInterfaceConfigListener(DataBroker dataBroker, ElanInterfaceManager elanInterfaceManager,
            JobCoordinator jobCoordinator, ElanInterfaceCache elanInterfaceCache) {
        super(Interface.class, ElanInterfaceConfigListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.elanInterfaceManager = elanInterfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.elanInterfaceCache = elanInterfaceCache;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("ElanInterfaceConfigListener init");
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface intrf) {
        // Sometimes elan service is not unbound on the interface when the user does nova delete followed
        // by neutron port delete since interface config is deleted a bit later. so adding logic to
        // unbind service for interface config removal.
        if (intrf == null || intrf.augmentation(IfL2vlan.class) == null) {
            LOG.debug("The interface {} is not a L2 interface. Ignoring it", intrf);
            return;
        }

        String interfaceName = intrf.getName();
        Optional<ElanInterface> elanInterface = elanInterfaceCache.get(interfaceName);
        if (!elanInterface.isPresent()) {
            LOG.debug("There is no ELAN service for interface {}. Ignoring it", interfaceName);
            return;
        }
        jobCoordinator.enqueueJob(ElanUtils.getElanInterfaceJobKey(interfaceName),
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                LOG.debug("unbinding elan service on interface {} for its config removal", interfaceName);
                elanInterfaceManager.unbindService(interfaceName, tx);
            })), ElanConstants.JOB_MAX_RETRIES);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface dataObjectModificationBefore,
            Interface dataObjectModificationAfter) {
        // Not required to handle this event
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface dataObjectModification) {
        // Not required to handle this event
    }

    @Override
    protected ElanInterfaceConfigListener getDataTreeChangeListener() {
        return this;
    }

}
