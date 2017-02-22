/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class PendingModificationCache<M> {

    /**
     * liberator Identifier -> (listenerKey -> cached entity).
     */
    private final Cache<String, Map<String, Collection<M>>> pendingModifications = //
            CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    public PendingModificationCache() {

    }

    public <T extends DataObject> void add(T dataObject, String listenerKey, M modification) {
        String identifier = extractLiberatorIdentifier(dataObject);
        if (identifier == null) {
            return;
        }

        synchronized (pendingModifications) {
            Map<String, Collection<M>> modificationMap = pendingModifications.getIfPresent(identifier);
            if (modificationMap == null) {
                modificationMap = Maps.newConcurrentMap();
                pendingModifications.put(identifier, modificationMap);
            }

            Collection<M> listenerModifications = modificationMap.get(listenerKey);
            if (listenerModifications == null) {
                listenerModifications = new LinkedBlockingQueue<>();
                modificationMap.put(listenerKey, listenerModifications);
            }

            listenerModifications.add(modification);
        }
    }

    public <T extends DataObject> Map<String, Collection<M>> remove(T dataObject) {
        String identifier = extractLiberatorIdentifier(dataObject);
        // TODO to avoid conflict between keys with same value
        // from different types, should add a postfix by related types
        if (identifier == null) {
            return null;
        }

        Map<String, Collection<M>> modifications = pendingModifications.getIfPresent(identifier);
        pendingModifications.invalidate(identifier);
        return modifications;
    }

    public <T extends DataObject> Map<String, Collection<M>> get(T dataObject) {
        String identifier = extractLiberatorIdentifier(dataObject);
        return identifier != null ? pendingModifications.getIfPresent(identifier) : null;
    }

    public void cleanup() {
        pendingModifications.cleanUp();
    }

    private <T extends DataObject> String extractLiberatorIdentifier(T dataObject) {
        if (dataObject instanceof ElanInterface) {
            return ((ElanInterface) dataObject).getKey().getName();
        }
        if (dataObject instanceof Interface) {
            return ((Interface) dataObject).getKey().getName();
        }
        if (dataObject instanceof VpnInterface) {
            return ((VpnInterface) dataObject).getKey().getName();
        }

        return null;
    }

    public static boolean isLiberatorKey(String listenerKey) {
        return FederationPluginConstants.ELAN_INTERFACE_KEY.equals(listenerKey)
                || FederationPluginConstants.VPN_INTERFACE_KEY.equals(listenerKey);
    }

}
