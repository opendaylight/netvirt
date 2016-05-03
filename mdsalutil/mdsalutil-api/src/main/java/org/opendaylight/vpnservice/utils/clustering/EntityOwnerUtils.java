/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.utils.clustering;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.controller.md.sal.common.api.clustering.*;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.utils.SystemPropertyReader;
import org.opendaylight.vpnservice.utils.cache.CacheUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

public class EntityOwnerUtils {
    public static final String ENTITY_OWNER_CACHE = "entity.owner.cache";
    private static final Logger LOG = LoggerFactory.getLogger(EntityOwnerUtils.class);

    static {
        createEntityOwnerCache();
    }
    private static void createEntityOwnerCache() {
        if (CacheUtil.getCache(ENTITY_OWNER_CACHE) == null) {
            CacheUtil.createCache(ENTITY_OWNER_CACHE);
        }
    }

    private static String getEntity(String entityType, String entityName) {
        return entityType;
    }

    private static void updateEntityOwner(String entityType, String entityName, Boolean isOwner) {
        ConcurrentMap<String, Boolean> entityOwnerCache =
                (ConcurrentMap<String, Boolean>) CacheUtil.getCache(ENTITY_OWNER_CACHE);
        String entity = getEntity(entityType, entityName);
        if (entityOwnerCache != null) {
            LOG.trace("updating entity owner "+isOwner+ " "+entity );
            entityOwnerCache.put(entity, isOwner);
        }
    }

    public static boolean amIEntityOwner(String entityType, String entityName) {
        ConcurrentMap<String, Boolean> entityOwnerCache =
                (ConcurrentMap<String, Boolean>) CacheUtil.getCache(ENTITY_OWNER_CACHE);
        String entity = getEntity(entityType, entityName);
        boolean ret = false;
        if (entityOwnerCache != null) {
            if (entityOwnerCache.get(entity) != null) {
                ret = entityOwnerCache.get(entity);
            }
        } else {
            LOG.error("entity owner cache null");
        }
        LOG.trace("get entity owner result {} for type {}" ,ret ,entity);
        return ret;
    }

    /**
     * Registers the entityName for ownership for given entityType
     * adds a local listener which takes care of updating the cached entity status
     * @param entityOwnershipService
     * @param entityType
     * @param entityName
     * @param listener also adds this listener for ownership events if provided
     * @throws CandidateAlreadyRegisteredException
     */
    public static void registerEntityCandidateForOwnerShip  (
            EntityOwnershipService entityOwnershipService,
            String entityType, String entityName, EntityOwnershipListener listener)
            throws CandidateAlreadyRegisteredException {
        LOG.info("registering for entity ownership for type "+entityType);
        Entity candidateEntity = new Entity(entityType, entityName);
        EntityOwnershipCandidateRegistration candidateRegistration = entityOwnershipService.registerCandidate(
                candidateEntity);
        EntityOwnershipListenerRegistration listenerRegistration = entityOwnershipService.registerListener(entityType,
                entityOwnershipListener);
        if (listener != null) {
            entityOwnershipService.registerListener(entityType, listener);
        }
        LOG.info("registered for entity ownership for type "+entityType);
        //TODO track registrations for closing
    }

    private static Listener entityOwnershipListener = new Listener();
    static class Listener implements EntityOwnershipListener {

        @Override
        public void ownershipChanged(EntityOwnershipChange ownershipChange) {
            String entityType = ownershipChange.getEntity().getType();
            String entityName = ownershipChange.getEntity().getId().toString();
            LOG.info("entity ownership changed for "+entityType);
            if (ownershipChange.hasOwner() && ownershipChange.isOwner()) {
                LOG.info("entity ownership change became owner for type "+entityType);
                updateEntityOwner(entityType, entityName, Boolean.TRUE);
            } else {
                LOG.info("entity ownership lost ownership for type "+entityType);
                updateEntityOwner(entityType, entityName, Boolean.FALSE);
            }
        }
    }
}