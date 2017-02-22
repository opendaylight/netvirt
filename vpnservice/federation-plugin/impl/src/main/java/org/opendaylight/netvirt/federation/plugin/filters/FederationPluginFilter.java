/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.netvirt.federation.plugin.FederatedMappings;
import org.opendaylight.netvirt.federation.plugin.PendingModificationCache;
import org.opendaylight.yangtools.yang.binding.DataObject;

/**
 * Filter federated dataObject. Federated entities filtered on the egress side
 * won't be sent to remote sites. Federated entities filtered on the ingress
 * side won't be written to the datastore.
 *
 * @param <T>
 *            dataObject
 * @param <R>
 *            root dataObject
 */
public interface FederationPluginFilter<T extends DataObject, R extends DataObject> {

    FilterResult applyEgressFilter(T dataObject, FederatedMappings federatedMappings,
            PendingModificationCache<DataTreeModification<?>> pendingModifications,
            DataTreeModification<T> dataTreeModification);

    FilterResult applyIngressFilter(String listenerKey, R dataObject);

}
