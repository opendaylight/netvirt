/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;

@Singleton
public class NatDataUtil {

    private final Map<String,Routers> routerMap = new ConcurrentHashMap<>();

    public synchronized void addtoRouterMap(Routers router) {
        routerMap.put(router.getRouterName(), router);
    }

    public synchronized void updateRouterMap(Routers router) {
        routerMap.put(router.getRouterName(), router);
    }

    public synchronized void removeFromRouterMap(Routers router) {
        routerMap.remove(router.getRouterName());
    }

    public Routers getRouter(String routerId) {
        return routerMap.get(routerId);
    }
}
