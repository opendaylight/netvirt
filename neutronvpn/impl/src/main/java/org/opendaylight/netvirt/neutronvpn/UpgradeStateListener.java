/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.tools.mdsal.listener.AbstractClusteredSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.mdsalutil.rev170830.Config;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UpgradeStateListener extends AbstractClusteredSyncDataTreeChangeListener<Config> {
    private static final Logger LOG = LoggerFactory.getLogger(UpgradeStateListener.class);

    private final NeutronSubnetGwMacResolver neutronSubnetGwMacResolver;

    @Inject
    public UpgradeStateListener(final DataBroker dataBroker,
                                final NeutronSubnetGwMacResolver neutronSubnetGwMacResolver) {
        super(dataBroker, new DataTreeIdentifier<>(
                LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Config.class)));
        LOG.trace("UpgradeStateListener (neutronvpn) initialized");
        this.neutronSubnetGwMacResolver = neutronSubnetGwMacResolver;
    }

    @Override
    public void add(@Nonnull Config newDataObject) {
    }

    @Override
    public void remove(@Nonnull Config removedDataObject) {
    }

    @Override
    public void update(@Nonnull Config original, Config updated) {
        LOG.info("UpgradeStateListener update from {} to {}", original, updated);
        neutronSubnetGwMacResolver.sendArpRequestsToExtGateways();
    }
}
