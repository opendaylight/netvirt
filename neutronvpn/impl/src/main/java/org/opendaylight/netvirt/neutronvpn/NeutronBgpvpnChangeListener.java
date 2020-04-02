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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.infrautils.utils.concurrent.KeyedLocks;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
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
public class NeutronBgpvpnChangeListener extends AsyncDataTreeChangeListenerBase<Bgpvpn, NeutronBgpvpnChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final IdManagerService idManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final NeutronBgpvpnUtils neutronBgpvpnUtils;
    private final String adminRDValue;

    @Inject
    public NeutronBgpvpnChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
                                       final IdManagerService idManager, final NeutronvpnUtils neutronvpnUtils,
                                       final NeutronBgpvpnUtils neutronBgpvpnUtils) {
        super(Bgpvpn.class, NeutronBgpvpnChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        this.idManager = idManager;
        this.neutronvpnUtils = neutronvpnUtils;
        this.neutronBgpvpnUtils = neutronBgpvpnUtils;
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
        boolean isLockAcquired = false;
        KeyedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try {
            if (isBgpvpnTypeL3(input.getType())) {
                isLockAcquired = vpnLock.tryLock(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS);
                if (!isLockAcquired) {
                    LOG.error("Add BGPVPN: add bgpvpn failed for vpn : {} due to failure in "
                            + "acquiring lock", vpnName);
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

                    String errMessage = checkVpnCreation(input);
                    if (errMessage != null) {
                        LOG.error(errMessage);
                        return;
                    }
                    String[] rdParams = rd.get(0).split(":");
                    if (rdParams[0].trim().equals(adminRDValue)) {
                        LOG.error("AS specific part of RD should not be same as that defined by DC Admin. Error "
                                + "encountered for BGPVPN {} with RD {}", vpnName, rd.get(0));
                        return;
                    }
                    String vpnWithSameRd = neutronvpnUtils.getVpnForRD(rd.get(0));
                    if (vpnWithSameRd != null) {
                        LOG.error("Failed to create VPN {} as another VPN {} with the same RD {} already exists.",
                                vpnName, vpnWithSameRd, rd);
                        return;
                    }
                    Uuid router = null;
                    List<Uuid> unpRtrs = neutronBgpvpnUtils.getUnprocessedRoutersForBgpvpn(input.getUuid());
                    if (unpRtrs != null && !unpRtrs.isEmpty()) {
                        // currently only one router
                        router = unpRtrs.get(0);
                    }
                    List<Uuid> unpNets  = neutronBgpvpnUtils.getUnprocessedNetworksForBgpvpn(input.getUuid());
                    if (!rd.isEmpty()) {
                        try {
                            nvpnManager.createVpn(input.getUuid(), input.getName(), input.getTenantId(), rd,
                                    importRouteTargets, exportRouteTargets, router, unpNets,
                                    false /*isL2Vpn*/, 0 /*l3vni*/);
                            neutronBgpvpnUtils.getUnProcessedRoutersMap().remove(input.getUuid());
                            neutronBgpvpnUtils.getUnProcessedNetworksMap().remove(input.getUuid());
                        } catch (Exception e) {
                            LOG.error("Creation of BGPVPN {} failed with error ", vpnName, e);
                        }
                    } else {
                        LOG.error("Create BgpVPN with id {} failed due to missing RD value", vpnName);
                    }
                }
            } else {
                LOG.warn("BGPVPN type for VPN {} is not L3", vpnName);
            }
        } finally {
            if (isLockAcquired) {
                vpnLock.unlock(vpnName);
            }
        }
    }

    protected String checkVpnCreation(Bgpvpn input) {

        String vpnName = input.getName();
        List<String> rd = input.getRouteDistinguishers();
        String msg;

        if (rd == null || rd.isEmpty()) {
            msg = String.format("Creation of BGPVPN failed for VPN %s due to absence of RD input", vpnName);
            return msg;
        }
        Optional<String> operationalVpn = getExistingOperationalVpn(vpnName, rd.get(0));
        if (operationalVpn != null && operationalVpn.isPresent()) {
            msg = String.format("Creation of BGPVPN failed for VPN %s as another VPN %s with the same RD %s "
                    + "is still available. Please retry creation of a new vpn with the same RD"
                    + " after a couple of minutes.", vpnName, operationalVpn.get(), rd.get(0));
            return msg;
        }
        List<String> existingRDs = neutronvpnUtils.getExistingRDsExcludingVpn(vpnName);
        if (existingRDs != null && existingRDs.contains(rd.get(0))) {
            msg = String.format("Creation of BGPVPN failed for VPN %s as another VPN with the same RD %s "
                    + "is already configured", vpnName, rd.get(0));
            return msg;
        }
        return null;
    }

    private Optional<String> getExistingOperationalVpn(String vpnName, String primaryRd) {
        Optional<String> existingVpnName = Optional.absent();
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataOptional;
        try {
            vpnInstanceOpDataOptional = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, OPERATIONAL, neutronvpnUtils.getVpnOpDataIdentifier(primaryRd));
        } catch (ReadFailedException e) {
            LOG.error("getExistingOperationalVpn: Exception while checking operational status of vpn with rd {}",
                    primaryRd, e);
            /*Read failed. We don't know if a VPN exists or not.
             * Return primaryRd to halt caller execution, to be safe.*/
            return existingVpnName;
        }
        if (vpnInstanceOpDataOptional.isPresent()
                && !vpnInstanceOpDataOptional.get().getVpnInstanceName().equals(vpnName)) {
            existingVpnName = Optional.of(vpnInstanceOpDataOptional.get().getVpnInstanceName());
        }
        return existingVpnName;
    }

    @Override
    protected void remove(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn input) {
        LOG.trace("Removing Bgpvpn : key: {}, value={}", identifier, input);
        Uuid vpnId = input.getUuid();
        boolean isLockAcquired = false;
        KeyedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try {
            if (isBgpvpnTypeL3(input.getType())) {
                isLockAcquired = vpnLock.tryLock(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS);
                if (!isLockAcquired) {
                    LOG.error("Remove BGPVPN: remove bgpvpn failed for vpn : {} due to failure in "
                            + "acquiring lock", vpnName);
                    return;
                }
                neutronBgpvpnUtils.getUnProcessedRoutersMap().remove(input.getUuid());
                neutronBgpvpnUtils.getUnProcessedNetworksMap().remove(input.getUuid());
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
                    int releasedId = neutronvpnUtils.releaseId(NeutronConstants.RD_IDPOOL_NAME,
                            vpnName);
                    if (releasedId == NeutronConstants.INVALID_ID) {
                        LOG.error("NeutronBgpvpnChangeListener remove: Unable to release ID for key {}", vpnName);
                    }
                }
            } else {
                LOG.warn("BGPVPN type for VPN {} is not L3", vpnName);
            }
        } finally {
            if (isLockAcquired) {
                vpnLock.unlock(vpnName);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Bgpvpn> identifier, Bgpvpn original, Bgpvpn update) {
        LOG.trace("Update Bgpvpn : key: {}, value={}", identifier, update);
        if (Objects.equals(original, update)) {
            return;
        }
        Uuid vpnId = update.getUuid();
        String vpnName = vpnId.getValue();
        boolean isLockAcquired = false;
        KeyedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        if (isBgpvpnTypeL3(update.getType())) {
            try {
                isLockAcquired = vpnLock.tryLock(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS);
                if (!isLockAcquired) {
                    LOG.error("Update VPN: update failed for vpn : {} due to failure in acquiring lock", vpnName);
                    return;
                }
                handleVpnInstanceUpdate(original.getUuid().getValue(), original.getRouteDistinguishers(),
                        update.getRouteDistinguishers());
            } catch (UnsupportedOperationException e) {
                LOG.error("Error while processing Update Bgpvpn.", e);
                return;
            } finally {
                if (isLockAcquired) {
                    vpnLock.unlock(vpnName);
                }
            }
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
}
