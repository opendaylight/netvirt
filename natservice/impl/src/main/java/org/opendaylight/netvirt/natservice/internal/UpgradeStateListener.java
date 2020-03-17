/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.Optional;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractClusteredSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.serviceutils.upgrade.rev180702.UpgradeConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UpgradeStateListener extends AbstractClusteredSyncDataTreeChangeListener<UpgradeConfig> {
    private static final Logger LOG = LoggerFactory.getLogger(UpgradeStateListener.class);

    private final DataBroker dataBroker;
    private final CentralizedSwitchScheduler centralizedSwitchScheduler;
    private final NatserviceConfig.NatMode natMode;
    private final SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private IMdsalApiManager mdsalManager;
    private IdManagerService idManager;
    private final NaptSwitchHA naptSwitchHA;
    private final JobCoordinator coordinator;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public UpgradeStateListener(final DataBroker dataBroker,
                                final CentralizedSwitchScheduler centralizedSwitchScheduler,
                                final SNATDefaultRouteProgrammer defaultRouteProgrammer,
                                final IMdsalApiManager mdsalManager,
                                final IdManagerService idManager,
                                final NaptSwitchHA naptSwitchHA,
                                final NatserviceConfig config, final JobCoordinator coordinator) {
        super(dataBroker, new DataTreeIdentifier<>(
                LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(UpgradeConfig.class)));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
        this.defaultRouteProgrammer = defaultRouteProgrammer;
        this.mdsalManager = mdsalManager;
        this.idManager = idManager;
        this.coordinator = coordinator;
        this.naptSwitchHA = naptSwitchHA;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatserviceConfig.NatMode.Controller;
        }
        LOG.trace("UpgradeStateListener (nat) initialized");
    }

    @Override
    public void add(@NonNull UpgradeConfig newDataObject) {
    }

    @Override
    public void remove(@NonNull UpgradeConfig removedDataObject) {
        if (natMode == NatserviceConfig.NatMode.Conntrack) {
            return;
        }
        LOG.debug("Verify is all Elected Napt Switch and connected back post upgrade");
    }

    @Override
    public void update(@NonNull UpgradeConfig original, UpgradeConfig updated) {
        if (natMode == NatserviceConfig.NatMode.Controller) {
            if (original.isUpgradeInProgress() && !updated.isUpgradeInProgress()) {
                Optional<NaptSwitches> npatSwitches = NatUtil.getAllPrimaryNaptSwitches(dataBroker);
                if (npatSwitches.isPresent()) {
                    for (RouterToNaptSwitch routerToNaptSwitch : npatSwitches.get().nonnullRouterToNaptSwitch()) {
                        Uint64 primaryNaptDpnId = routerToNaptSwitch.getPrimarySwitchId();
                        if (!NatUtil.getSwitchStatus(dataBroker, routerToNaptSwitch.getPrimarySwitchId())) {
                            String routerUuid = routerToNaptSwitch.getRouterName();
                            coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routerUuid,
                                () -> Collections.singletonList(
                                    txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                        confTx -> reElectNewNaptSwitch(routerUuid, primaryNaptDpnId, confTx)
                                )), NatConstants.NAT_DJC_MAX_RETRIES);
                        }
                    }
                }
            }
            return;
        }

        LOG.info("UpgradeStateListener update from {} to {}", original, updated);
        if (!(original.isUpgradeInProgress() && !updated.isUpgradeInProgress())) {
            return;
        }

        SingleTransactionDataBroker reader = new SingleTransactionDataBroker(dataBroker);
        ExtRouters routers;
        try {
            routers = reader.syncRead(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(ExtRouters.class));
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading external routers", e);
            return;
        }

        for (Routers router : routers.nonnullRouters()) {
            List<ExternalIps> externalIps = router.getExternalIps();
            if (router.isEnableSnat() && externalIps != null && !externalIps.isEmpty()) {
                centralizedSwitchScheduler.scheduleCentralizedSwitch(router);
            }
        }
    }

    private void reElectNewNaptSwitch(String routerName, Uint64 primaryNaptDpnId,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        // Check if this is externalRouter else ignore
        InstanceIdentifier<Routers> extRoutersId = NatUtil.buildRouterIdentifier(routerName);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, extRoutersId);
        if (!routerData.isPresent()) {
            LOG.debug("reElectNewNaptSwitch : SNAT->Ignoring Re-election for router {} since its not External Router",
                    routerName);
            return;
        }
        Routers extRouters = routerData.get();
        Uuid networkId = extRouters.getNetworkId();
        if (networkId == null) {
            LOG.error("hndlTepDelForSnatInEachRtr : SNAT -> Ignoring Re-election  with Napt {} for router {}"
                    + "as external network configuraton is missing", primaryNaptDpnId, routerName);
            return;
        }
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        LOG.debug("hndlTepDelForSnatInEachRtr : SNAT->Router {} is associated with ext nw {}", routerId, networkId);
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        Uint32 bgpVpnId;
        if (bgpVpnUuid == null) {
            LOG.debug("hndlTepDelForSnatInEachRtr : SNAT->Internal VPN-ID {} associated to router {}",
                    routerId, routerName);
            bgpVpnId = routerId;
        } else {
            bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
            if (bgpVpnId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepDelForSnatInEachRtr :SNAT->Invalid Private BGP VPN ID returned for routerName {}",
                        routerName);
                return;
            }
        }
        defaultRouteProgrammer.removeDefNATRouteInDPN(primaryNaptDpnId, bgpVpnId, confTx);
        if (routerData.get().isEnableSnat()) {
            LOG.info("hndlTepDelForSnatInEachRtr : SNAT enabled for router {}", routerId);

            Uint32 routerVpnId = routerId;
            if (bgpVpnId != NatConstants.INVALID_ID) {
                LOG.debug("hndlTepDelForSnatInEachRtr : SNAT -> Private BGP VPN ID (Internal BGP VPN ID) {} "
                        + "associated to the router {}", bgpVpnId, routerName);
                routerVpnId = bgpVpnId;
            } else {
                LOG.debug("hndlTepDelForSnatInEachRtr : SNAT -> Internal L3 VPN ID (Router ID) {} "
                        + "associated to the router {}", routerVpnId, routerName);
            }
            //Re-elect the other available switch as the NAPT switch and program the NAT flows.
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                    routerName, networkId);
            String externalVpnName = NatUtil.getAssociatedVPN(dataBroker,extRouters.getNetworkId());
            if (extNwProvType == null) {
                return;
            }
            NatUtil.removeSNATFromDPN(dataBroker, mdsalManager, idManager, naptSwitchHA, primaryNaptDpnId, extRouters,
                    routerId, routerVpnId, externalVpnName, extNwProvType, confTx);

        } else {
            LOG.info("hndlTepDelForSnatInEachRtr : SNAT is not enabled for router {} to handle addDPN event {}",
                    routerId, primaryNaptDpnId);
        }
    }

}
