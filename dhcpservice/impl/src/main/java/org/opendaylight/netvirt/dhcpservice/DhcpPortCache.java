/*
 * Copyright © 2015, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpPortCache {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpPortCache.class);
    private final ConcurrentHashMap<String, Port> portMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        LOG.trace("Initialize DhcpPortCache. ");
    }

    public  void put(@Nonnull  String interfaceName, Port port) {
        portMap.put(interfaceName, port);
        LOG.trace("Added the interface {} to DhcpPortCache",interfaceName);
    }

    @Nullable
    public Port get(@Nonnull String interfaceName) {
        return portMap.get(interfaceName);
    }

    public void remove(@Nonnull String interfaceName) {
        portMap.remove(interfaceName);
    }
}
