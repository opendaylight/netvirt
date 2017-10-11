/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.utils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclClusterUtilImpl implements AclClusterUtil {

    private static final Logger LOG = LoggerFactory.getLogger(AclClusterUtil.class);

    private static final String ACL_ENTITY_TYPE_FOR_OWNERSHIP = "netvirt-acl";
    private static final String ACL_ENTITY_NAME = "netvirt-acl";

    private final EntityOwnershipUtils entityOwnershipUtils;

    private EntityOwnershipCandidateRegistration candidateRegistration;

    @Inject
    public AclClusterUtilImpl(EntityOwnershipService entityOwnershipService) {
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        try {
            candidateRegistration = entityOwnershipUtils.getEntityOwnershipService().registerCandidate(
                    new Entity(ACL_ENTITY_TYPE_FOR_OWNERSHIP, ACL_ENTITY_NAME));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("Failed to register acl entity.", e);
        }
    }

    @Override
    public boolean isEntityOwner() {
        return entityOwnershipUtils.isEntityOwner(new Entity(ACL_ENTITY_TYPE_FOR_OWNERSHIP, ACL_ENTITY_NAME), 0, 1);
    }

    @PreDestroy
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());

        if (candidateRegistration != null) {
            candidateRegistration.close();
        }
    }

}
