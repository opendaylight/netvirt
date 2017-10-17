/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
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
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.NexthopManager.AdjacencyResult;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState.State;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class VrfEntryListener extends AsyncDataTreeChangeListenerBase<VrfEntry, VrfEntryListener>
    implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VrfEntryListener.class);
    private static final String FLOWID_PREFIX = "L3.";
    private static final BigInteger COOKIE_VM_FIB_TABLE =  new BigInteger("8000003", 16);
    private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;
    private static final int IPV4_ADDR_PREFIX_LENGTH = 32;
    private static final int LFIB_INTERVPN_PRIORITY = 15;
    public static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    private static final int DJC_MAX_RETRIES = 3;
    private static final BigInteger COOKIE_TABLE_MISS = new BigInteger("8000004", 16);

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final NexthopManager nextHopManager;
    private final IdManagerService idManager;
    private final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler;
    private final BaseVrfEntryHandler baseVrfEntryHandler;
    private final RouterInterfaceVrfEntryHandler routerInterfaceVrfEntryHandler;

    protected static boolean isOpenStackVniSemanticsEnforced;

    @Inject
    public VrfEntryListener(final DataBroker dataBroker, final IMdsalApiManager mdsalApiManager,
                            final NexthopManager nexthopManager, final IdManagerService idManager,
                            final IElanService elanManager,
                            final BaseVrfEntryHandler vrfEntryHandler,
                            final BgpRouteVrfEntryHandler bgpRouteVrfEntryHandler,
                            final RouterInterfaceVrfEntryHandler routerInterfaceVrfEntryHandler) {
        super(VrfEntry.class, VrfEntryListener.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalApiManager;
        this.nextHopManager = nexthopManager;
        this.idManager = idManager;
        this.baseVrfEntryHandler = vrfEntryHandler;
        this.bgpRouteVrfEntryHandler = bgpRouteVrfEntryHandler;
        this.routerInterfaceVrfEntryHandler = routerInterfaceVrfEntryHandler;

        isOpenStackVniSemanticsEnforced = elanManager.isOpenStackVniSemanticsEnforced();
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected VrfEntryListener getDataTreeChangeListener() {
        return VrfEntryListener.this;
    }

    @Override
    protected InstanceIdentifier<VrfEntry> getWildCardPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(VrfEntry.class);
    }

    @Override
    protected void add(final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
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
            EvpnVrfEntryHandler evpnVrfEntryHandler =
                    new EvpnVrfEntryHandler(dataBroker, this, bgpRouteVrfEntryHandler, nextHopManager);
            evpnVrfEntryHandler.createFlows(identifier, vrfEntry, rd);
            return;
        }
        RouterInterface routerInt = vrfEntry.getAugmentation(RouterInterface.class);
        if (routerInt != null) {
            // ping responder for router interfaces
            routerInterfaceVrfEntryHandler.createFlows(identifier, vrfEntry, rd);
            return;
        }
        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
            createFibEntries(identifier, vrfEntry);
            return;
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry) {
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
            EvpnVrfEntryHandler evpnVrfEntryHandler =
                    new EvpnVrfEntryHandler(dataBroker, this, bgpRouteVrfEntryHandler, nextHopManager);
            evpnVrfEntryHandler.removeFlows(identifier, vrfEntry, rd);
            return;
        }
        RouterInterface routerInt = vrfEntry.getAugmentation(RouterInterface.class);
        if (routerInt != null) {
            // ping responder for router interfaces
            routerInterfaceVrfEntryHandler.removeFlows(identifier, vrfEntry, rd);
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
    protected void update(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update) {
        Preconditions.checkNotNull(update, "VrfEntry should not be null or empty.");
        if (original.equals(update)) {
            return;
        }
        final String rd = identifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        LOG.debug("UPDATE: Updating Fib Entries to rd {} prefix {} route-paths {}",
            rd, update.getDestPrefix(), update.getRoutePaths());
        // Handle BGP Routes first
        if (RouteOrigin.value(update.getOrigin()) == RouteOrigin.BGP) {
            bgpRouteVrfEntryHandler.updateFlows(identifier, original, update, rd);
            LOG.info("UPDATE: Updated Fib Entries to rd {} prefix {} route-paths {}",
                rd, update.getDestPrefix(), update.getRoutePaths());
            return;
        }

        // Handle Vpn Interface driven Routes next (ie., STATIC and LOCAL)
        if (FibHelper.isControllerManagedVpnInterfaceRoute(RouteOrigin.value(update.getOrigin()))) {
            List<RoutePaths> originalRoutePath = original.getRoutePaths();
            List<RoutePaths> updateRoutePath = update.getRoutePaths();
            LOG.info("UPDATE: Original route-path {} update route-path {} ", originalRoutePath, updateRoutePath);

            //Updates need to be handled for extraroute even if original vrf entry route path is null or
            //updated vrf entry route path is null. This can happen during tunnel events.
            Optional<VpnInstanceOpDataEntry> optVpnInstance = FibUtil.getVpnInstanceOpData(dataBroker, rd);
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
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            nextHopsRemoved.parallelStream()
                    .forEach(nextHopRemoved -> FibUtil.updateUsedRdAndVpnToExtraRoute(
                             writeOperTxn, dataBroker, nextHopRemoved, rd,
                             update.getDestPrefix()));
            CheckedFuture<Void, TransactionCommitFailedException> operFuture = writeOperTxn.submit();
            try {
                operFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Exception encountered while submitting operational future for update vrfentry {}: "
                        + "{}", update, e);
            }

            createFibEntries(identifier, update);
            LOG.info("UPDATE: Updated Fib Entries to rd {} prefix {} route-paths {}",
                rd, update.getDestPrefix(), update.getRoutePaths());
            return;
        }

        /* Handl all other route origins */
        createFibEntries(identifier, update);

        LOG.info("UPDATE: Updated Fib Entries to rd {} prefix {} route-paths {}",
            rd, update.getDestPrefix(), update.getRoutePaths());
    }

    private void createFibEntries(final InstanceIdentifier<VrfEntry> vrfEntryIid, final VrfEntry vrfEntry) {
        final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final VpnInstanceOpDataEntry vpnInstance =
                FibUtil.getVpnInstance(dataBroker, vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId()
                + " has null vpnId!");
        final Collection<VpnToDpnList> vpnToDpnList;
        if (vrfEntry.getParentVpnRd() != null
                && FibHelper.isControllerManagedNonSelfImportedRoute(RouteOrigin.value(vrfEntry.getOrigin()))) {
            // This block MUST BE HIT only for PNF (Physical Network Function) FIB Entries.
            VpnInstanceOpDataEntry parentVpnInstance = FibUtil.getVpnInstance(dataBroker, vrfEntry.getParentVpnRd());
            vpnToDpnList = parentVpnInstance != null ? parentVpnInstance.getVpnToDpnList() :
                vpnInstance.getVpnToDpnList();
            LOG.info("createFibEntries: Processing creation of PNF FIB entry with rd {} prefix {}",
                    vrfEntry.getParentVpnRd(), vrfEntry.getDestPrefix());
        } else {
            vpnToDpnList = vpnInstance.getVpnToDpnList();
        }
        final Long vpnId = vpnInstance.getVpnId();
        final String rd = vrfTableKey.getRouteDistinguisher();
        SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
        if (subnetRoute != null) {
            final long elanTag = subnetRoute.getElantag();
            LOG.trace("SUBNETROUTE: createFibEntries: SubnetRoute augmented vrfentry found for rd {} prefix {}"
                    + " with elantag {}", rd, vrfEntry.getDestPrefix(), elanTag);
            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()), () -> {
                    WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                    for (final VpnToDpnList curDpn : vpnToDpnList) {
                        if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                            installSubnetRouteInFib(curDpn.getDpnId(), elanTag, rd, vpnId, vrfEntry, tx);
                            installSubnetBroadcastAddrDropRule(curDpn.getDpnId(), rd, vpnId.longValue(),
                                    vrfEntry, NwConstants.ADD_FLOW, tx);
                        }
                    }
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(tx.submit());
                    return futures;
                });
            }
            return;
        }

        final List<BigInteger> localDpnIdList = createLocalFibEntry(vpnInstance.getVpnId(), rd, vrfEntry);
        if (!localDpnIdList.isEmpty() && vpnToDpnList != null) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()), () -> {
                WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                synchronized (vpnInstance.getVpnInstanceName().intern()) {
                    for (VpnToDpnList vpnDpn : vpnToDpnList) {
                        if (!localDpnIdList.contains(vpnDpn.getDpnId())) {
                            if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                try {
                                    if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin())) {
                                        bgpRouteVrfEntryHandler.createRemoteFibEntry(vpnDpn.getDpnId(),
                                                vpnId, vrfTableKey.getRouteDistinguisher(), vrfEntry, tx, txnObjects);
                                    } else {
                                        createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(),
                                                vrfTableKey.getRouteDistinguisher(), vrfEntry, tx);
                                    }
                                } catch (NullPointerException e) {
                                    LOG.error("Failed to get create remote fib flows for prefix {} ",
                                            vrfEntry.getDestPrefix(), e);
                                }
                            }
                        }
                    }
                }
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(tx.submit());
                return futures;
            }, DJC_MAX_RETRIES);
        }

        Optional<String> optVpnUuid = FibUtil.getVpnNameFromRd(dataBroker, rd);
        if (optVpnUuid.isPresent()) {
            String vpnUuid = optVpnUuid.get();
            InterVpnLinkDataComposite interVpnLink = InterVpnLinkCache.getInterVpnLinkByVpnId(vpnUuid).orNull();
            if (interVpnLink != null) {
                LOG.debug("InterVpnLink {} found in Cache linking Vpn {}", interVpnLink.getInterVpnLinkName(), vpnUuid);
                FibUtil.getFirstNextHopAddress(vrfEntry).ifPresent(routeNexthop -> {
                    if (interVpnLink.isIpAddrTheOtherVpnEndpoint(routeNexthop, vpnUuid)) {
                        // This is an static route that points to the other endpoint of an InterVpnLink
                        // In that case, we should add another entry in FIB table pointing to LPortDispatcher table.
                        installIVpnLinkSwitchingFlows(interVpnLink, vpnUuid, vrfEntry, vpnId);
                        installInterVpnRouteInLFib(interVpnLink, vpnUuid, vrfEntry);
                    }
                });
            }
        }
    }

    void refreshFibTables(String rd, String prefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> vrfEntry = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
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
            List<String> vpnInstanceNames = lri.getVpnInstanceList();
            vpnInstanceNames.add(vpnInstanceName);
            builder.setVpnInstanceList(vpnInstanceNames);
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, lriId, builder.build());
        } else {
            LOG.debug("vpnName {} is present in LRI with label {}..", vpnInstanceName, lri.getLabel());
        }
        return prefixBuilder.build();
    }

    void installSubnetRouteInFib(final BigInteger dpnId, final long elanTag, final String rd,
                                         final long vpnId, final VrfEntry vrfEntry, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }
        FibUtil.getLabelFromRoutePaths(vrfEntry).ifPresent(label -> {
            List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
            synchronized (label.toString().intern()) {
                LabelRouteInfo lri = getLabelRouteInfo(label);
                if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {

                    if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                FibUtil.getVpnInstanceOpData(dataBroker, rd);
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
            }
        });
        final List<InstructionInfo> instructions = new ArrayList<>();
        BigInteger subnetRouteMeta = BigInteger.valueOf(elanTag).shiftLeft(24)
            .or(BigInteger.valueOf(vpnId).shiftLeft(1));
        instructions.add(new InstructionWriteMetadata(subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE));
        instructions.add(new InstructionGotoTable(NwConstants.L3_SUBNET_ROUTE_TABLE));
        baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions,
                NwConstants.ADD_FLOW, tx, null);

        if (vrfEntry.getRoutePaths() != null) {
            for (RoutePaths routePath : vrfEntry.getRoutePaths()) {
                if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                    List<ActionInfo> actionsInfos = new ArrayList<>();
                    // reinitialize instructions list for LFIB Table
                    final List<InstructionInfo> LFIBinstructions = new ArrayList<>();

                    actionsInfos.add(new ActionPopMpls());
                    LFIBinstructions.add(new InstructionApplyActions(actionsInfos));
                    LFIBinstructions.add(new InstructionWriteMetadata(subnetRouteMeta,
                            MetaDataUtil.METADATA_MASK_SUBNET_ROUTE));
                    LFIBinstructions.add(new InstructionGotoTable(NwConstants.L3_SUBNET_ROUTE_TABLE));

                    makeLFibTableEntry(dpnId, routePath.getLabel(), LFIBinstructions, DEFAULT_FIB_FLOW_PRIORITY,
                            NwConstants.ADD_FLOW, tx);
                }
            }
        }
        if (!wrTxPresent) {
            tx.submit();
        }
    }

    private void installSubnetBroadcastAddrDropRule(final BigInteger dpnId, final String rd, final long vpnId,
                                                    final VrfEntry vrfEntry, int addOrRemove, WriteTransaction tx) {
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
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
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
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (addOrRemove == NwConstants.ADD_FLOW) {
            tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId,flow, true);
        } else {
            tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
        }
    }

    /*
     * For a given route, it installs a flow in LFIB that sets the lportTag of the other endpoint and sends to
     * LportDispatcher table (via table 80)
     */
    private void installInterVpnRouteInLFib(final InterVpnLinkDataComposite interVpnLink, final String vpnName,
                                            final VrfEntry vrfEntry) {
        // INTERVPN routes are routes in a Vpn1 that have been leaked to Vpn2. In DC-GW, this Vpn2 route is pointing
        // to a list of DPNs where Vpn2's VpnLink was instantiated. In these DPNs LFIB must be programmed so that the
        // packet is commuted from Vpn2 to Vpn1.
        String interVpnLinkName = interVpnLink.getInterVpnLinkName();
        if (!interVpnLink.isActive()) {
            LOG.warn("InterVpnLink {} is NOT ACTIVE. InterVpnLink flows for prefix={} wont be installed in LFIB",
                     interVpnLinkName, vrfEntry.getDestPrefix());
            return;
        }

        List<BigInteger> targetDpns = interVpnLink.getEndpointDpnsByVpnName(vpnName);
        Optional<Long> optLportTag = interVpnLink.getEndpointLportTagByVpnName(vpnName);
        if (!optLportTag.isPresent()) {
            LOG.warn("Could not retrieve lportTag for VPN {} endpoint in InterVpnLink {}", vpnName, interVpnLinkName);
            return;
        }

        Long lportTag = optLportTag.get();
        Long label = FibUtil.getLabelFromRoutePaths(vrfEntry).orElse(null);
        if (label == null) {
            LOG.error("Could not find label in vrfEntry=[prefix={} routePaths={}]. LFIB entry for InterVpnLink skipped",
                      vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths());
            return;
        }
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionPopMpls());
        List<InstructionInfo> instructions = Arrays.asList(
            new InstructionApplyActions(actionsInfos),
            new InstructionWriteMetadata(MetaDataUtil.getMetaDataForLPortDispatcher(lportTag.intValue(),
                                                            ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                                                  NwConstants.L3VPN_SERVICE_INDEX)),
                                         MetaDataUtil.getMetaDataMaskForLPortDispatcher()),
            new InstructionGotoTable(NwConstants.L3_INTERFACE_TABLE));
        List<String> interVpnNextHopList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);

        for (BigInteger dpId : targetDpns) {
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
                                               final VrfEntry vrfEntry, long vpnTag) {
        Preconditions.checkNotNull(interVpnLink, "InterVpnLink cannot be null");
        Preconditions.checkArgument(vrfEntry.getRoutePaths() != null
            && vrfEntry.getRoutePaths().size() == 1);
        String destination = vrfEntry.getDestPrefix();
        String nextHop = vrfEntry.getRoutePaths().get(0).getNexthopAddress();
        String interVpnLinkName = interVpnLink.getInterVpnLinkName();

        // After having received a static route, we should check if the vpn is part of an inter-vpn-link.
        // In that case, we should populate the FIB table of the VPN pointing to LPortDisptacher table
        // using as metadata the LPortTag associated to that vpn in the inter-vpn-link.
        if (interVpnLink.getState().or(State.Error) != State.Active) {
            LOG.warn("Route to {} with nexthop={} cannot be installed because the interVpnLink {} is not active",
                destination, nextHop, interVpnLinkName);
            return;
        }

        Optional<Long> optOtherEndpointLportTag = interVpnLink.getOtherEndpointLportTagByVpnName(vpnUuid);
        if (!optOtherEndpointLportTag.isPresent()) {
            LOG.warn("Could not find suitable LportTag for the endpoint opposite to vpn {} in interVpnLink {}",
                vpnUuid, interVpnLinkName);
            return;
        }

        List<BigInteger> targetDpns = interVpnLink.getEndpointDpnsByVpnName(vpnUuid);
        if (targetDpns.isEmpty()) {
            LOG.warn("Could not find DPNs for endpoint opposite to vpn {} in interVpnLink {}",
                vpnUuid, interVpnLinkName);
            return;
        }

        String[] values = destination.split("/");
        String destPrefixIpAddress = values[0];
        int prefixLength = values.length == 1 ? 0 : Integer.parseInt(values[1]);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnTag), MetaDataUtil.METADATA_MASK_VRFID));
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

        int priority = DEFAULT_FIB_FLOW_PRIORITY + prefixLength;
        String flowRef = getInterVpnFibFlowRef(interVpnLinkName, destination, nextHop);
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_FIB_TABLE, flowRef, priority, flowRef, 0, 0,
            COOKIE_VM_FIB_TABLE, matches, instructions);

        LOG.trace("Installing flow in FIB table for vpn {} interVpnLink {} nextHop {} key {}",
            vpnUuid, interVpnLink.getInterVpnLinkName(), nextHop, flowRef);

        for (BigInteger dpId : targetDpns) {

            LOG.debug("Installing flow: VrfEntry=[prefix={} route-paths={}] dpn {} for InterVpnLink {} in FIB",
                vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(),
                dpId, interVpnLink.getInterVpnLinkName());

            mdsalManager.installFlow(dpId, flowEntity);
        }
    }

    private List<BigInteger> createLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
        List<BigInteger> returnLocalDpnId = new ArrayList<>();
        String localNextHopIP = vrfEntry.getDestPrefix();
        Prefixes localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, localNextHopIP);
        String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnId);
        if (localNextHopInfo == null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, localNextHopIP);
            List<Routes> vpnExtraRoutes = VpnExtraRouteHelper.getAllVpnExtraRoutes(dataBroker,
                    vpnName, usedRds, localNextHopIP);
            boolean localNextHopSeen = false;
            //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
            for (Routes vpnExtraRoute : vpnExtraRoutes) {
                String ipPrefix;
                if (isIpv4Address(vpnExtraRoute.getNexthopIpList().get(0))) {
                    ipPrefix = vpnExtraRoute.getNexthopIpList().get(0) + NwConstants.IPV4PREFIX;
                } else {
                    ipPrefix = vpnExtraRoute.getNexthopIpList().get(0) + NwConstants.IPV6PREFIX;
                }
                Prefixes localNextHopInfoLocal = FibUtil.getPrefixToInterface(dataBroker,
                    vpnId, ipPrefix);
                if (localNextHopInfoLocal != null) {
                    localNextHopSeen = true;
                    BigInteger dpnId =
                            checkCreateLocalFibEntry(localNextHopInfoLocal, localNextHopInfoLocal.getIpAddress(),
                                    vpnId, rd, vrfEntry, vpnId, vpnExtraRoute, vpnExtraRoutes);
                    returnLocalDpnId.add(dpnId);
                }
            }
            if (!localNextHopSeen && RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                if (optionalLabel.isPresent()) {
                    Long label = optionalLabel.get();
                    List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                    synchronized (label.toString().intern()) {
                        LabelRouteInfo lri = getLabelRouteInfo(label);
                        if (isPrefixAndNextHopPresentInLri(localNextHopIP, nextHopAddressList, lri)) {
                            Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                    FibUtil.getVpnInstanceOpData(dataBroker, rd);
                            if (vpnInstanceOpDataEntryOptional.isPresent()) {
                                String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                                if (lri.getVpnInstanceList().contains(vpnInstanceName)) {
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
                                    BigInteger dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP,
                                            vpnId, rd, vrfEntry, lri.getParentVpnid(), null, vpnExtraRoutes);
                                    returnLocalDpnId.add(dpnId);
                                } else {
                                    for (Routes extraRoutes : vpnExtraRoutes) {
                                        BigInteger dpnId = checkCreateLocalFibEntry(localNextHopInfo,
                                                localNextHopIP,
                                                vpnId, rd, vrfEntry, lri.getParentVpnid(),
                                                extraRoutes, vpnExtraRoutes);
                                        returnLocalDpnId.add(dpnId);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (returnLocalDpnId.isEmpty()) {
                LOG.error("Local DPNID is empty for rd {}, vpnId {}, vrfEntry {}", rd, vpnId, vrfEntry);
            }
        } else {
            BigInteger dpnId = checkCreateLocalFibEntry(localNextHopInfo, localNextHopIP, vpnId,
                    rd, vrfEntry, vpnId, /*routes*/ null, /*vpnExtraRoutes*/ null);
            if (dpnId != null) {
                returnLocalDpnId.add(dpnId);
            }
        }
        return returnLocalDpnId;
    }

    private BigInteger checkCreateLocalFibEntry(Prefixes localNextHopInfo, String localNextHopIP,
                                                final Long vpnId, final String rd,
                                                final VrfEntry vrfEntry, Long parentVpnId,
                                                Routes routes, List<Routes> vpnExtraRoutes) {
        String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnId);
        if (localNextHopInfo != null) {
            long groupId;
            long localGroupId;
            final BigInteger dpnId = localNextHopInfo.getDpnId();
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
            String jobKey = FibUtil.getCreateLocalNextHopJobKey(vpnId, dpnId, vrfEntry.getDestPrefix());
            String interfaceName = localNextHopInfo.getVpnInterfaceName();
            String prefix = vrfEntry.getDestPrefix();
            String gwMacAddress = vrfEntry.getGatewayMacAddress();
            //The loadbalancing group is created only if the extra route has multiple nexthops
            //to avoid loadbalancing the discovered routes
            if (vpnExtraRoutes != null) {
                if (isIpv4Address(routes.getNexthopIpList().get(0))) {
                    localNextHopIP = routes.getNexthopIpList().get(0) + NwConstants.IPV4PREFIX;
                } else {
                    localNextHopIP = routes.getNexthopIpList().get(0) + NwConstants.IPV6PREFIX;
                }
                if (vpnExtraRoutes.size() > 1) {
                    groupId = nextHopManager.createNextHopGroups(parentVpnId, rd, dpnId, vrfEntry, routes,
                            vpnExtraRoutes);
                    localGroupId = nextHopManager.getLocalNextHopGroup(parentVpnId, localNextHopIP);
                } else if (routes.getNexthopIpList().size() > 1) {
                    groupId = nextHopManager.createNextHopGroups(parentVpnId, rd, dpnId, vrfEntry, routes,
                            vpnExtraRoutes);
                    localGroupId = groupId;
                } else {
                    groupId = nextHopManager.getLocalNextHopGroup(parentVpnId, localNextHopIP);
                    localGroupId = groupId;
                }
            } else {
                groupId = nextHopManager.createLocalNextHop(parentVpnId, dpnId, interfaceName, localNextHopIP, prefix,
                        gwMacAddress, jobKey);
                localGroupId = groupId;
            }
            if (groupId == FibConstants.INVALID_GROUP_ID) {
                LOG.error("Unable to create Group for local prefix {} on rd {} for vpninterface {} on Node {}",
                        prefix, rd, interfaceName, dpnId.toString());
                return BigInteger.ZERO;
            }
            final List<InstructionInfo> instructions = Collections.singletonList(
                    new InstructionApplyActions(
                            Collections.singletonList(new ActionGroup(groupId))));
            final List<InstructionInfo> lfibinstructions = Collections.singletonList(
                    new InstructionApplyActions(
                            Arrays.asList(new ActionPopMpls(), new ActionGroup(groupId))));
            java.util.Optional<Long> optLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
            List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(jobKey, () -> {
                WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, instructions,
                        NwConstants.ADD_FLOW, tx, null);
                if (!FibUtil.enforceVxlanDatapathSemanticsforInternalRouterVpn(dataBroker,
                        localNextHopInfo.getSubnetId(), vpnName, rd)) {
                    optLabel.ifPresent(label -> {
                        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                            LOG.debug("Installing LFIB and tunnel table entry on dpn {} for interface {} with label "
                                            + "{}, rd {}, prefix {}, nexthop {}", dpnId,
                                    localNextHopInfo.getVpnInterfaceName(), optLabel, rd, vrfEntry.getDestPrefix(),
                                    nextHopAddressList);
                            makeLFibTableEntry(dpnId, label, lfibinstructions, DEFAULT_FIB_FLOW_PRIORITY,
                                    NwConstants.ADD_FLOW, tx);
                            // If the extra-route is reachable from VMs attached to the same switch,
                            // then the tunnel table can point to the load balancing group.
                            // If it is reachable from VMs attached to different switches,
                            // then it should be pointing to one of the local group in order to avoid looping.
                            if (vrfEntry.getRoutePaths().size() == 1) {
                                makeTunnelTableEntry(dpnId, label, groupId, tx);
                            } else {
                                makeTunnelTableEntry(dpnId, label, localGroupId, tx);
                            }
                        } else {
                            LOG.debug("Route with rd {} prefix {} label {} nexthop {} for vpn {} is an imported "
                                            + "route. LFib and Terminating table entries will not be created.",
                                    rd, vrfEntry.getDestPrefix(), optLabel, nextHopAddressList, vpnId);
                        }
                    });
                }
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(tx.submit());
                return futures;
            });
            return dpnId;
        }
        LOG.error("localNextHopInfo received is null for prefix {} on rd {} on vpn {}", vrfEntry.getDestPrefix(), rd,
                vpnName);
        return BigInteger.ZERO;
    }

    private LabelRouteInfo getLabelRouteInfo(Long label) {
        InstanceIdentifier<LabelRouteInfo> lriIid = InstanceIdentifier.builder(LabelRouteMap.class)
            .child(LabelRouteInfo.class, new LabelRouteInfoKey(label)).build();
        Optional<LabelRouteInfo> opResult = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, lriIid);
        if (opResult.isPresent()) {
            return opResult.get();
        }
        return null;
    }

    private boolean deleteLabelRouteInfo(LabelRouteInfo lri, String vpnInstanceName, WriteTransaction tx) {
        LOG.debug("deleting LRI : for label {} vpninstancename {}", lri.getLabel(), vpnInstanceName);
        InstanceIdentifier<LabelRouteInfo> lriId = InstanceIdentifier.builder(LabelRouteMap.class)
            .child(LabelRouteInfo.class, new LabelRouteInfoKey(lri.getLabel())).build();
        if (lri == null) {
            return true;
        }
        List<String> vpnInstancesList = lri.getVpnInstanceList() != null
            ? lri.getVpnInstanceList() : new ArrayList<>();
        if (vpnInstancesList.contains(vpnInstanceName)) {
            LOG.debug("vpninstance {} name is present", vpnInstanceName);
            vpnInstancesList.remove(vpnInstanceName);
        }
        if (vpnInstancesList.isEmpty()) {
            LOG.debug("deleting LRI instance object for label {}", lri.getLabel());
            if (tx != null) {
                tx.delete(LogicalDatastoreType.OPERATIONAL, lriId);
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

    void makeTunnelTableEntry(BigInteger dpId, long label, long groupId/*String egressInterfaceName*/,
                                      WriteTransaction tx) {
        List<ActionInfo> actionsInfos = Collections.singletonList(new ActionGroup(groupId));

        createTerminatingServiceActions(dpId, (int) label, actionsInfos, tx);

        LOG.debug("Terminating service Entry for dpID {} : label : {} egress : {} installed successfully",
            dpId, label, groupId);
    }

    public void createTerminatingServiceActions(BigInteger destDpId, int label, List<ActionInfo> actionsInfos,
                                                WriteTransaction tx) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.debug("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}",
            destDpId, label, actionsInfos);

        // Matching metadata
        // FIXME vxlan vni bit set is not working properly with OVS.need to revisit
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(label)));

        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionApplyActions(actionsInfos));

        FlowEntity terminatingServiceTableFlowEntity =
            MDSALUtil.buildFlowEntity(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE,
            getTableMissFlowRef(destDpId, NwConstants.INTERNAL_TUNNEL_TABLE, label), 5,
                String.format("%s:%d", "TST Flow Entry ", label),
            0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(label)), mkMatches, mkInstructions);

        FlowKey flowKey = new FlowKey(new FlowId(terminatingServiceTableFlowEntity.getFlowId()));

        FlowBuilder flowbld = terminatingServiceTableFlowEntity.getFlowBuilder();

        Node nodeDpn = FibUtil.buildDpnNode(terminatingServiceTableFlowEntity.getDpnId());
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(terminatingServiceTableFlowEntity.getTableId()))
            .child(Flow.class, flowKey).build();
        tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flowbld.build(),
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    private void removeTunnelTableEntry(BigInteger dpId, long label, WriteTransaction tx) {
        FlowEntity flowEntity;
        LOG.debug("remove terminatingServiceActions called with DpnId = {} and label = {}", dpId, label);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(label)));
        flowEntity = MDSALUtil.buildFlowEntity(dpId,
            NwConstants.INTERNAL_TUNNEL_TABLE,
            getTableMissFlowRef(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, (int) label),
            5, String.format("%s:%d", "TST Flow Entry ", label), 0, 0,
            COOKIE_TUNNEL.add(BigInteger.valueOf(label)), mkMatches, null);
        Node nodeDpn = FibUtil.buildDpnNode(flowEntity.getDpnId());
        FlowKey flowKey = new FlowKey(new FlowId(flowEntity.getFlowId()));
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flowEntity.getTableId())).child(Flow.class, flowKey).build();

        tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
        LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully", dpId, label);
    }

    public List<BigInteger> deleteLocalFibEntry(Long vpnId, String rd, VrfEntry vrfEntry) {
        List<BigInteger> returnLocalDpnId = new ArrayList<>();
        Prefixes localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, vrfEntry.getDestPrefix());
        String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnId);
        boolean isExtraroute = false;
        if (localNextHopInfo == null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
            if (usedRds.size() > 1) {
                LOG.error("The extra route prefix {} is still present in some DPNs in vpn {} on rd {}",
                        vrfEntry.getDestPrefix(), vpnName, rd);
                return returnLocalDpnId;
            }
            //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency
            //in the vpn
            Optional<Routes> extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                    vpnName, rd, vrfEntry.getDestPrefix());
            if (extraRouteOptional.isPresent()) {
                isExtraroute = true;
                Routes extraRoute = extraRouteOptional.get();
                String ipPrefix;
                if (isIpv4Address(extraRoute.getNexthopIpList().get(0))) {
                    ipPrefix = extraRoute.getNexthopIpList().get(0) + NwConstants.IPV4PREFIX;
                } else {
                    ipPrefix = extraRoute.getNexthopIpList().get(0) + NwConstants.IPV6PREFIX;
                }
                localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, ipPrefix);
                if (localNextHopInfo != null) {
                    String localNextHopIP = localNextHopInfo.getIpAddress();
                    BigInteger dpnId = checkDeleteLocalFibEntry(localNextHopInfo, localNextHopIP,
                            vpnId, rd, vrfEntry, isExtraroute, vpnId /*parentVpnId*/);
                    if (!dpnId.equals(BigInteger.ZERO)) {
                        LOG.trace("Deleting ECMP group for prefix {}, dpn {}", vrfEntry.getDestPrefix(), dpnId);
                        nextHopManager.setupLoadBalancingNextHop(vpnId, dpnId,
                                vrfEntry.getDestPrefix(), /*listBucketInfo*/ Collections.emptyList(),
                                /*remove*/ false);
                        returnLocalDpnId.add(dpnId);
                    }
                } else {
                    LOG.error("localNextHopInfo unavailable while deleting prefix {} with rds {}, primary rd {} in "
                            + "vpn {}", vrfEntry.getDestPrefix(), usedRds, rd, vpnName);
                }
            }

            if (localNextHopInfo == null) {
                /* Imported VRF entry */
                java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                if (optionalLabel.isPresent()) {
                    Long label = optionalLabel.get();
                    List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                    LabelRouteInfo lri = getLabelRouteInfo(label);
                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {
                        PrefixesBuilder prefixBuilder = new PrefixesBuilder();
                        prefixBuilder.setDpnId(lri.getDpnId());
                        BigInteger dpnId = checkDeleteLocalFibEntry(prefixBuilder.build(), nextHopAddressList.get(0),
                                vpnId, rd, vrfEntry, isExtraroute, lri.getParentVpnid());
                        if (!dpnId.equals(BigInteger.ZERO)) {
                            returnLocalDpnId.add(dpnId);
                        }
                    }
                }
            }

        } else {
            LOG.debug("Obtained prefix to interface for rd {} prefix {}", rd, vrfEntry.getDestPrefix());
            String localNextHopIP = localNextHopInfo.getIpAddress();
            BigInteger dpnId = checkDeleteLocalFibEntry(localNextHopInfo, localNextHopIP,
                vpnId, rd, vrfEntry, isExtraroute, vpnId /*parentVpnId*/);
            if (!dpnId.equals(BigInteger.ZERO)) {
                returnLocalDpnId.add(dpnId);
            }
        }

        return returnLocalDpnId;
    }

    private BigInteger checkDeleteLocalFibEntry(Prefixes localNextHopInfo, final String localNextHopIP,
                                                final Long vpnId, final String rd, final VrfEntry vrfEntry,
                                                boolean isExtraroute, final Long parentVpnId) {
        if (localNextHopInfo != null) {
            final BigInteger dpnId = localNextHopInfo.getDpnId();
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
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getCreateLocalNextHopJobKey(vpnId, dpnId,
                    vrfEntry.getDestPrefix()), () -> {
                    WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                    baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null,
                            NwConstants.DEL_FLOW, tx, null);
                    if (!FibUtil.enforceVxlanDatapathSemanticsforInternalRouterVpn(dataBroker,
                            localNextHopInfo.getSubnetId(), vpnId, rd)) {
                        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                            FibUtil.getLabelFromRoutePaths(vrfEntry).ifPresent(label -> {
                                makeLFibTableEntry(dpnId, label, null /* instructions */, DEFAULT_FIB_FLOW_PRIORITY,
                                        NwConstants.DEL_FLOW, tx);
                                removeTunnelTableEntry(dpnId, label, tx);
                            });
                        }
                    }
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(tx.submit());
                    return futures;
                });
            //TODO: verify below adjacency call need to be optimized (?)
            //In case of the removal of the extra route, the loadbalancing group is updated
            if (!isExtraroute) {
                baseVrfEntryHandler.deleteLocalAdjacency(dpnId, parentVpnId, localNextHopIP, vrfEntry.getDestPrefix());
            }
            return dpnId;
        }
        return BigInteger.ZERO;
    }

    private void createRemoteFibEntry(final BigInteger remoteDpnId, final long vpnId, String rd,
            final VrfEntry vrfEntry, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnId);
        LOG.debug("createremotefibentry: adding route {} for rd {} on remoteDpnId {}",
                vrfEntry.getDestPrefix(), rd, remoteDpnId);

        List<AdjacencyResult> adjacencyResults = baseVrfEntryHandler.resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);
        if (adjacencyResults == null || adjacencyResults.isEmpty()) {
            LOG.error("Could not get interface for route-paths: {} in vpn {} on DPN {}",
                    vrfEntry.getRoutePaths(), rd, remoteDpnId);
            LOG.error("Failed to add Route: {} in vpn: {}", vrfEntry.getDestPrefix(), rd);
            return;
        }

        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
        List<Routes> vpnExtraRoutes = VpnExtraRouteHelper.getAllVpnExtraRoutes(dataBroker,
                vpnName, usedRds, vrfEntry.getDestPrefix());
        // create loadbalancing groups for extra routes only when the extra route is present behind
        // multiple VMs
        if (!vpnExtraRoutes.isEmpty() && (vpnExtraRoutes.size() > 1
                || vpnExtraRoutes.get(0).getNexthopIpList().size() > 1)) {
            List<InstructionInfo> instructions = new ArrayList<>();
            // Obtain the local routes for this particular dpn.
            java.util.Optional<Routes> routes = vpnExtraRoutes
                    .stream()
                    .filter(route -> {
                        Prefixes prefixToInterface = FibUtil.getPrefixToInterface(dataBroker,
                                vpnId, FibUtil.getIpPrefix(route.getNexthopIpList().get(0)));
                        if (prefixToInterface == null) {
                            return false;
                        }
                        return remoteDpnId.equals(prefixToInterface.getDpnId());
                    }).findFirst();
            long groupId = nextHopManager.createNextHopGroups(vpnId, rd, remoteDpnId, vrfEntry,
                    routes.isPresent() ? routes.get() : null, vpnExtraRoutes);
            if (groupId == FibConstants.INVALID_GROUP_ID) {
                LOG.error("Unable to create Group for local prefix {} on rd {} on Node {}",
                        vrfEntry.getDestPrefix(), rd, remoteDpnId.toString());
                return;
            }
            List<ActionInfo> actionInfos =
                    Collections.singletonList(new ActionGroup(groupId));
            instructions.add(new InstructionApplyActions(actionInfos));
            baseVrfEntryHandler.makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions,
                    NwConstants.ADD_FLOW, tx, null);
        } else {
            baseVrfEntryHandler.programRemoteFib(remoteDpnId, vpnId, vrfEntry, tx, rd, adjacencyResults, null);
        }

        if (!wrTxPresent) {
            tx.submit();
        }
        LOG.debug("Successfully added FIB entry for prefix {} in vpnId {}", vrfEntry.getDestPrefix(), vpnId);
    }

    protected void cleanUpOpDataForFib(Long vpnId, String primaryRd, final VrfEntry vrfEntry) {
    /* Get interface info from prefix to interface mapping;
        Use the interface info to get the corresponding vpn interface op DS entry,
        remove the adjacency corresponding to this fib entry.
        If adjacency removed is the last adjacency, clean up the following:
         - vpn interface from dpntovpn list, dpn if last vpn interface on dpn
         - prefix to interface entry
         - vpn interface op DS
     */
        LOG.debug("Cleanup of prefix {} in VPN {}", vrfEntry.getDestPrefix(), vpnId);
        Prefixes prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, vrfEntry.getDestPrefix());
        Routes extraRoute = null;
        if (prefixInfo == null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
            String usedRd = usedRds.isEmpty() ? primaryRd : usedRds.get(0);
            extraRoute = baseVrfEntryHandler.getVpnToExtraroute(vpnId, usedRd, vrfEntry.getDestPrefix());
            if (extraRoute != null) {
                for (String nextHopIp : extraRoute.getNexthopIpList()) {
                    LOG.debug("NextHop IP for destination {} is {}", vrfEntry.getDestPrefix(), nextHopIp);
                    if (nextHopIp != null) {
                        String ipPrefix;
                        if (isIpv4Address(nextHopIp)) {
                            ipPrefix = nextHopIp + NwConstants.IPV4PREFIX;
                        } else {
                            ipPrefix = nextHopIp + NwConstants.IPV6PREFIX;
                        }
                        prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, ipPrefix);
                        checkCleanUpOpDataForFib(prefixInfo, vpnId, primaryRd, vrfEntry, extraRoute);
                    }
                }
            }
            if (prefixInfo == null) {
                java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                if (optionalLabel.isPresent()) {
                    Long label = optionalLabel.get();
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
            checkCleanUpOpDataForFib(prefixInfo, vpnId, primaryRd, vrfEntry, extraRoute);
        }
    }

    private void checkCleanUpOpDataForFib(final Prefixes prefixInfo, final Long vpnId, final String rd,
                                          final VrfEntry vrfEntry, final Routes extraRoute) {

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
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + ifName,
            new CleanupVpnInterfaceWorker(prefixInfo, vpnId, rd, vrfEntry, extraRoute));
    }

    private class CleanupVpnInterfaceWorker implements Callable<List<ListenableFuture<Void>>> {
        Prefixes prefixInfo;
        Long vpnId;
        String rd;
        VrfEntry vrfEntry;
        Routes extraRoute;

        CleanupVpnInterfaceWorker(final Prefixes prefixInfo, final Long vpnId, final String rd,
                                         final VrfEntry vrfEntry, final Routes extraRoute) {
            this.prefixInfo = prefixInfo;
            this.vpnId = vpnId;
            this.rd = rd;
            this.vrfEntry = vrfEntry;
            this.extraRoute = extraRoute;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            // If another renderer(for eg : CSS) needs to be supported, check can be performed here
            // to call the respective helpers.
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();

            //First Cleanup LabelRouteInfo
            //TODO(KIRAN) : Move the below block when addressing iRT/eRT for L3VPN Over VxLan
            LOG.debug("cleanupVpnInterfaceWorker: rd {} prefix {}", rd, prefixInfo.getIpAddress());
            if (VrfEntry.EncapType.Mplsgre.equals(vrfEntry.getEncapType())) {
                FibUtil.getLabelFromRoutePaths(vrfEntry).ifPresent(label -> {
                    List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                    synchronized (label.toString().intern()) {
                        LabelRouteInfo lri = getLabelRouteInfo(label);
                        if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix())
                                && nextHopAddressList.contains(lri.getNextHopIpList().get(0))) {
                            Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                    FibUtil.getVpnInstanceOpData(dataBroker, rd);
                            String vpnInstanceName = "";
                            if (vpnInstanceOpDataEntryOptional.isPresent()) {
                                vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                            }
                            boolean lriRemoved = deleteLabelRouteInfo(lri, vpnInstanceName, writeOperTxn);
                            if (lriRemoved) {
                                String parentRd = lri.getParentVpnRd();
                                FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                                        FibUtil.getNextHopLabelKey(parentRd, vrfEntry.getDestPrefix()));
                            }
                        } else {
                            FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                                    FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
                        }
                    }
                });
            }
            String ifName = prefixInfo.getVpnInterfaceName();
            Optional<VpnInterface> optvpnInterface = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                FibUtil.getVpnInterfaceIdentifier(ifName));
            if (Prefixes.PrefixCue.PhysNetFunc.equals(prefixInfo.getPrefixCue())) {
                /*Get vpnId for rd = networkId since op vpnInterface will be pointing to rd = networkId
                * */
                Optional<String> vpnName = FibUtil.getVpnNameFromRd(dataBroker, vrfEntry.getParentVpnRd());
                if (vpnName.isPresent()) {
                    vpnId = FibUtil.getVpnId(dataBroker, vpnName.get());
                }
            }
            if (optvpnInterface.isPresent()) {
                long associatedVpnId = FibUtil.getVpnId(dataBroker, optvpnInterface.get().getVpnInstanceName());
                if (vpnId != associatedVpnId) {
                    LOG.warn("Prefixes {} are associated with different vpn instance with id : {} rather than {}",
                        vrfEntry.getDestPrefix(), associatedVpnId, vpnId);
                    LOG.warn("Not proceeding with Cleanup op data for prefix {}", vrfEntry.getDestPrefix());
                    return null;
                } else {
                    LOG.debug("Processing cleanup of prefix {} associated with vpn {}",
                        vrfEntry.getDestPrefix(), associatedVpnId);
                }
            }
            if (extraRoute != null) {
                Optional<String> optVpnName = FibUtil.getVpnNameFromRd(dataBroker, rd);
                List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, vrfEntry.getDestPrefix());
                //Only one used Rd present in case of removal event
                String usedRd = usedRds.get(0);
                if (optVpnName.isPresent()) {
                    writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                            baseVrfEntryHandler.getVpnToExtrarouteIdentifier(optVpnName.get(), usedRd,
                                    vrfEntry.getDestPrefix()));
                    writeOperTxn.delete(LogicalDatastoreType.CONFIGURATION,
                            VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, vrfEntry.getDestPrefix()));
                }
            }
            Optional<Adjacencies> optAdjacencies = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                FibUtil.getAdjListPath(ifName));
            int numAdj = 0;
            if (optAdjacencies.isPresent()) {
                numAdj = optAdjacencies.get().getAdjacency().size();
            }
            //remove adjacency corr to prefix
            if (numAdj > 1) {
                LOG.info("cleanUpOpDataForFib: remove adjacency for prefix: {} {}", vpnId,
                        vrfEntry.getDestPrefix());
                writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                        FibUtil.getAdjacencyIdentifier(ifName, vrfEntry.getDestPrefix()));
            } else {
                //this is last adjacency (or) no more adjacency left for this vpn interface, so
                //clean up the vpn interface from DpnToVpn list
                LOG.info("Clean up vpn interface {} from dpn {} to vpn {} list.", ifName, prefixInfo.getDpnId(), rd);
                writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, FibUtil.getVpnInterfaceIdentifier(ifName));
            }
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            futures.add(writeOperTxn.submit());
            return futures;
        }
    }

    private void deleteFibEntries(final InstanceIdentifier<VrfEntry> identifier, final VrfEntry vrfEntry) {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        final String rd = vrfTableKey.getRouteDistinguisher();
        final VpnInstanceOpDataEntry vpnInstance =
                FibUtil.getVpnInstance(dataBroker, vrfTableKey.getRouteDistinguisher());
        if (vpnInstance == null) {
            LOG.error("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        final Collection<VpnToDpnList> vpnToDpnList;
        if (vrfEntry.getParentVpnRd() != null
                && FibHelper.isControllerManagedNonSelfImportedRoute(RouteOrigin.value(vrfEntry.getOrigin()))) {
            // This block MUST BE HIT only for PNF (Physical Network Function) FIB Entries.
            VpnInstanceOpDataEntry parentVpnInstance = FibUtil.getVpnInstance(dataBroker, vrfEntry.getParentVpnRd());
            vpnToDpnList = parentVpnInstance != null ? parentVpnInstance.getVpnToDpnList() :
                    vpnInstance.getVpnToDpnList();
            LOG.info("deleteFibEntries: Processing deletion of PNF FIB entry with rd {} prefix {}",
                    vrfEntry.getParentVpnRd(), vrfEntry.getDestPrefix());
        } else {
            vpnToDpnList = vpnInstance.getVpnToDpnList();
        }

        SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
        final java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
        List<String> nextHopAddressList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
        String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnInstance.getVpnId());
        if (subnetRoute != null) {
            long elanTag = subnetRoute.getElantag();
            LOG.trace("SUBNETROUTE: deleteFibEntries: SubnetRoute augmented vrfentry found for rd {} prefix {}"
                    + " with elantag {}", rd, vrfEntry.getDestPrefix(), elanTag);
            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix()),
                    () -> {
                        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

                        for (final VpnToDpnList curDpn : vpnToDpnList) {

                            baseVrfEntryHandler.makeConnectedRoute(curDpn.getDpnId(), vpnInstance.getVpnId(), vrfEntry,
                                vrfTableKey.getRouteDistinguisher(), null, NwConstants.DEL_FLOW, tx, null);
                            if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
                                optionalLabel.ifPresent(label -> makeLFibTableEntry(curDpn.getDpnId(), label, null,
                                        DEFAULT_FIB_FLOW_PRIORITY, NwConstants.DEL_FLOW, tx));
                            }

                            installSubnetBroadcastAddrDropRule(curDpn.getDpnId(), rd, vpnInstance.getVpnId(),
                                    vrfEntry, NwConstants.DEL_FLOW, tx);
                        }
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        futures.add(tx.submit());
                        return futures;
                    });
            }
            optionalLabel.ifPresent(label -> {
                synchronized (label.toString().intern()) {
                    LabelRouteInfo lri = getLabelRouteInfo(label);
                    if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopAddressList, lri)) {
                        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
                                FibUtil.getVpnInstanceOpData(dataBroker, rd);
                        String vpnInstanceName = "";
                        if (vpnInstanceOpDataEntryOptional.isPresent()) {
                            vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                        }
                        boolean lriRemoved = this.deleteLabelRouteInfo(lri, vpnInstanceName, null);
                        if (lriRemoved) {
                            String parentRd = lri.getParentVpnRd();
                            FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                                    FibUtil.getNextHopLabelKey(parentRd, vrfEntry.getDestPrefix()));
                            LOG.trace("SUBNETROUTE: deleteFibEntries: Released subnetroute label {} for rd {} prefix {}"
                                    + " as labelRouteInfo cleared", label, rd, vrfEntry.getDestPrefix());
                        }
                    } else {
                        FibUtil.releaseId(idManager, FibConstants.VPN_IDPOOL_NAME,
                                FibUtil.getNextHopLabelKey(rd, vrfEntry.getDestPrefix()));
                        LOG.trace("SUBNETROUTE: deleteFibEntries: Released subnetroute label {} for rd {} prefix {}",
                                label, rd, vrfEntry.getDestPrefix());
                    }
                }
            });
            return;
        }

        final List<BigInteger> localDpnIdList = deleteLocalFibEntry(vpnInstance.getVpnId(),
            vrfTableKey.getRouteDistinguisher(), vrfEntry);
        if (vpnToDpnList != null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker,
                    vpnInstance.getVpnId(), vrfEntry.getDestPrefix());
            String jobKey;
            Optional<Routes> extraRouteOptional;
            //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
            if (usedRds != null && !usedRds.isEmpty()) {
                jobKey = FibUtil.getJobKeyForRdPrefix(usedRds.get(0), vrfEntry.getDestPrefix());
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
                jobKey = FibUtil.getJobKeyForRdPrefix(rd, vrfEntry.getDestPrefix());
                extraRouteOptional = Optional.absent();
            }
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(jobKey,
                () -> {
                    WriteTransaction tx = dataBroker.newWriteOnlyTransaction();

                    if (localDpnIdList.size() <= 0) {
                        for (VpnToDpnList curDpn : vpnToDpnList) {
                            baseVrfEntryHandler.deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(),
                                vpnInstance.getVpnId(), vrfTableKey, vrfEntry, extraRouteOptional, tx);
                        }
                    } else {
                        for (BigInteger localDpnId : localDpnIdList) {
                            for (VpnToDpnList curDpn : vpnToDpnList) {
                                if (!curDpn.getDpnId().equals(localDpnId)) {
                                    baseVrfEntryHandler.deleteRemoteRoute(localDpnId, curDpn.getDpnId(),
                                        vpnInstance.getVpnId(), vrfTableKey, vrfEntry, extraRouteOptional, tx);
                                }
                            }
                        }
                    }
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    futures.add(tx.submit());
                    return futures;
                }, DJC_MAX_RETRIES);
        }

        //The flow/group entry has been deleted from config DS; need to clean up associated operational
        //DS entries in VPN Op DS, VpnInstanceOpData and PrefixToInterface to complete deletion
        cleanUpOpDataForFib(vpnInstance.getVpnId(), vrfTableKey.getRouteDistinguisher(), vrfEntry);

        // Remove all fib entries configured due to interVpnLink, when nexthop is the opposite endPoint
        // of the interVpnLink.
        Optional<String> optVpnUuid = FibUtil.getVpnNameFromRd(this.dataBroker, rd);
        if (optVpnUuid.isPresent()) {
            String vpnUuid = optVpnUuid.get();
            FibUtil.getFirstNextHopAddress(vrfEntry).ifPresent(routeNexthop -> {
                Optional<InterVpnLinkDataComposite> optInterVpnLink = InterVpnLinkCache.getInterVpnLinkByVpnId(vpnUuid);
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

    private void makeLFibTableEntry(BigInteger dpId, long label, List<InstructionInfo> instructions, int priority,
                                    int addOrRemove, WriteTransaction tx) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(label));

        // Install the flow entry in L3_LFIB_TABLE
        LOG.info("makeLFibTableEntry: dpnId {} label {} priority {}", dpId, label, priority);
        String flowRef = FibUtil.getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, label, priority);

        FlowEntity flowEntity;
        flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_LFIB_TABLE, flowRef, priority, flowRef, 0, 0,
            NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructions);
        Flow flow = flowEntity.getFlowBuilder().build();
        String flowId = flowEntity.getFlowId();
        FlowKey flowKey = new FlowKey(new FlowId(flowId));
        Node nodeDpn = FibUtil.buildDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
            .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flowKey).build();

        if (addOrRemove == NwConstants.ADD_FLOW) {
            tx.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flow, WriteTransaction.CREATE_MISSING_PARENTS);
        } else {
            tx.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
        }
        if (!wrTxPresent) {
            tx.submit();
        }

        LOG.debug("LFIB Entry for dpID {} : label : {} instructions {} : key {} {} successfully",
            dpId, label, instructions, flowKey, NwConstants.ADD_FLOW == addOrRemove ? "ADDED" : "REMOVED");
    }

    public void populateFibOnNewDpn(final BigInteger dpnId, final long vpnId, final String rd,
                                    final FutureCallback<List<Void>> callback) {
        LOG.trace("New dpn {} for vpn {} : populateFibOnNewDpn", dpnId, rd);
        InstanceIdentifier<VrfTables> id = buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);
        final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        if (!vrfTable.isPresent()) {
            LOG.info("populateFibOnNewDpn: dpn: {}: VRF Table not yet available for RD {}", dpnId, rd);
            if (callback != null) {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
                Futures.addCallback(listenableFuture, callback);
            }
            return;
        }
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
            () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                synchronized (vpnInstance.getVpnInstanceName().intern()) {
                    WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                    for (final VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
                        SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
                        if (subnetRoute != null) {
                            long elanTag = subnetRoute.getElantag();
                            installSubnetRouteInFib(dpnId, elanTag, rd, vpnId, vrfEntry, tx);
                            installSubnetBroadcastAddrDropRule(dpnId, rd, vpnId, vrfEntry, NwConstants.ADD_FLOW, tx);
                            continue;
                        }
                        RouterInterface routerInt = vrfEntry.getAugmentation(RouterInterface.class);
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
                            java.util.Optional<Long> optionalLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);
                            if (optionalLabel.isPresent()) {
                                List<String> nextHopList = FibHelper.getNextHopListFromRoutePaths(vrfEntry);
                                LabelRouteInfo lri = getLabelRouteInfo(optionalLabel.get());
                                if (isPrefixAndNextHopPresentInLri(vrfEntry.getDestPrefix(), nextHopList, lri)) {
                                    if (lri.getDpnId().equals(dpnId)) {
                                        createLocalFibEntry(vpnId, rd, vrfEntry);
                                        continue;
                                    }
                                }
                            }
                        }

                        boolean shouldCreateRemoteFibEntry = shouldCreateFibEntryForVrfAndVpnIdOnDpn(vpnId,
                                vrfEntry, dpnId);
                        if (shouldCreateRemoteFibEntry) {
                            LOG.trace("Will create remote FIB entry for vrfEntry {} on DPN {}",
                                    vrfEntry, dpnId);
                            if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin())) {
                                bgpRouteVrfEntryHandler.createRemoteFibEntry(dpnId, vpnId,
                                        vrfTable.get().getRouteDistinguisher(), vrfEntry, tx, txnObjects);
                            } else {
                                createRemoteFibEntry(dpnId, vpnId, vrfTable.get().getRouteDistinguisher(),
                                        vrfEntry, tx);
                            }
                        }
                    }
                    //TODO: if we have 100K entries in FIB, can it fit in one Tranasaction (?)
                    futures.add(tx.submit());
                }
                if (callback != null) {
                    ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
                    Futures.addCallback(listenableFuture, callback);
                }
                return futures;
            });
    }

    public void populateExternalRoutesOnDpn(final BigInteger dpnId, final long vpnId, final String rd,
                                            final String localNextHopIp, final String remoteNextHopIp) {
        LOG.trace("populateExternalRoutesOnDpn : dpn {}, vpn {}, rd {}, localNexthopIp {} , remoteNextHopIp {} ",
            dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
        InstanceIdentifier<VrfTables> id = buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (vrfTable.isPresent()) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    synchronized (vpnInstance.getVpnInstanceName().intern()) {
                        WriteTransaction writeCfgTxn = dataBroker.newWriteOnlyTransaction();
                        vrfTable.get().getVrfEntry().stream()
                            .filter(vrfEntry -> RouteOrigin.BGP == RouteOrigin.value(vrfEntry.getOrigin()))
                            .forEach(bgpRouteVrfEntryHandler.getConsumerForCreatingRemoteFib(dpnId, vpnId,
                                        rd, remoteNextHopIp, vrfTable, writeCfgTxn, txnObjects));
                        futures.add(writeCfgTxn.submit());
                    }
                    return futures;
                });
        }
    }

    public void manageRemoteRouteOnDPN(final boolean action,
                                       final BigInteger localDpnId,
                                       final long vpnId,
                                       final String rd,
                                       final String destPrefix,
                                       final String destTepIp,
                                       final long label) {
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);

        if (vpnInstance == null) {
            LOG.error("VpnInstance for rd {} not present for prefix {}", rd, destPrefix);
            return;
        }
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, localDpnId),
            () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                synchronized (vpnInstance.getVpnInstanceName().intern()) {
                    WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
                    VrfTablesKey vrfTablesKey = new VrfTablesKey(rd);
                    VrfEntry vrfEntry = getVrfEntry(dataBroker, rd, destPrefix);
                    if (vrfEntry == null) {
                        return futures;
                    }
                    LOG.trace("manageRemoteRouteOnDPN :: action {}, DpnId {}, vpnId {}, rd {}, destPfx {}",
                        action, localDpnId, vpnId, rd, destPrefix);
                    List<RoutePaths> routePathList = vrfEntry.getRoutePaths();
                    VrfEntry modVrfEntry;
                    if (routePathList == null || routePathList.isEmpty()) {
                        modVrfEntry = FibHelper.getVrfEntryBuilder(vrfEntry, label,
                                Collections.singletonList(destTepIp),
                                RouteOrigin.value(vrfEntry.getOrigin()), null /* parentVpnRd */).build();
                    } else {
                        modVrfEntry = vrfEntry;
                    }

                    if (action == true) {
                        LOG.trace("manageRemoteRouteOnDPN updated(add)  vrfEntry :: {}", modVrfEntry);
                        createRemoteFibEntry(localDpnId, vpnId, vrfTablesKey.getRouteDistinguisher(),
                                modVrfEntry, writeTransaction);
                    } else {
                        LOG.trace("manageRemoteRouteOnDPN updated(remove)  vrfEntry :: {}", modVrfEntry);
                        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnInstance.getVpnId(),
                                vrfEntry.getDestPrefix());
                        if (usedRds.size() > 1) {
                            LOG.debug("The extra route prefix is still present in some DPNs");
                            return futures;
                        }
                        //Is this fib route an extra route? If yes, get the nexthop which would be
                        //an adjacency in the vpn
                        Optional<Routes> extraRouteOptional = Optional.absent();
                        if (usedRds.size() != 0) {
                            extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                                    FibUtil.getVpnNameFromId(dataBroker, vpnInstance.getVpnId()),
                                    usedRds.get(0), vrfEntry.getDestPrefix());
                        }
                        baseVrfEntryHandler.deleteRemoteRoute(null, localDpnId, vpnId, vrfTablesKey, modVrfEntry,
                                extraRouteOptional, writeTransaction);
                    }
                    futures.add(writeTransaction.submit());
                }
                return futures;
            });
    }

    public void cleanUpDpnForVpn(final BigInteger dpnId, final long vpnId, final String rd,
                                 final FutureCallback<List<Void>> callback) {
        LOG.trace("cleanUpDpnForVpn: Remove dpn {} for vpn {} : cleanUpDpnForVpn", dpnId, rd);
        InstanceIdentifier<VrfTables> id = buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (vrfTable.isPresent()) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    synchronized (vpnInstance.getVpnInstanceName().intern()) {
                        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                        for (final VrfEntry vrfEntry : vrfTable.get().getVrfEntry()) {
                                /* Handle subnet routes here */
                            SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);
                            if (subnetRoute != null) {
                                LOG.trace("SUBNETROUTE: cleanUpDpnForVpn: Cleaning subnetroute {} on dpn {}"
                                        + " for vpn {}", vrfEntry.getDestPrefix(), dpnId, rd);
                                baseVrfEntryHandler.makeConnectedRoute(dpnId, vpnId, vrfEntry, rd, null,
                                        NwConstants.DEL_FLOW, tx, null);
                                List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
                                if (routePaths != null) {
                                    for (RoutePaths routePath : routePaths) {
                                        makeLFibTableEntry(dpnId, routePath.getLabel(), null,
                                                DEFAULT_FIB_FLOW_PRIORITY,
                                                NwConstants.DEL_FLOW, tx);
                                        LOG.trace("SUBNETROUTE: cleanUpDpnForVpn: Released subnetroute label {} for"
                                                + " rd {} prefix {}", routePath.getLabel(), rd,
                                                vrfEntry.getDestPrefix());
                                    }
                                }
                                installSubnetBroadcastAddrDropRule(dpnId, rd, vpnId, vrfEntry,
                                        NwConstants.DEL_FLOW, tx);
                                continue;
                            }
                            // ping responder for router interfaces
                            RouterInterface routerInt = vrfEntry.getAugmentation(RouterInterface.class);
                            if (routerInt != null) {
                                LOG.trace("Router augmented vrfentry found for rd:{}, uuid:{}, ip:{}, mac:{}",
                                    rd, routerInt.getUuid(), routerInt.getIpAddress(), routerInt.getMacAddress());
                                routerInterfaceVrfEntryHandler.installRouterFibEntry(vrfEntry, dpnId, vpnId,
                                        routerInt.getIpAddress(), new MacAddress(routerInt.getMacAddress()),
                                        NwConstants.DEL_FLOW);
                                continue;
                            }
                            // Passing null as we don't know the dpn
                            // to which prefix is attached at this point
                            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnInstance.getVpnId(),
                                    vrfEntry.getDestPrefix());
                            String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnInstance.getVpnId());
                            Optional<Routes> extraRouteOptional;
                            //Is this fib route an extra route? If yes, get the nexthop which would be
                            //an adjacency in the vpn
                            if (usedRds != null && !usedRds.isEmpty()) {
                                if (usedRds.size() > 1) {
                                    LOG.error("The extra route prefix is still present in some DPNs");
                                    return futures;
                                } else {
                                    extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker, vpnName,
                                            usedRds.get(0), vrfEntry.getDestPrefix());

                                }
                            } else {
                                extraRouteOptional = Optional.absent();
                            }
                            if (RouteOrigin.BGP.getValue().equals(vrfEntry.getOrigin())) {
                                bgpRouteVrfEntryHandler.deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(),
                                        vrfEntry, extraRouteOptional, tx, txnObjects);
                            } else {
                                baseVrfEntryHandler.deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(),
                                        vrfEntry, extraRouteOptional, tx);
                            }
                        }
                        futures.add(tx.submit());
                        if (callback != null) {
                            ListenableFuture<List<Void>> listenableFuture = Futures.allAsList(futures);
                            Futures.addCallback(listenableFuture, callback);
                        }
                    }
                    return futures;
                });
        }
    }

    public void cleanUpExternalRoutesOnDpn(final BigInteger dpnId, final long vpnId, final String rd,
                                           final String localNextHopIp, final String remoteNextHopIp) {
        LOG.trace("cleanUpExternalRoutesOnDpn : cleanup remote routes on dpn {} for vpn {}, rd {}, "
                + " localNexthopIp {} , remoteNexhtHopIp {}",
            dpnId, vpnId, rd, localNextHopIp, remoteNextHopIp);
        InstanceIdentifier<VrfTables> id = buildVrfId(rd);
        final VpnInstanceOpDataEntry vpnInstance = FibUtil.getVpnInstance(dataBroker, rd);
        List<SubTransaction> txnObjects =  new ArrayList<>();
        final Optional<VrfTables> vrfTable = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (vrfTable.isPresent()) {
            DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob(FibUtil.getJobKeyForVpnIdDpnId(vpnId, dpnId),
                () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    synchronized (vpnInstance.getVpnInstanceName().intern()) {
                        WriteTransaction writeCfgTxn = dataBroker.newWriteOnlyTransaction();
                        vrfTable.get().getVrfEntry().stream()
                            .filter(vrfEntry -> RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP)
                            .forEach(bgpRouteVrfEntryHandler.getConsumerForDeletingRemoteFib(dpnId, vpnId, rd,
                                remoteNextHopIp, vrfTable, writeCfgTxn, txnObjects));
                        futures.add(writeCfgTxn.submit());
                    }
                    return futures;
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

    private String getTableMissFlowRef(BigInteger dpnId, short tableId, int tableMiss) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR
                + tableMiss + FLOWID_PREFIX;
    }

    private VrfEntry getVrfEntry(DataBroker broker, String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = InstanceIdentifier.builder(FibEntries.class)
            .child(VrfTables.class, new VrfTablesKey(rd))
            .child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        Optional<VrfEntry> vrfEntry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
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
        List<BigInteger> targetDpns = interVpnLink.getEndpointDpnsByVpnName(vpnName);

        if (targetDpns.isEmpty()) {
            LOG.warn("Could not find DPNs for VPN {} in InterVpnLink {}", vpnName, interVpnLinkName);
            return;
        }

        java.util.Optional<String> optNextHop = FibUtil.getFirstNextHopAddress(vrfEntry);
        java.util.Optional<Long> optLabel = FibUtil.getLabelFromRoutePaths(vrfEntry);

        // delete from FIB
        //
        optNextHop.ifPresent(nextHop -> {
            String flowRef = getInterVpnFibFlowRef(interVpnLinkName, vrfEntry.getDestPrefix(), nextHop);
            FlowKey flowKey = new FlowKey(new FlowId(flowRef));
            Flow flow = new FlowBuilder().setKey(flowKey).setId(new FlowId(flowRef))
                    .setTableId(NwConstants.L3_FIB_TABLE).setFlowName(flowRef).build();

            LOG.trace("Removing flow in FIB table for interVpnLink {} key {}", interVpnLinkName, flowRef);
            for (BigInteger dpId : targetDpns) {
                LOG.debug("Removing flow: VrfEntry=[prefix={} nexthop={}] dpn {} for InterVpnLink {} in FIB",
                          vrfEntry.getDestPrefix(), nextHop, dpId, interVpnLinkName);

                mdsalManager.removeFlow(dpId, flow);
            }
        });

        // delete from LFIB
        //
        optLabel.ifPresent(label -> {
            LOG.trace("Removing flow in FIB table for interVpnLink {}", interVpnLinkName);

            WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
            for (BigInteger dpId : targetDpns) {
                LOG.debug("Removing flow: VrfEntry=[prefix={} label={}] dpn {} for InterVpnLink {} in LFIB",
                          vrfEntry.getDestPrefix(), label, dpId, interVpnLinkName);
                makeLFibTableEntry(dpId, label, /*instructions*/null, LFIB_INTERVPN_PRIORITY, NwConstants.DEL_FLOW, tx);
            }
            tx.submit();
        });
    }

    private boolean isPrefixAndNextHopPresentInLri(String prefix,
            List<String> nextHopAddressList, LabelRouteInfo lri) {
        return lri != null && lri.getPrefix().equals(prefix)
                && nextHopAddressList.contains(lri.getNextHopIpList().get(0));
    }

    private boolean shouldCreateFibEntryForVrfAndVpnIdOnDpn(Long vpnId, VrfEntry vrfEntry, BigInteger dpnId) {
        if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.BGP) {
            return true;
        }

        Prefixes prefix = FibUtil.getPrefixToInterface(dataBroker, vpnId, vrfEntry.getDestPrefix());
        if (prefix != null) {
            BigInteger prefixDpnId = prefix.getDpnId();
            if (dpnId.equals(prefixDpnId)) {
                LOG.trace("Should not create remote FIB entry for vrfEntry {} on DPN {}",
                        vrfEntry, dpnId);
                return false;
            }
        }
        return true;
    }
}
