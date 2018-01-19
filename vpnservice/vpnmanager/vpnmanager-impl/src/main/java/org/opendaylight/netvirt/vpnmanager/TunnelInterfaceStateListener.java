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
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.DpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
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
        TUNNEL_EP_DELETE
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
        handleTunnelEventForDPN(add, UpdateRouteAction.ADVERTISE_ROUTE, TunnelAction.TUNNEL_EP_ADD);
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
    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList, UpdateRouteAction action,
        TunnelAction tunnelAction) {
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        final String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        String rd;
        BigInteger remoteDpnId = null;
        boolean isTepDeletedOnDpn = false;

        VpnConstants.ITMTunnelLocType tunTypeVal = VpnUtil.getTunnelType(stateTunnelList);
        LOG.info("handleTunnelEventForDPN: Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} tunType {}",
                srcDpnId, srcTepIp, destTepIp, tunTypeVal);
        try {

            /*
             * Read the DpnToVpnList to get the list of VPNs having footprint on srcDPN
             * */
            InstanceIdentifier<DpnList> id = VpnUtil.getDpnToVpnListIdentifier(srcDpnId);
            Optional<DpnList> dpnToVpnListEntry =
                    VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);

            if (!dpnToVpnListEntry.isPresent()) {
                LOG.error("handleTunnelEventForDPN: Dpn {} Tep {} doesnt have any Vpn Footprint", srcDpnId, srcTepIp);
                return;
            }

            List<VpnInstances> vpnInstanceList = dpnToVpnListEntry.get().getVpnInstances();

            /*
             * Iterate over the list of VpnInterface for destDPN and get the prefix .
             * Create remote rule for each of those prefix on srcDPN.
             */
            if (tunTypeVal == VpnTunnelLocType.ITMTunnelLocType.Internal) {
                destDpninterfacelist = VpnUtil.getDpnInterfaceList(intfRpcService, remoteDpnId);
            }
            interfacelistIter = destDpninterfacelist.iterator();
            while (interfacelistIter.hasNext()) {
                intfName = interfacelistIter.next();
                VpnInterface vpnInterface =
                    VpnUtil.getConfiguredVpnInterface(dataBroker, intfName);
                if (vpnInterface != null) {
                    handleTunnelEventForDPNVpn(stateTunnelList, vpnIdRdMap,
                                    tunnelAction, isTepDeletedOnDpn,
                                    vpnInterface);
                }
            }

            //Iterate over the VpnId-to-Rd map.
            for (Map.Entry<Long, String> entry : vpnIdRdMap.entrySet()) {
                Long vpnId = entry.getKey();
                rd = entry.getValue();
                if ((tunnelAction == TunnelAction.TUNNEL_EP_ADD)
                    && (tunTypeVal == VpnConstants.ITMTunnelLocType.External)) {
                    fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                } else if ((tunnelAction == TunnelAction.TUNNEL_EP_DELETE)
                    && (tunTypeVal == VpnConstants.ITMTunnelLocType.External)) {
                    fibManager.cleanUpExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
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
        VpnConstants.ITMTunnelLocType tunTypeVal = VpnUtil.getTunnelType(stateTunnelList);
        BigInteger remoteDpnId = null;
        if (tunTypeVal.getValue() == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
            remoteDpnId = new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId());
        }
        if (cfgVpnInterface.getVpnInstanceNames() == null) {
            LOG.warn("handleTunnelEventForDpn: no vpnName found for interface {}", intfName);
            return;
        }
        for (VpnInstanceNames vpnInstance : cfgVpnInterface.getVpnInstanceNames()) {
            String vpnName = vpnInstance.getVpnName();

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
                    LOG.info("handleTunnelEventForDPN: Remote DpnId {} VpnId {} rd {} VpnInterface {} srcTepIp "
                            + "{} destTepIp {}", remoteDpnId, vpnId, rd , vpnInterface, srcTepIp, destTepIp);
                    for (Adjacency adj : adjList) {
                        prefix = adj.getIpAddress();
                        long label = adj.getLabel();
                        if (tunnelAction == TunnelAction.TUNNEL_EP_ADD
                            && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal) {
                            fibManager.manageRemoteRouteOnDPN(true, srcDpnId, vpnId, rd, prefix, destTepIp, label);
                        }

                        if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE
                            && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal) {
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
            if (tunnelAction == TunnelAction.TUNNEL_EP_ADD
                && tunTypeVal == VpnConstants.ITMTunnelLocType.External) {
                fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
            } else if (tunnelAction == TunnelAction.TUNNEL_EP_DELETE
                && tunTypeVal == VpnConstants.ITMTunnelLocType.External) {
                fibManager.cleanUpExternalRoutesOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
            }
        }

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
        if (VpnUtil.getTunnelType(stateTunnelList) == VpnConstants.ITMTunnelLocType.Internal) {
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
