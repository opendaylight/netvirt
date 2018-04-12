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
    protected void remove(InstanceIdentifier<Destination> identifier, Destination value) {
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Destination> identifier,
                          Destination original, Destination update) {
    }

    @Override
    protected void add(final InstanceIdentifier<Destination> identifier,
                       final Destination value) {
        LOG.trace("add: {}", value);
        if (vpnClusterOwnershipDriver.amIOwner()) {
            final String extraRoute = value.getDestinationIp();
            final String extraRoutePrefix = VpnUtil.getIpPrefix(extraRoute);
            final List<NextHop> nextHops = value.getNextHop();
            final String vpnName = value.getVpnName();
            if (nextHops != null){
                Optional.ofNullable(VpnUtil.getPrimaryRd(dataBroker, vpnName)).ifPresent(primaryRd -> {
                    if (!VpnUtil.isVpnPendingDelete(dataBroker, primaryRd)) {
                        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                        jobCoordinator.enqueueJob(primaryRd + "-" + extraRoute, () -> {
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
                                    String tunnelNextHop = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
                                    if (tunnelNextHop == null || tunnelNextHop.isEmpty()) {
                                        LOG.error("addExtraRoute: NextHop for interface {} is null / empty."
                                                        + " Failed advertising extra route for rd {} prefix {}"
                                                + " nexthop {}dpn {}", interfaceName, primaryRd, extraRoute,
                                                extraRouteNextHop, dpnId);
                                        return;
                                    }
                                    VpnUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                            VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, allocatedRd,
                                                    extraRoute), VpnUtil.getVpnToExtraroute(extraRoute, Collections
                                                    .singletonList(extraRouteNextHop)));
                                    nhList.add(new ImmutablePair<>(tunnelNextHop, allocatedRd));
                                });
                            }
                            List<ListenableFuture<Void>> futures = new ArrayList<>();
                            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                    VpnUtil.getNextHopLabelKey(primaryRd, extraRoutePrefix));
                            if (label == VpnConstants.INVALID_LABEL) {
                                String error = "Unable to fetch label from Id Manager. Bailing out of creation of"
                                        + " operational vpn interface adjacency " + extraRoutePrefix + "for vpn "
                                        + vpnName;
                                throw new NullPointerException(error);
                            }
                            L3vpnInput input = new L3vpnInput().setIpAddress(extraRoute).setNextHopRdPair(nhList)
                                    .setL3vni(l3vni).setLabel(label).setPrimaryRd(primaryRd).setVpnName(vpnName)
                                    .setDpnId(dpnId).setEncapType(encapType).setRd(rd).setRouteOrigin(origin);
                            L3vpnRegistry.getRegisteredPopulator(encapType).populateFib(input, writeConfigTxn);
                            return futures;
                        });
                    } else {
                        LOG.error("add: VPN with primaryRd {} is scheduled for delete. Ignoring extra-route {}"
                                + "processing", primaryRd, extraRoute);
                    }
                });
            } else {
                LOG.error("add: NextHops null for extra-route {} on vpn {}", extraRoute, vpnName);
            }
        }
    }

    public void addExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
                              Long l3vni, RouteOrigin origin, String intfName, Adjacency operationalAdj,
                              VrfEntry.EncapType encapType, WriteTransaction writeConfigTxn) {

        Boolean writeConfigTxnPresent = true;
        if (writeConfigTxn == null) {
            writeConfigTxnPresent = false;
            writeConfigTxn = dataBroker.newWriteOnlyTransaction();
        }

        //add extra route to vpn mapping; advertise with nexthop as tunnel ip
        VpnUtil.syncUpdate(
                dataBroker,
                LogicalDatastoreType.OPERATIONAL,
                VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, rd != null ? rd : routerID,
                        destination),
                VpnUtil.getVpnToExtraroute(destination, Collections.singletonList(nextHop)));

        BigInteger dpnId = null;
        if (intfName != null && !intfName.isEmpty()) {
            dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.error("addExtraRoute: NextHop for interface {} is null / empty."
                                + " Failed advertising extra route for rd {} prefix {} dpn {}", intfName, rd, destination,
                        dpnId);
                return;
            }
            nextHop = nextHopIp;
        }

        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);

        // TODO: This is a limitation to be stated in docs. When configuring static route to go to
        // another VPN, there can only be one nexthop or, at least, the nexthop to the interVpnLink should be in
        // first place.
        com.google.common.base.Optional<InterVpnLinkDataComposite> optVpnLink = interVpnLinkCache.getInterVpnLinkByEndpoint(nextHop);
        if (optVpnLink.isPresent() && optVpnLink.get().isActive()) {
            InterVpnLinkDataComposite interVpnLink = optVpnLink.get();
            // If the nexthop is the endpoint of Vpn2, then prefix must be advertised to Vpn1 in DC-GW, with nexthops
            // pointing to the DPNs where Vpn1 is instantiated. LFIB in these DPNS must have a flow entry, with lower
            // priority, where if Label matches then sets the lportTag of the Vpn2 endpoint and goes to LportDispatcher
            // This is like leaking one of the Vpn2 routes towards Vpn1
            String srcVpnUuid = interVpnLink.getVpnNameByIpAddress(nextHop);
            String dstVpnUuid = interVpnLink.getOtherVpnNameByIpAddress(nextHop);
            String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
            long newLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(dstVpnRd, destination));
            if (newLabel == 0) {
                LOG.error("addExtraRoute: Unable to fetch label from Id Manager. Bailing out of adding intervpnlink"
                        + " route for destination {}", destination);
                return;
            }
            ivpnLinkService.leakRoute(interVpnLink, srcVpnUuid, dstVpnUuid, destination, newLabel, RouteOrigin.STATIC);
        } else {
            com.google.common.base.Optional<Routes> optVpnExtraRoutes = VpnExtraRouteHelper
                    .getVpnExtraroutes(dataBroker, vpnName, rd != null ? rd : routerID, destination);
            if (optVpnExtraRoutes.isPresent()) {
                List<String> nhList = optVpnExtraRoutes.get().getNexthopIpList();
                if (nhList != null && nhList.size() > 1) {
                    // If nhList is greater than one for vpnextraroute, a call to populatefib doesn't update vrfentry.
                    fibManager.refreshVrfEntry(primaryRd, destination);
                } else {
                    L3vpnInput input = new L3vpnInput().setNextHop(operationalAdj).setNextHopIp(nextHop).setL3vni(l3vni)
                            .setPrimaryRd(primaryRd).setVpnName(vpnName).setDpnId(dpnId)
                            .setEncapType(encapType).setRd(rd).setRouteOrigin(origin);
                    L3vpnRegistry.getRegisteredPopulator(encapType).populateFib(input, writeConfigTxn);
                }
            }
        }

        if (!writeConfigTxnPresent) {
            writeConfigTxn.submit();
        }
    }

    protected void addNewAdjToVpnInterface(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, String primaryRd,
                                           Adjacency adj, BigInteger dpnId, WriteTransaction writeOperTxn,
                                           WriteTransaction writeConfigTxn) {

        com.google.common.base.Optional<VpnInterfaceOpDataEntry> optVpnInterface = VpnUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, identifier);

        if (optVpnInterface.isPresent()) {
            VpnInterfaceOpDataEntry currVpnIntf = optVpnInterface.get();
            String prefix = VpnUtil.getIpPrefix(adj.getIpAddress());
            String vpnName = currVpnIntf.getVpnInstanceName();
            VpnInstanceOpDataEntry vpnInstanceOpData = VpnUtil.getVpnInstanceOpData(dataBroker, primaryRd);
            InstanceIdentifier<AdjacenciesOp> adjPath = identifier.augmentation(AdjacenciesOp.class);
            com.google.common.base.Optional<AdjacenciesOp> optAdjacencies = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, adjPath);
            boolean isL3VpnOverVxLan = VpnUtil.isL3VpnOverVxLan(vpnInstanceOpData.getL3vni());
            VrfEntry.EncapType encapType = VpnUtil.getEncapType(isL3VpnOverVxLan);
            long l3vni = vpnInstanceOpData.getL3vni() == null ? 0L :  vpnInstanceOpData.getL3vni();
            VpnPopulator populator = L3vpnRegistry.getRegisteredPopulator(encapType);
            List<Adjacency> adjacencies;
            if (optAdjacencies.isPresent()) {
                adjacencies = optAdjacencies.get().getAdjacency();
            } else {
                // This code will be hit in case of first PNF adjacency
                adjacencies = new ArrayList<>();
            }
            long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            L3vpnInput input = new L3vpnInput().setNextHop(adj).setVpnName(vpnName)
                    .setInterfaceName(currVpnIntf.getName()).setPrimaryRd(primaryRd).setRd(primaryRd);
            Adjacency operationalAdjacency = null;
            if (adj.getNextHopIpList() != null && !adj.getNextHopIpList().isEmpty()) {
                RouteOrigin origin = adj.getAdjacencyType() == Adjacency.AdjacencyType.PrimaryAdjacency ? RouteOrigin.LOCAL
                        : RouteOrigin.STATIC;
                String nh = adj.getNextHopIpList().get(0);
                String vpnPrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
                synchronized (vpnPrefixKey.intern()) {
                    java.util.Optional<String> rdToAllocate = VpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(
                            dataBroker, vpnId, null, prefix, vpnName, nh, dpnId);
                    if (rdToAllocate.isPresent()) {
                        input.setRd(rdToAllocate.get());
                        operationalAdjacency = populator.createOperationalAdjacency(input);
                        int label = operationalAdjacency.getLabel().intValue();
                        vpnManager.addExtraRoute(vpnName, adj.getIpAddress(), nh, rdToAllocate.get(),
                                currVpnIntf.getVpnInstanceName(), l3vni, origin,
                                currVpnIntf.getName(), operationalAdjacency, encapType, writeConfigTxn);
                        LOG.info("addNewAdjToVpnInterface: Added extra route ip {} nh {} rd {} vpnname {} label {}"
                                        + " Interface {} on dpn {}", adj.getIpAddress(), nh, rdToAllocate.get(),
                                vpnName, label, currVpnIntf.getName(), dpnId);
                    } else {
                        LOG.error("addNewAdjToVpnInterface: No rds to allocate extraroute vpn {} prefix {}", vpnName,
                                prefix);
                        return;
                    }
                    // iRT/eRT use case Will be handled in a new patchset for L3VPN Over VxLAN.
                    // Keeping the MPLS check for now.
                    if (encapType.equals(VrfEntryBase.EncapType.Mplsgre)) {
                        final Adjacency opAdjacency = new AdjacencyBuilder(operationalAdjacency).build();
                        List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                                VpnUtil.getVpnsImportingMyRoute(dataBroker, vpnName);
                        vpnsToImportRoute.forEach(vpn -> {
                            if (vpn.getVrfId() != null) {
                                VpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(
                                        dataBroker, vpn.getVpnId(), vpnId, prefix,
                                        VpnUtil.getVpnName(dataBroker, vpn.getVpnId()), nh, dpnId)
                                        .ifPresent(
                                                rds -> vpnManager.addExtraRoute(
                                                        VpnUtil.getVpnName(dataBroker, vpn.getVpnId()),
                                                        adj.getIpAddress(), nh, rds,
                                                        currVpnIntf.getVpnInstanceName(),
                                                        l3vni, RouteOrigin.SELF_IMPORTED,
                                                        currVpnIntf.getName(), opAdjacency, encapType, writeConfigTxn));
                            }
                        });
                    }
                }
            } else if (adj.isPhysNetworkFunc()) { // PNF adjacency.
                LOG.trace("addNewAdjToVpnInterface: Adding prefix {} to interface {} for vpn {}", prefix,
                        currVpnIntf.getName(), vpnName);

                String parentVpnRd = getParentVpnRdForExternalSubnet(adj);

                writeOperTxn.merge(
                        LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(VpnUtil.getVpnId(dataBroker,
                                adj.getSubnetId().getValue()), prefix),
                        VpnUtil.getPrefixToInterface(BigInteger.ZERO, currVpnIntf.getName(), prefix,
                                adj.getSubnetId(), Prefixes.PrefixCue.PhysNetFunc), true);

                fibManager.addOrUpdateFibEntry(adj.getSubnetId().getValue(), adj.getMacAddress(),
                        adj.getIpAddress(), Collections.emptyList(), null /* EncapType */, 0 /* label */, 0 /*l3vni*/,
                        null /* gw-mac */, parentVpnRd, RouteOrigin.LOCAL, writeConfigTxn);

                input.setRd(adj.getVrfId());
            }
            if (operationalAdjacency == null) {
                operationalAdjacency = populator.createOperationalAdjacency(input);
            }
            adjacencies.add(operationalAdjacency);
            AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(adjacencies);
            VpnInterfaceOpDataEntry newVpnIntf =
                    VpnUtil.getVpnInterfaceOpDataEntry(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(),
                            aug, dpnId, currVpnIntf.isScheduledForRemove(), currVpnIntf.getLportTag(),
                            currVpnIntf.getGatewayMacAddress());

            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, identifier, newVpnIntf, true);
        }
    }
}
