/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetmapListener extends AsyncDataTreeChangeListenerBase<Subnetmap, SubnetmapListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapListener.class);
    private final DataBroker dataBroker;
    private final ExternalNetworkGroupInstaller externalNetworkGroupInstaller;
    private final NatServiceCounters natServiceCounters;

    @Inject
    public SubnetmapListener(final DataBroker dataBroker,
                             final ExternalNetworkGroupInstaller externalNetworkGroupInstaller,
                             NatServiceCounters natServiceCounters) {
        super(Subnetmap.class, SubnetmapListener.class);
        this.dataBroker = dataBroker;
        this.externalNetworkGroupInstaller = externalNetworkGroupInstaller;
        this.natServiceCounters = natServiceCounters;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnetmap> getWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("remove key: {} value: {}", subnetmap.key(), subnetmap);
        natServiceCounters.subnetmapRemove();
        externalNetworkGroupInstaller.removeExtNetGroupEntries(subnetmap);
    }

    @Override
    protected void update(InstanceIdentifier<Subnetmap> identifier,
                          Subnetmap subnetmapBefore, Subnetmap subnetmapAfter) {
        LOG.trace("update key: {}, original: {}, update: {}", subnetmapAfter.key(), subnetmapBefore, subnetmapAfter);
        natServiceCounters.subnetmapUpdate();
        externalNetworkGroupInstaller.installExtNetGroupEntries(subnetmapAfter);
    }

    @Override
    protected void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("add key: {} value: {}", subnetmap.key(), subnetmap);
        natServiceCounters.subnetmapAdd();
        externalNetworkGroupInstaller.installExtNetGroupEntries(subnetmap);
    }

    @Override
    protected SubnetmapListener getDataTreeChangeListener() {
        return this;
    }
}
