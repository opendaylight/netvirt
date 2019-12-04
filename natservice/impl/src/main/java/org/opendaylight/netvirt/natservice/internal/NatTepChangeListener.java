/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
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
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatTepChangeListener extends
    AsyncDataTreeChangeListenerBase<TunnelEndPoints, NatTepChangeListener> {

    private static final Logger LOG = LoggerFactory
        .getLogger(NatTepChangeListener.class);
    private final DataBroker dataBroker;
    private SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private OdlInterfaceRpcService interfaceService;
    private IdManagerService idManager;
    private IFibManager fibManager;
    private IBgpManager bgpManager;
    private IMdsalApiManager mdsalManager;
    private FloatingIPListener floatingIPListener;
    private FibRpcService fibRpcService;
    private NaptSwitchHA naptSwitchHA;
    private final NatMode natMode;
    private final SnatServiceManager natServiceManager;
    private final JobCoordinator coordinator;
    private final ManagedNewTransactionRunner txRunner;
    private final NatOverVxlanUtil natOverVxlanUtil;

    @Inject
    public NatTepChangeListener(final DataBroker dataBroker,
        final SNATDefaultRouteProgrammer defaultRouteProgrammer,
        final OdlInterfaceRpcService interfaceService,
        final IdManagerService idManager, final IFibManager fibManager,
        final IBgpManager bgpManager,
        final FloatingIPListener floatingIPListener,
        final FibRpcService fibRpcService,
        final IMdsalApiManager mdsalManager,
        final NaptSwitchHA naptSwitchHA,
        final NatserviceConfig config,
        final SnatServiceManager natServiceManager,
        final JobCoordinator coordinator,
        final NatOverVxlanUtil natOverVxlanUtil) {
        super(TunnelEndPoints.class, NatTepChangeListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.defaultRouteProgrammer = defaultRouteProgrammer;
        this.interfaceService = interfaceService;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.bgpManager = bgpManager;
        this.floatingIPListener = floatingIPListener;
        this.fibRpcService = fibRpcService;
        this.naptSwitchHA = naptSwitchHA;
        this.mdsalManager = mdsalManager;
        this.coordinator = coordinator;
        this.natServiceManager = natServiceManager;
        this.natOverVxlanUtil = natOverVxlanUtil;
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
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<TunnelEndPoints> getWildCardPath() {
        return InstanceIdentifier.builder(DpnEndpoints.class)
            .child(DPNTEPsInfo.class).child(TunnelEndPoints.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<TunnelEndPoints> key,
        TunnelEndPoints tep) {
        /*
         * Whenever the TEP on a given DPNID is removed, this API take care
         * of withdrawing the FIB entries for those Floating-IP existing on this
         * DPN and perform re-election of NAPT Switch for a VRF to which the current
         * DPN is elected as NAPT Switch.
         */
        Uint64 srcDpnId = key.firstIdentifierOf(DPNTEPsInfo.class)
            .firstKeyOf(DPNTEPsInfo.class).getDPNID();
        final String srcTepIp = tep.getIpAddress().stringValue();
        String tunnelType = tep.getTunnelType().getName();
        LOG.debug(
            "NAT Service : Remove Event triggered for Tep on DPN:{} having IP:{} and tunnelType:{}",
            srcDpnId, srcTepIp, tunnelType);
        handleTepDelForAllRtrs(srcDpnId, srcTepIp);
    }

    @Override
    protected void update(InstanceIdentifier<TunnelEndPoints> key,
        TunnelEndPoints origTep, TunnelEndPoints updatedTep) {
        // Will be handled in NatTunnelInterfaceStateListener.add()
        LOG.debug("NO ACTION duing update event : {}", updatedTep.key());
    }

    @Override
    protected void add(InstanceIdentifier<TunnelEndPoints> key,
        TunnelEndPoints tep) {
        LOG.debug("NO ACTION duing add event : {}", tep.key());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void handleTepDelForAllRtrs(Uint64 srcDpnId, String srcTepIp) {
        LOG.trace("handleTepDelForAllRtrs : TEP DEL ----- on DPN-ID {} having SRC IP : {}",
            srcDpnId,
            srcTepIp);

        List<RoutersList> routersList = null;
        InstanceIdentifier<DpnRoutersList> dpnRoutersListId = NatUtil.getDpnRoutersId(srcDpnId);
        Optional<DpnRoutersList> optionalRouterDpnList =
            SingleTransactionDataBroker
                .syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, dpnRoutersListId);
        if (optionalRouterDpnList.isPresent()) {
            routersList = optionalRouterDpnList.get().getRoutersList();
        } else {
            LOG.debug(
                "NAT Service : RouterDpnList is empty for DPN {}. Hence ignoring TEP DEL event",
                srcDpnId);
            return;
        }

        if (routersList == null) {
            LOG.error("handleTepDelForAllRtrs : DPN {} does not have the Routers presence",
                srcDpnId);
            return;
        }

        for (RoutersList router : routersList) {
            String routerName = router.getRouter();
            LOG.debug(
                "handleTepDelForAllRtrs :  TEP DEL : DNAT -> Withdrawing routes for router {} ",
                routerName);
            Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
            if (routerId == NatConstants.INVALID_ID) {
                LOG.error("handleTepDelForAllRtrs :Invalid ROUTER-ID {} returned for routerName {}",
                    routerId, routerName);
                return;
            }
            Uuid externalNetworkId = NatUtil.getNetworkIdFromRouterName(dataBroker, routerName);
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker,
                routerName, externalNetworkId);
            if (extNwProvType == null) {
                return;
            }
            boolean isFipExists = hndlTepDelForDnatInEachRtr(router, routerId, srcDpnId,
                extNwProvType);
            LOG.debug(
                "handleTepDelForAllRtrs :  TEP DEL : SNAT -> Withdrawing and Advertising routes for router {} ",
                router.getRouter());
            coordinator.enqueueJob((NatConstants.NAT_DJC_PREFIX + router.getRouter()), () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, configTx -> {
                    hndlTepDelForSnatInEachRtr(router, routerId, srcDpnId, srcTepIp, isFipExists,
                        extNwProvType, configTx);
                });

                return futures;
            }, NatConstants.NAT_DJC_MAX_RETRIES);
        }
        return;
    }

    private boolean hndlTepDelForDnatInEachRtr(RoutersList router, Uint32 routerId,
        Uint64 tepDeletedDpnId,
        ProviderTypes extNwProvType) {
        //DNAT : Withdraw the routes from the BGP
        String routerName = router.getRouter();
        Boolean isFipExists = Boolean.FALSE;

        LOG.debug("hndlTepDelForDnatInEachRtr : DNAT -> Trying to clear routes to the Floating IP "
            + "associated to the router {}", routerName);

        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType
            .CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.debug(
                "hndlTepDelForDnatInEachRtr : DNAT -> Could not read Router Ports data object with id: {} "
                    + "from DNAT FloatingIpInfo", routerName);
            return isFipExists;
        }
        RouterPorts routerPorts = optRouterPorts.get();
        Uuid extNwId = routerPorts.getExternalNetworkId();
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, extNwId);
        if (vpnName == null) {
            LOG.error(
                "hndlTepDelForDnatInEachRtr : DNAT -> No External VPN associated with Ext N/W {} for Router {}",
                extNwId, routerName);
            return isFipExists;
        }
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (extNwProvType == null) {
            return isFipExists;
        }
        Uint32 l3Vni = Uint32.ZERO;
        if (extNwProvType == ProviderTypes.VXLAN) {
            //get l3Vni value for external VPN
            l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
            if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
                LOG.debug(
                    "hndlTepDelForDnatInEachRtr : L3VNI value is not configured in Internet VPN {} and RD {} "
                        + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue to installing "
                        + "NAT flows", vpnName, rd);
                l3Vni = natOverVxlanUtil.getInternetVpnVni(vpnName, routerId);
            }
        }
        List<Ports> interfaces = routerPorts.getPorts();
        for (Ports port : interfaces) {
            //Get the DPN on which this interface resides
            String interfaceName = port.getPortName();
            Uint64 fipCfgdDpnId = NatUtil.getDpnForInterface(interfaceService, interfaceName);
            if (fipCfgdDpnId.equals(Uint64.ZERO)) {
                LOG.info(
                    "hndlTepDelForDnatInEachRtr : DNAT -> Abort processing Floating ip configuration. "
                        + "No DPN for port : {}", interfaceName);
                continue;
            }
            if (!fipCfgdDpnId.equals(tepDeletedDpnId)) {
                LOG.info(
                    "hndlTepDelForDnatInEachRtr : DNAT -> TEP deleted DPN {} is not the DPN {} which has the "
                        + "floating IP configured for the port: {}",
                    tepDeletedDpnId, fipCfgdDpnId, interfaceName);
                continue;
            }
            isFipExists = Boolean.TRUE;
            List<InternalToExternalPortMap> intExtPortMapList = port.getInternalToExternalPortMap();
            for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
                String internalIp = intExtPortMap.getInternalIp();
                String externalIp = intExtPortMap.getExternalIp();
                externalIp = NatUtil.validateAndAddNetworkMask(externalIp);
                LOG.debug(
                    "hndlTepDelForDnatInEachRtr : DNAT -> Withdrawing the FIB route to the floating IP {} "
                        + "configured for the port: {}",
                    externalIp, interfaceName);
                NatUtil.removePrefixFromBGP(bgpManager, fibManager, rd, externalIp, vpnName);
                Uint32 serviceId = null;
                if (extNwProvType == ProviderTypes.VXLAN) {
                    serviceId = l3Vni;
                } else {
                    serviceId = floatingIPListener
                        .getOperationalIpMapping(routerName, interfaceName, internalIp);
                    if (serviceId == null || serviceId == NatConstants.INVALID_ID) {
                        LOG.error(
                            "hndlTepDelForDnatInEachRtr : DNAT -> Unable to remove the table 21 entry pushing the"
                                + " MPLS label to the tunnel since label is invalid");
                        continue;
                    }
                }

                RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                    .setSourceDpid(fipCfgdDpnId).setIpAddress(externalIp).setServiceId(serviceId)
                    .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.FloatingIP).build();
                ListenableFuture<RpcResult<RemoveFibEntryOutput>> future = fibRpcService
                    .removeFibEntry(input);

                Futures.addCallback(future, new FutureCallback<RpcResult<RemoveFibEntryOutput>>() {

                    @Override
                    public void onFailure(Throwable error) {
                        LOG.error(
                            "hndlTepDelForDnatInEachRtr : DNAT -> Error in removing the table 21 entry pushing "
                                + "the MPLS label to the tunnel since label is invalid ", error);
                    }

                    @Override
                    public void onSuccess(RpcResult<RemoveFibEntryOutput> result) {
                        if (result.isSuccessful()) {
                            LOG.info(
                                "hndlTepDelForDnatInEachRtr : DNAT -> Successfully removed the entry pushing the "
                                    + "MPLS label to the tunnel");
                        } else {
                            LOG.error(
                                "hndlTepDelForDnatInEachRtr : DNAT -> Error in fib rpc call to remove the table "
                                    + "21 entry pushing the MPLS label to the tunnnel due to {}",
                                result.getErrors());
                        }
                    }
                }, MoreExecutors.directExecutor());
            }
        }
        return isFipExists;
    }

    private void hndlTepDelForSnatInEachRtr(RoutersList router, Uint32 routerId, Uint64 dpnId,
        String srcTepIp, Boolean isFipExists, ProviderTypes extNwProvType,
        TypedReadWriteTransaction<Configuration> confTx)
        throws ExecutionException, InterruptedException {

        /*SNAT :
        1) Elect a new switch as the primary NAPT
        2) Advertise the new routes to BGP for the newly elected TEP IP as the DPN IP
        3) This will make sure old routes are withdrawn and new routes are advertised.
         */

        String routerName = router.getRouter();
        LOG.debug(
            "hndlTepDelForSnatInEachRtr : SNAT -> Trying to clear routes to the External fixed IP associated "
                + "to the router {}", routerName);

        // Check if this is externalRouter else ignore
        InstanceIdentifier<Routers> extRoutersId = NatUtil.buildRouterIdentifier(routerName);
        Optional<Routers> routerData = Optional.absent();
        try {
            routerData = confTx.read(extRoutersId).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error retrieving routers {}", extRoutersId, e);
        }
        if (!routerData.isPresent()) {
            LOG.debug(
                "hndlTepDelForSnatInEachRtr : SNAT->Ignoring TEP del for router {} since its not External Router",
                routerName);
            return;
        }

        //Check if the DPN having the router is the NAPT switch
        Uint64 naptId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (naptId == null || naptId.equals(Uint64.ZERO) || !naptId.equals(dpnId)) {
            LOG.error(
                "hndlTepDelForSnatInEachRtr : SNAT -> Ignoring TEP delete for the DPN {} since"
                    + "srcTepIp : {} is NOT a NAPT switch", dpnId, srcTepIp);
            return;
        }
        if (natMode == NatMode.Conntrack) {
            Routers extRouter = routerData.get();
            natServiceManager.notify(confTx, extRouter, null, naptId, dpnId,
                SnatServiceManager.Action.CNT_ROUTER_DISBL);
            if (extRouter.isEnableSnat()) {
                natServiceManager.notify(confTx, extRouter, null, naptId, dpnId,
                    SnatServiceManager.Action.SNAT_ROUTER_DISBL);
            }
        } else {

            Uuid networkId = routerData.get().getNetworkId();
            if (networkId == null) {
                LOG.error(
                    "hndlTepDelForSnatInEachRtr : SNAT -> Ignoring TEP delete for the DPN {} for router {}"
                        + "as external network configuraton is missing", dpnId, routerName);
                return;
            }

            LOG.debug("hndlTepDelForSnatInEachRtr : SNAT->Router {} is associated with ext nw {}",
                routerId, networkId);
            Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
            Uint32 bgpVpnId;
            if (bgpVpnUuid == null) {
                LOG.debug(
                    "hndlTepDelForSnatInEachRtr : SNAT->Internal VPN-ID {} associated to router {}",
                    routerId, routerName);
                bgpVpnId = routerId;
            } else {
                bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                if (bgpVpnId == NatConstants.INVALID_ID) {
                    LOG.error(
                        "hndlTepDelForSnatInEachRtr :SNAT->Invalid Private BGP VPN ID returned for routerName {}",
                        routerName);
                    return;
                }
            }
            if (!isFipExists) {
                // Remove default entry in FIB to SNAT table
                LOG.debug(
                    "NAT Service : Installing default route in FIB on DPN {} for router {} with"
                        + " vpn {}...",
                    dpnId, routerName, bgpVpnId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, bgpVpnId, routerId, confTx);
            }

            if (routerData.get().isEnableSnat()) {
                LOG.info("hndlTepDelForSnatInEachRtr : SNAT enabled for router {}", routerId);

                Uint32 routerVpnId = routerId;
                if (bgpVpnId != NatConstants.INVALID_ID) {
                    LOG.debug(
                        "hndlTepDelForSnatInEachRtr : SNAT -> Private BGP VPN ID (Internal BGP VPN ID) {} "
                            + "associated to the router {}", bgpVpnId, routerName);
                    routerVpnId = bgpVpnId;
                } else {
                    LOG.debug(
                        "hndlTepDelForSnatInEachRtr : SNAT -> Internal L3 VPN ID (Router ID) {} "
                            + "associated to the router {}", routerVpnId, routerName);
                }
                //Re-elect the other available switch as the NAPT switch and program the NAT flows.
                NatUtil
                    .removeSNATFromDPN(dataBroker, mdsalManager, idManager, naptSwitchHA, dpnId,
                        routerName, routerId, routerVpnId, networkId, extNwProvType, confTx);
            } else {
                LOG.info(
                    "hndlTepDelForSnatInEachRtr : SNAT is not enabled for router {} to handle addDPN event {}",
                    routerId, dpnId);
            }
        }
    }

    @Override
    protected NatTepChangeListener getDataTreeChangeListener() {
        return this;
    }
}
