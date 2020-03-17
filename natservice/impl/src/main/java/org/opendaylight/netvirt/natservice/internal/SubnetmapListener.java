/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetmapListener extends AbstractAsyncDataTreeChangeListener<Subnetmap> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapListener.class);
    private final DataBroker dataBroker;
    private final ExternalNetworkGroupInstaller externalNetworkGroupInstaller;
    private final NatServiceCounters natServiceCounters;

    @Inject
    public SubnetmapListener(final DataBroker dataBroker,
                             final ExternalNetworkGroupInstaller externalNetworkGroupInstaller,
                             NatServiceCounters natServiceCounters) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class),
                Executors.newListeningSingleThreadExecutor("SubnetmapListener", LOG));
        this.dataBroker = dataBroker;
        this.externalNetworkGroupInstaller = externalNetworkGroupInstaller;
        this.natServiceCounters = natServiceCounters;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("remove key: {} value: {}", subnetmap.key(), subnetmap);
        natServiceCounters.subnetmapRemove();
        externalNetworkGroupInstaller.removeExtNetGroupEntries(subnetmap);
    }

    @Override
    public void update(InstanceIdentifier<Subnetmap> identifier,
                          Subnetmap subnetmapBefore, Subnetmap subnetmapAfter) {
        LOG.trace("update key: {}, original: {}, update: {}", subnetmapAfter.key(), subnetmapBefore, subnetmapAfter);
        natServiceCounters.subnetmapUpdate();
        externalNetworkGroupInstaller.installExtNetGroupEntries(subnetmapAfter);
    }

    @Override
    public void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("add key: {} value: {}", subnetmap.key(), subnetmap);
        natServiceCounters.subnetmapAdd();
        externalNetworkGroupInstaller.installExtNetGroupEntries(subnetmap);
    }
}
