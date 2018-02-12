/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;

/**
 * Maintains a cache of elan instance name to DpnInterfaces.
 *
 * @author Thomas Pantelis
 */
@Singleton
public class ElanInstanceDpnsCache {
    private final ConcurrentMap<String, Set<DpnInterfaces>> elanInstanceToDpnsCache = new ConcurrentHashMap<>();

    public void add(@Nonnull String elanInstanceName, @Nonnull DpnInterfaces dpnInterfaces) {
        elanInstanceToDpnsCache.computeIfAbsent(elanInstanceName, key -> ConcurrentHashMap.newKeySet())
                .add(dpnInterfaces);
    }

    public void remove(@Nonnull String elanInstanceName, @Nonnull DpnInterfaces dpnInterfaces) {
        elanInstanceToDpnsCache.computeIfPresent(elanInstanceName, (key, prevInterfacesSet) -> {
            prevInterfacesSet.remove(dpnInterfaces);
            return !prevInterfacesSet.isEmpty() ? prevInterfacesSet : null;
        });
    }

    @Nonnull
    public Collection<DpnInterfaces> get(@Nonnull String elanInstanceName) {
        Set<DpnInterfaces> dpns = elanInstanceToDpnsCache.get(elanInstanceName);
        return dpns != null ? Collections.unmodifiableCollection(dpns) : Collections.emptyList();
    }
}
