/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import com.google.common.collect.ImmutableMap;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String,
            Map<String, Long>> idCache = ImmutableMap.of(//
                    PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME, new ConcurrentHashMap<String, Long>(),
                    PolicyServiceConstants.POLICY_GROUP_POOL_NAME, new ConcurrentHashMap<String, Long>());

    @Inject
    public PolicyIdManager(final IdManagerService idManager) {
        this.idManager = idManager;
    }

    @PostConstruct
    public void init() {
        LOG.info("init");
        createIdPools();
    }

    public long getPolicyClassifierId(String policyClassifierName) {
        return allocateId(policyClassifierName, PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
    }

    public void releasePolicyClassifierId(String policyClassifierName) {
        releaseId(policyClassifierName, PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
    }

    public long getPolicyClassifierGroupId(String policyClassifierName, BigInteger remoteDpId) {
        return allocateId(getPolicyClassifierGroupKey(policyClassifierName, remoteDpId),
                PolicyServiceConstants.POLICY_GROUP_POOL_NAME);
    }

    public void releasePolicyClassifierGroupId(String policyClassifierName, BigInteger remoteDpId) {
        releaseId(getPolicyClassifierGroupKey(policyClassifierName, remoteDpId),
                PolicyServiceConstants.POLICY_GROUP_POOL_NAME);
    }

    public static String getPolicyClassifierGroupKey(String policyClassifier, BigInteger remoteDpId) {
        return policyClassifier + '-' + remoteDpId;
    }

    private void createIdPools() {
        createIdPool(PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME,
                PolicyServiceConstants.POLICY_CLASSIFIER_LOW_ID, PolicyServiceConstants.POLICY_CLASSIFIER_HIGH_ID);
        createIdPool(PolicyServiceConstants.POLICY_GROUP_POOL_NAME, PolicyServiceConstants.POLICY_GROUP_LOW_ID,
                PolicyServiceConstants.POLICY_GROUP_HIGH_ID);
    }

    private void createIdPool(String poolName, long lowId, long highId) {
        CreateIdPoolInput createPoolInput = new CreateIdPoolInputBuilder().setPoolName(poolName).setLow(lowId)
                .setHigh(highId).build();

        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPoolInput);
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.info("Created IdPool for {}", PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for {}", PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME, e);
        }
    }

    private long allocateId(String key, String poolName) {
        Long id = idCache.get(poolName).get(key);
        if (id != null) {
            return id;
        }

        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(key).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            Long idValue = rpcResult.getResult().getIdValue();
            if (idValue != null) {
                idCache.get(poolName).put(key, idValue);
                return idValue;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception thrown while allocating id for key {}", key);
        }

        return PolicyServiceConstants.INVALID_ID;
    }

    private void releaseId(String key, String poolName) {
        idCache.get(poolName).remove(key);
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(key).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to release {} returned with Errors {}", key, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception thrown while releasing id for key {}", key);
        }
    }
}
