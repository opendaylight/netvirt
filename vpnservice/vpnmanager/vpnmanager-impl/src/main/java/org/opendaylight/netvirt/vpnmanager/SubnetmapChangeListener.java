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
            if (isBgpVpn) {
                IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmap.getSubnetIp());
                VpnUtil.updateVpnInstanceWithIpFamily(dataBroker, vpnId.getValue(), ipVersion);
            }
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
            if (isBgpVpn) {
                IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmap.getSubnetIp());
                VpnUtil.updateVpnInstanceWithIpFamily(dataBroker, subnetmap.getInternetVpnId().getValue(), ipVersion);
            }
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
        Uuid subnetId = subnetmapUpdate.getId();
        if ((subnetmapUpdate.getNetworkId() == null)
            && subnetmapOriginal.getNetworkId() == null) {
            // transition: subnetmap is removed with syncwrite.
            // wait next write to do the update
            LOG.error("subnetmap has no network for subnetmap {}", subnetmapUpdate);
            return;
        }
        boolean updateCapableForCreation = false;
        String elanInstanceName = null;
        Long elanTag = new Long(0L);
        if (subnetmapUpdate.getNetworkId() != null) {
            updateCapableForCreation = true;
            elanInstanceName = subnetmapUpdate.getNetworkId().getValue();
            elanTag = getElanTag(elanInstanceName);
            if (elanTag.equals(0L)) {
                LOG.error("update:Unable to fetch elantag from ElanInstance {} and "
                    + "hence not proceeding with "
                    + "subnetmapListener update for subnet {}", elanInstanceName, subnetId.getValue());
                return;
            }

            // SubnetRoute for ExternalSubnets is handled in ExternalSubnetVpnInstanceListener.
            // Here we must handle only InternalVpnSubnetRoute and BGPVPNBasedSubnetRoute
            Network network = VpnUtil.getNeutronNetwork(dataBroker, subnetmapUpdate.getNetworkId());
            if (VpnUtil.getIsExternal(network)) {
                return;
            }
        }
        Uuid vpnIdInternetNew = subnetmapUpdate.getInternetVpnId();
        Uuid vpnIdInternetOld = subnetmapOriginal.getInternetVpnId();
        boolean returnValue1;
        returnValue1 = updateSubnetmapOpDataEntry(vpnIdInternetOld, vpnIdInternetNew,
                           subnetmapUpdate, subnetmapOriginal, false,
                           elanTag, updateCapableForCreation);
        Uuid vpnIdNew = subnetmapUpdate.getVpnId();
        Uuid vpnIdOld = subnetmapOriginal.getVpnId();
        boolean returnValue2;
        returnValue2 = updateSubnetmapOpDataEntry(vpnIdOld, vpnIdNew,
                      subnetmapUpdate, subnetmapOriginal, true, elanTag, updateCapableForCreation);
        if ((vpnIdOld != null && vpnIdNew != null
            && (!vpnIdNew.equals(vpnIdOld)))
            || (vpnIdInternetOld != null && vpnIdInternetNew != null
            && (!vpnIdInternetNew.equals(vpnIdInternetOld)))) {
            boolean isBgpVpn1 = false;
            boolean isBgpVpn2 = false;
            String primaryRd1 = null;
            String primaryRd2 = null;
            if (vpnIdOld != null && !vpnIdOld.equals(subnetmapUpdate.getRouterId())) {
                isBgpVpn1 = true;
                primaryRd1 = VpnUtil.getPrimaryRd(dataBroker, vpnIdOld.getValue());
            }
            if (vpnIdInternetOld != null && !vpnIdInternetOld.equals(subnetmapUpdate.getRouterId())) {
                isBgpVpn2 = true;
                primaryRd2 = VpnUtil.getPrimaryRd(dataBroker, vpnIdInternetOld.getValue());
            }
            boolean withdrawCapableVpn1 = false;
            if (isBgpVpn1 && primaryRd1 != null
                && vpnIdOld != null && VpnUtil.isBgpVpn(vpnIdOld.getValue(), primaryRd1)) {
                withdrawCapableVpn1 = true;
            }
            boolean withdrawCapableVpn2 = false;
            if (isBgpVpn2 && primaryRd2 != null
                && vpnIdInternetOld != null && VpnUtil.isBgpVpn(vpnIdInternetOld.getValue(), primaryRd2)) {
                withdrawCapableVpn2 = true;
            }
            if (withdrawCapableVpn1 == true || withdrawCapableVpn2 == true) {
                InstanceIdentifier<Subnetmaps> subnetMapsId = InstanceIdentifier.builder(Subnetmaps.class).build();
                Optional<Subnetmaps> allSubnetMaps = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    subnetMapsId);
                // calculate and store in list IpVersion for each subnetMap, belonging to current VpnInstance
                List<IpVersionChoice> snIpVersionsExternalVpn = new ArrayList<>();
                List<IpVersionChoice> snIpVersionsInternetVpn = new ArrayList<>();
                for (Subnetmap snMap: allSubnetMaps.get().getSubnetmap()) {
                    if (snMap.getId().equals(subnetmapUpdate.getId())) {
                        continue;
                    }
                    if (vpnIdOld != null && snMap.getVpnId() != null && snMap.getVpnId().equals(vpnIdOld)) {
                        snIpVersionsExternalVpn.add(NeutronUtils.getIpVersion(snMap.getSubnetIp()));
                    }
                    if (vpnIdInternetOld != null && snMap.getInternetVpnId() != null
                        && snMap.getVpnId().equals(vpnIdInternetOld)) {
                        snIpVersionsInternetVpn.add(NeutronUtils.getIpVersion(snMap.getSubnetIp()));
                    }
                }
                IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmapUpdate.getSubnetIp());
                if (withdrawCapableVpn1 == true && !snIpVersionsExternalVpn.contains(ipVersion)) {
                    // no more subnet with given IpVersion for current VpnInstance
                    VpnUtil.withdrawIpFamilyFromVpnInstance(dataBroker, vpnIdOld.getValue(), ipVersion);
                }
                if (withdrawCapableVpn2 == true && !snIpVersionsInternetVpn.contains(ipVersion)) {
                    // no more subnet with given IpVersion for current VpnInstance
                    VpnUtil.withdrawIpFamilyFromVpnInstance(dataBroker, vpnIdInternetOld.getValue(), ipVersion);
                }
            }
        }
        if (returnValue2 == false || returnValue1 == false) {
            return;
        }
        String vpnName1 = VpnUtil.getVpnNameFromUuid(dataBroker, vpnIdNew);
        String vpnName2 = VpnUtil.getVpnNameFromUuid(dataBroker, vpnIdInternetNew);
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
                    vpnSubnetRouteHandler.onPortAddedToSubnet(subnetmapUpdate, portId, vpnName1, vpnName2);
                    return;
                }
            }
        } else {
            for (Uuid portId : oldPortList) {
                if (! newPortList.contains(portId)) {
                    vpnSubnetRouteHandler.onPortRemovedFromSubnet(subnetmapUpdate, portId, vpnName1, vpnName2);
                    return;
                }
            }
        }
    }

    boolean updateSubnetmapOpDataEntry(Uuid vpnIdOld, Uuid vpnIdNew, Subnetmap subnetmapUpdate,
                                    Subnetmap subnetmapOriginal, boolean isExternalVpn,
                                    Long elanTag, boolean updateCapableForCreation) {
        // subnet added to VPN case
        if (vpnIdNew != null && vpnIdOld == null && updateCapableForCreation) {
            boolean isBgpVpn = !vpnIdNew.equals(subnetmapUpdate.getRouterId());
            if (!isBgpVpn) {
                return false;
            }
            IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmapUpdate.getSubnetIp());
            VpnUtil.updateVpnInstanceWithIpFamily(dataBroker, vpnIdNew.getValue(), ipVersion);
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmapUpdate, true, elanTag, isExternalVpn);
            return false;
        }
        // subnet removed from VPN case
        if (vpnIdOld != null && vpnIdNew == null) {
            Boolean isBgpVpn = vpnIdOld.equals(subnetmapOriginal.getRouterId()) ? false : true;
            if (!isBgpVpn) {
                return false;
            }
            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnIdOld.getValue());
            if (primaryRd != null) {
                InstanceIdentifier<Subnetmaps> subnetMapsId = InstanceIdentifier.builder(Subnetmaps.class).build();
                Optional<Subnetmaps> allSubnetMaps = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                                            subnetMapsId);
                // calculate and store in list IpVersion for each subnetMap, belonging to current VpnInstance
                List<IpVersionChoice> snIpVersions = new ArrayList<>();
                for (Subnetmap snMap: allSubnetMaps.get().getSubnetmap()) {
                    if (snMap.getId().equals(subnetmapOriginal.getId())) {
                        continue;
                    }
                    if (snMap.getVpnId() != null && snMap.getVpnId().equals(vpnIdOld)) {
                        snIpVersions.add(NeutronUtils.getIpVersion(snMap.getSubnetIp()));
                    }
                }
                IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmapOriginal.getSubnetIp());
                if (!snIpVersions.contains(ipVersion)) {
                    // no more subnet with given IpVersion for current VpnInstance
                    VpnUtil.withdrawIpFamilyFromVpnInstance(dataBroker, vpnIdOld.getValue(), ipVersion);
                }
            }
            // should be removed from internet VPN too
            vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmapOriginal, true, isExternalVpn);
            return false;
        }
        // subnet updated in VPN case
        if (vpnIdOld != null && vpnIdNew != null && (!vpnIdNew.equals(vpnIdOld))) {
            boolean isBgpVpn = !vpnIdOld.equals(subnetmapUpdate.getRouterId());
            String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnIdOld.getValue());
            if (primaryRd != null) {
                InstanceIdentifier<Subnetmaps> subnetMapsId = InstanceIdentifier.builder(Subnetmaps.class).build();
                Optional<Subnetmaps> allSubnetMaps = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                                            subnetMapsId);
                // calculate and store in list IpVersion for each subnetMap, belonging to current VpnInstance
                List<IpVersionChoice> snIpVersions = new ArrayList<>();
                for (Subnetmap snMap: allSubnetMaps.get().getSubnetmap()) {
                    if (snMap.getId().equals(subnetmapOriginal.getId())) {
                        continue;
                    }
                    if (snMap.getVpnId() != null && snMap.getVpnId().equals(vpnIdOld)) {
                        snIpVersions.add(NeutronUtils.getIpVersion(snMap.getSubnetIp()));
                    }
                }
                IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmapOriginal.getSubnetIp());
                if (!snIpVersions.contains(ipVersion)) {
                    // no more subnet with given IpVersion for current VpnInstance
                    VpnUtil.withdrawIpFamilyFromVpnInstance(dataBroker, vpnIdOld.getValue(), ipVersion);
                }
            }
            vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmapOriginal, true, isExternalVpn);
            if (!isBgpVpn || !updateCapableForCreation) {
                return false;
            }
            IpVersionChoice ipVersion = NeutronUtils.getIpVersion(subnetmapUpdate.getSubnetIp());
            VpnUtil.updateVpnInstanceWithIpFamily(dataBroker, vpnIdNew.getValue(), ipVersion);
            vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmapUpdate, true, elanTag, isExternalVpn);
        }
        return true;
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
