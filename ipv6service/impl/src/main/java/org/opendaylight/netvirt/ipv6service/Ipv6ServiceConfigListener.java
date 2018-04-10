/*
 * Copyright (c) 2016, 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.config.rev180409.Ipv6serviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class Ipv6ServiceConfigListener extends AsyncClusteredDataTreeChangeListenerBase<Ipv6serviceConfig,
        Ipv6ServiceConfigListener> {

    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceConfigListener.class);
    private final DataBroker dataBroker;
    private final IfMgr ifMgr;
    private final Ipv6serviceConfig ipv6ServiceConfig;

    /**
     * Intialize the member variables.
     *
     * @param broker the data broker instance.
     */
    @Inject
    public Ipv6ServiceConfigListener(DataBroker broker, IfMgr ifMgr, Ipv6serviceConfig ipv6ServiceConfig) {
        super(Ipv6serviceConfig.class, Ipv6ServiceConfigListener.class);
        this.dataBroker = broker;
        this.ifMgr = ifMgr;
        this.ipv6ServiceConfig = ipv6ServiceConfig;
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
    protected void remove(InstanceIdentifier<Ipv6serviceConfig> key, Ipv6serviceConfig del) {
        LOG.debug("Ipv6serviceConfig removed {}, {}", key, del);
        //Get all IPv6 interfaces and do Unsolicited RA from existing IPv6 Interface Cache
        List<IVirtualPort> virtualPorts = ifMgr.getInterfaceCache();
        for (IVirtualPort portId: virtualPorts) {
            VirtualPort port = ifMgr.getPort(portId.getIntfUUID());
            if (ipv6ServiceConfig.getIpv6RouterReachableTime() <= 2) {
                ifMgr.transmitUnsolicitedRA(port, Ipv6Constants.IPV6_RA_REACHABLE_TIME);
            } else {
                ifMgr.transmitUnsolicitedRA(port, ipv6ServiceConfig.getIpv6RouterReachableTime());
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Ipv6serviceConfig> key, Ipv6serviceConfig original,
                          Ipv6serviceConfig update) {
        LOG.debug("Ipv6serviceConfig updated {}, {}", key, update);
        if (!original.getIpv6RouterReachableTime().equals(update.getIpv6RouterReachableTime())) {
            //Get all IPv6 interfaces and do Unsolicited RA from existing IPv6 Interface Cache
            List<IVirtualPort> virtualPorts = ifMgr.getInterfaceCache();
            for (IVirtualPort portId : virtualPorts) {
                VirtualPort port = ifMgr.getPort(portId.getIntfUUID());
                if (ipv6ServiceConfig.getIpv6RouterReachableTime() <= 2) {
                    ifMgr.transmitUnsolicitedRA(port, Ipv6Constants.IPV6_RA_REACHABLE_TIME);
                } else {
                    ifMgr.transmitUnsolicitedRA(port, update.getIpv6RouterReachableTime());
                }
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<Ipv6serviceConfig> key, Ipv6serviceConfig add) {
        LOG.debug("Ipv6serviceConfig added {}, {}", key, add);
        //Get all IPv6 interfaces and do Unsolicited RA from existing IPv6 Interface Cache
        List<IVirtualPort> virtualPorts = ifMgr.getInterfaceCache();
        for (IVirtualPort portId: virtualPorts) {
            VirtualPort port = ifMgr.getPort(portId.getIntfUUID());
            if (ipv6ServiceConfig.getIpv6RouterReachableTime() <= 2) {
                ifMgr.transmitUnsolicitedRA(port, Ipv6Constants.IPV6_RA_REACHABLE_TIME);
            } else {
                ifMgr.transmitUnsolicitedRA(port, add.getIpv6RouterReachableTime());
            }
        }
    }

}
