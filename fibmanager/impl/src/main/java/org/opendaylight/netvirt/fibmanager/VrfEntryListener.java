/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionPopMpls;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchMplsLabel;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.RetryingManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.TransactionAdapter;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.NexthopManager.AdjacencyResult;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePathsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState.State;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VrfEntryListener extends AbstractAsyncDataTreeChangeListener<VrfEntry> {

    private static final Logger LOG = LoggerFactory.getLogger(VrfEntryListener.class);
    private static final String FLOWID_PREFIX = "L3.";
    private static final Uint64 COOKIE_VM_FIB_TABLE =  Uint64.valueOf("8000003", 16).intern();
    private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;
    private static final int IPV4_ADDR_PREFIX_LENGTH = 32;
    private static final int LFIB_INTERVPN_PRIORITY = 15;
    public static final Uint64 COOKIE_TUNNEL = Uint64.valueOf("9000000", 16).intern();
    private static final int MAX_RETRIES = 3;
    private static final Uint64 COOKIE_TABLE_MISS = Uint64.valueOf("8000004", 16).intern();

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final RetryingManagedNewTransactionRunner retryingTxRunner;
    private final IMdsalApiManager mdsalManager;
    private final NexthopManager nextHopManager;
    private final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler;
    private final BaseVrfEntryHandler baseVrfEntryHandler;
    private final RouterInterfaceVrfEntryHandler routerInterfaceVrfEntryHandler;
    private final JobCoordinator jobCoordinator;
    private final IElanService elanManager;
    private final FibUtil fibUtil;
    private final InterVpnLinkCache interVpnLinkCache;
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private final UpgradeState upgradeState;
    private final DataTreeEventCallbackRegistrar eventCallbacks;

    @Inject
    public VrfEntryListener(final DataBroker dataBroker, final IMdsalApiManager mdsalApiManager,
                            final NexthopManager nexthopManager,
                            final IElanService elanManager,
                            final BaseVrfEntryHandler vrfEntryHandler,
                            final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler,
                            final RouterInterfaceVrfEntryHandler routerInterfaceVrfEntryHandler,
                            final JobCoordinator jobCoordinator,
                            final FibUtil fibUtil,
                            final InterVpnLinkCache interVpnLinkCache,
                            final UpgradeState upgradeState,
                            final DataTreeEventCallbackRegistrar eventCallbacks) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(FibEntries.class)
                .child(VrfTables.class).child(VrfEntry.class),
                Executors.newListeningSingleThreadExecutor("VrfEntryListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.retryingTxRunner = new RetryingManagedNewTransactionRunner(dataBroker, MAX_RETRIES);
        this.mdsalManager = mdsalApiManager;
        this.nextHopManager = nexthopManager;
        this.elanManager = elanManager;
        this.baseVrfEntryHandler = vrfEntryHandler;
        this.bgpRouteVrfEntryHandler = bgpRouteVrfEntryHandler;
        this.routerInterfaceVrfEntryHandler = routerInterfaceVrfEntryHandler;
        this.jobCoordinator = jobCoordinator;
        this.fibUtil = fibUtil;
        this.interVpnLinkCache = interVpnLinkCache;
        this.upgradeState = upgradeState;
        this.eventCallbacks = eventCallbacks;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() {
        closeables.forEach(c -> {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing {}", c, e);
            }
        });
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void add(final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
        Preconditions.checkNotNull(vrfEntry, "VrfEntry should not be null or empty.");
        String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        LOG.debug("ADD: Adding Fib Entry rd {} prefix {} route-paths {}",
                rd, vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths());
        addFibEntries(identifier, vrfEntry, rd);
        LOG.info("ADD: Added Fib Entry rd {} prefix {} route-paths {}",
                 rd, vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths());
    }

    //This method is temporary. Eventually Factory design pattern will be used to get
    // right VrfEntryhandle and invoke its methods.
    private void addFibEntries(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            bgpRouteVrfEntryHandler.createFlows(identifier, vrfEntry, rd);
            return;
        }
        if (VrfEntry.EncapType.Vxlan.equals(vrfEntry.getEncapType())) {
            LOG.info("EVPN flows need to be programmed.");
            EvpnVrfEntryHandler evpnVrfEntryHandler = new EvpnVrfEntryHandler(dataBroker, this, bgpRouteVrfEntryHandler,
                    nextHopManager, jobCoordinator, fibUtil, upgradeState, eventCallbacks);
            evpnVrfEntryHandler.createFlows(identifier, vrfEntry, rd);
            closeables.add(evpnVrfEntryHandler);
            return;
        }
        RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
        if (routerInt != null) {
            // ping responder for router interfaces
            routerInterfaceVrfEntryHandler.createFlows(vrfEntry, rd);
            return;
        }
        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
            createFibEntries(identifier, vrfEntry);
            return;
        }
    }

    @Override
    public void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry) {
        Preconditions.checkNotNull(vrfEntry, "VrfEntry should not be null or empty.");
        String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        LOG.debug("REMOVE: Removing Fib Entry rd {} prefix {} route-paths {}",
                rd, vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths());
        removeFibEntries(identifier, vrfEntry, rd);
        LOG.info("REMOVE: Removed Fib Entry rd {} prefix {} route-paths {}",
            rd, vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths());
    }

    //This method is temporary. Eventually Factory design pattern will be used to get
    // right VrfEntryhandle and invoke its methods.
    private void removeFibEntries(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        if (VrfEntry.EncapType.Vxlan.equals(vrfEntry.getEncapType())) {
            LOG.info("EVPN flows to be deleted");
            EvpnVrfEntryHandler evpnVrfEntryHandler = new EvpnVrfEntryHandler(dataBroker, this, bgpRouteVrfEntryHandler,
                    nextHopManager, jobCoordinator, fibUtil, upgradeState, eventCallbacks);
            evpnVrfEntryHandler.removeFlows(identifier, vrfEntry, rd);
            closeables.add(evpnVrfEntryHandler);
            return;
        }
        RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
        if (routerInt != null) {
            // ping responder for router interfaces
            routerInterfaceVrfEntryHandler.removeFlows(vrfEntry, rd);
            return;
        }
        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
            deleteFibEntries(identifier, vrfEntry);
            return;
        }
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            bgpRouteVrfEntryHandler.removeFlows(identifier, vrfEntry, rd);
            return;
        }
    }

    @Override
    // "Redundant nullcheck of originalRoutePath, which is known to be non-null" - the null checking for
    // originalRoutePath is a little dicey - safest to keep the checking even if not needed.
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
        Preconditions.checkNotNull(update, "VrfEntry should not be null or empty.");
        final String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        LOG.debug("UPDATE: Updating Fib Entries to rd {} prefix {} route-paths {} origin {} old-origin {}", rd,
                update.getDestPrefix(), update.getRoutePaths(), update.getOrigin(), original.getOrigin());
        // Handle BGP Routes first
        if (RouteOrigin.value(update.getOrigin()) == RouteOrigin.BGP) {
            bgpRouteVrfEntryHandler.updateFlows(identifier, original, update, rd);
            LOG.info("UPDATE: Updated BGP advertised Fib Entry with rd {} prefix {} route-paths {}",
                    rd, update.getDestPrefix(), update.getRoutePaths());
            return;
        }

        if (RouteOrigin.value(update.getOrigin()) == RouteOrigin.STATIC) {
            List<RoutePaths> originalRoutePath = new ArrayList<RoutePaths>(original.nonnullRoutePaths().values());
            List<RoutePaths> updateRoutePath = new ArrayList<RoutePaths>(update.nonnullRoutePaths().values());
            LOG.info("UPDATE: Original route-path {} update route-path {} ", originalRoutePath, updateRoutePath);

            //Updates need to be handled for extraroute even if original vrf entry route path is null or
            //updated vrf entry route path is null. This can happen during tunnel events.
            Optional<VpnInstanceOpDataEntry> optVpnInstance = fibUtil.getVpnInstanceOpData(rd);
            List<String> usedRds = new ArrayList<>();
            if (optVpnInstance.isPresent()) {
                usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker,optVpnInstance.get().getVpnId(),
                        update.getDestPrefix());
            }
            // If original VRF Entry had nexthop null , but update VRF Entry
            // has nexthop , route needs to be created on remote Dpns
            if (originalRoutePath == null || originalRoutePath.isEmpty()
                    && updateRoutePath != null && !updateRoutePath.isEmpty() && usedRds.isEmpty()) {
                // TODO(vivek): Though ugly, Not handling this code now, as each
                // tep add event will invoke flow addition
                LOG.trace("Original VRF entry NH is null for destprefix {}. And the prefix is not an extra route."
                        + " This event is IGNORED here.", update.getDestPrefix());
                return;
            }

            // If original VRF Entry had valid nexthop , but update VRF Entry
            // has nexthop empty'ed out, route needs to be removed from remote Dpns
            if (updateRoutePath == null || updateRoutePath.isEmpty()
                    && originalRoutePath != null && !originalRoutePath.isEmpty() && usedRds.isEmpty()) {
                LOG.trace("Original VRF entry had valid NH for destprefix {}. And the prefix is not an extra route."
                        + "This event is IGNORED here.", update.getDestPrefix());
                return;
            }
            //Update the used rds and vpntoextraroute containers only for the deleted nextHops.
            List<String> nextHopsRemoved = FibHelper.getNextHopListFromRoutePaths(original);
            nextHopsRemoved.removeAll(FibHelper.getNextHopListFromRoutePaths(update));
            List<ListenableFuture<?>> futures = new ArrayList<>();
            ListenableFuture<?> configFuture =
                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, configTx ->
                    futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operTx ->
                        nextHopsRemoved.parallelStream()
                            .forEach(nextHopRemoved -> {
                                try {
                                    fibUtil.updateUsedRdAndVpnToExtraRoute(
                                        configTx, operTx, nextHopRemoved, rd, update.getDestPrefix());
                                } catch (ExecutionException | InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }))));
            futures.add(configFuture);
            Futures.addCallback(configFuture, new FutureCallback<Object>() {
                @Override
                public void onSuccess(Object result) {
                    createFibEntries(identifier, update);
                    LOG.info("UPDATE: Updated static Fib Entry with rd {} prefix {} route-paths {}",
                            rd, update.getDestPrefix(), update.getRoutePaths());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Exception encountered while submitting operational future for update vrfentry {}",
                            update, throwable);
                }
            }, MoreExecutors.directExecutor());
            return;
        }

        //Handle all other routes only on a cluster reboot
        if (original.equals(update)) {
            //Reboot use-case
            createFibEntries(identifier, update);
            LOG.info("UPDATE: Updated Non-static Fib Entry with rd {} prefix {} route-paths {}",
                    rd, update.getDestPrefix(), update.getRoutePaths());
            return;
        }

        LOG.info("UPDATE: Ignoring update for FIB entry with rd {} prefix {} route-origin {} route-paths {}",
                rd, update.getDestPrefix(), update.getOrigin(), update.getRoutePaths());
    }

    private void createFibEntries(final InstanceIdentifier<VrfEntry> vrfEntryIid, final VrfEntry vrfEntry) {
        final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final VpnInstanceOpDataEntry vpnInstance =
                fibUtil.getVpnInstance(vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId()
                + " has null vpnId!");
        final Map<VpnToDpnListKey, VpnToDpnList> keyVpnToDpnListMap;
        if (vrfEntry.getParentVpnRd() != null
                && FibHelper.isControllerManagedNonSelfImportedRoute(RouteOrigin.value(vrfEntry.getOrigin()))) {
            // This block MUST BE HIT only for PNF (Physical Network Function) FIB Entries.
            VpnInstanceOpDataEntry parentVpnInstance = fibUtil.getVpnInstance(vrfEntry.getParentVpnRd());
            keyVpnToDpnListMap = parentVpnInstance != null ? parentVpnInstance.nonnullVpnToDpnList() :
                vpnInstance.getVpnToDpnList();
            LOG.info("createFibEntries: Processing creation of PNF FIB entry with rd {} prefix {}",
                    vrfEntry.getParentVpnRd(), vrfEntry.getDestPrefix());
        } else {
            keyVpnToDpnListMap = vpnInstance.nonnullVpnToDpnList();
        }
        final Uint32 vpnId = vpnInstance.getVpnId();
        final String rd = vrfTableKey.getRouteDistinguisher();
        SubnetRoute subnetRoute = vrfEntry.augmentation(SubnetRoute.class);
        if (subnetRoute != null) {
            final long elanTag = subnetRoute.getElantag().toJava();
            LOG.trace("SUBNETROUTE: createFibEntries: SubnetRoute augmented vrfentry found for rd {} prefix {}"
                    + " with elantag {}", rd, vrfEntry.getDestPrefix(), elanTag);
            if (keyVpnToDpnListMap != null) {
                jobCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()),
                    () -> Collections.singletonList(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                            for (final VpnToDpnList curDpn : keyVpnToDpnListMap.values()) {
                                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                    installSubnetRouteInFib(curDpn.getDpnId(),
                                                                elanTag, rd, vpnId, vrfEntry, tx);
                                    installSubnetBroadcastAddrDropRule(curDpn.getDpnId(), rd,
                                        vpnId, vrfEntry, NwConstants.ADD_FLOW, tx);
                                }
                            }
                        })));
            }
            return;
        }
        // Get etherType value based on the IpPrefix address family type
        int etherType;
        try {
            etherType = NWUtil.getEtherTypeFromIpPrefix(vrfEntry.getDestPrefix());
        } catch (IllegalArgumentException ex) {
            LOG.error("Unable to get etherType for IP Prefix {}", vrfEntry.getDestPrefix());
            return;
        }

        final List<Uint64> localDpnIdList = createLocalFibEntry(vpnInstance.getVpnId(),
                                                                        rd, vrfEntry, etherType);
        if (!localDpnIdList.isEmpty() && keyVpnToDpnListMap != null) {
            jobCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    final ReentrantLock lock = lockFor(vpnInstance);
                    lock.lock();
                    try {
                        for (VpnToDpnList vpnDpn : keyVpnToDpnListMap.values()) {
                            if (!localDpnIdList.contains(vpnDpn.getDpnId())) {
                                if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                    try {
                                        if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin())) {
                                            bgpRouteVrfEntryHandler.createRemoteFibEntry(vpnDpn.getDpnId(),
                                                    vpnId, vrfTableKey.getRouteDistinguisher(), vrfEntry,
                                                    TransactionAdapter.toWriteTransaction(tx), txnObjects);
                                        } else {
                                            createRemoteFibEntry(vpnDpn.getDpnId(),
                                                    vpnInstance.getVpnId(),
                                                    vrfTableKey.getRouteDistinguisher(), vrfEntry, tx);
                                        }
                                    } catch (NullPointerException e) {
                                        LOG.error("Failed to get create remote fib flows for prefix {} ",
                                                vrfEntry.getDestPrefix(), e);
                                    }
                                }
                            }
                        }
                    } finally {
                        lock.unlock();
                    }
                })), MAX_RETRIES);
        }

        Optional<String> optVpnUuid = fibUtil.getVpnNameFromRd(rd);
        if (optVpnUuid.isPresent()) {
            String vpnUuid = optVpnUuid.get();
            InterVpnLinkDataComposite interVpnLink = interVpnLinkCache.getInterVpnLinkByVpnId(vpnUuid).orElse(null);
            if (interVpnLink != null) {
                LOG.debug("InterVpnLink {} found in Cache linking Vpn {}", interVpnLink.getInterVpnLinkName(), vpnUuid);
                FibUtil.getFirstNextHopAddress(vrfEntry).ifPresent(routeNexthop -> {
                    if (interVpnLink.isIpAddrTheOtherVpnEndpoint(routeNexthop, vpnUuid)) {
                        // This is an static route that points to the other endpoint of an InterVpnLink
                        // In that case, we should add another entry in FIB table pointing to LPortDispatcher table.
                        installIVpnLinkSwitchingFlows(interVpnLink, vpnUuid, vrfEntry, vpnId);
                        installInterVpnRouteInLFib(interVpnLink, vpnUuid, vrfEntry, etherType);
                    }
                });
            }
        }
    }

    void refreshFibTables(String rd, String prefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> vrfEntry;
        try {
            vrfEntry = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    vrfEntryId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("refreshFibTables: Exception while reading VrfEntry Ds for the prefix {} rd {}", prefix, rd, e);
            return;
        }
        if (vrfEntry.isPresent()) {
            createFibEntries(vrfEntryId, vrfEntry.get());
        }
    }

    private Prefixes updateVpnReferencesInLri(LabelRouteInfo lri, String vpnInstanceName, boolean isPresentInList) {
        LOG.debug("updating LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        PrefixesBuilder prefixBuilder = new PrefixesBuilder();
        prefixBuilder.setDpnId(lri.getDpnId());
        prefixBuilder.setVpnInterfaceName(lri.getVpnInterfaceName());
        prefixBuilder.setIpAddress(lri.getPrefix());
        // Increment the refCount here
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
            .child(LabelRouteInfo.class, new LabelRouteInfoKey(lri.getLabel())).build();
        LabelRouteInfoBuilder builder = new LabelRouteInfoBuilder(lri);
        if (!isPresentInList) {
            LOG.debug("vpnName {} is not present in LRI with label {}..", vpnInstanceName, lri.getLabel());
            List<String> vpnInstanceNames =
                lri.getVpnInstanceList() != null ? new ArrayList<>(lri.getVpnInstanceList()) : new ArrayList<>();
            vpnInstanceNames.add(vpnInstanceName);
            builder.setVpnInstanceList(vpnInstanceNames);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build());
        } else {
            LOG.debug("vpnName {} is present in LRI with label {}..", vpnInstanceName, lri.getLabel());
        }
        return prefixBuilder.build();
    }

    void installSubnetRouteInFib(final Uint64 dpnId, final long elanTag, final String rd,
            final Uint32 vpnId, final VrfEntry vrfEntry, TypedWriteTransaction<Configuration> tx) {
        if (tx == null) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                newTx -> installSubnetRouteInFib(dpnId, elanTag, rd, vpnId, vrfEntry, newTx)), LOG,
                "Error installing subnet route in FIB");
            return;
        }
        int etherType;
        try {
            etherType = NWUtil.getEtherTypeFromIpPrefix(vrfEntry.getDestPrefix());
        } catch (IllegalArgumentException ex) {
            LOG.error("Unable to get etherType for IP Prefix {}", vrfEntry.getDestPrefix());
            return;
        }
        FibUtil.getLabelFromRoutePaths(vrfEntry).ifPresent(label -> {
            List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
            final LabelRouteInfoKey lriKey = new LabelRouteInfoKey(label);
            final ReentrantLock lock = lockFor(lriKey);
            lock.lock();
            try {
                LabelRouteInfo lri = getLabelRouteInfo(lriKey);
                if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {

                    if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                fibUtil.getVpnInstanceOpData(rd);
                        if (vpnInstanceOpDataEntryOptional.isPresent()) {
                            String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                            if (!lri.getVpnInstanceList().contains(vpnInstanceName)) {
                                updateVpnReferencesInLri(lri, vpnInstanceName, false);
                            }
                        }
                    }
                    LOG.debug("SUBNETROUTE: installSubnetRouteInFib: Fetched labelRouteInfo for label {} interface {}"
                            + " and got dpn {}", label, lri.getVpnInterfaceName(), lri.getDpnId());
                }
            } finally {
                lock.unlock();
            }
        });
        final List<InstructionInfo> instructions = new ArrayList<>();
        Uint64 subnetRouteMeta = Uint64.valueOf(BigInteger.valueOf(elanTag).shiftLeft(24)
            .or(BigInteger.valueOf(vpnId.longValue()).shiftLeft(1)));
        instructions.add(new InstructionWriteMetadata(subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE));
        instructions.add(new InstructionGotoTable(NwConstants.L3_SUBNET_ROUTE_TABLE));
        baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions,
                NwConstants.ADD_FLOW, TransactionAdapter.toWriteTransaction(tx), null);
        if (vrfEntry.getRoutePaths() != null) {
            for (RoutePaths routePath : vrfEntry.getRoutePaths().values()) {
                if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                    List<ActionInfo> actionsInfos = new ArrayList<>();
                    // reinitialize instructions list for LFIB Table
                    final List<InstructionInfo> LFIBinstructions = new ArrayList<>();
                    actionsInfos.add(new ActionPopMpls(etherType));
                    LFIBinstructions.add(new InstructionApplyActions(actionsInfos));
                    LFIBinstructions.add(new InstructionWriteMetadata(subnetRouteMeta,
                            MetaDataUtil.METADATA_MASK_SUBNET_ROUTE));
                    LFIBinstructions.add(new InstructionGotoTable(NwConstants.L3_SUBNET_ROUTE_TABLE));

                    makeLFibTableEntry(dpnId, routePath.getLabel(), LFIBinstructions,
                            DEFAULT_FIB_FLOW_PRIORITY, NwConstants.ADD_FLOW, tx);
                }
            }
        }
    }

    private void installSubnetBroadcastAddrDropRule(final Uint64 dpnId, final String rd, final Uint32 vpnId,
            final VrfEntry vrfEntry, int addOrRemove, TypedWriteTransaction<Configuration> tx) {
        List<MatchInfo> matches = new ArrayList<>();

        LOG.debug("SUBNETROUTE: installSubnetBroadcastAddrDropRule: destPrefix {} rd {} vpnId {} dpnId {}",
                vrfEntry.getDestPrefix(), rd, vpnId, dpnId);
        String[] ipAddress = vrfEntry.getDestPrefix().split("/");
        String subnetBroadcastAddr = FibUtil.getBroadcastAddressFromCidr(vrfEntry.getDestPrefix());
        final int prefixLength = ipAddress.length == 1 ? 0 : Integer.parseInt(ipAddress[1]);

        InetAddress destPrefix;
        try {
            destPrefix = InetAddress.getByName(subnetBroadcastAddr);
        } catch (UnknownHostException e) {
            LOG.error("Failed to get destPrefix for prefix {} rd {} VpnId {} DPN {}",
                    vrfEntry.getDestPrefix(), rd, vpnId, dpnId, e);
            return;
        }

        // Match on VpnId and SubnetBroadCast IP address
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(MatchEthernetType.IPV4);

        if (prefixLength != 0) {
            matches.add(new MatchIpv4Destination(subnetBroadcastAddr, Integer.toString(IPV4_ADDR_PREFIX_LENGTH)));
        }

        //Action is to drop the packet
        List<InstructionInfo> dropInstructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionDrop());
        dropInstructions.add(new InstructionApplyActions(actionsInfos));

        int priority = DEFAULT_FIB_FLOW_PRIORITY + IPV4_ADDR_PREFIX_LENGTH;
        String flowRef = FibUtil.getFlowRef(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, rd, priority, destPrefix);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_SUBNET_ROUTE_TABLE, flowRef, priority,
                flowRef, 0, 0, COOKIE_TABLE_MISS, matches, dropInstructions);

        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        FlowKey flowKey = new FlowKey(new FlowId(flowId));
        Node nodeDpn = FibUtil.buildDpnNode(dpnId);

        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.key()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (addOrRemove == NwConstants.ADD_FLOW) {
            tx.mergeParentStructurePut(flowInstanceId,flow);
        } else {
            tx.delete(flowInstanceId);
        }
    }

    /*
     * For a given route, it installs a flow in LFIB that sets the lportTag of the other endpoint and sends to
     * LportDispatcher table (via table 80)
     */
    private void installInterVpnRouteInLFib(final InterVpnLinkDataComposite interVpnLink, final String vpnName,
                                            final VrfEntry vrfEntry, int etherType) {
        // INTERVPN routes are routes in a Vpn1 that have been leaked to Vpn2. In DC-GW, this Vpn2 route is pointing
        // to a list of DPNs where Vpn2's VpnLink was instantiated. In these DPNs LFIB must be programmed so that the
        // packet is commuted from Vpn2 to Vpn1.
        String interVpnLinkName = interVpnLink.getInterVpnLinkName();
        if (!interVpnLink.isActive()) {
            LOG.warn("InterVpnLink {} is NOT ACTIVE. InterVpnLink flows for prefix={} wont be installed in LFIB",
                     interVpnLinkName, vrfEntry.getDestPrefix());
            return;
        }

        Optional<Uint32> optLportTag = interVpnLink.getEndpointLportTagByVpnName(vpnName);
        if (!optLportTag.isPresent()) {
            LOG.warn("Could not retrieve lportTag for VPN {} endpoint in InterVpnLink {}", vpnName, interVpnLinkName);
            return;
        }

        Long lportTag = optLportTag.get().toJava();
        Uint32 label = FibUtil.getLabelFromRoutePaths(vrfEntry).orElse(null);
        if (label == null) {
            LOG.error("Could not find label in vrfEntry=[prefix={} routePaths={}]. LFIB entry for InterVpnLink skipped",
                      vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths());
            return;
        }
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionPopMpls(etherType));
        List<InstructionInfo> instructions = Arrays.asList(
            new InstructionApplyActions(actionsInfos),
            new InstructionWriteMetadata(MetaDataUtil.getMetaDataForLPortDispatcher(lportTag.intValue(),
                                                            ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                                                  NwConstants.L3VPN_SERVICE_INDEX)),
                                         MetaDataUtil.getMetaDataMaskForLPortDispatcher()),
            new InstructionGotoTable(NwConstants.L3_INTERFACE_TABLE));
        List<String> interVpnNextHopList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
        List<Uint64> targetDpns = interVpnLink.getEndpointDpnsByVpnName(vpnName);

        for (Uint64 dpId : targetDpns) {
            LOG.debug("Installing flow: VrfEntry=[prefix={} label={} nexthop={}] dpn {} for InterVpnLink {} in LFIB",
                      vrfEntry.getDestPrefix(), label, interVpnNextHopList, dpId, interVpnLink.getInterVpnLinkName());

            makeLFibTableEntry(dpId, label, instructions, LFIB_INTERVPN_PRIORITY, NwConstants.ADD_FLOW,
                               /*writeTx*/null);
        }
    }


    /*
     * Installs the flows in FIB table that, for a given route, do the switching from one VPN to the other.
     */
    private void installIVpnLinkSwitchingFlows(final InterVpnLinkDataComposite interVpnLink, final String vpnUuid,
                                               final VrfEntry vrfEntry, Uint32 vpnTag) {
        Preconditions.checkNotNull(interVpnLink, "InterVpnLink cannot be null");
        Preconditions.checkArgument(vrfEntry.getRoutePaths() != null
            && vrfEntry.getRoutePaths().size() == 1);
        String destination = vrfEntry.getDestPrefix();
        String nextHop = new ArrayList<RoutePaths>(vrfEntry.getRoutePaths().values()).get(0).getNexthopAddress();
        String interVpnLinkName = interVpnLink.getInterVpnLinkName();

        // After having received a static route, we should check if the vpn is part of an inter-vpn-link.
        // In that case, we should populate the FIB table of the VPN pointing to LPortDisptacher table
        // using as metadata the LPortTag associated to that vpn in the inter-vpn-link.
        if (interVpnLink.getState().orElse(State.Error) != State.Active) {
            LOG.warn("Route to {} with nexthop={} cannot be installed because the interVpnLink {} is not active",
                destination, nextHop, interVpnLinkName);
            return;
        }

        Optional<Uint32> optOtherEndpointLportTag = interVpnLink.getOtherEndpointLportTagByVpnName(vpnUuid);
        if (!optOtherEndpointLportTag.isPresent()) {
            LOG.warn("Could not find suitable LportTag for the endpoint opposite to vpn {} in interVpnLink {}",
                vpnUuid, interVpnLinkName);
            return;
        }

        List<Uint64> targetDpns = interVpnLink.getEndpointDpnsByVpnName(vpnUuid);
        if (targetDpns.isEmpty()) {
            LOG.warn("Could not find DPNs for endpoint opposite to vpn {} in interVpnLink {}",
                vpnUuid, interVpnLinkName);
            return;
        }

        String[] values = destination.split("/");
        String destPrefixIpAddress = values[0];
        int prefixLength = values.length == 1 ? 0 : Integer.parseInt(values[1]);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnTag.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(MatchEthernetType.IPV4);

        if (prefixLength != 0) {
            matches.add(new MatchIpv4Destination(destPrefixIpAddress, Integer.toString(prefixLength)));
        }

        List<Instruction> instructions =
            Arrays.asList(new InstructionWriteMetadata(
                    MetaDataUtil.getMetaDataForLPortDispatcher(optOtherEndpointLportTag.get().intValue(),
                        ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME, NwConstants
                            .L3VPN_SERVICE_INDEX)),
                    MetaDataUtil.getMetaDataMaskForLPortDispatcher()).buildInstruction(0),
                new InstructionGotoTable(NwConstants.L3_INTERFACE_TABLE).buildInstruction(1));
        Map<InstructionKey, Instruction> instructionsMap = new HashMap<InstructionKey, Instruction>();
        int instructionKey = 0;
        for (Instruction instructionObj : instructions) {
            instructionsMap.put(new InstructionKey(++instructionKey), instructionObj);
        }

        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        String flowRef = getInterVpnFibFlowRef(interVpnLinkName, destination, nextHop);
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef, priority, flowRef, 0, 0,
            COOKIE_VM_FIB_TABLE, matches, instructionsMap);

        LOG.trace("Installing flow in FIB table for vpn {} interVpnLink {} nextHop {} key {}",
            vpnUuid, interVpnLink.getInterVpnLinkName(), nextHop, flowRef);

        for (Uint64 dpId : targetDpns) {

            LOG.debug("Installing flow: VrfEntry=[prefix={} route-paths={}] dpn {} for InterVpnLink {} in FIB",
                vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(),
                dpId, interVpnLink.getInterVpnLinkName());

            mdsalManager.installFlow(dpId, flowEntity);
        }
    }

    private List<Uint64> createLocalFibEntry(Uint32 vpnId, String rd, VrfEntry vrfEntry, int etherType) {
        List<Uint64> returnLocalDpnId = new ArrayList<>();
        String localNextHopIP = vrfEntry.getDestPrefix();
        Prefixes localNextHopInfo = fibUtil.getPrefixToInterface(vpnId, localNextHopIP);
        String vpnName = fibUtil.getVpnNameFromId(vpnId);
        if (localNextHopInfo == null) {
            boolean localNextHopSeen = false;
            List<Routes> vpnExtraRoutes = null;
            //Synchronized to prevent missing bucket action due to race condition between refreshFib and
            // add/updateFib threads on missing nexthop in VpnToExtraroutes
            // FIXME: use an Identifier structure?
            final ReentrantLock lock = JvmGlobalLocks.getLockForString(localNextHopIP + FibConstants.SEPARATOR + rd);
            lock.lock();
            try {
                List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, localNextHopIP);
                vpnExtraRoutes = VpnExtraRouteHelper.getAllVpnExtraRoutes(dataBroker,
                        vpnName, usedRds, localNextHopIP);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Creating Local fib entry with vpnName {} usedRds {} localNextHopIP {} vpnExtraRoutes {}",
                            vpnName, usedRds, localNextHopIP, vpnExtraRoutes);
                }

                //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
                for (Routes vpnExtraRoute : vpnExtraRoutes) {
                    String ipPrefix;
                    if (isIpv4Address(vpnExtraRoute.getNexthopIpList().get(0))) {
                        ipPrefix = vpnExtraRoute.getNexthopIpList().get(0) + NwConstants.IPV4PREFIX;
                    } else {
                        ipPrefix = vpnExtraRoute.getNexthopIpList().get(0) + NwConstants.IPV6PREFIX;
                    }
                    Prefixes localNextHopInfoLocal = fibUtil.getPrefixToInterface(vpnId,
                            ipPrefix);
                    if (localNextHopInfoLocal != null) {
                        localNextHopSeen = true;
                        Uint64 dpnId =
                                checkCreateLocalFibEntry(localNextHopInfoLocal, localNextHopInfoLocal.getIpAddress(),
                                        vpnId, rd, vrfEntry, vpnExtraRoute, vpnExtraRoutes, etherType,
                                        /*parentVpnId*/ null);
                        returnLocalDpnId.add(dpnId);
                    }
                }
            } finally {
                lock.unlock();
            }
            if (!localNextHopSeen && RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                java.util.Optional<Uint32> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                if (optionalLabel.isPresent()) {
                    Uint32 label = optionalLabel.get();
                    List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                    final LabelRouteInfoKey lriKey = new LabelRouteInfoKey(label);
                    final ReentrantLock labelLock = lockFor(lriKey);
                    labelLock.lock();
                    try {
                        LabelRouteInfo lri = getLabelRouteInfo(lriKey);
                        Uint32 parentVpnId = lri.getParentVpnid();
                        if (isPrefixAndNextHopPresentInLri(localNextHopIP, nextHopAddressList, lri)) {
                            Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                    fibUtil.getVpnInstanceOpData(rd);
                            if (vpnInstanceOpDataEntryOptional.isPresent()) {
                                String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                                if (lri.getVpnInstanceList() != null && lri.getVpnInstanceList().contains(
                                       vpnInstanceName)) {
                                    localNextHopInfo = updateVpnReferencesInLri(lri, vpnInstanceName, true);
                                    localNextHopIP = lri.getPrefix();
                                } else {
                                    localNextHopInfo = updateVpnReferencesInLri(lri, vpnInstanceName, false);
                                    localNextHopIP = lri.getPrefix();
                                }
                            }
                            if (localNextHopInfo != null) {
                                LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                                        label, localNextHopInfo.getVpnInterfaceName(), lri.getDpnId());
                                if (vpnExtraRoutes.isEmpty()) {
                                    Uint64 dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP,
                                            vpnId, rd, vrfEntry, null, vpnExtraRoutes, etherType, parentVpnId);
                                    returnLocalDpnId.add(dpnId);
                                } else {
                                    for (Routes extraRoutes : vpnExtraRoutes) {
                                        Uint64 dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP,
                                                vpnId, rd, vrfEntry, extraRoutes, vpnExtraRoutes, etherType,
                                                parentVpnId);
                                        returnLocalDpnId.add(dpnId);
                                    }
                                }
                            }
                        }
                    } finally {
                        labelLock.unlock();
                    }
                }
            }
            if (returnLocalDpnId.isEmpty()) {
                LOG.error("Local DPNID is empty for rd {}, vpnId {}, vrfEntry {}", rd, vpnId, vrfEntry);
            }
        } else {
            Uint64 dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP, vpnId,
                    rd, vrfEntry, /*routes*/ null, /*vpnExtraRoutes*/ null, etherType,
                    /*parentVpnId*/ null);
            if (dpnId != null) {
                returnLocalDpnId.add(dpnId);
            }
        }
        return returnLocalDpnId;
    }

    private Uint64 checkCreateLocalFibEntry(Prefixes localNextHopInfo, String localNextHopIP,
                                                final Uint32 vpnId, final String rd,
                                                final VrfEntry vrfEntry,
                                                @Nullable Routes routes, @Nullable List<Routes> vpnExtraRoutes,
                                                int etherType, Uint32 parentVpnId) {
        String vpnName = fibUtil.getVpnNameFromId(vpnId);
        if (localNextHopInfo != null) {
            long groupId;
            long localGroupId;
            final Uint64 dpnId = localNextHopInfo.getDpnId();
            if (Prefixes.PrefixCue.Nat.equals(localNextHopInfo.getPrefixCue())) {
                LOG.debug("checkCreateLocalFibEntry: NAT Prefix {} with vpnId {} rd {}. Skip local dpn {}"
                        + " FIB processing", vrfEntry.getDestPrefix(), vpnId, rd, dpnId);
                return dpnId;
            }
            if (Prefixes.PrefixCue.PhysNetFunc.equals(localNextHopInfo.getPrefixCue())) {
                LOG.debug("checkCreateLocalFibEntry: PNF Prefix {} with vpnId {} rd {}. Skip local dpn {}"
                        + " FIB processing", vrfEntry.getDestPrefix(), vpnId, rd, dpnId);
                return dpnId;
            }
            if (!isVpnPresentInDpn(rd, dpnId)) {
                LOG.error("checkCreateLocalFibEntry: The VPN with id {} rd {} is not available on dpn {}",
                        vpnId, rd, dpnId.toString());
                return Uint64.ZERO;
            }
            String interfaceName = localNextHopInfo.getVpnInterfaceName();
            String prefix = vrfEntry.getDestPrefix();
            String gwMacAddress = vrfEntry.getGatewayMacAddress();
            //The loadbalancing group is created only if the extra route has multiple nexthops
            //to avoid loadbalancing the discovered routes
            if (RouteOrigin.STATIC.getValue().equals(vrfEntry.getOrigin()) && vpnExtraRoutes != null
                    && routes != null) {
                if (vpnExtraRoutes.size() > 1) {
                    groupId = nextHopManager.createNextHopGroups(vpnId, rd, dpnId, vrfEntry, routes, vpnExtraRoutes);
                    localGroupId = nextHopManager.getLocalSelectGroup(vpnId, vrfEntry.getDestPrefix());
                } else {
                    groupId = nextHopManager.createNextHopGroups(vpnId, rd, dpnId, vrfEntry, routes, vpnExtraRoutes);
                    localGroupId = groupId;
                }
            } else {
                groupId = nextHopManager.createLocalNextHop(vpnId, dpnId, interfaceName, localNextHopIP, prefix,
                        gwMacAddress, parentVpnId);
                localGroupId = groupId;
            }
            if (groupId == FibConstants.INVALID_GROUP_ID) {
                LOG.error("Unable to create Group for local prefix {} on rd {} for vpninterface {} on Node {}",
                        prefix, rd, interfaceName, dpnId.toString());
                return Uint64.ZERO;
            }
            final List<InstructionInfo> instructions = Collections.singletonList(
                    new InstructionApplyActions(
                            Collections.singletonList(new ActionGroup(groupId))));
            final List<InstructionInfo> lfibinstructions = Collections.singletonList(
                    new InstructionApplyActions(
                            Arrays.asList(new ActionPopMpls(etherType), new ActionGroup(localGroupId))));
            java.util.Optional<Uint32> optLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
            List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
            String jobKey = FibUtil.getCreateLocalNextHopJobKey(vpnId, dpnId, vrfEntry.getDestPrefix());
            jobCoordinator.enqueueJob(jobKey,
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions,
                            NwConstants.ADD_FLOW, TransactionAdapter.toWriteTransaction(tx), null);
                    if (FibUtil.isBgpVpn(vpnName, rd)) {
                        optLabel.ifPresent(label -> {
                            if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                                LOG.debug(
                                        "Installing LFIB and tunnel table entry on dpn {} for interface {} with label "
                                                + "{}, rd {}, prefix {}, nexthop {}", dpnId,
                                        localNextHopInfo.getVpnInterfaceName(), optLabel, rd, vrfEntry.getDestPrefix(),
                                        nextHopAddressList);
                                makeLFibTableEntry(dpnId, label, lfibinstructions, DEFAULT_FIB_FLOW_PRIORITY,
                                        NwConstants.ADD_FLOW, tx);
                                makeTunnelTableEntry(dpnId, label, localGroupId, tx);
                            } else {
                                LOG.debug("Route with rd {} prefix {} label {} nexthop {} for vpn {} is an imported "
                                                + "route. LFib and Terminating table entries will not be created.",
                                        rd, vrfEntry.getDestPrefix(), optLabel, nextHopAddressList, vpnId);
                            }
                        });
                    }
                })));
            return dpnId;
        }
        LOG.error("localNextHopInfo received is null for prefix {} on rd {} on vpn {}", vrfEntry.getDestPrefix(), rd,
                vpnName);
        return Uint64.ZERO;
    }

    private boolean isVpnPresentInDpn(String rd, Uint64 dpnId) {
        InstanceIdentifier<VpnToDpnList> id = VpnHelper.getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn;
        try {
            dpnInVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("isVpnPresentInDpn: Exception while reading VpnToDpnList Ds for the rd {} dpnId {}", rd,
                    dpnId, e);
            return false;
        }
        return dpnInVpn.isPresent();
    }

    @Nullable
    private LabelRouteInfo getLabelRouteInfo(Uint32 label) {
        return getLabelRouteInfo(new LabelRouteInfoKey(label));
    }

    @Nullable
    private LabelRouteInfo getLabelRouteInfo(LabelRouteInfoKey label) {
        InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
            .child(LabelRouteInfo.class, label).build();
        Optional<LabelRouteInfo> opResult = null;
        try {
            opResult = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    lriIid);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("refreshFibTables: Exception while reading LabelRouteInfo Ds for the label {}", label, e);
            return null;
        }
        if (opResult.isPresent()) {
            return opResult.get();
        }
        return null;
    }

    private boolean deleteLabelRouteInfo(LabelRouteInfo lri, String vpnInstanceName,
            @Nullable TypedWriteTransaction<Operational> tx) {
        if (lri == null) {
            return true;
        }

        LOG.debug("deleting LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
            .child(LabelRouteInfo.class, new LabelRouteInfoKey(lri.getLabel())).build();

        List<String> vpnInstancesList = lri.getVpnInstanceList() != null
            ? new ArrayList<>(lri.getVpnInstanceList()) : new ArrayList<>();
        if (vpnInstancesList.contains(vpnInstanceName)) {
            LOG.debug("vpninstance {} name is present", vpnInstanceName);
            vpnInstancesList.remove(vpnInstanceName);
        }
        if (vpnInstancesList.isEmpty()) {
            LOG.debug("deleting LRI instance object for label {}", lri.getLabel());
            if (tx != null) {
                tx.delete(lriId);
            } else {
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId);
            }
            return true;
        } else {
            LOG.debug("updating LRI instance object for label {}", lri.getLabel());
            LabelRouteInfoBuilder builder = new LabelRouteInfoBuilder(lri).setVpnInstanceList(vpnInstancesList);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build());
        }
        return false;
    }

    void makeTunnelTableEntry(Uint64 dpId, Uint32 label, long groupId/*String egressInterfaceName*/,
                                      TypedWriteTransaction<Configuration> tx) {
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionGroup(groupId));

        createTerminatingServiceActions(dpId, label, actionsInfos, tx);

        LOG.debug("Terminating service Entry for dpID {} : label : {} egress : {} installed successfully",
            dpId, label, groupId);
    }

    public void createTerminatingServiceActions(Uint64 destDpId, Uint32 label, List<ActionInfo> actionsInfos,
                                                TypedWriteTransaction<Configuration> tx) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.debug("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}",
            destDpId, label, actionsInfos);

        // Matching metadata
        // FIXME vxlan vni bit set is not working properly with OVS.need to revisit
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(label.longValue())));

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionApplyActions(actionsInfos));

        FlowEntity terminatingServiceTableFlowEntity =
            MDSALUtil.buildFlowEntity(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE,
            getTstTableMissFlowRef(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE, label),
                    FibConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY,
                    String.format("%s:%s", "TST Flow Entry ", label), 0, 0,
                    Uint64.valueOf(COOKIE_TUNNEL.longValue() + label.longValue()),
                    mkMatches, mkInstructions);

        FlowKey flowKey = new FlowKey(new FlowId(terminatingServiceTableFlowEntity.getFlowId()));

        FlowBuilder flowbld = terminatingServiceTableFlowEntity.getFlowBuilder();

        Node nodeDpn = FibUtil.buildDpnNode(terminatingServiceTableFlowEntity.getDpnId());
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.key()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(terminatingServiceTableFlowEntity.getTableId()))
            .child(Flow.class, flowKey).build();
        tx.mergeParentStructurePut(flowInstanceId, flowbld.build());
    }

    private void removeTunnelTableEntry(Uint64 dpId, Uint32 label, TypedWriteTransaction<Configuration> tx) {
        FlowEntity flowEntity;
        LOG.debug("remove terminatingServiceActions called with DpnId = {} and label = {}", dpId, label);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(label.longValue())));
        flowEntity = MDSALUtil.buildFlowEntity(dpId,
            NwConstants.INTERNAL_TUNNEL_TABLE,
            getTstTableMissFlowRef(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, label),
                FibConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY,
                String.format("%s:%s", "TST Flow Entry ", label), 0, 0,
                Uint64.valueOf(COOKIE_TUNNEL.longValue() + label.longValue()), mkMatches, null);
        Node nodeDpn = FibUtil.buildDpnNode(flowEntity.getDpnId());
        FlowKey flowKey = new FlowKey(new FlowId(flowEntity.getFlowId()));
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.key()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class, flowKey).build();

        tx.delete(flowInstanceId);
        LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully", dpId, label);
    }

    public List<Uint64> deleteLocalFibEntry(Uint32 vpnId, String rd, VrfEntry vrfEntry) {
        List<Uint64> returnLocalDpnId = new ArrayList<>();
        Prefixes localNextHopInfo = fibUtil.getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
        String vpnName = fibUtil.getVpnNameFromId(vpnId);
        boolean shouldUpdateNonEcmpLocalNextHop = true;
        if (localNextHopInfo == null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
            if (usedRds.size() > 1) {
                LOG.error("The extra route prefix {} is still present in some DPNs in vpn {} on rd {}",
                        vrfEntry.getDestPrefix(), vpnName, rd);
                return returnLocalDpnId;
            }
            String vpnRd = !usedRds.isEmpty() ? usedRds.get(0) : rd;
            //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency
            //in the vpn
            Optional<Routes> extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                    vpnName, vpnRd, vrfEntry.getDestPrefix());
            if (extraRouteOptional.isPresent()) {
                Routes extraRoute = extraRouteOptional.get();
                String ipPrefix;
                if (isIpv4Address(extraRoute.getNexthopIpList().get(0))) {
                    ipPrefix = extraRoute.getNexthopIpList().get(0) + NwConstants.IPV4PREFIX;
                } else {
                    ipPrefix = extraRoute.getNexthopIpList().get(0) + NwConstants.IPV6PREFIX;
                }
                if (extraRoute.getNexthopIpList().size() > 1) {
                    shouldUpdateNonEcmpLocalNextHop = false;
                }
                localNextHopInfo = fibUtil.getPrefixToInterface(vpnId, ipPrefix);
                if (localNextHopInfo != null) {
                    String localNextHopIP = localNextHopInfo.getIpAddress();
                    Uint64 dpnId = checkDeleteLocalFibEntry(localNextHopInfo, localNextHopIP, vpnName, vpnId, rd,
                            vrfEntry, shouldUpdateNonEcmpLocalNextHop);
                    if (!dpnId.equals(Uint64.ZERO)) {
                        LOG.trace("Deleting ECMP group for prefix {}, dpn {}", vrfEntry.getDestPrefix(), dpnId);
                        nextHopManager.deleteLoadBalancingNextHop(vpnId, dpnId, vrfEntry.getDestPrefix());
                        returnLocalDpnId.add(dpnId);
                    }
                } else {
                    LOG.error("localNextHopInfo unavailable while deleting prefix {} with rds {}, primary rd {} in "
                            + "vpn {}", vrfEntry.getDestPrefix(), usedRds, rd, vpnName);
                }
            }

            if (localNextHopInfo == null) {
                /* Imported VRF entry */
                java.util.Optional<Uint32> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                if (optionalLabel.isPresent()) {
                    Uint32 label = optionalLabel.get();
                    List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                    LabelRouteInfo lri = getLabelRouteInfo(label);
                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {
                        PrefixesBuilder prefixBuilder = new PrefixesBuilder();
                        prefixBuilder.setDpnId(lri.getDpnId());
                        Uint64 dpnId = checkDeleteLocalFibEntry(prefixBuilder.build(), nextHopAddressList.get(0),
                                vpnName, vpnId, rd, vrfEntry, shouldUpdateNonEcmpLocalNextHop);
                        if (!dpnId.equals(Uint64.ZERO)) {
                            returnLocalDpnId.add(dpnId);
                        }
                    }
                }
            }

        } else {
            LOG.debug("Obtained prefix to interface for rd {} prefix {}", rd, vrfEntry.getDestPrefix());
            String localNextHopIP = localNextHopInfo.getIpAddress();
            Uint64 dpnId = checkDeleteLocalFibEntry(localNextHopInfo, localNextHopIP, vpnName, vpnId, rd, vrfEntry,
                    shouldUpdateNonEcmpLocalNextHop);
            if (!dpnId.equals(Uint64.ZERO)) {
                returnLocalDpnId.add(dpnId);
            }
        }

        return returnLocalDpnId;
    }

    private Uint64 checkDeleteLocalFibEntry(Prefixes localNextHopInfo, final String localNextHopIP,
            final String vpnName, final Uint32 vpnId, final String rd, final VrfEntry vrfEntry,
            boolean shouldUpdateNonEcmpLocalNextHop) {
        if (localNextHopInfo != null) {
            final Uint64 dpnId = localNextHopInfo.getDpnId();
            if (Prefixes.PrefixCue.Nat.equals(localNextHopInfo.getPrefixCue())) {
                LOG.debug("checkDeleteLocalFibEntry: NAT Prefix {} with vpnId {} rd {}. Skip local dpn {}"
                        + " FIB processing", vrfEntry.getDestPrefix(), vpnId, rd, dpnId);
                return dpnId;
            }
            if (Prefixes.PrefixCue.PhysNetFunc.equals(localNextHopInfo.getPrefixCue())) {
                LOG.debug("checkDeleteLocalFibEntry: PNF Prefix {} with vpnId {} rd {}. Skip local dpn {}"
                        + " FIB processing", vrfEntry.getDestPrefix(), vpnId, rd, dpnId);
                return dpnId;
            }

            jobCoordinator.enqueueJob(FibUtil.getCreateLocalNextHopJobKey(vpnId, dpnId, vrfEntry.getDestPrefix()),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null,
                            NwConstants.DEL_FLOW, TransactionAdapter.toWriteTransaction(tx), null);
                    if (FibUtil.isBgpVpn(vpnName, rd)) {
                        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                            FibUtil.getLabelFromRoutePaths(vrfEntry).ifPresent(label -> {
                                makeLFibTableEntry(dpnId, label, null /* instructions */, DEFAULT_FIB_FLOW_PRIORITY,
                                        NwConstants.DEL_FLOW, tx);
                                removeTunnelTableEntry(dpnId, label, tx);
                            });
                        }
                    }
                })));
            //TODO: verify below adjacency call need to be optimized (?)
            //In case of the removal of the extra route, the loadbalancing group is updated
            if (shouldUpdateNonEcmpLocalNextHop) {
                baseVrfEntryHandler.deleteLocalAdjacency(dpnId, vpnId, localNextHopIP, vrfEntry.getDestPrefix());
            }
            return dpnId;
        }
        return Uint64.ZERO;
    }

    private void createRemoteFibEntry(final Uint64 remoteDpnId, final Uint32 vpnId, String rd,
            final VrfEntry vrfEntry, TypedWriteTransaction<Configuration> tx) {
        if (tx == null) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                newTx -> createRemoteFibEntry(remoteDpnId, vpnId, rd, vrfEntry, newTx)), LOG,
                "Error creating remote FIB entry");
            return;
        }

        String vpnName = fibUtil.getVpnNameFromId(vpnId);
        LOG.debug("createremotefibentry: adding route {} for rd {} on remoteDpnId {}", vrfEntry.getDestPrefix(), rd,
                remoteDpnId);

        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.STATIC) {
            programRemoteFibEntry(remoteDpnId, vpnId, rd, vrfEntry, tx);
            return;
        }
        // Handling static VRF entries
        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
        List<Routes> vpnExtraRoutes =
                VpnExtraRouteHelper.getAllVpnExtraRoutes(dataBroker, vpnName, usedRds, vrfEntry.getDestPrefix());
        if (!vpnExtraRoutes.isEmpty()) {
            programRemoteFibWithLoadBalancingGroups(remoteDpnId, vpnId, rd, vrfEntry, vpnExtraRoutes);
        } else {
            // Program in case of other static VRF entries like floating IPs
            programRemoteFibEntry(remoteDpnId, vpnId, rd, vrfEntry, tx);
        }
    }

    // Allow deprecated TransactionRunner calls for now
    @SuppressWarnings("ForbidCertainMethod")
    private void programRemoteFibWithLoadBalancingGroups(final Uint64 remoteDpnId, final Uint32 vpnId, String rd,
            final VrfEntry vrfEntry, List<Routes> vpnExtraRoutes) {
        // create loadbalancing groups for extra routes only when the extra route is
        // present behind multiple VMs
        // Obtain the local routes for this particular dpn.
        java.util.Optional<Routes> routes = vpnExtraRoutes.stream().filter(route -> {
            Prefixes prefixToInterface =
                    fibUtil.getPrefixToInterface(vpnId, FibUtil.getIpPrefix(route.getNexthopIpList().get(0)));
            if (prefixToInterface == null) {
                return false;
            }
            return remoteDpnId.equals(prefixToInterface.getDpnId());
        }).findFirst();
        long groupId = nextHopManager.createNextHopGroups(vpnId, rd, remoteDpnId, vrfEntry,
                routes.isPresent() ? routes.get() : null, vpnExtraRoutes);
        if (groupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to create Group for local prefix {} on rd {} on Node {}", vrfEntry.getDestPrefix(), rd,
                    remoteDpnId);
            return;
        }
        List<ActionInfo> actionInfos = Collections.singletonList(new ActionGroup(groupId));
        List<InstructionInfo> instructions = Lists.newArrayList(new InstructionApplyActions(actionInfos));
        String jobKey = FibUtil.getCreateRemoteNextHopJobKey(vpnId, remoteDpnId, vrfEntry.getDestPrefix());
        jobCoordinator.enqueueJob(jobKey,
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(Datastore.CONFIGURATION,
                txn -> {
                    baseVrfEntryHandler.makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions,
                            NwConstants.ADD_FLOW, TransactionAdapter.toWriteTransaction(txn), null);
                })));

        LOG.debug("Successfully added FIB entry for prefix {} in vpnId {}", vrfEntry.getDestPrefix(), vpnId);
    }

    private void programRemoteFibEntry(final Uint64 remoteDpnId, final Uint32 vpnId, String rd,
            final VrfEntry vrfEntry, TypedWriteTransaction<Configuration> tx) {
        List<AdjacencyResult> adjacencyResults = baseVrfEntryHandler.resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);
        if (adjacencyResults.isEmpty()) {
            LOG.error("Could not get interface for route-paths: {} in vpn {} on DPN {}", vrfEntry.getRoutePaths(), rd,
                    remoteDpnId);
            LOG.error("Failed to add Route: {} in vpn: {}", vrfEntry.getDestPrefix(), rd);
            return;
        }
        baseVrfEntryHandler.programRemoteFib(remoteDpnId, vpnId, vrfEntry, TransactionAdapter.toWriteTransaction(tx),
                rd, adjacencyResults, null);
        LOG.debug("Successfully programmed FIB entry for prefix {} in vpnId {}", vrfEntry.getDestPrefix(), vpnId);
    }

    protected void cleanUpOpDataForFib(Uint32 vpnId, String primaryRd, final VrfEntry vrfEntry) {
    /* Get interface info from prefix to interface mapping;
        Use the interface info to get the corresponding vpn interface op DS entry,
        remove the adjacency corresponding to this fib entry.
        If adjacency removed is the last adjacency, clean up the following:
         - vpn interface from dpntovpn list, dpn if last vpn interface on dpn
         - prefix to interface entry
         - vpn interface op DS
     */
        LOG.debug("Cleanup of prefix {} in VPN {}", vrfEntry.getDestPrefix(), vpnId);
        Prefixes prefixInfo = fibUtil.getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
        if (prefixInfo == null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
            String usedRd = usedRds.isEmpty() ? primaryRd : usedRds.get(0);
            Routes extraRoute = baseVrfEntryHandler.getVpnToExtraroute(vpnId, usedRd, vrfEntry.getDestPrefix());
            if (extraRoute != null && extraRoute.getNexthopIpList() != null) {
                for (String nextHopIp : extraRoute.getNexthopIpList()) {
                    LOG.debug("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                    if (nextHopIp != null) {
                        String ipPrefix;
                        if (isIpv4Address(nextHopIp)) {
                            ipPrefix = nextHopIp + NwConstants.IPV4PREFIX;
                        } else {
                            ipPrefix = nextHopIp + NwConstants.IPV6PREFIX;
                        }
                        prefixInfo = fibUtil.getPrefixToInterface(vpnId, ipPrefix);
                        checkCleanUpOpDataForFib(prefixInfo, vpnId, primaryRd, vrfEntry, extraRoute);
                    }
                }
            }
            if (prefixInfo == null) {
                java.util.Optional<Uint32> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                if (optionalLabel.isPresent()) {
                    Uint32 label = optionalLabel.get();
                    List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                    LabelRouteInfo lri = getLabelRouteInfo(label);
                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {
                        PrefixesBuilder prefixBuilder = new PrefixesBuilder();
                        prefixBuilder.setDpnId(lri.getDpnId());
                        prefixBuilder.setVpnInterfaceName(lri.getVpnInterfaceName());
                        prefixBuilder.setIpAddress(lri.getPrefix());
                        prefixInfo = prefixBuilder.build();
                        LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                                label, prefixInfo.getVpnInterfaceName(), lri.getDpnId());
                        checkCleanUpOpDataForFib(prefixInfo, vpnId, primaryRd, vrfEntry, extraRoute);
                    }
                }
            }
        } else {
            checkCleanUpOpDataForFib(prefixInfo, vpnId, primaryRd, vrfEntry, null /*Routes*/);
        }
    }

    private void checkCleanUpOpDataForFib(final Prefixes prefixInfo, final Uint32 vpnId, final String rd,
                                          final VrfEntry vrfEntry, @Nullable final Routes extraRoute) {

        if (prefixInfo == null) {
            LOG.error("Cleanup VPN Data Failed as unable to find prefix Info for prefix {} VpnId {} rd {}",
                    vrfEntry.getDestPrefix(), vpnId, rd);
            return; //Don't have any info for this prefix (shouldn't happen); need to return
        }

        if (Prefixes.PrefixCue.Nat.equals(prefixInfo.getPrefixCue())) {
            LOG.debug("NAT Prefix {} with vpnId {} rd {}. Skip FIB processing",
                    vrfEntry.getDestPrefix(), vpnId, rd);
            return;
        }

        String ifName = prefixInfo.getVpnInterfaceName();
        jobCoordinator.enqueueJob("VPNINTERFACE-" + ifName,
            new CleanupVpnInterfaceWorker(prefixInfo, vpnId, rd, vrfEntry, extraRoute));
    }

    private class CleanupVpnInterfaceWorker implements Callable<List<? extends ListenableFuture<?>>> {
        Prefixes prefixInfo;
        Uint32 vpnId;
        String rd;
        VrfEntry vrfEntry;
        Routes extraRoute;

        CleanupVpnInterfaceWorker(final Prefixes prefixInfo, final Uint32 vpnId, final String rd,
                                         final VrfEntry vrfEntry, final Routes extraRoute) {
            this.prefixInfo = prefixInfo;
            this.vpnId = vpnId;
            this.rd = rd;
            this.vrfEntry = vrfEntry;
            this.extraRoute = extraRoute;
        }

        @Override
        public List<? extends ListenableFuture<?>> call() {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
                //First Cleanup LabelRouteInfo
                //TODO(KIRAN) : Move the below block when addressing iRT/eRT for L3VPN Over VxLan
                LOG.debug("cleanupVpnInterfaceWorker: rd {} prefix {}", rd, prefixInfo.getIpAddress());
                if (VrfEntry.EncapType.Mplsgre.equals(vrfEntry.getEncapType())) {
                    FibUtil.getLabelFromRoutePaths(vrfEntry).ifPresent(label -> {
                        List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                        final LabelRouteInfoKey lriKey = new LabelRouteInfoKey(label);
                        final ReentrantLock lock = lockFor(lriKey);
                        lock.lock();
                        try {
                            LabelRouteInfo lri = getLabelRouteInfo(lriKey);
                            if (lri != null && Objects.equals(lri.getPrefix(), vrfEntry.getDestPrefix())
                                    && nextHopAddressList.contains(lri.getNextHopIpList().get(0))) {
                                Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                        fibUtil.getVpnInstanceOpData(rd);
                                String vpnInstanceName = "";
                                if (vpnInstanceOpDataEntryOptional.isPresent()) {
                                    vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                                }
                                boolean lriRemoved = deleteLabelRouteInfo(lri, vpnInstanceName, tx);
                                if (lriRemoved) {
                                    String parentRd = lri.getParentVpnRd();
                                    fibUtil.releaseId(FibConstants.VPN_IDPOOL_NAME, FibUtil.getNextHopLabelKey(
                                            parentRd, vrfEntry.getDestPrefix()));
                                }
                            } else {
                                fibUtil.releaseId(FibConstants.VPN_IDPOOL_NAME, FibUtil.getNextHopLabelKey(
                                        rd, vrfEntry.getDestPrefix()));
                            }
                        } finally {
                            lock.unlock();
                        }
                    });
                }
                String ifName = prefixInfo.getVpnInterfaceName();
                Optional<String> optVpnName = fibUtil.getVpnNameFromRd(rd);
                String vpnName = null;

                if (Prefixes.PrefixCue.PhysNetFunc.equals(prefixInfo.getPrefixCue())) {
                    // Get vpnId for rd = networkId since op vpnInterface will be pointing to rd = networkId
                    Optional<String> vpnNameOpt = fibUtil.getVpnNameFromRd(vrfEntry.getParentVpnRd());
                    if (vpnNameOpt.isPresent()) {
                        vpnId = fibUtil.getVpnId(vpnNameOpt.get());
                    }
                }
                if (optVpnName.isPresent()) {
                    vpnName = optVpnName.get();
                    Optional<VpnInterfaceOpDataEntry> opVpnInterface = tx
                            .read(FibUtil.getVpnInterfaceOpDataEntryIdentifier(ifName, vpnName)).get();
                    if (opVpnInterface.isPresent()) {
                        Uint32 associatedVpnId = fibUtil.getVpnId(vpnName);
                        if (!Objects.equals(vpnId, associatedVpnId)) {
                            LOG.warn("Prefixes {} are associated with different vpn instance with id {} rather than {}",
                                    vrfEntry.getDestPrefix(), associatedVpnId, vpnId);
                            LOG.warn("Not proceeding with Cleanup op data for prefix {}", vrfEntry.getDestPrefix());
                            return;
                        } else {
                            LOG.debug("Processing cleanup of prefix {} associated with vpn {}",
                                    vrfEntry.getDestPrefix(), associatedVpnId);
                        }
                    }
                }
                if (extraRoute != null) {
                    List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
                    //Only one used Rd present in case of removal event
                    String usedRd = usedRds.get(0);
                    if (optVpnName.isPresent()) {
                        tx.delete(BaseVrfEntryHandler.getVpnToExtrarouteIdentifier(vpnName, usedRd,
                                        vrfEntry.getDestPrefix()));
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, configTx ->
                            configTx.delete(VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, vrfEntry.getDestPrefix())));
                    }
                }
                handleAdjacencyAndVpnOpInterfaceDeletion(vrfEntry, ifName, vpnName, tx);
            }));
        }
    }

    /**
     * Check all the adjacency in VpnInterfaceOpData and decide whether to delete the entire interface or only adj.
     * Remove Adjacency from VPNInterfaceOpData.
     * if Adjacency != primary.
     * if Adjacency == primary , then mark it for deletion.
     * Remove entire VPNinterfaceOpData Entry.
     * if sie of Adjacency <= 2 and all are marked for deletion , delete the entire VPNinterface Op entry.
     * @param vrfEntry - VrfEntry removed
     * @param ifName - Interface name from VRFentry
     * @param vpnName - VPN name of corresponding VRF
     * @param tx - ReadWrite Tx
     */
    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void handleAdjacencyAndVpnOpInterfaceDeletion(VrfEntry vrfEntry, String ifName, String vpnName,
                                                          TypedReadWriteTransaction<Operational> tx)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<Adjacency> adjacencyIid =
                FibUtil.getAdjacencyIdentifierOp(ifName, vpnName, vrfEntry.getDestPrefix());
        Optional<Adjacency> adjacencyOptional = tx.read(adjacencyIid).get();
        if (adjacencyOptional.isPresent()) {
            if (adjacencyOptional.get().getAdjacencyType() != Adjacency.AdjacencyType.PrimaryAdjacency) {
                tx.delete(FibUtil.getAdjacencyIdentifierOp(ifName, vpnName, vrfEntry.getDestPrefix()));
            } else {
                tx.merge(adjacencyIid,
                        new AdjacencyBuilder(adjacencyOptional.get()).setMarkedForDeletion(true).build());
            }
        }

        Optional<AdjacenciesOp> optAdjacencies = tx.read(FibUtil.getAdjListPathOp(ifName, vpnName)).get();

        if (!optAdjacencies.isPresent() || optAdjacencies.get().getAdjacency() == null) {
            return;
        }

        @NonNull List<Adjacency> adjacencies
                = new ArrayList<Adjacency>(optAdjacencies.get().nonnullAdjacency().values());
        if (adjacencies.size() <= 2
                && adjacencies.stream().allMatch(adjacency ->
                adjacency.getAdjacencyType() == Adjacency.AdjacencyType.PrimaryAdjacency
                        && adjacency.isMarkedForDeletion() != null
                        && adjacency.isMarkedForDeletion()
        )) {
            LOG.info("Clean up vpn interface {} to vpn {} list.", ifName, vpnName);
            tx.delete(FibUtil.getVpnInterfaceOpDataEntryIdentifier(ifName, vpnName));
        }
    }

    private void deleteFibEntries(final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final String rd = vrfTableKey.getRouteDistinguisher();
        final VpnInstanceOpDataEntry vpnInstance = fibUtil.getVpnInstance(vrfTableKey.getRouteDistinguisher());
        if (vpnInstance == null) {
            LOG.error("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        final Map<VpnToDpnListKey, VpnToDpnList> keyVpnToDpnListMap;
        if (vrfEntry.getParentVpnRd() != null
                && FibHelper.isControllerManagedNonSelfImportedRoute(RouteOrigin.value(vrfEntry.getOrigin()))) {
            // This block MUST BE HIT only for PNF (Physical Network Function) FIB Entries.
            VpnInstanceOpDataEntry parentVpnInstance = fibUtil.getVpnInstance(vrfEntry.getParentVpnRd());
            keyVpnToDpnListMap = parentVpnInstance != null ? parentVpnInstance.getVpnToDpnList() :
                    vpnInstance.getVpnToDpnList();
            LOG.info("deleteFibEntries: Processing deletion of PNF FIB entry with rd {} prefix {}",
                    vrfEntry.getParentVpnRd(), vrfEntry.getDestPrefix());
        } else {
            keyVpnToDpnListMap = vpnInstance.getVpnToDpnList();
        }

        SubnetRoute subnetRoute = vrfEntry.augmentation(SubnetRoute.class);
        final java.util.Optional<Uint32> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
        List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
        String vpnName = fibUtil.getVpnNameFromId(vpnInstance.getVpnId());
        if (subnetRoute != null) {
            long elanTag = subnetRoute.getElantag().toJava();
            LOG.trace("SUBNETROUTE: deleteFibEntries: SubnetRoute augmented vrfentry found for rd {} prefix {}"
                    + " with elantag {}", rd, vrfEntry.getDestPrefix(), elanTag);
            if (keyVpnToDpnListMap != null) {
                jobCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()),
                    () -> Collections.singletonList(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                            for (final VpnToDpnList curDpn : keyVpnToDpnListMap.values()) {

                                baseVrfEntryHandler.makeConnectedRoute(curDpn.getDpnId(),
                                    vpnInstance.getVpnId(),
                                    vrfEntry, vrfTableKey.getRouteDistinguisher(), null,
                                        NwConstants.DEL_FLOW, TransactionAdapter.toWriteTransaction(tx), null);
                                if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                                    optionalLabel.ifPresent(label -> makeLFibTableEntry(curDpn.getDpnId(),
                                        label, null, DEFAULT_FIB_FLOW_PRIORITY, NwConstants.DEL_FLOW, tx));
                                }

                                installSubnetBroadcastAddrDropRule(curDpn.getDpnId(), rd,
                                    vpnInstance.getVpnId(),
                                    vrfEntry, NwConstants.DEL_FLOW, tx);
                            }
                        })));
            }
            optionalLabel.ifPresent(label -> {
                final LabelRouteInfoKey lriKey = new LabelRouteInfoKey(label);
                final ReentrantLock lock = lockFor(lriKey);
                lock.lock();
                try {
                    LabelRouteInfo lri = getLabelRouteInfo(lriKey);
                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {
                        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                fibUtil.getVpnInstanceOpData(rd);
                        String vpnInstanceName = "";
                        if (vpnInstanceOpDataEntryOptional.isPresent()) {
                            vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                        }
                        boolean lriRemoved = this.deleteLabelRouteInfo(lri, vpnInstanceName, null);
                        if (lriRemoved) {
                            String parentRd = lri.getParentVpnRd();
                            fibUtil.releaseId(FibConstants.VPN_IDPOOL_NAME, FibUtil.getNextHopLabelKey(
                                    parentRd, vrfEntry.getDestPrefix()));
                            LOG.trace("SUBNETROUTE: deleteFibEntries: Released subnetroute label {} for rd {} prefix {}"
                                    + " as labelRouteInfo cleared", label, rd, vrfEntry.getDestPrefix());
                        }
                    } else {
                        fibUtil.releaseId(FibConstants.VPN_IDPOOL_NAME, FibUtil.getNextHopLabelKey(
                                rd, vrfEntry.getDestPrefix()));
                        LOG.trace("SUBNETROUTE: deleteFibEntries: Released subnetroute label {} for rd {} prefix {}",
                                label, rd, vrfEntry.getDestPrefix());
                    }
                } finally {
                    lock.unlock();
                }
            });
            return;
        }

        final List<Uint64> localDpnIdList = deleteLocalFibEntry(vpnInstance.getVpnId(),
            vrfTableKey.getRouteDistinguisher(), vrfEntry);
        if (keyVpnToDpnListMap != null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker,
                    vpnInstance.getVpnId(), vrfEntry.getDestPrefix());
            String jobKey;
            Optional<Routes> extraRouteOptional;
            //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
            if (usedRds != null && !usedRds.isEmpty()) {
                if (usedRds.size() > 1) {
                    LOG.error("The extra route prefix is still present in some DPNs");
                    return ;
                } else {
                    // The first rd is retrieved from usedrds as Only 1 rd would be present as extra route prefix
                    //is not present in any other DPN
                    extraRouteOptional = VpnExtraRouteHelper
                            .getVpnExtraroutes(dataBroker, vpnName, usedRds.get(0), vrfEntry.getDestPrefix());
                }
            } else {
                extraRouteOptional = Optional.empty();
            }

            jobCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    if (localDpnIdList.size() <= 0) {
                        for (VpnToDpnList curDpn : keyVpnToDpnListMap.values()) {
                            baseVrfEntryHandler.deleteRemoteRoute(Uint64.ZERO, curDpn.getDpnId(),
                                vpnInstance.getVpnId(), vrfTableKey, vrfEntry, extraRouteOptional,
                                    TransactionAdapter.toWriteTransaction(tx));
                        }
                    } else {
                        for (Uint64 localDpnId : localDpnIdList) {
                            for (VpnToDpnList curDpn : keyVpnToDpnListMap.values()) {
                                if (!Objects.equals(curDpn.getDpnId(), localDpnId)) {
                                    baseVrfEntryHandler.deleteRemoteRoute(localDpnId, curDpn.getDpnId(),
                                        vpnInstance.getVpnId(), vrfTableKey, vrfEntry, extraRouteOptional,
                                            TransactionAdapter.toWriteTransaction(tx));
                                }
                            }
                        }
                    }
                    if (extraRouteOptional.isPresent()) {
                        //Remove select groups only for extra-routes
                        nextHopManager.removeNextHopPointer(nextHopManager
                                .getRemoteSelectGroupKey(vpnInstance.getVpnId(), vrfEntry.getDestPrefix()));
                        nextHopManager.removeNextHopPointer(nextHopManager
                                .getLocalSelectGroupKey(vpnInstance.getVpnId(), vrfEntry.getDestPrefix()));
                    }
                })), MAX_RETRIES);
        }

        //The flow/group entry has been deleted from config DS; need to clean up associated operational
        //DS entries in VPN Op DS, VpnInstanceOpData and PrefixToInterface to complete deletion
        cleanUpOpDataForFib(vpnInstance.getVpnId(), vrfTableKey.getRouteDistinguisher(), vrfEntry);

        // Remove all fib entries configured due to interVpnLink, when nexthop is the opposite endPoint
        // of the interVpnLink.
        Optional<String> optVpnUuid = fibUtil.getVpnNameFromRd(rd);
        if (optVpnUuid.isPresent()) {
            String vpnUuid = optVpnUuid.get();
            FibUtil.getFirstNextHopAddress(vrfEntry).ifPresent(routeNexthop -> {
                Optional<InterVpnLinkDataComposite> optInterVpnLink = interVpnLinkCache.getInterVpnLinkByVpnId(vpnUuid);
                if (optInterVpnLink.isPresent()) {
                    InterVpnLinkDataComposite interVpnLink = optInterVpnLink.get();
                    if (interVpnLink.isIpAddrTheOtherVpnEndpoint(routeNexthop, vpnUuid)) {
                        // This is route that points to the other endpoint of an InterVpnLink
                        // In that case, we should look for the FIB table pointing to
                        // LPortDispatcher table and remove it.
                        removeInterVPNLinkRouteFlows(interVpnLink, vpnUuid, vrfEntry);
                    }
                }
            });
        }

    }

    private void makeLFibTableEntry(Uint64 dpId, Uint32 label, @Nullable List<InstructionInfo> instructions,
                                    int priority, int addOrRemove, TypedWriteTransaction<Configuration> tx) {
        if (tx == null) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                newTx -> makeLFibTableEntry(dpId, label, instructions, priority, addOrRemove, newTx)), LOG,
                "Error making LFIB table entry");
            return;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(label.longValue()));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = FibUtil.getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, label, priority);

        FlowEntity flowEntity;
        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_LFIB_TABLE, flowRef, priority, flowRef, 0, 0,
            NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructions);
        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        FlowKey flowKey = new FlowKey(new FlowId(flowId));
        Node nodeDpn = FibUtil.buildDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.key()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (addOrRemove == NwConstants.ADD_FLOW) {
            tx.mergeParentStructurePut(flowInstanceId, flow);
        } else {
            tx.delete(flowInstanceId);
        }

        LOG.debug("LFIB Entry for dpID {} : label : {} instructions {} : key {} {} successfully",
            dpId, label, instructions, flowKey, NwConstants.ADD_FLOW == addOrRemove ? "ADDED" : "REMOVED");
    }

    public void populateFibOnNewDpn(final Uint64 dpnId, final Uint32 vpnId, final String rd,
                                    final FutureCallback<List<?>> callback) {
        LOG.trace("New dpn {} for vpn {} : populateFibOnNewDpn", dpnId, rd);
        jobCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
            () -> {
                InstanceIdentifier<VrfTables> id = buildVrfId(rd);
                final VpnInstanceOpDataEntry vpnInstance = fibUtil.getVpnInstance(rd);
                final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
                List<ListenableFuture<?>> futures = new ArrayList<>();
                if (!vrfTable.isPresent()) {
                    LOG.info("populateFibOnNewDpn: dpn: {}: VRF Table not yet available for RD {}", dpnId, rd);
                    if (callback != null) {
                        ListenableFuture<List<Object>> listenableFuture = Futures.allAsList(futures);
                        Futures.addCallback(listenableFuture, callback, MoreExecutors.directExecutor());
                    }
                    return futures;
                }

                final ReentrantLock lock = lockFor(vpnInstance);
                lock.lock();
                try {
                    futures.add(retryingTxRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                        for (final VrfEntry vrfEntry : vrfTable.get().nonnullVrfEntry().values()) {
                            SubnetRoute subnetRoute = vrfEntry.augmentation(SubnetRoute.class);
                            if (subnetRoute != null) {
                                long elanTag = subnetRoute.getElantag().toJava();
                                installSubnetRouteInFib(dpnId, elanTag, rd, vpnId, vrfEntry, tx);
                                installSubnetBroadcastAddrDropRule(dpnId, rd, vpnId, vrfEntry, NwConstants.ADD_FLOW,
                                        tx);
                                continue;
                            }
                            RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
                            if (routerInt != null) {
                                LOG.trace("Router augmented vrfentry found rd:{}, uuid:{}, ip:{}, mac:{}",
                                        rd, routerInt.getUuid(), routerInt.getIpAddress(), routerInt.getMacAddress());
                                routerInterfaceVrfEntryHandler.installRouterFibEntry(vrfEntry, dpnId, vpnId,
                                        routerInt.getIpAddress(), new MacAddress(routerInt.getMacAddress()),
                                        NwConstants.ADD_FLOW);
                                continue;
                            }
                            //Handle local flow creation for imports
                            if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                                java.util.Optional<Uint32> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                                if (optionalLabel.isPresent()) {
                                    List<String> nextHopList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                                    LabelRouteInfo lri = getLabelRouteInfo(optionalLabel.get());
                                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopList, lri)) {
                                        if (Objects.equals(lri.getDpnId(), dpnId)) {
                                            try {
                                                int etherType = NWUtil.getEtherTypeFromIpPrefix(
                                                        vrfEntry.getDestPrefix());
                                                createLocalFibEntry(vpnId, rd, vrfEntry, etherType);
                                            } catch (IllegalArgumentException ex) {
                                                LOG.warn("Unable to get etherType for IP Prefix {}",
                                                        vrfEntry.getDestPrefix());
                                            }
                                            continue;
                                        }
                                    }
                                }
                            }
                            boolean shouldCreateRemoteFibEntry = shouldCreateFibEntryForVrfAndVpnIdOnDpn(vpnId,
                                    vrfEntry, dpnId);
                            if (shouldCreateRemoteFibEntry) {
                                LOG.trace("Will create remote FIB entry for vrfEntry {} on DPN {}", vrfEntry, dpnId);
                                if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin())) {
                                    List<SubTransaction> txnObjects =  new ArrayList<>();
                                    bgpRouteVrfEntryHandler.createRemoteFibEntry(dpnId, vpnId,
                                            vrfTable.get().getRouteDistinguisher(), vrfEntry,
                                            TransactionAdapter.toWriteTransaction(tx), txnObjects);
                                } else {
                                    createRemoteFibEntry(dpnId, vpnId, vrfTable.get().getRouteDistinguisher(),
                                            vrfEntry, tx);
                                }
                            }
                        }
                    }));
                    if (callback != null) {
                        ListenableFuture<List<Object>> listenableFuture = Futures.allAsList(futures);
                        Futures.addCallback(listenableFuture, callback, MoreExecutors.directExecutor());
                    }
                } finally {
                    lock.unlock();
                }
                return futures;
            });
    }

    public void populateExternalRoutesOnDpn(final Uint64 dpnId, final Uint32 vpnId, final String rd,
                                            final String localNextHopIp, final String remoteNextHopIp) {
        LOG.trace("populateExternalRoutesOnDpn : dpn {}, vpn {}, rd {}, localNexthopIp {} , remoteNextHopIp {} ",
            dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
        InstanceIdentifier<VrfTables> id = buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = fibUtil.getVpnInstance(rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable;
        try {
            vrfTable = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("populateExternalRoutesOnDpn: Exception while reading the VrfTable for the rd {}", rd, e);
            return;
        }
        if (vrfTable.isPresent()) {
            jobCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    final ReentrantLock lock = lockFor(vpnInstance);
                    lock.lock();
                    try {
                        vrfTable.get().nonnullVrfEntry().values().stream()
                            .filter(vrfEntry -> RouteOrigin.BGP == RouteOrigin.value(vrfEntry.getOrigin()))
                            .forEach(bgpRouteVrfEntryHandler.getConsumerForCreatingRemoteFib(dpnId, vpnId,
                                rd, remoteNextHopIp, vrfTable, TransactionAdapter.toWriteTransaction(tx), txnObjects));
                    } finally {
                        lock.unlock();
                    }
                })));
        }
    }

    public void manageRemoteRouteOnDPN(final boolean action,
                                       final Uint64 localDpnId,
                                       final Uint32 vpnId,
                                       final String rd,
                                       final String destPrefix,
                                       final String destTepIp,
                                       final Uint32 label) {
        final VpnInstanceOpDataEntry vpnInstance = fibUtil.getVpnInstance(rd);

        if (vpnInstance == null) {
            LOG.error("VpnInstance for rd {} not present for prefix {}", rd, destPrefix);
            return;
        }

        jobCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, localDpnId),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                final ReentrantLock lock = lockFor(vpnInstance);
                lock.lock();
                try {
                    VrfTablesKey vrfTablesKey = new VrfTablesKey(rd);
                    VrfEntry vrfEntry = getVrfEntry(dataBroker, rd, destPrefix);
                    if (vrfEntry == null) {
                        return;
                    }
                    LOG.trace("manageRemoteRouteOnDPN :: action {}, DpnId {}, vpnId {}, rd {}, destPfx {}",
                            action, localDpnId, vpnId, rd, destPrefix);
                    Map<RoutePathsKey, RoutePaths> keyRoutePathsMap = vrfEntry.getRoutePaths();
                    VrfEntry modVrfEntry;
                    if (keyRoutePathsMap == null || keyRoutePathsMap.isEmpty()) {
                        modVrfEntry = FibHelper.getVrfEntryBuilder(vrfEntry, label,
                                Collections.singletonList(destTepIp),
                                RouteOrigin.value(vrfEntry.getOrigin()), null /* parentVpnRd */).build();
                    } else {
                        modVrfEntry = vrfEntry;
                    }

                    if (action) {
                        LOG.trace("manageRemoteRouteOnDPN updated(add)  vrfEntry :: {}", modVrfEntry);
                        createRemoteFibEntry(localDpnId, vpnId, vrfTablesKey.getRouteDistinguisher(),
                                modVrfEntry, tx);
                    } else {
                        LOG.trace("manageRemoteRouteOnDPN updated(remove)  vrfEntry :: {}", modVrfEntry);
                        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker,
                                vpnInstance.getVpnId(), vrfEntry.getDestPrefix());
                        if (usedRds.size() > 1) {
                            LOG.debug("The extra route prefix is still present in some DPNs");
                            return;
                        }
                        //Is this fib route an extra route? If yes, get the nexthop which would be
                        //an adjacency in the vpn
                        Optional<Routes> extraRouteOptional = Optional.empty();
                        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.STATIC && usedRds.size() != 0) {
                            extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                                    fibUtil.getVpnNameFromId(vpnInstance.getVpnId()),
                                    usedRds.get(0), vrfEntry.getDestPrefix());
                        }
                        baseVrfEntryHandler.deleteRemoteRoute(null, localDpnId, vpnId, vrfTablesKey, modVrfEntry,
                                extraRouteOptional, TransactionAdapter.toWriteTransaction(tx));
                    }
                } finally {
                    lock.unlock();
                }
            })));
    }

    public void cleanUpDpnForVpn(final Uint64 dpnId, final Uint32 vpnId, final String rd,
                                 final FutureCallback<List<?>> callback) {
        LOG.trace("cleanUpDpnForVpn: Remove dpn {} for vpn {} : cleanUpDpnForVpn", dpnId, rd);
        jobCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
            () -> {
                InstanceIdentifier<VrfTables> id = buildVrfId(rd);
                final VpnInstanceOpDataEntry vpnInstance = fibUtil.getVpnInstance(rd);
                List<SubTransaction> txnObjects = new ArrayList<>();
                final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
                List<ListenableFuture<?>> futures = new ArrayList<>();
                if (!vrfTable.isPresent()) {
                    LOG.error("cleanUpDpnForVpn: VRF Table not available for RD {}", rd);
                    if (callback != null) {
                        ListenableFuture<List<Object>> listenableFuture = Futures.allAsList(futures);
                        Futures.addCallback(listenableFuture, callback, MoreExecutors.directExecutor());
                    }
                    return futures;
                }
                final ReentrantLock lock = lockFor(vpnInstance);
                lock.lock();
                try {
                    futures.add(retryingTxRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                        String vpnName = fibUtil.getVpnNameFromId(vpnInstance.getVpnId());
                        for (final VrfEntry vrfEntry : vrfTable.get().nonnullVrfEntry().values()) {
                            /* parentRd is only filled for external PNF cases where the interface on the external
                             * network VPN are used to cleanup the flows. For all other cases, use "rd" for
                             * #fibUtil.isInterfacePresentInDpn().
                             * */
                            String parentRd = vrfEntry.getParentVpnRd() != null ? vrfEntry.getParentVpnRd()
                                    : rd;
                            /* Handle subnet routes here */
                            SubnetRoute subnetRoute = vrfEntry.augmentation(SubnetRoute.class);
                            if (subnetRoute != null && !fibUtil
                                    .isInterfacePresentInDpn(parentRd, dpnId)) {
                                LOG.trace("SUBNETROUTE: cleanUpDpnForVpn: Cleaning subnetroute {} on dpn {}"
                                        + " for vpn {}", vrfEntry.getDestPrefix(), dpnId, rd);
                                baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null,
                                        NwConstants.DEL_FLOW, TransactionAdapter.toWriteTransaction(tx), null);
                                Map<RoutePathsKey, RoutePaths> keyRoutePathsMap = vrfEntry.getRoutePaths();
                                if (keyRoutePathsMap != null) {
                                    for (RoutePaths routePath : keyRoutePathsMap.values()) {
                                        makeLFibTableEntry(dpnId, routePath.getLabel(), null,
                                                DEFAULT_FIB_FLOW_PRIORITY,
                                                NwConstants.DEL_FLOW, tx);
                                        LOG.trace("SUBNETROUTE: cleanUpDpnForVpn: Released subnetroute label {}"
                                                        + " for rd {} prefix {}", routePath.getLabel(), rd,
                                                vrfEntry.getDestPrefix());
                                    }
                                }
                                installSubnetBroadcastAddrDropRule(dpnId, rd, vpnId, vrfEntry,
                                        NwConstants.DEL_FLOW, tx);
                                continue;
                            }
                            // ping responder for router interfaces
                            RouterInterface routerInt = vrfEntry.augmentation(RouterInterface.class);
                            if (routerInt != null) {
                                LOG.trace("Router augmented vrfentry found for rd:{}, uuid:{}, ip:{}, mac:{}",
                                        rd, routerInt.getUuid(), routerInt.getIpAddress(),
                                        routerInt.getMacAddress());
                                routerInterfaceVrfEntryHandler.installRouterFibEntry(vrfEntry, dpnId, vpnId,
                                        routerInt.getIpAddress(), new MacAddress(routerInt.getMacAddress()),
                                        NwConstants.DEL_FLOW);
                                continue;
                            }
                            //Handle local flow deletion for imports
                            if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                                java.util.Optional<Uint32> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                                if (optionalLabel.isPresent()) {
                                    List<String> nextHopList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                                    LabelRouteInfo lri = getLabelRouteInfo(optionalLabel.get());
                                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopList,
                                            lri) && Objects.equals(lri.getDpnId(), dpnId)) {
                                        deleteLocalFibEntry(vpnId, rd, vrfEntry);
                                    }
                                }
                            }
                            // Passing null as we don't know the dpn
                            // to which prefix is attached at this point
                            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker,
                                    vpnInstance.getVpnId(), vrfEntry.getDestPrefix());
                            Optional<Routes> extraRouteOptional;
                            //Is this fib route an extra route? If yes, get the nexthop which would be
                            //an adjacency in the vpn
                            if (usedRds != null && !usedRds.isEmpty()) {
                                if (usedRds.size() > 1) {
                                    LOG.error("The extra route prefix is still present in some DPNs");
                                    return;
                                } else {
                                    extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker, vpnName,
                                            usedRds.get(0), vrfEntry.getDestPrefix());

                                }
                            } else {
                                extraRouteOptional = Optional.empty();
                            }
                            if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin())) {
                                bgpRouteVrfEntryHandler.deleteRemoteRoute(null, dpnId, vpnId,
                                        vrfTable.get().key(), vrfEntry, extraRouteOptional,
                                        TransactionAdapter.toWriteTransaction(tx), txnObjects);
                            } else {
                                if (subnetRoute == null || !fibUtil
                                        .isInterfacePresentInDpn(parentRd, dpnId)) {
                                    baseVrfEntryHandler.deleteRemoteRoute(null, dpnId, vpnId,
                                            vrfTable.get().key(), vrfEntry, extraRouteOptional,
                                            TransactionAdapter.toWriteTransaction(tx));
                                }
                            }
                        }
                    }));
                } finally {
                    lock.unlock();
                }
                if (callback != null) {
                    ListenableFuture<List<Object>> listenableFuture = Futures.allAsList(futures);
                    Futures.addCallback(listenableFuture, callback, MoreExecutors.directExecutor());
                }
                return futures;
            });
    }

    public void cleanUpExternalRoutesOnDpn(final Uint64 dpnId, final Uint32 vpnId, final String rd,
                                           final String localNextHopIp, final String remoteNextHopIp) {
        LOG.trace("cleanUpExternalRoutesOnDpn : cleanup remote routes on dpn {} for vpn {}, rd {}, "
                + " localNexthopIp {} , remoteNexhtHopIp {}",
            dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
        InstanceIdentifier<VrfTables> id = buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = fibUtil.getVpnInstance(rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable;
        try {
            vrfTable = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getVrfEntry: Exception while reading VrfTable for the rd {} vpnId {}", rd, vpnId, e);
            return;
        }
        if (vrfTable.isPresent()) {
            jobCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> {
                    final ReentrantLock lock = lockFor(vpnInstance);
                    lock.lock();
                    try {
                        return Collections.singletonList(
                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                                tx -> vrfTable.get().nonnullVrfEntry().values().stream()
                                    .filter(vrfEntry -> RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP)
                                    .forEach(bgpRouteVrfEntryHandler.getConsumerForDeletingRemoteFib(dpnId, vpnId,
                                        remoteNextHopIp, vrfTable, TransactionAdapter.toWriteTransaction(tx),
                                            txnObjects))));
                    } finally {
                        lock.unlock();
                    }
                });
        }
    }

    public static InstanceIdentifier<VrfTables> buildVrfId(String rd) {
        InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        return idBuilder.build();
    }

    private String getInterVpnFibFlowRef(String interVpnLinkName, String prefix, String nextHop) {
        return FLOWID_PREFIX + interVpnLinkName + NwConstants.FLOWID_SEPARATOR + prefix + NwConstants
                .FLOWID_SEPARATOR + nextHop;
    }

    private String getTstTableMissFlowRef(Uint64 dpnId, short tableId, Uint32 tableMiss) {
        return FLOWID_PREFIX + FibConstants.TST_FLOW_ID_SUFFIX + dpnId + NwConstants.FLOWID_SEPARATOR
                + tableId + NwConstants.FLOWID_SEPARATOR + tableMiss + FLOWID_PREFIX;
    }

    @Nullable
    private VrfEntry getVrfEntry(DataBroker broker, String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = InstanceIdentifier.builder(FibEntries.class)
            .child(VrfTables.class, new VrfTablesKey(rd))
            .child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        Optional<VrfEntry> vrfEntry;
        try {
            vrfEntry = SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.CONFIGURATION,
                    vrfEntryId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getVrfEntry: Exception while reading VrfEntry for the prefix {} rd {}", ipPrefix, rd, e);
            return null;
        }
        if (vrfEntry.isPresent()) {
            return vrfEntry.get();
        }
        return null;
    }

    public void removeInterVPNLinkRouteFlows(final InterVpnLinkDataComposite interVpnLink,
                                             final String vpnName,
                                             final VrfEntry vrfEntry) {
        Preconditions.checkArgument(vrfEntry.getRoutePaths() != null && vrfEntry.getRoutePaths().size() == 1);

        String interVpnLinkName = interVpnLink.getInterVpnLinkName();
        List<Uint64> targetDpns = interVpnLink.getEndpointDpnsByVpnName(vpnName);

        if (targetDpns.isEmpty()) {
            LOG.warn("Could not find DPNs for VPN {} in InterVpnLink {}", vpnName, interVpnLinkName);
            return;
        }

        java.util.Optional<String> optNextHop = FibUtil.getFirstNextHopAddress(vrfEntry);
        java.util.Optional<Uint32> optLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);

        // delete from FIB
        //
        optNextHop.ifPresent(nextHop -> {
            String flowRef = getInterVpnFibFlowRef(interVpnLinkName, vrfEntry.getDestPrefix(), nextHop);
            FlowKey flowKey = new FlowKey(new FlowId(flowRef));
            Flow flow = new FlowBuilder().withKey(flowKey).setId(new FlowId(flowRef))
                    .setTableId(NwConstants.L3_FIB_TABLE).setFlowName(flowRef).build();

            LOG.trace("Removing flow in FIB table for interVpnLink {} key {}", interVpnLinkName, flowRef);
            for (Uint64 dpId : targetDpns) {
                LOG.debug("Removing flow: VrfEntry=[prefix={} nexthop={}] dpn {} for InterVpnLink {} in FIB",
                          vrfEntry.getDestPrefix(), nextHop, dpId, interVpnLinkName);

                mdsalManager.removeFlow(dpId, flow);
            }
        });

        // delete from LFIB
        //
        optLabel.ifPresent(label -> {
            LOG.trace("Removing flow in FIB table for interVpnLink {}", interVpnLinkName);

            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                for (Uint64 dpId : targetDpns) {
                    LOG.debug("Removing flow: VrfEntry=[prefix={} label={}] dpn {} for InterVpnLink {} in LFIB",
                            vrfEntry.getDestPrefix(), label, dpId, interVpnLinkName);
                    makeLFibTableEntry(dpId, label, /*instructions*/null, LFIB_INTERVPN_PRIORITY,
                            NwConstants.DEL_FLOW, tx);
                }
            }), LOG, "Error removing flows");
        });
    }

    private static boolean isPrefixAndNextHopPresentInLri(String prefix,
            List<String> nextHopAddressList, LabelRouteInfo lri) {
        return lri != null && Objects.equals(lri.getPrefix(), prefix)
                && nextHopAddressList.contains(lri.getNextHopIpList().get(0));
    }

    private boolean shouldCreateFibEntryForVrfAndVpnIdOnDpn(Uint32 vpnId, VrfEntry vrfEntry, Uint64 dpnId) {
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            return true;
        }

        Prefixes prefix = fibUtil.getPrefixToInterface(vpnId, vrfEntry.getDestPrefix());
        if (prefix != null) {
            Uint64 prefixDpnId = prefix.getDpnId();
            if (dpnId.equals(prefixDpnId)) {
                LOG.trace("Should not create remote FIB entry for vrfEntry {} on DPN {}",
                        vrfEntry, dpnId);
                return false;
            }
        }
        return true;
    }

    private static ReentrantLock lockFor(final VpnInstanceOpDataEntry vpnInstance) {
        // FIXME: use vpnInstance.key() instead?
        return JvmGlobalLocks.getLockForString(vpnInstance.getVpnInstanceName());
    }

    private static ReentrantLock lockFor(LabelRouteInfoKey label) {
        return JvmGlobalLocks.getLockFor(label);
    }
}
