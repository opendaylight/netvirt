/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;

/**
 * Interface for an AclInterface cache.
 *
 * @author Thomas Pantelis
 */
public interface AclInterfaceCache {

    /**
     * Adds a new AclInterface if not already existing, otherwise updates the existing instance. In either case,
     * the given updateFunction is used to build a new instance.
     *
     * @param interfaceId the interface Id
     * @param updateFunction the function used to build a new instance from the provided AclInterface.Builder. If an
     *                       instance already exists in the cache, the previous instance is passed to the function and
     *                       the Builder is initially populated from the existing instance.
     * @return the new or updated AclInterface
     */
    @Nonnull
    AclInterface addOrUpdate(@Nonnull String interfaceId,
            @Nonnull BiConsumer<AclInterface, AclInterface.Builder> updateFunction);

    /**
     * Updates an existing AclInterface instance in the cache. The given updateFunction is used to build a new instance.
     *
     * @param interfaceId the interface Id
     * @param updateFunction the function used to build a new instance from the provided AclInterface.Builder. The
     *                       previous instance is passed to the function and the Builder is initially populated from
     *                       the existing instance. The function returns a boolean indicating if any updates were
     *                       actually made.
     * @return the updated AclInterface or null if no instance was present or no updates were made
     */
    @Nullable
    AclInterface updateIfPresent(@Nonnull String interfaceId,
            @Nonnull BiFunction<AclInterface, AclInterface.Builder, Boolean> updateFunction);

    /**
     * Removes an AclInterface instance from the cache.
     *
     * @param interfaceId the interface Id
     * @return the AclInterface if present, null oherwise
     */
    AclInterface remove(@Nonnull String interfaceId);

    /**
     * Gets an AclInterface instance from the cache if present.
     *
     * @param interfaceId the interface Id
     * @return the AclInterface instance if found, null otherwise
     */
    @Nullable
    AclInterface get(@Nonnull String interfaceId);

    @Nonnull
    Collection<Entry<String, AclInterface>> entries();
}
