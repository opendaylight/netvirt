/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipChange;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipListener;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipListenerRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6ServiceEosHandler implements EntityOwnershipListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceEosHandler.class);
    private static final String EOS_ENTITY_OWNER = "netvirt-ipv6service-owner-entity";

    private volatile boolean isEosOwner;

    private final EntityOwnershipService entityOwnershipService;
    private EntityOwnershipListenerRegistration listenerRegistration;
    private EntityOwnershipCandidateRegistration candidateRegistration;

    @Inject
    public Ipv6ServiceEosHandler(final EntityOwnershipService eos) {
        entityOwnershipService = eos;
    }

    @PostConstruct
    public void init() {
        registerEosListener();
    }

    @Override
    @PreDestroy
    public void close() {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }

        if (candidateRegistration != null) {
            candidateRegistration.close();
        }
        LOG.trace("Entity ownership unregistered");
    }

    private void registerEosListener() {
        listenerRegistration =
                entityOwnershipService.registerListener(EOS_ENTITY_OWNER, this);
        Entity instanceEntity = new Entity(
                EOS_ENTITY_OWNER, EOS_ENTITY_OWNER);
        try {
            candidateRegistration = entityOwnershipService.registerCandidate(instanceEntity);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.warn("Instance entity {} was already registered", instanceEntity);
        }
        LOG.trace("Entity ownership registration successful");
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange entityOwnershipChange) {
        LOG.trace("ownershipChanged: {}", entityOwnershipChange);

        if (entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().isOwner()
                || !entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().wasOwner()) {
            isEosOwner = true;
        } else {
            isEosOwner = false;
        }
    }

    public boolean isClusterOwner() {
        LOG.trace("isClusterOwner: {}", isEosOwner);
        return isEosOwner;
    }
}
