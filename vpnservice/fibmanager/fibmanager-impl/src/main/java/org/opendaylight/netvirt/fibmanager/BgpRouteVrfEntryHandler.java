/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.genius.utils.batching.SubTransaction;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class BgpRouteVrfEntryHandler extends VrfEntryHandler implements AutoCloseable, ResourceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BgpRouteVrfEntryHandler.class);
    private final DataBroker dataBroker;

    private static final int BATCH_INTERVAL = 500;
    private static final int BATCH_SIZE = 1000;
    private final BlockingQueue<ActionableResource> vrfEntryBufferQ = new LinkedBlockingQueue<>();
    private final ResourceBatchingManager resourceBatchingManager;
    private final NexthopManager nexthopManager;

    @Inject
    public BgpRouteVrfEntryHandler(final DataBroker dataBroker,
                                   final NexthopManager nexthopManager) {
        super(dataBroker, nexthopManager);
        this.dataBroker = dataBroker;
        this.nexthopManager = nexthopManager;

        resourceBatchingManager = ResourceBatchingManager.getInstance();
        resourceBatchingManager.registerBatchableResource("FIB-VRFENTRY", vrfEntryBufferQ, this);
    }

    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    public DataBroker getResourceBroker() {
        return dataBroker;
    }

    @Override
    public int getBatchSize() {
        return BATCH_SIZE;
    }

    @Override
    public int getBatchInterval() {
        return BATCH_INTERVAL;
    }

    @Override
    public LogicalDatastoreType getDatastoreType() {
        return LogicalDatastoreType.CONFIGURATION;
    }

    @Override
    public void update(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier,
                       Object original, Object update, List<SubTransaction> transactionObjects) {
        if ((original instanceof VrfEntry) && (update instanceof VrfEntry)) {
            createFibEntries(tx, identifier, (VrfEntry) update, transactionObjects);
        }
    }

    @Override
    public void create(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier,
                       Object vrfEntry, List<SubTransaction> transactionObjects) {
        if (vrfEntry instanceof VrfEntry) {
            createFibEntries(tx, identifier, (VrfEntry) vrfEntry, transactionObjects);
        }
    }

    @Override
    public void delete(WriteTransaction tx, LogicalDatastoreType datastoreType, InstanceIdentifier identifier,
                       Object vrfEntry, List<SubTransaction> transactionObjects) {
        if (vrfEntry instanceof VrfEntry) {
            deleteFibEntries(tx, identifier, (VrfEntry) vrfEntry, transactionObjects);
        }
    }

    public void createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        ActionableResource actResource = new ActionableResourceImpl(rd + vrfEntry.getDestPrefix());
        actResource.setAction(ActionableResource.CREATE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(vrfEntry);
        vrfEntryBufferQ.add(actResource);
    }

    public void removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd) {
        ActionableResource actResource = new ActionableResourceImpl(rd + vrfEntry.getDestPrefix());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(vrfEntry);
        vrfEntryBufferQ.add(actResource);
    }

    public void updateFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update, String rd) {
        ActionableResource actResource = new ActionableResourceImpl(rd + update.getDestPrefix());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(identifier);
        actResource.setInstance(update);
        actResource.setOldInstance(original);
        vrfEntryBufferQ.add(actResource);
    }

    /*
      Please note that the following createFibEntries will be invoked only for BGP Imported Routes.
      The invocation of the following method is via create() callback from the MDSAL Batching Infrastructure
      provided by ResourceBatchingManager
     */
    private void createFibEntries(WriteTransaction writeTx, final InstanceIdentifier<VrfEntry> vrfEntryIid,
                                  final VrfEntry vrfEntry, List<SubTransaction> txnObjects) {
        final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);

        final VpnInstanceOpDataEntry vpnInstance =
                FibUtil.getVpnInstance(dataBroker, vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance, "Vpn Instance not available " + vrfTableKey.getRouteDistinguisher());
        Preconditions.checkNotNull(vpnInstance.getVpnId(), "Vpn Instance with rd " + vpnInstance.getVrfId()
                + " has null vpnId!");

        final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            for (VpnToDpnList vpnDpn : vpnToDpnList) {
                if (vpnDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                    createRemoteFibEntry(vpnDpn.getDpnId(), vpnInstance.getVpnId(), vrfTableKey.getRouteDistinguisher(),
                            vrfEntry, writeTx, txnObjects);
                }
            }
        }
    }

    /*
      Please note that the following deleteFibEntries will be invoked only for BGP Imported Routes.
      The invocation of the following method is via delete() callback from the MDSAL Batching Infrastructure
      provided by ResourceBatchingManager
     */
    private void deleteFibEntries(WriteTransaction writeTx, final InstanceIdentifier<VrfEntry> identifier,
                                  final VrfEntry vrfEntry, List<SubTransaction> txnObjects) {
        final VrfTablesKey vrfTableKey = identifier.firstKeyOf(VrfTables.class);
        String rd = vrfTableKey.getRouteDistinguisher();
        final VpnInstanceOpDataEntry vpnInstance =
                FibUtil.getVpnInstance(dataBroker, vrfTableKey.getRouteDistinguisher());
        if (vpnInstance == null) {
            LOG.debug("VPN Instance for rd {} is not available from VPN Op Instance Datastore", rd);
            return;
        }
        String vpnName = FibUtil.getVpnNameFromId(dataBroker, vpnInstance.getVpnId());
        final Collection<VpnToDpnList> vpnToDpnList = vpnInstance.getVpnToDpnList();
        if (vpnToDpnList != null) {
            List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker,
                    vpnInstance.getVpnId(), vrfEntry.getDestPrefix());
            Optional<Routes> extraRouteOptional;
            //Is this fib route an extra route? If yes, get the nexthop which would be an adjacency in the vpn
            if (usedRds != null && !usedRds.isEmpty()) {
                if (usedRds.size() > 1) {
                    LOG.error("The extra route prefix is still present in some DPNs");
                    return ;
                } else {
                    extraRouteOptional = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker, vpnName,
                            usedRds.get(0), vrfEntry.getDestPrefix());
                }
            } else {
                extraRouteOptional = Optional.absent();
            }
            for (VpnToDpnList curDpn : vpnToDpnList) {
                if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                    deleteRemoteRoute(BigInteger.ZERO, curDpn.getDpnId(), vpnInstance.getVpnId(),
                            vrfTableKey, vrfEntry, extraRouteOptional, writeTx, txnObjects);
                }
            }
        }
    }

    public void programRemoteFibForBgpRoutes(final BigInteger remoteDpnId,
                                             final long vpnId,
                                             final VrfEntry vrfEntry,
                                             WriteTransaction tx,
                                             String rd,
                                             List<NexthopManager.AdjacencyResult> adjacencyResults,
                                             List<SubTransaction> txnObjects) {
        Preconditions.checkArgument(vrfEntry.getRoutePaths().size() <= 2);

        if (adjacencyResults.size() == 1) {
            programRemoteFib(remoteDpnId, vpnId, vrfEntry, tx, rd, adjacencyResults, txnObjects);
            return;
        }
        // ECMP Use case, point to LB group. Move the mpls label accordingly.
        List<String> tunnelList =
                adjacencyResults.stream()
                        .map(adjacencyResult -> adjacencyResult.getNextHopIp())
                        .sorted().collect(toList());
        String lbGroupKey = FibUtil.getGreLbGroupKey(tunnelList);
        long groupId = nexthopManager.createNextHopPointer(lbGroupKey);
        int index = 0;
        List<ActionInfo> actionInfos = new ArrayList<>();
        for (NexthopManager.AdjacencyResult adjResult : adjacencyResults) {
            String nextHopIp = adjResult.getNextHopIp();
            java.util.Optional<Long> optionalLabel = FibUtil.getLabelForNextHop(vrfEntry, nextHopIp);
            if (!optionalLabel.isPresent()) {
                LOG.warn("NextHopIp {} not found in vrfEntry {}", nextHopIp, vrfEntry);
                continue;
            }
            long label = optionalLabel.get();

            actionInfos.add(new ActionRegLoad(index, FibConstants.NXM_REG_MAPPING.get(index++), 0,
                    31, label));
        }
        List<InstructionInfo> instructions = new ArrayList<>();
        actionInfos.add(new ActionGroup(index, groupId));
        instructions.add(new InstructionApplyActions(actionInfos));
        makeConnectedRoute(remoteDpnId, vpnId, vrfEntry, rd, instructions, NwConstants.ADD_FLOW, tx, txnObjects);
    }

    public void createRemoteFibEntry(final BigInteger remoteDpnId,
                                     final long vpnId,
                                     final String rd,
                                     final VrfEntry vrfEntry,
                                     WriteTransaction tx,
                                     List<SubTransaction> txnObjects) {
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        LOG.debug("createRemoteFibEntryForBgpRoutes: adding route {} for rd {} on remoteDpnId {}",
                vrfEntry.getDestPrefix(), rd, remoteDpnId);

        List<NexthopManager.AdjacencyResult> adjacencyResults =
                resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);
        if (adjacencyResults == null || adjacencyResults.isEmpty()) {
            LOG.error("Could not get interface for route-paths: {} in vpn {}", vrfEntry.getRoutePaths(), rd);
            LOG.warn("Failed to add Route: {} in vpn: {}", vrfEntry.getDestPrefix(), rd);
            return;
        }

        programRemoteFibForBgpRoutes(remoteDpnId, vpnId, vrfEntry, tx, rd, adjacencyResults, txnObjects);

        if (!wrTxPresent) {
            tx.submit();
        }
        LOG.debug("Successfully added FIB entry for prefix {} in vpnId {}", vrfEntry.getDestPrefix(), vpnId);
    }

    private void deleteFibEntryForBgpRoutes (BigInteger remoteDpnId, long vpnId, VrfEntry vrfEntry,
                                             String rd, WriteTransaction tx, List<SubTransaction> txnObjects) {
        // When the tunnel is removed the fib entries should be reprogrammed/deleted depending on
        // the adjacencyResults.
        List<NexthopManager.AdjacencyResult> adjacencyResults = resolveAdjacency(remoteDpnId, vpnId, vrfEntry, rd);
        if (!adjacencyResults.isEmpty()) {
            programRemoteFibForBgpRoutes(remoteDpnId, vpnId, vrfEntry, tx, rd, adjacencyResults, txnObjects);
        }
    }

    public void deleteRemoteRoute(final BigInteger localDpnId, final BigInteger remoteDpnId,
                                  final long vpnId, final VrfTablesKey vrfTableKey,
                                  final VrfEntry vrfEntry, Optional<Routes> extraRouteOptional,
                                  WriteTransaction tx, List<SubTransaction> txnObjects) {

        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }

        LOG.debug("deleting remote route: prefix={}, vpnId={} localDpnId {} remoteDpnId {}",
                vrfEntry.getDestPrefix(), vpnId, localDpnId, remoteDpnId);
        String rd = vrfTableKey.getRouteDistinguisher();

        if (localDpnId != null && localDpnId != BigInteger.ZERO) {
            // localDpnId is not known when clean up happens for last vm for a vpn on a dpn
            if (extraRouteOptional.isPresent()) {
                nexthopManager.setupLoadBalancingNextHop(vpnId, remoteDpnId, vrfEntry.getDestPrefix(), null , false);
            }
            deleteFibEntryForBgpRoutes(remoteDpnId, vpnId, vrfEntry, rd, tx, txnObjects);
            return;
        }

        // below two reads are kept as is, until best way is found to identify dpnID
        VpnNexthop localNextHopInfo = nexthopManager.getVpnNexthop(vpnId, vrfEntry.getDestPrefix());
        if (extraRouteOptional.isPresent()) {
            nexthopManager.setupLoadBalancingNextHop(vpnId, remoteDpnId, vrfEntry.getDestPrefix(), null , false);
        } else {
            checkDpnDeleteFibEntry(localNextHopInfo, remoteDpnId, vpnId, vrfEntry, rd, tx, txnObjects);
        }
        if (!wrTxPresent) {
            tx.submit();
        }
    }

    public Consumer<? super VrfEntry> getConsumerForCreatingRemoteFib(
            final BigInteger dpnId, final long vpnId, final String rd,
            final String remoteNextHopIp, final Optional<VrfTables> vrfTable,
            WriteTransaction writeCfgTxn, List<SubTransaction> txnObjects) {
        return vrfEntry -> vrfEntry.getRoutePaths().stream()
                .filter(routes -> !routes.getNexthopAddress().isEmpty()
                        && remoteNextHopIp.trim().equals(routes.getNexthopAddress().trim()))
                .findFirst()
                .ifPresent(routes -> {
                    LOG.trace("creating remote FIB entry for prefix {} rd {} on Dpn {}",
                            vrfEntry.getDestPrefix(), rd, dpnId);
                    createRemoteFibEntry(dpnId, vpnId, vrfTable.get().getRouteDistinguisher(), vrfEntry, writeCfgTxn, txnObjects);
                });
    }

    public Consumer<? super VrfEntry> getConsumerForDeletingRemoteFib(
            final BigInteger dpnId, final long vpnId, final String rd,
            final String remoteNextHopIp, final Optional<VrfTables> vrfTable,
            WriteTransaction writeCfgTxn, List<SubTransaction> txnObjects) {
        return vrfEntry -> vrfEntry.getRoutePaths().stream()
                .filter(routes -> !routes.getNexthopAddress().isEmpty()
                        && remoteNextHopIp.trim().equals(routes.getNexthopAddress().trim()))
                .findFirst()
                .ifPresent(routes -> {
                    LOG.trace(" deleting remote FIB entry {}", vrfEntry);
                    deleteRemoteRoute(null, dpnId, vpnId, vrfTable.get().getKey(), vrfEntry,
                            Optional.absent(), writeCfgTxn, txnObjects);
                });
    }


}
