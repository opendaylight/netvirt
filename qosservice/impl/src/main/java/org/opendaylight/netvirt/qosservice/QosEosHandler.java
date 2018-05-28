/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
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
public class QosEosHandler implements EntityOwnershipListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(QosEosHandler.class);

    private volatile boolean isEosOwner;

    private final EntityOwnershipService entityOwnershipService;
    private EntityOwnershipListenerRegistration listenerRegistration;
    private EntityOwnershipCandidateRegistration candidateRegistration;
    private final List<Consumer<Boolean>> localOwnershipChangedListeners = new CopyOnWriteArrayList<>();

    @Inject
    public QosEosHandler(final EntityOwnershipService eos) {
        entityOwnershipService = eos;
    }

    @PostConstruct
    public void init() {
        registerQosAlertEosListener();
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
        LOG.trace("entity ownership unregisterated");
    }

    public void addLocalOwnershipChangedListener(Consumer<Boolean> listener) {
        localOwnershipChangedListeners.add(listener);
    }

    private void registerQosAlertEosListener() {
        listenerRegistration =
                entityOwnershipService.registerListener(QosConstants.QOS_ALERT_OWNER_ENTITY_TYPE, this);
        Entity instanceEntity = new Entity(
                QosConstants.QOS_ALERT_OWNER_ENTITY_TYPE, QosConstants.QOS_ALERT_OWNER_ENTITY_TYPE);
        try {
            candidateRegistration = entityOwnershipService.registerCandidate(instanceEntity);
            LOG.trace("entity ownership registeration successful");
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.trace("qosalert instance entity {} was already registered for ownership", instanceEntity);
        }
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange entityOwnershipChange) {
        LOG.trace("ownershipChanged: {}", entityOwnershipChange);

        if (entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().isOwner()
                || !entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().wasOwner()) {
            localOwnershipChangedListeners.forEach(l -> l.accept(Boolean.TRUE));
            isEosOwner = true;
        } else {
            localOwnershipChangedListeners.forEach(l -> l.accept(Boolean.FALSE));
            isEosOwner = false;
        }
    }

    public boolean isQosClusterOwner() {
        LOG.trace("isQosClusterOwner: {}", isEosOwner);
        return isEosOwner;
    }
}
