/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.cache;

import com.google.common.base.Optional;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches ElanInterface instances by name and the set of ElanInterfacenames by elen instance name.
 *
 * @author Thomas Pantelis
 */
@Singleton
public class ElanInterfaceCache extends InstanceIdDataObjectCache<ElanInterface> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanInterfaceCache.class);

    private final Map<String, Set<String>> elanInstanceToInterfacesCache = new ConcurrentHashMap<>();

    @Inject
    public ElanInterfaceCache(DataBroker dataBroker, CacheProvider cacheProvider) {
        super(ElanInterface.class, dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class), cacheProvider);
    }

    @Nonnull
    public Optional<ElanInterface> get(@Nonnull String interfaceName) {
        try {
            return get(ElanUtils.getElanInterfaceConfigurationDataPathId(interfaceName));
        } catch (ReadFailedException e) {
            LOG.warn("Error reading ElanInterface {}", interfaceName, e);
            return Optional.absent();
        }
    }

    @Nonnull
    public Optional<EtreeInterface> getEtreeInterface(@Nonnull String interfaceName) {
        Optional<ElanInterface> elanInterface = get(interfaceName);
        return elanInterface.isPresent() ? Optional.fromNullable(
                elanInterface.get().getAugmentation(EtreeInterface.class)) : Optional.absent();
    }

    @Nonnull
    public Collection<String> getInterfaceNames(@Nonnull String elanInstanceName) {
        Set<String> removed = elanInstanceToInterfacesCache.remove(elanInstanceName);
        return removed != null ? Collections.unmodifiableCollection(removed) : Collections.emptySet();
    }

    @Override
    protected void added(InstanceIdentifier<ElanInterface> path, ElanInterface elanInterface) {
        elanInstanceToInterfacesCache.computeIfAbsent(elanInterface.getElanInstanceName(),
            key -> ConcurrentHashMap.newKeySet()).add(elanInterface.getName());
    }

    @Override
    protected void removed(InstanceIdentifier<ElanInterface> path, ElanInterface elanInterface) {
        String elanInstanceName = elanInterface.getElanInstanceName();
        elanInstanceToInterfacesCache.computeIfPresent(elanInstanceName , (key, prevInterfacesSet) -> {
            prevInterfacesSet.remove(elanInterface.getName());
            return !prevInterfacesSet.isEmpty() ? prevInterfacesSet : null;
        });
    }
}
