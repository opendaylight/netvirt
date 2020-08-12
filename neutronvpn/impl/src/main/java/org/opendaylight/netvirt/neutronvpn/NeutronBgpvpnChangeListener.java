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
        } catch (UnsupportedOperationException e) {
            LOG.error("Error while processing Update Bgpvpn.", e);
            return;
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

}