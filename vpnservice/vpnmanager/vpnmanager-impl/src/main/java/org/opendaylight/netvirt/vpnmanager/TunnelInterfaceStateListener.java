/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnelInterfaceStateListener extends AsyncDataTreeChangeListenerBase<StateTunnelList,
    TunnelInterfaceStateListener> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelInterfaceStateListener.class);
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final ItmRpcService itmRpcService;
    private OdlInterfaceRpcService intfRpcService;
    private VpnInterfaceManager vpnInterfaceManager;
    private VpnSubnetRouteHandler vpnSubnetRouteHandler;

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
     *
     * @param dataBroker dataBroker
     * @param bgpManager bgpManager
     * @param fibManager fibManager
     * @param itmRpcService itmRpcService
     */
    public TunnelInterfaceStateListener(final DataBroker dataBroker,
        final IBgpManager bgpManager,
        final IFibManager fibManager,
        final ItmRpcService itmRpcService,
        final OdlInterfaceRpcService ifaceMgrRpcService,
        final VpnInterfaceManager vpnInterfaceManager,
        final VpnSubnetRouteHandler vpnSubnetRouteHandler) {
        super(StateTunnelList.class, TunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.itmRpcService = itmRpcService;
        this.intfRpcService = ifaceMgrRpcService;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    protected TunnelInterfaceStateListener getDataTreeChangeListener() {
        return TunnelInterfaceStateListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
        LOG.trace("remove: Tunnel deletion---- {}", del);
        if (isGreTunnel(del)) {
            programDcGwLoadBalancingGroup(del, NwConstants.DEL_FLOW);
        }
        handleTunnelEventForDPN(del, UpdateRouteAction.WITHDRAW_ROUTE, TunnelAction.TUNNEL_EP_DELETE);
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original,
        StateTunnelList update) {
        LOG.trace("update: Tunnel updation---- {}", update);
        LOG.info("update: ITM Tunnel {} of type {} state event changed from :{} to :{}",
            update.getTunnelInterfaceName(),
            fibManager.getTransportTypeStr(update.getTransportType().toString()),
            original.getOperState(), update.getOperState());
        TunnelOperStatus tunOpStatus = update.getOperState();
        if ((tunOpStatus != TunnelOperStatus.Down) && (tunOpStatus != TunnelOperStatus.Up)) {
            LOG.info("update: Returning from unsupported tunnelOperStatus {} for tunnel interface {}", tunOpStatus,
                    update.getTunnelInterfaceName());
            return;
        }
        if (isGreTunnel(update)) {
            programDcGwLoadBalancingGroup(update, NwConstants.MOD_FLOW);
        }

        //Remove the corresponding nexthop from the routepath under extraroute in fibentries.
        BigInteger srcDpnId = new BigInteger(update.getSrcInfo().getTepDeviceId());
        String srcTepIp = String.valueOf(update.getSrcInfo().getTepIp().getValue());
        List<VpnInstanceOpDataEntry> vpnInstanceOpData = VpnUtil.getAllVpnInstanceOpData(dataBroker);
        if (vpnInstanceOpData == null) {
            LOG.trace("update: No vpnInstanceOpdata present");
            return;
        }
        WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
        if (tunOpStatus == TunnelOperStatus.Up) {
            handleTunnelEventForDPN(update, UpdateRouteAction.ADVERTISE_ROUTE, TunnelAction.TUNNEL_EP_ADD);
        } else {
            vpnInstanceOpData.stream().filter(opData -> {
                if (opData.getVpnToDpnList() == null) {
                    return false;
                }
                return opData.getVpnToDpnList().stream().anyMatch(vpnToDpn -> vpnToDpn.getDpnId().equals(srcDpnId));
            }).forEach(opData -> {
                List<DestPrefixes> prefixes = VpnExtraRouteHelper.getExtraRouteDestPrefixes(dataBroker,
                        opData.getVpnId());
                prefixes.forEach(destPrefix -> {
                    VrfEntry vrfEntry = VpnUtil.getVrfEntry(dataBroker, opData.getVrfId(),
                            destPrefix.getDestPrefix());
                    if (vrfEntry == null || vrfEntry.getRoutePaths() == null) {
                        return;
                    }
                    List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
                    routePaths.forEach(routePath -> {
                        if (routePath.getNexthopAddress().equals(srcTepIp)) {
                            fibManager.updateRoutePathForFibEntry(dataBroker, opData.getVrfId(),
                                    destPrefix.getDestPrefix(), srcTepIp, routePath.getLabel(),
                                    false, writeConfigTxn);
                        }
                    });
                });
            });
        }
        writeConfigTxn.submit();
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList add) {
        LOG.trace("add: Tunnel addition---- {}", add);
        TunnelOperStatus tunOpStatus = add.getOperState();
        if ((tunOpStatus != TunnelOperStatus.Down) && (tunOpStatus != TunnelOperStatus.Up)) {
            LOG.info("add: Returning from unsupported tunnelOperStatus {} for tunnel interface {}", tunOpStatus,
                    add.getTunnelInterfaceName());
            return;
        }
        if (tunOpStatus != TunnelOperStatus.Up) {
            LOG.error("add: Tunnel {} is not yet UP.", add.getTunnelInterfaceName());
        }
        if (isGreTunnel(add)) {
            programDcGwLoadBalancingGroup(add, NwConstants.ADD_FLOW);
        }
        LOG.info("add: ITM Tunnel ,type {} ,added between src: {} and dest: {}",
            fibManager.getTransportTypeStr(add.getTransportType().toString()),
            add.getSrcInfo().getTepDeviceId(), add.getDstInfo().getTepDeviceId());
        handleTunnelEventForDPN(add, UpdateRouteAction.ADVERTISE_ROUTE, TunnelAction.TUNNEL_EP_ADD);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList, UpdateRouteAction action,
        TunnelAction tunnelAction) {
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        final String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        String rd;
        BigInteger remoteDpnId = null;
        boolean isTepDeletedOnDpn = false;

        LOG.info("handleTunnelEventForDPN: Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} ",
                srcDpnId, srcTepIp, destTepIp);
        int tunTypeVal = getTunnelType(stateTunnelList);
        LOG.trace("handleTunnelEventForDPN: tunTypeVal is {}", tunTypeVal);
        try {
            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                LOG.info("handleTunnelEventForDPN: Tunnel ADD event received for Dpn {} VTEP Ip {} destTepIp",
                        srcDpnId, srcTepIp, destTepIp);
                if (isTunnelInLogicalGroup(stateTunnelList)) {
                    return;
                }
            } else if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE) {
                LOG.info("handleTunnelEventForDPN: Tunnel DELETE event received for Dpn {} VTEP Ip {} DestTepIp",
                        srcDpnId, srcTepIp, destTepIp);
                // When tunnel EP is deleted on a DPN , VPN gets two deletion event.
                // One for a DPN on which tunnel EP was deleted and another for other-end DPN.
                // Update the adj for the vpninterfaces for a DPN on which TEP is deleted.
                // Update the adj & VRF for the vpninterfaces for a DPN on which TEP is deleted.
                // Dont update the adj & VRF for vpninterfaces for a DPN on which TEP is not deleted.
                String endpointIpForDPN = null;
                try {
                    endpointIpForDPN = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, srcDpnId);
                } catch (Exception e) {
                    LOG.error("handleTunnelEventForDPN: Unable to resolve endpoint IP for srcDpn {}", srcDpnId);
                    /* this dpn does not have the VTEP */
                    endpointIpForDPN = null;
                }

                if (endpointIpForDPN == null) {
                    LOG.info("handleTunnelEventForDPN: Tunnel TEP is deleted on Dpn {} VTEP Ip {} destTepIp", srcDpnId,
                            srcTepIp, destTepIp);
                    isTepDeletedOnDpn = true;
                }
            }

            // Get the list of VpnInterfaces from Intf Mgr for a SrcDPN on which TEP is added/deleted
            Future<RpcResult<GetDpnInterfaceListOutput>> result;
            List<String> srcDpninterfacelist = new ArrayList<>();
            List<String> destDpninterfacelist = new ArrayList<>();
            try {
                result =
                    intfRpcService.getDpnInterfaceList(new GetDpnInterfaceListInputBuilder().setDpid(srcDpnId).build());
                RpcResult<GetDpnInterfaceListOutput> rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.error("handleTunnelEventForDPN: RPC Call to GetDpnInterfaceList for srcDpnid {} srcTepIp {}"
                            + " destTepIP {} returned with Errors {}", srcDpnId, srcTepIp, destTepIp,
                            rpcResult.getErrors());
                } else {
                    srcDpninterfacelist = rpcResult.getResult().getInterfacesList();
                }
            } catch (Exception e) {
                LOG.error("handleTunnelEventForDPN: Exception {} when querying for GetDpnInterfaceList for srcDpnid {}"
                        + " srcTepIp {} destTepIp {}, trace {}", e, srcDpnId, srcTepIp, destTepIp, e.getStackTrace());
            }

            // Get the list of VpnInterfaces from Intf Mgr for a destDPN only for internal tunnel.
            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                remoteDpnId = new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId());
                try {
                    result = intfRpcService.getDpnInterfaceList(
                        new GetDpnInterfaceListInputBuilder().setDpid(remoteDpnId).build());
                    RpcResult<GetDpnInterfaceListOutput> rpcResult = result.get();
                    if (!rpcResult.isSuccessful()) {
                        LOG.error("handleTunnelEventForDPN: RPC Call to GetDpnInterfaceList for remoteDpnid {}"
                                + " srcTepIP {} destTepIp {} returned with Errors {}", remoteDpnId, srcTepIp,
                                destTepIp, rpcResult.getErrors());
                    } else {
                        destDpninterfacelist = rpcResult.getResult().getInterfacesList();
                    }
                } catch (Exception e) {
                    LOG.error("handleTunnelEventForDPN: Exception {} when querying for GetDpnInterfaceList"
                            + " for remoteDpnid {} srcTepIp {} destTepIp {}, trace {}", e, remoteDpnId,
                            srcTepIp, destTepIp, e.getStackTrace());
                }
            }

            /*
             * Iterate over the list of VpnInterface for a SrcDpn on which TEP is added or deleted and read the adj.
             * Update the adjacencies with the updated nexthop.
             */
            Iterator<String> interfacelistIter = srcDpninterfacelist.iterator();
            String intfName = null;
            List<Uuid> subnetList = new ArrayList<>();
            Map<Long, String> vpnIdRdMap = new HashMap<>();

            while (interfacelistIter.hasNext()) {
                intfName = interfacelistIter.next();
                final VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, intfName);
                if (vpnInterface != null) {

                    DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                    dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + intfName,
                            new UpdateVpnInterfaceOnTunnelEvent(dataBroker,
                                    vpnInterfaceManager,
                                    tunnelAction,
                                    vpnInterface,
                                    stateTunnelList,
                                    isTepDeletedOnDpn));

                    // Populate the List of subnets
                    InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                        InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                            new PortOpDataEntryKey(intfName)).build();
                    Optional<PortOpDataEntry> optionalPortOp =
                        VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                    if (optionalPortOp.isPresent()) {
                        Uuid subnetId = optionalPortOp.get().getSubnetId();
                        if (!subnetList.contains(subnetId)) {
                            subnetList.add(subnetId);
                        }
                    }
                    //Populate the map for VpnId-to-Rd
                    long vpnId = VpnUtil.getVpnId(dataBroker, VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
                    rd = VpnUtil.getVpnRd(dataBroker, VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
                    vpnIdRdMap.put(vpnId, rd);
                }
            }

            /*
             * Iterate over the list of VpnInterface for destDPN and get the prefix .
             * Create remote rule for each of those prefix on srcDPN.
             */
            interfacelistIter = destDpninterfacelist.iterator();
            while (interfacelistIter.hasNext()) {
                intfName = interfacelistIter.next();
                final VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, intfName);
                if (vpnInterface != null) {
                    Adjacencies adjacencies = vpnInterface.getAugmentation(Adjacencies.class);
                    List<Adjacency> adjList = adjacencies != null ? adjacencies.getAdjacency()
                            : Collections.emptyList();
                    String prefix = null;
                    long vpnId = VpnUtil.getVpnId(dataBroker, VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
                    if (vpnIdRdMap.containsKey(vpnId)) {
                        rd = vpnIdRdMap.get(vpnId);
                        LOG.info("handleTunnelEventForDPN: Remote DpnId {} VpnId {} rd {} VpnInterface {} srcTepIp {}"
                                + " destTepIp {}", remoteDpnId, vpnId, rd , vpnInterface, srcTepIp, destTepIp);
                        for (Adjacency adj : adjList) {
                            prefix = adj.getIpAddress();
                            long label = adj.getLabel();
                            if ((tunnelAction == TunnelAction.TUNNEL_EP_ADD)
                                && (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue())) {
                                fibManager.manageRemoteRouteOnDPN(true, srcDpnId, vpnId, rd, prefix, destTepIp, label);
                            }

                            if ((tunnelAction == TunnelAction.TUNNEL_EP_DELETE)
                                && (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue())) {
                                fibManager.manageRemoteRouteOnDPN(false, srcDpnId, vpnId, rd, prefix, destTepIp, label);
                            }
                        }
                    }
                }
            }

            //Iterate over the VpnId-to-Rd map.
            for (Map.Entry<Long, String> entry : vpnIdRdMap.entrySet()) {
                Long vpnId = entry.getKey();
                rd = entry.getValue();
                if ((tunnelAction == TunnelAction.TUNNEL_EP_ADD)
                    && (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                    fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                } else if ((tunnelAction == TunnelAction.TUNNEL_EP_DELETE)
                    && (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                    fibManager.cleanUpExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                }
            }

            VpnInterface vpnInterface = VpnUtil.getOperationalVpnInterface(dataBroker, intfName);
            rd = VpnUtil.getVpnRd(dataBroker, VpnHelper.getFirstVpnNameFromVpnInterface(vpnInterface));
            String vpnName = VpnUtil.getVpnNameFromRd(dataBroker, rd);

            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                for (Uuid subnetId : subnetList) {
                    // Populate the List of subnets
                    vpnSubnetRouteHandler.updateSubnetRouteOnTunnelUpEvent(subnetId, srcDpnId);
                }
            }

            if ((tunnelAction == TunnelAction.TUNNEL_EP_DELETE) && isTepDeletedOnDpn) {
                for (Uuid subnetId : subnetList) {
                    // Populate the List of subnets
                    vpnSubnetRouteHandler.updateSubnetRouteOnTunnelDownEvent(subnetId, srcDpnId);
                }
            }
        } catch (Exception e) {
            LOG.error("handleTunnelEventForDPN: Unable to handle the tunnel event for srcDpnId {} srcTepIp {}"
                    + " remoteDpnid {} destTepIp {}", srcDpnId, srcTepIp, remoteDpnId, destTepIp, e);
        }
    }


    private class UpdateVpnInterfaceOnTunnelEvent implements Callable {
        private VpnInterface vpnInterface;
        private StateTunnelList stateTunnelList;
        private VpnInterfaceManager vpnInterfaceManager;
        private DataBroker broker;
        private TunnelAction tunnelAction;
        private boolean isTepDeletedOnDpn;

        UpdateVpnInterfaceOnTunnelEvent(DataBroker broker,
            VpnInterfaceManager vpnInterfaceManager,
            TunnelAction tunnelAction,
            VpnInterface vpnInterface,
            StateTunnelList stateTunnelList,
            boolean isTepDeletedOnDpn) {
            this.broker = broker;
            this.vpnInterfaceManager = vpnInterfaceManager;
            this.stateTunnelList = stateTunnelList;
            this.vpnInterface = vpnInterface;
            this.tunnelAction = tunnelAction;
            this.isTepDeletedOnDpn = isTepDeletedOnDpn;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();

            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                vpnInterfaceManager.updateVpnInterfaceOnTepAdd(vpnInterface,
                                                            stateTunnelList,
                                                            writeConfigTxn,
                                                            writeOperTxn);
            }

            if ((tunnelAction == TunnelAction.TUNNEL_EP_DELETE) && isTepDeletedOnDpn) {
                vpnInterfaceManager.updateVpnInterfaceOnTepDelete(vpnInterface,
                                                                stateTunnelList,
                                                                writeConfigTxn,
                                                                writeOperTxn);
            }

            futures.add(writeOperTxn.submit());
            futures.add(writeConfigTxn.submit());
            return futures;
        }
    }

    private int getTunnelType(StateTunnelList stateTunnelList) {
        int tunTypeVal = 0;
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

    private void programDcGwLoadBalancingGroup(StateTunnelList tunnelState, int addOrRemove) {
        IpAddress dcGwIp = tunnelState.getDstInfo().getTepIp();
        String dcGwIpAddress = String.valueOf(dcGwIp.getValue());
        List<String> availableDcGws = getDcGwIps();
        BigInteger dpId = new BigInteger(tunnelState.getSrcInfo().getTepDeviceId());
        boolean isTunnelUp = TunnelOperStatus.Up == tunnelState.getOperState();
        fibManager.programDcGwLoadBalancingGroup(availableDcGws, dpId, dcGwIpAddress, addOrRemove, isTunnelUp);
    }

    private List<String> getDcGwIps() {
        InstanceIdentifier<DcGatewayIpList> dcGatewayIpListid =
                InstanceIdentifier.builder(DcGatewayIpList.class).build();
        DcGatewayIpList dcGatewayIpListConfig =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, dcGatewayIpListid).orNull();
        if (dcGatewayIpListConfig == null) {
            return Collections.EMPTY_LIST;
        }
        return dcGatewayIpListConfig.getDcGatewayIp()
                .stream()
                .filter(dcGwIp -> dcGwIp.getTunnnelType().equals(TunnelTypeMplsOverGre.class))
                .map(dcGwIp -> String.valueOf(dcGwIp.getIpAddress().getValue())).sorted()
                .collect(toList());
    }

    private boolean isTunnelInLogicalGroup(StateTunnelList stateTunnelList) {
        String ifaceName = stateTunnelList.getTunnelInterfaceName();
        if (getTunnelType(stateTunnelList) == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
            Interface configIface = InterfaceUtils.getInterface(dataBroker, stateTunnelList.getTunnelInterfaceName());
            IfTunnel ifTunnel = (configIface != null) ? configIface.getAugmentation(IfTunnel.class) : null;
            if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
                ParentRefs refs = configIface.getAugmentation(ParentRefs.class);
                if (refs != null && !Strings.isNullOrEmpty(refs.getParentInterface())) {
                    return true; //multiple VxLAN tunnels enabled, i.e. only logical tunnel should be treated
                }
            }
        }
        LOG.trace("isTunnelInLogicalGroup: MULTIPLE_VxLAN_TUNNELS: ignoring the tunnel event for {}", ifaceName);
        return false;
    }
}
