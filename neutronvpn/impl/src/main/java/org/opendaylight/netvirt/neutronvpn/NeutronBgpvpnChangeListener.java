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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeL3;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.Bgpvpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.Bgpvpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronBgpvpnChangeListener extends AsyncDataTreeChangeListenerBase<Bgpvpn, NeutronBgpvpnChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final IdManagerService idManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final String adminRDValue;

    @Inject
    public NeutronBgpvpnChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
                                       final IdManagerService idManager, final NeutronvpnUtils neutronvpnUtils) {
        super(Bgpvpn.class, NeutronBgpvpnChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        this.idManager = idManager;
        this.neutronvpnUtils = neutronvpnUtils;
        BundleContext bundleContext = FrameworkUtil.getBundle(NeutronBgpvpnChangeListener.class).getBundleContext();
        adminRDValue = bundleContext.getProperty(NeutronConstants.RD_PROPERTY_KEY);
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        createIdPool();
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Bgpvpn> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Bgpvpns.class).child(Bgpvpn.class);
    }

    @Override
    protected NeutronBgpvpnChangeListener getDataTreeChangeListener() {
        return NeutronBgpvpnChangeListener.this;
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
    protected void add(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        LOG.trace("Adding Bgpvpn : key: {}, value={}", identifier, input);
        String vpnName = input.getUuid().getValue();
        if (isBgpvpnTypeL3(input.getType())) {
            VpnInstance.Type vpnInstanceType = VpnInstance.Type.L3;
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

            List<String> rd = input.getRouteDistinguishers();

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
                if (!rd.isEmpty()) {
                    try {
                        nvpnManager.createVpn(input.getUuid(), input.getName(), input.getTenantId(), rd,
                                importRouteTargets, exportRouteTargets, routersList, input.getNetworks(),
                                vpnInstanceType, 0 /*l3vni*/);
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
    protected void remove(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        LOG.trace("Removing Bgpvpn : key: {}, value={}", identifier, input);
        if (isBgpvpnTypeL3(input.getType())) {
            nvpnManager.removeVpn(input.getUuid());
            // Release RD Id in pool
            neutronvpnUtils.releaseRDId(NeutronConstants.RD_IDPOOL_NAME, input.getUuid().toString());
        } else {
            LOG.warn("BGPVPN type for VPN {} is not L3", input.getUuid().getValue());
        }
    }

    @Override
    protected void update(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn original, Bgpvpn update) {
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
                        nvpnManager.dissociateNetworksFromVpn(vpnId, oldNetworks);
                    }

                    //add new (Delta) Networks
                    if (!newNetworks.isEmpty()) {
                        LOG.trace("Adding delta New networks {} ", newNetworks);
                        nvpnManager.associateNetworksToVpn(vpnId, newNetworks);
                    }
                }
            } else {
                //add new Networks
                LOG.trace("Adding New networks {} ", newNetworks);
                nvpnManager.associateNetworksToVpn(vpnId, newNetworks);
            }
        } else if (oldNetworks != null && !oldNetworks.isEmpty()) {
            LOG.trace("Removing old networks {} ", oldNetworks);
            nvpnManager.dissociateNetworksFromVpn(vpnId, oldNetworks);

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
                    if (validateRouteInfo(routerId)) {
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
            if (result != null && result.get().isSuccessful()) {
                LOG.info("Created IdPool for Bgpvpn RD");
            } else {
                LOG.error("Failed to create ID pool for BGPVPN RD, result future returned {}", result);
            }
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
