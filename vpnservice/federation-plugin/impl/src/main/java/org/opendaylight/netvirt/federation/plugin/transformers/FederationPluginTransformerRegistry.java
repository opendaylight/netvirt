/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.transformers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class FederationPluginTransformerRegistry {

    private static final Map<String, FederationPluginTransformer<? extends DataObject, ? extends DataObject>>
        TRANSFORMERS = new ConcurrentHashMap<>();

    private FederationPluginTransformerRegistry() {

    }

    public static void registerTransformer(String listenerKey,
            FederationPluginTransformer<? extends DataObject, ? extends DataObject> transformer) {
        TRANSFORMERS.put(listenerKey, transformer);
    }

    public static FederationPluginTransformer<? extends DataObject, ? extends DataObject> getTransformer(
            String listenerKey) {
        return TRANSFORMERS.get(listenerKey);
    }
}
