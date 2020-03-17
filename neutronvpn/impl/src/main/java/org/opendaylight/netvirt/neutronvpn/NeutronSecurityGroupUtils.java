/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class NeutronSecurityGroupUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSecurityGroupUtils.class);

    private final IdManagerService idManager;

    @Inject
    public NeutronSecurityGroupUtils(final IdManagerService idManager) {
        this.idManager = idManager;
    }

    /**
     * Creates remote acl id pools.
     */
    public void createAclIdPool() {
        createIdPoolForAclTag(NeutronSecurityGroupConstants.ACL_TAG_POOL_NAME);
    }

    /**
     * Creates id pool for ACL tag.
     *
     * @param poolName the pool name
     */
    public void createIdPoolForAclTag(String poolName) {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
                .setPoolName(poolName).setLow(NeutronSecurityGroupConstants.ACL_TAG_POOL_START)
                .setHigh(NeutronSecurityGroupConstants.ACL_TAG_POOL_END).build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Created IdPool for {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create ID pool [{}] for remote ACL ids", poolName, e);
            throw new RuntimeException("Failed to create ID pool [{}] for remote ACL ids", e);
        }
    }

    /**
     * Deletes remote acl id pools.
     */
    public void deleteAclIdPool() {
        deleteIdPool(NeutronSecurityGroupConstants.ACL_TAG_POOL_NAME);
    }

    /**
     * Deletes id pool.
     *
     * @param poolName the pool name
     */
    public void deleteIdPool(String poolName) {
        DeleteIdPoolInput deletePool = new DeleteIdPoolInputBuilder().setPoolName(poolName).build();
        try {
            Future<RpcResult<DeleteIdPoolOutput>> result = idManager.deleteIdPool(deletePool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Deleted IdPool for {}", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to delete ID pool [{}]", poolName, e);
            throw new RuntimeException("Failed to delete ID pool [" + poolName + "]", e);
        }
    }

    /**
     * Allocates ACL tag.
     *
     * @param aclName the ACL name
     * @return the integer
     */
    public Integer allocateAclTag(String aclName) {
        Integer aclTag = allocateId(NeutronSecurityGroupConstants.ACL_TAG_POOL_NAME, aclName,
                NeutronSecurityGroupConstants.INVALID_ACL_TAG);
        return aclTag;
    }

    /**
     * Releases ACL tag.
     *
     * @param aclName the ACL name
     */
    public void releaseAclTag(String aclName) {
        releaseId(NeutronSecurityGroupConstants.ACL_TAG_POOL_NAME, aclName);
    }

    public Integer allocateId(String poolName, String idKey, Integer defaultId) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                Integer allocatedId = rpcResult.getResult().getIdValue().intValue();
                LOG.debug("Allocated ACL ID: {} with key: {} into pool: {}", allocatedId, idKey, poolName);
                return allocatedId;
            } else {
                LOG.error("RPC Call to Get Unique Id for key {} from pool {} returned with Errors {}",
                        idKey, poolName, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting Unique Id for key {} from pool {} ", idKey, poolName, e);
        }
        return defaultId;
    }

    public void releaseId(String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<ReleaseIdOutput>> result = idManager.releaseId(idInput);
            RpcResult<ReleaseIdOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to release Id with Key {} from pool {} returned with Errors {}",
                        idKey, poolName, rpcResult.getErrors());
            } else {
                LOG.debug("Released ACL ID with key: {} from pool: {}", idKey, poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when releasing Id for key {} from pool {} ", idKey, poolName, e);
        }
    }

    public Acl getAcl(DataBroker broker, InstanceIdentifier<Acl> aclInstanceIdentifier) {
        return MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, aclInstanceIdentifier, broker).orElse(null);
    }
}
