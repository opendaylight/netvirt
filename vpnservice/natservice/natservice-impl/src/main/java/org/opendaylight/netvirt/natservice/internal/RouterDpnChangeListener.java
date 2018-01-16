/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RouterDpnChangeListener
        extends AsyncDataTreeChangeListenerBase<DpnVpninterfacesList, RouterDpnChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(RouterDpnChangeListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;
    private final NaptSwitchHA naptSwitchHA;
    private final IdManagerService idManager;
    private final INeutronVpnManager nvpnManager;
    private final ExternalNetworkGroupInstaller extNetGroupInstaller;
    private final IElanService elanManager;
    private final JobCoordinator coordinator;
    private final SnatServiceManager natServiceManager;
    private final NatMode natMode;

    @Inject
    public RouterDpnChangeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                   final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                                   final NaptSwitchHA naptSwitchHA,
                                   final IdManagerService idManager,
                                   final ExternalNetworkGroupInstaller extNetGroupInstaller,
                                   final INeutronVpnManager nvpnManager,
                                   final SnatServiceManager natServiceManager,
                                   final NatserviceConfig config,
                                   final IElanService elanManager,
                                   final JobCoordinator coordinator) {
        super(DpnVpninterfacesList.class, RouterDpnChangeListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.naptSwitchHA = naptSwitchHA;
        this.idManager = idManager;
        this.extNetGroupInstaller = extNetGroupInstaller;
        this.nvpnManager = nvpnManager;
        this.elanManager = elanManager;
        this.natServiceManager = natServiceManager;
        this.coordinator = coordinator;
        this.natMode = config != null ? config.getNatMode() : NatMode.Controller;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected RouterDpnChangeListener getDataTreeChangeListener() {
        return RouterDpnChangeListener.this;
    }

    @Override
    protected InstanceIdentifier<DpnVpninterfacesList> getWildCardPath() {
        return InstanceIdentifier.create(NeutronRouterDpns.class).child(RouterDpnList.class)
            .child(DpnVpninterfacesList.class);
    }

    @Override
    protected void add(final InstanceIdentifier<DpnVpninterfacesList> identifier, final DpnVpninterfacesList dpnInfo) {
        LOG.trace("add : key: {}, value: {}", dpnInfo.getKey(), dpnInfo);
        final String routerUuid = identifier.firstKeyOf(RouterDpnList.class).getRouterId();
        BigInteger dpnId = dpnInfo.getDpnId();
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
                    BigInteger naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, router.getRouterName());
                    if (naptSwitch == null || naptSwitch.equals(BigInteger.ZERO)) {
                        LOG.warn("add : NAPT switch is not selected.");
                        return;
                    }
                    //If it is for NAPT switch skip as the flows would be already programmed.
                    if (naptSwitch.equals(dpnId)) {
                        LOG.debug("Skipping the notification recived for NAPT switch {}", routerUuid);
                        return;
                    }
                    natServiceManager.notify(router, naptSwitch, dpnId,
                            SnatServiceManager.Action.SNAT_ROUTER_ENBL);
                } else {
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + dpnInfo.getKey(), () -> {
                        WriteTransaction writeFlowInvTx = dataBroker.newWriteOnlyTransaction();
                        WriteTransaction removeFlowInvTx = dataBroker.newWriteOnlyTransaction();
                        LOG.debug("add : Router {} is associated with ext nw {}", routerUuid, networkId);
                        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerUuid);
                        Long routerId = NatUtil.getVpnId(dataBroker, routerUuid);
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        if (routerId == NatConstants.INVALID_ID) {
                            LOG.error("add : Invalid routerId returned for routerName {}", routerUuid);
                            writeFlowInvTx.cancel();
                            removeFlowInvTx.cancel();
                            return futures;
                        }
                        extNetGroupInstaller.installExtNetGroupEntries(networkId, dpnId);
                        Long vpnId;
                        if (vpnName == null) {
                            LOG.debug("add : Internal vpn associated to router {}", routerUuid);
                            vpnId = routerId;
                            if (vpnId == NatConstants.INVALID_ID) {
                                LOG.error("add : Invalid vpnId returned for routerName {}", routerUuid);
                                writeFlowInvTx.cancel();
                                removeFlowInvTx.cancel();
                                return futures;
                            }
                            LOG.debug("add : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                            //Install default entry in FIB to SNAT table
                            LOG.info("add : Installing default route in FIB on dpn {} for router {} with vpn {}",
                                    dpnId, routerUuid, vpnId);
                            installDefaultNatRouteForRouterExternalSubnets(dpnId,
                                    NatUtil.getExternalSubnetIdsFromExternalIps(router.getExternalIps()));
                            snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId, writeFlowInvTx);
                        } else {
                            LOG.debug("add : External BGP vpn associated to router {}", routerUuid);
                            vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
                            if (vpnId == NatConstants.INVALID_ID) {
                                LOG.error("add : Invalid vpnId returned for routerName {}", routerUuid);
                                writeFlowInvTx.cancel();
                                removeFlowInvTx.cancel();
                                return futures;
                            }

                            LOG.debug("add : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                            //Install default entry in FIB to SNAT table
                            LOG.debug("add : Installing default route in FIB on dpn {} for routerId {} with "
                                    + "vpnId {}...", dpnId, routerUuid, vpnId);
                            installDefaultNatRouteForRouterExternalSubnets(dpnId,
                                    NatUtil.getExternalSubnetIdsFromExternalIps(router.getExternalIps()));
                            snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId, routerId, writeFlowInvTx);
                        }

                        if (router.isEnableSnat()) {
                            LOG.info("add : SNAT enabled for router {}", routerUuid);
                            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                                    routerUuid, networkId);
                            if (extNwProvType == null) {
                                LOG.error("add : External Network Provider Type missing");
                                writeFlowInvTx.cancel();
                                removeFlowInvTx.cancel();
                                return futures;
                            }
                            NatUtil.handleSNATForDPN(dataBroker, mdsalManager, idManager, naptSwitchHA,
                                    elanManager, nvpnManager,
                                    dpnId, routerUuid, routerId, vpnId, writeFlowInvTx, removeFlowInvTx,
                                    extNwProvType);
                        } else {
                            LOG.info("add : SNAT is not enabled for router {} to handle addDPN event {}",
                                    routerUuid, dpnId);
                        }
                        futures.add(NatUtil.waitForTransactionToComplete(writeFlowInvTx));
                        futures.add(NatUtil.waitForTransactionToComplete(removeFlowInvTx));
                        return futures;
                    }, NatConstants.NAT_DJC_MAX_RETRIES);
                } // end of controller based SNAT
            }
        } else {
            LOG.debug("add : Router {} is not associated with External network", routerUuid);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<DpnVpninterfacesList> identifier, DpnVpninterfacesList dpnInfo) {
        LOG.trace("remove : key: {}, value: {}", dpnInfo.getKey(), dpnInfo);
        final String routerUuid = identifier.firstKeyOf(RouterDpnList.class).getRouterId();
        Long routerId = NatUtil.getVpnId(dataBroker, routerUuid);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("REMOVE: Invalid routId returned for routerName {}",routerUuid);
            return;
        }
        BigInteger dpnId = dpnInfo.getDpnId();
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
                    BigInteger naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, router.getRouterName());
                    if (naptSwitch == null || naptSwitch.equals(BigInteger.ZERO)) {
                        LOG.warn("remove : NAPT switch is not selected.");
                        return;
                    }
                    //If it is for NAPT switch skip as the flows would be already programmed.
                    if (naptSwitch.equals(dpnId)) {
                        LOG.debug("Skipping the notification recived for NAPT switch {}", routerUuid);
                        return;
                    }
                    natServiceManager.notify(router, naptSwitch, dpnId,
                            SnatServiceManager.Action.SNAT_ROUTER_DISBL);
                } else {
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + dpnInfo.getKey(), () -> {
                        WriteTransaction removeFlowInvTx = dataBroker.newWriteOnlyTransaction();
                        LOG.debug("remove : Router {} is associated with ext nw {}", routerUuid, networkId);
                        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerUuid);
                        Long vpnId;
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        if (vpnName == null) {
                            LOG.debug("remove : Internal vpn associated to router {}", routerUuid);
                            vpnId = routerId;
                            if (vpnId == NatConstants.INVALID_ID) {
                                LOG.error("remove : Invalid vpnId returned for routerName {}", routerUuid);
                                removeFlowInvTx.cancel();
                                return futures;
                            }
                            LOG.debug("remove : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                            //Remove default entry in FIB
                            LOG.debug("remove : Removing default route in FIB on dpn {} for vpn {} ...", dpnId,
                                    vpnName);
                            snatDefaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId, removeFlowInvTx);
                        } else {
                            LOG.debug("remove : External vpn associated to router {}", routerUuid);
                            vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
                            if (vpnId == NatConstants.INVALID_ID) {
                                LOG.error("remove : Invalid vpnId returned for routerName {}", routerUuid);
                                removeFlowInvTx.cancel();
                                return futures;
                            }
                            LOG.debug("remove : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                            //Remove default entry in FIB
                            LOG.debug("remove : Removing default route in FIB on dpn {} for vpn {} ...", dpnId,
                                    vpnName);
                            snatDefaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId, routerId, removeFlowInvTx);
                        }

                        if (router.isEnableSnat()) {
                            LOG.info("remove : SNAT enabled for router {}", routerUuid);
                            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                                    routerUuid, networkId);

                            NatUtil.removeSNATFromDPN(dataBroker, mdsalManager, idManager, naptSwitchHA, dpnId,
                                    routerUuid, routerId, vpnId, extNwProvType, removeFlowInvTx);
                        } else {
                            LOG.info("remove : SNAT is not enabled for router {} to handle removeDPN event {}",
                                    routerUuid, dpnId);
                        }
                        futures.add(NatUtil.waitForTransactionToComplete(removeFlowInvTx));
                        return futures;
                    }, NatConstants.NAT_DJC_MAX_RETRIES);
                } // end of controller based SNAT
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<DpnVpninterfacesList> identifier, DpnVpninterfacesList original,
                          DpnVpninterfacesList update) {
        LOG.trace("Update key: {}, original: {}, update: {}", update.getKey(), original, update);
    }

    private void installDefaultNatRouteForRouterExternalSubnets(BigInteger dpnId, Collection<Uuid> externalSubnetIds) {
        if (externalSubnetIds == null) {
            LOG.error("installDefaultNatRouteForRouterExternalSubnets : No external subnets for router");
            return;
        }

        for (Uuid subnetId : externalSubnetIds) {
            long vpnIdForSubnet = NatUtil.getExternalSubnetVpnId(dataBroker, subnetId);
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
