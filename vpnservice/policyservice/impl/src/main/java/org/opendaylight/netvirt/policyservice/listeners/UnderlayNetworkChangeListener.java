/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.UnderlayNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.UnderlayNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UnderlayNetworkChangeListener
        extends AsyncDataTreeChangeListenerBase<UnderlayNetwork, UnderlayNetworkChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(UnderlayNetworkChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;

    @Inject
    public UnderlayNetworkChangeListener(DataBroker dataBroker, PolicyServiceUtil policyServiceUtil) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected UnderlayNetworkChangeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected InstanceIdentifier<UnderlayNetwork> getWildCardPath() {
        return InstanceIdentifier.create(UnderlayNetworks.class).child(UnderlayNetwork.class);
    }

    @Override
    protected void remove(InstanceIdentifier<UnderlayNetwork> key, UnderlayNetwork underlayNetwork) {
        LOG.info("Underlay network {} removed", underlayNetwork.getNetworkName());
        policyServiceUtil.removeOperationalUnderlayNetwork(underlayNetwork.getNetworkName());
    }

    @Override
    protected void update(InstanceIdentifier<UnderlayNetwork> key, UnderlayNetwork origUnderlayNetwork,
            UnderlayNetwork updatedUnderlayNetwork) {
        LOG.info("Underlay network {} updated", updatedUnderlayNetwork .getNetworkName());
    }

    @Override
    protected void add(InstanceIdentifier<UnderlayNetwork> key, UnderlayNetwork underlayNetwork) {
        LOG.info("Underlay network {} added", underlayNetwork.getNetworkName());
    }

}
