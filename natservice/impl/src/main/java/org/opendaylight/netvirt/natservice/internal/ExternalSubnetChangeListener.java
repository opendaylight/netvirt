/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalSubnetChangeListener extends AbstractAsyncDataTreeChangeListener<Subnets> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetChangeListener.class);
    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;

    @Inject
    public ExternalSubnetChangeListener(final DataBroker dataBroker,
                     final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                     final IElanService elanService, final IVpnManager vpnManager,
                     DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ExternalSubnets.class)
                .child(Subnets.class),
                Executors.newListeningSingleThreadExecutor("ExternalSubnetChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<Subnets> key, Subnets subnet) {
        LOG.info("remove : External Subnet remove mapping method - key:{}. value={}",
                subnet.key(), subnet);
        String extSubnetUuid = subnet.getId().getValue();
        Uint32 vpnId = NatUtil.getVpnId(dataBroker, extSubnetUuid);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("Vpn Instance not found for External subnet : {}", extSubnetUuid);
            return;
        } else {
            snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet,
                    subnet.getExternalNetworkId().getValue(), NwConstants.DEL_FLOW, vpnId);
        }
    }

    @Override
    public void update(InstanceIdentifier<Subnets> key, Subnets orig,
            Subnets update) {
    }

    @Override
    public void add(InstanceIdentifier<Subnets> key, Subnets subnet) {
    }
}
