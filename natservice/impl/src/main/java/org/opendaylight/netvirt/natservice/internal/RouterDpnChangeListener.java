/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterDpnChangeListener extends AbstractAsyncDataTreeChangeListener<DpnVpninterfacesList> {

    private static final Logger LOG = LoggerFactory.getLogger(RouterDpnChangeListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;
    private final NaptSwitchHA naptSwitchHA;
    private final IdManagerService idManager;
    private final INeutronVpnManager nvpnManager;
    private final ExternalNetworkGroupInstaller extNetGroupInstaller;
    private final JobCoordinator coordinator;
    private final SnatServiceManager natServiceManager;
    private final NatMode natMode;
    private final UpgradeState upgradeState;

    @Inject
    public RouterDpnChangeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                   final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                                   final NaptSwitchHA naptSwitchHA,
                                   final IdManagerService idManager,
                                   final ExternalNetworkGroupInstaller extNetGroupInstaller,
                                   final INeutronVpnManager nvpnManager,
                                   final SnatServiceManager natServiceManager,
                                   final NatserviceConfig config,
                                   final JobCoordinator coordinator,
                                   final UpgradeState upgradeState) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(NeutronRouterDpns.class)
                .child(RouterDpnList.class).child(DpnVpninterfacesList.class),
                Executors.newListeningSingleThreadExecutor("RouterDpnChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.naptSwitchHA = naptSwitchHA;
        this.idManager = idManager;
        this.extNetGroupInstaller = extNetGroupInstaller;
        this.nvpnManager = nvpnManager;
        this.natServiceManager = natServiceManager;
        this.coordinator = coordinator;
        this.natMode = config != null ? config.getNatMode() : NatMode.Controller;
        this.upgradeState = upgradeState;
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

    @Override
    public void add(final InstanceIdentifier<DpnVpninterfacesList> identifier, final DpnVpninterfacesList dpnInfo) {
        LOG.trace("add : key: {}, value: {}", dpnInfo.key(), dpnInfo);
        final String routerUuid = identifier.firstKeyOf(RouterDpnList.class).getRouterId();
        Uint64 dpnId = dpnInfo.getDpnId();
        //check router is associated to external network
        InstanceIdentifier<Routers> id = NatUtil.buildRouterIdentifier(routerUuid);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            Routers router = routerData.get();
            Uuid networkId = router.getNetworkId();
            if (networkId != null) {
                if (natMode == NatMode.Conntrack) {
                    Uint64 naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, router.getRouterName());
                    if (naptSwitch == null || naptSwitch.equals(Uint64.ZERO)) {
                        LOG.warn("add : NAPT switch is not selected.");
                        return;
                    }
                    //If it is for NAPT switch skip as the flows would be already programmed.
                    if (naptSwitch.equals(dpnId)) {
                        LOG.debug("Skipping the notification recived for NAPT switch {}", routerUuid);
                        return;
                    }
                    LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                        confTx -> {
                            natServiceManager.notify(confTx, router, null, naptSwitch, dpnId,
                                    SnatServiceManager.Action.CNT_ROUTER_ENBL);
                            if (router.isEnableSnat()) {
                                natServiceManager.notify(confTx, router, null, naptSwitch, naptSwitch,
                                        SnatServiceManager.Action.SNAT_ROUTER_ENBL);
                            }
                        }), LOG, "Error notifying NAT service manager");
                } else {
                    Uint32 routerId = NatUtil.getVpnId(dataBroker, routerUuid);
                    if (routerId == NatConstants.INVALID_ID) {
                        LOG.error("add : Invalid routerId returned for routerName {}", routerUuid);
                        return;
                    }
                    ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                            routerUuid, networkId);
                    if (extNwProvType == ProviderTypes.FLAT || extNwProvType == ProviderTypes.VLAN) {
                        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + networkId, () -> {
                            extNetGroupInstaller.installExtNetGroupEntries(networkId, dpnId);
                            installDefaultNatRouteForRouterExternalSubnets(dpnId,
                                    NatUtil.getExternalSubnetIdsFromExternalIps(
                                            new ArrayList<ExternalIps>(router.nonnullExternalIps().values())));
                            return Collections.emptyList();
                        });
                    }
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + router.getRouterName(), () -> {
                        LOG.debug("add : Router {} is associated with ext nw {}", routerUuid, networkId);
                        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerUuid);
                        return Collections.singletonList(
                            txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
                                Uint32 vpnId;
                                if (vpnName == null) {
                                    LOG.debug("add : Internal vpn associated to router {}", routerUuid);
                                    vpnId = routerId;
                                    if (vpnId == NatConstants.INVALID_ID) {
                                        LOG.error("add : Invalid vpnId returned for routerName {}", routerUuid);
                                        return;
                                    }
                                    LOG.debug("add : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                                    //Install default entry in FIB to SNAT table
                                    LOG.info(
                                        "add : Installing default route in FIB on dpn {} for router {} with vpn {}",
                                        dpnId, routerUuid, vpnId);
                                    snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId, confTx);
                                } else {
                                    LOG.debug("add : External BGP vpn associated to router {}", routerUuid);
                                    vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
                                    if (vpnId == NatConstants.INVALID_ID) {
                                        LOG.error("add : Invalid vpnId returned for routerName {}", routerUuid);
                                        return;
                                    }
                                    LOG.debug("add : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                                    //Install default entry in FIB to SNAT table
                                    LOG.debug("add : Installing default route in FIB on dpn {} for routerId {} with "
                                        + "vpnId {}...", dpnId, routerUuid, vpnId);
                                    snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId, routerId, confTx);
                                }
                                /* install V6 internet default fallback rule in FIB_TABLE if router
                                 * is having V6 subnet
                                 */
                                Uuid internetVpnId = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                                if (internetVpnId != null) {
                                    nvpnManager.programV6InternetFallbackFlow(new Uuid(routerUuid),
                                            internetVpnId, NwConstants.ADD_FLOW);
                                }
                                if (router.isEnableSnat()) {
                                    LOG.info("add : SNAT enabled for router {}", routerUuid);
                                    if (extNwProvType == null) {
                                        LOG.error("add : External Network Provider Type missing");
                                        return;
                                    }
                                    NatUtil.handleSNATForDPN(dataBroker, mdsalManager, idManager, naptSwitchHA,
                                        dpnId, router, routerId, vpnId, confTx, extNwProvType, upgradeState);
                                } else {
                                    LOG.info("add : SNAT is not enabled for router {} to handle addDPN event {}",
                                        routerUuid, dpnId);
                                }
                            }));
                    }, NatConstants.NAT_DJC_MAX_RETRIES);
                } // end of controller based SNAT
            }
        } else {
            LOG.debug("add : Router {} is not associated with External network", routerUuid);
        }
    }

    @Override
    public void remove(InstanceIdentifier<DpnVpninterfacesList> identifier, DpnVpninterfacesList dpnInfo) {
        LOG.trace("remove : key: {}, value: {}", dpnInfo.key(), dpnInfo);
        final String routerUuid = identifier.firstKeyOf(RouterDpnList.class).getRouterId();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerUuid);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("REMOVE: Invalid routId returned for routerName {}",routerUuid);
            return;
        }
        Uint64 dpnId = dpnInfo.getDpnId();
        //check router is associated to external network
        InstanceIdentifier<Routers> id = NatUtil.buildRouterIdentifier(routerUuid);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            Routers router = routerData.get();
            Uuid networkId = router.getNetworkId();
            if (networkId != null) {
                if (natMode == NatMode.Conntrack) {
                    Uint64 naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, router.getRouterName());
                    if (naptSwitch == null || naptSwitch.equals(Uint64.ZERO)) {
                        LOG.warn("remove : NAPT switch is not selected.");
                        return;
                    }
                    //If it is for NAPT switch skip as the flows would be already programmed.
                    if (naptSwitch.equals(dpnId)) {
                        LOG.debug("Skipping the notification recived for NAPT switch {}", routerUuid);
                        return;
                    }
                    LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                        confTx -> {
                            natServiceManager.notify(confTx, router, null, naptSwitch, dpnId,
                                    SnatServiceManager.Action.CNT_ROUTER_DISBL);
                            if (router.isEnableSnat()) {
                                natServiceManager.notify(confTx, router, null, naptSwitch, naptSwitch,
                                        SnatServiceManager.Action.SNAT_ROUTER_DISBL);
                            }
                        }), LOG, "Error notifying NAT service manager");
                } else {
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routerUuid, () -> {
                        LOG.debug("remove : Router {} is associated with ext nw {}", routerUuid, networkId);
                        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerUuid);
                        return Collections.singletonList(
                            txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
                                Uint32 vpnId;
                                if (vpnName == null) {
                                    LOG.debug("remove : Internal vpn associated to router {}", routerUuid);
                                    vpnId = routerId;
                                    if (vpnId == NatConstants.INVALID_ID) {
                                        LOG.error("remove : Invalid vpnId returned for routerName {}", routerUuid);
                                        return;
                                    }
                                    LOG.debug("remove : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                                    //Remove default entry in FIB
                                    LOG.debug("remove : Removing default route in FIB on dpn {} ...", dpnId);
                                    snatDefaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId, confTx);
                                } else {
                                    LOG.debug("remove : External vpn associated to router {}", routerUuid);
                                    vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
                                    if (vpnId == NatConstants.INVALID_ID) {
                                        LOG.error("remove : Invalid vpnId returned for routerName {}", routerUuid);
                                        return;
                                    }
                                    LOG.debug("remove : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                                    //Remove default entry in FIB
                                    LOG.debug("remove : Removing default route in FIB on dpn {} for vpn {} ...", dpnId,
                                        vpnName);
                                    snatDefaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId, routerId, confTx);
                                }
                                /* remove V6 internet default fallback rule in FIB_TABLE if router
                                 * is having V6 subnet
                                 */
                                Uuid internetVpnId = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                                if (internetVpnId != null) {
                                    nvpnManager.programV6InternetFallbackFlow(new Uuid(routerUuid), internetVpnId,
                                            NwConstants.DEL_FLOW);
                                }
                                if (router.isEnableSnat()) {
                                    ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                                        routerUuid, networkId);
                                    if (extNwProvType == null) {
                                        return;
                                    }
                                    LOG.info("remove : SNAT enabled for router {}", routerUuid);
                                    String externalVpnName = NatUtil.getAssociatedVPN(dataBroker,
                                        routerData.get().getNetworkId());
                                    NatUtil.removeSNATFromDPN(dataBroker, mdsalManager, idManager, naptSwitchHA, dpnId,
                                        router, routerId, vpnId, externalVpnName, extNwProvType, confTx);
                                } else {
                                    LOG.info("remove : SNAT is not enabled for router {} to handle removeDPN event {}",
                                        routerUuid, dpnId);
                                }
                            }));
                    }, NatConstants.NAT_DJC_MAX_RETRIES);
                } // end of controller based SNAT
            }
        }
    }

    @Override
    public void update(InstanceIdentifier<DpnVpninterfacesList> identifier, DpnVpninterfacesList original,
                          DpnVpninterfacesList update) {
        LOG.trace("Update key: {}, original: {}, update: {}", update.key(), original, update);
    }

    private void installDefaultNatRouteForRouterExternalSubnets(Uint64 dpnId, Collection<Uuid> externalSubnetIds) {
        if (externalSubnetIds == null) {
            LOG.error("installDefaultNatRouteForRouterExternalSubnets : No external subnets for router");
            return;
        }

        for (Uuid subnetId : externalSubnetIds) {
            Uint32 vpnIdForSubnet = NatUtil.getExternalSubnetVpnId(dataBroker, subnetId);
            if (vpnIdForSubnet != NatConstants.INVALID_ID) {
                LOG.info("installDefaultNatRouteForRouterExternalSubnets : Installing default routes in FIB on dpn {} "
                        + "for subnetId {} with vpnId {}", dpnId, subnetId, vpnIdForSubnet);
                snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnIdForSubnet, subnetId.getValue());
            } else {
                LOG.debug("installDefaultNatRouteForRouterExternalSubnets : No vpnID for subnet {} found", subnetId);
            }
        }
    }
}
