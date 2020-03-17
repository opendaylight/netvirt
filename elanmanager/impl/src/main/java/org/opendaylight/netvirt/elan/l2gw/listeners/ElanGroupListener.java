/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanGroupListener extends AsyncClusteredDataTreeChangeListenerBase<Group, ElanGroupListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanGroupListener.class);
    private final ManagedNewTransactionRunner txRunner;
    private final ElanClusterUtils elanClusterUtils;
    private final ElanUtils elanUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public ElanGroupListener(DataBroker db, ElanClusterUtils elanClusterUtils, ElanUtils elanUtils,
            ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils, ElanInstanceCache elanInstanceCache) {
        super(Group.class, ElanGroupListener.class);
        this.txRunner = new ManagedNewTransactionRunnerImpl(db);
        this.elanClusterUtils = elanClusterUtils;
        this.elanUtils = elanUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanInstanceCache = elanInstanceCache;
        registerListener(LogicalDatastoreType.CONFIGURATION, db);
        LOG.trace("ElanGroupListener registered");
    }

    @Override
    protected InstanceIdentifier<Group> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class)
                .augmentation(FlowCapableNode.class).child(Group.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Group> identifier, Group del) {
        LOG.trace("received group removed {}", del.key().getGroupId());
    }


    @Nullable
    ElanInstance getElanInstanceFromGroupId(Group update) {
        for (ElanInstance elanInstance : elanInstanceCache.getAllPresent()) {
            if (elanInstance.getElanTag() != null) {
                long elanTag = elanInstance.getElanTag().longValue();
                long elanBCGroupId = ElanUtils.getElanRemoteBroadCastGroupID(elanTag);
                if (elanBCGroupId == update.getGroupId().getValue().toJava()) {
                    return elanInstance;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Uint64 getDpnId(String node) {
        //openflow:1]
        String[] temp = node.split(":");
        if (temp.length == 2) {
            return Uint64.valueOf(temp[1]);
        }
        return null;
    }

    @Override
    protected void update(InstanceIdentifier<Group> identifier, @Nullable Group original, Group update) {
        LOG.trace("received group updated {}", update.key().getGroupId());
        final Uint64 dpnId = getDpnId(identifier.firstKeyOf(Node.class).getId().getValue());
        if (dpnId == null) {
            return;
        }

        List<L2GatewayDevice> allDevices = ElanL2GwCacheUtils.getAllElanDevicesFromCache();
        if (allDevices.isEmpty()) {
            LOG.trace("no elan devices present in cache {}", update.key().getGroupId());
            return;
        }
        int expectedElanFootprint = 0;
        final ElanInstance elanInstance = getElanInstanceFromGroupId(update);
        if (elanInstance == null) {
            LOG.trace("no elan instance is null {}", update.key().getGroupId());
            return;
        }

        final int devicesSize = ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanInstance.getElanInstanceName()).size();
        if (devicesSize == 0) {
            LOG.trace("no elan devices in elan cache {} {}", elanInstance.getElanInstanceName(),
                    update.key().getGroupId());
            return;
        }
        boolean updateGroup = false;
        List<DpnInterfaces> dpns = elanUtils.getElanDPNByName(elanInstance.getElanInstanceName());
        if (dpns.size() > 0) {
            expectedElanFootprint += dpns.size();
        } else {
            updateGroup = true;
        }
        expectedElanFootprint += devicesSize;
        if (update.getBuckets() != null && update.getBuckets().getBucket() != null) {
            if (update.getBuckets().getBucket().size() != expectedElanFootprint) {
                updateGroup = true;
            } else {
                LOG.trace("no of buckets matched perfectly {} {}", elanInstance.getElanInstanceName(),
                        update.key().getGroupId());
            }
        }
        if (updateGroup) {
            List<Bucket> bucketList = elanL2GatewayMulticastUtils.getRemoteBCGroupBuckets(elanInstance, null, dpnId, 0,
                    elanInstance.getElanTag().toJava());
            expectedElanFootprint--;//remove local bcgroup bucket
            if (bucketList.size() != expectedElanFootprint) {
                //no point in retrying if not able to meet expected foot print
                return;
            }
            LOG.trace("no of buckets mismatched {} {}", elanInstance.getElanInstanceName(),
                    update.key().getGroupId());
            elanClusterUtils.runOnlyInOwnerNode(elanInstance.getElanInstanceName(), "updating broadcast group", () -> {
                LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    confTx -> elanL2GatewayMulticastUtils.setupElanBroadcastGroups(elanInstance, dpnId, confTx)),
                    LOG, "Error setting up ELAN BGs");
                return null;
            });
        } else {
            LOG.trace("no buckets in the update {} {}", elanInstance.getElanInstanceName(),
                    update.key().getGroupId());
        }
    }

    @Override
    protected void add(InstanceIdentifier<Group> identifier, Group added) {
        LOG.trace("received group add {}", added.key().getGroupId());
        update(identifier, null/*original*/, added);
    }

    @Override
    protected ElanGroupListener getDataTreeChangeListener() {
        return this;
    }
}



