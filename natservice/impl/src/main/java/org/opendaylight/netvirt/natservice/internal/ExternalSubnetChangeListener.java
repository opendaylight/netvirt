/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
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
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.UpgradeState;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalSubnetChangeListener extends AsyncDataTreeChangeListenerBase<Subnets,
    ExternalSubnetChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetChangeListener.class);
    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;

    @Inject
    public ExternalSubnetChangeListener(final DataBroker dataBroker,
                     final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                     final IElanService elanService, final IVpnManager vpnManager,
                     final UpgradeState upgradeState, DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar) {
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnets> getWildCardPath() {
        return InstanceIdentifier.create(ExternalSubnets.class).child(Subnets.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Subnets> key, Subnets subnet) {
        LOG.info("remove : External Subnet remove mapping method - key:{}. value={}",
                subnet.key(), subnet);
        String extSubnetUuid = subnet.getId().getValue();
        long vpnId = NatUtil.getVpnId(dataBroker, extSubnetUuid);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("Vpn Instance not found for External subnet : {}", extSubnetUuid);
            return;
        } else {
            snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet,
                    subnet.getExternalNetworkId().getValue(), NwConstants.DEL_FLOW, vpnId);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Subnets> key, Subnets orig,
            Subnets update) {
    }

    @Override
    protected void add(InstanceIdentifier<Subnets> key, Subnets subnet) {
    }

    @Override
    protected ExternalSubnetChangeListener getDataTreeChangeListener() {
        return ExternalSubnetChangeListener.this;
    }
}
