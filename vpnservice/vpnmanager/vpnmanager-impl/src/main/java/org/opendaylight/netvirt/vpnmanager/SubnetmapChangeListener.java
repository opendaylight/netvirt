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
import java.util.HashMap;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SubnetmapChangeListener extends AsyncDataTreeChangeListenerBase<Subnetmap, SubnetmapChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapChangeListener.class);
    private final DataBroker dataBroker;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;

    public SubnetmapChangeListener(final DataBroker dataBroker, final VpnSubnetRouteHandler vpnSubnetRouteHandler) {
        super(Subnetmap.class, SubnetmapChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
    }

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
        LOG.trace("add:SubnetmapChangeListener add subnetmap method - key: {}, value: {}", identifier, subnetmap);
        Uuid subnetId = subnetmap.getId();
        Uuid vpnId = subnetmap.getVpnId();
        boolean isExtNetwork = VpnUtil.getIsExternal(VpnUtil.getNeutronNetwork(dataBroker, subnetmap.getNetworkId()));
        if (subnetmap.getVpnId() != null) {
            // SubnetRoute for ExternalSubnets is handled in ExternalSubnetVpnInstanceListener.
            // Here we must handle only InternalVpnSubnetRoute and BGPVPNBasedSubnetRoute
            Network network = VpnUtil.getNeutronNetwork(dataBroker, subnetmap.getNetworkId());
            if (network == null) {
                LOG.info("update: vpnId {}, networkId: {}, subnetId: {}: network was not found",
                    vpnId.getValue(), subnetmap.getNetworkId().getValue(), subnetId.getValue());
                return;
            }
            if (isExtNetwork) {
                return;
            }
            boolean isBgpVpn = !vpnId.equals(subnetmap.getRouterId());
            String elanInstanceName = subnetmap.getNetworkId().getValue();
            Long elanTag = getElanTag(elanInstanceName);
            if (elanTag.equals(0L)) {
                LOG.error("add:Unable to fetch elantag from ElanInstance {} and hence not proceeding with "
                        + "subnetmapListener add for subnet {}", elanInstanceName, subnetId.getValue());
                return;
            }
            // subnet added to VPN case upon config DS replay after reboot
            // ports added to subnet upon config DS replay after reboot are handled implicitly by subnetAddedToVpn
            // in SubnetRouteHandler
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, isBgpVpn , elanTag, true);
        }
        if (subnetmap.getInternetVpnId() != null) {
            // SubnetRoute for ExternalNetwork is not impacting for internetvpn
            if (isExtNetwork) {
                return;
            }
            boolean isBgpVpn = !subnetmap.getInternetVpnId().equals(subnetmap.getRouterId());
            String elanInstanceName = subnetmap.getNetworkId().getValue();
            Long elanTag = getElanTag(elanInstanceName);
            if (elanTag.equals(0L)) {
                LOG.error("add:Unable to fetch elantag from ElanInstance {} and hence not proceeding with "
                        + "subnetmapListener add for subnet {}", elanInstanceName, subnetId.getValue());
                return;
            }
            // subnet added to VPN case upon config DS replay after reboot
            // ports added to subnet upon config DS replay after reboot are handled implicitly by subnetAddedToVpn
            // in SubnetRouteHandler
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, isBgpVpn , elanTag, false);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("remove:SubnetmapListener remove subnetmap method - key: {}, value: {}", identifier, subnetmap);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmapOriginal, Subnetmap
            subnetmapUpdate) {
        LOG.trace("update:SubnetmapListener update subnetmap method - key: {}, original: {}, update: {}",
                    identifier, subnetmapOriginal, subnetmapUpdate);
        Uuid vpnIdNewOther = subnetmapUpdate.getVpnId();
        Uuid vpnIdOldOther = subnetmapOriginal.getVpnId();
        Uuid vpnIdInternetNew = subnetmapUpdate.getInternetVpnId();
        Uuid vpnIdInternetOld = subnetmapOriginal.getInternetVpnId();
        Network network = VpnUtil.getNeutronNetwork(dataBroker, subnetmapUpdate.getNetworkId());
        String elanInstanceName = subnetmapUpdate.getNetworkId().getValue();
        Uuid subnetId = subnetmapUpdate.getId();
        if (network == null) {
            LOG.info("update: ExtVpn : vpnIdNew: {}, vpnIdOld: {} "
                + " InternetVpn : vpnIdNew: {}, vpnIdOld {} "
                + ", networkId: {}, subnetId: {}: network was not found",
                vpnIdOldOther, vpnIdNewOther, vpnIdInternetOld, vpnIdInternetNew,
                elanInstanceName, subnetId.getValue());
            return;
        }
        // SubnetRoute for ExternalSubnets is handled in ExternalSubnetVpnInstanceListener.
        // Here we must handle only InternalVpnSubnetRoute and BGPVPNBasedSubnetRoute
        if (VpnUtil.getIsExternal(network)) {
            return;
        }
        HashMap<Uuid, Uuid> mapVpnIdsNewOld = new HashMap<Uuid, Uuid>();
        mapVpnIdsNewOld.put(vpnIdInternetNew, vpnIdInternetOld);
        mapVpnIdsNewOld.put(vpnIdNewOther, vpnIdOldOther);
        mapVpnIdsNewOld.forEach((vpnIdNew,vpnIdOld) -> {
            boolean isExternalVpn = true;
            if (vpnIdNew == vpnIdInternetNew && vpnIdOld == vpnIdInternetOld) {
                isExternalVpn = false;
            }
            Long elanTag = getElanTag(elanInstanceName);
            if (elanTag.equals(0L)) {
                LOG.error("update:Unable to fetch elantag from ElanInstance {} and hence not proceeding with "
                    + "subnetmapListener update for subnet {}", elanInstanceName, subnetId.getValue());
                return;
            }
            // subnet added to VPN case
            if (vpnIdNew != null && vpnIdOld == null) {
                boolean isBgpVpn = !vpnIdNew.equals(subnetmapUpdate.getRouterId());
                if (!isBgpVpn) {
                    return;
                }
                vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmapUpdate, true, elanTag, isExternalVpn);
                return;
            }
            // subnet removed from VPN case
            if (vpnIdOld != null && vpnIdNew == null) {
                Boolean isBgpVpn = vpnIdOld.equals(subnetmapOriginal.getRouterId()) ? false : true;
                if (!isBgpVpn) {
                    return;
                }
                // should be removed from internet VPN too
                vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmapOriginal, true, isExternalVpn);
                return;
            }
            // subnet updated in VPN case
            if (vpnIdOld != null && vpnIdNew != null && (!vpnIdNew.equals(vpnIdOld))) {
                boolean isRouterDissociatedFromVpn = !vpnIdOld.equals(subnetmapUpdate.getRouterId());
                String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnIdOld.getValue());
                if (isRouterDissociatedFromVpn && primaryRd != null) {
                    InstanceIdentifier<Subnetmaps> subnetMapsId = InstanceIdentifier.builder(Subnetmaps.class).build();
                    Optional<Subnetmaps> allSubnetMaps = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        subnetMapsId);
                    // calculate and store in list IpVersion for each subnetMap, belonging to current VpnInstance
                    List<IpVersionChoice> snIpVersions = new ArrayList<>();
                    for (Subnetmap snMap: allSubnetMaps.get().getSubnetmap()) {
                        if (snMap.getId().equals(subnetmapUpdate.getId())) {
                            continue;
                        }
                        if (snMap.getVpnId() != null && snMap.getVpnId().equals(vpnIdOld)) {
                            snIpVersions.add(NeutronUtils.getIpVersion(snMap.getSubnetIp()));
                        }
                    }
                    IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmapUpdate.getSubnetIp());
                    if (!snIpVersions.contains(ipVersion)) {
                        // no more subnet with given IpVersion for current VpnInstance
                        VpnUtil.withdrawIpFamilyFromVpnInstance(dataBroker, vpnIdOld.getValue(), ipVersion);
                    }
                }
                vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmapOriginal, true, isExternalVpn);
                if (!isBgpVpn) {
                    return;
                }
                vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmapUpdate, true, elanTag, isExternalVpn);
                return;
            }
            String vpnName = VpnUtil.getVpnNameFromUuid(dataBroker, vpnIdNew);
            if (vpnName == null) {
                return;
            }
            // port added/removed to/from subnet case
            List<Uuid> oldPortList;
            List<Uuid> newPortList;
            newPortList = subnetmapUpdate.getPortList() != null ? subnetmapUpdate.getPortList() : new ArrayList<>();
            oldPortList = subnetmapOriginal.getPortList() != null ? subnetmapOriginal.getPortList() : new ArrayList<>();
            if (newPortList.size() == oldPortList.size()) {
                return;
            }
            if (newPortList.size() > oldPortList.size()) {
                for (Uuid portId : newPortList) {
                    if (! oldPortList.contains(portId)) {
                        vpnSubnetRouteHandler.onPortAddedToSubnet(subnetmapUpdate, portId, vpnName);
                        return;
                    }
                }
            } else {
                for (Uuid portId : oldPortList) {
                    if (! newPortList.contains(portId)) {
                        vpnSubnetRouteHandler.onPortRemovedFromSubnet(subnetmapUpdate, portId, vpnName);
                        return;
                    }
                }
            }
        });
    }

    @Override
    protected SubnetmapChangeListener getDataTreeChangeListener() {
        return this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected long getElanTag(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
        long elanTag = 0L;
        try {
            Optional<ElanInstance> elanInstance = VpnUtil.read(dataBroker, LogicalDatastoreType
                    .CONFIGURATION, elanIdentifierId);
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
        } catch (Exception e) {
            LOG.error("Notification failed because of failure in fetching elanTag for ElanInstance {}",
                elanInstanceName, e);
        }
        return elanTag;
    }
}
