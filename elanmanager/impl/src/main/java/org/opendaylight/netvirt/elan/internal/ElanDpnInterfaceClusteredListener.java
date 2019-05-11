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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.cache.ElanInstanceDpnsCache;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
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

    private final DataBroker broker;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final JobCoordinator jobCoordinator;
    private final ElanInstanceCache elanInstanceCache;
    private final ElanInstanceDpnsCache elanInstanceDpnsCache;

    @Inject
    public ElanDpnInterfaceClusteredListener(DataBroker broker, EntityOwnershipUtils entityOwnershipUtils,
                                             ElanL2GatewayUtils elanL2GatewayUtils,
                                             ElanClusterUtils elanClusterUtils, JobCoordinator jobCoordinator,
                                             ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                             ElanInstanceCache elanInstanceCache,
                                             ElanInstanceDpnsCache elanInstanceDpnsCache) {
        this.broker = broker;
        this.entityOwnershipUtils = entityOwnershipUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.jobCoordinator = jobCoordinator;
        this.elanInstanceCache = elanInstanceCache;
        this.elanInstanceDpnsCache = elanInstanceDpnsCache;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, this.broker);
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
        elanClusterUtils.runOnlyInOwnerNode(elanName, "updating mcast mac upon tunnel event",
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

        jobCoordinator.enqueueJob(elanName + ":l2gw", () -> {
            try {
                if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                        HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                    // deleting Elan L2Gw Devices UcastLocalMacs From Dpn
                    elanL2GatewayUtils.deleteElanL2GwDevicesUcastLocalMacsFromDpn(elanName,
                            dpnInterfaces.getDpId());

                    //Removing this dpn from cache to avoid race between this and local ucast mac listener
                    elanInstanceDpnsCache.remove(getElanName(identifier), dpnInterfaces);

                    // updating remote mcast mac on l2gw devices
                    elanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName);
                }
            } finally {
                elanInstanceDpnsCache.remove(getElanName(identifier), dpnInterfaces);
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
            elanInstanceDpnsCache.remove(getElanName(identifier), dpnInterfaces);
            LOG.debug("dpninterfaces last dpn interface on this elan {} ", dpnInterfaces.key());
            // this is the last dpn interface on this elan
            handleUpdate(identifier, dpnInterfaces);
        }
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(identifier);

        jobCoordinator.enqueueJob(elanName + ":l2gw", () -> {
            elanInstanceDpnsCache.add(getElanName(identifier), dpnInterfaces);
            if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                ElanInstance elanInstance = elanInstanceCache.get(elanName).orNull();
                if (elanInstance != null) {
                    elanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(
                            dpnInterfaces.getDpId(), elanInstance, dpnInterfaces.getInterfaces().get(0));

                    // updating remote mcast mac on l2gw devices
                    elanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName);
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
