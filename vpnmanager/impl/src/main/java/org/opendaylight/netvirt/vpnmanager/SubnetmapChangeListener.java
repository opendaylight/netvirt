/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes.NetworkType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetmapChangeListener extends AsyncDataTreeChangeListenerBase<Subnetmap, SubnetmapChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapChangeListener.class);
    private final DataBroker dataBroker;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;

    @Inject
    public SubnetmapChangeListener(final DataBroker dataBroker, final VpnSubnetRouteHandler vpnSubnetRouteHandler) {
        super(Subnetmap.class, SubnetmapChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnetmap> getWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void registerListener(final DataBroker db) {
        try {
            registerListener(LogicalDatastoreType.CONFIGURATION, db);
        } catch (final Exception e) {
            LOG.error("VPNManager subnetMap config DataChange listener registration fail!", e);
            throw new IllegalStateException("VPNManager subnetMap config DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.debug("SubnetmapChangeListener add subnetmap method - key: {}, value: {}", identifier, subnetmap);
        Uuid subnetId = subnetmap.getId();
        Network network = VpnUtil.getNeutronNetwork(dataBroker, subnetmap.getNetworkId());
        if (network == null) {
            LOG.error("SubnetMapChangeListener:add: network was not found for subnetId {}", subnetId.getValue());
            return;
        }
        if (subnetmap.getVpnId() != null) {
            if (subnetmap.getNetworkType().equals(NetworkType.VLAN)) {
                VpnUtil.addRouterPortToElanDpnListForVlaninAllDpn(subnetmap.getVpnId().getValue(), dataBroker);
            }
        }
        if (VpnUtil.getIsExternal(network)) {
            LOG.debug("SubnetmapListener:add: provider subnetwork {} is handling in "
                      + "ExternalSubnetVpnInstanceListener", subnetId.getValue());
            return;
        }
        String elanInstanceName = subnetmap.getNetworkId().getValue();
        long elanTag = getElanTag(elanInstanceName);
        if (elanTag == 0L) {
            LOG.error("SubnetMapChangeListener:add: unable to fetch elantag from ElanInstance {} for subnet {}",
                      elanInstanceName, subnetId.getValue());
            return;
        }
        if (subnetmap.getVpnId() != null) {
            boolean isBgpVpn = !subnetmap.getVpnId().equals(subnetmap.getRouterId());
            LOG.info("SubnetMapChangeListener:add: subnetmap {} with elanTag {} to VPN {}", subnetmap, elanTag,
                     subnetmap.getVpnId());
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, isBgpVpn, elanTag);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("SubnetmapListener:remove: subnetmap method - key: {}, value: {}", identifier, subnetmap);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmapOriginal, Subnetmap
            subnetmapUpdate) {
        LOG.debug("SubnetMapChangeListener update method - key {}, original {}, update {}", identifier,
                  subnetmapOriginal, subnetmapUpdate);
        Uuid subnetId = subnetmapUpdate.getId();
        Network network = VpnUtil.getNeutronNetwork(dataBroker, subnetmapUpdate.getNetworkId());
        if (network == null) {
            LOG.error("SubnetMapChangeListener:update: network was not found for subnetId {}", subnetId.getValue());
            return;
        }
        String elanInstanceName = subnetmapUpdate.getNetworkId().getValue();
        long elanTag = getElanTag(elanInstanceName);
        if (elanTag == 0L) {
            LOG.error("SubnetMapChangeListener:update: unable to fetch elantag from ElanInstance {} for subnetId {}",
                      elanInstanceName, subnetId);
            return;
        }
        updateVlanDataEntry(subnetmapOriginal.getVpnId(), subnetmapUpdate.getVpnId(), subnetmapUpdate,
                subnetmapOriginal, elanTag, elanInstanceName);
        if (VpnUtil.getIsExternal(network)) {
            LOG.debug("SubnetMapChangeListener:update: provider subnetwork {} is handling in "
                      + "ExternalSubnetVpnInstanceListener", subnetId.getValue());
            return;
        }
        // update on BGPVPN or InternalVPN change
        Uuid vpnIdOld = subnetmapOriginal.getVpnId();
        Uuid vpnIdNew = subnetmapUpdate.getVpnId();
        if (!Objects.equals(vpnIdOld, vpnIdNew)) {
            LOG.info("SubnetMapChangeListener:update: update subnetOpDataEntry for subnet {} imported in VPN",
                     subnetmapUpdate.getId().getValue());
            updateSubnetmapOpDataEntry(subnetmapOriginal.getVpnId(), subnetmapUpdate.getVpnId(), subnetmapUpdate,
                                       subnetmapOriginal, elanTag);
        }
        // update on Internet VPN Id change
        Uuid inetVpnIdOld = subnetmapOriginal.getInternetVpnId();
        Uuid inetVpnIdNew = subnetmapUpdate.getInternetVpnId();
        if (!Objects.equals(inetVpnIdOld, inetVpnIdNew)) {
            LOG.info("SubnetMapChangeListener:update: update subnetOpDataEntry for subnet {} imported in InternetVPN",
                     subnetmapUpdate.getId().getValue());
            updateSubnetmapOpDataEntry(inetVpnIdOld, inetVpnIdNew, subnetmapUpdate, subnetmapOriginal, elanTag);
        }
        // update on PortList change
        List<Uuid> oldPortList;
        List<Uuid> newPortList;
        newPortList = subnetmapUpdate.getPortList() != null ? subnetmapUpdate.getPortList() : new ArrayList<>();
        oldPortList = subnetmapOriginal.getPortList() != null ? subnetmapOriginal.getPortList() : new ArrayList<>();
        if (newPortList.size() == oldPortList.size()) {
            return;
        }
        LOG.info("SubnetMapChangeListener:update: update port list for subnet {}", subnetmapUpdate.getId().getValue());
        if (newPortList.size() > oldPortList.size()) {
            for (Uuid portId : newPortList) {
                if (! oldPortList.contains(portId)) {
                    vpnSubnetRouteHandler.onPortAddedToSubnet(subnetmapUpdate, portId);
                    return;
                }
            }
        } else {
            for (Uuid portId : oldPortList) {
                if (! newPortList.contains(portId)) {
                    vpnSubnetRouteHandler.onPortRemovedFromSubnet(subnetmapUpdate, portId);
                    return;
                }
            }
        }
    }

    private void updateSubnetmapOpDataEntry(Uuid vpnIdOld, Uuid vpnIdNew, Subnetmap subnetmapUpdate,
                                    Subnetmap subnetmapOriginal, Long elanTag) {

        // subnet added to VPN
        if (vpnIdNew != null && vpnIdOld == null) {
            if (vpnIdNew.equals(subnetmapUpdate.getRouterId())) {
                return;
            }
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmapUpdate, true, elanTag);
        }
        // subnet removed from VPN
        if (vpnIdOld != null && vpnIdNew == null) {
            if (vpnIdOld.equals(subnetmapOriginal.getRouterId())) {
                return;
            }
            vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmapOriginal, true);
        }
        // subnet updated in VPN
        if (vpnIdOld != null && vpnIdNew != null && (!vpnIdNew.equals(vpnIdOld))) {
            vpnSubnetRouteHandler.onSubnetUpdatedInVpn(subnetmapUpdate, elanTag);
        }
    }

    private void updateVlanDataEntry(Uuid vpnIdOld, Uuid vpnIdNew, Subnetmap subnetmapUpdate,
            Subnetmap subnetmapOriginal, Long elanTag, String  elanInstanceName) {
        if (vpnIdNew != null && vpnIdOld == null) {
            if (elanInstanceName != null && subnetmapUpdate.getNetworkType().equals(NetworkType.VLAN)) {
                VpnUtil.addRouterPortToElanDpnListForVlaninAllDpn(vpnIdNew.getValue(), dataBroker);
            }
        }
        if (vpnIdOld != null && vpnIdNew == null) {
            if (subnetmapOriginal.getNetworkType().equals(NetworkType.VLAN)) {
                VpnUtil.removeRouterPortFromElanDpnListForVlanInAllDpn(elanInstanceName, subnetmapOriginal
                        .getRouterInterfacePortId().getValue(), vpnIdOld.getValue(), dataBroker);
            }
        }
    }

    @Override
    protected SubnetmapChangeListener getDataTreeChangeListener() {
        return this;
    }

    protected long getElanTag(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        long elanTag = 0L;
        try {
            Optional<ElanInstance> elanInstance = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
            if (elanInstance.isPresent()) {
                if (elanInstance.get().getElanTag() != null) {
                    elanTag = elanInstance.get().getElanTag();
                } else {
                    LOG.error("Notification failed because of failure in fetching elanTag for ElanInstance {}",
                            elanInstanceName);
                }
            } else {
                LOG.error("Notification failed because of failure in reading ELANInstance {}", elanInstanceName);
            }
        } catch (ReadFailedException e) {
            LOG.error("Notification failed because of failure in fetching elanTag for ElanInstance {}",
                elanInstanceName, e);
        }
        return elanTag;
    }
}
