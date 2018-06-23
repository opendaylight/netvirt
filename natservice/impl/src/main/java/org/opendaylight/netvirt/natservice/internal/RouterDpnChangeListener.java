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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
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
    private final ManagedNewTransactionRunner txRunner;
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
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
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
        LOG.trace("add : key: {}, value: {}", dpnInfo.key(), dpnInfo);
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
                    Long routerId = NatUtil.getVpnId(dataBroker, routerUuid);
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
                                    NatUtil.getExternalSubnetIdsFromExternalIps(router.getExternalIps()));
                            return Collections.emptyList();
                        });
                    }
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + router.getRouterName(), () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>(2);
                        LOG.debug("add : Router {} is associated with ext nw {}", routerUuid, networkId);
                        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerUuid);
                        futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeFlowInvTx -> {
                            Long vpnId;
                            if (vpnName == null) {
                                LOG.debug("add : Internal vpn associated to router {}", routerUuid);
                                vpnId = routerId;
                                if (vpnId == NatConstants.INVALID_ID) {
                                    LOG.error("add : Invalid vpnId returned for routerName {}", routerUuid);
                                    return;
                                }
                                LOG.debug("add : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                                //Install default entry in FIB to SNAT table
                                LOG.info("add : Installing default route in FIB on dpn {} for router {} with vpn {}",
                                        dpnId, routerUuid, vpnId);
                                snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId, writeFlowInvTx);
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
                                snatDefaultRouteProgrammer.installDefNATRouteInDPN(dpnId, vpnId, routerId,
                                        writeFlowInvTx);
                            }
                            if (router.isEnableSnat()) {
                                LOG.info("add : SNAT enabled for router {}", routerUuid);
                                if (extNwProvType == null) {
                                    LOG.error("add : External Network Provider Type missing");
                                    return;
                                }
                                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(removeFlowInvTx -> {
                                    handleSNATForDPN(dpnId, routerUuid, routerId, vpnId, writeFlowInvTx,
                                            removeFlowInvTx, extNwProvType);
                                }));
                            } else {
                                LOG.info("add : SNAT is not enabled for router {} to handle addDPN event {}",
                                        routerUuid, dpnId);
                            }
                        }));
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
        LOG.trace("remove : key: {}, value: {}", dpnInfo.key(), dpnInfo);
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
                    coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routerUuid, () -> {
                        LOG.debug("remove : Router {} is associated with ext nw {}", routerUuid, networkId);
                        Uuid vpnName = NatUtil.getVpnForRouter(dataBroker, routerUuid);
                        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                            Long vpnId;
                            if (vpnName == null) {
                                LOG.debug("remove : Internal vpn associated to router {}", routerUuid);
                                vpnId = routerId;
                                if (vpnId == NatConstants.INVALID_ID) {
                                    LOG.error("remove : Invalid vpnId returned for routerName {}", routerUuid);
                                    return;
                                }
                                LOG.debug("remove : Retrieved vpnId {} for router {}", vpnId, routerUuid);
                                //Remove default entry in FIB
                                LOG.debug("remove : Removing default route in FIB on dpn {} for vpn {} ...", dpnId,
                                        vpnName);
                                snatDefaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId, tx);
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
                                snatDefaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, vpnId, routerId, tx);
                            }

                            if (router.isEnableSnat()) {
                                LOG.info("remove : SNAT enabled for router {}", routerUuid);
                                removeSNATFromDPN(dpnId, routerUuid, routerId, vpnId, networkId, tx);
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
    protected void update(InstanceIdentifier<DpnVpninterfacesList> identifier, DpnVpninterfacesList original,
                          DpnVpninterfacesList update) {
        LOG.trace("Update key: {}, original: {}, update: {}", update.key(), original, update);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    void handleSNATForDPN(BigInteger dpnId, String routerName, long routerId, Long routerVpnId,
            WriteTransaction writeFlowInvTx, WriteTransaction removeFlowInvTx, ProviderTypes extNwProvType) {
       //Check if primary and secondary switch are selected, If not select the role
        //Install select group to NAPT switch
        //Install default miss entry to NAPT switch
        BigInteger naptSwitch;
        try {
            BigInteger naptId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
            if (naptId == null || naptId.equals(BigInteger.ZERO) || !naptSwitchHA.getSwitchStatus(naptId)) {
                LOG.debug("handleSNATForDPN : No NaptSwitch is selected for router {}", routerName);
                naptSwitch = dpnId;
                boolean naptstatus = naptSwitchHA.updateNaptSwitch(routerName, naptSwitch);
                if (!naptstatus) {
                    LOG.error("handleSNATForDPN : Failed to update newNaptSwitch {} for routername {}",
                            naptSwitch, routerName);
                    return;
                }
                LOG.debug("handleSNATForDPN : Switch {} is elected as NaptSwitch for router {}", dpnId, routerName);

                // When NAPT switch is elected during first VM comes up for the given Router
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    NatOverVxlanUtil.validateAndCreateVxlanVniPool(dataBroker, nvpnManager,
                            idManager, NatConstants.ODL_VNI_POOL_NAME);
                }

                Routers extRouters = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
                if (extRouters != null) {
                    NatUtil.createRouterIdsConfigDS(dataBroker, routerId, routerName);
                    naptSwitchHA.subnetRegisterMapping(extRouters, routerId);
                }

                naptSwitchHA.installSnatFlows(routerName, routerId, naptSwitch, routerVpnId, writeFlowInvTx);

                // Install miss entry (table 26) pointing to table 46
                FlowEntity flowEntity = naptSwitchHA.buildSnatFlowEntityForNaptSwitch(dpnId, routerName,
                        routerVpnId, NatConstants.ADD_FLOW);
                if (flowEntity == null) {
                    LOG.error("handleSNATForDPN : Failed to populate flowentity for router {} with dpnId {}",
                            routerName, dpnId);
                    return;
                }
                LOG.debug("handleSNATForDPN : Successfully installed flow for dpnId {} router {}", dpnId, routerName);
                mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
                //Removing primary flows from old napt switch
                if (naptId != null && !naptId.equals(BigInteger.ZERO)) {
                    LOG.debug("handleSNATForDPN : Removing primary flows from old napt switch {} for router {}",
                            naptId, routerName);
                    naptSwitchHA.removeSnatFlowsInOldNaptSwitch(routerName, routerId, naptId, null, removeFlowInvTx);
                }
            } else if (naptId.equals(dpnId)) {
                LOG.debug("handleSNATForDPN : NaptSwitch {} gone down during cluster reboot came alive", naptId);
            } else {
                naptSwitch = naptId;
                LOG.debug("handleSNATForDPN : Napt switch with Id {} is already elected for router {}",
                        naptId, routerName);

                //installing group
                List<BucketInfo> bucketInfo = naptSwitchHA.handleGroupInNeighborSwitches(dpnId,
                        routerName, routerId, naptSwitch);
                naptSwitchHA.installSnatGroupEntry(dpnId, bucketInfo, routerName);

                // Install miss entry (table 26) pointing to group
                long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerName), idManager);
                FlowEntity flowEntity =
                        naptSwitchHA.buildSnatFlowEntity(dpnId, routerName, groupId,
                                routerVpnId, NatConstants.ADD_FLOW);
                if (flowEntity == null) {
                    LOG.error("handleSNATForDPN : Failed to populate flowentity for router {} with dpnId {} groupId {}",
                            routerName, dpnId, groupId);
                    return;
                }
                LOG.debug("handleSNATForDPN : Successfully installed flow for dpnId {} router {} group {}",
                        dpnId, routerName, groupId);
                mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
            }

        } catch (Exception ex) {
            LOG.error("handleSNATForDPN : Exception in handleSNATForDPN", ex);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    void removeSNATFromDPN(BigInteger dpnId, String routerName, long routerId, long routerVpnId,
            Uuid extNetworkId, WriteTransaction removeFlowInvTx) {
        //irrespective of naptswitch or non-naptswitch, SNAT default miss entry need to be removed
        //remove miss entry to NAPT switch
        //if naptswitch elect new switch and install Snat flows and remove those flows in oldnaptswitch

        Collection<String> externalIpCache = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
        ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, extNetworkId);
        if (extNwProvType == null) {
            return;
        }
        //Get the external IP labels other than VXLAN provider type. Since label is not applicable for VXLAN
        Map<String, Long> externalIpLabel;
        if (extNwProvType == ProviderTypes.VXLAN) {
            externalIpLabel = null;
        } else {
            externalIpLabel = NatUtil.getExternalIpsLabelForRouter(dataBroker, routerId);
        }
        BigInteger naptSwitch = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptSwitch == null || naptSwitch.equals(BigInteger.ZERO)) {
            LOG.error("removeSNATFromDPN : No naptSwitch is selected for router {}", routerName);
            return;
        }
        try {
            boolean naptStatus =
                naptSwitchHA.isNaptSwitchDown(routerName, routerId, dpnId, naptSwitch, routerVpnId,
                        externalIpCache, removeFlowInvTx);
            if (!naptStatus) {
                LOG.debug("removeSNATFromDPN: Switch with DpnId {} is not naptSwitch for router {}",
                    dpnId, routerName);
                long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerName), idManager);
                FlowEntity flowEntity = null;
                try {
                    flowEntity = naptSwitchHA.buildSnatFlowEntity(dpnId, routerName, groupId, routerVpnId,
                        NatConstants.DEL_FLOW);
                    if (flowEntity == null) {
                        LOG.error("removeSNATFromDPN : Failed to populate flowentity for router:{} "
                                + "with dpnId:{} groupId:{}", routerName, dpnId, groupId);
                        return;
                    }
                    LOG.debug("removeSNATFromDPN : Removing default SNAT miss entry flow entity {}", flowEntity);
                    mdsalManager.removeFlowToTx(flowEntity, removeFlowInvTx);

                } catch (Exception ex) {
                    LOG.error("removeSNATFromDPN : Failed to remove default SNAT miss entry flow entity {}",
                        flowEntity, ex);
                    return;
                }
                LOG.debug("removeSNATFromDPN : Removed default SNAT miss entry flow for dpnID {} with routername {}",
                    dpnId, routerName);

                //remove group
                GroupEntity groupEntity = null;
                try {
                    groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName,
                        GroupTypes.GroupAll, Collections.emptyList() /*listBucketInfo*/);
                    LOG.info("removeSNATFromDPN : Removing NAPT GroupEntity:{}", groupEntity);
                    mdsalManager.removeGroup(groupEntity);
                } catch (Exception ex) {
                    LOG.error("removeSNATFromDPN : Failed to remove group entity {}", groupEntity, ex);
                    return;
                }
                LOG.debug("removeSNATFromDPN : Removed default SNAT miss entry flow for dpnID {} with routerName {}",
                    dpnId, routerName);
            } else {
                naptSwitchHA.removeSnatFlowsInOldNaptSwitch(routerName, routerId, naptSwitch,
                        externalIpLabel, removeFlowInvTx);
                //remove table 26 flow ppointing to table46
                FlowEntity flowEntity = null;
                try {
                    flowEntity = naptSwitchHA.buildSnatFlowEntityForNaptSwitch(dpnId, routerName, routerVpnId,
                        NatConstants.DEL_FLOW);
                    if (flowEntity == null) {
                        LOG.error("removeSNATFromDPN : Failed to populate flowentity for router {} with dpnId {}",
                                routerName, dpnId);
                        return;
                    }
                    LOG.debug("removeSNATFromDPN : Removing default SNAT miss entry flow entity for router {} with "
                        + "dpnId {} in napt switch {}", routerName, dpnId, naptSwitch);
                    mdsalManager.removeFlowToTx(flowEntity, removeFlowInvTx);

                } catch (Exception ex) {
                    LOG.error("removeSNATFromDPN : Failed to remove default SNAT miss entry flow entity {}",
                        flowEntity, ex);
                    return;
                }
                LOG.debug("removeSNATFromDPN : Removed default SNAT miss entry flow for dpnID {} with routername {}",
                    dpnId, routerName);

                //best effort to check IntExt model
                naptSwitchHA.bestEffortDeletion(routerId, routerName, externalIpLabel, removeFlowInvTx);
            }
        } catch (Exception ex) {
            LOG.error("removeSNATFromDPN : Exception while handling naptSwitch down for router {}", routerName, ex);
        }
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
