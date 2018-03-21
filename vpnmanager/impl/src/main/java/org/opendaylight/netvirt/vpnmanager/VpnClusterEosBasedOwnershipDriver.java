/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/*
 * Copyright © 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipListener;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipListenerRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class VpnClusterEosBasedOwnershipDriver extends VpnClusterOwnershipDriverBase
        implements EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(VpnClusterEosBasedOwnershipDriver.class);
    private EntityOwnershipService entityOwnershipService;
    private EntityOwnershipListenerRegistration registeredVpnEosListener;
    private EntityOwnershipCandidateRegistration vpnEntityCandidate;

    @Inject
    public VpnClusterEosBasedOwnershipDriver(final EntityOwnershipService entityOwnershipService) {
        this.entityOwnershipService = entityOwnershipService;
    }

    @PostConstruct
    public void start() {
        // TODO: At this point it uses EntityOwnership
        // TODO: This will be enhanced in a subsequent review to elect based on specific Shard availability
        try {
            registeredVpnEosListener = entityOwnershipService.registerListener(VPN_SERVICE_ENTITY, this);
            vpnEntityCandidate = entityOwnershipService.registerCandidate(
                    new Entity(VPN_SERVICE_ENTITY, VPN_SERVICE_ENTITY));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("Failed to register entity {} with EntityOwnershipService", e.getEntity());
        }
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() throws Exception {
        if (registeredVpnEosListener != null) {
            registeredVpnEosListener.close();
        }

        if (vpnEntityCandidate != null) {
            vpnEntityCandidate.close();
        }
        LOG.info("{} closed", getClass().getSimpleName());
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange entityOwnershipChange) {
        LOG.info("VPN Service ownershipChanged: {}", entityOwnershipChange);
        if (entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().isOwner()
                || !entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().wasOwner()) {
            // If I currently got ownership (or) if there is no current owner and I was a past owner
            amIOwner = true;
        } else {
            // Neither am current owner nor am a past owner
            amIOwner = false;
        }
    }
}
