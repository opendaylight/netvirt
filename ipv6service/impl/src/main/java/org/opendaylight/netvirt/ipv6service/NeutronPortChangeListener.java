/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import com.google.common.base.Strings;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronPortChangeListener extends AbstractClusteredAsyncDataTreeChangeListener<Port> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;

    @Inject
    public NeutronPortChangeListener(final DataBroker dataBroker, IfMgr ifMgr) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Ports.class).child(Port.class),
                Executors.newListeningSingleThreadExecutor("NeutronPortChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.ifMgr = ifMgr;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<Port> identifier, Port port) {
        if (Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY.equalsIgnoreCase(port.getDeviceOwner())) {
            // Todo: revisit when IPv6 north-south support is implemented.
            LOG.info("IPv6Service (TODO): Skipping router_gateway port {} for add event", port);
            return;
        }
        if (Ipv6ServiceConstants.DEVICE_OWNER_DHCP.equalsIgnoreCase(port.getDeviceOwner())) {
            LOG.info("IPv6Service: Skipping network_dhcp port {} for add event", port);
            return;
        }

        LOG.debug("Add port notification handler is invoked for port {} ", port);
        for (FixedIps fixedip : port.nonnullFixedIps()) {
            if (fixedip.getIpAddress().getIpv4Address() != null) {
                continue;
            }
            addInterfaceInfo(port, fixedip);
        }
    }

    @Override
    public void remove(InstanceIdentifier<Port> identifier, Port port) {
        if (Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY.equalsIgnoreCase(port.getDeviceOwner())) {
            // Todo: revisit when IPv6 north-south support is implemented.
            LOG.info("IPv6Service (TODO): Skipping router_gateway port {} for remove event", port);
            return;
        }
        if (Ipv6ServiceConstants.DEVICE_OWNER_DHCP.equalsIgnoreCase(port.getDeviceOwner())) {
            LOG.info("IPv6Service: Skipping network_dhcp port {} for remove event", port);
            return;
        }

        LOG.debug("remove port notification handler is invoked for port {}", port);
        ifMgr.removePort(port.getUuid());
    }

    @Override
    public void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        if (Ipv6ServiceConstants.NETWORK_ROUTER_GATEWAY.equalsIgnoreCase(update.getDeviceOwner())) {
            // Todo: revisit when IPv6 north-south support is implemented.
            LOG.info("IPv6Service (TODO): Skipping router_gateway port {} for update event", update);
            return;
        }
        if (Ipv6ServiceConstants.DEVICE_OWNER_DHCP.equalsIgnoreCase(update.getDeviceOwner())) {
            LOG.info("IPv6Service: Skipping network_dhcp port {} for update event", update);
            return;
        }

        LOG.debug("update port notification handler is invoked for port {} ", update);

        Set<FixedIps> ipsBefore = getFixedIpSet(original.getFixedIps());
        Set<FixedIps> ipsAfter = getFixedIpSet(update.getFixedIps());

        Set<FixedIps> deletedIps = new HashSet<>(ipsBefore);
        deletedIps.removeAll(ipsAfter);

        if (!ipsBefore.equals(ipsAfter)) {
            Boolean portIncludesV6Address = Boolean.FALSE;
            ifMgr.clearAnyExistingSubnetInfo(update.getUuid());

            Set<FixedIps> remainingIps = new HashSet<>(ipsAfter);
            remainingIps.removeAll(deletedIps);
            for (FixedIps fixedip : remainingIps) {
                if (fixedip.getIpAddress().getIpv4Address() != null) {
                    continue;
                }
                portIncludesV6Address = Boolean.TRUE;
                addInterfaceInfo(update, fixedip);
            }

            if (update.getDeviceOwner().equalsIgnoreCase(Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE)) {
                ifMgr.updateRouterIntf(update.getUuid(), new Uuid(update.getDeviceId()), update.getFixedIps(),
                        deletedIps);
            } else {
                ifMgr.updateHostIntf(update.getUuid(), portIncludesV6Address);
            }
        }
        //Neutron Port update with proper device owner information
        if ((Strings.isNullOrEmpty(original.getDeviceOwner()) || Strings.isNullOrEmpty(original.getDeviceId()))
                && !Strings.isNullOrEmpty(update.getDeviceOwner()) && !Strings.isNullOrEmpty(update.getDeviceId())) {
            for (FixedIps fixedip : update.nonnullFixedIps()) {
                if (fixedip.getIpAddress().getIpv4Address() != null) {
                    continue;
                }
                addInterfaceInfo(update, fixedip);
            }
        }
    }

    protected void addInterfaceInfo(Port port, FixedIps fixedip) {
        if (Ipv6ServiceConstants.NETWORK_ROUTER_INTERFACE.equalsIgnoreCase(port.getDeviceOwner())) {
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

    private Set<FixedIps> getFixedIpSet(@Nullable List<FixedIps> fixedIps) {
        return fixedIps != null ? new HashSet<>(fixedIps) : Collections.emptySet();
    }
}
