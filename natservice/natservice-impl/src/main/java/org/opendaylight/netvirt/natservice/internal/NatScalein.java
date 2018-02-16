/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
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
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.scaleinservice.api.TombstonedNodeManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
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

    @Inject
    public NatScalein(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                        final IdManagerService idManager, final JobCoordinator jobCoordinator,
                        final NaptSwitchHA naptSwitchHA,
                        final IElanService elanManager,
                        final INeutronVpnManager nvpnManager,
                        final TombstonedNodeManager tombstonedNodeManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.idManager = idManager;
        this.naptSwitchHA = naptSwitchHA;
        this.jobCoordinator = jobCoordinator;
        this.tombstonedNodeManager = tombstonedNodeManager;
        this.elanManager = elanManager;
        this.nvpnManager = nvpnManager;
        tombstonedNodeManager.addOnRecoveryCallback((dpnId) -> {
            jobCoordinator.enqueueJob(dpnId.toString(), () -> {
                remove(dpnId);
                return null;
            });
            return null;
        });
    }

    protected void remove(BigInteger srcDpnId) {
        LOG.debug("Called Remove on addOnRecoveryCallback");
        InstanceIdentifier<DpnRoutersList> dpnRoutersListIdentifier = NatUtil
                .getDpnRoutersId(srcDpnId);
        Optional<DpnRoutersList> optionalDpnRoutersList =
            SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
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
                        BigInteger naptId = NatUtil
                                .getPrimaryNaptfromRouterName(dataBroker, routerName);
                        if (naptId == null || naptId.equals(BigInteger.ZERO)) {
                            long routerId = NatUtil.getVpnId(dataBroker, routerName);
                            long routerVpnId = getBgpVpnIdForRouter(routerId, routerName);

                            if (routerVpnId != -1) {
                                WriteTransaction writeFlowInvTx = dataBroker.newWriteOnlyTransaction();
                                WriteTransaction removeFlowInvTx = dataBroker.newWriteOnlyTransaction();

                                ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                                        routerName, extRouters.getNetworkId());
                                NatUtil.handleSNATForDPN(dataBroker, mdsalManager, idManager, naptSwitchHA,
                                        elanManager, nvpnManager,
                                        srcDpnId, routerName, routerId, routerVpnId, writeFlowInvTx, removeFlowInvTx,
                                        extNwProvType);
                                futures.add(NatUtil.waitForTransactionToComplete(writeFlowInvTx));
                                futures.add(NatUtil.waitForTransactionToComplete(removeFlowInvTx));
                            }
                        }
                        return futures;
                    }, NatConstants.NAT_DJC_MAX_RETRIES);
                }
            });
        }
    }

    private long getBgpVpnIdForRouter(long routerId, String routerName) {

        if (routerId == NatConstants.INVALID_ID) {
            LOG.error(
                    "NAT Service : SNAT -> Invalid ROUTER-ID {} returned for routerName {}",
                    routerId, routerName);
            return -1;
        }
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        long bgpVpnId;
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
                return -1;
            }
        }
        return bgpVpnId;
    }
}
