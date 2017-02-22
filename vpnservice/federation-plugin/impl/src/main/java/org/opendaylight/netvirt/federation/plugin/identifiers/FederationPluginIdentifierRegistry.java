/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.identifiers;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class FederationPluginIdentifierRegistry {

    private static final //
        Map<String, FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject>>
        IDENTIFIERS = new ConcurrentHashMap<>();
    private static final Map<String, LogicalDatastoreType> DSTYPES = new ConcurrentHashMap<>();
    private static final Map<Pair<LogicalDatastoreType, Class<? extends DataObject>>, String> LISTENERS
            = new ConcurrentHashMap<>();

    private FederationPluginIdentifierRegistry() {

    }

    public static void registerIdentifier(String listenerKey, LogicalDatastoreType datastoreType,
            FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject> identifier) {
        IDENTIFIERS.put(listenerKey, identifier);
        DSTYPES.put(listenerKey, datastoreType);
        LISTENERS.put(Pair.of(datastoreType, getSubtreeClass(identifier)), listenerKey);
    }

    public static
        FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject> getIdentifier(
            String listenerKey) {
        return IDENTIFIERS.get(listenerKey);
    }

    public static LogicalDatastoreType getDatastoreType(String listenerKey) {
        return DSTYPES.get(listenerKey);
    }

    @SuppressWarnings("unchecked")
    public static String getListenerKey(LogicalDatastoreType datastoreType, Class<? extends DataObject> subtreeClass) {
        String listenerKey = LISTENERS.get(Pair.of(datastoreType, subtreeClass));
        if (listenerKey != null) {
            return listenerKey;
        }

        Class<?>[] interfaces = subtreeClass.getInterfaces();
        if (interfaces != null) {
            for (Class<?> iface : interfaces) {
                return getListenerKey(datastoreType, (Class<? extends DataObject>) iface);

            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends DataObject> getSubtreeClass(
            FederationPluginIdentifier<? extends DataObject, ? extends DataObject, ? extends DataObject> identifier) {
        for (Type type : identifier.getClass().getGenericInterfaces()) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType().equals(FederationPluginIdentifier.class)) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments != null && actualTypeArguments.length > 1) {
                    return (Class<? extends DataObject>) actualTypeArguments[2];
                }
            }
        }

        return null;
    }

}
