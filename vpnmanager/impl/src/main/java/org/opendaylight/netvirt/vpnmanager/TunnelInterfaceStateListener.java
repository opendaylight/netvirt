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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelInterfaceStateListener extends AsyncDataTreeChangeListenerBase<StateTunnelList,
        TunnelInterfaceStateListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelInterfaceStateListener.class);
    private final DataBroker dataBroker;
    private final IFibManager fibManager;
    private final OdlInterfaceRpcService intfRpcService;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private final JobCoordinator jobCoordinator;

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
     */
    @Inject
    public TunnelInterfaceStateListener(final DataBroker dataBroker,
                                        final IFibManager fibManager,
                                        final OdlInterfaceRpcService ifaceMgrRpcService,
                                        final VpnInterfaceManager vpnInterfaceManager,
                                        final VpnSubnetRouteHandler vpnSubnetRouteHandler,
                                        final JobCoordinator jobCoordinator) {
        super(StateTunnelList.class, TunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.fibManager = fibManager;
        this.intfRpcService = ifaceMgrRpcService;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
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
        handleTunnelEventForDPN(del, TunnelAction.TUNNEL_EP_DELETE);
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
        if (tunOpStatus != TunnelOperStatus.Down && tunOpStatus != TunnelOperStatus.Up) {
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
            handleTunnelEventForDPN(update, TunnelAction.TUNNEL_EP_ADD);
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
                            fibManager.updateRoutePathForFibEntry(opData.getVrfId(),
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
        if (tunOpStatus != TunnelOperStatus.Down && tunOpStatus != TunnelOperStatus.Up) {
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
    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList,
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
                    srcDpninterfacelist = rpcResult.getResult().getInterfaces();
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
                        destDpninterfacelist = rpcResult.getResult().getInterfaces();
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
            Iterator<Interfaces> interfacelistIter = srcDpninterfacelist.iterator();
            Interfaces interfaces = null;
            String intfName = null;
            List<Uuid> subnetList = new ArrayList<>();
            Map<Long, String> vpnIdRdMap = new HashMap<>();
            Set<String> listVpnName = new HashSet<String>();

            while (interfacelistIter.hasNext()) {
                interfaces = interfacelistIter.next();
                if (!L2vlan.class.equals(interfaces.getInterfaceType())) {
                    LOG.info("handleTunnelEventForDPN: Interface {} not of type L2Vlan", interfaces.getInterfaceName());
                    return;
                }
                intfName = interfaces.getInterfaceName();
                VpnInterface vpnInterface =
                     VpnUtil.getConfiguredVpnInterface(dataBroker, intfName);
                if (vpnInterface != null && !Boolean.TRUE.equals(vpnInterface.isScheduledForRemove())) {
                    listVpnName.addAll(VpnHelper
                        .getVpnInterfaceVpnInstanceNamesString(vpnInterface.getVpnInstanceNames()));
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
            interfacelistIter = destDpninterfacelist.iterator();
            while (interfacelistIter.hasNext()) {
                interfaces = interfacelistIter.next();
                if (!L2vlan.class.equals(interfaces.getInterfaceType())) {
                    LOG.info("handleTunnelEventForDPN: Interface {} not of type L2Vlan", interfaces.getInterfaceName());
                    return;
                }
                intfName = interfaces.getInterfaceName();
                VpnInterface vpnInterface =
                        VpnUtil.getConfiguredVpnInterface(dataBroker, intfName);
                if (vpnInterface != null) {
                    handleTunnelEventForDPNVpn(stateTunnelList, vpnIdRdMap,
                            tunnelAction, isTepDeletedOnDpn,
                            subnetList, TunnelEventProcessingMethod.MANAGEREMOTEROUTES,
                            vpnInterface);
                }
            }

            //Iterate over the VpnId-to-Rd map.
            for (Map.Entry<Long, String> entry : vpnIdRdMap.entrySet()) {
                Long vpnId = entry.getKey();
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
                if ((tunnelAction == TunnelAction.TUNNEL_EP_DELETE) && isTepDeletedOnDpn) {
                    for (Uuid subnetId : subnetList) {
                        // Populate the List of subnets
                        vpnSubnetRouteHandler.updateSubnetRouteOnTunnelDownEvent(subnetId, srcDpnId);
                    }
                }
            }
        } catch (RuntimeException e) {
            LOG.error("handleTunnelEventForDpn: Unable to handle the tunnel event for srcDpnId {} srcTepIp {}"
                    + " remoteDpnId {} destTepIp {}", srcDpnId, srcTepIp, remoteDpnId, destTepIp, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPNVpn(StateTunnelList stateTunnelList,
                                            Map<Long, String> vpnIdRdMap, TunnelAction tunnelAction,
                                            boolean isTepDeletedOnDpn, List<Uuid> subnetList,
                                            TunnelEventProcessingMethod method,
                                            VpnInterface cfgVpnInterface) {
        String rd;
        String intfName = cfgVpnInterface.getName();
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        int tunTypeVal = getTunnelType(stateTunnelList);
        BigInteger remoteDpnId = null;
        if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
            remoteDpnId = new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId());
        }
        if (cfgVpnInterface.getVpnInstanceNames() == null) {
            LOG.warn("handleTunnelEventForDpn: no vpnName found for interface {}", intfName);
            return;
        }
        try {
            for (VpnInstanceNames vpnInstance : cfgVpnInterface.getVpnInstanceNames()) {
                String vpnName = vpnInstance.getVpnName();
                if (method == TunnelEventProcessingMethod.POPULATESUBNETS) {
                    Optional<VpnInterfaceOpDataEntry> opVpnInterface = VpnUtil
                            .getVpnInterfaceOpDataEntry(dataBroker, intfName, vpnName);
                    if (opVpnInterface.isPresent() && !opVpnInterface.get().isScheduledForRemove()) {
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
                        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                        rd = VpnUtil.getVpnRd(dataBroker, vpnName);
                        vpnIdRdMap.put(vpnId, rd);
                    }
                } else if (method == TunnelEventProcessingMethod.MANAGEREMOTEROUTES) {
                    Optional<VpnInterfaceOpDataEntry> opVpnInterface = VpnUtil.getVpnInterfaceOpDataEntry(dataBroker,
                            intfName, vpnName);
                    if (opVpnInterface.isPresent()) {
                        VpnInterfaceOpDataEntry vpnInterface  = opVpnInterface.get();
                        AdjacenciesOp adjacencies = vpnInterface.getAugmentation(AdjacenciesOp.class);
                        List<Adjacency> adjList = adjacencies != null ? adjacencies.getAdjacency()
                                : Collections.emptyList();
                        String prefix = null;
                        long vpnId = VpnUtil.getVpnId(dataBroker, vpnInterface.getVpnInstanceName());
                        if (vpnIdRdMap.containsKey(vpnId)) {
                            rd = vpnIdRdMap.get(vpnId);
                            LOG.info("handleTunnelEventForDPN: Remote DpnId {} VpnId {} rd {} VpnInterface {}"
                                    + " srcTepIp {} destTepIp {}", remoteDpnId, vpnId, rd , vpnInterface, srcTepIp,
                                    destTepIp);
                            for (Adjacency adj : adjList) {
                                prefix = adj.getIpAddress();
                                long label = adj.getLabel();
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
        } catch (ReadFailedException e) {
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
        public List<ListenableFuture<Void>> call() {
            WriteTransaction writeConfigTxn = dataBroker.newWriteOnlyTransaction();
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD) {
                vpnInterfaceManager.updateVpnInterfaceOnTepAdd(vpnInterface,
                        stateTunnelList,
                        writeConfigTxn,
                        writeOperTxn);
            }

            if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE && isTepDeletedOnDpn) {
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
        fibManager.programDcGwLoadBalancingGroup(availableDcGws, dpId, dcGwIpAddress, addOrRemove, isTunnelUp,
                tunnelState.getTransportType());
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
            IfTunnel ifTunnel = configIface != null ? configIface.getAugmentation(IfTunnel.class) : null;
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
