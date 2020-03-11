/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibEntryInputs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.DpnRoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.routers.dpn.routers.list.RoutersListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatTunnelInterfaceStateListener extends AbstractAsyncDataTreeChangeListener<StateTunnelList> {

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
    private final NatOverVxlanUtil natOverVxlanUtil;
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
     * @param natOverVxlanUtils      - Nat Over Vxlan Utility
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
                                           final IInterfaceManager interfaceManager,
                                           final NatOverVxlanUtil natOverVxlanUtils) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(TunnelsState.class)
                .child(StateTunnelList.class),
                Executors.newListeningSingleThreadExecutor("NatTunnelInterfaceStateListener", LOG));
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
        this.natOverVxlanUtil = natOverVxlanUtils;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatMode.Controller;
        }
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void add(InstanceIdentifier<StateTunnelList> instanceIdentifier, StateTunnelList add) {
        LOG.trace("add : TEP addtion---- {}", add);
        hndlTepEvntsForDpn(add, TunnelAction.TUNNEL_EP_ADD);
    }

    @Override
    public void remove(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList del) {
        LOG.trace("remove : TEP deletion---- {}", del);
        // Moved the remove implementation logic to NatTepChangeLister.remove()
    }

    @Override
    public void update(InstanceIdentifier<StateTunnelList> identifier, StateTunnelList original,
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

    private void hndlTepEvntsForDpn(StateTunnelList stateTunnelList, TunnelAction tunnelAction) {
        LOG.trace("hndlTepEvntsForDpn : stateTunnelList {}", stateTunnelList);
        final Uint64 srcDpnId = stateTunnelList.getSrcInfo() != null
                ? Uint64.valueOf(stateTunnelList.getSrcInfo().getTepDeviceId()) : Uint64.ZERO;
        final String srcTepIp = stateTunnelList.getSrcInfo() != null
                ? stateTunnelList.getSrcInfo().getTepIp().stringValue() : null;
        final String destTepIp = stateTunnelList.getDstInfo() != null
                ? stateTunnelList.getDstInfo().getTepIp().stringValue() : null;
        LOG.trace("hndlTepEvntsForDpn : Handle tunnel event for srcDpn {} SrcTepIp {} DestTepIp {} ",
                srcDpnId, srcTepIp, destTepIp);
        if (srcDpnId == Uint64.ZERO || srcTepIp == null || destTepIp == null) {
            LOG.error("hndlTepEvntsForDpn invalid srcDpnId {}, srcTepIp {}, destTepIp {}",
                    srcDpnId, srcTepIp, destTepIp);
            return;
        }
        int tunTypeVal = getTunnelType(stateTunnelList);
        LOG.trace("hndlTepEvntsForDpn : tunTypeVal is {}", tunTypeVal);
        String srcTepId = stateTunnelList.getSrcInfo().getTepDeviceId() != null
                ? stateTunnelList.getSrcInfo().getTepDeviceId() : "0";
        String tunnelType = stateTunnelList.getTransportType() != null
                ? stateTunnelList.getTransportType().toString() : null;
        String tunnelName = stateTunnelList.getTunnelInterfaceName();

        if (tunTypeVal == NatConstants.ITMTunnelLocType.Invalid.getValue() || srcDpnId.equals(Uint64.ZERO)
                || srcTepIp == null || destTepIp == null) {
            LOG.warn("hndlTepEvntsForDpn : Ignoring TEP event {} for the DPN {} "
                    + "since its a INVALID TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and " + "TUNNEL NAME {} ",
                tunnelAction, srcTepId, tunnelType, srcTepIp, destTepIp, tunnelName);
            return;
        }

        switch (tunnelAction) {
            case TUNNEL_EP_ADD:
                try {
                    txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
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
                // Moved the current implementation logic to NatTepChangeListener.remove()
                break;
            case TUNNEL_EP_UPDATE:
                break;
            default:
                LOG.warn("hndlTepEvntsForDpn: unknown tunnelAction: {}", tunnelAction);
                break;
        }
    }

    private boolean hndlTepAddForAllRtrs(Uint64 srcDpnId, String tunnelType, String tunnelName, String srcTepIp,
                                         String destTepIp, TypedReadWriteTransaction<Configuration> writeFlowInvTx)
            throws ExecutionException, InterruptedException {
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

        Map<RoutersListKey, RoutersList> keyRoutersListMap = optionalRouterDpnList.get().getRoutersList();
        if (keyRoutersListMap == null) {
            LOG.debug("hndlTepAddForAllRtrs : Ignoring TEP add for the DPN {} since no routers are associated"
                + " for the DPN having the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and"
                + "TUNNEL NAME {} ", srcDpnId, tunnelType, srcTepIp, destTepIp, tunnelName);
            return false;
        }

        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, srcDpnId);
        for (RoutersList router : keyRoutersListMap.values()) {
            String routerName = router.getRouter();
            Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
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

    private void hndlTepAddForSnatInEachRtr(RoutersList router, Uint32 routerId, final Uint64 srcDpnId,
            String tunnelType, String srcTepIp, String destTepIp, String tunnelName, String nextHopIp,
            ProviderTypes extNwProvType, TypedReadWriteTransaction<Configuration> writeFlowInvTx)
            throws ExecutionException, InterruptedException {

        /*SNAT : Remove the old routes to the external IP having the old TEP IP as the next hop IP
                 Advertise to the BGP about the new route to the external IP having the new TEP IP
                  added as the next hop IP
         */
        String routerName = router.getRouter();

        // Check if this is externalRouter else ignore
        InstanceIdentifier<Routers> extRoutersId = NatUtil.buildRouterIdentifier(routerName);
        Optional<Routers> routerData;
        try {
            routerData = writeFlowInvTx.read(extRoutersId).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error reading router data for {}", extRoutersId, e);
            routerData = Optional.empty();
        }
        if (!routerData.isPresent()) {
            LOG.warn("hndlTepAddForSnatInEachRtr : SNAT->Ignoring TEP add for router {} since its not External Router",
                    routerName);
            return;
        }

        Uint64 naptId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptId == null || naptId.equals(Uint64.ZERO)) {
            LOG.warn("hndlTepAddForSnatInEachRtr : SNAT -> Ignoring TEP add for the DPN {} having the router {} since"
                    + " the router is not part of the NAT service  - the TUNNEL TYPE {} b/w SRC IP {} and DST IP {} and"
                    + "TUNNEL NAME {} ", srcDpnId, routerName, tunnelType, srcTepIp, destTepIp, tunnelName);
            return;
        }
        if (natMode == NatMode.Conntrack) {
            Routers extRouter = routerData.get();
            natServiceManager.notify(writeFlowInvTx, extRouter, null, naptId, srcDpnId,
                    SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_ENBL);
            if (extRouter.isEnableSnat()) {
                natServiceManager.notify(writeFlowInvTx, extRouter, null, naptId, srcDpnId,
                        SnatServiceManager.Action.SNAT_ROUTER_ENBL);
            }
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

    private boolean hndlTepAddOnNonNaptSwitch(Uint64 srcDpnId, Uint64 primaryDpnId, String tunnelType,
        String srcTepIp, String destTepIp, String tunnelName, String routerName, Uint32 routerId, Uuid vpnName,
        TypedWriteTransaction<Configuration> confTx) {

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
        Uint32 vpnId;
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
            defaultRouteProgrammer.installDefNATRouteInDPN(srcDpnId, vpnId, confTx);

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
            defaultRouteProgrammer.installDefNATRouteInDPN(srcDpnId, vpnId, routerId, confTx);

            LOG.debug("hndlTepAddOnNonNaptSwitch : Install group in non NAPT switch {} for router {}",
                    srcDpnId, routerName);
            List<BucketInfo> bucketInfoForNonNaptSwitches =
                externalRouterListner.getBucketInfoForNonNaptSwitches(srcDpnId, primaryDpnId, routerName, routerId);
            Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
                NatUtil.getGroupIdKey(routerName));
            if (groupId != NatConstants.INVALID_ID) {
                externalRouterListner.installGroup(srcDpnId, routerName, groupId, bucketInfoForNonNaptSwitches);
                LOG.debug("hndlTepAddOnNonNaptSwitch : SNAT -> in the SNAT miss entry pointing to group {} "
                        + "in the non NAPT switch {}", groupId, srcDpnId);
                FlowEntity flowEntity =
                    externalRouterListner.buildSnatFlowEntityWithUpdatedVpnId(srcDpnId, routerName, groupId, vpnId);
                mdsalManager.addFlow(confTx, flowEntity);
            } else {
                LOG.error("hndlTepAddOnNonNaptSwitch: Unable to obtain group ID for Key: {}", routerName);
            }
        }
        return true;
    }

    private boolean hndlTepAddOnNaptSwitch(Uint64 srcDpnId, String tunnelType, String srcTepIp,
                                           String destTepIp, String tunnelName, Uint32 routerId,
                                           Optional<Routers> routerData, String nextHopIp, Uuid vpnName,
                                           ProviderTypes extNwProvType, TypedWriteTransaction<Configuration> confTx) {
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
        Uint32 vpnId;
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
            if (vpnId == NatConstants.INVALID_ID) {
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
            fibManager.removeFibEntry(rd, externalIp, null, null, null);
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
        Uint32 l3Vni = Uint32.ZERO;
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
                l3Vni = natOverVxlanUtil.getInternetVpnVni(externalVpnName, routerId);
            }
        }

        for (final String externalIp : externalIps) {
            Uint32 serviceId = null;
            String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
            if (extNwProvType == ProviderTypes.VXLAN) {
                LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Advertise the route to the externalIp {} "
                        + "having nextHopIp {}", externalIp, nextHopIp);
                NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, externalVpnName, rd,
                        externalIp, nextHopIp, l3Vni, tunnelName, gwMacAddress, confTx, RouteOrigin.STATIC,
                        srcDpnId, networkId);
                serviceId = l3Vni;
            } else {

                serviceId = externalRouterListner.checkExternalIpLabel(routerId,
                        externalIp);
                if (serviceId == null || serviceId == NatConstants.INVALID_ID) {
                    LOG.error("hndlTepAddOnNaptSwitch : SNAT->Unable to advertise to the DC GW "
                            + "since label is invalid");
                    return false;
                }
                LOG.debug("hndlTepAddOnNaptSwitch : SNAT -> Advertise the route to the externalIp {} "
                        + "having nextHopIp {}", externalIp, nextHopIp);
                Uint32 l3vni = Uint32.ZERO;
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    l3vni = natOverVxlanUtil.getInternetVpnVni(externalVpnName, l3vni);
                }
                NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, externalVpnName, rd,
                    fibExternalIp, nextHopIp, networkId.getValue(), null /* mac-address */, serviceId, l3vni,
                        RouteOrigin.STATIC, srcDpnId);
            }

            LOG.debug("hndlTepAddOnNaptSwitch: SNAT -> Install custom FIB routes "
                    + "(Table 21 -> Push MPLS label to Tunnel port");
            List<Instruction> customInstructions = new ArrayList<>();
            int customInstructionIndex = 0;
            Uint32 externalSubnetVpnId = NatUtil.getExternalSubnetVpnIdForRouterExternalIp(dataBroker, externalIp,
                    router);
            if (externalSubnetVpnId != NatConstants.INVALID_ID) {
                LOG.debug("hndlTepAddOnNaptSwitch : Will install custom FIB router with external subnet VPN ID {}",
                        externalSubnetVpnId);
                Uint64 subnetIdMetaData = MetaDataUtil.getVpnIdMetadata(externalSubnetVpnId.longValue());
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
                public void onFailure(@NonNull Throwable error) {
                    LOG.error("hndlTepAddOnNaptSwitch : SNAT->Error in generate label or fib install process",
                            error);
                }

                @Override
                public void onSuccess(@NonNull RpcResult<CreateFibEntryOutput> result) {
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

    private void hndlTepAddForDnatInEachRtr(RoutersList router, Uint32 routerId, String nextHopIp,
            Uint64 tepAddedDpnId, ProviderTypes extNwProvType, TypedWriteTransaction<Configuration> confTx) {
        //DNAT : Advertise the new route to the floating IP having the new TEP IP as the next hop IP
        final String routerName = router.getRouter();

        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts;
        try {
            optRouterPorts = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, routerPortsId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("hndlTepAddForDnatInEachRtr: Exception while reading RouterPorts DS for the router {}",
                    routerName, e);
            return;
        }
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
        Uint32 l3Vni = Uint32.ZERO;
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
                l3Vni = natOverVxlanUtil.getInternetVpnVni(vpnName, routerId);
            }
        }
        for (Ports port : routerPorts.nonnullPorts().values()) {
            //Get the DPN on which this interface resides
            final String interfaceName = port.getPortName();
            final Uint64 fipCfgdDpnId = NatUtil.getDpnForInterface(interfaceService, interfaceName);
            if (fipCfgdDpnId.equals(Uint64.ZERO)) {
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
            for (InternalToExternalPortMap intExtPortMap : port.nonnullInternalToExternalPortMap().values()) {
                final String internalIp = intExtPortMap.getInternalIp();
                final String externalIp = intExtPortMap.getExternalIp();
                LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Advertising the FIB route to the floating IP {} "
                        + "configured for the port: {}", externalIp, interfaceName);
                Uint32 serviceId = null;
                String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);

                if (extNwProvType == ProviderTypes.VXLAN) {
                    LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Advertise the route to the externalIp {} "
                            + "having nextHopIp {}", externalIp, nextHopIp);
                    NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd,
                        externalIp, nextHopIp, l3Vni, interfaceName, gwMacAddress, confTx, RouteOrigin.STATIC,
                        fipCfgdDpnId, extNwId);
                    serviceId = l3Vni;
                } else {
                    serviceId = floatingIPListener.getOperationalIpMapping(routerName, interfaceName, internalIp);
                    if (serviceId == null || serviceId == NatConstants.INVALID_ID) {
                        LOG.error("hndlTepAddForDnatInEachRtr : DNAT -> Unable to advertise to the DC GW since label "
                                + "is invalid");
                        return;
                    }
                    LOG.debug("hndlTepAddForDnatInEachRtr : DNAT -> Advertise the route to the externalIp {} "
                            + "having nextHopIp {}", externalIp, nextHopIp);
                    Uint32 l3vni = Uint32.ZERO;
                    if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                        l3vni = natOverVxlanUtil.getInternetVpnVni(vpnName, l3vni);
                    }
                    NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd,
                        fibExternalIp, nextHopIp, null, null, serviceId, l3vni,
                            RouteOrigin.STATIC, fipCfgdDpnId);
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
                    public void onFailure(@NonNull Throwable error) {
                        LOG.error("hndlTepAddForDnatInEachRtr : DNAT -> Error in generate label or fib install process",
                                error);
                    }

                    @Override
                    public void onSuccess(@NonNull RpcResult<CreateFibEntryOutput> result) {
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
