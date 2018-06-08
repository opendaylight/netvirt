/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronPortChangeListener extends AsyncClusteredDataTreeChangeListenerBase<Port,
        NeutronPortChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    @Inject
    public NeutronPortChangeListener(final DataBroker dataBroker, IfMgr ifMgr) {
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port port) {
        if (port.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY)) {
            // Todo: revisit when IPv6 north-south support is implemented.
            LOG.info("IPv6Service (TODO): Skipping router_gateway port {} for add event", port);
            return;
        }

        LOG.debug("Add port notification handler is invoked for port {} ", port);
        List<FixedIps> ipList = port.getFixedIps();
        for (FixedIps fixedip : ipList) {
            if (fixedip.getIpAddress().getIpv4Address() != null) {
                continue;
            }
            addInterfaceInfo(port, fixedip);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port port) {
        if (port.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY)) {
            // Todo: revisit when IPv6 north-south support is implemented.
            LOG.info("IPv6Service (TODO): Skipping router_gateway port {} for remove event", port);
            return;
        }

        LOG.debug("remove port notification handler is invoked for port {}", port);
        ifMgr.removePort(port.getUuid());
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        if (update.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY)) {
            // Todo: revisit when IPv6 north-south support is implemented.
            LOG.info("IPv6Service (TODO): Skipping router_gateway port {} for update event", update);
            return;
        }

        LOG.debug("update port notification handler is invoked for port {} ", update);

        Set<FixedIps> oldIPs = getFixedIpSet(original.getFixedIps());
        Set<FixedIps> newIPs = getFixedIpSet(update.getFixedIps());
        if (!oldIPs.equals(newIPs)) {
            Boolean portIncludesV6Address = Boolean.FALSE;
            ifMgr.clearAnyExistingSubnetInfo(update.getUuid());
            List<FixedIps> ipList = update.getFixedIps();
            for (FixedIps fixedip : ipList) {
                if (fixedip.getIpAddress().getIpv4Address() != null) {
                    continue;
                }
                portIncludesV6Address = Boolean.TRUE;
                addInterfaceInfo(update, fixedip);
            }

            if (update.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE)) {
                ifMgr.updateRouterIntf(update.getUuid(), new Uuid(update.getDeviceId()), update.getFixedIps());
            } else {
                ifMgr.updateHostIntf(update.getUuid(), portIncludesV6Address);
            }
        }
    }

    protected void addInterfaceInfo(Port port, FixedIps fixedip) {
        if (port.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE)) {
            LOG.info("IPv6: addInterfaceInfo is invoked for a router interface {}, fixedIp: {}", port, fixedip);
            // Add router interface
            ifMgr.addRouterIntf(port.getUuid(),
                    new Uuid(port.getDeviceId()),
                    fixedip.getSubnetId(),
                    port.getNetworkId(),
                    fixedip.getIpAddress(),
                    port.getMacAddress().getValue(),
                    port.getDeviceOwner());
        } else {
            LOG.info("IPv6: addInterfaceInfo is invoked for a host interface {}, fixedIp: {}", port, fixedip);
            // Add host interface
            ifMgr.addHostIntf(port.getUuid(),
                    fixedip.getSubnetId(),
                    port.getNetworkId(),
                    fixedip.getIpAddress(),
                    port.getMacAddress().getValue(),
                    port.getDeviceOwner());
        }
    }

    private Set<FixedIps> getFixedIpSet(List<FixedIps> fixedIps) {
        return fixedIps != null ? new HashSet<>(fixedIps) : Collections.emptySet();
    }

    @Override
    protected NeutronPortChangeListener getDataTreeChangeListener() {
        return NeutronPortChangeListener.this;
    }
}
