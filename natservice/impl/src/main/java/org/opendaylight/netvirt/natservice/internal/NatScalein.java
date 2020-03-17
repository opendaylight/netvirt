/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.cloudscaler.api.TombstonedNodeManager;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatScalein {

    private static final Logger LOG = LoggerFactory.getLogger(NatScalein.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final JobCoordinator jobCoordinator;
    private NaptSwitchHA naptSwitchHA;
    private final IElanService elanManager;
    private final INeutronVpnManager nvpnManager;
    private final TombstonedNodeManager tombstonedNodeManager;
    private final ManagedNewTransactionRunner txRunner;
    private final UpgradeState upgradeState;

    @Inject
    public NatScalein(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
        final IdManagerService idManager, final JobCoordinator jobCoordinator,
        final NaptSwitchHA naptSwitchHA,
        final IElanService elanManager,
        final INeutronVpnManager nvpnManager,
        final TombstonedNodeManager tombstonedNodeManager,
        final UpgradeState upgradeState) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.idManager = idManager;
        this.naptSwitchHA = naptSwitchHA;
        this.jobCoordinator = jobCoordinator;
        this.tombstonedNodeManager = tombstonedNodeManager;
        this.elanManager = elanManager;
        this.nvpnManager = nvpnManager;
        this.upgradeState = upgradeState;
        tombstonedNodeManager.addOnRecoveryCallback((dpnId) -> {
            jobCoordinator.enqueueJob(dpnId.toString(), () -> {
                remove(dpnId);
                return null;
            });
            return null;
        });
    }

    protected void remove(Uint64 srcDpnId) {
        LOG.debug("Called Remove on addOnRecoveryCallback");
        InstanceIdentifier<DpnRoutersList> dpnRoutersListIdentifier = NatUtil
            .getDpnRoutersId(srcDpnId);
        Optional<DpnRoutersList> optionalDpnRoutersList =
            SingleTransactionDataBroker
                .syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, dpnRoutersListIdentifier);
        if (optionalDpnRoutersList.isPresent()) {
            List<RoutersList> routersListFromDs = optionalDpnRoutersList.get()
                .getRoutersList();
            routersListFromDs.forEach(router -> {
                String routerName = router.getRouter();
                Routers extRouters = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
                if (extRouters != null && extRouters.isEnableSnat()) {
                    jobCoordinator.enqueueJob((NatConstants.NAT_DJC_PREFIX + routerName), () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        Uint64 naptId = NatUtil
                            .getPrimaryNaptfromRouterName(dataBroker, routerName);
                        if (naptId == null || naptId.equals(Uint64.ZERO)) {
                            Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
                            Uint32 routerVpnId = getBgpVpnIdForRouter(routerId, routerName);

                            if (routerVpnId != NatConstants.INVALID_ID) {
                                ProviderTypes extNwProvType = NatEvpnUtil
                                    .getExtNwProvTypeFromRouterName(dataBroker,
                                        routerName, extRouters.getNetworkId());
                                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                    confTx -> {
                                        NatUtil
                                            .handleSNATForDPN(dataBroker, mdsalManager, idManager,
                                                naptSwitchHA,
                                                srcDpnId, extRouters, routerId, routerVpnId, confTx,
                                                extNwProvType, upgradeState);
                                    });
                            }
                        }
                        return futures;
                    }, NatConstants.NAT_DJC_MAX_RETRIES);
                }
            });
        }
    }

    private Uint32 getBgpVpnIdForRouter(Uint32 routerId, String routerName) {

        if (routerId == NatConstants.INVALID_ID) {
            LOG.error(
                "NAT Service : SNAT -> Invalid ROUTER-ID {} returned for routerName {}",
                routerId, routerName);
            return NatConstants.INVALID_ID;
        }
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        Uint32 bgpVpnId;
        if (bgpVpnUuid == null) {
            LOG.debug(
                "NAT Service : SNAT -> Internal VPN-ID {} associated to router {}",
                routerId, routerName);
            bgpVpnId = routerId;
        } else {
            bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
            if (bgpVpnId == NatConstants.INVALID_ID) {
                LOG.error(
                    "NAT Service : SNAT -> Invalid Private BGP VPN ID returned for routerName {}",
                    routerName);
                return NatConstants.INVALID_ID;
            }
        }
        return bgpVpnId;
    }
}
