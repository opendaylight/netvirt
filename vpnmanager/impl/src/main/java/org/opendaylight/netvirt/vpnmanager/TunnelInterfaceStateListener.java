/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.DpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.to.vpn.list.dpn.list.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TunnelInterfaceStateListener extends AsyncDataTreeChangeListenerBase<StateTunnelList,
        TunnelInterfaceStateListener> {

    private static final Logger LOG = LoggerFactory.getLogger(TunnelInterfaceStateListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IFibManager fibManager;
    private final OdlInterfaceRpcService intfRpcService;
    private final VpnUtil vpnUtil;

    protected enum TunnelEvent {
        TUNNEL_ADD,
        TUNNEL_DELETE
    }

    /**
     * Responsible for listening to tunnel interface state change.
     * @param dataBroker Data Broker
     * @param fibManager FIB APIs
     * @param ifaceMgrRpcService Interface Manager RPC
     * @param vpnUtil Vpn Utility
     */
    @Inject
    public TunnelInterfaceStateListener(final DataBroker dataBroker,
                                        final IFibManager fibManager,
                                        final OdlInterfaceRpcService ifaceMgrRpcService,
                                        VpnUtil vpnUtil) {
        super(StateTunnelList.class, TunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.fibManager = fibManager;
        this.intfRpcService = ifaceMgrRpcService;
        this.vpnUtil = vpnUtil;
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
            programDcGwLoadBalancingGroup(del, NwConstants.MOD_FLOW, false);
        }
        handleTunnelEventForDPN(del, TunnelEvent.TUNNEL_DELETE);
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
                        && opData.getVpnToDpnList().stream().anyMatch(
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
                        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
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
        boolean isTunnelUp = TunnelOperStatus.Up == add.getOperState();
        if (isGreTunnel(add)) {
            programDcGwLoadBalancingGroup(add, NwConstants.ADD_FLOW, isTunnelUp);
        }
        LOG.info("add: ITM Tunnel ,type {} ,added between src: {} and dest: {}",
                fibManager.getTransportTypeStr(add.getTransportType() != null
                        ? add.getTransportType().toString() : "Invalid"),
                add.getSrcInfo() != null ? add.getSrcInfo().getTepDeviceId() : "0",
                add.getDstInfo() != null ? add.getDstInfo().getTepDeviceId() : "0");
        handleTunnelEventForDPN(add, TunnelEvent.TUNNEL_ADD);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPN(StateTunnelList stateTunnelList, TunnelEvent tunnelEvent) {
        final Uint64 srcDpnId = stateTunnelList.getSrcInfo() != null
                ? Uint64.valueOf(stateTunnelList.getSrcInfo().getTepDeviceId()).intern() : Uint64.ZERO;
        final String srcTepIp = stateTunnelList.getSrcInfo() != null
                ? stateTunnelList.getSrcInfo().getTepIp().stringValue() : "0";
        String destTepIp = stateTunnelList.getDstInfo() != null
                ? stateTunnelList.getDstInfo().getTepIp().stringValue() : "0";
        Uint64 remoteDpnId = null;

        LOG.info("handleTunnelEventForDPN: Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} ",
                srcDpnId, srcTepIp, destTepIp);
        int tunTypeVal = getTunnelType(stateTunnelList);
        LOG.trace("handleTunnelEventForDPN: tunTypeVal is {}", tunTypeVal);
        try {

            /*
             * Read the DpnToVpnList to get the list of VPNs having footprint on srcDPN
             * */
            InstanceIdentifier<DpnList> id = VpnUtil.getDpnToVpnListIdentifier(srcDpnId);
            Optional<DpnList> dpnToVpnListEntry =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            if (!dpnToVpnListEntry.isPresent()) {
                LOG.warn("handleTunnelEventForDPN: Dpn {} Tep {} doesnt have any Vpn Footprint", srcDpnId, srcTepIp);
                return;
            }

            List<VpnInstances> vpnInstanceList = dpnToVpnListEntry.get().getVpnInstances();

            /*
             * Iterate over the list of VpnInterface of remote DPN and program the rules
             * for each of those prefix on srcDPN.
             * */

            List<Interfaces> destDpninterfacelist = new ArrayList<>();
            Interfaces interfaces = null;
            String intfName = null;
            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                remoteDpnId = Uint64.valueOf(stateTunnelList.getDstInfo() != null
                        ? stateTunnelList.getDstInfo().getTepDeviceId() : "0").intern();
                destDpninterfacelist = VpnUtil.getDpnInterfaceList(intfRpcService, remoteDpnId);
            }

            Iterator<Interfaces> interfacelistIter = destDpninterfacelist.iterator();
            while (interfacelistIter.hasNext()) {
                interfaces = interfacelistIter.next();
                if (!L2vlan.class.equals(interfaces.getInterfaceType())) {
                    LOG.info("handleTunnelEventForDPN: Interface {} not of type L2Vlan", interfaces.getInterfaceName());
                    continue;
                }
                intfName = interfaces.getInterfaceName();
                VpnInterface vpnInterface = vpnUtil.getConfiguredVpnInterface(intfName);
                if (vpnInterface != null) {
                    handleTunnelEventForDPNVpn(stateTunnelList, tunnelEvent, vpnInterface, vpnInstanceList);
                }
            }
        } catch (RuntimeException e) {
            LOG.error("handleTunnelEventForDpn: Unable to handle the tunnel event for srcDpnId {} srcTepIp {}"
                    + " remoteDpnId {} destTepIp {}", srcDpnId, srcTepIp, remoteDpnId, destTepIp, e);
        } catch (ReadFailedException e) {
            LOG.error("handleTunnelEventForDpn: Unable to read DpnList for srcDpnId {} ", srcDpnId, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTunnelEventForDPNVpn(StateTunnelList stateTunnelList,
                                            TunnelEvent tunnelEvent,
                                            VpnInterface cfgVpnInterface, List<VpnInstances> vpnInstanceList) {
        String rd = null;
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
            LOG.error("handleTunnelEventForDpn: VpnInstance is not found for config vpn-interface {}. "
                            + "Skip remote route handling for srcTepIp {}, DestTepIp {}, TunnelType {}, SrcDpnId {}, "
                            + "RemoteDpnId {}", intfName, srcTepIp, destTepIp,
                    stateTunnelList.getDstInfo().getTepDeviceType(), srcDpnId, remoteDpnId);
            return;
        }
        for (VpnInstanceNames vpnInstanceNames : cfgVpnInterface.getVpnInstanceNames()) {
            String vpnName = vpnInstanceNames.getVpnName();
            VpnInstances vpnInstance = vpnInstanceList.stream().filter(instances ->
                    instances.getVpnInstanceName().equals(vpnName)).findAny().orElse(null);
            if (vpnInstance != null) {
                Optional<VpnInterfaceOpDataEntry> opVpnInterface = vpnUtil.getVpnInterfaceOpDataEntry(intfName,
                        vpnName);
                if (opVpnInterface.isPresent()) {
                    VpnInterfaceOpDataEntry vpnInterface  = opVpnInterface.get();
                    AdjacenciesOp adjacencies = vpnInterface.augmentation(AdjacenciesOp.class);
                    List<Adjacency> adjList = adjacencies != null ? adjacencies.getAdjacency()
                            : Collections.emptyList();
                    String prefix = null;
                    Uint32 vpnId = vpnUtil.getVpnId(vpnInterface.getVpnInstanceName());
                    rd = vpnInstance.getVrfId();
                    LOG.error(
                            "handleTunnelEventForDPN: Remote DpnId {} VpnId {} rd {} VpnInterface {} srcTepIp {} "
                                    + "destTepIp {} tunnelEvent {}",
                            remoteDpnId, vpnId, rd, vpnInterface, srcTepIp, destTepIp, tunnelEvent);
                    for (Adjacency adj : adjList) {
                        prefix = adj.getIpAddress();
                        Uint32 label = adj.getLabel();
                        if (tunnelEvent == TunnelEvent.TUNNEL_ADD
                                && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                            fibManager.manageRemoteRouteOnDPN(true, srcDpnId, vpnId, rd, prefix, destTepIp,
                                    label);
                        }

                        if (tunnelEvent == TunnelEvent.TUNNEL_DELETE
                                && tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                            fibManager.manageRemoteRouteOnDPN(false, srcDpnId, vpnId, rd, prefix, destTepIp,
                                    label);
                        }
                    }
                } else {
                    LOG.error("handleTunnelEventForDPN: Vpn-Interface-Op-data is not found for the interface {}. "
                                    + "Skip remote route handling for srcTepIp {}, DestTepIp {}, TunnelType {} "
                                    + "SrcDpnId {}, RemoteDpnId {}", intfName, srcTepIp, destTepIp,
                            stateTunnelList.getDstInfo().getTepDeviceType(), srcDpnId, remoteDpnId);
                }
            } else {
                LOG.debug("handleTunnelEventForDPN: VpnInterface {} vpnInstance {} doesn't have footprint on"
                        + " dpn {}", cfgVpnInterface.getName(), vpnName , srcDpnId);
            }
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
}
