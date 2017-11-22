/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.Collections;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElanDpnInterfaceClusteredListener
        extends AsyncClusteredDataTreeChangeListenerBase<DpnInterfaces, ElanDpnInterfaceClusteredListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnInterfaceClusteredListener.class);

    private final DataBroker broker;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;

    public ElanDpnInterfaceClusteredListener(DataBroker broker, EntityOwnershipUtils entityOwnershipUtils,
                                             ElanUtils elanUtils, ElanL2GatewayUtils elanL2GatewayUtils) {
        this.broker = broker;
        this.entityOwnershipUtils = entityOwnershipUtils;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanUtils.getElanL2GatewayMulticastUtils();
    }

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
            LOG.debug("dpnInterface updation, no external l2 devices to update for elan {} with Dp Id:", elanName,
                    dpnInterfaces.getDpId());
            return;
        }
        ElanClusterUtils.runOnlyInOwnerNode(entityOwnershipUtils, elanName, "updating mcast mac upon tunnel event",
            () -> Collections.singletonList(
                    elanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName)));
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        // this is the last dpn interface on this elan
        final String elanName = getElanName(identifier);
        //Cache need to be updated in all cluster nodes and not only by leader node .
        //Hence moved out from DJC job

        DataStoreJobCoordinator.getInstance().enqueueJob(elanName + ":l2gw", () -> {
            try {
                if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                        HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                    // deleting Elan L2Gw Devices UcastLocalMacs From Dpn
                    elanL2GatewayUtils.deleteElanL2GwDevicesUcastLocalMacsFromDpn(elanName,
                            dpnInterfaces.getDpId());

                    //Removing this dpn from cache to avoid race between this and local ucast mac listener
                    ElanUtils.removeDPNInterfaceFromElanInCache(getElanName(identifier), dpnInterfaces);

                    // updating remote mcast mac on l2gw devices
                    elanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName);
                }
            } finally {
                ElanUtils.removeDPNInterfaceFromElanInCache(getElanName(identifier), dpnInterfaces);
            }

            return null;
        });
    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
                          final DpnInterfaces dpnInterfaces) {
        LOG.debug("dpninterfaces update fired new size {}", dpnInterfaces.getInterfaces().size());
        if (dpnInterfaces.getInterfaces().isEmpty()) {
            ElanUtils.removeDPNInterfaceFromElanInCache(getElanName(identifier), dpnInterfaces);
            LOG.debug("dpninterfaces last dpn interface on this elan {} ", dpnInterfaces.getKey());
            // this is the last dpn interface on this elan
            handleUpdate(identifier, dpnInterfaces);
        }
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(identifier);

        DataStoreJobCoordinator.getInstance().enqueueJob(elanName + ":l2gw", () -> {
            try {
                if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                        HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                    ElanUtils.addDPNInterfaceToElanInCache(getElanName(identifier), dpnInterfaces);
                    ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
                    elanL2GatewayUtils.installElanL2gwDevicesLocalMacsInDpn(
                            dpnInterfaces.getDpId(), elanInstance, dpnInterfaces.getInterfaces().get(0));

                    // updating remote mcast mac on l2gw devices
                    elanL2GatewayMulticastUtils.updateRemoteMcastMacOnElanL2GwDevices(elanName);
                }
            } finally {
                ElanUtils.addDPNInterfaceToElanInCache(getElanName(identifier), dpnInterfaces);
            }
            return null;
        });
    }

    private String getElanName(InstanceIdentifier<DpnInterfaces> identifier) {
        return identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
    }

    @Override
    protected ElanDpnInterfaceClusteredListener getDataTreeChangeListener() {
        return this;
    }

}
