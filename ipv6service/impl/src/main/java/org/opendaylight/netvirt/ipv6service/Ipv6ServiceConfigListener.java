/*
 * Copyright (c) 2018 Alten Calsoft Labs India Pvt Ltd and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev180411.Ipv6serviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class Ipv6ServiceConfigListener extends AsyncClusteredDataTreeChangeListenerBase<Ipv6serviceConfig,
        Ipv6ServiceConfigListener> {

    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceConfigListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;
    private final Ipv6PktHandler ipv6PktHandler;

    /**
     * Intialize the member variables.
     *
     * @param broker the data broker instance.
     */
    @Inject
    public Ipv6ServiceConfigListener(final DataBroker broker, final IfMgr ifMgr, Ipv6PktHandler ipv6PktHandler) {
        super(Ipv6serviceConfig.class, Ipv6ServiceConfigListener.class);
        this.dataBroker = broker;
        this.ifMgr = ifMgr;
        this.ipv6PktHandler = ipv6PktHandler;
    }

    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Ipv6serviceConfig> getWildCardPath() {
        return InstanceIdentifier.create(Ipv6serviceConfig.class);
    }

    @Override
    protected Ipv6ServiceConfigListener getDataTreeChangeListener() {
        return Ipv6ServiceConfigListener.this;
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.debug("Ipv6serviceConfig Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Ipv6serviceConfig> key, Ipv6serviceConfig del) {
        LOG.debug("REMOVE: Ipv6serviceConfig removed {}, {}", key, del);
        //Get all IPv6 router interfaces and do Unsolicited RA from existing IPv6 Interface Cache
        List<IVirtualPort> virtualPorts = ifMgr.getInterfaceCache();
        if (!virtualPorts.isEmpty()) {
            for (IVirtualPort portId : virtualPorts) {
                VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(portId.getNetworkID());
                ifMgr.transmitUnsolicitedNA(routerPort);
                ifMgr.transmitUnsolicitedRA(routerPort, Ipv6Constants.IPV6_RA_REACHABLE_TIME,
                        true);
            }
        } else {
            LOG.debug("REMOVE: Ipv6RouterReachableTime removed the value {}, but there is no IPv6 Interface "
                            + "is available to send Unsolicited RA with default router reachable time (120 Secs)",
                    del.getIpv6RouterReachableTime());
        }
    }

    @Override
    protected void update(InstanceIdentifier<Ipv6serviceConfig> key, Ipv6serviceConfig original,
                          Ipv6serviceConfig update) {
        LOG.debug("UPDATE: Ipv6RouterReachableTime updated with new value {} and old value {}",
                update.getIpv6RouterReachableTime(), original.getIpv6RouterReachableTime());
        if (!original.getIpv6RouterReachableTime().equals(update.getIpv6RouterReachableTime())) {
            //Get all IPv6 router interfaces and do Unsolicited RA from existing IPv6 Interface Cache
            List<IVirtualPort> virtualPorts = ifMgr.getInterfaceCache();
            if (!virtualPorts.isEmpty()) {
                for (IVirtualPort portId : virtualPorts) {
                    VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(portId.getNetworkID());
                    ifMgr.transmitUnsolicitedNA(routerPort);
                    ifMgr.transmitUnsolicitedRA(routerPort,
                            TimeUnit.SECONDS.toMillis(update.getIpv6RouterReachableTime()),
                            true);
                }
            } else {
                LOG.debug("UPDATE: Ipv6RouterReachableTime updated with new value {}, but there is no IPv6 Interface "
                                + "is available to send Unsolicited RA with updated router reachable time",
                        update.getIpv6RouterReachableTime());
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<Ipv6serviceConfig> key, Ipv6serviceConfig add) {
        LOG.debug("ADD: Ipv6RouterReachableTime configured with value {}", add.getIpv6RouterReachableTime());
        //Get all IPv6 router interfaces and do Unsolicited RA from existing IPv6 Interface Cache
        List<IVirtualPort> virtualPorts = ifMgr.getInterfaceCache();
        if (!virtualPorts.isEmpty()) {
            for (IVirtualPort portId : virtualPorts) {
                VirtualPort routerPort = ifMgr.getRouterV6InterfaceForNetwork(portId.getNetworkID());
                ifMgr.transmitUnsolicitedNA(routerPort);
                ifMgr.transmitUnsolicitedRA(routerPort, TimeUnit.SECONDS.toMillis(add.getIpv6RouterReachableTime()),
                        true);
            }
        } else {
            LOG.debug("ADD: Ipv6RouterReachableTime configured with value {}, but there is no IPv6 Interface is "
                            + "available to send Unsolicited RA with configured router reachable time",
                    add.getIpv6RouterReachableTime());
        }
    }

}