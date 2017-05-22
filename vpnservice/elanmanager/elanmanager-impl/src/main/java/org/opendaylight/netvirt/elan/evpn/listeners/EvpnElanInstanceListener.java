/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.evpn.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnMacVrfUtils;
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class EvpnElanInstanceListener extends AsyncDataTreeChangeListenerBase<ElanInstance, EvpnElanInstanceListener> {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnElanInstanceListener.class);
    private final DataBroker broker;
    private final EvpnUtils evpnUtils;
    private final EvpnMacVrfUtils evpnMacVrfUtils;

    @Inject
    public EvpnElanInstanceListener(final DataBroker dataBroker, final EvpnUtils evpnUtils,
                                    EvpnMacVrfUtils evpnMacVrfUtils) {
        super(ElanInstance.class, EvpnElanInstanceListener.class);
        this.broker = dataBroker;
        this.evpnUtils = evpnUtils;
        this.evpnMacVrfUtils = evpnMacVrfUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ElanInstance> getWildCardPath() {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class).build();
    }

    @Override
    protected void add(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance evpnAugmentation) {
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance evpnAugmentation) {
    }

    @Override
    protected void update(InstanceIdentifier<ElanInstance> instanceIdentifier, ElanInstance original,
                          ElanInstance update) {
        String elanName = update.getElanInstanceName();
        ElanUtils.addElanInstanceIntoCache(elanName, update);
        if (evpnUtils.isWithdrawEvpnRT2Routes(original, update)) {
            evpnUtils.withdrawEvpnRT2Routes(original.getAugmentation(EvpnAugmentation.class), elanName);
            evpnMacVrfUtils.updateEvpnDmacFlows(original, false);
        } else if (evpnUtils.isAdvertiseEvpnRT2Routes(original, update)) {
            evpnUtils.advertiseEvpnRT2Routes(update.getAugmentation(EvpnAugmentation.class), elanName);
            evpnMacVrfUtils.updateEvpnDmacFlows(update, true);
        }
    }

    @Override
    protected EvpnElanInstanceListener getDataTreeChangeListener() {
        return this;
    }

}
