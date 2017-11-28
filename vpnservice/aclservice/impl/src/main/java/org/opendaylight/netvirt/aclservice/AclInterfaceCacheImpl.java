/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.inject.Singleton;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the AclInterfaceCache interface.
 *
 * @author Thomas Pantelis
 */
@Singleton
public class AclInterfaceCacheImpl implements AclInterfaceCache {
    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceCacheImpl.class);

    private final ConcurrentMap<String, AclInterface> cache = new ConcurrentHashMap<>();

    @Override
    public AclInterface addOrUpdate(@Nonnull String interfaceId,
            BiConsumer<AclInterface, AclInterface.Builder> updateFunction) {
        while (true) {
            AclInterface aclInterface = cache.computeIfPresent(interfaceId,
                (key, prevAclInterface) -> {
                    Builder builder = AclInterface.builder(prevAclInterface);
                    updateFunction.accept(prevAclInterface, builder);
                    return builder.build();
                });

            if (aclInterface == null) {
                Builder builder = AclInterface.builder();
                builder.interfaceId(interfaceId);
                updateFunction.accept(null, builder);
                aclInterface = builder.build();
                if (cache.putIfAbsent(interfaceId, aclInterface) == null) {
                    return aclInterface;
                }
            } else {
                return aclInterface;
            }
        }
    }

    @Override
    public AclInterface updateIfPresent(String interfaceId,
            BiFunction<AclInterface, AclInterface.Builder, Boolean> updateFunction) {
        final AtomicBoolean updated = new AtomicBoolean(false);
        AclInterface aclInterface =  cache.computeIfPresent(interfaceId,
            (key, prevAclInterface) -> {
                Builder builder = AclInterface.builder(prevAclInterface);
                updated.set(updateFunction.apply(prevAclInterface, builder));
                return builder.build();
            });

        return updated.get() ? aclInterface : null;
    }

    @Override
    public void remove(String interfaceId) {
        AclInterface aclInterface = cache.get(interfaceId);
        if (aclInterface == null) {
            LOG.debug("AclInterface object not found in cache for interface {}", interfaceId);
            return;
        }

        if (aclInterface.isMarkedForDelete()) {
            cache.remove(interfaceId);
        } else {
            aclInterface.setIsMarkedForDelete(true);
        }
    }

    @Override
    public AclInterface get(String interfaceId) {
        return cache.get(interfaceId);
    }

    @Override
    public Collection<Entry<String, AclInterface>> entries() {
        return new ArrayList<>(cache.entrySet());
    }
}
