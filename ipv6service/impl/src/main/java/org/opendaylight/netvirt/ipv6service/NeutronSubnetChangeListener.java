/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import com.google.common.collect.ImmutableBiMap;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.Dhcpv6Base;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.Dhcpv6Off;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.Dhcpv6Slaac;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.Dhcpv6Stateful;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.Dhcpv6Stateless;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronSubnetChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Subnet,
        NeutronSubnetChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetChangeListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    private static final ImmutableBiMap<Class<? extends IpVersionBase>,String> IPV_MAP
            = new ImmutableBiMap.Builder<Class<? extends IpVersionBase>,String>()
            .put(IpVersionV4.class, Ipv6ServiceConstants.IP_VERSION_V4)
            .put(IpVersionV6.class, Ipv6ServiceConstants.IP_VERSION_V6)
            .build();

    private static final ImmutableBiMap<Class<? extends Dhcpv6Base>,String> DHCPV6_MAP
            = new ImmutableBiMap.Builder<Class<? extends Dhcpv6Base>,String>()
            .put(Dhcpv6Off.class,Ipv6ServiceConstants.DHCPV6_OFF)
            .put(Dhcpv6Stateful.class,Ipv6ServiceConstants.IPV6_DHCPV6_STATEFUL)
            .put(Dhcpv6Slaac.class,Ipv6ServiceConstants.IPV6_SLAAC)
            .put(Dhcpv6Stateless.class,Ipv6ServiceConstants.IPV6_DHCPV6_STATELESS)
            .build();

    @Inject
    public NeutronSubnetChangeListener(final DataBroker dataBroker, IfMgr ifMgr) {
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnet> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet.class);
    }

    @Override
    protected void add(InstanceIdentifier<Subnet> identifier, Subnet input) {
        if (IPV_MAP.get(input.getIpVersion()).equals(Ipv6ServiceConstants.IP_VERSION_V6)) {
            LOG.info("Add Subnet notification handler is invoked {} ", input);
            String ipv6AddrMode = "";
            if (input.getIpv6AddressMode() != null) {
                ipv6AddrMode = DHCPV6_MAP.get(input.getIpv6AddressMode());
            }
            String ipv6RaMode = "";
            if (input.getIpv6RaMode() != null) {
                ipv6RaMode = DHCPV6_MAP.get(input.getIpv6RaMode());
            }
            ifMgr.addSubnet(input.getUuid(), input.getName(),
                    input.getTenantId(), input.getGatewayIp(), IPV_MAP.get(input.getIpVersion()),
                    input.getCidr(), ipv6AddrMode, ipv6RaMode);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Subnet> identifier, Subnet input) {
        ifMgr.removeSubnet(input.getUuid());
    }

    @Override
    protected void update(InstanceIdentifier<Subnet> identifier, Subnet original, Subnet update) {
        LOG.debug("Update Subnet notification handler is invoked Original: {}, Update: {}", original, update);
    }

    @Override
    protected NeutronSubnetChangeListener getDataTreeChangeListener() {
        return NeutronSubnetChangeListener.this;
    }

}
