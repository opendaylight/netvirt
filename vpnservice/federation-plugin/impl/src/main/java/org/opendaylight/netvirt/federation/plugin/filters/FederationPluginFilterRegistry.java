/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class FederationPluginFilterRegistry {

    private static final Map<String, FederationPluginFilter<? extends DataObject, ? extends DataObject>>
        FILTERS = new ConcurrentHashMap<>();

    private FederationPluginFilterRegistry() {

    }

    public static void registerFilter(String listenerKey,
            FederationPluginFilter<? extends DataObject, ? extends DataObject> filter) {
        FILTERS.put(listenerKey, filter);
    }

    public static FederationPluginFilter<? extends DataObject, ? extends DataObject> getFilter(String listenerKey) {
        return FILTERS.get(listenerKey);
    }
}
