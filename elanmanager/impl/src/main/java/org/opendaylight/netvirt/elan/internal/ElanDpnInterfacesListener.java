/*
 * Copyright (c) 2017 Ericsson and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import static java.util.Collections.emptyList;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanDpnInterfacesListener
        extends AbstractAsyncDataTreeChangeListener<DpnInterfaces> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnInterfacesListener.class);
    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceManager;
    private final ElanServiceProvider elanService;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public ElanDpnInterfacesListener(final DataBroker dataBroker, final IInterfaceManager interfaceManager,
                                     final ElanServiceProvider elanService, final JobCoordinator jobCoordinator,
                                     final ElanInstanceCache elanInstanceCache) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class).child(DpnInterfaces.class),
                Executors.newListeningSingleThreadExecutor("ElanDpnInterfacesListener", LOG));
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces dpnInterfaces) {

    }

    @Override
    public void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
                          DpnInterfaces update) {
        LOG.debug("received Dpninterfaces update event for dpn {}", update.getDpId());
        Uint64 dpnId = update.getDpId();
        String elanInstanceName = identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
        ElanInstance elanInstance = elanInstanceCache.get(elanInstanceName).orElse(null);

        if (elanInstance != null && !elanInstance.isExternal() && ElanUtils.isVlan(elanInstance)) {
            List<String> interfaces = update.getInterfaces();
            // trigger deletion for vlan provider intf on the DPN for the vlan provider network
            if (interfaces != null && interfaces.size() == 1 && interfaceManager.isExternalInterface(
                    interfaces.get(0))) {
                LOG.debug("deleting vlan prv intf for elan {}, dpn {}", elanInstanceName, dpnId);
                jobCoordinator.enqueueJob(dpnId.toString(), () -> {
                    elanService.deleteExternalElanNetwork(elanInstance, dpnId);
                    return emptyList();
                });
            }
        }
    }

    @Override
    public void add(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces dpnInterfaces) {
        LOG.debug("received Dpninterfaces add event for dpn {}", dpnInterfaces.getDpId());
        Uint64 dpnId = dpnInterfaces.getDpId();
        String elanInstanceName = identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
        ElanInstance elanInstance = elanInstanceCache.get(elanInstanceName).orElse(null);

        // trigger creation of vlan provider intf for the vlan provider network
        // on br-int patch port for this DPN
        if (elanInstance != null && !elanInstance.isExternal() && ElanUtils.isVlan(elanInstance)) {
            jobCoordinator.enqueueJob(dpnId.toString(), () -> {
                LOG.debug("creating vlan member intf for elan {}, dpn {}",
                        elanInstance.getPhysicalNetworkName(), dpnId);
                elanService.createExternalElanNetwork(elanInstance, dpnId);
                return emptyList();
            });
        }
    }
}
