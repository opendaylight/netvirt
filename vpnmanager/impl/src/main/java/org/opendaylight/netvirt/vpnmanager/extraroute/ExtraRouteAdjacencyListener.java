/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.extraroute;

import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnClusterOwnershipDriver;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ExtraRouteAdjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.Destination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.destination.NextHop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExtraRouteAdjacencyListener extends AsyncClusteredDataTreeChangeListenerBase<NextHop,
        ExtraRouteAdjacencyListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ExtraRouteAdjacencyListener.class);
    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final OdlInterfaceRpcService interfaceMgrRpcService;
    private final IVpnClusterOwnershipDriver vpnClusterOwnershipDriver;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public ExtraRouteAdjacencyListener(final DataBroker dataBroker, final IdManagerService idManager,
                                       final IFibManager fibManager, OdlInterfaceRpcService interfaceMgrRpcService,
                                       final IVpnClusterOwnershipDriver vpnClusterOwnershipDriver,
                                       final JobCoordinator jobCoordinator, final VpnUtil vpnUtil) {
        super(NextHop.class, ExtraRouteAdjacencyListener.class);
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.interfaceMgrRpcService = interfaceMgrRpcService;
        this.vpnClusterOwnershipDriver = vpnClusterOwnershipDriver;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
        txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<NextHop> getWildCardPath() {
        return InstanceIdentifier.create(ExtraRouteAdjacency.class).child(Vpn.class).child(Destination.class)
                .child(NextHop.class);
    }

    @Override
    protected ExtraRouteAdjacencyListener getDataTreeChangeListener() {
        return ExtraRouteAdjacencyListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<NextHop> identifier, NextHop oldNextHop) {
        if (vpnClusterOwnershipDriver.amIOwner()) {
            LOG.trace("remove: {}", oldNextHop);
            final String extraRoute = identifier.firstKeyOf(Destination.class).getDestinationIp();
            final String vpnName = identifier.firstKeyOf(Vpn.class).getVpnName();
            final String extraRoutePrefix = VpnUtil.getIpPrefix(extraRoute);
            String interfaceName = oldNextHop.getInterfaceName();
            String extraRouteNexthop = oldNextHop.getNextHopIp();
            if (interfaceName != null) {
                Optional.ofNullable(vpnUtil.getPrimaryRd(vpnName)).ifPresent(primaryRd -> {
                    jobCoordinator.enqueueJob(primaryRd + '-' + extraRoutePrefix, () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                            final BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceMgrRpcService,
                                    interfaceName);
                            String tunnelNextHop = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
                            if (tunnelNextHop == null || tunnelNextHop.isEmpty()) {
                                LOG.error("delExtraRoute: NextHop for interface {} is null / empty. Failed"
                                                + " withdrawing extra route for rd {} prefix {} dpn {}", interfaceName,
                                        primaryRd, extraRoute, dpnId);
                            } else {
                                fibManager.removeOrUpdateFibEntry(primaryRd, extraRoutePrefix, tunnelNextHop, tx);
                            }
                        });
                        ListenableFutures.addErrorLogging(future, LOG, "remove: Failed for extra-route {} next-hop {},"
                                + "interface {} vpn {}", extraRoutePrefix, extraRouteNexthop, interfaceName, vpnName);
                        futures.add(future);
                        return futures;
                    });
                });
            } else {
                LOG.info("remove: Extra-route {} with next-hop {} on vpn {} not bound to any inteface. Ignoring...",
                        extraRoutePrefix, extraRouteNexthop, vpnName);
            }
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<NextHop> identifier,
                          NextHop oldExtraRouteAdjacency, NextHop newExtraRouteAdjacency) {
        //triggered when an interface is bound to an extra-route next-hop-ip
    }

    @Override
    protected void add(final InstanceIdentifier<NextHop> identifier,
                       final NextHop newNextHop) {
        if (vpnClusterOwnershipDriver.amIOwner()) {
            LOG.trace("add: {}", newNextHop);
            final String extraRoute = identifier.firstKeyOf(Destination.class).getDestinationIp();
            final String vpnName = identifier.firstKeyOf(Vpn.class).getVpnName();
            final String extraRoutePrefix = VpnUtil.getIpPrefix(extraRoute);
            final String extraRouteNextHop = newNextHop.getNextHopIp();
            final String interfaceName = newNextHop.getInterfaceName();
            if (interfaceName != null) {
                Optional.ofNullable(vpnUtil.getPrimaryRd(vpnName)).ifPresent(primaryRd -> {
                    jobCoordinator.enqueueJob(primaryRd + "-" + extraRoutePrefix, () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        if (!vpnUtil.isVpnPendingDelete(primaryRd)) {
                            long vpnId = vpnUtil.getVpnId(vpnName);
                            final BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceMgrRpcService,
                                    interfaceName);
                            vpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(vpnId, null, extraRoutePrefix, vpnName,
                                    extraRouteNextHop, dpnId)
                                    .ifPresent(allocatedRd -> {
                                        String tunnelNextHop = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker,
                                                dpnId);
                                        if (tunnelNextHop == null || tunnelNextHop.isEmpty()) {
                                            LOG.error("add: Tunnel NextHop for interface {} is null / empty."
                                                            + " Failed to process extra route for rd {} prefix {}"
                                                            + " nexthop {} dpn {}", interfaceName, primaryRd,
                                                    extraRoute, extraRouteNextHop, dpnId);
                                        } else {
                                            ListenableFuture<Void> configFuture = txRunner
                                                    .callWithNewWriteOnlyTransactionAndSubmit(configTx -> {
                                                        ListenableFuture<Void> operFuture = txRunner
                                                                .callWithNewWriteOnlyTransactionAndSubmit(operTx -> {
                                                                    operTx.merge(LogicalDatastoreType.OPERATIONAL,
                                                                            VpnExtraRouteHelper
                                                                                    .getVpnToExtrarouteVrfIdIdentifier(
                                                                                    vpnName, allocatedRd, extraRoute),
                                                                            VpnUtil.getVpnToExtraroute(extraRoute,
                                                                                    Collections.singletonList(
                                                                                            extraRouteNextHop)),
                                                                            WriteTransaction.CREATE_MISSING_PARENTS);
                                                                });
                                                        operFuture.get(); //Wait for oper data write
                                                        long label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
                                                                VpnUtil.getNextHopLabelKey(primaryRd,
                                                                        extraRoutePrefix));
                                                        if (label == VpnConstants.INVALID_LABEL) {
                                                            LOG.error("add: Unable to fetch label from Id Manager."
                                                                    + " Bailing out of processing extra-route {} for "
                                                                    + "vpn {} with nexthop {} tunnelNexthop {}",
                                                                    extraRoutePrefix, vpnName, extraRouteNextHop,
                                                                    tunnelNextHop);
                                                        } else {
                                                            VpnInstanceOpDataEntry vpnInstanceOpData = vpnUtil
                                                                    .getVpnInstanceOpData(primaryRd);
                                                            boolean isL3VpnOverVxLan = VpnUtil
                                                                    .isL3VpnOverVxLan(vpnInstanceOpData.getL3vni());
                                                            VrfEntry.EncapType encapType = VpnUtil.getEncapType(
                                                                    isL3VpnOverVxLan);
                                                            long l3vni = vpnInstanceOpData.getL3vni() == null ? 0L
                                                                    : vpnInstanceOpData.getL3vni();
                                                            L3vpnInput input = new L3vpnInput().setIpAddress(
                                                                    extraRoutePrefix).setNextHopIp(tunnelNextHop)
                                                                    .setL3vni(l3vni)
                                                                    .setLabel(label).setPrimaryRd(primaryRd)
                                                                    .setVpnName(vpnName).setEncapType(encapType)
                                                                    .setRouteOrigin(RouteOrigin.STATIC);
                                                            L3vpnRegistry.getRegisteredPopulator(encapType)
                                                                    .populateFib(input, configTx);
                                                        }
                                                    });
                                            ListenableFutures.addErrorLogging(configFuture, LOG, "add: failed for "
                                                            + "extra-route {} next-hop {} interface {} vpn {}",
                                                    extraRoutePrefix, extraRouteNextHop, interfaceName, vpnName);
                                            futures.add(configFuture);
                                        }
                                    });
                        } else {
                            LOG.error("add: VPN with primaryRd {} is scheduled for delete. Ignoring extra-route {}"
                                    + "processing", primaryRd, extraRoute);
                        }
                        return futures;
                    });
                });
            } else {
                LOG.info("add: Extra-route {} with nextHop {} on vpn {} not bound to any interface. Ignoring...",
                        extraRoutePrefix, extraRouteNextHop, vpnName);
            }
        }
    }
}
