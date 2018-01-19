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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
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
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.DestPrefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.DpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
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
        TUNNEL_ADD,
        TUNNEL_DELETE
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
        handleTunnelEventForDPN(del, TunnelAction.TUNNEL_DELETE);
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
            handleTunnelEventForDPN(update, TunnelAction.TUNNEL_ADD);
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
        handleTunnelEventForDPN(add, TunnelAction.TUNNEL_ADD);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList, TunnelAction tunnelAction) {
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        final String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        String rd;
        BigInteger remoteDpnId = null;

        int tunTypeVal = VpnUtil.getTunnelType(stateTunnelList);
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

            List<VpnNames> vpnInstanceList = dpnToVpnListEntry.get().getVpnNames();
            /*
             * Iterate over the list of VpnInterface for destDPN and get the prefix .
             * Create remote rule for each of those prefix on srcDPN if Vpn FootPrint exists.
             */
            List<String> destDpninterfacelist = new ArrayList<>();
            String intfName = null;
            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                destDpninterfacelist = VpnUtil.getDpnInterfaceList(intfRpcService, remoteDpnId);
            }

            Iterator<String> interfacelistIter = destDpninterfacelist.iterator();
            while (interfacelistIter.hasNext()) {
                intfName = interfacelistIter.next();
                VpnInterface vpnInterface =
                    VpnUtil.getConfiguredVpnInterface(dataBroker, intfName);
                if (vpnInterface != null) {
                    handleTunnelEventForDPNVpn(stateTunnelList, vpnInstanceList, tunnelAction, vpnInterface);
                }
            }

            /*
             * Program the BGP routes of all the VPNs which have footprint on the source DPN.
             */
            vpnInstanceList.forEach(vpnInstance -> {
                long vpnId = VpnUtil.getVpnId(dataBroker, vpnInstance.getVpnInstanceName());
                final String vrfId = vpnInstance.getVrfId();
                if ((tunnelAction == TunnelAction.TUNNEL_ADD)
                        && (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                    fibManager.populateExternalRoutesOnDpn(srcDpnId, vpnId, vrfId, srcTepIp, destTepIp);
                } else if ((tunnelAction == TunnelAction.TUNNEL_DELETE)
                        && (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                    fibManager.cleanUpExternalRoutesOnDpn(srcDpnId, vpnId, vrfId, srcTepIp, destTepIp);
                }
            });

        } catch (RuntimeException e) {
            LOG.error("handleTunnelEventForDpn: Unable to handle the tunnel event for srcDpnId {} srcTepIp {}"
                     + " remoteDpnId {} destTepIp {}", srcDpnId, srcTepIp, remoteDpnId, destTepIp, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPNVpn(StateTunnelList stateTunnelList,
                                            List<VpnNames> vpnInstanceList,
                                            TunnelAction tunnelAction,
                                            VpnInterface cfgVpnInterface) {
        String rd;
        String intfName = cfgVpnInterface.getName();
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        int tunTypeVal = VpnUtil.getTunnelType(stateTunnelList);
        BigInteger remoteDpnId = null;
        long vpnId;
        if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
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
                VpnNames vpnInst = vpnInstanceList.stream().filter(instances ->
                        instances.getVpnInstanceName().equals(vpnInterface.getVpnInstanceName()))
                        .findAny().orElse(null);
                vpnId = VpnUtil.getVpnId(dataBroker, vpnInterface.getVpnInstanceName());
                if (vpnInst != null) {
                    LOG.info("handleTunnelEventForDPN: Remote DpnId {} VpnId {} VpnInterface {} srcTepIp "
                            + "{} destTepIp {}", remoteDpnId, vpnId, vpnInterface, srcTepIp, destTepIp);
                    for (Adjacency adj : adjList) {
                        prefix = adj.getIpAddress();
                        long label = adj.getLabel();
                        rd = adj.getVrfId();
                        if (tunnelAction == TunnelAction.TUNNEL_ADD
                                && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                            fibManager.manageRemoteRouteOnDPN(true, srcDpnId, vpnId, rd, prefix, destTepIp, label);
                        }

                        if (tunnelAction == TunnelAction.TUNNEL_DELETE
                                && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                            fibManager.manageRemoteRouteOnDPN(false, srcDpnId, vpnId, rd, prefix, destTepIp, label);
                        }
                    }
                } else {
                    LOG.debug("handleTunnelEventForDPNVpn: VpnInterface {} VpnId {} vpnInstance {} doesnt have"
                            + "footprint on dpn {}", vpnInterface, vpnId, vpnInterface.getVpnInstanceName() , srcDpnId);
                }
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
}
