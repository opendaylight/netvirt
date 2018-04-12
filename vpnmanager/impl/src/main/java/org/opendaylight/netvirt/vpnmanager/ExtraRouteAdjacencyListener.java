/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnClusterOwnershipDriver;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ExtraRouteAdjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.Destination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.destination.NextHop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Singleton
public class ExtraRouteAdjacencyListener extends AsyncDataTreeChangeListenerBase<Destination,
        ExtraRouteAdjacencyListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ExtraRouteAdjacencyListener.class);
    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final OdlInterfaceRpcService interfaceMgrRpcService;
    private final IVpnClusterOwnershipDriver vpnClusterOwnershipDriver;
    private final JobCoordinator jobCoordinator;

    @Inject
    public ExtraRouteAdjacencyListener(final DataBroker dataBroker, final IdManagerService idManager,
                                       final IFibManager fibManager, OdlInterfaceRpcService interfaceMgrRpcService,
                                       final IVpnClusterOwnershipDriver vpnClusterOwnershipDriver,
                                       final JobCoordinator jobCoordinator) {
        super(Destination.class, ExtraRouteAdjacencyListener.class);
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.interfaceMgrRpcService = interfaceMgrRpcService;
        this.vpnClusterOwnershipDriver = vpnClusterOwnershipDriver;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Destination> getWildCardPath() {
        return InstanceIdentifier.create(ExtraRouteAdjacency.class).child(Destination.class);
    }

    @Override
    protected ExtraRouteAdjacencyListener getDataTreeChangeListener() {
        return ExtraRouteAdjacencyListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Destination> identifier, Destination oldExtraRouteAdjacency) {
        if (vpnClusterOwnershipDriver.amIOwner()) {
            LOG.trace("remove: {}", oldExtraRouteAdjacency);
            final String extraRoute = oldExtraRouteAdjacency.getDestinationIp();
            final String extraRoutePrefix = VpnUtil.getIpPrefix(extraRoute);
            final String vpnName = oldExtraRouteAdjacency.getVpnName();
            Optional.ofNullable(VpnUtil.getPrimaryRd(dataBroker, vpnName)).ifPresent(primaryRd -> {
                jobCoordinator.enqueueJob(primaryRd + '-' + extraRoutePrefix, () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                    fibManager.removeFibEntry(primaryRd, extraRoutePrefix, writeConfigTxn);
                    futures.add(writeConfigTxn.submit());
                    return futures;
                });
            });
        }
    }

    protected void delAdjFromVpnInterface(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, Adjacency adj,
                                          BigInteger dpnId, WriteTransaction writeOperTxn, WriteTransaction writeConfigTxn) {
        com.google.common.base.Optional<VpnInterfaceOpDataEntry> optVpnInterface = VpnUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterfaceOpDataEntry currVpnIntf = optVpnInterface.get();

            InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
            com.google.common.base.Optional<AdjacenciesOp> optAdjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
            if (optAdjacencies.isPresent()) {
                List<Adjacency> adjacencies = optAdjacencies.get().getAdjacency();

                if (!adjacencies.isEmpty()) {
                    LOG.trace("delAdjFromVpnInterface: Adjacencies are {}", adjacencies);
                    Iterator<Adjacency> adjIt = adjacencies.iterator();
                    while (adjIt.hasNext()) {
                        Adjacency adjElem = adjIt.next();
                        if (adjElem.getIpAddress().equals(adj.getIpAddress())) {
                            String rd = adjElem.getVrfId();
                            adjIt.remove();

                            AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(adjacencies);
                            VpnInterfaceOpDataEntry newVpnIntf = VpnUtil
                                    .getVpnInterfaceOpDataEntry(currVpnIntf.getName(),
                                            currVpnIntf.getVpnInstanceName(), aug, dpnId, currVpnIntf.isScheduledForRemove(),
                                            currVpnIntf.getLportTag(), currVpnIntf.getGatewayMacAddress());

                            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf, true);
                            if (adj.getNextHopIpList() != null) {
                                for (String nh : adj.getNextHopIpList()) {
                                    deleteExtraRouteFromCurrentAndImportingVpns(
                                            currVpnIntf.getVpnInstanceName(), adj.getIpAddress(), nh, rd,
                                            currVpnIntf.getName(), writeConfigTxn);
                                }
                            } else if (adj.isPhysNetworkFunc()) {
                                LOG.info("delAdjFromVpnInterface: deleting PNF adjacency prefix {} subnet {}",
                                        adj.getIpAddress(), adj.getSubnetId());
                                fibManager.removeFibEntry(adj.getSubnetId().getValue(), adj.getIpAddress(),
                                        writeConfigTxn);
                            }
                            break;
                        }

                    }
                }
                LOG.info("delAdjFromVpnInterface: Removed adj {} on dpn {} rd {}", adj.getIpAddress(),
                        dpnId, adj.getVrfId());
            } else {
                LOG.error("delAdjFromVpnInterface: Cannnot DEL adjacency, since operational interface is "
                        + "unavailable dpnId {} adjIP {} rd {}", dpnId, adj.getIpAddress(), adj.getVrfId());
            }
        }
    }

    private void deleteExtraRouteFromCurrentAndImportingVpns(String vpnName, String destination, String nextHop,
                                                             String rd, String intfName, WriteTransaction writeConfigTxn) {
        vpnManager.delExtraRoute(vpnName, destination, nextHop, rd, vpnName, intfName, writeConfigTxn);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
        for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
            String vpnRd = vpn.getVrfId();
            if (vpnRd != null) {
                vpnManager.delExtraRoute(vpnName, destination, nextHop, vpnRd, vpnName, intfName, writeConfigTxn);
            }
        }
    }

    public void delExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
                              String intfName, WriteTransaction writeConfigTxn) {
        Boolean writeConfigTxnPresent = true;
        BigInteger dpnId = null;
        if (writeConfigTxn == null) {
            writeConfigTxnPresent = false;
            writeConfigTxn = dataBroker.newWriteOnlyTransaction();
        }
        String tunnelIp = nextHop;
        if (intfName != null && !intfName.isEmpty()) {
            dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.error("delExtraRoute: NextHop for interface {} is null / empty."
                                + " Failed advertising extra route for rd {} prefix {} dpn {}", intfName, rd, destination,
                        dpnId);
            }
            tunnelIp = nextHopIp;
        }
        if (rd != null) {
            String primaryRd = VpnUtil.getVpnRd(dataBroker, vpnName);
            removePrefixFromBGP(primaryRd, rd, vpnName, destination, nextHop, tunnelIp, dpnId, writeConfigTxn);
            LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName, rd);
        } else {
            // add FIB route directly
            fibManager.removeOrUpdateFibEntry(routerID, destination, tunnelIp, writeConfigTxn);
            LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName,
                    routerID);
        }
        if (!writeConfigTxnPresent) {
            writeConfigTxn.submit();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Destination> identifier,
                          Destination original, Destination update) {
        Optional.ofNullable(VpnUtil.getPrimaryRd(dataBroker, vpnName)).ifPresent(primaryRd -> {
            if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                List<Pair<String, String>> nextHopRdPair = new ArrayList<>();
                for (NextHop nextHop : nextHops) {
                    String interfaceName = nextHop.getInterfaceName();
                    BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceMgrRpcService,
                            interfaceName);
                    String tunnelNextHop = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
                    if (tunnelNextHop == null || tunnelNextHop.isEmpty()) {
                        LOG.error("delExtraRoute: NextHop for interface {} is null / empty. Failed withdrawing"
                                        + " extra route for rd {} prefix {} dpn {}", interfaceName, primaryRd,
                                extraRoute, dpnId);
                        continue;
                    }
                    nextHopRdPair.add(tunnelNextHop, );
                }
                if (primaryRd != vpnName) {
                    String primaryRd = VpnUtil.getVpnRd(dataBroker, vpnName);
                    removePrefixFromBGP(primaryRd, rd, vpnName, destination, nextHop, tunnelIp, dpnId, writeConfigTxn);
                    LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName, rd);
                } else {
                    // add FIB route directly
                    fibManager.removeOrUpdateFibEntry(vpnName, extraRoute, tunnelIp, writeConfigTxn);
                    LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName,
                            routerID);
                }
            }
        });
    }

    @Override
    protected void add(final InstanceIdentifier<Destination> identifier,
                       final Destination newExtraRouteAdjacency) {
        if (vpnClusterOwnershipDriver.amIOwner()) {
            LOG.trace("add: {}", newExtraRouteAdjacency);
            final String extraRoute = newExtraRouteAdjacency.getDestinationIp();
            final String extraRoutePrefix = VpnUtil.getIpPrefix(extraRoute);
            final List<NextHop> nextHops = newExtraRouteAdjacency.getNextHop();
            final String vpnName = newExtraRouteAdjacency.getVpnName();
            if (nextHops != null){
                Optional.ofNullable(VpnUtil.getPrimaryRd(dataBroker, vpnName)).ifPresent(primaryRd -> {
                    jobCoordinator.enqueueJob(primaryRd + "-" + extraRoutePrefix, () -> {
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                            List<Pair<String, String>> nhList = new ArrayList<>();
                            for (NextHop nextHop : nextHops) {
                                final String extraRouteNextHop = nextHop.getNextHopIp();
                                final String interfaceName = nextHop.getInterfaceName();
                                final BigInteger dpnId = InterfaceUtils.getDpnForInterface(interfaceMgrRpcService,
                                        interfaceName);
                                //Construct FIB and other objects to commit
                                VpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(dataBroker, vpnId, null,
                                        extraRoutePrefix, vpnName, extraRouteNextHop, dpnId)
                                        .ifPresent(allocatedRd -> {
                                            //Build nh-list for route-paths
                                            String tunnelNextHop = InterfaceUtils
                                                    .getEndpointIpAddressForDPN(dataBroker, dpnId);
                                            if (tunnelNextHop == null || tunnelNextHop.isEmpty()) {
                                                LOG.error("addExtraRoute: NextHop for interface {} is null / empty."
                                                                + " Failed advertising extra route for rd {} prefix {}"
                                                                + " nexthop {}dpn {}", interfaceName, primaryRd,
                                                        extraRoute, extraRouteNextHop, dpnId);
                                                return;
                                            }
                                            VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                                    VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName,
                                                            allocatedRd, extraRoute),
                                                    VpnUtil.getVpnToExtraroute(extraRoute,
                                                            Collections.singletonList(extraRouteNextHop)));
                                            nhList.add(new ImmutablePair<>(tunnelNextHop, allocatedRd));
                                        });
                            }
                            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                    VpnUtil.getNextHopLabelKey(primaryRd, extraRoutePrefix));
                            if (label == VpnConstants.INVALID_LABEL) {
                                String error = "Unable to fetch label from Id Manager. Bailing out of creation of"
                                        + " operational vpn interface adjacency " + extraRoutePrefix + "for vpn "
                                        + vpnName;
                                throw new NullPointerException(error);
                            }
                            VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker,
                                    primaryRd);
                            boolean isL3VpnOverVxLan = VpnUtil.isL3VpnOverVxLan(vpnInstanceOpData.getL3vni());
                            VrfEntry.EncapType encapType = VpnUtil.getEncapType(isL3VpnOverVxLan);
                            long l3vni = vpnInstanceOpData.getL3vni() == null ? 0L :  vpnInstanceOpData.getL3vni();
                            L3vpnInput input = new L3vpnInput().setIpAddress(extraRoutePrefix).setNextHopRdPair(nhList)
                                    .setL3vni(l3vni).setLabel(label).setPrimaryRd(primaryRd).setVpnName(vpnName)
                                    .setEncapType(encapType).setRouteOrigin(RouteOrigin.STATIC);
                            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
                            L3vpnRegistry.getRegisteredPopulator(encapType).populateFib(input, writeConfigTxn);
                            futures.add(writeConfigTxn.submit());
                        } else {
                            LOG.error("add: VPN with primaryRd {} is scheduled for delete. Ignoring extra-route {}"
                                    + "processing", primaryRd, extraRoute);
                        }
                        return futures;
                    });
                });
            } else {
                LOG.error("add: NextHops null for extra-route {} on vpn {}", extraRoute, vpnName);
            }
        }
    }
}
