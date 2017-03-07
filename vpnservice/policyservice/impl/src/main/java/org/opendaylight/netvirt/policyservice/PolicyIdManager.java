/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage allocation of policy ids and policy group ids.
 *
 */
@Singleton
public class PolicyIdManager {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyIdManager.class);

    private final IdManagerService idManager;

    @Inject
    public PolicyIdManager(final IdManagerService idManager) {
        this.idManager = idManager;
    }

    @PostConstruct
    public void init() {
        LOG.info("init");
        createIdPool();
    }

    private void createIdPool() {
        CreateIdPoolInput createPoolInput = new CreateIdPoolInputBuilder()
                .setPoolName(PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME)
                .setLow(PolicyServiceConstants.POLICY_CLASSIFIER_LOW_ID)
                .setHigh(PolicyServiceConstants.POLICY_CLASSIFIER_HIGH_ID).build();

        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPoolInput);
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.info("Created IdPool for {}", PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for {}", PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME, e);
        }
    }

    public long getPolicyClassifierId(String policyClassifierName) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME).setIdKey(policyClassifierName).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception thrown while allocating id for key {}", policyClassifierName);
        }

        return PolicyServiceConstants.INVALID_ID;
    }

    public void releasePolicyClassifierId(String policyClassifierName) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder()
                .setPoolName(PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME).setIdKey(policyClassifierName).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to release {} returned with Errors {}", policyClassifierName, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception thrown while releasing id for key {}", policyClassifierName);
        }
    }

}
