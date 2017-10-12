/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

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

    private static volatile boolean isEosOwner;

    private final EntityOwnershipService entityOwnershipService;
    private final QosAlertManager qosAlertManager;
    private EntityOwnershipListenerRegistration listenerRegistration;
    private EntityOwnershipCandidateRegistration candidateRegistration;

    @Inject
    public QosEosHandler(final EntityOwnershipService eos, final QosAlertManager qam) {
        entityOwnershipService = eos;
        qosAlertManager = qam;
        isEosOwner = false;
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

    private void registerQosAlertEosListener() {
        listenerRegistration =
                entityOwnershipService.registerListener(QosConstants.QOS_ALERT_OWNER_ENTITY_TYPE, this);
        Entity instanceEntity = new Entity(
                QosConstants.QOS_ALERT_OWNER_ENTITY_TYPE, QosConstants.QOS_ALERT_OWNER_ENTITY_TYPE);
        try {
            candidateRegistration = entityOwnershipService.registerCandidate(instanceEntity);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.warn("qosalert instance entity {} was already "
                    + "registered for ownership", instanceEntity);
        }
        LOG.trace("entity ownership registeration successful");
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange entityOwnershipChange) {
        LOG.trace("ownershipChanged: {}", entityOwnershipChange);

        if (entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().isOwner()
                || !entityOwnershipChange.getState().hasOwner() && entityOwnershipChange.getState().wasOwner()) {
            qosAlertManager.setQosAlertOwner(true); // continue polling until new owner is elected
            isEosOwner = true;
        } else {
            qosAlertManager.setQosAlertOwner(false); // no longer an owner
            isEosOwner = false;
        }
    }

    public static boolean isQosClusterOwner() {
        LOG.trace("isQosClusterOwner: {}", isEosOwner);
        return isEosOwner;
    }
}
