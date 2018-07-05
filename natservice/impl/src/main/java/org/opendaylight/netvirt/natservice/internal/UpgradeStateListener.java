/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractClusteredSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsalutil.rev170830.Config;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UpgradeStateListener extends AbstractClusteredSyncDataTreeChangeListener<Config> {
    private static final Logger LOG = LoggerFactory.getLogger(UpgradeStateListener.class);

    private final DataBroker dataBroker;
    private final CentralizedSwitchScheduler centralizedSwitchScheduler;
    private final NatserviceConfig.NatMode natMode;

    @Inject
    public UpgradeStateListener(final DataBroker dataBroker,
                                final CentralizedSwitchScheduler centralizedSwitchScheduler,
                                final NatserviceConfig config) {
        super(dataBroker, new DataTreeIdentifier<>(
                LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Config.class)));
        this.dataBroker = dataBroker;
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatserviceConfig.NatMode.Controller;
        }
        LOG.trace("UpgradeStateListener (nat) initialized");
    }

    @Override
    public void add(@Nonnull Config newDataObject) {
    }

    @Override
    public void remove(@Nonnull Config removedDataObject) {
    }

    @Override
    public void update(@Nonnull Config original, Config updated) {
        if (natMode != NatserviceConfig.NatMode.Conntrack) {
            return;
        }

        LOG.info("UpgradeStateListener update from {} to {}", original, updated);
        if (!(original.isUpgradeInProgress() && !updated.isUpgradeInProgress())) {
            return;
        }

        SingleTransactionDataBroker reader = new SingleTransactionDataBroker(dataBroker);
        ExtRouters routers;
        try {
            routers = reader.syncRead(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(ExtRouters.class));
        } catch (ReadFailedException e) {
            LOG.error("Error reading external routers", e);
            return;
        }

        for (Routers router : routers.getRouters()) {
            List<ExternalIps> externalIps = router.getExternalIps();
            if (router.isEnableSnat() && externalIps != null && !externalIps.isEmpty()) {
                centralizedSwitchScheduler.scheduleCentralizedSwitch(router);
            }
        }
    }
}
