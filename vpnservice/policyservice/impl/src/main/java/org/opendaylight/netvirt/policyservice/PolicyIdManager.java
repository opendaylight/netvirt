/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.opendaylight.genius.itm.globals.ITMConstants;
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

@Singleton
public class PolicyIdManager {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyIdManager.class);

    private final IdManagerService idManager;

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
                LOG.info("Created IdPool for NextHopPointerPool");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for NextHopPointerPool", e);
        }
    }

    public long getPolicyClassifierId(String policyClassifierName) {
        return allocateId(policyClassifierName, PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
    }

    public void releasePolicyClassifierId(String policyClassifierName) {
        releaseId(policyClassifierName, PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
    }

    public long getPolicyClassifierGroupId(String policyClassifier, BigInteger dpId) {
        return allocateId(policyClassifier + '-' + dpId, ITMConstants.VXLAN_GROUP_POOL_NAME);
    }

    public void releasePolicyClassifierGroupId(String policyClassifierName) {
        releaseId(policyClassifierName, PolicyServiceConstants.POLICY_CLASSIFIER_POOL_NAME);
    }

    private long allocateId(String key, String poolName) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(key).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception thrown while allocating id for key {}", key);
        }

        return PolicyServiceConstants.INVALID_ID;
    }

    private void releaseId(String key, String poolName) {
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
