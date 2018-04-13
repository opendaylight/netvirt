/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeHwvtep;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepTypeInternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibEntryInputs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatTunnelInterfaceStateListener
    extends AsyncDataTreeChangeListenerBase<StateTunnelList, NatTunnelInterfaceStateListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NatTunnelInterfaceStateListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IFibManager fibManager;
    private final SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private final NaptSwitchHA naptSwitchHA;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final IBgpManager bgpManager;
    private final ExternalRoutersListener externalRouterListner;
    private final SnatServiceManager natServiceManager;
    private final OdlInterfaceRpcService interfaceService;
    private final FloatingIPListener floatingIPListener;
    private final FibRpcService fibRpcService;
    private final IElanService elanManager;
    private final IInterfaceManager interfaceManager;
    private final NatMode natMode;

    protected enum TunnelAction {
        TUNNEL_EP_ADD,
        TUNNEL_EP_DELETE,
        TUNNEL_EP_UPDATE
    }

    /**
     * Responsible for listening to tunnel interface state change.
     *
     * @param dataBroker             - dataBroker service reference
     * @param bgpManager             Used to advertise routes to the BGP Router
     * @param fibManager             - FIB Manager
     * @param defaultRouteProgrammer - Default Route Programmer
     * @param naptSwitchHA           - NAPT Switch HA
     * @param mdsalManager           - MDSAL Manager
     * @param idManager              - ID manager
     * @param externalRouterListner  - External Router Listener
     * @param natServiceManager      - Nat Service Manager
     * @param interfaceService       - Interface Service
     * @param floatingIPListener     -  Floating IP Listener
     * @param fibRpcService          - FIB RPC Service
     * @param config                 - Nat Service Config
     * @param elanManager            - Elan Manager
     * @param interfaceManager       - Interface Manager
     */
    @Inject
    public NatTunnelInterfaceStateListener(final DataBroker dataBroker,
                                           final IBgpManager bgpManager,
                                           final IFibManager fibManager,
                                           final SNATDefaultRouteProgrammer defaultRouteProgrammer,
                                           final NaptSwitchHA naptSwitchHA,
                                           final IMdsalApiManager mdsalManager,
                                           final IdManagerService idManager,
                                           final ExternalRoutersListener externalRouterListner,
                                           final SnatServiceManager natServiceManager,
                                           final OdlInterfaceRpcService interfaceService,
                                           final FloatingIPListener floatingIPListener,
                                           final FibRpcService fibRpcService,
                                           final NatserviceConfig config,
                                           final IElanService elanManager,
                                           final IInterfaceManager interfaceManager) {
        super(StateTunnelList.class, NatTunnelInterfaceStateListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.defaultRouteProgrammer = defaultRouteProgrammer;
        this.naptSwitchHA = naptSwitchHA;
        this.mdsalManager = mdsalManager;
        this.idManager = idManager;
        this.externalRouterListner = externalRouterListner;
        this.natServiceManager = natServiceManager;
        this.interfaceService = interfaceService;
        this.floatingIPListener = floatingIPListener;
        this.fibRpcService = fibRpcService;
        this.elanManager = elanManager;
        this.interfaceManager = interfaceManager;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatMode.Controller;
        }
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    protected NatTunnelInterfaceStateListener getDataTreeChangeListener() {
        return NatTunnelInterfaceStateListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> instanceIdentifier, StateTunnelList add) {
        LOG.trace("add : TEP addtion---- {}", add);
        hndlTepEvntsForDpn(add, TunnelAction.TUNNEL_EP_ADD);
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
        LOG.trace("remove : TEP deletion---- {}", del);
        hndlTepEvntsForDpn(del, TunnelAction.TUNNEL_EP_DELETE);
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original,
                          StateTunnelList update) {
        LOG.trace("update : Tunnel updation---- {}", update);
        //UPDATE IS A SEQUENCE OF DELETE AND ADD EVENTS . DELETE MIGHT HAVE CHANGED THE PRIMARY AND ADVERTISED. SO
        // NOTHING TO HANDLE
    }

    private int getTunnelType(StateTunnelList stateTunnelList) {
        int tunTypeVal = 0;
        if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeInternal.class) {
            tunTypeVal = NatConstants.ITMTunnelLocType.Internal.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeExternal.class) {
            tunTypeVal = NatConstants.ITMTunnelLocType.External.getValue();
        } else if (stateTunnelList.getDstInfo().getTepDeviceType() == TepTypeHwvtep.class) {
            tunTypeVal = NatConstants.ITMTunnelLocType.Hwvtep.getValue();
        } else {
            tunTypeVal = NatConstants.ITMTunnelLocType.Invalid.getValue();
        }
        return tunTypeVal;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    void removeSNATFromDPN(BigInteger dpnId, String routerName, long routerId, long routerVpnId,
            Uuid networkId, ProviderTypes extNwProvType, WriteTransaction writeFlowInvTx) {
        //irrespective of naptswitch or non-naptswitch, SNAT default miss entry need to be removed
        //remove miss entry to NAPT switch
        //if naptswitch elect new switch and install Snat flows and remove those flows in oldnaptswitch

        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("removeSNATFromDPN : SNAT -> Invalid routerId returned for routerName {}", routerName);
            return;
        }
        Collection<String> externalIpCache = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
        if (extNwProvType == null) {
            return;
        }
        Map<String, Long> externalIpLabel;
        if (extNwProvType == ProviderTypes.VXLAN) {
            externalIpLabel = null;
        } else {
            externalIpLabel = NatUtil.getExternalIpsLabelForRouter(dataBroker, routerId);
        }
        try {
            final String externalVpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
            if (externalVpnName == null) {
                LOG.error("removeSNATFromDPN : SNAT -> No VPN associated with ext nw {} in router {}",
                    networkId, routerId);
                return;
            }

            BigInteger naptSwitch = dpnId;
            boolean naptStatus =
                naptSwitchHA.isNaptSwitchDown(routerName, routerId, dpnId, naptSwitch,
                        routerVpnId, externalIpCache, false, writeFlowInvTx);
            if (!naptStatus) {
                LOG.debug("removeSNATFromDPN:SNAT->NaptSwitchDown:Switch with DpnId {} is not naptSwitch for router {}",
                    dpnId, routerName);
                long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerName), idManager);
                FlowEntity flowEntity = null;
                try {
                    flowEntity = naptSwitchHA.buildSnatFlowEntity(dpnId, routerName, groupId,
                        routerVpnId, NatConstants.DEL_FLOW);
                    if (flowEntity == null) {
                        LOG.error("removeSNATFromDPN : SNAT -> Failed to populate flowentity for "
                            + "router {} with dpnId {} groupIs {}", routerName, dpnId, groupId);
                        return;
                    }
                    LOG.debug("removeSNATFromDPN : SNAT->Removing default SNAT miss entry flow entity {}", flowEntity);
                    mdsalManager.removeFlowToTx(flowEntity, writeFlowInvTx);

                } catch (Exception ex) {
                    LOG.error("removeSNATFromDPN : SNAT->Failed to remove default SNAT miss entry flow entity {}",
                        flowEntity, ex);
                    return;
                }
                LOG.debug("removeSNATFromDPN:SNAT->Removed default SNAT miss entry flow for dpnID {}, routername {}",
                    dpnId, routerName);

                //remove group
                GroupEntity groupEntity = null;
                try {
                    groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName,
                        GroupTypes.GroupAll, Collections.emptyList() /*listBucketInfo*/);
                    LOG.info("removeSNATFromDPN : SNAT->Removing NAPT GroupEntity:{} on Dpn {}", groupEntity, dpnId);
                    mdsalManager.removeGroup(groupEntity);
                } catch (Exception ex) {
                    LOG.error("removeSNATFromDPN : SNAT->Failed to remove group entity {}", groupEntity, ex);
                    return;
                }
                LOG.debug("removeSNATFromDPN : SNAT->Removed default SNAT miss entry flow for dpnID {}, routerName {}",
                    dpnId, routerName);
            } else {
                naptSwitchHA.removeSnatFlowsInOldNaptSwitch(routerName, routerId, dpnId,
                        externalIpLabel, writeFlowInvTx);
                //remove table 26 flow ppointing to table46
                FlowEntity flowEntity = null;
                try {
                    flowEntity = naptSwitchHA.buildSnatFlowEntityForNaptSwitch(dpnId, routerName, routerVpnId,
                        NatConstants.DEL_FLOW);
                    if (flowEntity == null) {
                        LOG.error("removeSNATFromDPN : SNAT->Failed to populate flowentity for router {} with dpnId {}",
                            routerName, dpnId);
                        return;
                    }
                    LOG.debug("removeSNATFromDPN : SNAT->Removing default SNAT miss entry flow entity for "
                        + "router {} with dpnId {} in napt switch {}", routerName, dpnId, naptSwitch);
                    mdsalManager.removeFlowToTx(flowEntity, writeFlowInvTx);

                } catch (Exception ex) {
                    LOG.error("removeSNATFromDPN : SNAT->Failed to remove default SNAT miss entry flow entity {}",
                        flowEntity, ex);
                    return;
                }
                LOG.debug("removeSNATFromDPN : SNAT->Removed default SNAT miss entry flow for dpnID {} "
                        + "with routername {}", dpnId, routerName);

                //best effort to check IntExt model
                naptSwitchHA.bestEffortDeletion(routerId, routerName, externalIpLabel, writeFlowInvTx);
            }
        } catch (Exception ex) {
            LOG.error("removeSNATFromDPN : SNAT->Exception while handling naptSwitch down for router {}",
                routerName, ex);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void hndlTepEvntsForDpn(StateTunnelList stateTunnelList, TunnelAction tunnelAction) {
        final BigInteger srcDpnId = new BigInteger(stateTunnelList.getSrcInfo().getTepDeviceId());
        final String srcTepIp = String.valueOf(stateTunnelList.getSrcInfo().getTepIp().getValue());
        final String destTepIp = String.valueOf(stateTunnelList.getDstInfo().getTepIp().getValue());
        LOG.trace("hndlTepEvntsForDpn : Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} ",
                srcDpnId, srcTepIp, destTepIp);
        int tunTypeVal = getTunnelType(stateTunnelList);
        LOG.trace("hndlTepEvntsForDpn : tunTypeVal is {}", tunTypeVal);
        try {
            String srcTepId = stateTunnelList.getSrcInfo().getTepDeviceId();
            String tunnelType = stateTunnelList.getTransportType().toString();
            String tunnelName = stateTunnelList.getTunnelInterfaceName();

            if (tunTypeVal == NatConstants.ITMTunnelLocType.Invalid.getValue()) {
                LOG.warn("hndlTepEvntsForDpn : Ignoring TEP event {} for the DPN {} "
                        + "since its a INVALID TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and " + "TUNNEL NAME {} ",
                    tunnelAction, srcTepId, tunnelType, srcTepIp, destTepIp, tunnelName);
                return;
            }

            switch (tunnelAction) {
                case TUNNEL_EP_ADD:
                    try {
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                            if (isTunnelInLogicalGroup(stateTunnelList)
                                    || !hndlTepAddForAllRtrs(srcDpnId, tunnelType, tunnelName, srcTepIp, destTepIp,
                                    tx)) {
                                LOG.debug("hndlTepEvntsForDpn : Unable to process TEP ADD");
                            }
                        }).get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("Error processing tunnel endpoint addition", e);
                    }
                    break;
                case TUNNEL_EP_DELETE:
                    try {
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                            if (!handleTepDelForAllRtrs(srcDpnId, tunnelType, tunnelName, srcTepIp, destTepIp, tx)) {
                                LOG.debug("hndlTepEvntsForDpn : Unable to process TEP DEL");
                            }
                        }).get();
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("Error processing tunnel endpoint removal", e);
                    }
                    break;
                case TUNNEL_EP_UPDATE:
                    break;
                default:
                    LOG.warn("hndlTepEvntsForDpn: unknown tunnelAction: {}", tunnelAction);
                    break;
            }
        } catch (Exception e) {
            LOG.error("hndlTepEvntsForDpn : Unable to handle the TEP event.", e);
        }
    }

    private boolean hndlTepAddForAllRtrs(BigInteger srcDpnId, String tunnelType, String tunnelName, String srcTepIp,
                                         String destTepIp, WriteTransaction writeFlowInvTx) {
        LOG.trace("hndlTepAddForAllRtrs: TEP ADD ----- for EXTERNAL/HWVTEP ITM Tunnel, TYPE {} ,State is UP b/w SRC IP"
            + " : {} and DEST IP: {}", fibManager.getTransportTypeStr(tunnelType), srcTepIp, destTepIp);

        InstanceIdentifier<DpnRoutersList> dpnRoutersListId = NatUtil.getDpnRoutersId(srcDpnId);
        Optional<DpnRoutersList> optionalRouterDpnList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, dpnRoutersListId);
        if (!optionalRouterDpnList.isPresent()) {
            LOG.info("hndlTepAddForAllRtrs : RouterDpnList model is empty for DPN {}. Hence ignoring TEP add event "
                    + "for the ITM TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and TUNNEL NAME {} ",
                srcDpnId, tunnelType, srcTepIp, destTepIp, tunnelName);
            return false;
        }

        List<RoutersList> routersList = optionalRouterDpnList.get().getRoutersList();
        if (routersList == null) {
            LOG.debug("hndlTepAddForAllRtrs : Ignoring TEP add for the DPN {} since no routers are associated"
                + " for the DPN having the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and"
                + "TUNNEL NAME {} ", srcDpnId, tunnelType, srcTepIp, destTepIp, tunnelName);
            return false;
        }

        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, srcDpnId);
        for (RoutersList router : routersList) {
            String routerName = router.getRouter();
            long routerId = NatUtil.getVpnId(dataBroker, routerName);
            if (routerId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepAddForAllRtrs :Invalid ROUTER-ID {} returned for routerName {}",
                        routerId, routerName);
                return false;
            }
            LOG.debug("hndlTepAddForAllRtrs : TEP ADD : DNAT -> Advertising routes for router {} ", routerName);
            Uuid externalNetworkId = NatUtil.getNetworkIdFromRouterName(dataBroker,routerName);
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                    routerName, externalNetworkId);
            if (extNwProvType == null) {
                return false;
            }
            hndlTepAddForDnatInEachRtr(router, routerId, nextHopIp, srcDpnId, extNwProvType, writeFlowInvTx);

            LOG.debug("hndlTepAddForAllRtrs : TEP ADD : SNAT -> Advertising routes for router {} ", routerName);
            hndlTepAddForSnatInEachRtr(router, routerId, srcDpnId, tunnelType, srcTepIp, destTepIp,
                tunnelName, nextHopIp, extNwProvType, writeFlowInvTx);
        }
        return true;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean handleTepDelForAllRtrs(BigInteger srcDpnId, String tunnelType, String tunnelName, String srcTepIp,
                                           String destTepIp, WriteTransaction writeFlowInvTx) {

        LOG.trace("handleTepDelForAllRtrs : TEP DEL ----- for EXTERNAL/HWVTEP ITM Tunnel,TYPE {},State is UP b/w SRC IP"
            + " : {} and DEST IP: {}", fibManager.getTransportTypeStr(tunnelType), srcTepIp, destTepIp);

        // When tunnel EP is deleted on a DPN , VPN gets two deletion event.
        // One for a DPN on which tunnel EP was deleted and another for other-end DPN.
        // Handle only the DPN on which it was deleted , ignore other event.
        // DPN on which TEP is deleted , endpoint IP will be null.
        String endpointIpForDPN = null;
        try {
            endpointIpForDPN = NatUtil.getEndpointIpAddressForDPN(dataBroker, srcDpnId);
        } catch (Exception e) {
                /* this dpn does not have the VTEP */
            LOG.error("handleTepDelForAllRtrs : DPN {} does not have the VTEP", srcDpnId);
            endpointIpForDPN = null;
        }

        if (endpointIpForDPN != null) {
            LOG.trace("handleTepDelForAllRtrs : Ignore TEP DELETE event received for DPN {} VTEP IP {} since its "
                 + "the other end DPN w.r.t the delted TEP", srcDpnId, srcTepIp);
            return false;
        }

        List<RoutersList> routersList = null;
        InstanceIdentifier<DpnRoutersList> dpnRoutersListId = NatUtil.getDpnRoutersId(srcDpnId);
        Optional<DpnRoutersList> optionalRouterDpnList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, dpnRoutersListId);
        if (optionalRouterDpnList.isPresent()) {
            routersList = optionalRouterDpnList.get().getRoutersList();
        } else {
            LOG.warn("handleTepDelForAllRtrs : RouterDpnList is empty for DPN {}.Hence ignoring TEP DEL event "
                    + "for the ITM TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and TUNNEL NAME {} ",
                srcDpnId, tunnelType, srcTepIp, destTepIp, tunnelName);
            return false;
        }

        if (routersList == null) {
            LOG.error("handleTepDelForAllRtrs : DPN {} does not have the Routers presence", srcDpnId);
            return false;
        }

        for (RoutersList router : routersList) {
            String routerName = router.getRouter();
            LOG.debug("handleTepDelForAllRtrs :  TEP DEL : DNAT -> Withdrawing routes for router {} ", routerName);
            long routerId = NatUtil.getVpnId(dataBroker, routerName);
            if (routerId == NatConstants.INVALID_ID) {
                LOG.error("handleTepDelForAllRtrs :Invalid ROUTER-ID {} returned for routerName {}",
                        routerId, routerName);
                return false;
            }
            Uuid externalNetworkId = NatUtil.getNetworkIdFromRouterName(dataBroker,routerName);
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                    routerName, externalNetworkId);
            if (extNwProvType == null) {
                return false;
            }
            hndlTepDelForDnatInEachRtr(router, routerId, srcDpnId, extNwProvType);
            LOG.debug("handleTepDelForAllRtrs :  TEP DEL : SNAT -> Withdrawing and Advertising routes for router {} ",
                router.getRouter());
            hndlTepDelForSnatInEachRtr(router, routerId, srcDpnId, tunnelType, srcTepIp, destTepIp,
                    tunnelName, extNwProvType, writeFlowInvTx);
        }
        return true;
    }

    private void hndlTepAddForSnatInEachRtr(RoutersList router, long routerId, final BigInteger srcDpnId,
            String tunnelType, String srcTepIp, String destTepIp, String tunnelName, String nextHopIp,
            ProviderTypes extNwProvType, WriteTransaction writeFlowInvTx) {

        /*SNAT : Remove the old routes to the external IP having the old TEP IP as the next hop IP
                 Advertise to the BGP about the new route to the external IP having the new TEP IP
                  added as the next hop IP
         */
        String routerName = router.getRouter();

        // Check if this is externalRouter else ignore
        InstanceIdentifier<Routers> extRoutersId = NatUtil.buildRouterIdentifier(routerName);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, extRoutersId);
        if (!routerData.isPresent()) {
            LOG.warn("hndlTepAddForSnatInEachRtr : SNAT->Ignoring TEP add for router {} since its not External Router",
                    routerName);
            return;
        }

        BigInteger naptId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptId == null || naptId.equals(BigInteger.ZERO)) {
            LOG.warn("hndlTepAddForSnatInEachRtr : SNAT -> Ignoring TEP add for the DPN {} having the router {} since"
                    + " the router is not part of the NAT service  - the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and"
                    + "TUNNEL NAME {} ", srcDpnId, routerName, tunnelType, srcTepIp, destTepIp, tunnelName);
            return;
        }
        if (natMode == NatMode.Conntrack) {
            natServiceManager.notify(routerData.get(), naptId, srcDpnId,
                    SnatServiceManager.Action.SNAT_ROUTER_ENBL);
        } else {
            Uuid bgpVpnUuId = NatUtil.getVpnForRouter(dataBroker, routerName);
            //Check if the DPN having the router is the NAPT switch
            if (!naptId.equals(srcDpnId)) {
                /*
            1) Install default NAT rule from table 21 to 26
            2) Install the group which forward packet to the tunnel port for the NAPT switch.
            3) Install the flow 26 which forwards the packet to the group.
                 */
                if (!hndlTepAddOnNonNaptSwitch(srcDpnId, naptId, tunnelType, srcTepIp, destTepIp, tunnelName,
                        routerName, routerId, bgpVpnUuId, writeFlowInvTx)) {
                    LOG.error("hndlTepAddForSnatInEachRtr : Unable to process the TEP add event on NON-NAPT switch {}",
                            srcDpnId);
                    return;
                }
                return;
            }
            if (!hndlTepAddOnNaptSwitch(srcDpnId, tunnelType, srcTepIp, destTepIp, tunnelName, routerId,
                    routerData, nextHopIp, bgpVpnUuId, extNwProvType, writeFlowInvTx)) {
                LOG.debug("hndlTepAddForSnatInEachRtr : Unable to process the TEP add event on NAPT switch {}",
                        srcDpnId);
                return;
            }
        }
        return;
    }

    private boolean hndlTepAddOnNonNaptSwitch(BigInteger srcDpnId, BigInteger primaryDpnId, String tunnelType,
                                              String srcTepIp, String destTepIp, String tunnelName, String routerName,
                                              long routerId, Uuid vpnName, WriteTransaction writeFlowInvTx) {

        /*
        1) Install default NAT rule from table 21 to 26
        2) Install the group which forward packet to the tunnel port for the NAPT switch.
        3) Install the flow 26 which forwards the packet to the group.
        */
        LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> Processing TEP add for the DPN {} having the router {} since "
                + "its THE NON NAPT switch for the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} "
                + "and TUNNEL NAME {} ",
            srcDpnId, routerName, tunnelType, srcTepIp, destTepIp, tunnelName);
        LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> Install default NAT rule from table 21 to 26");
        Long vpnId;
        if (vpnName == null) {
            LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> Internal VPN associated to router {}", routerId);
            vpnId = routerId;
            if (vpnId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepAddOnNonNaptSwitch : SNAT -> Invalid Internal VPN ID returned for routerName {}",
                        routerId);
                return false;
            }
            LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> Retrieved vpnId {} for router {}", vpnId, routerName);
            //Install default entry in FIB to SNAT table
            LOG.debug("hndlTepAddOnNonNaptSwitch : Installing default route in FIB on DPN {} for router {} with"
                + " vpn {}...", srcDpnId, routerName, vpnId);
            defaultRouteProgrammer.installDefNATRouteInDPN(srcDpnId, vpnId, writeFlowInvTx);

            LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> Install the group which forward packet to the tunnel port "
                + "for the NAPT switch {} and the flow 26 which forwards to group", primaryDpnId);
            externalRouterListner.handleSwitches(srcDpnId, routerName, routerId, primaryDpnId);
        } else {
            LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> External BGP VPN (Private BGP) associated to router {}",
                    routerId);
            vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
            if (vpnId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepAddOnNonNaptSwitch : SNAT -> Invalid Private BGP VPN ID returned for routerName {}",
                        routerId);
                return false;
            }
            if (routerId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepAddOnNonNaptSwitch : SNAT -> Invalid routId returned for routerName {}", routerId);
                return false;
            }
            LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> Retrieved vpnId {} for router {}", vpnId, routerId);
            //Install default entry in FIB to SNAT table
            LOG.debug("hndlTepAddOnNonNaptSwitch : Installing default route in FIB on dpn {} for routerId {} "
                + "with vpnId {}...", srcDpnId, routerId, vpnId);
            defaultRouteProgrammer.installDefNATRouteInDPN(srcDpnId, vpnId, routerId, writeFlowInvTx);

            LOG.debug("hndlTepAddOnNonNaptSwitch : Install group in non NAPT switch {} for router {}",
                    srcDpnId, routerName);
            List<BucketInfo> bucketInfoForNonNaptSwitches =
                externalRouterListner.getBucketInfoForNonNaptSwitches(srcDpnId, primaryDpnId, routerName, routerId);
            long groupId = externalRouterListner.installGroup(srcDpnId, routerName, bucketInfoForNonNaptSwitches);

            LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> in the SNAT miss entry pointing to group {} "
                    + "in the non NAPT switch {}", groupId, srcDpnId);
            FlowEntity flowEntity =
                externalRouterListner.buildSnatFlowEntityWithUpdatedVpnId(srcDpnId, routerName, groupId, vpnId);
            mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
        }
        return true;
    }

    private boolean hndlTepAddOnNaptSwitch(BigInteger srcDpnId, String tunnelType, String srcTepIp,
                                           String destTepIp, String tunnelName, long routerId,
                                           Optional<Routers> routerData, String nextHopIp, Uuid vpnName,
                                           ProviderTypes extNwProvType, WriteTransaction writeFlowInvTx) {
        if (!routerData.isPresent()) {
            LOG.warn("hndlTepAddOnNaptSwitch: routerData is not present");
            return false;
        }
        Routers router = routerData.get();
        String routerName = router.getRouterName();
        LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Processing TEP add for the DPN {} having the router {} since "
            + "its THE NAPT switch for the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} "
            + "and TUNNEL NAME {} ", srcDpnId, routerName, tunnelType, srcTepIp, destTepIp, tunnelName);

        Uuid networkId = router.getNetworkId();
        if (networkId == null) {
            LOG.warn("hndlTepAddOnNaptSwitch : SNAT -> Ignoring TEP add since the router {} is not associated to the "
                + "external network", routerName);
            return false;
        }

        LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Router {} is associated with Ext nw {}", routerId, networkId);
        Long vpnId;
        if (vpnName == null) {
            LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Internal VPN associated to router {}", routerId);
            vpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
            if (vpnId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepAddOnNaptSwitch : Invalid External VPN-ID returned for routerName {}", routerName);
                return false;
            }
            LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Retrieved External VPN-ID {} for router {}", vpnId, routerId);
        } else {
            LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Private BGP VPN associated to router {}", routerId);
            vpnId = NatUtil.getVpnId(dataBroker, vpnName.getValue());
            if (vpnId == null || vpnId == NatConstants.INVALID_ID) {
                LOG.error("hndlTepAddOnNaptSwitch : Invalid vpnId returned for routerName {}", routerName);
                return false;
            }
            LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Retrieved vpnId {} for router {}", vpnId, routerId);
        }

        /*1) Withdraw the old route to the external IP from the BGP which was having the
             next hop as the old TEP IP.
          2) Advertise to the BGP about the new route to the external IP having the
          new TEP IP as the next hop.
          3) Populate a new FIB entry with the next hop IP as the new TEP IP using the
          FIB manager.
        */

        //Withdraw the old route to the external IP from the BGP which was having the
        //next hop as the old TEP IP.
        final String externalVpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
        if (externalVpnName == null) {
            LOG.error("hndlTepAddOnNaptSwitch :  SNAT -> No VPN associated with ext nw {} in router {}",
                networkId, routerId);
            return false;
        }
        Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
        LOG.debug("hndlTepAddOnNaptSwitch : Clearing the FIB entries but not the BGP routes");
        for (String externalIp : externalIps) {
            String rd = NatUtil.getVpnRd(dataBroker, externalVpnName);
            LOG.debug("hndlTepAddOnNaptSwitch : Removing Fib entry rd {} prefix {}", rd, externalIp);
            fibManager.removeFibEntry(rd, externalIp, null);
        }

        /*
        Advertise to the BGP about the new route to the external IP having the
        new TEP IP as the next hop.
        Populate a new FIB entry with the next hop IP as the new TEP IP using the
        FIB manager.
        */
        String rd = NatUtil.getVpnRd(dataBroker, externalVpnName);
        if (extNwProvType == null) {
            return false;
        }
        String gwMacAddress = null;
        long l3Vni = 0;
        if (extNwProvType == ProviderTypes.VXLAN) {
            // Get the External Gateway MAC Address which is Router gateway MAC address for SNAT
            gwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
            if (gwMacAddress != null) {
                LOG.debug("hndlTepAddOnNaptSwitch : External Gateway MAC address {} found for External Router ID {}",
                        gwMacAddress, routerId);
            } else {
                LOG.error("hndlTepAddOnNaptSwitch : No External Gateway MAC address found for External Router ID {}",
                        routerId);
                return false;
            }
            //get l3Vni value for external VPN
            l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
            if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
                LOG.debug("hndlTepAddOnNaptSwitch : L3VNI value is not configured in Internet VPN {} and RD {} "
                        + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue to installing "
                        + "NAT flows", vpnName, rd);
                l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, externalVpnName, routerId).longValue();
            }
        }

        for (final String externalIp : externalIps) {
            long serviceId = 0;
            String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
            if (extNwProvType == ProviderTypes.VXLAN) {
                LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Advertise the route to the externalIp {} "
                        + "having nextHopIp {}", externalIp, nextHopIp);
                NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, externalVpnName, rd,
                        externalIp, nextHopIp, l3Vni, tunnelName, gwMacAddress, writeFlowInvTx, RouteOrigin.STATIC,
                        srcDpnId, networkId);
                serviceId = l3Vni;
            } else {

                Long label = externalRouterListner.checkExternalIpLabel(routerId,
                        externalIp);
                if (label == null || label == NatConstants.INVALID_ID) {
                    LOG.error("hndlTepAddOnNaptSwitch : SNAT->Unable to advertise to the DC GW "
                            + "since label is invalid");
                    return false;
                }
                LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Advertise the route to the externalIp {} "
                        + "having nextHopIp {}", externalIp, nextHopIp);
                long l3vni = 0;
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    l3vni = NatOverVxlanUtil.getInternetVpnVni(idManager, externalVpnName, l3vni).longValue();
                }
                Uuid externalSubnetId = NatUtil.getExternalSubnetForRouterExternalIp(externalIp, router);
                NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, externalVpnName, rd, externalSubnetId,
                        fibExternalIp, nextHopIp, networkId.getValue(), null /* mac-address */, label, l3vni,
                        RouteOrigin.STATIC, srcDpnId);
                serviceId = label;
            }

            LOG.debug("hndlTepAddOnNaptSwitch: SNAT -> Install custom FIB routes "
                    + "(Table 21 -> Push MPLS label to Tunnel port");
            List<Instruction> customInstructions = new ArrayList<>();
            int customInstructionIndex = 0;
            long externalSubnetVpnId = NatUtil.getExternalSubnetVpnIdForRouterExternalIp(dataBroker, externalIp,
                    router);
            if (externalSubnetVpnId != NatConstants.INVALID_ID) {
                LOG.debug("hndlTepAddOnNaptSwitch : Will install custom FIB router with external subnet VPN ID {}",
                        externalSubnetVpnId);
                BigInteger subnetIdMetaData = MetaDataUtil.getVpnIdMetadata(externalSubnetVpnId);
                customInstructions.add(new InstructionWriteMetadata(subnetIdMetaData,
                        MetaDataUtil.METADATA_MASK_VRFID).buildInstruction(customInstructionIndex));
                customInstructionIndex++;
            }
            customInstructions.add(new InstructionGotoTable(NwConstants.INBOUND_NAPT_TABLE)
                    .buildInstruction(customInstructionIndex));
            CreateFibEntryInput input =
                    new CreateFibEntryInputBuilder().setVpnName(externalVpnName).setSourceDpid(srcDpnId)
                    .setInstruction(customInstructions).setIpAddress(fibExternalIp)
                    .setIpAddressSource(FibEntryInputs.IpAddressSource.ExternalFixedIP)
                    .setServiceId(serviceId).setInstruction(customInstructions).build();
            ListenableFuture<RpcResult<CreateFibEntryOutput>> listenableFuture = fibRpcService.createFibEntry(input);

            Futures.addCallback(listenableFuture, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {

                @Override
                public void onFailure(@Nonnull Throwable error) {
                    LOG.error("hndlTepAddOnNaptSwitch : SNAT->Error in generate label or fib install process",
                            error);
                }

                @Override
                public void onSuccess(@Nonnull RpcResult<CreateFibEntryOutput> result) {
                    if (result.isSuccessful()) {
                        LOG.info("hndlTepAddOnNaptSwitch : SNAT -> Successfully installed custom FIB routes "
                                + "for prefix {}", externalIp);
                    } else {
                        LOG.error("hndlTepAddOnNaptSwitch : SNAT -> Error in rpc call to create custom Fib entries "
                                + "for prefix {} in DPN {}, {}", externalIp, srcDpnId, result.getErrors());
                    }
                }
            }, MoreExecutors.directExecutor());
        }

        return true;
    }

    private void hndlTepAddForDnatInEachRtr(RoutersList router, long routerId, String nextHopIp,
            BigInteger tepAddedDpnId, ProviderTypes extNwProvType, WriteTransaction writeFlowInvTx) {
        //DNAT : Advertise the new route to the floating IP having the new TEP IP as the next hop IP
        final String routerName = router.getRouter();

        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType
            .CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Could not read Router Ports data object with id: {} "
                    + "from DNAT FloatinIpInfo", routerName);
            return;
        }
        RouterPorts routerPorts = optRouterPorts.get();
        Uuid extNwId = routerPorts.getExternalNetworkId();
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, extNwId);
        if (vpnName == null) {
            LOG.info("hndlTepAddForDnatInEachRtr : DNAT -> No External VPN associated with ext nw {} for router {}",
                extNwId, routerName);
            return;
        }

        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (extNwProvType == null) {
            return;
        }
        String gwMacAddress = null;
        long l3Vni = 0;
        if (extNwProvType == ProviderTypes.VXLAN) {
            // Get the External Gateway MAC Address which is Router gateway MAC address for SNAT
            gwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
            if (gwMacAddress != null) {
                LOG.debug("hndlTepAddForDnatInEachRtr : External GwMAC address {} found for External Router ID {}",
                        gwMacAddress, routerId);
            } else {
                LOG.error("hndlTepAddForDnatInEachRtr : No External GwMAC address found for External Router ID {}",
                        routerId);
                return;
            }
            //get l3Vni value for external VPN
            l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
            if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
                LOG.debug("hndlTepAddForDnatInEachRtr : L3VNI value is not configured in Internet VPN {} and RD {} "
                        + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue to installing "
                        + "NAT flows", vpnName, rd);
                l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
            }
        }
        List<Ports> interfaces = routerPorts.getPorts();
        for (Ports port : interfaces) {
            //Get the DPN on which this interface resides
            final String interfaceName = port.getPortName();
            final BigInteger fipCfgdDpnId = NatUtil.getDpnForInterface(interfaceService, interfaceName);
            if (fipCfgdDpnId.equals(BigInteger.ZERO)) {
                LOG.info("hndlTepAddForDnatInEachRtr : DNAT->Skip processing Floating ip configuration for the port {},"
                    + "since no DPN present for it", interfaceName);
                continue;
            }
            if (!fipCfgdDpnId.equals(tepAddedDpnId)) {
                LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> TEP added DPN {} is not the DPN {} which has the "
                    + "floating IP configured for the port: {}",
                    tepAddedDpnId, fipCfgdDpnId, interfaceName);
                continue;
            }
            List<InternalToExternalPortMap> intExtPortMapList = port.getInternalToExternalPortMap();
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                final String internalIp = intExtPortMap.getInternalIp();
                final String externalIp = intExtPortMap.getExternalIp();
                LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Advertising the FIB route to the floating IP {} "
                        + "configured for the port: {}", externalIp, interfaceName);
                long serviceId = 0;
                String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);

                if (extNwProvType == ProviderTypes.VXLAN) {
                    LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Advertise the route to the externalIp {} "
                            + "having nextHopIp {}", externalIp, nextHopIp);
                    NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd,
                            externalIp, nextHopIp, l3Vni, interfaceName, gwMacAddress, writeFlowInvTx,
                            RouteOrigin.STATIC, fipCfgdDpnId, extNwId);
                    serviceId = l3Vni;
                } else {
                    long label = floatingIPListener.getOperationalIpMapping(routerName, interfaceName, internalIp);
                    if (label == NatConstants.INVALID_ID) {
                        LOG.error("hndlTepAddForDnatInEachRtr : DNAT -> Unable to advertise to the DC GW since label "
                                + "is invalid");
                        return;
                    }
                    LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Advertise the route to the externalIp {} "
                            + "having nextHopIp {}", externalIp, nextHopIp);
                    long l3vni = 0;
                    if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                        l3vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, l3vni).longValue();
                    }
                    NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd, null,
                            fibExternalIp, nextHopIp, null, null, label, l3vni, RouteOrigin.STATIC,
                            fipCfgdDpnId);
                    serviceId = label;
                }

                //Install custom FIB routes (Table 21 -> Push MPLS label to Tunnel port
                List<Instruction> customInstructions = new ArrayList<>();
                customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(0));
                CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                    .setSourceDpid(fipCfgdDpnId).setInstruction(customInstructions)
                    .setIpAddressSource(FibEntryInputs.IpAddressSource.FloatingIP)
                    .setIpAddress(fibExternalIp).setServiceId(serviceId).setInstruction(customInstructions)
                        .build();
                ListenableFuture<RpcResult<CreateFibEntryOutput>> listenableFuture =
                        fibRpcService.createFibEntry(input);

                Futures.addCallback(listenableFuture, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {

                    @Override
                    public void onFailure(@Nonnull Throwable error) {
                        LOG.error("hndlTepAddForDnatInEachRtr : DNAT -> Error in generate label or fib install process",
                                error);
                    }

                    @Override
                    public void onSuccess(@Nonnull RpcResult<CreateFibEntryOutput> result) {
                        if (result.isSuccessful()) {
                            LOG.info("hndlTepAddForDnatInEachRtr : DNAT -> Successfully installed custom FIB routes "
                                    + "for prefix {}", externalIp);
                        } else {
                            LOG.error("hndlTepAddForDnatInEachRtr : DNAT -> Error in rpc call to create custom Fib "
                                + "entries for prefix {} in DPN {}, {}", externalIp, fipCfgdDpnId, result.getErrors());
                        }
                    }
                }, MoreExecutors.directExecutor());
            }
        }
    }

    private void hndlTepDelForSnatInEachRtr(RoutersList router, long routerId, BigInteger dpnId, String tunnelType,
                                            String srcTepIp, String destTepIp, String tunnelName,
                                            ProviderTypes extNwProvType, WriteTransaction writeFlowInvTx) {
       /*SNAT :
            1) Elect a new switch as the primary NAPT
            2) Advertise the new routes to BGP for the newly elected TEP IP as the DPN IP
            3) This will make sure old routes are withdrawn and new routes are advertised.
         */

        String routerName = router.getRouter();
        LOG.debug("hndlTepDelForSnatInEachRtr : SNAT -> Trying to clear routes to the External fixed IP associated "
                + "to the router {}", routerName);

        // Check if this is externalRouter else ignore
        InstanceIdentifier<Routers> extRoutersId = NatUtil.buildRouterIdentifier(routerName);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, extRoutersId);
        if (!routerData.isPresent()) {
            LOG.debug("hndlTepDelForSnatInEachRtr : SNAT->Ignoring TEP del for router {} since its not External Router",
                    routerName);
            return;
        }

        //Check if the DPN having the router is the NAPT switch
        BigInteger naptId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptId == null || naptId.equals(BigInteger.ZERO) || !naptId.equals(dpnId)) {
            LOG.warn("hndlTepDelForSnatInEachRtr : SNAT -> Ignoring TEP delete for the DPN {} since"
                    + " its NOT a NAPT switch for the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and"
                    + "TUNNEL NAME {} ", dpnId, tunnelType, srcTepIp, destTepIp, tunnelName);
            return;
        }
        if (natMode == NatMode.Conntrack) {
            natServiceManager.notify(routerData.get(), naptId, dpnId, SnatServiceManager.Action.SNAT_ROUTER_DISBL);
        } else {


            Uuid networkId = routerData.get().getNetworkId();
            if (networkId == null) {
                LOG.error("hndlTepDelForSnatInEachRtr : SNAT->Ignoring TEP delete for the DPN {} having the router {} "
                                + "since the Router instance {} not found in ExtRouters model b/w SRC IP {} and DST "
                                + "IP {} and TUNNEL NAME {} ", dpnId, routerData.get().getRouterName(), tunnelType,
                        srcTepIp, destTepIp, tunnelName);
                return;
            }

            LOG.debug("hndlTepDelForSnatInEachRtr : SNAT->Router {} is associated with ext nw {}", routerId, networkId);
            Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
            Long bgpVpnId;
            if (bgpVpnUuid == null) {
                LOG.debug("hndlTepDelForSnatInEachRtr : SNAT->Internal VPN-ID {} associated to router {}",
                        routerId, routerName);
                bgpVpnId = routerId;

                //Install default entry in FIB to SNAT table
                LOG.debug("hndlTepDelForSnatInEachRtr : Installing default route in FIB on DPN {} for router {} with"
                        + " vpn {}...", dpnId, routerName, bgpVpnId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, bgpVpnId, writeFlowInvTx);
            } else {
                bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                if (bgpVpnId == NatConstants.INVALID_ID) {
                    LOG.error("hndlTepDelForSnatInEachRtr :SNAT->Invalid Private BGP VPN ID returned for routerName {}",
                            routerName);
                    return;
                }
                LOG.debug("hndlTepDelForSnatInEachRtr :SNAT->External BGP VPN (Private BGP) {} associated to router {}",
                        bgpVpnId, routerName);
                //Install default entry in FIB to SNAT table
                LOG.debug("hndlTepDelForSnatInEachRtr : Installing default route in FIB on dpn {} for routerId {} "
                        + "with vpnId {}...", dpnId, routerId, bgpVpnId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, bgpVpnId, routerId, writeFlowInvTx);
            }

            if (routerData.get().isEnableSnat()) {
                LOG.info("hndlTepDelForSnatInEachRtr : SNAT enabled for router {}", routerId);

                long routerVpnId = routerId;
                if (bgpVpnId != NatConstants.INVALID_ID) {
                    LOG.debug("hndlTepDelForSnatInEachRtr : SNAT -> Private BGP VPN ID (Internal BGP VPN ID) {} "
                            + "associated to the router {}", bgpVpnId, routerName);
                    routerVpnId = bgpVpnId;
                } else {
                    LOG.debug("hndlTepDelForSnatInEachRtr : SNAT -> Internal L3 VPN ID (Router ID) {} "
                            + "associated to the router {}", routerVpnId, routerName);
                }
                //Re-elect the other available switch as the NAPT switch and program the NAT flows.
                removeSNATFromDPN(dpnId, routerName, routerId, routerVpnId, networkId, extNwProvType, writeFlowInvTx);
            } else {
                LOG.info("hndlTepDelForSnatInEachRtr : SNAT is not enabled for router {} to handle addDPN event {}",
                        routerId, dpnId);
            }
        }
    }

    private void hndlTepDelForDnatInEachRtr(RoutersList router, long routerId, BigInteger tepDeletedDpnId,
            ProviderTypes extNwProvType) {
        //DNAT : Withdraw the routes from the BGP
        String routerName = router.getRouter();

        LOG.debug("hndlTepDelForDnatInEachRtr : DNAT -> Trying to clear routes to the Floating IP "
                + "associated to the router {}", routerName);

        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType
            .CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.debug("hndlTepDelForDnatInEachRtr : DNAT -> Could not read Router Ports data object with id: {} "
                    + "from DNAT FloatingIpInfo", routerName);
            return;
        }
        RouterPorts routerPorts = optRouterPorts.get();
        Uuid extNwId = routerPorts.getExternalNetworkId();
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, extNwId);
        if (vpnName == null) {
            LOG.error("hndlTepDelForDnatInEachRtr : DNAT -> No External VPN associated with Ext N/W {} for Router {}",
                extNwId, routerName);
            return;
        }
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (extNwProvType == null) {
            return;
        }
        long l3Vni = 0;
        if (extNwProvType == ProviderTypes.VXLAN) {
            //get l3Vni value for external VPN
            l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
            if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
                LOG.debug("hndlTepDelForDnatInEachRtr : L3VNI value is not configured in Internet VPN {} and RD {} "
                        + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue to installing "
                        + "NAT flows", vpnName, rd);
                l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
            }
        }
        List<Ports> interfaces = routerPorts.getPorts();
        for (Ports port : interfaces) {
            //Get the DPN on which this interface resides
            String interfaceName = port.getPortName();
            BigInteger fipCfgdDpnId = NatUtil.getDpnForInterface(interfaceService, interfaceName);
            if (fipCfgdDpnId.equals(BigInteger.ZERO)) {
                LOG.info("hndlTepDelForDnatInEachRtr : DNAT -> Abort processing Floating ip configuration. "
                        + "No DPN for port : {}", interfaceName);
                continue;
            }
            if (!fipCfgdDpnId.equals(tepDeletedDpnId)) {
                LOG.info("hndlTepDelForDnatInEachRtr : DNAT -> TEP deleted DPN {} is not the DPN {} which has the "
                    + "floating IP configured for the port: {}",
                    tepDeletedDpnId, fipCfgdDpnId, interfaceName);
                continue;
            }
            List<InternalToExternalPortMap> intExtPortMapList = port.getInternalToExternalPortMap();
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                String internalIp = intExtPortMap.getInternalIp();
                String externalIp = intExtPortMap.getExternalIp();
                externalIp = NatUtil.validateAndAddNetworkMask(externalIp);
                LOG.debug("hndlTepDelForDnatInEachRtr : DNAT -> Withdrawing the FIB route to the floating IP {} "
                    + "configured for the port: {}",
                    externalIp, interfaceName);
                NatUtil.removePrefixFromBGP(bgpManager, fibManager, rd, externalIp, vpnName, LOG);
                long serviceId = 0;
                if (extNwProvType == ProviderTypes.VXLAN) {
                    serviceId = l3Vni;
                } else {
                    long label = floatingIPListener.getOperationalIpMapping(routerName, interfaceName, internalIp);
                    if (label == NatConstants.INVALID_ID) {
                        LOG.error("hndlTepDelForDnatInEachRtr : DNAT -> Unable to remove the table 21 entry pushing the"
                                + " MPLS label to the tunnel since label is invalid");
                        return;
                    }
                    serviceId = label;
                }

                RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                    .setSourceDpid(fipCfgdDpnId).setIpAddress(externalIp).setServiceId(serviceId)
                    .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.FloatingIP).build();
                ListenableFuture<RpcResult<RemoveFibEntryOutput>> listenableFuture =
                        fibRpcService.removeFibEntry(input);

                Futures.addCallback(listenableFuture, new FutureCallback<RpcResult<RemoveFibEntryOutput>>() {

                    @Override
                    public void onFailure(@Nonnull Throwable error) {
                        LOG.error("hndlTepDelForDnatInEachRtr : DNAT -> Error in removing the table 21 entry pushing "
                            + "the MPLS label to the tunnel since label is invalid ", error);
                    }

                    @Override
                    public void onSuccess(@Nonnull RpcResult<RemoveFibEntryOutput> result) {
                        if (result.isSuccessful()) {
                            LOG.info("hndlTepDelForDnatInEachRtr : DNAT -> Successfully removed the entry pushing the "
                                + "MPLS label to the tunnel");
                        } else {
                            LOG.error("hndlTepDelForDnatInEachRtr : DNAT -> Error in fib rpc call to remove the table "
                                + "21 entry pushing the MPLS label to the tunnnel due to {}", result.getErrors());
                        }
                    }
                }, MoreExecutors.directExecutor());
            }
        }
    }

    protected boolean isTunnelInLogicalGroup(StateTunnelList stateTunnelList) {
        String ifaceName = stateTunnelList.getTunnelInterfaceName();
        if (getTunnelType(stateTunnelList) == NatConstants.ITMTunnelLocType.Internal.getValue()) {
            Interface configIface = interfaceManager.getInterfaceInfoFromConfigDataStore(ifaceName);
            IfTunnel ifTunnel = configIface != null ? configIface.augmentation(IfTunnel.class) : null;
            if (ifTunnel != null && ifTunnel.getTunnelInterfaceType().isAssignableFrom(TunnelTypeVxlan.class)) {
                ParentRefs refs = configIface.augmentation(ParentRefs.class);
                if (refs != null && !Strings.isNullOrEmpty(refs.getParentInterface())) {
                    return true; //multiple VxLAN tunnels enabled, i.e. only logical tunnel should be treated
                }
            }
        }
        LOG.trace("isTunnelInLogicalGroup: ignoring the tunnel event for {}", ifaceName);
        return false;
    }
}
