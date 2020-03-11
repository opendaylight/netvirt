/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static java.util.Collections.emptyList;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.cache.InstanceIdDataObjectCache;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.caches.CacheProvider;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.metrics.Counter;
import org.opendaylight.infrautils.metrics.Labeled;
import org.opendaylight.infrautils.metrics.MetricDescriptor;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.arp.responder.ArpResponderHandler;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.intfc.VpnPopulator;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.LabelRouteMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.NeutronRouterDpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.RouterDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn.list.DpnVpninterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes.NetworkType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnInterfaceManager extends AbstractAsyncDataTreeChangeListener<VpnInterface> {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceManager.class);
    private static final short DJC_MAX_RETRIES = 3;

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final OdlInterfaceRpcService ifaceMgrRpcService;
    private final VpnFootprintService vpnFootprintService;
    private final IInterfaceManager interfaceManager;
    private final IVpnManager vpnManager;
    private final ArpResponderHandler arpResponderHandler;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;

    private final ConcurrentHashMap<String, Runnable> vpnIntfMap = new ConcurrentHashMap<>();

    private final Map<String, ConcurrentLinkedQueue<UnprocessedVpnInterfaceData>> unprocessedVpnInterfaces =
            new ConcurrentHashMap<>();

    private final InstanceIdDataObjectCache<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryCache;
    private static final String FIB_EVENT_SOURCE_TUNNEL_STATE = "tunnelEvent";
    protected static final String FIB_EVENT_SOURCE_CONFIG_VPN_INTERFACE = "configVpnIntfEvent";
    private final MetricProvider metricProvider;
    private final Labeled<Labeled<Labeled<Counter>>> vpnIfCounter;
    private Counter counter;

    @Inject
    public VpnInterfaceManager(final DataBroker dataBroker,
                               final IBgpManager bgpManager,
                               final IdManagerService idManager,
                               final IMdsalApiManager mdsalManager,
                               final IFibManager fibManager,
                               final OdlInterfaceRpcService ifaceMgrRpcService,
                               final VpnFootprintService vpnFootprintService,
                               final IInterfaceManager interfaceManager,
                               final IVpnManager vpnManager,
                               final ArpResponderHandler arpResponderHandler,
                               final JobCoordinator jobCoordinator,
                               final CacheProvider cacheProvider,
                               final VpnUtil vpnUtil,
                               final MetricProvider metricProvider) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class),
                Executors.newListeningSingleThreadExecutor("VpnInterfaceManager", LOG));

        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.mdsalManager = mdsalManager;
        this.fibManager = fibManager;
        this.ifaceMgrRpcService = ifaceMgrRpcService;
        this.vpnFootprintService = vpnFootprintService;
        this.interfaceManager = interfaceManager;
        this.vpnManager = vpnManager;
        this.arpResponderHandler = arpResponderHandler;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
        this.metricProvider = metricProvider;
        vpnIfCounter = metricProvider.newCounter(MetricDescriptor.builder().anchor(this)
                .project("netvirt").module("vpnmanager")
                .id("vpnifmanager").build(), "entitytype", "action","name");

        vpnInstanceOpDataEntryCache = new InstanceIdDataObjectCache<>(VpnInstanceOpDataEntry.class, dataBroker,
                LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.builder(
                        VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class).build(), cacheProvider);
        start();
    }

    public Runnable isNotifyTaskQueued(String intfName) {
        return vpnIntfMap.remove(intfName);
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        vpnInstanceOpDataEntryCache.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void add(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface) {
        LOG.trace("Received VpnInterface add event: vpnInterface={}", vpnInterface);
        LOG.info("add: intfName {} onto vpnName {}", vpnInterface.getName(),
                VpnHelper.getVpnInterfaceVpnInstanceNamesString(
                        new ArrayList<VpnInstanceNames>(vpnInterface.nonnullVpnInstanceNames().values())));
        addVpnInterface(identifier, vpnInterface, null, null);
    }

    private boolean canHandleNewVpnInterface(final InstanceIdentifier<VpnInterface> identifier,
                          final VpnInterface vpnInterface, String vpnName) {
        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnName);
        lock.lock();
        try {
            if (isVpnInstanceReady(vpnName)) {
                return true;
            }
            addToUnprocessedVpnInterfaces(identifier, vpnInterface, vpnName);
            return false;
        } finally {
            lock.unlock();
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void addVpnInterface(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface,
                             final @Nullable List<Adjacency> oldAdjs, final @Nullable List<Adjacency> newAdjs) {
        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnInterface.nonnullVpnInstanceNames().values()) {
            String vpnName = vpnInterfaceVpnInstance.getVpnName();
            addVpnInterfaceCall(identifier, vpnInterface, oldAdjs, newAdjs, vpnName);
        }
    }

    private void addVpnInterfaceCall(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface vpnInterface,
                         final List<Adjacency> oldAdjs, final List<Adjacency> newAdjs, String vpnName) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class);
        final String interfaceName = key.getName();

        if (!canHandleNewVpnInterface(identifier, vpnInterface, vpnName)) {
            LOG.error("add: VpnInstance {} for vpnInterface {} not ready, holding on ",
                  vpnName, vpnInterface.getName());
            return;
        }
        InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier = VpnUtil
             .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        List<Adjacency> copyOldAdjs = null;
        if (oldAdjs != null) {
            copyOldAdjs = new ArrayList<>();
            copyOldAdjs.addAll(oldAdjs);
        }
        List<Adjacency> copyNewAdjs = null;
        if (newAdjs != null) {
            copyNewAdjs = new ArrayList<>();
            copyNewAdjs.addAll(newAdjs);
        }
        addVpnInterfaceToVpn(vpnInterfaceOpIdentifier, vpnInterface, copyOldAdjs, copyNewAdjs, identifier, vpnName);
    }

    private void addVpnInterfaceToVpn(final InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier,
                    final VpnInterface vpnInterface, final @Nullable List<Adjacency> oldAdjs,
                    final @Nullable List<Adjacency> newAdjs,
                    final InstanceIdentifier<VpnInterface> identifier, String vpnName) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class);
        final String interfaceName = key.getName();
        String primaryRd = vpnUtil.getPrimaryRd(vpnName);
        counter = vpnIfCounter.label("vpninterface.add").label("rd.interface")
                .label(primaryRd + "." + interfaceName);
        counter.increment();
        if (!vpnUtil.isVpnPendingDelete(primaryRd)) {
            Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
            boolean isBgpVpnInternetVpn = vpnUtil.isBgpVpnInternet(vpnName);
            if (interfaceState != null) {
                try {
                    final Uint64 dpnId = InterfaceUtils.getDpIdFromInterface(interfaceState);
                    final int ifIndex = interfaceState.getIfIndex();
                    jobCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName, () -> {
                        // TODO Deal with sequencing — the config tx must only submitted if the oper tx goes in
                        // (the inventory tx goes in last)
                        List<ListenableFuture<?>> futures = new ArrayList<>();
                        List<String> ipAddressList = new ArrayList<String>();
                        //set of prefix used, as entry in prefix-to-interface datastore
                        // is prerequisite for refresh Fib to avoid race condition leading to
                        // missing remote next hop in bucket actions on bgp-vpn delete
                        Set<String> prefixListForRefreshFib = new HashSet<>();
                        ListenableFuture<?> confFuture =
                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                                confTx -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                                    operTx -> futures.add(
                                        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, invTx -> {
                                            LOG.info(
                                                "addVpnInterface: VPN Interface add event - intfName {} vpnName {}"
                                                    + " on dpn {}",
                                                vpnInterface.getName(), vpnName, vpnInterface.getDpnId());
                                            processVpnInterfaceUp(dpnId, vpnInterface, primaryRd, ifIndex, false,
                                                confTx, operTx, invTx, interfaceState, vpnName,
                                                prefixListForRefreshFib, FIB_EVENT_SOURCE_CONFIG_VPN_INTERFACE,
                                                    ipAddressList);
                                            if (oldAdjs != null && !oldAdjs.equals(newAdjs)) {
                                                LOG.info("addVpnInterface: Adjacency changed upon VPNInterface {}"
                                                    + " Update for swapping VPN {} case.", interfaceName, vpnName);
                                                if (newAdjs != null) {
                                                    for (Adjacency adj : newAdjs) {
                                                        if (oldAdjs.contains(adj)) {
                                                            oldAdjs.remove(adj);
                                                        } else {
                                                            if (!isBgpVpnInternetVpn
                                                                || vpnUtil.isAdjacencyEligibleToVpnInternet(adj)) {
                                                                addNewAdjToVpnInterface(vpnInterfaceOpIdentifier,
                                                                    primaryRd, adj, dpnId, operTx, confTx, invTx,
                                                                    prefixListForRefreshFib);
                                                            }
                                                        }
                                                    }
                                                }
                                                for (Adjacency adj : oldAdjs) {
                                                    if (!isBgpVpnInternetVpn
                                                        || vpnUtil.isAdjacencyEligibleToVpnInternet(adj)) {
                                                        delAdjFromVpnInterface(vpnInterfaceOpIdentifier, adj, dpnId,
                                                            operTx, confTx);
                                                    }
                                                }
                                            }
                                        })))));
                        Futures.addCallback(confFuture,
                            new VpnInterfaceCallBackHandler(primaryRd, prefixListForRefreshFib),
                            MoreExecutors.directExecutor());
                        futures.add(confFuture);
                        Futures.addCallback(confFuture, new PostVpnInterfaceWorker(interfaceName, ipAddressList,
                                        true, "Config"),
                            MoreExecutors.directExecutor());
                        LOG.info("addVpnInterface: Addition of interface {} in VPN {} on dpn {}"
                            + " processed successfully", interfaceName, vpnName, dpnId);
                        return futures;
                    });
                } catch (NumberFormatException | IllegalStateException e) {
                    LOG.error("addVpnInterface: Unable to retrieve dpnId from interface operational data store for "
                            + "interface {}. Interface addition on vpn {} failed", interfaceName,
                        vpnName, e);
                    return;
                }
            } else if (Boolean.TRUE.equals(vpnInterface.isRouterInterface())) {
                jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getName(),
                    () -> {
                        ListenableFuture<?> future =
                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, confTx -> {
                                createFibEntryForRouterInterface(primaryRd, vpnInterface, interfaceName,
                                    confTx, vpnName);
                                LOG.info("addVpnInterface: Router interface {} for vpn {} on dpn {}", interfaceName,
                                    vpnName, vpnInterface.getDpnId());
                            });
                        LoggingFutures.addErrorLogging(future, LOG,
                            "Error creating FIB entry for interface {} on VPN {}", vpnInterface.getName(), vpnName);
                        return Collections.singletonList(future);
                    });
            } else {
                LOG.info("addVpnInterface: Handling addition of VPN interface {} on vpn {} skipped as interfaceState"
                    + " is not available", interfaceName, vpnName);
            }
        } else {
            LOG.error("addVpnInterface: Handling addition of VPN interface {} on vpn {} dpn {} with rd {} skipped"
                    + " as vpn is pending delete", interfaceName, vpnName, vpnInterface.getDpnId(), primaryRd);
        }
    }

    // "Unconditional wait" and "Wait not in loop" wrt the VpnNotifyTask below - suppressing the FB violation -
    // see comments below.
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    protected void processVpnInterfaceUp(final Uint64 dpId, VpnInterface vpnInterface, final String primaryRd,
            final int lportTag, boolean isInterfaceUp,
            TypedWriteTransaction<Configuration> writeConfigTxn,
            TypedWriteTransaction<Operational> writeOperTxn,
            TypedReadWriteTransaction<Configuration> writeInvTxn,
            Interface interfaceState, final String vpnName,
            Set<String> prefixListForRefreshFib, String eventSource ,
                                         List<String> ipAddressList) throws ExecutionException, InterruptedException {
        final String interfaceName = vpnInterface.getName();
        Optional<VpnInterfaceOpDataEntry> optOpVpnInterface = vpnUtil.getVpnInterfaceOpDataEntry(interfaceName,
                vpnName);
        VpnInterfaceOpDataEntry opVpnInterface = optOpVpnInterface.isPresent() ? optOpVpnInterface.get() : null;
        boolean isBgpVpnInternetVpn = vpnUtil.isBgpVpnInternet(vpnName);
        if (!isInterfaceUp) {
            LOG.info("processVpnInterfaceUp: Binding vpn service to interface {} onto dpn {} for vpn {}",
                     interfaceName, dpId, vpnName);
            Uint32 vpnId = vpnUtil.getVpnId(vpnName);
            if (VpnConstants.INVALID_ID.equals(vpnId)) {
                LOG.warn("processVpnInterfaceUp: VpnInstance to VPNId mapping not available for VpnName {}"
                        + " processing vpninterface {} on dpn {}, bailing out now.", vpnName, interfaceName,
                        dpId);
                return;
            }

            boolean waitForVpnInterfaceOpRemoval = false;
            if (opVpnInterface != null) {
                String opVpnName = opVpnInterface.getVpnInstanceName();
                String primaryInterfaceIp = null;
                if (Objects.equals(opVpnName, vpnName)) {
                    // Please check if the primary VRF Entry does not exist for VPNInterface
                    // If so, we have to process ADD, as this might be a DPN Restart with Remove and Add triggered
                    // back to back
                    // However, if the primary VRF Entry for this VPNInterface exists, please continue bailing out !
                    List<Adjacency> adjs = vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(interfaceName);
                    if (adjs == null) {
                        LOG.error("processVpnInterfaceUp: VPN Interface {} on dpn {} for vpn {} failed as adjacencies"
                                + " for this vpn interface could not be obtained", interfaceName, dpId,
                                vpnName);
                        return;
                    }
                    for (Adjacency adj : adjs) {
                        if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                            primaryInterfaceIp = adj.getIpAddress();
                            break;
                        }
                    }
                    if (primaryInterfaceIp == null) {
                        LOG.error("processVpnInterfaceUp: VPN Interface {} addition on dpn {} for vpn {} failed"
                                + " as primary adjacency for this vpn interface could not be obtained", interfaceName,
                                dpId, vpnName);
                        return;
                    }
                    // Get the rd of the vpn instance
                    VrfEntry vrf = vpnUtil.getVrfEntry(primaryRd, primaryInterfaceIp);
                    if (vrf != null) {
                        LOG.error("processVpnInterfaceUp: VPN Interface {} on dpn {} for vpn {} already provisioned ,"
                                + " bailing out from here.", interfaceName, dpId, vpnName);
                        return;
                    }
                    waitForVpnInterfaceOpRemoval = true;
                } else {
                    LOG.error("processVpnInterfaceUp: vpn interface {} to go to configured vpn {} on dpn {},"
                            + " but in operational vpn {}", interfaceName, vpnName, dpId, opVpnName);
                }
            }
            if (!waitForVpnInterfaceOpRemoval) {
                // Add the VPNInterface and quit
                vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, primaryRd, interfaceName,
                        null/*ipAddressSourceValuePair*/,
                        true /* add */);
                processVpnInterfaceAdjacencies(dpId, lportTag, vpnName, primaryRd, interfaceName,
                        vpnId, writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState, prefixListForRefreshFib,
                        eventSource, ipAddressList);
                if (!isBgpVpnInternetVpn) {
                    vpnUtil.bindService(vpnName, interfaceName, false /*isTunnelInterface*/);
                }
                LOG.info("processVpnInterfaceUp: Plumbed vpn interface {} onto dpn {} for vpn {}", interfaceName,
                        dpId, vpnName);
                if (interfaceManager.isExternalInterface(interfaceName)) {
                    processExternalVpnInterface(interfaceName, vpnName, dpId, lportTag,
                        NwConstants.ADD_FLOW);
                }
                return;
            }

            // FIB didn't get a chance yet to clean up this VPNInterface
            // Let us give it a chance here !
            LOG.info("processVpnInterfaceUp: Trying to add VPN Interface {} on dpn {} for vpn {},"
                    + " but waiting for FIB to clean up! ", interfaceName, dpId, vpnName);
            try {
                Runnable notifyTask = new VpnNotifyTask();
                synchronized (notifyTask) {
                    // Per FB's "Unconditional wait" violation, the code should really verify that the condition it
                    // intends to wait for is not already satisfied before calling wait. However the VpnNotifyTask is
                    // published here while holding the lock on it so this path will hit the wait before notify can be
                    // invoked.
                    vpnIntfMap.put(interfaceName, notifyTask);
                    try {
                        notifyTask.wait(VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignored
                    }
                }
            } finally {
                vpnIntfMap.remove(interfaceName);
            }

            if (opVpnInterface != null) {
                LOG.warn("processVpnInterfaceUp: VPN Interface {} removal on dpn {} for vpn {}"
                        + " by FIB did not complete on time," + " bailing addition ...", interfaceName,
                        dpId, vpnName);
                vpnUtil.unsetScheduledToRemoveForVpnInterface(interfaceName);
                return;
            }
            // VPNInterface got removed, proceed with Add
            LOG.info("processVpnInterfaceUp: Continuing to plumb vpn interface {} onto dpn {} for vpn {}",
                    interfaceName, dpId, vpnName);
            vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, primaryRd, interfaceName,
                    null/*ipAddressSourceValuePair*/,
                    true /* add */);
            processVpnInterfaceAdjacencies(dpId, lportTag, vpnName, primaryRd, interfaceName,
                    vpnId, writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState, prefixListForRefreshFib,
                    eventSource, ipAddressList);
            if (!isBgpVpnInternetVpn) {
                vpnUtil.bindService(vpnName, interfaceName, false/*isTunnelInterface*/);
            }
            LOG.info("processVpnInterfaceUp: Plumbed vpn interface {} onto dpn {} for vpn {} after waiting for"
                    + " FIB to clean up", interfaceName, dpId, vpnName);
            if (interfaceManager.isExternalInterface(interfaceName)) {
                processExternalVpnInterface(interfaceName, vpnName, dpId,
                               lportTag, NwConstants.ADD_FLOW);
            }
        } else {
            try {
                // Interface is retained in the DPN, but its Link Up.
                // Advertise prefixes again for this interface to BGP
                InstanceIdentifier<VpnInterface> identifier =
                        VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName());
                InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier =
                        VpnUtil.getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                advertiseAdjacenciesForVpnToBgp(primaryRd, dpId, vpnInterfaceOpIdentifier, vpnName, interfaceName);
                // Perform similar operation as interface add event for extraroutes.
                InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
                Optional<Adjacencies> optAdjacencies = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, path);
                if (!optAdjacencies.isPresent()) {
                    LOG.trace("No config adjacencyKeyAdjacencyMap present for vpninterface {}", vpnInterface);
                    return;
                }
                Map<AdjacencyKey, Adjacency> adjacencyKeyAdjacencyMap = optAdjacencies.get().nonnullAdjacency();
                for (Adjacency adjacency : adjacencyKeyAdjacencyMap.values()) {
                    if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                        continue;
                    }
                    // if BGPVPN Internet, filter only IPv6 Adjacencies
                    if (isBgpVpnInternetVpn && !vpnUtil.isAdjacencyEligibleToVpnInternet(adjacency)) {
                        continue;
                    }
                    addNewAdjToVpnInterface(vpnInterfaceOpIdentifier, primaryRd, adjacency,
                            dpId, writeOperTxn, writeConfigTxn, writeInvTxn, prefixListForRefreshFib);
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("processVpnInterfaceUp: Failed to read data store for interface {} vpn {} rd {} dpn {}",
                        interfaceName, vpnName, primaryRd, dpId);
            }
        }
    }

    private void processExternalVpnInterface(String interfaceName, String vpnName, Uint64 dpId,
        int lportTag, int addOrRemove) {
        Uuid extNetworkId;
        try {
            // vpn instance of ext-net interface is the network-id
            extNetworkId = new Uuid(vpnName);
        } catch (IllegalArgumentException e) {
            LOG.error("processExternalVpnInterface: VPN instance {} is not Uuid. Processing external vpn interface {}"
                    + " on dpn {} failed", vpnName, interfaceName, dpId);
            return;
        }

        List<Uuid> routerIds = vpnUtil.getExternalNetworkRouterIds(extNetworkId);
        if (routerIds == null || routerIds.isEmpty()) {
            LOG.info("processExternalVpnInterface: No router is associated with {}."
                    + " Bailing out of processing external vpn interface {} on dpn {} for vpn {}",
                    extNetworkId.getValue(), interfaceName, dpId, vpnName);
            return;
        }

        LOG.info("processExternalVpnInterface: Router-ids {} associated with exernal vpn-interface {} on dpn {}"
                + " for vpn {}", routerIds, interfaceName, dpId, vpnName);
        for (Uuid routerId : routerIds) {
            String routerName = routerId.getValue();
            Uint64 primarySwitch = vpnUtil.getPrimarySwitchForRouter(routerName);
            if (Objects.equals(primarySwitch, dpId)) {
                Routers router = vpnUtil.getExternalRouter(routerName);
                if (router != null) {
                    if (addOrRemove == NwConstants.ADD_FLOW) {
                        vpnManager.addArpResponderFlowsToExternalNetworkIps(routerName,
                                VpnUtil.getIpsListFromExternalIps(new ArrayList<ExternalIps>(router
                                                .nonnullExternalIps().values())), router.getExtGwMacAddress(),
                                dpId, interfaceName, lportTag);
                    } else {
                        vpnManager.removeArpResponderFlowsToExternalNetworkIps(routerName,
                                VpnUtil.getIpsListFromExternalIps(new ArrayList<ExternalIps>(router
                                        .nonnullExternalIps().values())),
                                dpId, interfaceName, lportTag);
                    }
                } else {
                    LOG.error("processExternalVpnInterface: No external-router found for router-id {}. Bailing out of"
                            + " processing external vpn-interface {} on dpn {} for vpn {}", routerName,
                            interfaceName, dpId, vpnName);
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void advertiseAdjacenciesForVpnToBgp(final String rd, Uint64 dpnId,
                                                 final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
                                                  String vpnName, String interfaceName) {
        if (rd == null) {
            LOG.error("advertiseAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} on dpn {} in vpn {}",
                  interfaceName, dpnId, vpnName);
            return;
        } else {
            if (rd.equals(vpnName)) {
                LOG.info("advertiseAdjacenciesForVpnFromBgp: Ignoring BGP advertisement for interface {} on dpn {}"
                        + " as it is in internal vpn{} with rd {}", interfaceName, dpnId, vpnName, rd);
                return;
            }
        }
        LOG.info("advertiseAdjacenciesForVpnToBgp: Advertising interface {} on dpn {} in vpn {} with rd {} ",
                interfaceName, dpnId, vpnName, rd);

        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        if (nextHopIp == null) {
            LOG.error("advertiseAdjacenciesForVpnToBgp: NextHop for interface {} on dpn {} is null,"
                    + " returning from advertising route with rd {} vpn {} to bgp", interfaceName, dpnId,
                    rd, vpnName);
            return;
        }

        try {
            //Read NextHops
            InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
            Optional<AdjacenciesOp> adjacencies = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, path);
            if (adjacencies.isPresent()) {
                Map<AdjacencyKey, Adjacency> nextHopsMap = adjacencies.get().getAdjacency();
                if (nextHopsMap != null && !nextHopsMap.isEmpty()) {
                    LOG.debug("advertiseAdjacenciesForVpnToBgp:  NextHops are {} for interface {} on dpn {} for vpn {}"
                            + " rd {}", nextHopsMap, interfaceName, dpnId, vpnName, rd);
                    VpnInstanceOpDataEntry vpnInstanceOpData = vpnUtil.getVpnInstanceOpData(rd);
                    Uint32 l3vni = vpnInstanceOpData.getL3vni();
                    VrfEntry.EncapType encapType = VpnUtil.isL3VpnOverVxLan(l3vni)
                            ? VrfEntry.EncapType.Vxlan : VrfEntry.EncapType.Mplsgre;
                    for (Adjacency nextHop : nextHopsMap.values()) {
                        if (nextHop.getAdjacencyType() == AdjacencyType.ExtraRoute) {
                            continue;
                        }
                        String gatewayMac = null;
                        Uint32 label = Uint32.ZERO;
                        if (VpnUtil.isL3VpnOverVxLan(l3vni)) {
                            final VpnPortipToPort gwPort = vpnUtil.getNeutronPortFromVpnPortFixedIp(
                                    vpnInstanceOpData.getVpnInstanceName(), nextHop.getIpAddress());
                            gatewayMac = arpResponderHandler.getGatewayMacAddressForInterface(gwPort, interfaceName)
                                    .get();
                        } else {
                            label = nextHop.getLabel();
                        }
                        try {
                            LOG.info("VPN ADVERTISE: advertiseAdjacenciesForVpnToBgp: Adding Fib Entry rd {} prefix {}"
                                    + " nexthop {} label {}", rd, nextHop.getIpAddress(), nextHopIp, label);
                            bgpManager.advertisePrefix(rd, nextHop.getMacAddress(), nextHop.getIpAddress(), nextHopIp,
                                    encapType, label, l3vni, Uint32.ZERO /*l2vni*/,
                                    gatewayMac);
                            LOG.info("VPN ADVERTISE: advertiseAdjacenciesForVpnToBgp: Added Fib Entry rd {} prefix {}"
                                            + " nexthop {} label {} for interface {} on dpn {} for vpn {}", rd,
                                    nextHop.getIpAddress(), nextHopIp, label, interfaceName, dpnId, vpnName);
                        } catch (Exception e) {
                            LOG.error("advertiseAdjacenciesForVpnToBgp: Failed to advertise prefix {} in vpn {}"
                                    + " with rd {} for interface {} on dpn {}", nextHop.getIpAddress(), vpnName, rd,
                                    interfaceName, dpnId, e);
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("advertiseAdjacenciesForVpnToBgp: Failed to read data store for interface {} dpn {} nexthop {}"
                    + "vpn {} rd {}", interfaceName, dpnId, nextHopIp, vpnName, rd);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void withdrawAdjacenciesForVpnFromBgp(final InstanceIdentifier<VpnInterfaceOpDataEntry> identifier,
                        String vpnName, String interfaceName, TypedWriteTransaction<Configuration> writeConfigTxn,
                        TypedWriteTransaction<Operational> writeOperTx) {
        //Read NextHops
        InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
        String rd = vpnUtil.getVpnRd(interfaceName);
        if (rd == null) {
            LOG.error("withdrawAdjacenciesForVpnFromBgp: Unable to recover rd for interface {} in vpn {}",
                interfaceName, vpnName);
            return;
        } else {
            if (rd.equals(vpnName)) {
                LOG.info(
                        "withdrawAdjacenciesForVpnFromBgp: Ignoring BGP withdrawal for interface {} as it is in "
                                + "internal vpn{} with rd {}", interfaceName, vpnName, rd);
                return;
            }
        }
        LOG.info("withdrawAdjacenciesForVpnFromBgp: For interface {} in vpn {} with rd {}", interfaceName,
               vpnName, rd);
        Optional<AdjacenciesOp> adjacencies = Optional.empty();
        try {
            adjacencies = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    path);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("withdrawAdjacenciesForVpnFromBgp: Failed to read data store for interface {} vpn {}",
                    interfaceName, vpnName);
        }
        if (adjacencies.isPresent()) {
            Map<AdjacencyKey, Adjacency> nextHopsMap = adjacencies.get().getAdjacency();

            if (nextHopsMap != null && !nextHopsMap.isEmpty()) {
                LOG.trace("withdrawAdjacenciesForVpnFromBgp: NextHops are {} for interface {} in vpn {} rd {}",
                        nextHopsMap, interfaceName, vpnName, rd);
                for (Adjacency nextHop : nextHopsMap.values()) {
                    try {
                        if (nextHop.getAdjacencyType() != AdjacencyType.ExtraRoute) {
                            LOG.info("VPN WITHDRAW: withdrawAdjacenciesForVpnFromBgp: Removing Fib Entry rd {}"
                                    + " prefix {} for interface {} in vpn {}", rd, nextHop.getIpAddress(),
                                    interfaceName, vpnName);
                            bgpManager.withdrawPrefix(rd, nextHop.getIpAddress());
                            LOG.info("VPN WITHDRAW: withdrawAdjacenciesForVpnFromBgp: Removed Fib Entry rd {}"
                                    + " prefix {} for interface {} in vpn {}", rd, nextHop.getIpAddress(),
                                    interfaceName, vpnName);
                        } else if (nextHop.getNextHopIpList() != null) {
                            // Perform similar operation as interface delete event for extraroutes.
                            String allocatedRd = nextHop.getVrfId();
                            for (String nh : nextHop.getNextHopIpList()) {
                                deleteExtraRouteFromCurrentAndImportingVpns(
                                    vpnName, nextHop.getIpAddress(), nh, allocatedRd, interfaceName, writeConfigTxn,
                                    writeOperTx);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("withdrawAdjacenciesForVpnFromBgp: Failed to withdraw prefix {} in vpn {} with rd {}"
                                + " for interface {} ", nextHop.getIpAddress(), vpnName, rd, interfaceName, e);
                    }
                }
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void processVpnInterfaceAdjacencies(Uint64 dpnId, final int lportTag, String vpnName,
                                                  String primaryRd, String interfaceName, final Uint32 vpnId,
                                                  TypedWriteTransaction<Configuration> writeConfigTxn,
                                                  TypedWriteTransaction<Operational> writeOperTxn,
                                                  TypedReadWriteTransaction<Configuration> writeInvTxn,
                                                  Interface interfaceState, Set<String> prefixListForRefreshFib,
                                                  String eventSource, List<String> ipAddressList)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<VpnInterface> identifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        // Read NextHops
        Optional<VpnInterface> vpnInteface = Optional.empty();
        try {
            vpnInteface = SingleTransactionDataBroker.syncReadOptional(dataBroker,
            LogicalDatastoreType.CONFIGURATION, identifier);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("processVpnInterfaceAdjacencies: Failed to read data store for interface {} vpn {} rd {}"
                    + "dpn {}", interfaceName, vpnName, primaryRd, dpnId);
        }
        Uuid intfnetworkUuid = null;
        NetworkType networkType = null;
        Long segmentationId = Long.valueOf(-1);
        Adjacencies adjacencies = null;
        if (vpnInteface.isPresent()) {
            intfnetworkUuid = vpnInteface.get().getNetworkId();
            networkType = vpnInteface.get().getNetworkType();
            segmentationId = vpnInteface.get().getSegmentationId().toJava();
            adjacencies = vpnInteface.get().augmentation(Adjacencies.class);
            if (adjacencies == null) {
                addVpnInterfaceToOperational(vpnName, interfaceName, dpnId, null/*adjacencies*/, lportTag,
                        null/*gwMac*/,  null/*gatewayIp*/, writeOperTxn);
                return;
            }
        }
        // Get the rd of the vpn instance
        String nextHopIp = null;
        String gatewayIp = null;
        try {
            nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        } catch (Exception e) {
            LOG.error("processVpnInterfaceAdjacencies: Unable to retrieve endpoint ip address for "
                    + "dpnId {} for vpnInterface {} vpnName {}", dpnId, interfaceName, vpnName);
        }
        List<String> nhList = new ArrayList<>();
        if (nextHopIp != null) {
            nhList.add(nextHopIp);
            LOG.debug("processVpnInterfaceAdjacencies: NextHop for interface {} on dpn {} in vpn {} is {}",
                    interfaceName, dpnId, vpnName, nhList);
        }
        Optional<String> gwMac = Optional.empty();
        String vpnInterfaceSubnetGwMacAddress = null;
        VpnInstanceOpDataEntry vpnInstanceOpData = vpnUtil.getVpnInstanceOpData(primaryRd);
        Uint32 l3vni = vpnInstanceOpData.getL3vni() != null ? vpnInstanceOpData.getL3vni() : Uint32.ZERO;
        boolean isL3VpnOverVxLan = VpnUtil.isL3VpnOverVxLan(l3vni);
        VrfEntry.EncapType encapType = isL3VpnOverVxLan ? VrfEntry.EncapType.Vxlan : VrfEntry.EncapType.Mplsgre;
        VpnPopulator registeredPopulator = L3vpnRegistry.getRegisteredPopulator(encapType);
        Map<AdjacencyKey, Adjacency> nextHopsMap = adjacencies != null ? adjacencies.getAdjacency()
                : Collections.<AdjacencyKey, Adjacency>emptyMap();
        List<Adjacency> value = new ArrayList<>();
        for (Adjacency nextHop : nextHopsMap.values()) {
            String rd = primaryRd;
            String nexthopIpValue = nextHop.getIpAddress().split("/")[0];
            ipAddressList.add(nexthopIpValue);
            if (vpnInstanceOpData.getBgpvpnType() == VpnInstanceOpDataEntry.BgpvpnType.InternetBGPVPN
                    && NWUtil.isIpv4Address(nexthopIpValue)) {
                String prefix = nextHop.getIpAddress() == null ?  "null" :
                      VpnUtil.getIpPrefix(nextHop.getIpAddress());
                LOG.debug("processVpnInterfaceAdjacencies: UnsupportedOperation : Not Adding prefix {} to interface {}"
                      + " as InternetVpn has an IPV4 address {}", prefix, interfaceName, vpnName);
                continue;
            }
            if (nextHop.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
                Prefixes.PrefixCue prefixCue = nextHop.isPhysNetworkFunc()
                        ? Prefixes.PrefixCue.PhysNetFunc : Prefixes.PrefixCue.None;
                LOG.debug("processVpnInterfaceAdjacencies: Adding prefix {} to interface {} with nextHopsMap {} "
                        + "on dpn {} for vpn {}", prefix, interfaceName, nhList, dpnId, vpnName);

                Prefixes prefixes = intfnetworkUuid != null
                    ? VpnUtil.getPrefixToInterface(dpnId, interfaceName, prefix, intfnetworkUuid ,networkType,
                            segmentationId, prefixCue) :
                    VpnUtil.getPrefixToInterface(dpnId, interfaceName, prefix, prefixCue);
                writeOperTxn.mergeParentStructureMerge(VpnUtil.getPrefixToInterfaceIdentifier(
                        vpnUtil.getVpnId(vpnName), prefix), prefixes);
                final Uuid subnetId = nextHop.getSubnetId();

                gatewayIp = nextHop.getSubnetGatewayIp();
                if (gatewayIp == null) {
                    Optional<String> gatewayIpOptional = vpnUtil.getVpnSubnetGatewayIp(subnetId);
                    if (gatewayIpOptional.isPresent()) {
                        gatewayIp = gatewayIpOptional.get();
                    }
                }

                if (gatewayIp != null) {
                    gwMac = getMacAddressForSubnetIp(vpnName, interfaceName, gatewayIp);
                    if (gwMac.isPresent()) {
                        // A valid mac-address is available for this subnet-gateway-ip
                        // Use this for programming ARP_RESPONDER table here.  And save this
                        // info into vpnInterface operational, so it can used in VrfEntryProcessor
                        // to populate L3_GW_MAC_TABLE there.
                        arpResponderHandler.addArpResponderFlow(dpnId, lportTag, interfaceName,
                                gatewayIp, gwMac.get());
                        vpnInterfaceSubnetGwMacAddress = gwMac.get();
                    } else {
                        // A valid mac-address is not available for this subnet-gateway-ip
                        // Use the connected-mac-address to configure ARP_RESPONDER Table.
                        // Save this connected-mac-address as gateway-mac-address for the
                        // VrfEntryProcessor to use this later to populate the L3_GW_MAC_TABLE.
                        gwMac = InterfaceUtils.getMacAddressFromInterfaceState(interfaceState);
                        if (gwMac.isPresent()) {
                            vpnUtil.setupGwMacIfExternalVpn(dpnId, interfaceName, vpnId, writeInvTxn,
                                    NwConstants.ADD_FLOW, gwMac.get());
                            arpResponderHandler.addArpResponderFlow(dpnId, lportTag, interfaceName,
                                    gatewayIp, gwMac.get());
                        } else {
                            LOG.error("processVpnInterfaceAdjacencies: Gateway MAC for subnet ID {} could not be "
                                + "obtained, cannot create ARP responder flow for interface name {}, vpnName {}, "
                                + "gwIp {}",
                                subnetId, interfaceName, vpnName, gatewayIp);
                        }
                    }
                } else {
                    LOG.warn("processVpnInterfaceAdjacencies: Gateway IP for subnet ID {} could not be obtained, "
                        + "cannot create ARP responder flow for interface name {}, vpnName {}",
                        subnetId, interfaceName, vpnName);
                    gwMac = InterfaceUtils.getMacAddressFromInterfaceState(interfaceState);
                }
                LOG.info("processVpnInterfaceAdjacencies: Added prefix {} to interface {} with nextHopsMap {} on dpn {}"
                        + " for vpn {}", prefix, interfaceName, nhList, dpnId, vpnName);
            } else {
                //Extra route adjacency
                String prefix = VpnUtil.getIpPrefix(nextHop.getIpAddress());
                String vpnPrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
                // FIXME: separate this out somehow?
                final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnPrefixKey);
                lock.lock();
                try {
                    java.util.Optional<String> rdToAllocate = vpnUtil
                            .allocateRdForExtraRouteAndUpdateUsedRdsMap(vpnId, null, prefix, vpnName,
                                    nextHop.getNextHopIpList().get(0), dpnId);
                    if (rdToAllocate.isPresent()) {
                        rd = rdToAllocate.get();
                        LOG.info("processVpnInterfaceAdjacencies: The rd {} is allocated for the extraroute {}",
                            rd, prefix);
                    } else {
                        LOG.error("processVpnInterfaceAdjacencies: No rds to allocate extraroute {}", prefix);
                        continue;
                    }
                } finally {
                    lock.unlock();
                }
                LOG.info("processVpnInterfaceAdjacencies: Added prefix {} and nextHopList {} as extra-route for vpn{}"
                        + " interface {} on dpn {}", nextHop.getIpAddress(), nextHop.getNextHopIpList(), vpnName,
                        interfaceName, dpnId);
            }
            // Please note that primary adjacency will use a subnet-gateway-mac-address that
            // can be different from the gateway-mac-address within the VRFEntry as the
            // gateway-mac-address is a superset.
            RouteOrigin origin = VpnUtil.getRouteOrigin(nextHop.getAdjacencyType());
            L3vpnInput input = new L3vpnInput().setNextHop(nextHop).setRd(rd).setVpnName(vpnName)
                .setInterfaceName(interfaceName).setNextHopIp(nextHopIp).setPrimaryRd(primaryRd)
                .setSubnetGatewayMacAddress(vpnInterfaceSubnetGwMacAddress).setRouteOrigin(origin);
            Adjacency operationalAdjacency = null;
            try {
                operationalAdjacency = registeredPopulator.createOperationalAdjacency(input);
            } catch (NullPointerException e) {
                LOG.error("processVpnInterfaceAdjacencies: failed to create operational adjacency: input: {}, {}",
                    input, e.getMessage());
                return;
            }
            if (nextHop.getAdjacencyType() != AdjacencyType.PrimaryAdjacency) {
                vpnManager.addExtraRoute(vpnName, nextHop.getIpAddress(), nextHop.getNextHopIpList().get(0), rd,
                    vpnName, l3vni, origin, interfaceName, operationalAdjacency, encapType, prefixListForRefreshFib,
                    writeConfigTxn);
            }
            value.add(operationalAdjacency);
        }

        AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(value);
        addVpnInterfaceToOperational(vpnName, interfaceName, dpnId, aug, lportTag,
                gwMac.isPresent() ? gwMac.get() : null, gatewayIp, writeOperTxn);

        L3vpnInput input = new L3vpnInput().setNextHopIp(nextHopIp).setL3vni(l3vni.longValue()).setPrimaryRd(primaryRd)
                .setGatewayMac(gwMac.orElse(null)).setInterfaceName(interfaceName)
                .setVpnName(vpnName).setDpnId(dpnId).setEncapType(encapType);

        for (Adjacency nextHop : aug.nonnullAdjacency().values()) {
            // Adjacencies other than primary Adjacencies are handled in the addExtraRoute call above.
            if (nextHop.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                RouteOrigin origin = VpnUtil.getRouteOrigin(nextHop.getAdjacencyType());
                input.setNextHop(nextHop).setRd(nextHop.getVrfId()).setRouteOrigin(origin);
                registeredPopulator.populateFib(input, interfaceName, eventSource, writeConfigTxn);
            }
        }
    }

    private void addVpnInterfaceToOperational(String vpnName, String interfaceName, Uint64 dpnId, AdjacenciesOp aug,
                                              long lportTag, String gwMac, String gwIp,
                                              TypedWriteTransaction<Operational> writeOperTxn) {
        VpnInterfaceOpDataEntry opInterface =
              VpnUtil.getVpnInterfaceOpDataEntry(interfaceName, vpnName, aug, dpnId, lportTag, gwMac, gwIp);
        InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId = VpnUtil
            .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        writeOperTxn.mergeParentStructurePut(interfaceId, opInterface);
        LOG.info("addVpnInterfaceToOperational: Added VPN Interface {} on dpn {} vpn {} to operational datastore",
                interfaceName, dpnId, vpnName);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateVpnInterfaceOnTepAdd(VpnInterfaceOpDataEntry vpnInterface,
                                           StateTunnelList stateTunnelList,
                                           TypedWriteTransaction<Configuration> writeConfigTxn,
                                           TypedWriteTransaction<Operational> writeOperTxn) {

        String srcTepIp = stateTunnelList.getSrcInfo().getTepIp().stringValue();
        Uint64 srcDpnId = Uint64.valueOf(stateTunnelList.getSrcInfo().getTepDeviceId()).intern();
        AdjacenciesOp adjacencies = vpnInterface.augmentation(AdjacenciesOp.class);
        Map<AdjacencyKey, Adjacency> keyAdjacencyMap =
            adjacencies != null && adjacencies.getAdjacency() != null ? adjacencies.getAdjacency()
                    : Collections.<AdjacencyKey, Adjacency>emptyMap();
        if (keyAdjacencyMap.isEmpty()) {
            LOG.trace("updateVpnInterfaceOnTepAdd: Adjacencies are empty for vpnInterface {} on dpn {}",
                    vpnInterface, srcDpnId);
            return;
        }
        String prefix = null;
        List<Adjacency> value = new ArrayList<>();
        boolean isFibNextHopAddReqd = false;
        String vpnName = vpnInterface.getVpnInstanceName();
        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
        String primaryRd = vpnUtil.getPrimaryRd(vpnName);
        LOG.info("updateVpnInterfaceOnTepAdd: AdjacencyList for interface {} on dpn {} vpn {} is {}",
                vpnInterface.getName(), vpnInterface.getDpnId(),
                vpnInterface.getVpnInstanceName(), keyAdjacencyMap);
        for (Adjacency adj : keyAdjacencyMap.values()) {
            String rd = adj.getVrfId();
            rd = rd != null ? rd : vpnName;
            prefix = adj.getIpAddress();
            Uint32 label = adj.getLabel();
            List<String> nhList = Collections.singletonList(srcTepIp);
            List<String> nextHopList = adj.getNextHopIpList();
            // If TEP is added , update the nexthop of primary adjacency.
            // Secondary adj nexthop is already pointing to primary adj IP address.
            if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                value.add(new AdjacencyBuilder(adj).setNextHopIpList(nhList).build());
                if (nextHopList != null && !nextHopList.isEmpty()) {
                    /* everything right already */
                } else {
                    isFibNextHopAddReqd = true;
                }
            } else {
                Optional<VrfEntry> vrfEntryOptional = FibHelper.getVrfEntry(dataBroker, primaryRd, prefix);
                if (!vrfEntryOptional.isPresent()) {
                    continue;
                }
                nhList = FibHelper.getNextHopListFromRoutePaths(vrfEntryOptional.get());
                if (!nhList.contains(srcTepIp)) {
                    nhList.add(srcTepIp);
                    isFibNextHopAddReqd = true;
                }
                value.add(adj);
            }

            if (isFibNextHopAddReqd) {
                updateLabelMapper(label, nhList);
                LOG.info("updateVpnInterfaceOnTepAdd: Updated label mapper : label {} dpn {} prefix {} nexthoplist {}"
                        + " vpn {} vpnid {} rd {} interface {}", label, srcDpnId , prefix, nhList,
                        vpnInterface.getVpnInstanceName(), vpnId, rd, vpnInterface.getName());
                // Update the VRF entry with nextHop
                fibManager.updateRoutePathForFibEntry(primaryRd, prefix, srcTepIp,
                        label, true, vpnInterface.getName(), FIB_EVENT_SOURCE_TUNNEL_STATE,
                        writeConfigTxn);

                //Get the list of VPN's importing this route(prefix) .
                // Then update the VRF entry with nhList
                List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnUtil.getVpnsImportingMyRoute(vpnName);
                for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                    String vpnRd = vpn.getVrfId();
                    if (vpnRd != null) {
                        fibManager.updateRoutePathForFibEntry(vpnRd, prefix,
                            srcTepIp, label, true, vpnInterface.getName(), FIB_EVENT_SOURCE_TUNNEL_STATE,
                                writeConfigTxn);
                        LOG.info("updateVpnInterfaceOnTepAdd: Exported route with rd {} prefix {} nhList {} label {}"
                                + " interface {} dpn {} from vpn {} to VPN {} vpnRd {}", rd, prefix, nhList, label,
                            vpnInterface.getName(), srcDpnId, vpnName,
                            vpn.getVpnInstanceName(), vpnRd);
                    }
                }
                // Advertise the prefix to BGP only for external vpn
                // since there is a nexthop change.
                try {
                    if (!rd.equalsIgnoreCase(vpnName)) {
                        bgpManager.advertisePrefix(rd, null /*macAddress*/, prefix, nhList,
                                VrfEntry.EncapType.Mplsgre, label, Uint32.ZERO /*evi*/, Uint32.ZERO /*l2vni*/,
                                null /*gatewayMacAddress*/);
                    }
                    LOG.info("updateVpnInterfaceOnTepAdd: Advertised rd {} prefix {} nhList {} label {}"
                            + " for interface {} on dpn {} vpn {}", rd, prefix, nhList, label, vpnInterface.getName(),
                            srcDpnId, vpnName);
                } catch (Exception ex) {
                    LOG.error("updateVpnInterfaceOnTepAdd: Exception when advertising prefix {} nh {} label {}"
                            + " on rd {} for interface {} on dpn {} vpn {}", prefix, nhList, label, rd,
                            vpnInterface.getName(), srcDpnId, vpnName, ex);
                }
            }
        }
        AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(value);
        VpnInterfaceOpDataEntry opInterface = new VpnInterfaceOpDataEntryBuilder(vpnInterface)
                .withKey(new VpnInterfaceOpDataEntryKey(vpnInterface.getName(), vpnName))
                .addAugmentation(AdjacenciesOp.class, aug).build();
        InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId =
                VpnUtil.getVpnInterfaceOpDataEntryIdentifier(vpnInterface.getName(), vpnName);
        writeOperTxn.mergeParentStructurePut(interfaceId, opInterface);
        LOG.info("updateVpnInterfaceOnTepAdd: interface {} updated successully on tep add on dpn {} vpn {}",
                vpnInterface.getName(), srcDpnId, vpnName);

    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateVpnInterfaceOnTepDelete(VpnInterfaceOpDataEntry vpnInterface,
                                              StateTunnelList stateTunnelList,
                                              TypedWriteTransaction<Configuration> writeConfigTxn,
                                              TypedWriteTransaction<Operational> writeOperTxn) {

        AdjacenciesOp adjacencies = vpnInterface.augmentation(AdjacenciesOp.class);
        List<Adjacency> adjList = adjacencies != null ? new ArrayList<Adjacency>(adjacencies
                .nonnullAdjacency().values()) : new ArrayList<>();
        String prefix = null;
        boolean isNextHopRemoveReqd = false;
        String srcTepIp = stateTunnelList.getSrcInfo().getTepIp().stringValue();
        Uint64 srcDpnId = Uint64.valueOf(stateTunnelList.getSrcInfo().getTepDeviceId()).intern();
        String vpnName = vpnInterface.getVpnInstanceName();
        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
        String primaryRd = vpnUtil.getVpnRd(vpnName);
        if (adjList != null) {
            List<Adjacency> value = new ArrayList<>();
            LOG.info("updateVpnInterfaceOnTepDelete: AdjacencyList for interface {} on dpn {} vpn {} is {}",
                    vpnInterface.getName(), vpnInterface.getDpnId(),
                    vpnInterface.getVpnInstanceName(), adjList);
            for (Adjacency adj : adjList) {
                List<String> nhList = new ArrayList<>();
                String rd = adj.getVrfId();
                rd = rd != null ? rd : vpnName;
                prefix = adj.getIpAddress();
                List<String> nextHopList = adj.getNextHopIpList();
                Uint32 label = adj.getLabel();
                if (nextHopList != null && !nextHopList.isEmpty()) {
                    isNextHopRemoveReqd = true;
                }
                // If TEP is deleted , remove the nexthop from primary adjacency.
                // Secondary adj nexthop will continue to point to primary adj IP address.
                if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    value.add(new AdjacencyBuilder(adj).setNextHopIpList(nhList).build());
                } else {
                    Optional<VrfEntry> vrfEntryOptional = FibHelper.getVrfEntry(dataBroker, primaryRd, prefix);
                    if (!vrfEntryOptional.isPresent()) {
                        continue;
                    }
                    nhList = FibHelper.getNextHopListFromRoutePaths(vrfEntryOptional.get());
                    if (nhList.contains(srcTepIp)) {
                        nhList.remove(srcTepIp);
                        isNextHopRemoveReqd = true;
                    }
                    value.add(adj);
                }

                if (isNextHopRemoveReqd) {
                    updateLabelMapper(label, nhList);
                    LOG.info("updateVpnInterfaceOnTepDelete: Updated label mapper : label {} dpn {} prefix {}"
                            + " nexthoplist {} vpn {} vpnid {} rd {} interface {}", label, srcDpnId,
                            prefix, nhList, vpnName,
                            vpnId, rd, vpnInterface.getName());
                    // Update the VRF entry with removed nextHop
                    fibManager.updateRoutePathForFibEntry(primaryRd, prefix, srcTepIp,
                            label, false, vpnInterface.getName(), FIB_EVENT_SOURCE_TUNNEL_STATE,
                            writeConfigTxn);
                    //Get the list of VPN's importing this route(prefix) .
                    // Then update the VRF entry with nhList
                    List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnUtil.getVpnsImportingMyRoute(vpnName);
                    for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                        String vpnRd = vpn.getVrfId();
                        if (vpnRd != null) {
                            fibManager.updateRoutePathForFibEntry(vpnRd, prefix,
                                srcTepIp, label, false, vpnInterface.getName(),
                                    FIB_EVENT_SOURCE_TUNNEL_STATE,
                                    writeConfigTxn);
                            LOG.info("updateVpnInterfaceOnTepDelete: Exported route with rd {} prefix {} nhList {}"
                                    + " label {} interface {} dpn {} from vpn {} to VPN {} vpnRd {}", rd, prefix,
                                    nhList, label, vpnInterface.getName(), srcDpnId,
                                    vpnName,
                                    vpn.getVpnInstanceName(), vpnRd);
                        }
                    }

                    // Withdraw prefix from BGP only for external vpn.
                    try {
                        if (!rd.equalsIgnoreCase(vpnName)) {
                            bgpManager.withdrawPrefix(rd, prefix);
                        }
                        LOG.info("updateVpnInterfaceOnTepDelete: Withdrawn rd {} prefix {} nhList {} label {}"
                                + " for interface {} on dpn {} vpn {}", rd, prefix, nhList, label,
                                vpnInterface.getName(), srcDpnId,
                                vpnName);
                    } catch (Exception ex) {
                        LOG.error("updateVpnInterfaceOnTepDelete: Exception when withdrawing prefix {} nh {} label {}"
                                + " on rd {} for interface {} on dpn {} vpn {}", prefix, nhList, label, rd,
                                vpnInterface.getName(), srcDpnId, vpnName, ex);
                    }
                }
            }
            AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(value);
            VpnInterfaceOpDataEntry opInterface = new VpnInterfaceOpDataEntryBuilder(vpnInterface)
                    .withKey(new VpnInterfaceOpDataEntryKey(vpnInterface.getName(), vpnName))
                    .addAugmentation(AdjacenciesOp.class, aug).build();
            InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId =
                    VpnUtil.getVpnInterfaceOpDataEntryIdentifier(vpnInterface.getName(), vpnName);
            writeOperTxn.mergeParentStructurePut(interfaceId, opInterface);
            LOG.info("updateVpnInterfaceOnTepDelete: interface {} updated successully on tep delete on dpn {} vpn {}",
                         vpnInterface.getName(), srcDpnId, vpnName);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<VpnInstanceOpDataEntry> getVpnsExportingMyRoute(final String vpnName) {
        List<VpnInstanceOpDataEntry> vpnsToExportRoute = new ArrayList<>();
        final VpnInstanceOpDataEntry vpnInstanceOpDataEntry;
        String vpnRd = vpnUtil.getVpnRd(vpnName);
        try {
            VpnInstanceOpDataEntry opDataEntry = vpnUtil.getVpnInstanceOpData(vpnRd);
            if (opDataEntry == null) {
                LOG.error("getVpnsExportingMyRoute: Null vpn instance op data for vpn {} rd {}"
                        + " when check for vpns exporting the routes", vpnName, vpnRd);
                return vpnsToExportRoute;
            }
            vpnInstanceOpDataEntry = opDataEntry;
        } catch (Exception re) {
            LOG.error("getVpnsExportingMyRoute: DSexception when retrieving vpn instance op data for vpn {} rd {}"
                    + " to check for vpns exporting the routes", vpnName, vpnRd, re);
            return vpnsToExportRoute;
        }
        Predicate<VpnInstanceOpDataEntry> excludeVpn = input -> {
            if (input.getVpnInstanceName() == null) {
                LOG.error("getVpnsExportingMyRoute.excludeVpn: Received vpn instance with rd {}  without a name",
                        input.getVrfId());
                return false;
            }
            return !input.getVpnInstanceName().equals(vpnName);
        };

        Predicate<VpnInstanceOpDataEntry> matchRTs = input -> {
            Iterable<String> commonRTs =
                    VpnUtil.intersection(VpnUtil.getRts(vpnInstanceOpDataEntry, VpnTarget.VrfRTType.ImportExtcommunity),
                        VpnUtil.getRts(input, VpnTarget.VrfRTType.ExportExtcommunity));
            return Iterators.size(commonRTs.iterator()) > 0;
        };

        vpnsToExportRoute =
                vpnUtil.getAllVpnInstanceOpData().stream().filter(excludeVpn).filter(matchRTs).collect(
                        Collectors.toList());
        return vpnsToExportRoute;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    void handleVpnsExportingRoutes(String vpnName, String vpnRd) {
        List<VpnInstanceOpDataEntry> vpnsToExportRoute = getVpnsExportingMyRoute(vpnName);
        for (VpnInstanceOpDataEntry vpn : vpnsToExportRoute) {
            List<VrfEntry> vrfEntries = vpnUtil.getAllVrfEntries(vpn.getVrfId());
            if (vrfEntries != null) {
                LoggingFutures.addErrorLogging(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, confTx -> {
                        for (VrfEntry vrfEntry : vrfEntries) {
                            try {
                                if (!FibHelper.isControllerManagedNonInterVpnLinkRoute(
                                    RouteOrigin.value(vrfEntry.getOrigin()))) {
                                    LOG.info("handleVpnsExportingRoutes: vrfEntry with rd {} prefix {}"
                                            + " is not a controller managed non intervpn link route. Ignoring.",
                                        vpn.getVrfId(), vrfEntry.getDestPrefix());
                                    continue;
                                }
                                String prefix = vrfEntry.getDestPrefix();
                                String gwMac = vrfEntry.getGatewayMacAddress();
                                vrfEntry.nonnullRoutePaths().values().forEach(routePath -> {
                                    String nh = routePath.getNexthopAddress();
                                    Uint32 label = routePath.getLabel();
                                    if (FibHelper.isControllerManagedVpnInterfaceRoute(RouteOrigin.value(
                                        vrfEntry.getOrigin()))) {
                                        LOG.info(
                                            "handleVpnsExportingRoutesImporting: Importing fib entry rd {}"
                                                + " prefix {} nexthop {} label {} to vpn {} vpnRd {}",
                                            vpn.getVrfId(), prefix, nh, label, vpnName, vpnRd);
                                        fibManager.addOrUpdateFibEntry(vpnRd, null /*macAddress*/, prefix,
                                            Collections.singletonList(nh), VrfEntry.EncapType.Mplsgre, label,
                                                Uint32.ZERO /*l3vni*/, gwMac, vpn.getVrfId(), RouteOrigin.SELF_IMPORTED,
                                                null, null, confTx);
                                    } else {
                                        LOG.info("handleVpnsExportingRoutes: Importing subnet route fib entry"
                                                + " rd {} prefix {} nexthop {} label {} to vpn {} vpnRd {}",
                                            vpn.getVrfId(), prefix, nh, label, vpnName, vpnRd);
                                        SubnetRoute route = vrfEntry.augmentation(SubnetRoute.class);
                                        importSubnetRouteForNewVpn(vpnRd, prefix, nh, label, route, vpn.getVrfId(),
                                            confTx);
                                    }
                                });
                            } catch (RuntimeException e) {
                                LOG.error("getNextHopAddressList: Exception occurred while importing route with rd {}"
                                        + " prefix {} routePaths {} to vpn {} vpnRd {}", vpn.getVrfId(),
                                    vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(), vpnName, vpnRd);
                            }
                        }
                    }), LOG, "Error handing VPN exporting routes");
            } else {
                LOG.info("getNextHopAddressList: No vrf entries to import from vpn {} with rd {} to vpn {} with rd {}",
                        vpn.getVpnInstanceName(), vpn.getVrfId(), vpnName, vpnRd);
            }
        }
    }

    @Override
    public void remove(InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
        LOG.trace("Received VpnInterface remove event: vpnInterface={}", vpnInterface);
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class);
        final String interfaceName = key.getName();
        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnInterface.nonnullVpnInstanceNames().values()) {
            String vpnName = vpnInterfaceVpnInstance.getVpnName();
            final String rd = vpnManager.getVpnRd(dataBroker, vpnName);
            counter = vpnIfCounter.label("vpninterface.remove").label("rd.interface").label(rd + "." + interfaceName);
            counter.increment();
            removeVpnInterfaceCall(identifier, vpnInterface, vpnName, interfaceName);
        }
    }

    private void removeVpnInterfaceCall(final InstanceIdentifier<VpnInterface> identifier,
                                final VpnInterface vpnInterface, final String vpnName,
                                final String interfaceName) {
        if (Boolean.TRUE.equals(vpnInterface.isRouterInterface())) {
            jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getName(), () -> {
                ListenableFuture<?> future =
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, confTx -> {
                        deleteFibEntryForRouterInterface(vpnInterface, confTx, vpnName);
                        LOG.info("remove: Router interface {} for vpn {}", interfaceName, vpnName);
                    });
                LoggingFutures.addErrorLogging(future, LOG, "Error removing call for interface {} on VPN {}",
                        vpnInterface.getName(), vpnName);
                return Collections.singletonList(future);
            }, DJC_MAX_RETRIES);
        } else {
            Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceName);
            removeVpnInterfaceFromVpn(identifier, vpnInterface, vpnName, interfaceName, interfaceState);
        }
    }

    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    private void removeVpnInterfaceFromVpn(final InstanceIdentifier<VpnInterface> identifier,
                                           final VpnInterface vpnInterface, final String vpnName,
                                           final String interfaceName, final Interface interfaceState) {
        LOG.info("remove: VPN Interface remove event - intfName {} vpn {} dpn {}" ,vpnInterface.getName(),
                vpnName, vpnInterface.getDpnId());
        removeInterfaceFromUnprocessedList(identifier, vpnInterface);
        jobCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> {
                List<ListenableFuture<?>> futures = new ArrayList<>(3);
                List<String> ipAddressList = new ArrayList<String>();
                ListenableFuture<?> configFuture = txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    writeConfigTxn -> futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL,
                        writeOperTxn -> futures.add(
                            txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, writeInvTxn -> {
                                LOG.info("remove: - intfName {} onto vpnName {} running config-driven",
                                        interfaceName, vpnName);
                                Uint64 dpId;
                                int ifIndex;
                                String gwMacAddress;
                                InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId =
                                        VpnUtil.getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                                Optional<VpnInterfaceOpDataEntry> optVpnInterface;
                                try {
                                    optVpnInterface = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                            LogicalDatastoreType.OPERATIONAL, interfaceId);
                                } catch (InterruptedException | ExecutionException e) {
                                    LOG.error("remove: Failed to read data store for interface {} vpn {}",
                                            interfaceName, vpnName);
                                    return;
                                }
                                if (interfaceState != null) {
                                    try {
                                        dpId = InterfaceUtils.getDpIdFromInterface(interfaceState);
                                    } catch (NumberFormatException | IllegalStateException e) {
                                        LOG.error("remove: Unable to retrieve dpnId from interface operational"
                                                        + " data store for interface {} on dpn {} for vpn {} Fetching"
                                                        + " from vpn interface op data store. ", interfaceName,
                                                vpnInterface.getDpnId(), vpnName, e);
                                        dpId = Uint64.ZERO;
                                    }
                                    ifIndex = interfaceState.getIfIndex();
                                    gwMacAddress = interfaceState.getPhysAddress().getValue();
                                } else {
                                    LOG.info("remove: Interface state not available for {}. Trying to fetch data"
                                            + " from vpn interface op.", interfaceName);
                                    if (optVpnInterface.isPresent()) {
                                        VpnInterfaceOpDataEntry vpnOpInterface = optVpnInterface.get();
                                        dpId = vpnOpInterface.getDpnId();
                                        ifIndex = vpnOpInterface.getLportTag().intValue();
                                        gwMacAddress = vpnOpInterface.getGatewayMacAddress();
                                    } else {
                                        LOG.error("remove: Handling removal of VPN interface {} for vpn {} skipped"
                                                + " as interfaceState and vpn interface op is not"
                                                + " available", interfaceName, vpnName);
                                        return;
                                    }
                                }
                                processVpnInterfaceDown(dpId, interfaceName, ifIndex, gwMacAddress,
                                        optVpnInterface.isPresent() ? optVpnInterface.get() : null, false,
                                        FIB_EVENT_SOURCE_CONFIG_VPN_INTERFACE, ipAddressList,
                                        writeConfigTxn, writeOperTxn, writeInvTxn);
                                LOG.info(
                                        "remove: Removal of vpn interface {} on dpn {} for vpn {} processed "
                                                + "successfully",
                                        interfaceName, vpnInterface.getDpnId(), vpnName);
                            })))));
                futures.add(configFuture);
                Futures.addCallback(configFuture, new PostVpnInterfaceWorker(
                        interfaceName, ipAddressList,false, "Config"), MoreExecutors.directExecutor());
                return futures;
            }, DJC_MAX_RETRIES);
    }

    protected void processVpnInterfaceDown(Uint64 dpId,
                                           String interfaceName,
                                           int lportTag,
                                           String gwMac,
                                           VpnInterfaceOpDataEntry vpnOpInterface,
                                           boolean isInterfaceStateDown,
                                           String eventSource,
                                           List<String> ipAddressList,
                                           TypedWriteTransaction<Configuration> writeConfigTxn,
                                           TypedWriteTransaction<Operational> writeOperTxn,
                                           TypedReadWriteTransaction<Configuration> writeInvTxn)
            throws ExecutionException, InterruptedException {
        if (vpnOpInterface == null) {
            LOG.error("processVpnInterfaceDown: Unable to process delete/down for interface {} on dpn {}"
                    + " as it is not available in operational data store", interfaceName, dpId);
            return;
        }
        final String vpnName = vpnOpInterface.getVpnInstanceName();
        InstanceIdentifier<VpnInterfaceOpDataEntry> identifier = VpnUtil.getVpnInterfaceOpDataEntryIdentifier(
                                                    interfaceName, vpnName);
        if (!isInterfaceStateDown) {
            final Uint32 vpnId = vpnUtil.getVpnId(vpnName);
            vpnUtil.scheduleVpnInterfaceForRemoval(interfaceName, dpId, vpnName,  null);
            final boolean isBgpVpnInternetVpn = vpnUtil.isBgpVpnInternet(vpnName);
            removeAdjacenciesFromVpn(dpId, lportTag, interfaceName, vpnName,
                    vpnId, gwMac, eventSource, ipAddressList, writeConfigTxn, writeOperTxn, writeInvTxn);
            if (interfaceManager.isExternalInterface(interfaceName)) {
                processExternalVpnInterface(interfaceName, vpnName, dpId, lportTag,
                    NwConstants.DEL_FLOW);
            }
            if (!isBgpVpnInternetVpn) {
                vpnUtil.unbindService(interfaceName, isInterfaceStateDown);
            }
            LOG.info("processVpnInterfaceDown: Unbound vpn service from interface {} on dpn {} for vpn {}"
                    + " successful", interfaceName, dpId, vpnName);
        } else {
            // Interface is retained in the DPN, but its Link Down.
            // Only withdraw the prefixes for this interface from BGP
            withdrawAdjacenciesForVpnFromBgp(identifier, vpnName, interfaceName, writeConfigTxn, writeOperTxn);
        }
    }

    private void removeAdjacenciesFromVpn(final Uint64 dpnId, final int lportTag, final String interfaceName,
                                          final String vpnName, final Uint32 vpnId, String gwMac,
                                          String source, List<String> ipAddressList,
                                          TypedWriteTransaction<Configuration> writeConfigTxn,
                                          TypedWriteTransaction<Operational> writeOperTxn,
                                          TypedReadWriteTransaction<Configuration> writeInvTxn)
            throws ExecutionException, InterruptedException {
        //Read NextHops
        try {
            InstanceIdentifier<VpnInterfaceOpDataEntry> identifier = VpnUtil
                    .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
            Optional<VpnInterfaceOpDataEntry> vpnInterfaceOpDataEnteryOptional =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.OPERATIONAL, identifier);
            boolean isNonPrimaryAdjIp = false;
            String primaryRd = vpnUtil.getVpnRd(vpnName);
            LOG.info("removeAdjacenciesFromVpn: For interface {} on dpn {} RD recovered for vpn {} as rd {}",
                    interfaceName, dpnId, vpnName, primaryRd);
            if (!vpnInterfaceOpDataEnteryOptional.isPresent()) {
                LOG.error("removeAdjacenciesFromVpn: VpnInterfaceOpDataEntry-Oper DS is absent for Interface {} "
                        + "on vpn {} dpn {}", interfaceName, vpnName, dpnId);
                return;
            }
            AdjacenciesOp adjacencies = vpnInterfaceOpDataEnteryOptional.get().augmentation(AdjacenciesOp.class);

            if (adjacencies != null && adjacencies.getAdjacency() != null) {
                Map<AdjacencyKey, Adjacency> nextHopsMap = adjacencies.nonnullAdjacency();
                LOG.info("removeAdjacenciesFromVpn: NextHops for interface {} on dpn {} for vpn {} are {}",
                        interfaceName, dpnId, vpnName, nextHopsMap);
                for (Adjacency nextHop : nextHopsMap.values()) {
                    if (nextHop.isPhysNetworkFunc()) {
                        LOG.info("removeAdjacenciesFromVpn: Removing PNF FIB entry rd {} prefix {}",
                                nextHop.getSubnetId().getValue(), nextHop.getIpAddress());
                        fibManager.removeFibEntry(nextHop.getSubnetId().getValue(), nextHop.getIpAddress(),
                                interfaceName, source, null/*writeCfgTxn*/);
                    } else {
                        String rd = nextHop.getVrfId();
                        List<String> nhList;
                        if (nextHop.getAdjacencyType() != AdjacencyType.PrimaryAdjacency) {
                            nhList = getNextHopForNonPrimaryAdjacency(nextHop, vpnName, dpnId, interfaceName);
                            isNonPrimaryAdjIp = Boolean.TRUE;
                        } else {
                            // This is a primary adjacency
                            nhList = nextHop.getNextHopIpList() != null ? nextHop.getNextHopIpList()
                                    : emptyList();
                            removeGwMacAndArpResponderFlows(nextHop, vpnId, dpnId, lportTag, gwMac,
                                    vpnInterfaceOpDataEnteryOptional.get().getGatewayIpAddress(),
                                    interfaceName, writeInvTxn);
                            isNonPrimaryAdjIp = Boolean.FALSE;
                        }
                        if (!nhList.isEmpty()) {
                            if (Objects.equals(primaryRd, vpnName)) {
                                //this is an internal vpn - the rd is assigned to the vpn instance name;
                                //remove from FIB directly
                                nhList.forEach(removeAdjacencyFromInternalVpn(nextHop, vpnName,
                                        interfaceName, source, dpnId, writeConfigTxn, writeOperTxn));
                            } else {
                                removeAdjacencyFromBgpvpn(nextHop, nhList, vpnName, primaryRd, dpnId, rd,
                                        interfaceName, isNonPrimaryAdjIp, source, writeConfigTxn, writeOperTxn);
                            }
                        } else {
                            LOG.error("removeAdjacenciesFromVpn: nextHop empty for ip {} rd {} adjacencyType {}"
                                            + " interface {}", nextHop.getIpAddress(), rd,
                                    nextHop.getAdjacencyType().toString(), interfaceName);
                            bgpManager.withdrawPrefixIfPresent(rd, nextHop.getIpAddress());
                            fibManager.removeFibEntry(primaryRd, nextHop.getIpAddress(),
                                    interfaceName, source, writeConfigTxn);
                        }
                    }
                    String ip = nextHop.getIpAddress().split("/")[0];
                    ipAddressList.add(ip);
                    LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, ip);
                    if (vpnVipToPort != null && vpnVipToPort.getPortName().equals(interfaceName)) {
                        vpnUtil.removeLearntVpnVipToPort(vpnName, ip, null);
                        LOG.info("removeAdjacenciesFromVpn: VpnInterfaceManager removed LearntVpnVipToPort entry"
                                 + " for Interface {} ip {} on dpn {} for vpn {}",
                                vpnVipToPort.getPortName(), ip, dpnId, vpnName);
                    }
                    // Remove the MIP-IP from VpnPortIpToPort.
                    if (isNonPrimaryAdjIp) {
                        VpnPortipToPort persistedIp = vpnUtil.getVpnPortipToPort(vpnName, ip);
                        if (persistedIp != null && persistedIp.isLearntIp()
                                && persistedIp.getPortName().equals(interfaceName)) {
                            VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, ip, null);
                            LOG.info(
                                    "removeAdjacenciesFromVpn: Learnt-IP: {} interface {} of vpn {} removed "
                                            + "from VpnPortipToPort",
                                    persistedIp.getPortFixedip(), persistedIp.getPortName(), vpnName);
                        }
                    }
                    VpnPortipToPort vpnPortipToPort = vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnName, ip);
                    if (vpnPortipToPort != null) {
                        VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, ip, null);
                        LOG.info("removeAdjacenciesFromVpn: VpnInterfaceManager removed vpnPortipToPort entry for "
                                 + "Interface {} ip {} on dpn {} for vpn {}",
                            vpnPortipToPort.getPortName(), ip, dpnId, vpnName);
                    }
                }
            } else {
                // this vpn interface has no more adjacency left, so clean up the vpn interface from Operational DS
                LOG.info("removeAdjacenciesFromVpn: Vpn Interface {} on vpn {} dpn {} has no adjacencies."
                        + " Removing it.", interfaceName, vpnName, dpnId);
                writeOperTxn.delete(identifier);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("removeAdjacenciesFromVpn: Failed to read data store for interface {} dpn {} vpn {}",
                    interfaceName, dpnId, vpnName);
        }
    }

    private Consumer<String> removeAdjacencyFromInternalVpn(Adjacency nextHop, String vpnName,
                                                            String interfaceName, String source, Uint64 dpnId,
                                                            TypedWriteTransaction<Configuration> writeConfigTxn,
                                                            TypedWriteTransaction<Operational> writeOperTx) {
        return (nh) -> {
            String primaryRd = vpnUtil.getVpnRd(vpnName);
            String prefix = nextHop.getIpAddress();
            String vpnNamePrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
            LOG.info("remove adjacencies for nexthop {} vpnName {} interfaceName {} dpnId {}",
                    nextHop, vpnName, interfaceName, dpnId);
            // FIXME: separate this out somehow?
            final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnNamePrefixKey);
            lock.lock();
            try {
                if (vpnUtil.removeOrUpdateDSForExtraRoute(vpnName, primaryRd, dpnId.toString(), interfaceName,
                        prefix, nextHop.getNextHopIpList().get(0), nh, writeOperTx)) {
                    //If extra-route is present behind at least one VM, then do not remove or update
                    //fib entry for route-path representing that CSS nexthop, just update vpntoextraroute and
                    //prefixtointerface DS
                    return;
                }
                fibManager.removeOrUpdateFibEntry(vpnName, nextHop.getIpAddress(), nh, interfaceName, source,
                        writeConfigTxn);
            } finally {
                lock.unlock();
            }
            LOG.info("removeAdjacenciesFromVpn: removed/updated FIB with rd {} prefix {}"
                            + " nexthop {} for interface {} source {} on dpn {} for internal vpn {}",
                    vpnName, nextHop.getIpAddress(), nh, interfaceName, source, dpnId, vpnName);
        };
    }

    private void removeAdjacencyFromBgpvpn(Adjacency nextHop, List<String> nhList, String vpnName, String primaryRd,
                                           Uint64 dpnId, String rd, String interfaceName, boolean isNonPrimaryAdjIp,
                                           String source, TypedWriteTransaction<Configuration> writeConfigTxn,
                                           TypedWriteTransaction<Operational> writeOperTx) {
        List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                vpnUtil.getVpnsImportingMyRoute(vpnName);
        nhList.forEach((nh) -> {
            //IRT: remove routes from other vpns importing it
            if (isNonPrimaryAdjIp) {
                removeLearntPrefixFromBGP(rd, nextHop.getIpAddress(), nh, interfaceName, source, writeConfigTxn);
            } else {
                vpnManager.removePrefixFromBGP(vpnName, primaryRd, rd, interfaceName, nextHop.getIpAddress(),
                        nextHop.getNextHopIpList().get(0), nh, dpnId, writeConfigTxn, writeOperTx);
            }
            for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
                String vpnRd = vpn.getVrfId();
                if (vpnRd != null) {
                    fibManager.removeOrUpdateFibEntry(vpnRd,
                            nextHop.getIpAddress(), nh, interfaceName, source, writeConfigTxn);
                    LOG.info("removeAdjacenciesFromVpn: Removed Exported route with rd {}"
                                    + " prefix {} nextHop {} from VPN {} parentVpn {}"
                                    + " for interface {} on dpn {}", vpnRd, nextHop.getIpAddress(), nh,
                            vpn.getVpnInstanceName(), vpnName, interfaceName, dpnId);
                }
            }
        });
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeLearntPrefixFromBGP(String rd, String prefix, String nextHop,
                                           String interfaceName,
                                           String source, TypedWriteTransaction<Configuration> writeConfigTxn) {
        try {
            if (!fibManager.checkFibEntryExist(dataBroker, rd, prefix, nextHop)) {
                LOG.info("removeLearntPrefixFromBGP: IP {} with nexthop {} rd {} is already removed.Ignoring this"
                        + " operation", prefix, nextHop, rd);
                return;
            }
            LOG.info("removeLearntPrefixFromBGP: VPN WITHDRAW: Removing Fib Entry rd {} prefix {} nexthop {}",
                    rd, prefix, nextHop);
            fibManager.removeOrUpdateFibEntry(rd, prefix, nextHop, interfaceName, source, writeConfigTxn);
            bgpManager.withdrawPrefix(rd, prefix); // TODO: Might be needed to include nextHop here
            LOG.info("removeLearntPrefixFromBGP: VPN WITHDRAW: Removed Fib Entry rd {} prefix {} nexthop {}",
                    rd, prefix, nextHop);
        } catch (Exception e) {
            LOG.error("removeLearntPrefixFromBGP: Delete prefix {} rd {} nextHop {} failed", prefix, rd, nextHop, e);
        }
    }

    private void removeGwMacAndArpResponderFlows(Adjacency nextHop, Uint32 vpnId, Uint64 dpnId,
                                                 int lportTag, String gwMac, String gwIp, String interfaceName,
                                                 TypedReadWriteTransaction<Configuration> writeInvTxn)
            throws ExecutionException, InterruptedException {
        final Uuid subnetId = nextHop.getSubnetId();
        if (nextHop.getSubnetGatewayMacAddress() == null) {
            // A valid mac-address was not available for this subnet-gateway-ip
            // So a connected-mac-address was used for this subnet and we need
            // to remove the flows for the same here from the L3_GW_MAC_TABLE.
            vpnUtil.setupGwMacIfExternalVpn(dpnId, interfaceName, vpnId, writeInvTxn, NwConstants.DEL_FLOW, gwMac);
        }
        arpResponderHandler.removeArpResponderFlow(dpnId, lportTag, interfaceName, gwIp,
                subnetId);
    }

    private List<String> getNextHopForNonPrimaryAdjacency(Adjacency nextHop, String vpnName, Uint64 dpnId,
                                                  String interfaceName) {
        // This is either an extra-route (or) a learned IP via subnet-route
        List<String> nhList = null;
        String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
        if (nextHopIp == null || nextHopIp.isEmpty()) {
            LOG.error("removeAdjacenciesFromVpn: Unable to obtain nextHopIp for"
                            + " extra-route/learned-route in rd {} prefix {} interface {} on dpn {}"
                            + " for vpn {}", nextHop.getVrfId(), nextHop.getIpAddress(), interfaceName, dpnId,
                    vpnName);
            nhList = emptyList();
        } else {
            nhList = Collections.singletonList(nextHopIp);
        }
        return nhList;
    }

    private Optional<String> getMacAddressForSubnetIp(String vpnName, String ifName, String ipAddress) {
        VpnPortipToPort gwPort = vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnName, ipAddress);
        //Check if a router gateway interface is available for the subnet gw is so then use Router interface
        // else use connected interface
        if (gwPort != null && gwPort.isSubnetIp()) {
            LOG.info("getGatewayMacAddressForSubnetIp: Retrieved gw Mac as {} for ip {} interface {} vpn {}",
                    gwPort.getMacAddress(), ipAddress, ifName, vpnName);
            return Optional.of(gwPort.getMacAddress());
        }
        return Optional.empty();
    }

    @Override
    public void update(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface original,
        final VpnInterface update) {
        LOG.trace("Received VpnInterface update event: original={}, update={}", original, update);
        LOG.info("update: VPN Interface update event - intfName {} on dpn {} oldVpn {} newVpn {}", update.getName(),
                update.getDpnId(), original.getVpnInstanceNames(), update.getVpnInstanceNames());
        if (original.equals(update)) {
            LOG.info("update: original {} update {} are same. No update required.", original, update);
            return;
        }
        final String vpnInterfaceName = update.getName();
        final Uint64 dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        LOG.info("VPN Interface update event - intfName {}", vpnInterfaceName);
        //handles switching between <internal VPN - external VPN>
        jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterfaceName, () -> {
            List<ListenableFuture<?>> futures = new ArrayList<>();
            if (handleVpnInstanceUpdateForVpnInterface(identifier, original, update, futures)) {
                LOG.info("update: handled Instance update for VPNInterface {} on dpn {} from oldVpn(s) {} "
                                + "to newVpn(s) {}",
                        original.getName(), dpnId,
                        VpnHelper.getVpnInterfaceVpnInstanceNamesString(
                                new ArrayList<VpnInstanceNames>(original.nonnullVpnInstanceNames().values())),
                        VpnHelper.getVpnInterfaceVpnInstanceNamesString(
                                new ArrayList<VpnInstanceNames>(update.nonnullVpnInstanceNames().values())));
                return emptyList();
            }
            updateVpnInstanceAdjChange(original, update, vpnInterfaceName, futures);
            return futures;
        });
    }

    private boolean handleVpnInstanceUpdateForVpnInterface(InstanceIdentifier<VpnInterface> identifier,
                                                           VpnInterface original, VpnInterface update,
                                                           List<ListenableFuture<?>> futures) {
        boolean isVpnInstanceUpdate = false;
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class);
        final String interfaceName = key.getName();
        List<String> oldVpnList = VpnUtil.getVpnListForVpnInterface(original);
        List<String> oldVpnListCopy = new ArrayList<>();
        oldVpnListCopy.addAll(oldVpnList);
        List<String> newVpnList = VpnUtil.getVpnListForVpnInterface(update);
        List<String> newVpnListCopy = new ArrayList<>();
        newVpnListCopy.addAll(newVpnList);

        oldVpnList.removeAll(newVpnList);
        newVpnList.removeAll(oldVpnListCopy);
        //This block will execute only on if there is a change in the VPN Instance.
        if (!oldVpnList.isEmpty() || !newVpnList.isEmpty()) {
            /*
             * Internet BGP-VPN Instance update with single router:
             * ====================================================
             * In this case single VPN Interface will be part of maximum 2 VPN Instance only.
             *     1st VPN Instance : router VPN or external BGP-VPN.
             *     2nd VPN Instance : Internet BGP-VPN(router-gw update/delete) for public network access.
             *
             * VPN Instance UPDATE:
             * oldVpnList = 0 and newVpnList = 1 (Internet BGP-VPN)
             * oldVpnList = 1 and newVpnList = 0 (Internet BGP-VPN)
             *
             * External BGP-VPN Instance update with single router:
             * ====================================================
             * In this case single VPN interface will be part of maximum 1 VPN Instance only.
             *
             * Updated VPN Instance will be always either internal router VPN to
             * external BGP-VPN or external BGP-VPN to internal router VPN swap.
             *
             * VPN Instance UPDATE:
             * oldVpnList = 1 and newVpnList = 1 (router VPN to Ext-BGPVPN)
             * oldVpnList = 1 and newVpnList = 1 (Ext-BGPVPN to router VPN)
             *
             * Dual Router VPN Instance Update:
             * ================================
             * In this case single VPN interface will be part of maximum 3 VPN Instance only.
             *
             * 1st VPN Instance : router VPN or external BGP-VPN-1.
             * 2nd VPN Instance : router VPN or external BGP-VPN-2.
             * 3rd VPN Instance : Internet BGP-VPN(router-gw update/delete) for public network access.
             *
             * Dual Router --> Associated with common external BGP-VPN Instance.
             * 1st router and 2nd router are getting associated with single External BGP-VPN
             * 1) add 1st router to external bgpvpn --> oldVpnList=1, newVpnList=1;
             * 2) add 2nd router to the same external bgpvpn --> oldVpnList=1, newVpnList=0
             * In this case, we need to call removeVpnInterfaceCall() followed by addVpnInterfaceCall()
             *
             *
             */
            isVpnInstanceUpdate = true;
            if (VpnUtil.isDualRouterVpnUpdate(oldVpnListCopy, newVpnListCopy)) {
                if ((oldVpnListCopy.size() == 2 || oldVpnListCopy.size() == 3)
                        && oldVpnList.size() == 1 && newVpnList.isEmpty()) {
                    //Identify the external BGP-VPN Instance and pass that value as newVpnList
                    List<String> externalBgpVpnList = new ArrayList<>();
                    for (String newVpnName : newVpnListCopy) {
                        String primaryRd = vpnUtil.getPrimaryRd(newVpnName);
                        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnUtil.getVpnInstanceOpData(primaryRd);
                        if (vpnInstanceOpDataEntry.getBgpvpnType() == VpnInstanceOpDataEntry
                                .BgpvpnType.BGPVPN) {
                            externalBgpVpnList.add(newVpnName);
                            break;
                        }
                    }
                    //This call will execute removeVpnInterfaceCall() followed by addVpnInterfaceCall()
                    updateVpnInstanceChange(identifier, interfaceName, original, update, oldVpnList,
                            externalBgpVpnList, oldVpnListCopy, futures);

                } else if ((oldVpnListCopy.size() == 2 || oldVpnListCopy.size() == 3)
                        && oldVpnList.isEmpty() && newVpnList.size() == 1) {
                    //Identify the router VPN Instance and pass that value as oldVpnList
                    List<String> routerVpnList = new ArrayList<>();
                    for (String newVpnName : newVpnListCopy) {
                        String primaryRd = vpnUtil.getPrimaryRd(newVpnName);
                        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnUtil.getVpnInstanceOpData(primaryRd);
                        if (vpnInstanceOpDataEntry.getBgpvpnType() == VpnInstanceOpDataEntry
                                .BgpvpnType.BGPVPN) {
                            routerVpnList.add(newVpnName);
                            break;
                        }
                    }
                    //This call will execute removeVpnInterfaceCall() followed by addVpnInterfaceCall()
                    updateVpnInstanceChange(identifier, interfaceName, original, update, routerVpnList,
                            newVpnList, oldVpnListCopy, futures);

                } else {
                    //Handle remaining use cases.
                    updateVpnInstanceChange(identifier, interfaceName, original, update, oldVpnList, newVpnList,
                            oldVpnListCopy, futures);
                }
            } else {
                updateVpnInstanceChange(identifier, interfaceName, original, update, oldVpnList, newVpnList,
                        oldVpnListCopy, futures);
            }
        }
        return isVpnInstanceUpdate;
    }

    private void updateVpnInstanceChange(InstanceIdentifier<VpnInterface> identifier, String interfaceName,
                                         VpnInterface original, VpnInterface update, List<String> oldVpnList,
                                         List<String> newVpnList, List<String> oldVpnListCopy,
                                         List<ListenableFuture<?>> futures) {
        final Adjacencies origAdjs = original.augmentation(Adjacencies.class);
        final List<Adjacency> oldAdjs = origAdjs != null && origAdjs.getAdjacency() != null
                ? new ArrayList<Adjacency>(origAdjs.getAdjacency().values()) : new ArrayList<>();
        final Adjacencies updateAdjs = update.augmentation(Adjacencies.class);
        final List<Adjacency> newAdjs = updateAdjs != null && updateAdjs.getAdjacency() != null
                ? new ArrayList<Adjacency>(updateAdjs.getAdjacency().values()) : new ArrayList<>();

        boolean isOldVpnRemoveCallExecuted = false;
        for (String oldVpnName : oldVpnList) {
            LOG.info("updateVpnInstanceChange: VPN Interface update event - intfName {} "
                    + "remove from vpnName {} ", interfaceName, oldVpnName);
            removeVpnInterfaceCall(identifier, original, oldVpnName, interfaceName);
            LOG.info("updateVpnInstanceChange: Processed Remove for update on VPNInterface"
                            + " {} upon VPN update from old vpn {} to newVpn(s) {}", interfaceName, oldVpnName,
                    newVpnList);
            isOldVpnRemoveCallExecuted = true;
        }
        //Wait for previous interface bindings to be removed
        if (isOldVpnRemoveCallExecuted && !newVpnList.isEmpty()) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                LOG.error("updateVpnInstanceChange: InterruptedException caught for interface {}", interfaceName, e);
            }
        }
        for (String newVpnName : newVpnList) {
            String primaryRd = vpnUtil.getPrimaryRd(newVpnName);
            counter = vpnIfCounter.label("vpninterface.update").label("rd.interface")
                    .label(primaryRd + "." + update.getName());
            counter.increment();
            if (!vpnUtil.isVpnPendingDelete(primaryRd)) {
                LOG.info("updateVpnInstanceChange: VPN Interface update event - intfName {} "
                        + "onto vpnName {} ", interfaceName, newVpnName);
                addVpnInterfaceCall(identifier, update, oldAdjs, newAdjs, newVpnName);
                LOG.info("updateVpnInstanceChange: Processed Add for update on VPNInterface {}"
                                + "from oldVpn(s) {} to newVpn {} ",
                        interfaceName, oldVpnListCopy, newVpnName);
                /* This block will execute only if V6 subnet is associated with internet BGP-VPN.
                 * Use Case:
                 *     In Dual stack network, first V4 subnet only attached to router and router is associated
                 *     with internet BGP-VPN(router-gw). At this point VPN interface is having only router vpn instance.
                 *     Later V6 subnet is added to router, at this point existing VPN interface will get updated
                 *     with Internet BGP-VPN instance(Note: Internet BGP-VPN Instance update in vpn interface
                 *     is applicable for only on V6 subnet is added to router). newVpnList = Contains only Internet
                 *     BGP-VPN Instance. So we required V6 primary adjacency info needs to be populated onto
                 *     router VPN as well as Internet BGP-VPN.
                 *
                 *     addVpnInterfaceCall() --> It will create V6 Adj onto Internet BGP-VPN only.
                 *     updateVpnInstanceAdjChange() --> This method call is needed for second primary V6 Adj
                 *                                       update in existing router VPN instance.
                 */
                if (vpnUtil.isBgpVpnInternet(newVpnName)) {
                    LOG.info("updateVpnInstanceChange: VPN Interface {} with new Adjacency {} in existing "
                            + "VPN instance {}", interfaceName, newAdjs, original.getVpnInstanceNames());
                    updateVpnInstanceAdjChange(original, update, interfaceName, futures);
                }
            } else {
                LOG.info("updateVpnInstanceChange: failed to Add for update on VPNInterface {} from oldVpn(s) {} to "
                                + "newVpn {} as the new vpn does not exist in oper DS or it is in PENDING_DELETE state",
                        interfaceName, oldVpnListCopy, newVpnName);
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<ListenableFuture<?>> updateVpnInstanceAdjChange(VpnInterface original, VpnInterface update,
                                                                    String vpnInterfaceName,
                                                                    List<ListenableFuture<?>> futures) {
        final Adjacencies origAdjs = original.augmentation(Adjacencies.class);
        final List<Adjacency> oldAdjs = origAdjs != null && origAdjs.getAdjacency()
                != null ? new ArrayList<Adjacency>(origAdjs.getAdjacency().values()) : new ArrayList<>();
        final Adjacencies updateAdjs = update.augmentation(Adjacencies.class);
        final List<Adjacency> newAdjs = updateAdjs != null && updateAdjs.getAdjacency()
                != null ? new ArrayList<Adjacency>(updateAdjs.getAdjacency().values()) : new ArrayList<>();

        final Uint64 dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        for (VpnInstanceNames vpnInterfaceVpnInstance : update.nonnullVpnInstanceNames().values()) {
            String newVpnName = vpnInterfaceVpnInstance.getVpnName();
            List<Adjacency> copyNewAdjs = new ArrayList<>(newAdjs);
            List<Adjacency> copyOldAdjs = new ArrayList<>(oldAdjs);
            String primaryRd = vpnUtil.getPrimaryRd(newVpnName);
            if (!vpnUtil.isVpnPendingDelete(primaryRd)) {
                // TODO Deal with sequencing — the config tx must only submitted if the oper tx goes in
                //set of prefix used as entry in prefix-to-interface datastore
                // is prerequisite for refresh Fib to avoid race condition leading to missing remote next hop
                // in bucket actions on bgp-vpn delete
                Set<String> prefixListForRefreshFib = new HashSet<>();
                ListenableFuture<?> configTxFuture = txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    confTx -> futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL,
                        operTx -> {
                            InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier =
                                VpnUtil.getVpnInterfaceOpDataEntryIdentifier(vpnInterfaceName, newVpnName);
                            LOG.info("VPN Interface update event-intfName {} onto vpnName {} running config-driven",
                                    update.getName(), newVpnName);
                            //handle both addition and removal of adjacencies
                            // currently, new adjacency may be an extra route
                            boolean isBgpVpnInternetVpn = vpnUtil.isBgpVpnInternet(newVpnName);
                            if (!oldAdjs.equals(newAdjs)) {
                                for (Adjacency adj : copyNewAdjs) {
                                    if (copyOldAdjs.contains(adj)) {
                                        copyOldAdjs.remove(adj);
                                    } else {
                                        // add new adjacency
                                        if (!isBgpVpnInternetVpn || vpnUtil.isAdjacencyEligibleToVpnInternet(adj)) {
                                            try {
                                                addNewAdjToVpnInterface(vpnInterfaceOpIdentifier, primaryRd, adj,
                                                    dpnId, operTx, confTx, confTx, prefixListForRefreshFib);
                                            } catch (RuntimeException e) {
                                                LOG.error("Failed to add adjacency {} to vpn interface {} with"
                                                        + " dpnId {}", adj, vpnInterfaceName, dpnId, e);
                                            }
                                        }
                                        LOG.info("update: new Adjacency {} with nextHop {} label {} subnet {} "
                                            + " added to vpn interface {} on vpn {} dpnId {}",
                                            adj.getIpAddress(), adj.getNextHopIpList(), adj.getLabel(),
                                            adj.getSubnetId(), update.getName(), newVpnName, dpnId);
                                    }
                                }
                                for (Adjacency adj : copyOldAdjs) {
                                    if (!isBgpVpnInternetVpn || vpnUtil.isAdjacencyEligibleToVpnInternet(adj)) {
                                        if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency
                                            && !adj.isPhysNetworkFunc()) {
                                            delAdjFromVpnInterface(vpnInterfaceOpIdentifier, adj, dpnId, operTx,
                                                confTx);
                                            //remove FIB entry
                                            String vpnRd = vpnUtil.getVpnRd(newVpnName);
                                            LOG.debug("update: remove prefix {} from the FIB and BGP entry "
                                                + "for the Vpn-Rd {} ", adj.getIpAddress(), vpnRd);
                                            //remove BGP entry
                                            fibManager.removeFibEntry(vpnRd, adj.getIpAddress(),
                                                    vpnInterfaceName, null, confTx);
                                            if (vpnRd != null && !vpnRd.equalsIgnoreCase(newVpnName)) {
                                                bgpManager.withdrawPrefix(vpnRd, adj.getIpAddress());
                                            }
                                        } else {
                                            delAdjFromVpnInterface(vpnInterfaceOpIdentifier, adj, dpnId,
                                                operTx, confTx);
                                        }
                                    }
                                    LOG.info("update: Adjacency {} with nextHop {} label {} subnet {} removed from"
                                        + " vpn interface {} on vpn {}", adj.getIpAddress(), adj.getNextHopIpList(),
                                        adj.getLabel(), adj.getSubnetId(), update.getName(), newVpnName);
                                }
                            }
                        })));
                Futures.addCallback(configTxFuture, new VpnInterfaceCallBackHandler(primaryRd, prefixListForRefreshFib),
                    MoreExecutors.directExecutor());
                futures.add(configTxFuture);
                for (ListenableFuture<?> future : futures) {
                    LoggingFutures.addErrorLogging(future, LOG, "update: failed for interface {} on vpn {}",
                            update.getName(), update.getVpnInstanceNames());
                }
            } else {
                LOG.error("update: Ignoring update of vpnInterface {}, as newVpnInstance {} with primaryRd {}"
                        + " is already marked for deletion", vpnInterfaceName, newVpnName, primaryRd);
            }
        }
        return futures;
    }

    private void updateLabelMapper(Uint32 label, List<String> nextHopIpList) {
        final String labelStr = Preconditions.checkNotNull(label, "updateLabelMapper: label cannot be null or empty!")
                .toString();
        // FIXME: separate this out somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(labelStr);
        lock.lock();
        try {
            InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
                    .child(LabelRouteInfo.class, new LabelRouteInfoKey(label)).build();
            Optional<LabelRouteInfo> opResult = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, lriIid);
            if (opResult.isPresent()) {
                LabelRouteInfo labelRouteInfo =
                        new LabelRouteInfoBuilder(opResult.get()).setNextHopIpList(nextHopIpList).build();
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid,
                    labelRouteInfo, VpnUtil.SINGLE_TRANSACTION_BROKER_NO_RETRY);
            }
            LOG.info("updateLabelMapper: Updated label rotue info for label {} with nextHopList {}", label,
                    nextHopIpList);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("updateLabelMapper: Failed to read data store for label {} nexthopList {}", label,
                    nextHopIpList);
        } catch (TransactionCommitFailedException e) {
            LOG.error("updateLabelMapper: Failed to commit to data store for label {} nexthopList {}", label,
                    nextHopIpList);
        } finally {
            lock.unlock();
        }
    }

    public synchronized void importSubnetRouteForNewVpn(String rd, String prefix, String nextHop, Uint32 label,
        SubnetRoute route, String parentVpnRd, TypedWriteTransaction<Configuration> writeConfigTxn) {

        RouteOrigin origin = RouteOrigin.SELF_IMPORTED;
        VrfEntry vrfEntry = FibHelper.getVrfEntryBuilder(prefix, label, nextHop, origin, parentVpnRd)
                .addAugmentation(SubnetRoute.class, route).build();
        List<VrfEntry> vrfEntryList = Collections.singletonList(vrfEntry);
        InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).setVrfEntry(vrfEntryList).build();
        if (writeConfigTxn != null) {
            writeConfigTxn.mergeParentStructureMerge(vrfTableId, vrfTableNew);
        } else {
            vpnUtil.syncUpdate(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableNew);
        }
        LOG.info("SUBNETROUTE: importSubnetRouteForNewVpn: Created vrfEntry for rd {} prefix {} nexthop {} label {}"
                + " and elantag {}", rd, prefix, nextHop, label, route.getElantag());
    }

    protected void addNewAdjToVpnInterface(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, String primaryRd,
                                           Adjacency adj, Uint64 dpnId,
                                           TypedWriteTransaction<Operational> writeOperTxn,
                                           TypedWriteTransaction<Configuration> writeConfigTxn,
                                           TypedReadWriteTransaction<Configuration> writeInvTxn,
                                           Set<String> prefixListForRefreshFib)
            throws ExecutionException, InterruptedException {
        String interfaceName = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class).getName();
        String configVpnName = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class).getVpnInstanceName();
        try {
            Optional<VpnInterfaceOpDataEntry> optVpnInterface = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, identifier);
            if (optVpnInterface.isPresent()) {
                VpnInterfaceOpDataEntry currVpnIntf = optVpnInterface.get();
                String prefix = VpnUtil.getIpPrefix(adj.getIpAddress());
                List<String> ipAddress = new ArrayList<String>();
                ipAddress.add(prefix);
                String vpnName = currVpnIntf.getVpnInstanceName();
                VpnInstanceOpDataEntry vpnInstanceOpData = vpnUtil.getVpnInstanceOpData(primaryRd);
                InstanceIdentifier<AdjacenciesOp> adjPath = identifier.augmentation(AdjacenciesOp.class);
                Optional<AdjacenciesOp> optAdjacencies = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, adjPath);
                boolean isL3VpnOverVxLan = VpnUtil.isL3VpnOverVxLan(vpnInstanceOpData.getL3vni());
                VrfEntry.EncapType encapType = VpnUtil.getEncapType(isL3VpnOverVxLan);
                Uint32 l3vni = vpnInstanceOpData.getL3vni() == null ? Uint32.ZERO : vpnInstanceOpData.getL3vni();
                VpnPopulator populator = L3vpnRegistry.getRegisteredPopulator(encapType);
                List<Adjacency> adjacencies = new ArrayList<>();
                if (optAdjacencies.isPresent() && optAdjacencies.get().getAdjacency() != null) {
                    adjacencies.addAll(optAdjacencies.get().getAdjacency().values());
                }
                Uint32 vpnId = vpnUtil.getVpnId(vpnName);
                L3vpnInput input = new L3vpnInput().setNextHop(adj).setVpnName(vpnName)
                        .setInterfaceName(currVpnIntf.getName()).setPrimaryRd(primaryRd).setRd(primaryRd);
                Adjacency operationalAdjacency = null;
                //Handling dual stack neutron port primary adjacency
                if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency && !adj.isPhysNetworkFunc()) {
                    LOG.trace("addNewAdjToVpnInterface: Adding prefix {} to existing interface {} for vpn {}", prefix,
                            currVpnIntf.getName(), vpnName);
                    Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,
                            currVpnIntf.getName());
                    if (interfaceState != null) {
                        processVpnInterfaceAdjacencies(dpnId, currVpnIntf.getLportTag().intValue(), vpnName, primaryRd,
                            currVpnIntf.getName(), vpnId, writeConfigTxn, writeOperTxn, writeInvTxn, interfaceState,
                            prefixListForRefreshFib, FIB_EVENT_SOURCE_CONFIG_VPN_INTERFACE, ipAddress);
                    }
                }
                if (adj.getNextHopIpList() != null && !adj.getNextHopIpList().isEmpty()
                        && adj.getAdjacencyType() != AdjacencyType.PrimaryAdjacency) {
                    RouteOrigin origin = adj.getAdjacencyType() == AdjacencyType.LearntIp ? RouteOrigin.DYNAMIC
                            : RouteOrigin.STATIC;
                    String nh = adj.getNextHopIpList().get(0);
                    String vpnPrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
                    // FIXME: separate out to somehow?
                    final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnPrefixKey);
                    lock.lock();
                    try {
                        java.util.Optional<String> rdToAllocate = vpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(
                                vpnId, null, prefix, vpnName, nh, dpnId);
                        if (rdToAllocate.isPresent()) {
                            input.setRd(rdToAllocate.get());
                            operationalAdjacency = populator.createOperationalAdjacency(input);
                            int label = operationalAdjacency.getLabel().intValue();
                            vpnManager.addExtraRoute(vpnName, adj.getIpAddress(), nh, rdToAllocate.get(),
                                    currVpnIntf.getVpnInstanceName(), l3vni, origin,
                                    currVpnIntf.getName(), operationalAdjacency, encapType,
                                    prefixListForRefreshFib, writeConfigTxn);
                            LOG.info("addNewAdjToVpnInterface: Added extra route ip {} nh {} rd {} vpnname {} label {}"
                                            + " Interface {} on dpn {}", adj.getIpAddress(), nh, rdToAllocate.get(),
                                    vpnName, label, currVpnIntf.getName(), dpnId);
                        } else {
                            LOG.error("addNewAdjToVpnInterface: No rds to allocate extraroute vpn {} prefix {}",
                                    vpnName, prefix);
                            return;
                        }
                        // iRT/eRT use case Will be handled in a new patchset for L3VPN Over VxLAN.
                        // Keeping the MPLS check for now.
                        if (encapType.equals(VrfEntryBase.EncapType.Mplsgre)) {
                            final Adjacency opAdjacency = new AdjacencyBuilder(operationalAdjacency).build();
                            List<VpnInstanceOpDataEntry> vpnsToImportRoute =
                                    vpnUtil.getVpnsImportingMyRoute(vpnName);
                            vpnsToImportRoute.forEach(vpn -> {
                                if (vpn.getVrfId() != null) {
                                    vpnUtil.allocateRdForExtraRouteAndUpdateUsedRdsMap(vpn.getVpnId(),
                                            vpnId, prefix,
                                            vpnUtil.getVpnName(vpn.getVpnId()), nh, dpnId)
                                            .ifPresent(
                                                rds -> vpnManager.addExtraRoute(
                                                        vpnUtil.getVpnName(vpn.getVpnId()), adj.getIpAddress(),
                                                        nh, rds, currVpnIntf.getVpnInstanceName(), l3vni,
                                                        RouteOrigin.SELF_IMPORTED, currVpnIntf.getName(), opAdjacency,
                                                        encapType, prefixListForRefreshFib, writeConfigTxn));
                                }
                            });
                        }
                    } finally {
                        lock.unlock();
                    }
                } else if (adj.isPhysNetworkFunc()) { // PNF adjacency.
                    LOG.trace("addNewAdjToVpnInterface: Adding prefix {} to interface {} for vpn {}", prefix,
                            currVpnIntf.getName(), vpnName);

                    InstanceIdentifier<VpnInterface> vpnIfaceConfigidentifier = VpnUtil
                            .getVpnInterfaceIdentifier(currVpnIntf.getName());
                    Optional<VpnInterface> vpnIntefaceConfig = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, vpnIfaceConfigidentifier);
                    Prefixes pnfPrefix = VpnUtil.getPrefixToInterface(Uint64.ZERO, currVpnIntf.getName(), prefix,
                            Prefixes.PrefixCue.PhysNetFunc);
                    if (vpnIntefaceConfig.isPresent()) {
                        pnfPrefix = VpnUtil.getPrefixToInterface(Uint64.ZERO, currVpnIntf.getName(), prefix,
                                vpnIntefaceConfig.get().getNetworkId(), vpnIntefaceConfig.get().getNetworkType(),
                                vpnIntefaceConfig.get().getSegmentationId().toJava(), Prefixes.PrefixCue.PhysNetFunc);
                    }

                    String parentVpnRd = getParentVpnRdForExternalSubnet(adj);

                    writeOperTxn.mergeParentStructureMerge(
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnUtil.getVpnId(adj.getSubnetId().getValue()),
                                    prefix), pnfPrefix);

                    fibManager.addOrUpdateFibEntry(adj.getSubnetId().getValue(), adj.getMacAddress(),
                            adj.getIpAddress(), emptyList(), null /* EncapType */, Uint32.ZERO /* label */,
                            Uint32.ZERO /*l3vni*/, null /* gw-mac */, parentVpnRd,
                            RouteOrigin.LOCAL, interfaceName, null, writeConfigTxn);

                    input.setRd(adj.getVrfId());
                }
                if (operationalAdjacency == null) {
                    operationalAdjacency = populator.createOperationalAdjacency(input);
                }
                adjacencies.add(operationalAdjacency);
                AdjacenciesOp aug = VpnUtil.getVpnInterfaceOpDataEntryAugmentation(adjacencies);
                VpnInterfaceOpDataEntry newVpnIntf =
                        VpnUtil.getVpnInterfaceOpDataEntry(currVpnIntf.getName(), currVpnIntf.getVpnInstanceName(),
                                aug, dpnId, currVpnIntf.getLportTag().toJava(),
                                currVpnIntf.getGatewayMacAddress(), currVpnIntf.getGatewayIpAddress());
                writeOperTxn.mergeParentStructureMerge(identifier, newVpnIntf);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("addNewAdjToVpnInterface: Failed to read data store for interface {} dpn {} vpn {} rd {} ip "
                    + "{}", interfaceName, dpnId, configVpnName, primaryRd, adj.getIpAddress());
        }
    }

    @Nullable
    private String getParentVpnRdForExternalSubnet(Adjacency adj) {
        Subnets subnets = vpnUtil.getExternalSubnet(adj.getSubnetId());
        return subnets != null ? subnets.getExternalNetworkId().getValue() : null;
    }

    protected void delAdjFromVpnInterface(InstanceIdentifier<VpnInterfaceOpDataEntry> identifier, Adjacency adj,
                                            Uint64 dpnId, TypedWriteTransaction<Operational> writeOperTxn,
                                            TypedWriteTransaction<Configuration> writeConfigTxn) {
        String interfaceName = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class).getName();
        String vpnName = identifier.firstKeyOf(VpnInterfaceOpDataEntry.class).getVpnInstanceName();
        try {
            Optional<VpnInterfaceOpDataEntry> optVpnInterface = SingleTransactionDataBroker.syncReadOptional(
                    dataBroker, LogicalDatastoreType.OPERATIONAL, identifier);
            if (optVpnInterface.isPresent()) {
                VpnInterfaceOpDataEntry currVpnIntf = optVpnInterface.get();
                InstanceIdentifier<AdjacenciesOp> path = identifier.augmentation(AdjacenciesOp.class);
                Optional<AdjacenciesOp> optAdjacencies = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, path);
                if (optAdjacencies.isPresent()) {
                    Map<AdjacencyKey, Adjacency> keyAdjacencyMap = optAdjacencies.get().getAdjacency();

                    if (keyAdjacencyMap != null && !keyAdjacencyMap.isEmpty()) {
                        LOG.trace("delAdjFromVpnInterface: Adjacencies are {}", keyAdjacencyMap);
                        for (Adjacency adjacency : keyAdjacencyMap.values()) {
                            if (Objects.equals(adjacency.getIpAddress(), adj.getIpAddress())) {
                                String rd = adjacency.getVrfId();
                                if (adj.getNextHopIpList() != null) {
                                    for (String nh : adj.getNextHopIpList()) {
                                        deleteExtraRouteFromCurrentAndImportingVpns(
                                                currVpnIntf.getVpnInstanceName(), adj.getIpAddress(), nh, rd,
                                                currVpnIntf.getName(), writeConfigTxn, writeOperTxn);
                                    }
                                } else if (adj.isPhysNetworkFunc()) {
                                    LOG.info("delAdjFromVpnInterface: deleting PNF adjacency prefix {} subnet {}",
                                            adj.getIpAddress(), adj.getSubnetId());
                                    fibManager.removeFibEntry(adj.getSubnetId().getValue(), adj.getIpAddress(),
                                            interfaceName, null, writeConfigTxn);
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
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("delAdjFromVpnInterface: Failed to read data store for ip {} interface {} dpn {} vpn {}",
                    adj.getIpAddress(), interfaceName, dpnId, vpnName);
        }
    }

    private void deleteExtraRouteFromCurrentAndImportingVpns(String vpnName, String destination, String nextHop,
                                    String rd, String intfName, TypedWriteTransaction<Configuration> writeConfigTxn,
                                    TypedWriteTransaction<Operational> writeOperTx) {
        LOG.info("removing extra-route {} for nexthop {} in VPN {} intfName {} rd {}",
                destination, nextHop, vpnName, intfName, rd);
        vpnManager.delExtraRoute(vpnName, destination, nextHop, rd, vpnName, intfName, writeConfigTxn, writeOperTx);
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = vpnUtil.getVpnsImportingMyRoute(vpnName);
        for (VpnInstanceOpDataEntry vpn : vpnsToImportRoute) {
            String vpnRd = vpn.getVrfId();
            if (vpnRd != null) {
                LOG.info("deleting extra-route {} for nexthop {} in VPN {} intfName {} vpnRd {}",
                        destination, nextHop, vpnName, intfName, vpnRd);
                vpnManager.delExtraRoute(vpnName, destination, nextHop, vpnRd, vpnName, intfName, writeConfigTxn,
                        writeOperTx);
            }
        }
    }

    InstanceIdentifier<DpnVpninterfacesList> getRouterDpnId(String routerName, Uint64 dpnId) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName))
            .child(DpnVpninterfacesList.class, new DpnVpninterfacesListKey(dpnId)).build();
    }

    InstanceIdentifier<RouterDpnList> getRouterId(String routerName) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName)).build();
    }

    protected void createFibEntryForRouterInterface(String primaryRd, VpnInterface vpnInterface, String interfaceName,
                                                TypedWriteTransaction<Configuration> writeConfigTxn, String vpnName) {
        if (vpnInterface == null) {
            return;
        }
        List<Adjacency> adjs = vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(interfaceName);
        if (adjs == null) {
            LOG.error("createFibEntryForRouterInterface: VPN Interface {} of router addition failed as adjacencies for"
                    + " this vpn interface could not be obtained. vpn {}", interfaceName, vpnName);
            return;
        }
        for (Adjacency adj : adjs) {
            if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                String primaryInterfaceIp = adj.getIpAddress();
                String macAddress = adj.getMacAddress();
                String prefix = VpnUtil.getIpPrefix(primaryInterfaceIp);

                Uint32 label = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
                        VpnUtil.getNextHopLabelKey(primaryRd, prefix));
                if (label.longValue() == VpnConstants.INVALID_LABEL) {
                    LOG.error(
                            "createFibEntryForRouterInterface: Unable to retrieve label for vpn pool {}, "
                                    + "vpninterface {}, vpn {}, rd {}",
                            VpnConstants.VPN_IDPOOL_NAME, interfaceName, vpnName, primaryRd);
                    return;
                }
                RouterInterface routerInt = new RouterInterfaceBuilder().setUuid(vpnName)
                        .setIpAddress(primaryInterfaceIp).setMacAddress(macAddress).build();
                fibManager.addFibEntryForRouterInterface(primaryRd, prefix,
                        routerInt, label, writeConfigTxn);
                LOG.info("createFibEntryForRouterInterface: Router interface {} for vpn {} rd {} prefix {} label {}"
                        + " macAddress {} processed successfully;", interfaceName, vpnName, primaryRd, prefix, label,
                        macAddress);
            } else {
                LOG.error("createFibEntryForRouterInterface: VPN Interface {} of router addition failed as primary"
                                + " adjacency for this vpn interface could not be obtained. rd {} vpnName {}",
                        interfaceName, primaryRd, vpnName);
            }
        }
    }

    protected void deleteFibEntryForRouterInterface(VpnInterface vpnInterface,
            TypedWriteTransaction<Configuration> writeConfigTxn, String vpnName) {
        Adjacencies adjs = vpnInterface.augmentation(Adjacencies.class);
        String rd = vpnUtil.getVpnRd(vpnName);
        if (adjs != null) {
            Map<AdjacencyKey, Adjacency> keyAdjacencyMap = adjs.nonnullAdjacency();
            for (Adjacency adj : keyAdjacencyMap.values()) {
                if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    String primaryInterfaceIp = adj.getIpAddress();
                    String prefix = VpnUtil.getIpPrefix(primaryInterfaceIp);
                    fibManager.removeFibEntry(rd, prefix, null, null, writeConfigTxn);
                    LOG.info("deleteFibEntryForRouterInterface: FIB for router interface {} deleted for vpn {} rd {}"
                            + " prefix {}", vpnInterface.getName(), vpnName, rd, prefix);
                }
            }
        } else {
            LOG.error("deleteFibEntryForRouterInterface: Adjacencies for vpninterface {} is null, rd: {}",
                    vpnInterface.getName(), rd);
        }
    }

    private void processSavedInterface(UnprocessedVpnInterfaceData intefaceData, String vpnName) {
        final VpnInterfaceKey key = intefaceData.identifier.firstKeyOf(VpnInterface.class);
        final String interfaceName = key.getName();
        InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpIdentifier = VpnUtil
                 .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
        addVpnInterfaceToVpn(vpnInterfaceOpIdentifier, intefaceData.vpnInterface, null, null,
                  intefaceData.identifier, vpnName);
    }

    private void addToUnprocessedVpnInterfaces(InstanceIdentifier<VpnInterface> identifier,
                                              VpnInterface vpnInterface, String vpnName) {
        ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces = unprocessedVpnInterfaces
               .get(vpnName);
        if (vpnInterfaces == null) {
            vpnInterfaces = new ConcurrentLinkedQueue<>();
        }
        vpnInterfaces.add(new UnprocessedVpnInterfaceData(identifier, vpnInterface));
        unprocessedVpnInterfaces.put(vpnName, vpnInterfaces);
        LOG.info("addToUnprocessedVpnInterfaces: Saved unhandled vpn interface {} in vpn instance {}",
                vpnInterface.getName(), vpnName);
    }

    public boolean isVpnInstanceReady(String vpnInstanceName) {
        String vpnRd = vpnUtil.getVpnRd(vpnInstanceName);
        if (vpnRd == null) {
            return false;
        }
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnUtil.getVpnInstanceOpData(vpnRd);

        return vpnInstanceOpDataEntry != null;
    }

    public void processSavedInterfaces(String vpnInstanceName, boolean hasVpnInstanceCreatedSuccessfully) {
        // FIXME: separate out to somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnInstanceName);
        lock.lock();
        try {
            ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces =
                    unprocessedVpnInterfaces.get(vpnInstanceName);
            if (vpnInterfaces != null) {
                while (!vpnInterfaces.isEmpty()) {
                    UnprocessedVpnInterfaceData savedInterface = vpnInterfaces.poll();
                    if (hasVpnInstanceCreatedSuccessfully) {
                        processSavedInterface(savedInterface, vpnInstanceName);
                        LOG.info("processSavedInterfaces: Handle saved vpn interfaces {} in vpn instance {}",
                                savedInterface.vpnInterface.getName(), vpnInstanceName);
                    } else {
                        LOG.error("processSavedInterfaces: Cannot process vpn interface {} in vpn instance {}",
                                savedInterface.vpnInterface.getName(), vpnInstanceName);
                    }
                }
            } else {
                LOG.info("processSavedInterfaces: No interfaces in queue for VPN {}", vpnInstanceName);
            }
        } finally {
            lock.unlock();
        }
    }

    private void removeInterfaceFromUnprocessedList(InstanceIdentifier<VpnInterface> identifier,
            VpnInterface vpnInterface) {
        // FIXME: use VpnInstanceNamesKey perhaps? What about nulls?
        final String firstVpnName = VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface);
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(firstVpnName);
        lock.lock();
        try {
            ConcurrentLinkedQueue<UnprocessedVpnInterfaceData> vpnInterfaces =
                    unprocessedVpnInterfaces.get(firstVpnName);
            if (vpnInterfaces != null) {
                if (vpnInterfaces.remove(new UnprocessedVpnInterfaceData(identifier, vpnInterface))) {
                    LOG.info("removeInterfaceFromUnprocessedList: Removed vpn interface {} in vpn instance {} from "
                            + "unprocessed list", vpnInterface.getName(), firstVpnName);
                }
            } else {
                LOG.info("removeInterfaceFromUnprocessedList: No interfaces in queue for VPN {}", firstVpnName);
            }
        } finally {
            lock.unlock();
        }
    }

    public void vpnInstanceIsReady(String vpnInstanceName) {
        processSavedInterfaces(vpnInstanceName, true);
    }

    public void vpnInstanceFailed(String vpnInstanceName) {
        processSavedInterfaces(vpnInstanceName, false);
    }

    private static class UnprocessedVpnInterfaceData {
        InstanceIdentifier<VpnInterface> identifier;
        VpnInterface vpnInterface;

        UnprocessedVpnInterfaceData(InstanceIdentifier<VpnInterface> identifier, VpnInterface vpnInterface) {
            this.identifier = identifier;
            this.vpnInterface = vpnInterface;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (identifier == null ? 0 : identifier.hashCode());
            result = prime * result + (vpnInterface == null ? 0 : vpnInterface.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            UnprocessedVpnInterfaceData other = (UnprocessedVpnInterfaceData) obj;
            if (identifier == null) {
                if (other.identifier != null) {
                    return false;
                }
            } else if (!identifier.equals(other.identifier)) {
                return false;
            }
            if (vpnInterface == null) {
                if (other.vpnInterface != null) {
                    return false;
                }
            } else if (!vpnInterface.equals(other.vpnInterface)) {
                return false;
            }
            return true;
        }
    }

    public void updateVpnInterfacesForUnProcessAdjancencies(String vpnName) {
        String primaryRd = vpnUtil.getVpnRd(vpnName);
        VpnInstanceOpDataEntry vpnInstanceOpData = vpnUtil.getVpnInstanceOpData(primaryRd);
        if (vpnInstanceOpData == null || vpnInstanceOpData.getVpnToDpnList() == null) {
            return;
        }
        List<VpnToDpnList> vpnToDpnLists = new ArrayList<VpnToDpnList>(vpnInstanceOpData.getVpnToDpnList().values());
        if (vpnToDpnLists == null || vpnToDpnLists.isEmpty()) {
            return;
        }
        LOG.debug("Update the VpnInterfaces for Unprocessed Adjancencies for vpnName:{}", vpnName);
        vpnToDpnLists.forEach(vpnToDpnList -> {
            if (vpnToDpnList.getVpnInterfaces() == null) {
                return;
            }
            vpnToDpnList.nonnullVpnInterfaces().values().forEach(vpnInterface -> {
                try {
                    InstanceIdentifier<VpnInterfaceOpDataEntry> existingVpnInterfaceId =
                            VpnUtil.getVpnInterfaceOpDataEntryIdentifier(vpnInterface.getInterfaceName(), vpnName);
                    Optional<VpnInterfaceOpDataEntry> vpnInterfaceOptional = SingleTransactionDataBroker
                            .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, existingVpnInterfaceId);
                    if (!vpnInterfaceOptional.isPresent()) {
                        return;
                    }
                    List<Adjacency> configVpnAdjacencies = vpnUtil.getAdjacenciesForVpnInterfaceFromConfig(
                            vpnInterface.getInterfaceName());
                    if (configVpnAdjacencies == null) {
                        LOG.debug("There is no adjacency available for vpnInterface:{}", vpnInterface);
                        return;
                    }
                    List<Adjacency> operationVpnAdjacencies = new ArrayList<Adjacency>(vpnInterfaceOptional.get()
                            .augmentation(AdjacenciesOp.class).nonnullAdjacency().values());
                    // Due to insufficient rds,  some of the extra route wont get processed when it is added.
                    // The unprocessed adjacencies will be present in config vpn interface DS but will be missing
                    // in operational DS. These unprocessed adjacencies will be handled below.
                    // To obtain unprocessed adjacencies, filtering is done by which the missing adjacencies in
                    // operational DS are retrieved which is used to call addNewAdjToVpnInterface method.
                    configVpnAdjacencies.stream()
                        .filter(adjacency -> operationVpnAdjacencies.stream()
                                .noneMatch(operationalAdjacency ->
                                    Objects.equals(operationalAdjacency.getIpAddress(), adjacency.getIpAddress())))
                        .forEach(adjacency -> {
                            LOG.debug("Processing the vpnInterface{} for the Ajacency:{}", vpnInterface, adjacency);
                            jobCoordinator.enqueueJob("VPNINTERFACE-" + vpnInterface.getInterfaceName(),
                                () -> {
                                    // TODO Deal with sequencing — the config tx must only submitted
                                    // if the oper tx goes in
                                    if (vpnUtil.isAdjacencyEligibleToVpn(adjacency, vpnName)) {
                                        List<ListenableFuture<?>> futures = new ArrayList<>();
                                        futures.add(
                                            txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, operTx -> {
                                                //set of prefix used, as entry in prefix-to-interface datastore
                                                // is prerequisite for refresh Fib to avoid race condition leading
                                                // to missing remote next hop in bucket actions on bgp-vpn delete
                                                Set<String> prefixListForRefreshFib = new HashSet<>();
                                                ListenableFuture<?> configTxFuture =
                                                    txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                                        confTx -> addNewAdjToVpnInterface(existingVpnInterfaceId,
                                                            primaryRd, adjacency,
                                                            vpnInterfaceOptional.get().getDpnId(),
                                                                operTx, confTx, confTx, prefixListForRefreshFib));
                                                Futures.addCallback(configTxFuture,
                                                    new VpnInterfaceCallBackHandler(primaryRd, prefixListForRefreshFib),
                                                    MoreExecutors.directExecutor());
                                                futures.add(configTxFuture);
                                            }));
                                        return futures;
                                    } else {
                                        return emptyList();
                                    }
                                });
                        });
                } catch (InterruptedException | ExecutionException e) {
                    LOG.error("updateVpnInterfacesForUnProcessAdjancencies: Failed to read data store for vpn {} rd {}",
                            vpnName, primaryRd);
                }
            });
        });
    }

    private class PostVpnInterfaceWorker implements FutureCallback<Object> {
        private final String interfaceName;
        private final boolean add;
        private final String txnDestination;
        List<String> ipPrefixList;

        PostVpnInterfaceWorker(String interfaceName, List<String> ipAddressList, boolean add, String transactionDest) {
            this.interfaceName = interfaceName;
            this.ipPrefixList = ipAddressList;
            this.add = add;
            this.txnDestination = transactionDest;
        }

        @Override
        public void onSuccess(Object voidObj) {
            if (add) {
                LOG.debug("VpnInterfaceManager: VrfEntries for Interface {} ipAddressList {} stored into destination {}"
                        + " successfully", interfaceName, ipPrefixList, txnDestination);
            } else {
                LOG.debug("VpnInterfaceManager: VrfEntries for Interface {} ipAddressList {} removed successfully",
                        interfaceName, ipPrefixList);
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (add) {
                LOG.error("VpnInterfaceManager: VrfEntries for Interface {} ipAddressList {} failed to store into "
                        + "destination {}", interfaceName, ipPrefixList, txnDestination, throwable);
            } else {
                LOG.error("VpnInterfaceManager: VrfEntries for Interface {} ipAddressList {} removal failed",
                        interfaceName, ipPrefixList, throwable);
                vpnUtil.unsetScheduledToRemoveForVpnInterface(interfaceName);
            }
        }
    }

    private class VpnInterfaceCallBackHandler implements FutureCallback<Object> {
        private final String primaryRd;
        private final Set<String> prefixListForRefreshFib;

        VpnInterfaceCallBackHandler(String primaryRd, Set<String> prefixListForRefreshFib) {
            this.primaryRd = primaryRd;
            this.prefixListForRefreshFib = prefixListForRefreshFib;
        }

        @Override
        public void onSuccess(Object voidObj) {
            prefixListForRefreshFib.forEach(prefix -> {
                fibManager.refreshVrfEntry(primaryRd, prefix);
            });
        }

        @Override
        public void onFailure(Throwable throwable) {
            LOG.debug("write Tx config operation failed", throwable);
        }
    }
}
