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
import org.opendaylight.netvirt.elan.evpn.utils.EvpnUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class EvpnElanInstanceManager extends AsyncDataTreeChangeListenerBase<EvpnAugmentation, EvpnElanInstanceManager>
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnElanInstanceManager.class);

    private final DataBroker broker;
    private final EvpnUtils evpnUtils;

    @Inject
    public EvpnElanInstanceManager(final DataBroker dataBroker, final EvpnUtils evpnUtils) {
        super(EvpnAugmentation.class, EvpnElanInstanceManager.class);
        this.broker = dataBroker;
        this.evpnUtils = evpnUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<EvpnAugmentation> getWildCardPath() {
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class)
                .augmentation(EvpnAugmentation.class).build();
    }

    @Override
    protected void add(InstanceIdentifier<EvpnAugmentation> instanceIdentifier, EvpnAugmentation evpnAugmentation) {
        String elanName = instanceIdentifier.firstKeyOf(ElanInstance.class).getElanInstanceName();
        evpnUtils.advertiseEvpnRT2Routes(evpnAugmentation, elanName);
    }

    @Override
    protected void remove(InstanceIdentifier<EvpnAugmentation> instanceIdentifier, EvpnAugmentation evpnAugmentation) {
        String elanName = instanceIdentifier.firstKeyOf(ElanInstance.class).getElanInstanceName();
        evpnUtils.withdrawEvpnRT2Routes(evpnAugmentation, elanName);
    }

    @Override
    protected void update(InstanceIdentifier<EvpnAugmentation> instanceIdentifier, EvpnAugmentation original,
                          EvpnAugmentation update) {
        String elanName = instanceIdentifier.firstKeyOf(ElanInstance.class).getElanInstanceName();
        if (evpnUtils.isWithdrawEvpnRT2Routes(original, update)) {
            evpnUtils.withdrawEvpnRT2Routes(original, elanName);
        } else if (evpnUtils.isAdvertiseEvpnRT2Routes(original, update)) {
            evpnUtils.advertiseEvpnRT2Routes(update, elanName);
        }
    }

    @Override
    protected EvpnElanInstanceManager getDataTreeChangeListener() {
        return this;
    }

}
