/*
 * Copyright (c) 2017 Mellanox. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.api.utils;

import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.Hostconfig;

@Singleton
public class HostConfigCache {
    private ConcurrentHashMap<String, Hostconfig> hostConfigMap;

    @Inject
    public HostConfigCache() {
        this.hostConfigMap = new ConcurrentHashMap<>();
    }

    public void add(String hostId, Hostconfig hostConfig) {
        if (hostId != null && hostConfig != null) {
            this.hostConfigMap.put(hostId, hostConfig);
        }
    }

    public Hostconfig get(String hostId) {
        return this.hostConfigMap.get(hostId);
    }

    public void remove(String hostId) {
        this.hostConfigMap.remove(hostId);
    }
}
