/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.get.dpn._interface.list.output.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelInterfaceStateListener extends AbstractAsyncDataTreeChangeListener<StateTunnelList> {

    private static final Logger LOG = LoggerFactory.getLogger(TunnelInterfaceStateListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IFibManager fibManager;
    private final OdlInterfaceRpcService intfRpcService;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;
    private static final int RETRY_COUNT = 3;

    protected enum UpdateRouteAction {
        ADVERTISE_ROUTE, WITHDRAW_ROUTE
    }

    protected enum TunnelAction {
        TUNNEL_EP_ADD,
        TUNNEL_EP_DELETE,
        TUNNEL_EP_UPDATE
    }

    /**
     * Responsible for listening to tunnel interface state change.
     * @param dataBroker Data Broker
     * @param fibManager FIB APIs
     * @param ifaceMgrRpcService Interface Manager RPC
     * @param vpnInterfaceManager Vpn Interface APIs
     * @param vpnSubnetRouteHandler Subnet-Route APIs
     * @param jobCoordinator Key based job serialization mechanism
     * @param vpnUtil Vpn Utility
     */
    @Inject
    public TunnelInterfaceStateListener(final DataBroker dataBroker,
                                        final IFibManager fibManager,
                                        final OdlInterfaceRpcService ifaceMgrRpcService,
                                        final VpnInterfaceManager vpnInterfaceManager,
                                        final VpnSubnetRouteHandler vpnSubnetRouteHandler,
                                        final JobCoordinator jobCoordinator,
                                        VpnUtil vpnUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class),
                Executors.newListeningSingleThreadExecutor("TunnelInterfaceStateListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.fibManager = fibManager;
        this.intfRpcService = ifaceMgrRpcService;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.jobCoordinator = jobCoordinator;
        this.vpnUtil = vpnUtil;
        start();
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }


    @Override
    public void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
        LOG.trace("remove: Tunnel deletion---- {}", del);
        if (isGreTunnel(del)) {
            programDcGwLoadBalancingGroup(del, NwConstants.MOD_FLOW, false);
        }
        handleTunnelEventForDPN(del, TunnelAction.TUNNEL_EP_DELETE);
    }

    @Override
    public void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original,
                          StateTunnelList update) {
        LOG.trace("update: Tunnel updation---- {}", update);
        LOG.info("update: ITM Tunnel {} of type {} state event changed from :{} to :{}",
                update.getTunnelInterfaceName(),
                fibManager.getTransportTypeStr(update.getTransportType().toString()),
                original.getOperState(), update.getOperState());
        TunnelOperStatus tunOpStatus = update.getOperState();
        if (tunOpStatus != TunnelOperStatus.Down && tunOpStatus != TunnelOperStatus.Up) {
            LOG.info("update: Returning from unsupported tunnelOperStatus {} for tunnel interface {}", tunOpStatus,
                    update.getTunnelInterfaceName());
            return;
        }
        boolean isTunnelUp = TunnelOperStatus.Up == update.getOperState();
        if (isGreTunnel(update)) {
            programDcGwLoadBalancingGroup(update, NwConstants.MOD_FLOW, isTunnelUp);
        }

        //Remove the corresponding nexthop from the routepath under extraroute in fibentries.
        Uint64 srcDpnId = Uint64.valueOf(update.getSrcInfo().getTepDeviceId()).intern();
        String srcTepIp = update.getSrcInfo().getTepIp().stringValue();
        List<VpnInstanceOpDataEntry> vpnInstanceOpData = vpnUtil.getAllVpnInstanceOpData();
        if (vpnInstanceOpData == null) {
            LOG.trace("update: No vpnInstanceOpdata present");
            return;
        }
        vpnInstanceOpData.stream()
                .filter(opData -> opData.getVpnToDpnList() != null
                        && opData.getVpnToDpnList().values().stream().anyMatch(
                            vpnToDpn -> Objects.equals(vpnToDpn.getDpnId(), srcDpnId)))
                .forEach(opData -> {
                    List<DestPrefixes> prefixes = VpnExtraRouteHelper.getExtraRouteDestPrefixes(dataBroker,
                            opData.getVpnId());
                    prefixes.forEach(destPrefix -> {
                        VrfEntry vrfEntry = vpnUtil.getVrfEntry(opData.getVrfId(),
                                destPrefix.getDestPrefix());
                        if (vrfEntry == null || vrfEntry.getRoutePaths() == null) {
                            return;
                        }
                        List<RoutePaths> routePaths = new ArrayList<RoutePaths>(vrfEntry.getRoutePaths().values());
                        routePaths.forEach(routePath -> {
                            if (Objects.equals(routePath.getNexthopAddress(), srcTepIp)) {
                                String prefix = destPrefix.getDestPrefix();
                                String vpnPrefixKey = VpnUtil.getVpnNamePrefixKey(opData.getVpnInstanceName(),
                                        prefix);
                                // FIXME: separate out to somehow?
                                final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnPrefixKey);
                                lock.lock();
                                try {
                                    fibManager.refreshVrfEntry(opData.getVrfId(), prefix);
                                } finally {
                                    lock.unlock();
                                }
                            }
                        });
                    });
                });
    }

    @Override
    public void add(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList add) {
        LOG.trace("add: Tunnel addition---- {}", add);
        TunnelOperStatus tunOpStatus = add.getOperState();
        if (tunOpStatus != TunnelOperStatus.Down && tunOpStatus != TunnelOperStatus.Up) {
            LOG.info("add: Returning from unsupported tunnelOperStatus {} for tunnel interface {}", tunOpStatus,
                    add.getTunnelInterfaceName());
            return;
        }
        if (tunOpStatus != TunnelOperStatus.Up) {
            LOG.error("add: Tunnel {} is not yet UP.", add.getTunnelInterfaceName());
        }
        boolean isTunnelUp = TunnelOperStatus.Up == add.getOperState();
        if (isGreTunnel(add)) {
            programDcGwLoadBalancingGroup(add, NwConstants.ADD_FLOW, isTunnelUp);
        }
        LOG.info("add: ITM Tunnel ,type {} ,added between src: {} and dest: {}",
                fibManager.getTransportTypeStr(add.getTransportType() != null
                        ? add.getTransportType().toString() : "Invalid"),
                add.getSrcInfo() != null ? add.getSrcInfo().getTepDeviceId() : "0",
                add.getDstInfo() != null ? add.getDstInfo().getTepDeviceId() : "0");
        handleTunnelEventForDPN(add, TunnelAction.TUNNEL_EP_ADD);
    }

    public enum TunnelEventProcessingMethod {
        POPULATESUBNETS(0), MANAGEREMOTEROUTES(1);

        private final int method;

        TunnelEventProcessingMethod(int id) {
            this.method = id;
        }

        public int getValue() {
            return method;
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList, TunnelAction tunnelAction) {
        final Uint64 srcDpnId = stateTunnelList.getSrcInfo() != null
                ? Uint64.valueOf(stateTunnelList.getSrcInfo().getTepDeviceId()).intern() : Uint64.ZERO;
        final String srcTepIp = stateTunnelList.getSrcInfo() != null
                ? stateTunnelList.getSrcInfo().getTepIp().stringValue() : "0";
        String destTepIp = stateTunnelList.getDstInfo() != null
                ? stateTunnelList.getDstInfo().getTepIp().stringValue() : "0";
        String rd;
        Uint64 remoteDpnId = null;
        boolean isTepDeletedOnDpn = false;

        LOG.info("handleTunnelEventForDPN: Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} ",
                srcDpnId, srcTepIp, destTepIp);
        int tunTypeVal = getTunnelType(stateTunnelList);
        LOG.trace("handleTunnelEventForDPN: tunTypeVal is {}", tunTypeVal);
        try {
            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                LOG.info("handleTunnelEventForDPN: Tunnel ADD event received for Dpn {} VTEP Ip {} destTepIp {}",
                        srcDpnId, srcTepIp, destTepIp);
                if (isTunnelInLogicalGroup(stateTunnelList)) {
                    return;
                }
            } else if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE) {
                LOG.info("handleTunnelEventForDPN: Tunnel DELETE event received for Dpn {} VTEP Ip {} DestTepIp {}",
                        srcDpnId, srcTepIp, destTepIp);
                // When tunnel EP is deleted on a DPN , VPN gets two deletion event.
                // One for a DPN on which tunnel EP was deleted and another for other-end DPN.
                // Update the adj for the vpninterfaces for a DPN on which TEP is deleted.
                // Update the adj & VRF for the vpninterfaces for a DPN on which TEP is deleted.
                // Dont update the adj & VRF for vpninterfaces for a DPN on which TEP is not deleted.
                String endpointIpForDPN;
                try {
                    endpointIpForDPN = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, srcDpnId);
                } catch (Exception e) {
                    LOG.error("handleTunnelEventForDPN: Unable to resolve endpoint IP for srcDpn {}", srcDpnId);
                    /* this dpn does not have the VTEP */
                    endpointIpForDPN = null;
                }

                if (endpointIpForDPN == null) {
                    LOG.info("handleTunnelEventForDPN: Tunnel TEP is deleted on Dpn {} VTEP Ip {} destTepIp {}",
                            srcDpnId, srcTepIp, destTepIp);
                    isTepDeletedOnDpn = true;
                }
            }

            // Get the list of VpnInterfaces from Intf Mgr for a SrcDPN on which TEP is added/deleted
            Future<RpcResult<GetDpnInterfaceListOutput>> result;
            List<Interfaces> srcDpninterfacelist = new ArrayList<>();
            List<Interfaces> destDpninterfacelist = new ArrayList<>();
            try {
                result = intfRpcService.getDpnInterfaceList(
                        new GetDpnInterfaceListInputBuilder().setDpid(srcDpnId).build());
                RpcResult<GetDpnInterfaceListOutput> rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.error("handleTunnelEventForDPN: RPC Call to GetDpnInterfaceList for srcDpnid {} srcTepIp {}"
                                    + " destTepIP {} returned with Errors {}", srcDpnId, srcTepIp, destTepIp,
                            rpcResult.getErrors());
                } else {
                    srcDpninterfacelist = rpcResult.getResult().nonnullInterfaces();
                }
            } catch (Exception e) {
                LOG.error("handleTunnelEventForDPN: Exception when querying for GetDpnInterfaceList for srcDpnid {}"
                        + " srcTepIp {} destTepIp {}", srcDpnId, srcTepIp, destTepIp, e);
            }
            // Get the list of VpnInterfaces from Intf Mgr for a destDPN only for internal tunnel.
            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                remoteDpnId = Uint64.valueOf(stateTunnelList.getDstInfo() != null
                        ? stateTunnelList.getDstInfo().getTepDeviceId() : "0").intern();
                try {
                    result = intfRpcService.getDpnInterfaceList(
                            new GetDpnInterfaceListInputBuilder().setDpid(remoteDpnId).build());
                    RpcResult<GetDpnInterfaceListOutput> rpcResult = result.get();
                    if (!rpcResult.isSuccessful()) {
                        LOG.error("handleTunnelEventForDPN: RPC Call to GetDpnInterfaceList for remoteDpnid {}"
                                        + " srcTepIP {} destTepIp {} returned with Errors {}", remoteDpnId, srcTepIp,
                                destTepIp, rpcResult.getErrors());
                    } else {
                        destDpninterfacelist = rpcResult.getResult().nonnullInterfaces();
                    }
                } catch (Exception e) {
                    LOG.error("handleTunnelEventForDPN: Exception when querying for GetDpnInterfaceList"
                                    + " for remoteDpnid {} srcTepIp {} destTepIp {}", remoteDpnId,
                            srcTepIp, destTepIp, e);
                }
            }

            /*
             * Iterate over the list of VpnInterface for a SrcDpn on which TEP is added or deleted and read the adj.
             * Update the adjacencies with the updated nexthop.
             */
            List<Uuid> subnetList = new ArrayList<>();
            Map<Uint32, String> vpnIdRdMap = new HashMap<>();
            Set<String> listVpnName = new HashSet<>();

            for (Interfaces interfaces : srcDpninterfacelist) {
                if (!L2vlan.class.equals(interfaces.getInterfaceType())) {
                    LOG.info("handleTunnelEventForDPN: Interface {} not of type L2Vlan", interfaces.getInterfaceName());
                    continue;
                }
                String intfName = interfaces.getInterfaceName();
                VpnInterface vpnInterface =
                     vpnUtil.getConfiguredVpnInterface(intfName);
                if (vpnInterface != null) {
                    listVpnName.addAll(VpnHelper
                        .getVpnInterfaceVpnInstanceNamesString(
                                new ArrayList<VpnInstanceNames>(vpnInterface.nonnullVpnInstanceNames().values())));
                    handleTunnelEventForDPNVpn(stateTunnelList, vpnIdRdMap,
                            tunnelAction, isTepDeletedOnDpn,
                            subnetList, TunnelEventProcessingMethod.POPULATESUBNETS,
                            vpnInterface);
                }
            }
            /*
             * Iterate over the list of VpnInterface for destDPN and get the prefix .
             * Create remote rule for each of those prefix on srcDPN.
             */
            for (Interfaces interfaces : destDpninterfacelist) {
                if (!L2vlan.class.equals(interfaces.getInterfaceType())) {
                    LOG.info("handleTunnelEventForDPN: Interface {} not of type L2Vlan", interfaces.getInterfaceName());
                    continue;
                }
                String intfName = interfaces.getInterfaceName();
                VpnInterface vpnInterface =
                        vpnUtil.getConfiguredVpnInterface(intfName);
                if (vpnInterface != null) {
                    handleTunnelEventForDPNVpn(stateTunnelList, vpnIdRdMap,
                            tunnelAction, isTepDeletedOnDpn,
                            subnetList, TunnelEventProcessingMethod.MANAGEREMOTEROUTES,
                            vpnInterface);
                }
            }

            //Iterate over the VpnId-to-Rd map.
            for (Map.Entry<Uint32, String> entry : vpnIdRdMap.entrySet()) {
                Uint32 vpnId = entry.getKey();
                rd = entry.getValue();
                if (tunnelAction == TunnelAction.TUNNEL_EP_ADD
                    && tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
                    fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                } else if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE
                    && tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
                    fibManager.cleanUpExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                }
            }
            if (listVpnName.size() >= 1) {
                if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                    for (Uuid subnetId : subnetList) {
                        // Populate the List of subnets
                        vpnSubnetRouteHandler.updateSubnetRouteOnTunnelUpEvent(subnetId, srcDpnId);
                    }
                }
                if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE && isTepDeletedOnDpn) {
                    for (Uuid subnetId : subnetList) {
                        // Populate the List of subnets
                        vpnSubnetRouteHandler.updateSubnetRouteOnTunnelDownEvent(subnetId, srcDpnId);
                    }
                }
            }
            /*
             * Program the BGP routes of all the VPNs which have footprint on the source DPN.
             *
             * DC-GW LB groups are programmed in DJC Jobs, so DJC with same key is used here to make sure
             * groups are programmed first, then only BGP routes are programmed.
             */
            jobCoordinator.enqueueJob(FibHelper.getJobKeyForDcGwLoadBalancingGroup(srcDpnId), () -> {
                listVpnName.forEach(vpnName -> {
                    Uint32 vpnId = vpnUtil.getVpnId(vpnName);
                    final String vrfId = vpnIdRdMap.get(vpnId);
                    if ((tunnelAction == TunnelAction.TUNNEL_EP_ADD)
                            && (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                        fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, vrfId,
                                srcTepIp, destTepIp);
                    }
                });
                return Collections.emptyList();
            },RETRY_COUNT);
        } catch (RuntimeException e) {
            LOG.error("handleTunnelEventForDpn: Unable to handle the tunnel event for srcDpnId {} srcTepIp {}"
                    + " remoteDpnId {} destTepIp {}", srcDpnId, srcTepIp, remoteDpnId, destTepIp, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPNVpn(StateTunnelList stateTunnelList,
                                            Map<Uint32, String> vpnIdRdMap, TunnelAction tunnelAction,
                                            boolean isTepDeletedOnDpn, List<Uuid> subnetList,
                                            TunnelEventProcessingMethod method,
                                            VpnInterface cfgVpnInterface) {
        String rd;
        String intfName = cfgVpnInterface.getName();
        final Uint64 srcDpnId = Uint64.valueOf(stateTunnelList.getSrcInfo() != null
                ? stateTunnelList.getSrcInfo().getTepDeviceId() : "0").intern();
        String destTepIp = stateTunnelList.getDstInfo() != null ? stateTunnelList.getDstInfo().getTepIp().stringValue()
                : null;
        String srcTepIp = stateTunnelList.getSrcInfo() != null ? stateTunnelList.getSrcInfo().getTepIp().stringValue()
                : null;
        int tunTypeVal = getTunnelType(stateTunnelList);
        Uint64 remoteDpnId = null;
        if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
            remoteDpnId = Uint64.valueOf(stateTunnelList.getDstInfo() != null
                    ? stateTunnelList.getDstInfo().getTepDeviceId() : "0").intern();
        }
        if (cfgVpnInterface.getVpnInstanceNames() == null) {
            LOG.warn("handleTunnelEventForDpn: no vpnName found for interface {}", intfName);
            return;
        }
        try {
            for (VpnInstanceNames vpnInstance : cfgVpnInterface.getVpnInstanceNames().values()) {
                String vpnName = vpnInstance.getVpnName();
                if (method == TunnelEventProcessingMethod.POPULATESUBNETS) {
                    Optional<VpnInterfaceOpDataEntry> opVpnInterface = vpnUtil
                            .getVpnInterfaceOpDataEntry(intfName, vpnName);
                    if (opVpnInterface.isPresent()) {
                        VpnInterfaceOpDataEntry vpnInterface  = opVpnInterface.get();
                        jobCoordinator.enqueueJob("VPNINTERFACE-" + intfName,
                                new UpdateVpnInterfaceOnTunnelEvent(tunnelAction,
                                        vpnInterface,
                                        stateTunnelList,
                                        isTepDeletedOnDpn));

                        // Populate the List of subnets
                        InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                                InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                                        new PortOpDataEntryKey(intfName)).build();
                        Optional<PortOpDataEntry> optionalPortOp =
                                SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                        LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                        if (optionalPortOp.isPresent()) {
                            List<Uuid> subnetIdList = optionalPortOp.get().getSubnetIds();
                            if (subnetIdList != null) {
                                for (Uuid subnetId : subnetIdList) {
                                    if (!subnetList.contains(subnetId)) {
                                        subnetList.add(subnetId);
                                    }
                                }
                            }
                        }
                        //Populate the map for VpnId-to-Rd
                        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
                        rd = vpnUtil.getVpnRd(vpnName);
                        vpnIdRdMap.put(vpnId, rd);
                    }
                } else if (method == TunnelEventProcessingMethod.MANAGEREMOTEROUTES) {
                    Optional<VpnInterfaceOpDataEntry> opVpnInterface = vpnUtil.getVpnInterfaceOpDataEntry(intfName,
                            vpnName);
                    if (opVpnInterface.isPresent()) {
                        VpnInterfaceOpDataEntry vpnInterface  = opVpnInterface.get();
                        AdjacenciesOp adjacencies = vpnInterface.augmentation(AdjacenciesOp.class);
                        Map<AdjacencyKey, Adjacency> adjacencyKeyAdjacencyMap =
                            adjacencies != null && adjacencies.getAdjacency() != null ? adjacencies.getAdjacency()
                                : Collections.<AdjacencyKey, Adjacency>emptyMap();
                        String prefix = null;
                        Uint32 vpnId = vpnUtil.getVpnId(vpnInterface.getVpnInstanceName());
                        if (vpnIdRdMap.containsKey(vpnId)) {
                            rd = vpnIdRdMap.get(vpnId);
                            LOG.info("handleTunnelEventForDPN: Remote DpnId {} VpnId {} rd {} VpnInterface {}"
                                    + " srcTepIp {} destTepIp {}", remoteDpnId, vpnId, rd , vpnInterface, srcTepIp,
                                    destTepIp);
                            for (Adjacency adj : adjacencyKeyAdjacencyMap.values()) {
                                prefix = adj.getIpAddress();
                                Uint32 label = adj.getLabel();
                                if (tunnelAction == TunnelAction.TUNNEL_EP_ADD
                                        && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                                    fibManager.manageRemoteRouteOnDPN(true, srcDpnId, vpnId, rd, prefix, destTepIp,
                                            label);
                                }
                                if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE
                                        && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                                    fibManager.manageRemoteRouteOnDPN(false, srcDpnId, vpnId, rd, prefix, destTepIp,
                                            label);
                                }
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("handleTunnelEventForDPN: Failed to read data store for interface {} srcDpn {} srcTep {} "
                    + "dstTep {}", intfName, srcDpnId, srcTepIp, destTepIp);
        }
    }

    private class UpdateVpnInterfaceOnTunnelEvent implements Callable {
        private final VpnInterfaceOpDataEntry vpnInterface;
        private final StateTunnelList stateTunnelList;
        private final TunnelAction tunnelAction;
        private final boolean isTepDeletedOnDpn;

        UpdateVpnInterfaceOnTunnelEvent(TunnelAction tunnelAction,
                                        VpnInterfaceOpDataEntry vpnInterface,
                                        StateTunnelList stateTunnelList,
                                        boolean isTepDeletedOnDpn) {
            this.stateTunnelList = stateTunnelList;
            this.vpnInterface = vpnInterface;
            this.tunnelAction = tunnelAction;
            this.isTepDeletedOnDpn = isTepDeletedOnDpn;
        }

        @Override
        public List<? extends ListenableFuture<?>> call() {
            List<ListenableFuture<?>> futures = new ArrayList<>(2);
            futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, confTx ->
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, operTx -> {
                    if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                        vpnInterfaceManager.updateVpnInterfaceOnTepAdd(vpnInterface, stateTunnelList, confTx, operTx);
                    }

                    if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE && isTepDeletedOnDpn) {
                        vpnInterfaceManager.updateVpnInterfaceOnTepDelete(vpnInterface, stateTunnelList, confTx,
                            operTx);
                    }
                }))));
            return futures;
        }
    }

    private int getTunnelType(StateTunnelList stateTunnelList) {
        int tunTypeVal = 0;
        if (stateTunnelList.getDstInfo() == null) {
            return VpnConstants.ITMTunnelLocType.Invalid.getValue();
        }
        if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Internal.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeExternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.External.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeHwvtep.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Hwvtep.getValue();
        } else {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Invalid.getValue();
        }
        return tunTypeVal;
    }

    private boolean isGreTunnel(StateTunnelList del) {
        return del.getTransportType() == TunnelTypeMplsOverGre.class;
    }

    private void programDcGwLoadBalancingGroup(StateTunnelList tunnelState, int addOrRemove, boolean isTunnelUp) {
        IpAddress dcGwIp = tunnelState.getDstInfo().getTepIp();
        String dcGwIpAddress = String.valueOf(dcGwIp.stringValue());
        Uint64 dpId = Uint64.valueOf(tunnelState.getSrcInfo().getTepDeviceId()).intern();
        fibManager.programDcGwLoadBalancingGroup(dpId, dcGwIpAddress, addOrRemove, isTunnelUp,
                tunnelState.getTransportType());
    }

    private boolean isTunnelInLogicalGroup(StateTunnelList stateTunnelList) {
        String ifaceName = stateTunnelList.getTunnelInterfaceName();
        if (getTunnelType(stateTunnelList) == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
            Interface configIface = InterfaceUtils.getInterface(dataBroker, stateTunnelList.getTunnelInterfaceName());
            IfTunnel ifTunnel = configIface != null ? configIface.augmentation(IfTunnel.class) : null;
            if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
                ParentRefs refs = configIface.augmentation(ParentRefs.class);
                if (refs != null && !Strings.isNullOrEmpty(refs.getParentInterface())) {
                    return true; //multiple VxLAN tunnels enabled, i.e. only logical tunnel should be treated
                }
            }
        }
        LOG.trace("isTunnelInLogicalGroup: MULTIPLE_VxLAN_TUNNELS: ignoring the tunnel event for {}", ifaceName);
        return false;
    }
}
