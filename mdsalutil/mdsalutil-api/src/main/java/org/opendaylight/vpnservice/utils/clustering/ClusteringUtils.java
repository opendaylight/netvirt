/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.utils.clustering;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipState;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class ClusteringUtils {

    public static boolean isNodeEntityOwner(EntityOwnershipService entityOwnershipService, String entityType,
            String nodeId) {
        Entity entity = new Entity(entityType, nodeId);
        Optional<EntityOwnershipState> entityState = entityOwnershipService.getOwnershipState(entity);
        if (entityState.isPresent()) {
            return entityState.get().isOwner();
        }
        return false;
    }

    public static boolean isNodeEntityOwner(EntityOwnershipService entityOwnershipService, String entityType,
            YangInstanceIdentifier nodeId) {
        Entity entity = new Entity(entityType, nodeId);
        Optional<EntityOwnershipState> entityState = entityOwnershipService.getOwnershipState(entity);
        if (entityState.isPresent()) {
            return entityState.get().isOwner();
        }
        return false;
    }
}
