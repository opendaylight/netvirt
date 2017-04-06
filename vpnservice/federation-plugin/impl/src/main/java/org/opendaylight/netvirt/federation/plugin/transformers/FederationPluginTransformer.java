/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.transformers;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification.ModificationType;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Transform federated dataObject.
 *
 * @param <T>
 *            dataObject
 * @param <R>
 *            root dataObject
 */
public interface FederationPluginTransformer<T extends DataObject, R extends DataObject> {

    R applyEgressTransformation(T dataObject, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications);

    Pair<InstanceIdentifier<T>, T> applyIngressTransformation(R dataObject, ModificationType modificationType,
            int generationNumber, String remoteIp);

}
