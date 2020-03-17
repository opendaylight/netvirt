/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.upgrade.rev180702.UpgradeConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UpgradeStateListener extends AbstractClusteredSyncDataTreeChangeListener<UpgradeConfig> {
    private static final Logger LOG = LoggerFactory.getLogger(UpgradeStateListener.class);

    private final NeutronSubnetGwMacResolver neutronSubnetGwMacResolver;

    @Inject
    public UpgradeStateListener(final DataBroker dataBroker,
                                final NeutronSubnetGwMacResolver neutronSubnetGwMacResolver) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(UpgradeConfig.class));
        LOG.trace("UpgradeStateListener (neutronvpn) initialized");
        this.neutronSubnetGwMacResolver = neutronSubnetGwMacResolver;
    }

    @Override
    public void add(@NonNull UpgradeConfig newDataObject) {
    }

    @Override
    public void remove(@NonNull UpgradeConfig removedDataObject) {
    }

    @Override
    public void update(@NonNull UpgradeConfig original, UpgradeConfig updated) {
        LOG.info("UpgradeStateListener update from {} to {}", original, updated);
        neutronSubnetGwMacResolver.sendArpRequestsToExtGateways();
    }
}
