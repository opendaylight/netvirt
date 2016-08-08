/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.IsDcgwPresentOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnelInterfaceStateListener extends AbstractDataChangeListener<StateTunnelList>
        implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(TunnelInterfaceStateListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private IFibManager fibManager;
    private ItmRpcService itmRpcService;
    protected enum UpdateRouteAction {
        ADVERTISE_ROUTE, WITHDRAW_ROUTE
    }

    /**
     * Responsible for listening to tunnel interface state change
     *
     * @param dataBroker dataBroker
     * @param bgpManager bgpManager
     * @param fibManager fibManager
     * @param itmRpcService itmRpcService
     */
    public TunnelInterfaceStateListener(final DataBroker dataBroker,
                                        final IBgpManager bgpManager,
                                        final IFibManager fibManager,
                                        final ItmRpcService itmRpcService) {
        super(StateTunnelList.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.itmRpcService = itmRpcService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                getWildCardPath(), this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
            listenerRegistration = null;
        }
        LOG.info("{} close", getClass().getSimpleName());
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
        LOG.trace("Tunnel deletion---- {}", del);
        handlePrefixesForDPNs(del, UpdateRouteAction.WITHDRAW_ROUTE);
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original, StateTunnelList update) {
        LOG.trace("Tunnel updation---- {}", update);
        LOG.trace("ITM Tunnel {} of type {} state event changed from :{} to :{}",
                update.getTunnelInterfaceName(),
                fibManager.getTransportTypeStr(update.getTransportType().toString()),
                original.isTunnelState(), update.isTunnelState());
        //withdraw all prefixes in all vpns for this dpn
        boolean isTunnelUp = update.isTunnelState();
        handlePrefixesForDPNs(update, isTunnelUp ? UpdateRouteAction.ADVERTISE_ROUTE :
                UpdateRouteAction.WITHDRAW_ROUTE);
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList add) {
        LOG.trace("Tunnel addition---- {}", add);

        if (!add.isTunnelState()) {
            LOG.trace("Tunnel {} is not yet UP.",
                    add.getTunnelInterfaceName());
            return;
        } else {
            LOG.trace("ITM Tunnel ,type {} ,State is UP b/w src: {} and dest: {}",
                    fibManager.getTransportTypeStr(add.getTransportType().toString()),
                    add.getSrcInfo().getTepDeviceId(), add.getDstInfo().getTepDeviceId());
            handlePrefixesForDPNs(add, UpdateRouteAction.ADVERTISE_ROUTE);
        }
    }

    private void handlePrefixesForDPNs(StateTunnelList stateTunnelList, UpdateRouteAction action) {
        BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        BigInteger destDpnId;
        String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());

        InstanceIdentifier.InstanceIdentifierBuilder<VpnInstances> idBuilder = InstanceIdentifier.builder(VpnInstances.class);
        InstanceIdentifier<VpnInstances> vpnInstancesId = idBuilder.build();
        Optional<VpnInstances> vpnInstances = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnInstancesId);
        long tunTypeVal = 0, vpnId;

        if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Internal.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeExternal.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.External.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeHwvtep.class) {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Hwvtep.getValue();
        } else {
            tunTypeVal = VpnConstants.ITMTunnelLocType.Invalid.getValue();
        }
        LOG.trace("tunTypeVal is {}", tunTypeVal);

        long dcgwPresentStatus = VpnConstants.DCGWPresentStatus.Invalid.getValue();
        if (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
            Future<RpcResult<IsDcgwPresentOutput>> result;
            try {
                result = itmRpcService.isDcgwPresent(new IsDcgwPresentInputBuilder()
                        .setDcgwIp(destTepIp)
                        .build());
                RpcResult<IsDcgwPresentOutput> rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("RPC Call to isDcgwPresent {} returned with Errors {}", destTepIp, rpcResult.getErrors());
                } else {
                    dcgwPresentStatus = rpcResult.getResult().getRetVal();
                }
            } catch (Exception e) {
                LOG.warn("Exception {} when querying for isDcgwPresent {}, trace {}", e, destTepIp, e.getStackTrace());
            }
        }

        if (vpnInstances.isPresent()) {
            List<VpnInstance> vpnInstanceList = vpnInstances.get().getVpnInstance();
            Iterator<VpnInstance> vpnInstIter = vpnInstanceList.iterator();
            LOG.trace("vpnInstIter {}", vpnInstIter);
            while (vpnInstIter.hasNext()) {
                VpnInstance vpnInstance = vpnInstIter.next();
                LOG.trace("vpnInstance {}", vpnInstance);
                vpnId = VpnUtil.getVpnId(dataBroker, vpnInstance.getVpnInstanceName());
                try {
                    VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
                    LOG.trace("vpnConfig {}", vpnConfig);
                    String rd = vpnConfig.getRouteDistinguisher();
                    if (rd == null || rd.isEmpty()) {
                        rd = vpnInstance.getVpnInstanceName();
                        LOG.trace("rd is null or empty. Assigning VpnInstanceName to rd {}", rd);
                    }
                    InstanceIdentifier<VpnToDpnList> srcId =
                            VpnUtil.getVpnToDpnListIdentifier(rd, srcDpnId);
                    Optional<VpnToDpnList> srcDpnInVpn =
                            VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, srcId);
                    if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                        destDpnId = new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId());
                        InstanceIdentifier<VpnToDpnList> destId =
                                VpnUtil.getVpnToDpnListIdentifier(rd, destDpnId);
                        Optional<VpnToDpnList> destDpnInVpn =
                                VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, destId);
                        if (!(srcDpnInVpn.isPresent() &&
                                destDpnInVpn.isPresent())) {
                            LOG.trace(" srcDpn {} - destDPN {}, do not share the VPN {} with rd {}.",
                                    srcDpnId, destDpnId, vpnInstance.getVpnInstanceName(), rd);
                            continue;
                        }
                    }
                    if (srcDpnInVpn.isPresent()) {
                        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                                .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces>
                                vpnInterfaces = srcDpnInVpn.get().getVpnInterfaces();
                        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                                .vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces vpnInterface : vpnInterfaces) {
                            InstanceIdentifier<VpnInterface> vpnIntfId =
                                    VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getInterfaceName());
                            LOG.trace("vpnInterface {}", vpnInterface);
                            InstanceIdentifier<Adjacencies> path =
                                    vpnIntfId.augmentation(Adjacencies.class);
                            Optional<Adjacencies> adjacencies =
                                    VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
                            LOG.trace("adjacencies {}", adjacencies);
                            if (adjacencies.isPresent()) {
                                List<Adjacency> adjacencyList = adjacencies.get().getAdjacency();
                                Iterator<Adjacency> adjacencyIterator = adjacencyList.iterator();

                                while (adjacencyIterator.hasNext()) {
                                    Adjacency adjacency = adjacencyIterator.next();
                                    try {
                                        if (action == UpdateRouteAction.ADVERTISE_ROUTE) {
                                            LOG.info("VPNInterfaceManager : Added Fib Entry rd {} prefix {} nextHop {} label {}",
                                                    rd, adjacency.getIpAddress(), adjacency.getNextHopIpList(),
                                                    adjacency.getLabel());
//                                            vrf = new VrfEntryBuilder().set
                                            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                                                fibManager.handleRemoteRoute(true,
                                                        new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId()),
                                                        new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId()),
                                                        VpnUtil.getVpnId(dataBroker, vpnInstance.getVpnInstanceName()),
                                                        rd, adjacency.getIpAddress(), srcTepIp, destTepIp);
                                            }
                                            if (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) {
                                                fibManager.populateFibOnDpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp);
                                            }
                                        } else if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                                            LOG.info("VPNInterfaceManager : Removed Fib entry rd {} prefix {}",
                                                    rd, adjacency.getIpAddress());
                                            if (tunTypeVal == VpnConstants.ITMTunnelLocType.Internal.getValue()) {
                                                fibManager.handleRemoteRoute(false, srcDpnId,
                                                        new BigInteger(stateTunnelList.getDstInfo().getTepDeviceId()),
                                                        vpnId, rd, adjacency.getIpAddress(), srcTepIp, destTepIp);
                                            }
                                            if ((tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue()) &&
                                                    (dcgwPresentStatus == VpnConstants.DCGWPresentStatus.Absent.getValue())) {                                                bgpManager.withdrawPrefix(rd, adjacency.getIpAddress());
                                                fibManager.cleanUpDpnForVpn(srcDpnId, vpnId, rd, srcTepIp, destTepIp, null);
                                            }
                                        }
                                    } catch (Exception e) {
                                        LOG.error("Exception when updating prefix {} in vrf {} to BGP",
                                                adjacency.getIpAddress(), rd);
                                    }
                                }
                            } else {
                                LOG.trace("no adjacencies present for path {}.", path);
                            }
                        }
                        // if (action == UpdateRouteAction.WITHDRAW_ROUTE) {
                        //    fibManager.cleanUpDpnForVpn(dpnId, VpnUtil.getVpnId(dataBroker, vpnInstance.getVpnInstanceName()), rd);
                        // }
                        // Go through all the VrfEntries and withdraw and readvertise the prefixes to BGP for which the nextHop is the SrcTepIp
                        if ((action == UpdateRouteAction.ADVERTISE_ROUTE) &&
                                (tunTypeVal == VpnConstants.ITMTunnelLocType.External.getValue())) {
                            List<VrfEntry> vrfEntries = VpnUtil.getAllVrfEntries(dataBroker, rd);
                            if (vrfEntries != null) {
                                for (VrfEntry vrfEntry : vrfEntries) {
                                    String destPrefix = vrfEntry.getDestPrefix().trim();
                                    int vpnLabel = vrfEntry.getLabel().intValue();
                                    List<String> nextHops = vrfEntry.getNextHopAddressList();
                                    if (nextHops.contains(srcTepIp.trim())) {
                                        bgpManager.withdrawPrefix(rd, destPrefix);
                                        bgpManager.advertisePrefix(rd, destPrefix, nextHops, vpnLabel);
                                    }
                                }
                            }
                        }
                    } else {
                        LOG.trace("dpnInVpn check failed for srcDpnId {}.", srcDpnId);
                    }
                } catch (Exception e) {
                    LOG.error("updatePrefixesForDPN {} in vpn {} failed", 0, vpnInstance.getVpnInstanceName(), e);
                }
            }
        } else {
            LOG.trace("No vpn instances present.");
        }
    }
}
