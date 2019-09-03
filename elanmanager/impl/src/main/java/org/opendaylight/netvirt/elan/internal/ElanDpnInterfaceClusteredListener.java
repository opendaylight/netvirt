/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import static java.util.Collections.emptyList;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.jobs.BcGroupUpdateJob;
import org.opendaylight.netvirt.elan.l2gw.jobs.DpnDmacJob;
import org.opendaylight.netvirt.elan.l2gw.jobs.McastUpdateJob;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanRefUtil;
import org.opendaylight.netvirt.elan.utils.ElanDmacUtils;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanDpnInterfaceClusteredListener
        extends AsyncClusteredDataTreeChangeListenerBase<DpnInterfaces, ElanDpnInterfaceClusteredListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnInterfaceClusteredListener.class);

    private final EntityOwnershipUtils entityOwnershipUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanRefUtil elanRefUtil;
    private final ElanDmacUtils elanDmacUtils;
    private final ElanItmUtils elanItmUtils;

    @Inject
    public ElanDpnInterfaceClusteredListener(EntityOwnershipUtils entityOwnershipUtils,
                                             ElanL2GatewayUtils elanL2GatewayUtils,
                                             ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                             ElanRefUtil elanRefUtil, ElanDmacUtils elanDmacUtils,
                                             ElanItmUtils elanItmUtils) {
        this.entityOwnershipUtils = entityOwnershipUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanRefUtil = elanRefUtil;
        this.elanDmacUtils = elanDmacUtils;
        this.elanItmUtils = elanItmUtils;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, this.elanRefUtil.getDataBroker());
    }

    @Override
    public InstanceIdentifier<DpnInterfaces> getWildCardPath() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class)
                .child(DpnInterfaces.class).build();
    }

    void handleUpdate(InstanceIdentifier<DpnInterfaces> id, DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(id);
        if (ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).isEmpty()) {
            LOG.debug("dpnInterface updation, no external l2 devices to update for elan {} with Dp Id {}", elanName,
                    dpnInterfaces.getDpId());
            return;
        }
        elanRefUtil.getElanClusterUtils().runOnlyInOwnerNode(elanName, "updating mcast mac upon tunnel event",
            () -> {
                elanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName);
                return emptyList();
            });
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        // this is the last dpn interface on this elan
        final String elanName = getElanName(identifier);
        //Cache need to be updated in all cluster nodes and not only by leader node .
        //Hence moved out from DJC job

        elanRefUtil.getJobCoordinator().enqueueJob(elanName + ":l2gw", () -> {
            try {
                if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                        HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                    // deleting Elan L2Gw Devices UcastLocalMacs From Dpn
                    DpnDmacJob.uninstallDmacFromL2gws(elanName, dpnInterfaces, elanL2GatewayUtils, elanRefUtil,
                            elanDmacUtils);

                    //Removing this dpn from cache to avoid race between this and local ucast mac listener
                    elanRefUtil.getElanInstanceDpnsCache().remove(getElanName(identifier), dpnInterfaces);

                    // updating remote mcast mac on l2gw devices
                    McastUpdateJob.updateAllMcastsForDpnDelete(elanName, elanL2GatewayMulticastUtils,
                            elanRefUtil.getElanClusterUtils(), dpnInterfaces.getDpId(), elanItmUtils);
                    BcGroupUpdateJob.updateAllBcGroups(elanName, elanRefUtil, elanL2GatewayMulticastUtils,
                            elanRefUtil.getDataBroker());
                }
            } finally {
                elanRefUtil.getElanInstanceDpnsCache().remove(getElanName(identifier), dpnInterfaces);
            }

            return null;
        });
    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
                          final DpnInterfaces dpnInterfaces) {
        List<String> interfaces = dpnInterfaces.getInterfaces();
        if (interfaces != null && !interfaces.isEmpty()) {
            LOG.debug("dpninterfaces update fired new size {}", interfaces.size());
            elanRefUtil.getElanInstanceDpnsCache().remove(getElanName(identifier), original);
            elanRefUtil.getElanInstanceDpnsCache().add(getElanName(identifier), dpnInterfaces);
            LOG.debug("dpninterfaces last dpn interface on this elan {} ", dpnInterfaces.key());
            // this is the last dpn interface on this elan
            handleUpdate(identifier, dpnInterfaces);
        }
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(identifier);
        elanRefUtil.getJobCoordinator().enqueueJob(elanName + ":l2gw", () -> {
            elanRefUtil.getElanInstanceDpnsCache().add(getElanName(identifier), dpnInterfaces);
            if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                ElanInstance elanInstance = elanRefUtil.getElanInstanceCache().get(elanName).orNull();
                if (elanInstance != null) {
                    BcGroupUpdateJob.updateAllBcGroups(elanName, elanRefUtil, elanL2GatewayMulticastUtils,
                            elanRefUtil.getDataBroker());
                    // updating remote mcast mac on l2gw devices
                    McastUpdateJob.updateAllMcastsForDpnAdd(elanName, elanL2GatewayMulticastUtils,
                            elanRefUtil.getElanClusterUtils());
                    DpnDmacJob.installDmacFromL2gws(elanName, dpnInterfaces, elanL2GatewayUtils, elanRefUtil,
                            elanDmacUtils);
                }
            }
            return emptyList();
        });
    }

    private static String getElanName(InstanceIdentifier<DpnInterfaces> identifier) {
        return identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
    }

    @Override
    protected ElanDpnInterfaceClusteredListener getDataTreeChangeListener() {
        return this;
    }

}
