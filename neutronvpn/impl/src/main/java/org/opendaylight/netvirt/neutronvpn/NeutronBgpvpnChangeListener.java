/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.AcquireResult;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeL3;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.Bgpvpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.Bgpvpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronBgpvpnChangeListener extends AbstractAsyncDataTreeChangeListener<Bgpvpn> {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnChangeListener.class);

    private final NeutronvpnManager nvpnManager;
    private final IdManagerService idManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final NeutronBgpvpnUtils neutronBgpvpnUtils;
    private final String adminRDValue;

    @Inject
    public NeutronBgpvpnChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
            final IdManagerService idManager, final NeutronvpnUtils neutronvpnUtils,
            final NeutronBgpvpnUtils neutronBgpvpnUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Neutron.class).child(Bgpvpns.class).child(Bgpvpn.class),
                Executors.newSingleThreadExecutor("NeutronBgpvpnChangeListener", LOG));
        this.nvpnManager = neutronvpnManager;
        this.idManager = idManager;
        this.neutronvpnUtils = neutronvpnUtils;
        this.neutronBgpvpnUtils = neutronBgpvpnUtils;
        BundleContext bundleContext = FrameworkUtil.getBundle(NeutronBgpvpnChangeListener.class).getBundleContext();
        adminRDValue = bundleContext.getProperty(NeutronConstants.RD_PROPERTY_KEY);
        init();
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    private boolean isBgpvpnTypeL3(Class<? extends BgpvpnTypeBase> bgpvpnType) {
        if (BgpvpnTypeL3.class.equals(bgpvpnType)) {
            return true;
        } else {
            LOG.warn("CRUD operations supported only for L3 type Bgpvpn");
            return false;
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void add(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        LOG.trace("Adding Bgpvpn : key: {}, value={}", identifier, input);

        String vpnName = input.getUuid().getValue();
        if (!isBgpvpnTypeL3(input.getType())) {
            LOG.warn("BGPVPN type for VPN {} is not L3", vpnName);
            return;
        }
        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try (AcquireResult lock = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
            if (!lock.wasAcquired()) {
                LOG.error("Add BGPVPN: add bgpvpn failed for vpn : {} due to failure in acquiring lock", vpnName);
                return;
            }
            // handle route-target(s)
            List<String> inputRouteList = input.getRouteTargets();
            List<String> inputImportRouteList = input.getImportTargets();
            List<String> inputExportRouteList = input.getExportTargets();
            Set<String> inputImportRouteSet = new HashSet<>();
            Set<String> inputExportRouteSet = new HashSet<>();

            if (inputRouteList != null && !inputRouteList.isEmpty()) {
                inputImportRouteSet.addAll(inputRouteList);
                inputExportRouteSet.addAll(inputRouteList);
            }
            if (inputImportRouteList != null && !inputImportRouteList.isEmpty()) {
                inputImportRouteSet.addAll(inputImportRouteList);
            }
            if (inputExportRouteList != null && !inputExportRouteList.isEmpty()) {
                inputExportRouteSet.addAll(inputExportRouteList);
            }
            List<String> importRouteTargets = new ArrayList<>(inputImportRouteSet);
            List<String> exportRouteTargets = new ArrayList<>(inputExportRouteSet);
            boolean rdIrtErtStringsValid;

            List<String> rdList = input.getRouteDistinguishers();

            if (rdList != null && !rdList.isEmpty()) {
                // get the primary RD for vpn instance, if exist
                rdIrtErtStringsValid =
                        !(input.getRouteDistinguishers().stream().anyMatch(rdStr -> rdStr.contains(" ")));
                rdIrtErtStringsValid =
                        rdIrtErtStringsValid && !(importRouteTargets.stream().anyMatch(irtStr -> irtStr.contains(" ")));
                rdIrtErtStringsValid =
                        rdIrtErtStringsValid && !(exportRouteTargets.stream().anyMatch(ertStr -> ertStr.contains(" ")));
                if (!rdIrtErtStringsValid) {
                    LOG.error("Error encountered for BGPVPN {} with RD {} as RD/iRT/eRT contains whitespace "
                            + "characters", vpnName, input.getRouteDistinguishers());
                    return;
                }
                String primaryRd = neutronvpnUtils.getVpnRd(vpnName);
                if (primaryRd == null) {
                    primaryRd = rdList.get(0);
                }

                String[] rdParams = primaryRd.split(":");
                if (rdParams[0].trim().equals(adminRDValue)) {
                    LOG.error("AS specific part of RD should not be same as that defined by DC Admin. Error "
                            + "encountered for BGPVPN {} with RD {}", vpnName, primaryRd);
                    return;
                }
                String vpnWithSameRd = neutronvpnUtils.getVpnForRD(primaryRd);
                if (vpnWithSameRd != null) {
                    LOG.error("Creation of L3VPN failed for VPN {} as another VPN {} with the same RD {} "
                            + "is already configured", vpnName, vpnWithSameRd, primaryRd);
                    return;
                }
                String existingOperationalVpn = neutronvpnUtils.getExistingOperationalVpn(primaryRd);
                if (existingOperationalVpn != null) {
                    LOG.error("checkVpnCreation: Creation of L3VPN failed for VPN {} as another VPN {} with the "
                            + "same RD {} is still available.", vpnName, existingOperationalVpn, primaryRd);
                    return;
                }
                List<Uuid> unpRtrs = neutronBgpvpnUtils.getUnprocessedRoutersForBgpvpn(input.getUuid());
                List<Uuid> unpNets = neutronBgpvpnUtils.getUnprocessedNetworksForBgpvpn(input.getUuid());

                // TODO: Currently handling routers and networks for backward compatibility. Below logic needs to be
                // removed once updated to latest BGPVPN API's.
                List<Uuid> inputRouters = input.getRouters();
                if (inputRouters != null && !inputRouters.isEmpty()) {
                    if (unpRtrs != null) {
                        unpRtrs.addAll(inputRouters);
                    } else {
                        unpRtrs = new ArrayList<>(inputRouters);
                    }
                }
                if (unpRtrs != null && unpRtrs.size() > NeutronConstants.MAX_ROUTERS_PER_BGPVPN) {
                    LOG.error("Creation of BGPVPN for rd {} failed: maximum allowed number of associated "
                            + "routers is {}.", rdList, NeutronConstants.MAX_ROUTERS_PER_BGPVPN);
                    return;
                }
                List<Uuid> inputNetworks = input.getNetworks();
                if (inputNetworks != null && !inputNetworks.isEmpty()) {
                    if (unpNets != null) {
                        unpNets.addAll(inputNetworks);
                    } else {
                        unpNets = new ArrayList<>(inputNetworks);
                    }
                }
                try {
                    nvpnManager.createVpn(input.getUuid(), input.getName(), input.getTenantId(), rdList,
                            importRouteTargets, exportRouteTargets, unpRtrs, unpNets, false /* isL2Vpn */,
                            0 /* l3vni */);
                    neutronBgpvpnUtils.getUnProcessedRoutersMap().remove(input.getUuid());
                    neutronBgpvpnUtils.getUnProcessedNetworksMap().remove(input.getUuid());
                } catch (Exception e) {
                    LOG.error("Creation of BGPVPN {} failed with error ", vpnName, e);
                }
            } else {
                LOG.error("add: RD is absent for BGPVPN {}", vpnName);
            }
        }
    }

    @Override
    public void remove(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        LOG.trace("Removing Bgpvpn : key: {}, value={}", identifier, input);
        Uuid vpnId = input.getUuid();
        String vpnName = vpnId.getValue();
        if (!isBgpvpnTypeL3(input.getType())) {
            LOG.warn("BGPVPN type for VPN {} is not L3", vpnName);
            return;
        }
        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try (AcquireResult lock = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
            if (!lock.wasAcquired()) {
                LOG.error("Remove BGPVPN: remove bgpvpn failed for vpn : {} due to failure in acquiring lock", vpnName);
                return;
            }
            neutronBgpvpnUtils.getUnProcessedRoutersMap().remove(input.getUuid());
            neutronBgpvpnUtils.getUnProcessedNetworksMap().remove(input.getUuid());
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            if (vpnMap == null) {
                LOG.error("Failed to handle BGPVPN Remove for VPN {} as that VPN is not configured"
                        + " yet as a VPN Instance", vpnName);
                return;
            }
            nvpnManager.removeVpn(input.getUuid());
        }
    }

    @Override
    public void update(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn original, Bgpvpn update) {
        LOG.trace("Update Bgpvpn : key: {}, value={}", identifier, update);
        if (Objects.equals(original, update)) {
            return;
        }
        String vpnName = update.getUuid().getValue();
        if (!isBgpvpnTypeL3(update.getType())) {
            LOG.warn("BGPVPN type for VPN {} is not L3", vpnName);
            return;
        }
        boolean rdIrtErtStringsValid = true;
        rdIrtErtStringsValid = rdIrtErtStringsValid
                && !(update.getRouteDistinguishers().stream().anyMatch(rdStr -> rdStr.contains(" ")));
        rdIrtErtStringsValid =
                rdIrtErtStringsValid && !(update.getImportTargets().stream().anyMatch(irtStr -> irtStr.contains(" ")));
        rdIrtErtStringsValid =
                rdIrtErtStringsValid && !(update.getExportTargets().stream().anyMatch(ertStr -> ertStr.contains(" ")));
        if (!rdIrtErtStringsValid) {
            LOG.error("Error encountered for BGPVPN {} with RD {} as RD/iRT/eRT contains whitespace characters",
                    vpnName, update.getRouteDistinguishers());
            return;
        }
        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try (AcquireResult lock = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
            if (!lock.wasAcquired()) {
                LOG.error("Update VPN: update failed for vpn : {} due to failure in acquiring lock", vpnName);
                return;
            }
            handleVpnInstanceUpdate(original.getUuid().getValue(), original.getRouteDistinguishers(),
                    update.getRouteDistinguishers());

            // TODO: Currently handling routers and networks for backward compatibility. Below logic needs to be
            // removed once updated to latest BGPVPN API's.
            Uuid vpnId = update.getUuid();
            List<Uuid> oldNetworks = new ArrayList<>(original.getNetworks());
            List<Uuid> newNetworks = new ArrayList<>(update.getNetworks());
            handleNetworksUpdate(vpnId, oldNetworks, newNetworks);

            List<Uuid> oldRouters = original.getRouters();
            List<Uuid> newRouters = update.getRouters();
            handleRoutersUpdate(vpnId, oldRouters, newRouters);
        } catch (UnsupportedOperationException e) {
            LOG.error("Error while processing Update Bgpvpn.", e);
        }
    }

    protected void handleVpnInstanceUpdate(String vpnInstanceName, final List<String> originalRds,
            List<String> updateRDs) throws UnsupportedOperationException {
        if (updateRDs == null || updateRDs.isEmpty()) {
            return;
        }
        int oldRdsCount = originalRds.size();

        for (String rd : originalRds) {
            // If the existing rd is not present in the updateRds list, not allow to process the updateRDs.
            if (!updateRDs.contains(rd)) {
                LOG.error("The existing RD {} not present in the updatedRDsList:{}", rd, updateRDs);
                throw new UnsupportedOperationException("The existing RD not present in the updatedRDsList");
            }
        }
        if (updateRDs.size() == oldRdsCount) {
            LOG.debug("There is no update in the List of Route Distinguisher for the VpnInstance:{}", vpnInstanceName);
            return;
        }
        LOG.debug("update the VpnInstance:{} with the List of RDs: {}", vpnInstanceName, updateRDs);
        nvpnManager.updateVpnInstanceWithRDs(vpnInstanceName, updateRDs);
    }

    /**
     * Handle networks update.
     *
     * @deprecated Retaining method for backward compatibility. Below method needs to be removed once
     *             updated to latest BGPVPN API's.
     *
     * @param vpnId the vpn id
     * @param oldNetworks the old networks
     * @param newNetworks the new networks
     */
    @Deprecated
    private void handleNetworksUpdate(Uuid vpnId, List<Uuid> oldNetworks, List<Uuid> newNetworks) {
        if (newNetworks != null && !newNetworks.isEmpty()) {
            if (oldNetworks != null && !oldNetworks.isEmpty()) {
                if (oldNetworks != newNetworks) {
                    Iterator<Uuid> iter = newNetworks.iterator();
                    while (iter.hasNext()) {
                        Uuid net = iter.next();
                        if (oldNetworks.contains(net)) {
                            oldNetworks.remove(net);
                            iter.remove();
                        }
                    }
                    //clear removed networks
                    if (!oldNetworks.isEmpty()) {
                        LOG.trace("Removing old networks {} ", oldNetworks);
                        List<String> errorMessages = nvpnManager.dissociateNetworksFromVpn(vpnId, oldNetworks);
                        if (!errorMessages.isEmpty()) {
                            LOG.error("handleNetworksUpdate: dissociate old Networks not part of bgpvpn update,"
                                    + " from vpn {} failed due to {}", vpnId.getValue(), errorMessages);
                        }
                    }

                    //add new (Delta) Networks
                    if (!newNetworks.isEmpty()) {
                        LOG.trace("Adding delta New networks {} ", newNetworks);
                        List<String> errorMessages = nvpnManager.associateNetworksToVpn(vpnId, newNetworks);
                        if (!errorMessages.isEmpty()) {
                            LOG.error("handleNetworksUpdate: associate new Networks not part of original bgpvpn,"
                                    + " to vpn {} failed due to {}", vpnId.getValue(), errorMessages);
                        }
                    }
                }
            } else {
                //add new Networks
                LOG.trace("Adding New networks {} ", newNetworks);
                List<String> errorMessages = nvpnManager.associateNetworksToVpn(vpnId, newNetworks);
                if (!errorMessages.isEmpty()) {
                    LOG.error("handleNetworksUpdate: associate new Networks to vpn {} failed due to {}",
                            vpnId.getValue(), errorMessages);
                }
            }
        } else if (oldNetworks != null && !oldNetworks.isEmpty()) {
            LOG.trace("Removing old networks {} ", oldNetworks);
            List<String> errorMessages = nvpnManager.dissociateNetworksFromVpn(vpnId, oldNetworks);
            if (!errorMessages.isEmpty()) {
                LOG.error("handleNetworksUpdate: dissociate old Networks from vpn {} failed due to {}",
                        vpnId.getValue(), errorMessages);
            }
        }
    }

    /**
     * Handle routers update.
     *
     * @deprecated Retaining method for backward compatibility. Below method needs to be removed once
     *             updated to latest BGPVPN API's.
     *
     * @param vpnId the vpn id
     * @param oldRouters the old routers
     * @param newRouters the new routers
     */
    @Deprecated
    private void handleRoutersUpdate(Uuid vpnId, List<Uuid> oldRouters, List<Uuid> newRouters) {
        // for dualstack case we can associate with one VPN instance maximum 2 routers: one with
        // only IPv4 ports and one with only IPv6 ports, or only one router with IPv4/IPv6 ports
        // TODO: check router ports ethertype to follow this restriction
        if (oldRouters != null && !oldRouters.isEmpty()) {
            //remove to oldRouters the newRouters if existing
            List<Uuid> oldRoutersCopy = new ArrayList<>();
            oldRoutersCopy.addAll(oldRouters);
            if (newRouters != null) {
                newRouters.forEach(r -> oldRoutersCopy.remove(r));
            }
            /* dissociate old router */
            oldRoutersCopy.forEach(r -> {
                nvpnManager.dissociateRouterFromVpn(vpnId, r);
            });
        }
        if (newRouters != null && !newRouters.isEmpty()) {
            if (newRouters.size() > NeutronConstants.MAX_ROUTERS_PER_BGPVPN) {
                LOG.debug("In handleRoutersUpdate: maximum allowed number of associated routers is 2. VPN: {} "
                        + "is already associated with router: {} and with router: {}",
                        vpnId, newRouters.get(0).getValue(), newRouters.get(1).getValue());
                return;
            } else {
                for (Uuid routerId : newRouters) {
                    if (oldRouters != null && oldRouters.contains(routerId)) {
                        continue;
                    }
                    /* If the first time BGP-VPN is getting associated with router, then no need
                       to validate if the router is already been associated with any other BGP-VPN.
                       This will avoid unnecessary MD-SAL data store read operations in VPN-MAPS.
                     */
                    if (oldRouters == null || oldRouters.isEmpty()) {
                        nvpnManager.associateRouterToVpn(vpnId, routerId);
                    } else if (validateRouteInfo(routerId)) {
                        nvpnManager.associateRouterToVpn(vpnId, routerId);
                    }
                }
            }
        }
    }

    private boolean validateRouteInfo(Uuid routerID) {
        Uuid assocVPNId;
        if ((assocVPNId = neutronvpnUtils.getVpnForRouter(routerID, true)) != null) {
            LOG.warn("VPN router association failed due to router {} already associated to another VPN {}",
                    routerID.getValue(), assocVPNId.getValue());
            return false;
        }
        return true;
    }
}