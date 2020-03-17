/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeL3;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.Bgpvpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.Bgpvpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronBgpvpnChangeListener extends AbstractAsyncDataTreeChangeListener<Bgpvpn> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final IdManagerService idManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final String adminRDValue;

    @Inject
    public NeutronBgpvpnChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
                                       final IdManagerService idManager, final NeutronvpnUtils neutronvpnUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Bgpvpns.class).child(Bgpvpn.class), Executors.newSingleThreadExecutor(
                        "NeutronBgpvpnChangeListener", LOG));
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        this.idManager = idManager;
        this.neutronvpnUtils = neutronvpnUtils;
        BundleContext bundleContext = FrameworkUtil.getBundle(NeutronBgpvpnChangeListener.class).getBundleContext();
        adminRDValue = bundleContext.getProperty(NeutronConstants.RD_PROPERTY_KEY);
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        createIdPool();
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
        if (isBgpvpnTypeL3(input.getType())) {
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
            List<String> importRouteTargets = new ArrayList<>();
            List<String> exportRouteTargets = new ArrayList<>();
            importRouteTargets.addAll(inputImportRouteSet);
            exportRouteTargets.addAll(inputExportRouteSet);

            List<String> rd = input.getRouteDistinguishers() != null
                    ? input.getRouteDistinguishers() : new ArrayList<>();

            if (rd == null || rd.isEmpty()) {
                // generate new RD
                // TODO - commented out for now to avoid "Dead store to rd" violation.
                //rd = generateNewRD(input.getUuid());
            } else {
                String[] rdParams = rd.get(0).split(":");
                if (rdParams[0].trim().equals(adminRDValue)) {
                    LOG.error("AS specific part of RD should not be same as that defined by DC Admin. Error "
                            + "encountered for BGPVPN {} with RD {}", vpnName, rd.get(0));
                    return;
                }
                List<String> existingRDs = neutronvpnUtils.getExistingRDs();
                if (!Collections.disjoint(existingRDs, rd)) {
                    LOG.error("Failed to create VPN {} as another VPN with the same RD {} already exists.", vpnName,
                            rd);
                    return;
                }
                List<Uuid> routersList = null;
                if (input.getRouters() != null && !input.getRouters().isEmpty()) {
                    // try to take all routers
                    routersList = input.getRouters();
                }
                if (routersList != null && routersList.size() > NeutronConstants.MAX_ROUTERS_PER_BGPVPN) {
                    LOG.error("Creation of BGPVPN for rd {} failed: maximum allowed number of associated "
                             + "routers is {}.", rd, NeutronConstants.MAX_ROUTERS_PER_BGPVPN);
                    return;
                }
                List<Uuid> networkList = null;
                if (input.getNetworks() != null && !input.getNetworks().isEmpty()) {
                    networkList = input.getNetworks();
                }
                if (!rd.isEmpty()) {
                    try {
                        nvpnManager.createVpn(input.getUuid(), input.getName(), input.getTenantId(), rd,
                                importRouteTargets, exportRouteTargets, routersList, networkList,
                                false /*isL2Vpn*/, 0 /*l3vni*/);
                    } catch (Exception e) {
                        LOG.error("Creation of BGPVPN {} failed", vpnName, e);
                    }
                } else {
                    LOG.error("Create BgpVPN with id {} failed due to missing RD value", vpnName);
                }
            }
        } else {
            LOG.warn("BGPVPN type for VPN {} is not L3", vpnName);
        }
    }

    @Override
    public void remove(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        LOG.trace("Removing Bgpvpn : key: {}, value={}", identifier, input);
        Uuid vpnId = input.getUuid();
        if (isBgpvpnTypeL3(input.getType())) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            if (vpnMap == null) {
                LOG.error("Failed to handle BGPVPN Remove for VPN {} as that VPN is not configured"
                        + " yet as a VPN Instance", vpnId.getValue());
                return;
            }
            nvpnManager.removeVpn(input.getUuid());
            // Release RD Id in pool
            List<String> rd = input.getRouteDistinguishers();
            if (rd == null || rd.isEmpty()) {
                int releasedId = neutronvpnUtils.releaseId(NeutronConstants.RD_IDPOOL_NAME, vpnId.getValue());
                if (releasedId == NeutronConstants.INVALID_ID) {
                    LOG.error("NeutronBgpvpnChangeListener remove: Unable to release ID for key {}", vpnId.getValue());
                }
            }
        } else {
            LOG.warn("BGPVPN type for VPN {} is not L3", vpnId.getValue());
        }
    }

    @Override
    public void update(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn original, Bgpvpn update) {
        LOG.trace("Update Bgpvpn : key: {}, value={}", identifier, update);
        Uuid vpnId = update.getUuid();
        if (isBgpvpnTypeL3(update.getType())) {
            try {
                handleVpnInstanceUpdate(original.getUuid().getValue(), original.getRouteDistinguishers(),
                        update.getRouteDistinguishers());
            } catch (UnsupportedOperationException e) {
                LOG.error("Error while processing Update Bgpvpn.", e);
                return;
            }
            List<Uuid> oldNetworks = original.getNetworks();
            List<Uuid> newNetworks = update.getNetworks();
            handleNetworksUpdate(vpnId, oldNetworks, newNetworks);
            List<Uuid> oldRouters = original.getRouters();
            List<Uuid> newRouters = update.getRouters();
            handleRoutersUpdate(vpnId, oldRouters, newRouters);
        } else {
            LOG.warn("BGPVPN type for VPN {} is not L3", vpnId.getValue());
        }
    }

    protected void handleVpnInstanceUpdate(String vpnInstanceName,final List<String> originalRds,
                                           List<String> updateRDs) throws UnsupportedOperationException {
        if (updateRDs == null || updateRDs.isEmpty()) {
            return;
        }
        int oldRdsCount = originalRds.size();

        for (String rd : originalRds) {
            //If the existing rd is not present in the updateRds list, not allow to process the updateRDs.
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

    protected void handleNetworksUpdate(Uuid vpnId, List<Uuid> oldNetworks, List<Uuid> newNetworks) {
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

    protected void handleRoutersUpdate(Uuid vpnId, List<Uuid> oldRouters, List<Uuid> newRouters) {
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

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder().setPoolName(NeutronConstants.RD_IDPOOL_NAME)
                .setLow(NeutronConstants.RD_IDPOOL_START)
                .setHigh(new BigInteger(NeutronConstants.RD_IDPOOL_SIZE).longValue()).build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            Collection<RpcError> rpcErrors = null;
            if (result != null && result.get() != null) {
                RpcResult<CreateIdPoolOutput> rpcResult = result.get();
                LOG.info("Created IdPool for Bgpvpn RD");
                if (rpcResult.isSuccessful()) {
                    LOG.info("Created IdPool for Bgpvpn RD");
                    return;
                }
                rpcErrors = rpcResult.getErrors();
                LOG.error("Failed to create ID pool for BGPVPN RD, result future returned {}", result);
            }
            LOG.error("createIdPool: Failed to create ID pool for BGPVPN RD, the call returned with RPC errors {}",
                    rpcErrors != null ? rpcErrors : "RpcResult is null");
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for Bgpvpn RD", e);
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
