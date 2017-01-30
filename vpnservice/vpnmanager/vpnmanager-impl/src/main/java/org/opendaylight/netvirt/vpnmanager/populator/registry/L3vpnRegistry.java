/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.populator.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L3vpnRegistry {
    private static Map<VrfEntry.EncapType, VpnPopulator> l3VpnPopulatorRegistry = new ConcurrentHashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(L3vpnRegistry.class);

    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    public void close() {
        LOG.trace("L3vpnRegistry Closed");
    }

    public static void registerL3vpnPopulator(VrfEntry.EncapType encapType, VpnPopulator vpnPopulator) {
        l3VpnPopulatorRegistry.put(encapType, vpnPopulator);
        LOG.trace("Registered VpnPopulator for {}", encapType);
    }

    public static VpnPopulator getRegisteredPopulator(VrfEntry.EncapType encapType) {
        return l3VpnPopulatorRegistry.get(encapType);
    }
}
