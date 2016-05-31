/*
 * Copyright (c) 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers;

import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipCandidateRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderEntityListener implements EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderEntityListener.class);

    private final EntityOwnershipListenerRegistration listenerRegistration;

    private EntityOwnershipCandidateRegistration candidateRegistration;

    private static AtomicBoolean hasProviderEntityOwnership = new AtomicBoolean(false);

    public ProviderEntityListener(EntityOwnershipService entityOwnershipService) {
        this.listenerRegistration =
                entityOwnershipService.registerListener(Constants.NETVIRT_OWNER_ENTITY_TYPE, this);

        //register instance entity to get the ownership of the netvirt provider
        Entity instanceEntity = new Entity(
                Constants.NETVIRT_OWNER_ENTITY_TYPE, Constants.NETVIRT_OWNER_ENTITY_TYPE);
        try {
            this.candidateRegistration = entityOwnershipService.registerCandidate(instanceEntity);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.warn("OVSDB Netvirt Provider instance entity {} was already "
                    + "registered for ownership", instanceEntity, e);
        }
    }

    public void close() {
        if (listenerRegistration != null) {
            this.listenerRegistration.close();
        }
        if (candidateRegistration != null) {
            this.candidateRegistration.close();
        }
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {
        handleOwnershipChange(ownershipChange);
    }

    private void handleOwnershipChange(EntityOwnershipChange ownershipChange) {
        if (ownershipChange.isOwner()) {
            LOG.info("*This* instance of OVSDB netvirt provider is a MASTER instance");
            hasProviderEntityOwnership.set(true);
        } else {
            LOG.info("*This* instance of OVSDB netvirt provider is a SLAVE instance");
            hasProviderEntityOwnership.set(false);
        }
    }

    public static boolean isMasterProviderInstance() {
        return hasProviderEntityOwnership.get();
    }
}