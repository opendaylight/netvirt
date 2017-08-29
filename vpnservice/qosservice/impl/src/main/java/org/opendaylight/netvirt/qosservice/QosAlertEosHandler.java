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
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipCandidateRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosAlertEosHandler implements EntityOwnershipListener, AutoCloseable {

    private static EntityOwnershipService entityOwnershipService;
    private static QosAlertManager qosAlertManager;
    private static EntityOwnershipListenerRegistration listenerRegistration;
    private static EntityOwnershipCandidateRegistration candidateRegistration;
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertEosHandler.class);
    private static boolean isEosOwner;

    @Inject
    public QosAlertEosHandler(final EntityOwnershipService eos, final QosAlertManager qam) {
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
        listenerRegistration.close();
        candidateRegistration.close();
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
                    + "registered for ownership", instanceEntity, e);
        }
        LOG.trace("entity ownership registeration successful");
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange entityOwnershipChange) {
        LOG.trace("ownershipChanged: {}", entityOwnershipChange);

        if ((entityOwnershipChange.hasOwner() && entityOwnershipChange.isOwner())
                || (!entityOwnershipChange.hasOwner() && entityOwnershipChange.wasOwner())) {
            qosAlertManager.setQosAlertOwner(true); // continue polling until new owner is elected
            isEosOwner = true;
        } else {
            qosAlertManager.setQosAlertOwner(false); // no longer an owner
            isEosOwner = false;
        }
    }

    public static boolean isQosClusterOwner() {
        LOG.trace("isQosClusterOwner: {}", isEosOwner);
        return (isEosOwner);
    }
}
