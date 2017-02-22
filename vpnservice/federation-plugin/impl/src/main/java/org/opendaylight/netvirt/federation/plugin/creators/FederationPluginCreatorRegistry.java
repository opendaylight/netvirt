/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.creators;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class FederationPluginCreatorRegistry {

    private static final Map<String, FederationPluginModificationCreator<? extends DataObject, ? extends DataObject>>
        CREATORS = new ConcurrentHashMap<>();

    private FederationPluginCreatorRegistry() {

    }

    public static void registerCreator(String listenerKey,
            FederationPluginModificationCreator<? extends DataObject, ? extends DataObject> creator) {
        CREATORS.put(listenerKey, creator);
    }

    public static FederationPluginModificationCreator<? extends DataObject, ? extends DataObject> getCreator(
            String listenerKey) {
        return CREATORS.get(listenerKey);
    }
}
