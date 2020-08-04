/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NwConstants.NxmOfFieldType;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.MatchFromField;
import org.opendaylight.genius.mdsalutil.actions.ActionLearn.MatchFromValue;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPopMpls;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchMplsLabel;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
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
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalIpsCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProtocolTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.RouterIdName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.ExternalCounters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.ips.counter.external.counters.ExternalIpCounter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.IpPortMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.IntextIpProtocolType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.IpPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.router.id.name.RouterIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class ExternalRoutersListener extends AbstractAsyncDataTreeChangeListener<Routers> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalRoutersListener.class);

    private static final Uint64 COOKIE_TUNNEL = Uint64.valueOf("9000000", 16).intern();
    private static final Uint64 COOKIE_VM_LFIB_TABLE = Uint64.valueOf("8000022", 16).intern();

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final ItmRpcService itmManager;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final IdManagerService idManager;
    private final NaptManager naptManager;
    private final NAPTSwitchSelector naptSwitchSelector;
    private final IBgpManager bgpManager;
    private final VpnRpcService vpnService;
    private final FibRpcService fibService;
    private final SNATDefaultRouteProgrammer defaultRouteProgrammer;
    private final NaptEventHandler naptEventHandler;
    private final NaptPacketInHandler naptPacketInHandler;
    private final IFibManager fibManager;
    private final IVpnManager vpnManager;
    private final EvpnSnatFlowProgrammer evpnSnatFlowProgrammer;
    private final NatMode natMode;
    private final IElanService elanManager;
    private final JobCoordinator coordinator;
    private final IInterfaceManager interfaceManager;
    private final NatOverVxlanUtil natOverVxlanUtil;
    private final int snatPuntTimeout;

    @Inject
    public ExternalRoutersListener(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                   final ItmRpcService itmManager,
                                   final OdlInterfaceRpcService odlInterfaceRpcService,
                                   final IdManagerService idManager,
                                   final NaptManager naptManager,
                                   final NAPTSwitchSelector naptSwitchSelector,
                                   final IBgpManager bgpManager,
                                   final VpnRpcService vpnService,
                                   final FibRpcService fibService,
                                   final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer,
                                   final NaptEventHandler naptEventHandler,
                                   final NaptPacketInHandler naptPacketInHandler,
                                   final IFibManager fibManager,
                                   final IVpnManager vpnManager,
                                   final EvpnSnatFlowProgrammer evpnSnatFlowProgrammer,
                                   final NatserviceConfig config,
                                   final IElanService elanManager,
                                   final JobCoordinator coordinator,
                                   final NatOverVxlanUtil natOverVxlanUtil,
                                   final IInterfaceManager interfaceManager) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(ExtRouters.class)
                .child(Routers.class),
                Executors.newListeningSingleThreadExecutor("ExternalRoutersListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.idManager = idManager;
        this.naptManager = naptManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.bgpManager = bgpManager;
        this.vpnService = vpnService;
        this.fibService = fibService;
        this.defaultRouteProgrammer = snatDefaultRouteProgrammer;
        this.naptEventHandler = naptEventHandler;
        this.naptPacketInHandler = naptPacketInHandler;
        this.fibManager = fibManager;
        this.vpnManager = vpnManager;
        this.evpnSnatFlowProgrammer = evpnSnatFlowProgrammer;
        this.elanManager = elanManager;
        this.coordinator = coordinator;
        this.interfaceManager = interfaceManager;
        this.natOverVxlanUtil = natOverVxlanUtil;
        if (config != null) {
            this.natMode = config.getNatMode();
            this.snatPuntTimeout = config.getSnatPuntTimeout().intValue();
        } else {
            this.natMode = NatMode.Controller;
            this.snatPuntTimeout = 0;
        }
        init();
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        // This class handles ExternalRouters for Controller SNAT mode.
        // For Conntrack SNAT mode, its handled in SnatExternalRoutersListener.java
        if (natMode == NatMode.Controller) {
            NatUtil.createGroupIdPool(idManager);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void add(InstanceIdentifier<Routers> identifier, Routers routers) {
        if (natMode != NatMode.Controller) {
            return;
        }
        // Populate the router-id-name container
        String routerName = routers.getRouterName();
        LOG.info("add : external router event for {}", routerName);
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        NatUtil.createRouterIdsConfigDS(dataBroker, routerId, routerName);
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        try {
            if (routers.isEnableSnat()) {
                coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routers.key(),
                    () -> Collections.singletonList(
                        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
                            LOG.info("add : Installing NAT default route on all dpns part of router {}", routerName);
                            Uint32 bgpVpnId = NatConstants.INVALID_ID;
                            if (bgpVpnUuid != null) {
                                bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                            }
                            addOrDelDefFibRouteToSNAT(routerName, routerId, bgpVpnId, bgpVpnUuid, true, confTx);
                            // Allocate Primary Napt Switch for this router
                            Uint64 primarySwitchId = getPrimaryNaptSwitch(routerName);
                            if (primarySwitchId != null && !primarySwitchId.equals(Uint64.ZERO)) {
                                handleEnableSnat(routers, routerId, primarySwitchId, bgpVpnId, confTx);
                            }
                        }
                    )), NatConstants.NAT_DJC_MAX_RETRIES);
            } else {
                LOG.info("add : SNAT is disabled for external router {} ", routerName);
            }
        } catch (Exception ex) {
            LOG.error("add : Exception while Installing NAT flows on all dpns as part of router {}",
                    routerName, ex);
        }
    }

    public void handleEnableSnat(Routers routers, Uint32 routerId, Uint64 primarySwitchId, Uint32 bgpVpnId,
                                 TypedWriteTransaction<Configuration> confTx) {
        String routerName = routers.getRouterName();
        LOG.info("handleEnableSnat : Handling SNAT for router {}", routerName);

        naptManager.initialiseExternalCounter(routers, routerId);
        subnetRegisterMapping(routers, routerId);

        LOG.debug("handleEnableSnat:About to create and install outbound miss entry in Primary Switch {} for router {}",
            primarySwitchId, routerName);

        ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName,
                routers.getNetworkId());
        if (extNwProvType == null) {
            LOG.error("handleEnableSnat : External Network Provider Type missing");
            return;
        }

        if (bgpVpnId != NatConstants.INVALID_ID) {
            installFlowsWithUpdatedVpnId(primarySwitchId, routerName, bgpVpnId, routerId,
                routers.getNetworkId(),false, confTx, extNwProvType);
        } else {
            // write metadata and punt
            installOutboundMissEntry(routerName, routerId, primarySwitchId, confTx);
            handlePrimaryNaptSwitch(primarySwitchId, routerName, routerId, routers.getNetworkId(), confTx);
            // Now install entries in SNAT tables to point to Primary for each router
            List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
            for (Uint64 dpnId : switches) {
                // Handle switches and NAPT switches separately
                if (!dpnId.equals(primarySwitchId)) {
                    LOG.debug("handleEnableSnat : Handle Ordinary switch");
                    handleSwitches(dpnId, routerName, routerId, primarySwitchId);
                }
            }
        }

        Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
        if (externalIps.isEmpty()) {
            LOG.error("handleEnableSnat : Internal External mapping not found for router {}", routerName);
            return;
        } else {
            for (String externalIpAddrPrefix : externalIps) {
                LOG.debug("handleEnableSnat : Calling handleSnatReverseTraffic for primarySwitchId {}, "
                    + "routerName {} and externalIpAddPrefix {}", primarySwitchId, routerName, externalIpAddrPrefix);
                externalIpAddrPrefix = NatUtil.validateAndAddNetworkMask(externalIpAddrPrefix);
                handleSnatReverseTraffic(confTx, primarySwitchId, routers, routerId, routerName, externalIpAddrPrefix
                );
            }
        }
        LOG.debug("handleEnableSnat : Exit");
    }

    private Uint64 getPrimaryNaptSwitch(String routerName) {
        // Allocate Primary Napt Switch for this router
        Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (primarySwitchId != null && !primarySwitchId.equals(Uint64.ZERO)) {
            LOG.debug("getPrimaryNaptSwitch : Primary NAPT switch with DPN ID {} is already elected for router {}",
                primarySwitchId, routerName);
            return primarySwitchId;
        }
        return selectNewNAPTSwitch(routerName);
    }

    private Uint64 selectNewNAPTSwitch(String routerName) {
        // Allocated an id from VNI pool for the Router.
        natOverVxlanUtil.getRouterVni(routerName, NatConstants.INVALID_ID);
        Uint64 primarySwitchId = naptSwitchSelector.selectNewNAPTSwitch(routerName, null);
        LOG.debug("getPrimaryNaptSwitch : Primary NAPT switch DPN ID {}", primarySwitchId);

        return primarySwitchId;
    }

    protected void installNaptPfibExternalOutputFlow(String routerName, Uint32 routerId, Uint64 dpnId,
                                                     TypedWriteTransaction<Configuration> confTx) {
        Uint32 extVpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
        if (extVpnId == NatConstants.INVALID_ID) {
            LOG.error("installNaptPfibExternalOutputFlow - not found extVpnId for router {}", routerId);
            extVpnId = routerId;
        }
        List<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerName);
        if (externalIps.isEmpty()) {
            LOG.error("installNaptPfibExternalOutputFlow - empty external Ips list for dpnId {} extVpnId {}",
                dpnId, extVpnId);
            return;
        }
        for (String ip : externalIps) {
            Uuid subnetId = getSubnetIdForFixedIp(ip);
            if (subnetId != null) {
                Uint32 subnetVpnId = NatUtil.getExternalSubnetVpnId(dataBroker, subnetId);
                if (subnetVpnId != NatConstants.INVALID_ID) {
                    extVpnId = subnetVpnId;
                }
                LOG.debug("installNaptPfibExternalOutputFlow - dpnId {} extVpnId {} subnetId {}",
                    dpnId, extVpnId, subnetId);
                FlowEntity postNaptFlowEntity = buildNaptPfibFlowEntity(dpnId, extVpnId);
                if (postNaptFlowEntity != null) {
                    mdsalManager.addFlow(confTx, postNaptFlowEntity);
                }
            }
        }
    }

    @Nullable
    private Uuid getSubnetIdForFixedIp(String ip) {
        if (ip != null) {
            IpAddress externalIpv4Address = new IpAddress(new Ipv4Address(ip));
            Port port = NatUtil.getNeutronPortForRouterGetewayIp(dataBroker, externalIpv4Address);
            return NatUtil.getSubnetIdForFloatingIp(port, externalIpv4Address);
        }
        LOG.error("getSubnetIdForFixedIp : ip is null");
        return null;
    }

    protected void subnetRegisterMapping(Routers routerEntry, Uint32 segmentId) {
        LOG.debug("subnetRegisterMapping : Fetching values from extRouters model");
        List<String> externalIps = NatUtil.getIpsListFromExternalIps(
                new ArrayList<ExternalIps>(routerEntry.nonnullExternalIps().values()));
        int counter = 0;
        int extIpCounter = externalIps.size();
        LOG.debug("subnetRegisterMapping : counter values before looping counter {} and extIpCounter {}",
                counter, extIpCounter);
        @Nullable List<Uuid> subnetIds = routerEntry.getSubnetIds();
        if (subnetIds == null) {
            return;
        }
        for (Uuid subnet : subnetIds) {
            LOG.debug("subnetRegisterMapping : Looping internal subnets for subnet {}", subnet);
            InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier
                .builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnet))
                .build();
            Optional<Subnetmap> sn;
            try {
                sn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.CONFIGURATION, subnetmapId);
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Failed to read SubnetMap for  subnetmap Id {}", subnetmapId, e);
                sn = Optional.empty();
            }
            if (sn.isPresent()) {
                // subnets
                Subnetmap subnetmapEntry = sn.get();
                String subnetString = subnetmapEntry.getSubnetIp();
                String[] subnetSplit = subnetString.split("/");
                String subnetIp = subnetSplit[0];
                try {
                    InetAddress address = InetAddress.getByName(subnetIp);
                    if (address instanceof Inet6Address) {
                        LOG.debug("subnetRegisterMapping : Skipping ipv6 subnet {} for the router {} with ipv6 address "
                                + "{} ", subnet, routerEntry.getRouterName(), address);
                        continue;
                    }
                } catch (UnknownHostException e) {
                    LOG.error("subnetRegisterMapping : Invalid ip address {}", subnetIp, e);
                    return;
                }
                String subnetPrefix = "0";
                if (subnetSplit.length == 2) {
                    subnetPrefix = subnetSplit[1];
                }
                IPAddress subnetAddr = new IPAddress(subnetIp, Integer.parseInt(subnetPrefix));
                LOG.debug("subnetRegisterMapping : subnetAddr is {} and subnetPrefix is {}",
                    subnetAddr.getIpAddress(), subnetAddr.getPrefixLength());
                //externalIps
                LOG.debug("subnetRegisterMapping : counter values counter {} and extIpCounter {}",
                        counter, extIpCounter);
                if (extIpCounter != 0) {
                    if (counter < extIpCounter) {
                        String[] ipSplit = externalIps.get(counter).split("/");
                        String externalIp = ipSplit[0];
                        String extPrefix = Short.toString(NatConstants.DEFAULT_PREFIX);
                        if (ipSplit.length == 2) {
                            extPrefix = ipSplit[1];
                        }
                        IPAddress externalIpAddr = new IPAddress(externalIp, Integer.parseInt(extPrefix));
                        LOG.debug("subnetRegisterMapping : externalIp is {} and extPrefix  is {}",
                            externalIpAddr.getIpAddress(), externalIpAddr.getPrefixLength());
                        naptManager.registerMapping(segmentId, subnetAddr, externalIpAddr);
                        LOG.debug("subnetRegisterMapping : Called registerMapping for subnetIp {}, prefix {}, "
                                + "externalIp {}. prefix {}", subnetIp, subnetPrefix, externalIp, extPrefix);
                    } else {
                        counter = 0;    //Reset the counter which runs on externalIps for round-robbin effect
                        LOG.debug("subnetRegisterMapping : Counter on externalIps got reset");
                        String[] ipSplit = externalIps.get(counter).split("/");
                        String externalIp = ipSplit[0];
                        String extPrefix = Short.toString(NatConstants.DEFAULT_PREFIX);
                        if (ipSplit.length == 2) {
                            extPrefix = ipSplit[1];
                        }
                        IPAddress externalIpAddr = new IPAddress(externalIp, Integer.parseInt(extPrefix));
                        LOG.debug("subnetRegisterMapping : externalIp is {} and extPrefix  is {}",
                            externalIpAddr.getIpAddress(), externalIpAddr.getPrefixLength());
                        naptManager.registerMapping(segmentId, subnetAddr, externalIpAddr);
                        LOG.debug("subnetRegisterMapping : Called registerMapping for subnetIp {}, prefix {}, "
                                + "externalIp {}. prefix {}", subnetIp, subnetPrefix,
                            externalIp, extPrefix);
                    }
                }
                counter++;
                LOG.debug("subnetRegisterMapping : Counter on externalIps incremented to {}", counter);
            } else {
                LOG.warn("subnetRegisterMapping : No internal subnets present in extRouters Model");
            }
        }
    }

    private void addOrDelDefFibRouteToSNAT(String routerName, Uint32 routerId, Uint32 bgpVpnId,
            Uuid bgpVpnUuid, boolean create, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        //Check if BGP VPN exists. If exists then invoke the new method.
        if (bgpVpnId != NatConstants.INVALID_ID) {
            if (bgpVpnUuid != null) {
                String bgpVpnName = bgpVpnUuid.getValue();
                LOG.debug("Populate the router-id-name container with the mapping BGP VPN-ID {} -> BGP VPN-NAME {}",
                    bgpVpnId, bgpVpnName);
                RouterIds rtrs = new RouterIdsBuilder().withKey(new RouterIdsKey(bgpVpnId))
                    .setRouterId(bgpVpnId).setRouterName(bgpVpnName).build();
                confTx.mergeParentStructurePut(getRoutersIdentifier(bgpVpnId), rtrs);
            }
            if (create) {
                addDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, bgpVpnId, confTx);
            } else {
                removeDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, bgpVpnId, confTx);
            }
            return;
        }

        //Router ID is used as the internal VPN's name, hence the vrf-id in VpnInstance Op DataStore
        addOrDelDefaultFibRouteForSNAT(routerName, routerId, create, confTx);
    }

    private void addOrDelDefaultFibRouteForSNAT(String routerName, Uint32 routerId, boolean create,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        if (switches.isEmpty()) {
            LOG.info("addOrDelDefaultFibRouteForSNAT : No switches found for router {}", routerName);
            return;
        }
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("addOrDelDefaultFibRouteForSNAT : Could not retrieve router Id for {} to program "
                    + "default NAT route in FIB", routerName);
            return;
        }
        for (Uint64 dpnId : switches) {
            if (create) {
                LOG.debug("addOrDelDefaultFibRouteForSNAT : installing default NAT route for router {} in dpn {} "
                        + "for the internal vpn-id {}", routerId, dpnId, routerId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, routerId, confTx);
            } else {
                LOG.debug("addOrDelDefaultFibRouteForSNAT : removing default NAT route for router {} in dpn {} "
                        + "for the internal vpn-id {}", routerId, dpnId, routerId);
                defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, routerId, confTx);
            }
        }
    }

    private void addDefaultFibRouteForSnatWithBgpVpn(String routerName, Uint32 routerId,
                                                     Uint32 bgpVpnId, TypedWriteTransaction<Configuration> confTx) {
        List<Uint64> dpnIds = NatUtil.getDpnsForRouter(dataBroker, routerName);
        if (dpnIds.isEmpty()) {
            LOG.error("addOrDelDefaultFibRouteForSNATWIthBgpVpn: No dpns are part of router {} to program "
                + "default NAT flows for BGP-VPN {}", routerName, bgpVpnId);
            return;
        }
        for (Uint64 dpnId : dpnIds) {
            if (bgpVpnId != NatConstants.INVALID_ID) {
                LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : installing default NAT route for router {} "
                    + "in dpn {} for the BGP vpnID {}", routerId, dpnId, bgpVpnId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, bgpVpnId, routerId, confTx);
            } else {
                LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : installing default NAT route for router {} "
                    + "in dpn {} for the internal vpn", routerId, dpnId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, routerId, confTx);
            }
        }
    }

    private void removeDefaultFibRouteForSnatWithBgpVpn(String routerName, Uint32 routerId, Uint32 bgpVpnId,
                                                        TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        List<Uint64> dpnIds = NatUtil.getDpnsForRouter(dataBroker, routerName);
        if (dpnIds.isEmpty()) {
            LOG.error("addOrDelDefaultFibRouteForSNATWIthBgpVpn: No dpns are part of router {} to program "
                + "default NAT flows for BGP-VPN {}", routerName, bgpVpnId);
            return;
        }
        for (Uint64 dpnId : dpnIds) {
            if (bgpVpnId != NatConstants.INVALID_ID) {
                LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : removing default NAT route for router {} "
                    + "in dpn {} for the BGP vpnID {}", routerId, dpnId, bgpVpnId);
                defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, bgpVpnId, routerId, confTx);
            } else {
                LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : removing default NAT route for router {} "
                    + "in dpn {} for the internal vpn", routerId, dpnId);
                defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, routerId, confTx);
            }
        }
    }

    protected void installOutboundMissEntry(String routerName, Uint32 routerId, Uint64 primarySwitchId,
        TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("installOutboundMissEntry : Router ID from getVpnId {}", routerId);
        if (routerId != NatConstants.INVALID_ID) {
            LOG.debug("installOutboundMissEntry : Creating miss entry on primary {}, for router {}",
                    primarySwitchId, routerId);
            createOutboundTblEntry(primarySwitchId, routerId, confTx);
        } else {
            LOG.error("installOutboundMissEntry : Unable to fetch Router Id  for RouterName {}, failed to "
                + "createAndInstallMissEntry", routerName);
        }
    }

    public String getFlowRefOutbound(Uint64 dpnId, short tableId, Uint32 routerID, int protocol) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID + NatConstants.FLOWID_SEPARATOR + protocol;
    }

    private String getFlowRefNaptPreFib(Uint64 dpnId, short tableId, Uint32 vpnId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + vpnId;
    }

    public Uint64 getCookieOutboundFlow(Uint32 routerId) {
        return Uint64.valueOf((NwConstants.COOKIE_OUTBOUND_NAPT_TABLE).toJava().add(
                new BigInteger("0110001", 16)).add(BigInteger.valueOf(routerId.longValue())));
    }

    private ActionLearn getLearnActionForPunt(int protocol, int hardTimeout, Uint64 cookie) {
        long l4SrcPortField;
        long l4DstPortField;
        int l4portFieldLen = NxmOfFieldType.NXM_OF_TCP_SRC.getFlowModHeaderLenInt();

        if (protocol == NwConstants.IP_PROT_TCP) {
            l4SrcPortField = NxmOfFieldType.NXM_OF_TCP_SRC.getType();
            l4DstPortField = NxmOfFieldType.NXM_OF_TCP_DST.getType();
        } else {
            l4SrcPortField = NxmOfFieldType.NXM_OF_UDP_SRC.getType();
            l4DstPortField = NxmOfFieldType.NXM_OF_UDP_DST.getType();
        }
        List<ActionLearn.FlowMod> flowMods = Arrays.asList(
                new MatchFromValue(NwConstants.ETHTYPE_IPV4, NxmOfFieldType.NXM_OF_ETH_TYPE.getType(),
                        NxmOfFieldType.NXM_OF_ETH_TYPE.getFlowModHeaderLenInt()),
                new MatchFromValue(protocol, NxmOfFieldType.NXM_OF_IP_PROTO.getType(),
                        NxmOfFieldType.NXM_OF_IP_PROTO.getFlowModHeaderLenInt()),
                new MatchFromField(NxmOfFieldType.NXM_OF_IP_SRC.getType(), NxmOfFieldType.NXM_OF_IP_SRC.getType(),
                        NxmOfFieldType.NXM_OF_IP_SRC.getFlowModHeaderLenInt()),
                new MatchFromField(NxmOfFieldType.NXM_OF_IP_DST.getType(), NxmOfFieldType.NXM_OF_IP_DST.getType(),
                        NxmOfFieldType.NXM_OF_IP_DST.getFlowModHeaderLenInt()),
                new MatchFromField(l4SrcPortField, l4SrcPortField, l4portFieldLen),
                new MatchFromField(l4DstPortField, l4DstPortField, l4portFieldLen),
                new MatchFromField(NxmOfFieldType.OXM_OF_METADATA.getType(),
                        MetaDataUtil.METADATA_VPN_ID_OFFSET,
                        NxmOfFieldType.OXM_OF_METADATA.getType(), MetaDataUtil.METADATA_VPN_ID_OFFSET,
                        MetaDataUtil.METADATA_VPN_ID_BITLEN));

        return new ActionLearn(0, hardTimeout, 7, cookie, 0,
                NwConstants.OUTBOUND_NAPT_TABLE, 0, 0, flowMods);
    }

    private FlowEntity buildIcmpDropFlow(Uint64 dpnId, Uint32 routerId, Uint32 vpnId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(MatchIpProtocol.ICMP);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionInfos = new ArrayList<>();
        actionInfos.add(new ActionDrop());

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionInfos));

        String flowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
                NwConstants.IP_PROT_ICMP);
        FlowEntity flow = MDSALUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, "icmp drop flow", 0, 0,
                NwConstants.COOKIE_OUTBOUND_NAPT_TABLE, matches, instructions);
        return flow;
    }

    protected FlowEntity buildOutboundFlowEntity(Uint64 dpId, Uint32 routerId, int protocol) {
        LOG.debug("buildOutboundFlowEntity : called for dpId {} and routerId{}", dpId, routerId);
        Uint64 cookie = getCookieOutboundFlow(routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpProtocol((short)protocol));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        if (snatPuntTimeout != 0) {
            actionsInfos.add(getLearnActionForPunt(protocol, snatPuntTimeout, cookie));
        }
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = getFlowRefOutbound(dpId, NwConstants.OUTBOUND_NAPT_TABLE, routerId, protocol);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
            5, flowRef, 0, 0,
            cookie, matches, instructions);
        LOG.debug("installOutboundMissEntry : returning flowEntity {}", flowEntity);
        return flowEntity;
    }

    public void createOutboundTblEntry(Uint64 dpnId, Uint32 routerId, TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("createOutboundTblEntry : called for dpId {} and routerId {}", dpnId, routerId);
        FlowEntity tcpflowEntity = buildOutboundFlowEntity(dpnId, routerId, NwConstants.IP_PROT_TCP);
        LOG.debug("createOutboundTblEntry : Installing tcp flow {}", tcpflowEntity);
        mdsalManager.addFlow(confTx, tcpflowEntity);

        FlowEntity udpflowEntity = buildOutboundFlowEntity(dpnId, routerId, NwConstants.IP_PROT_UDP);
        LOG.debug("createOutboundTblEntry : Installing udp flow {}", udpflowEntity);
        mdsalManager.addFlow(confTx, udpflowEntity);

        FlowEntity icmpDropFlow = buildIcmpDropFlow(dpnId, routerId, routerId);
        LOG.debug("createOutboundTblEntry: Installing icmp drop flow {}", icmpDropFlow);
        mdsalManager.addFlow(confTx, icmpDropFlow);
    }

    @Nullable
    protected String getTunnelInterfaceName(Uint64 srcDpId, Uint64 dstDpId) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        RpcResult<GetTunnelInterfaceNameOutput> rpcResult;
        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result =
                itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                    .setSourceDpid(srcDpId)
                    .setDestinationDpid(dstDpId)
                    .setTunnelType(tunType)
                    .build());
            rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                tunType = TunnelTypeGre.class;
                result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                    .setSourceDpid(srcDpId)
                    .setDestinationDpid(dstDpId)
                    .setTunnelType(tunType)
                    .build());
                rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("getTunnelInterfaceName : RPC Call to getTunnelInterfaceId returned with Errors {}",
                            rpcResult.getErrors());
                } else {
                    return rpcResult.getResult().getInterfaceName();
                }
                LOG.warn("getTunnelInterfaceName : RPC Call to getTunnelInterfaceId returned with Errors {}",
                        rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException e) {
            LOG.error("getTunnelInterfaceName : Exception when getting tunnel interface Id for tunnel "
                    + "between {} and {}", srcDpId, dstDpId, e);
        }

        return null;
    }

    protected void installSnatMissEntryForPrimrySwch(Uint64 dpnId, String routerName, Uint32 routerId,
        TypedWriteTransaction<Configuration> confTx) {

        LOG.debug("installSnatMissEntry : called for for the primary NAPT switch dpnId {} ", dpnId);
        // Install miss entry pointing to group
        FlowEntity flowEntity = buildSnatFlowEntityForPrmrySwtch(dpnId, routerName, routerId);
        mdsalManager.addFlow(confTx, flowEntity);
    }

    protected void installSnatMissEntry(Uint64 dpnId, List<BucketInfo> bucketInfo,
            String routerName, Uint32 routerId) {
        LOG.debug("installSnatMissEntry : called for dpnId {} with primaryBucket {} ",
            dpnId, bucketInfo.get(0));
        // Install the select group
        Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
            NatUtil.getGroupIdKey(routerName));
        if (groupId == NatConstants.INVALID_ID) {
            LOG.error("installSnatMissEntry: Unable to obtain group ID for Key: {}", routerName);
            return;
        }
        GroupEntity groupEntity =
            MDSALUtil.buildGroupEntity(dpnId, groupId.longValue(), routerName, GroupTypes.GroupAll, bucketInfo);
        LOG.debug("installSnatMissEntry : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
        mdsalManager.syncInstallGroup(groupEntity);
        // Install miss entry pointing to group
        FlowEntity flowEntity = buildSnatFlowEntity(dpnId, routerName, routerId, groupId);
        if (flowEntity == null) {
            LOG.error("installSnatMissEntry : Flow entity received as NULL. "
                    + "Cannot proceed with installation of SNAT Flow in table {} which is pointing to Group "
                    + "on Non NAPT DPN {} for router {}", NwConstants.PSNAT_TABLE, dpnId, routerName);
            return;
        }
        mdsalManager.installFlow(flowEntity);
    }

    void installGroup(Uint64 dpnId, String routerName, Uint32 groupId, List<BucketInfo> bucketInfo) {
        GroupEntity groupEntity =
            MDSALUtil.buildGroupEntity(dpnId, groupId.longValue(), routerName, GroupTypes.GroupAll, bucketInfo);
        LOG.debug("installGroup : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
        mdsalManager.syncInstallGroup(groupEntity);
    }

    private FlowEntity buildSnatFlowEntity(Uint64 dpId, String routerName, Uint32 routerId, Uint32 groupId) {
        LOG.debug("buildSnatFlowEntity : called for dpId {}, routerName {} and groupId {}",
                dpId, routerName, groupId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        Uint64 tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, natOverVxlanUtil, elanManager, idManager,
                routerId, routerName);
        actionsInfo.add(new ActionSetFieldTunnelId(tunnelId));
        LOG.debug("buildSnatFlowEntity : Setting the tunnel to the list of action infos {}", actionsInfo);
        actionsInfo.add(new ActionGroup(groupId.longValue()));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("buildSnatFlowEntity : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    private FlowEntity buildSnatFlowEntityForPrmrySwtch(Uint64 dpId, String routerName, Uint32 routerId) {

        LOG.debug("buildSnatFlowEntityForPrmrySwtch : called for primary NAPT switch dpId {}, routerName {}", dpId,
            routerName);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));

        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("buildSnatFlowEntityForPrmrySwtch : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    // TODO : Replace this with ITM Rpc once its available with full functionality
    protected void installTerminatingServiceTblEntry(Uint64 dpnId, String routerName, Uint32 routerId,
        TypedWriteTransaction<Configuration> confTx) {

        LOG.debug("installTerminatingServiceTblEntry : for switch {}, routerName {}",
            dpnId, routerName);
        FlowEntity flowEntity = buildTsFlowEntity(dpnId, routerName, routerId);
        if (flowEntity == null) {
            LOG.error("installTerminatingServiceTblEntry : Flow entity received as NULL. "
                    + "Cannot proceed with installation of Terminating Service table {} which is pointing to table {} "
                    + "on DPN {} for router {}", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.OUTBOUND_NAPT_TABLE,
                    dpnId, routerName);
            return;
        }
        mdsalManager.addFlow(confTx, flowEntity);

    }

    private FlowEntity buildTsFlowEntity(Uint64 dpId, String routerName, Uint32 routerId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        Uint64 tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, natOverVxlanUtil, elanManager,
                idManager, routerId, routerName);
        matches.add(new MatchTunnelId(tunnelId));
        String flowRef = getFlowRefTs(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, Uint32.valueOf(tunnelId.longValue()));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_TS_TABLE, matches, instructions);
        return flowEntity;
    }

    public String getFlowRefTs(Uint64 dpnId, short tableId, Uint32 routerID) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }

    public static String getFlowRefSnat(Uint64 dpnId, short tableId, String routerID) {
        return NatConstants.SNAT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }

    protected void handleSwitches(Uint64 dpnId, String routerName, Uint32 routerId, Uint64 primarySwitchId) {
        LOG.debug("handleSwitches : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName(dpnId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<>();

        if (ifNamePrimary != null) {
            LOG.debug("handleSwitches : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId, true);
            if (listActionInfoPrimary.isEmpty()) {
                LOG.error("handleSwitches : Unable to retrieve output actions on Non-NAPT switch {} for router {}"
                        + " towards Napt-switch {} via tunnel interface {}", dpnId, routerName, primarySwitchId,
                        ifNamePrimary);
            }
        } else {
            LOG.error("handleSwitches : Unable to obtain primary tunnel interface to Napt-Switch {} from "
                    + "Non-Napt switch {} for router {}", primarySwitchId, dpnId, routerName);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);

        listBucketInfo.add(0, bucketPrimary);
        installSnatMissEntry(dpnId, listBucketInfo, routerName, routerId);
    }

    List<BucketInfo> getBucketInfoForNonNaptSwitches(Uint64 nonNaptSwitchId,
                                                     Uint64 primarySwitchId, String routerName, Uint32 routerId) {
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName(nonNaptSwitchId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<>();

        if (ifNamePrimary != null) {
            LOG.debug("getBucketInfoForNonNaptSwitches : On Non- Napt switch , Primary Tunnel interface is {}",
                    ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId, true);
            if (listActionInfoPrimary.isEmpty()) {
                LOG.error("getBucketInfoForNonNaptSwitches : Unable to retrieve output actions on Non-NAPT switch {} "
                        + "for router {} towards Napt-switch {} via tunnel interface {}",
                        nonNaptSwitchId, routerName, primarySwitchId, ifNamePrimary);
            }
        } else {
            LOG.error("getBucketInfoForNonNaptSwitches : Unable to obtain primary tunnel interface to Napt-Switch {} "
                    + "from Non-Napt switch {} for router {}", primarySwitchId, nonNaptSwitchId, routerName);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);

        listBucketInfo.add(0, bucketPrimary);
        return listBucketInfo;
    }

    protected void handlePrimaryNaptSwitch(Uint64 dpnId, String routerName, Uint32 routerId, Uuid externalNwUuid,
        TypedWriteTransaction<Configuration> confTx) {

       /*
        * Primary NAPT Switch â€“ bucket Should always point back to its own Outbound Table
        */

        LOG.debug("handlePrimaryNaptSwitch : Installing SNAT miss entry in Primary NAPT switch {} ", dpnId);

/*
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        listActionInfoPrimary.add(new ActionNxResubmit(NatConstants.TERMINATING_SERVICE_TABLE));
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);
*/

        installSnatMissEntryForPrimrySwch(dpnId, routerName, routerId, confTx);
        installTerminatingServiceTblEntry(dpnId, routerName, routerId, confTx);
        //Install the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table matching on the router ID.
        installNaptPfibEntry(dpnId, routerId, confTx);
        Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
        if (networkId != null) {
            Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
            if (vpnUuid != null) {
                Uint32 extVpnId = NatUtil.getExternalVpnIdForExtNetwork(dataBroker, externalNwUuid);
                coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + networkId, () -> {
                    installNaptPfibEntriesForExternalSubnets(routerName, dpnId, null);
                    //Install the NAPT PFIB TABLE which forwards outgoing packet to FIB Table matching on the VPN ID.
                    if (extVpnId != null && extVpnId != NatConstants.INVALID_ID) {
                        installNaptPfibEntry(dpnId, extVpnId, null);
                    }
                    return Collections.emptyList();
                });
            } else {
                LOG.warn("handlePrimaryNaptSwitch : External Vpn ID missing for Ext-Network : {}", networkId);
            }
        } else {
            LOG.warn("handlePrimaryNaptSwitch : External Network not available for router : {}", routerName);
        }
    }

    public void installNaptPfibEntry(Uint64 dpnId, Uint32 segmentId,
            @Nullable TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("installNaptPfibEntry : called for dpnId {} and segmentId {} ", dpnId, segmentId);
        FlowEntity naptPfibFlowEntity = buildNaptPfibFlowEntity(dpnId, segmentId);
        if (confTx != null) {
            mdsalManager.addFlow(confTx, naptPfibFlowEntity);
        } else {
            mdsalManager.installFlow(naptPfibFlowEntity);
        }
    }

    public FlowEntity buildNaptPfibFlowEntity(Uint64 dpId, Uint32 segmentId) {

        LOG.debug("buildNaptPfibFlowEntity : called for dpId {}, segmentId {}", dpId, segmentId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(Uint64.valueOf(BigInteger.ZERO)));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRefTs(dpId, NwConstants.NAPT_PFIB_TABLE, segmentId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.NAPT_PFIB_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);
        LOG.debug("buildNaptPfibFlowEntity : Returning NaptPFib Flow Entity {}", flowEntity);
        return flowEntity;
    }

    public void handleSnatReverseTraffic(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId, Routers router,
                                         Uint32 routerId, String routerName, String externalIp) {
        LOG.debug("handleSnatReverseTraffic : entry for DPN ID {}, routerId {}, externalIp: {}",
            dpnId, routerId, externalIp);
        Uuid networkId = router.getNetworkId();
        if (networkId == null) {
            LOG.error("handleSnatReverseTraffic : networkId is null for the router ID {}", routerId);
            return;
        }
        Collection<Uuid> externalSubnetList = NatUtil.getExternalSubnetIdsFromExternalIps(
                new ArrayList<ExternalIps>(router.nonnullExternalIps().values()));
        // FLAT/VLAN case having external-subnet as VPN
        String externalSubnetVpn = null;
        if (externalSubnetList != null && !externalSubnetList.isEmpty()) {
            Boolean isExternalIpsAdvertized = Boolean.FALSE;
            for (Uuid externalSubnetId : externalSubnetList) {
                Optional<Subnets> externalSubnet = NatUtil
                    .getOptionalExternalSubnets(dataBroker, externalSubnetId);
                // externalSubnet data model will exist for FLAT/VLAN external netowrk UCs.
                if (externalSubnet.isPresent()) {
                    externalSubnetVpn = externalSubnetId.getValue();
                    advToBgpAndInstallFibAndTsFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE,
                        externalSubnetVpn, routerId, routerName,
                        externalIp, networkId, router, confTx);
                    isExternalIpsAdvertized = Boolean.TRUE;
                }
            }
            if (isExternalIpsAdvertized) {
                LOG.trace("External Ips {} advertized for Router {}", router.getExternalIps(), routerName);
                return;
            }
        }

        // VXVLAN/GRE case having Internet-VPN
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
        if (vpnName == null) {
            LOG.error("handleSnatReverseTraffic : No VPN associated with ext nw {} to handle add external ip "
                    + "configuration {} in router {}", networkId, externalIp, routerId);
            return;
        }
        advToBgpAndInstallFibAndTsFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE, vpnName, routerId, routerName,
            externalIp, networkId, router, confTx);
        LOG.debug("handleSnatReverseTraffic : exit for DPN ID {}, routerId {}, externalIp : {}",
            dpnId, routerId, externalIp);
    }

    public void advToBgpAndInstallFibAndTsFlows(final Uint64 dpnId, final short tableId, final String vpnName,
                                                final Uint32 routerId, final String routerName, final String externalIp,
                                                final Uuid extNetworkId, @Nullable final Routers router,
                                                final TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("advToBgpAndInstallFibAndTsFlows : entry for DPN ID {}, tableId {}, vpnname {} "
                + "and externalIp {}", dpnId, tableId, vpnName, externalIp);
        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null || rd.isEmpty()) {
            LOG.error("advToBgpAndInstallFibAndTsFlows : Unable to get RD for VPN Name {}", vpnName);
            return;
        }
        ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, extNetworkId);
        if (extNwProvType == null) {
            LOG.error("advToBgpAndInstallFibAndTsFlows : External Network Provider Type missing");
            return;
        }
        if (extNwProvType == ProviderTypes.VXLAN) {
            evpnSnatFlowProgrammer.evpnAdvToBgpAndInstallFibAndTsFlows(dpnId, tableId, externalIp, vpnName, rd,
                nextHopIp, routerId, routerName, extNetworkId, confTx);
            return;
        }
        //Generate VPN label for the external IP
        GenerateVpnLabelInput labelInput = new GenerateVpnLabelInputBuilder().setVpnName(vpnName)
            .setIpPrefix(externalIp).build();
        ListenableFuture<RpcResult<GenerateVpnLabelOutput>> labelFuture = vpnService.generateVpnLabel(labelInput);

        //On successful generation of the VPN label, advertise the route to the BGP and install the FIB routes.
        ListenableFuture<RpcResult<CreateFibEntryOutput>> future = Futures.transformAsync(labelFuture, result -> {
            if (result.isSuccessful()) {
                LOG.debug("advToBgpAndInstallFibAndTsFlows : inside apply with result success");
                GenerateVpnLabelOutput output = result.getResult();
                final Uint32 label = output.getLabel();

                int externalIpInDsFlag = 0;
                //Get IPMaps from the DB for the router ID
                List<IpMap> dbIpMaps = NaptManager.getIpMapList(dataBroker, routerId);
                for (IpMap dbIpMap : dbIpMaps) {
                    String dbExternalIp = dbIpMap.getExternalIp();
                    //Select the IPMap, whose external IP is the IP for which FIB is installed
                    if (dbExternalIp.contains(externalIp)) {
                        String dbInternalIp = dbIpMap.getInternalIp();
                        IpMapKey dbIpMapKey = dbIpMap.key();
                        LOG.debug("advToBgpAndInstallFibAndTsFlows : Setting label {} for internalIp {} "
                                + "and externalIp {}", label, dbInternalIp, externalIp);
                        IpMap newIpm = new IpMapBuilder().withKey(dbIpMapKey).setInternalIp(dbInternalIp)
                            .setExternalIp(dbExternalIp).setLabel(label).build();
                        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            naptManager.getIpMapIdentifier(routerId, dbInternalIp), newIpm);
                        externalIpInDsFlag++;
                    }
                }
                if (externalIpInDsFlag <= 0) {
                    LOG.debug("advToBgpAndInstallFibAndTsFlows : External Ip {} not found in DS, "
                            + "Failed to update label {} for routerId {} in DS",
                            externalIp, label, routerId);
                    String errMsg = String.format("Failed to update label %s due to external Ip %s not"
                        + " found in DS for router %s", label, externalIp, routerId);
                    return Futures.immediateFailedFuture(new Exception(errMsg));
                }
                //Inform BGP
                Uint32 l3vni = Uint32.ZERO;
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    l3vni = natOverVxlanUtil.getInternetVpnVni(vpnName, l3vni);
                }
                Routers extRouter = router != null ? router :
                    NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
                NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd,
                    externalIp, nextHopIp, extRouter.getNetworkId().getValue(), null, label, l3vni,
                    RouteOrigin.STATIC, dpnId);

                //Install custom FIB routes
                List<Instruction> tunnelTableCustomInstructions = new ArrayList<>();
                tunnelTableCustomInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
                makeTunnelTableEntry(dpnId, label, l3vni, tunnelTableCustomInstructions, confTx,
                        extNwProvType);
                makeLFibTableEntry(dpnId, label, tableId, confTx);

                //Install custom FIB routes - FIB table.
                List<Instruction> fibTableCustomInstructions = createFibTableCustomInstructions(tableId,
                        routerName, externalIp);
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    //Install the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
                    NatUtil.makePreDnatToSnatTableEntry(mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, confTx);
                }
                String externalVpn = vpnName;
                Uuid externalSubnetId = NatUtil.getExternalSubnetForRouterExternalIp(externalIp, extRouter);
                if (extNwProvType == ProviderTypes.VLAN || extNwProvType == ProviderTypes.FLAT) {
                    Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker,
                            externalSubnetId);
                    if (externalSubnet.isPresent()) {
                        externalVpn =  externalSubnetId.getValue();
                    }
                }
                String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
                CreateFibEntryInput input = new CreateFibEntryInputBuilder()
                    .setVpnName(externalVpn)
                    .setSourceDpid(dpnId).setIpAddress(fibExternalIp).setServiceId(label)
                    .setIpAddressSource(CreateFibEntryInput.IpAddressSource.ExternalFixedIP)
                    .setInstruction(fibTableCustomInstructions).build();
                return fibService.createFibEntry(input);
            } else {
                LOG.error("advToBgpAndInstallFibAndTsFlows : inside apply with result failed");
                String errMsg = String.format("Could not retrieve the label for prefix %s in VPN %s, %s",
                    externalIp, vpnName, result.getErrors());
                return Futures.immediateFailedFuture(new RuntimeException(errMsg));
            }
        }, MoreExecutors.directExecutor());

        Futures.addCallback(future, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {

            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("advToBgpAndInstallFibAndTsFlows : Error in generate label or fib install process", error);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<CreateFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LOG.info("advToBgpAndInstallFibAndTsFlows : Successfully installed custom FIB routes for prefix {}",
                            externalIp);
                } else {
                    LOG.error("advToBgpAndInstallFibAndTsFlows : Error in rpc call to create custom Fib entries "
                            + "for prefix {} in DPN {}, {}", externalIp, dpnId, result.getErrors());
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private List<Instruction> createFibTableCustomInstructions(short tableId, String routerName,
            String externalIp) {
        List<Instruction> fibTableCustomInstructions = new ArrayList<>();
        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
        Uint32 externalSubnetVpnId = NatUtil.getExternalSubnetVpnIdForRouterExternalIp(dataBroker,
                externalIp, router);
        int instructionIndex = 0;
        if (externalSubnetVpnId != NatConstants.INVALID_ID) {
            Uint64 subnetIdMetaData = MetaDataUtil.getVpnIdMetadata(externalSubnetVpnId.longValue());
            fibTableCustomInstructions.add(new InstructionWriteMetadata(subnetIdMetaData,
                    MetaDataUtil.METADATA_MASK_VRFID).buildInstruction(instructionIndex));
            instructionIndex++;
        }

        fibTableCustomInstructions.add(new InstructionGotoTable(tableId).buildInstruction(instructionIndex));
        return fibTableCustomInstructions;
    }

    private void makeLFibTableEntry(Uint64 dpId, Uint32 serviceId, short tableId,
        TypedWriteTransaction<Configuration> confTx) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId.longValue()));

        Map<InstructionKey, Instruction> instructionsMap = new HashMap<InstructionKey, Instruction>();
        int instructionKey = 0;
        List<ActionInfo> actionsInfos = new ArrayList<>();
        //NAT is required for IPv4 only. Hence always etherType will be IPv4
        actionsInfos.add(new ActionPopMpls(NwConstants.ETHTYPE_IPV4));
        Instruction writeInstruction = new InstructionApplyActions(actionsInfos).buildInstruction(0);
        instructionsMap.put(new InstructionKey(++instructionKey), writeInstruction);
        instructionsMap.put(new InstructionKey(++instructionKey),
                new InstructionGotoTable(tableId).buildInstruction(1));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            COOKIE_VM_LFIB_TABLE, matches, instructionsMap);

        mdsalManager.addFlow(confTx, dpId, flowEntity);

        LOG.debug("makeLFibTableEntry : LFIB Entry for dpID {} : label : {} modified successfully", dpId, serviceId);
    }

    private void makeTunnelTableEntry(Uint64 dpnId, Uint32 serviceId, Uint32 l3Vni,
        List<Instruction> customInstructions, TypedWriteTransaction<Configuration> confTx,
        ProviderTypes extNwProvType) {

        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.debug("makeTunnelTableEntry : DpnId = {} and serviceId = {} and actions = {}",
            dpnId, serviceId, customInstructions);

        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            mkMatches.add(new MatchTunnelId(Uint64.valueOf(l3Vni)));
        } else {
            mkMatches.add(new MatchTunnelId(Uint64.valueOf(serviceId)));
        }
        Map<InstructionKey, Instruction> customInstructionsMap = new HashMap<InstructionKey, Instruction>();
        int instructionKey = 0;
        for (Instruction instructionObj : customInstructions) {
            customInstructionsMap.put(new InstructionKey(++instructionKey), instructionObj);
        }
        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""),
                NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY,
                String.format("%s:%s", "TST Flow Entry ", serviceId), 0, 0,
                Uint64.valueOf(COOKIE_TUNNEL.toJava().add(BigInteger.valueOf(serviceId.longValue()))),
                mkMatches, customInstructionsMap);

        mdsalManager.addFlow(confTx, dpnId, terminatingServiceTableFlowEntity);
    }

    protected InstanceIdentifier<RouterIds> getRoutersIdentifier(Uint32 routerId) {
        return InstanceIdentifier.builder(RouterIdName.class).child(RouterIds.class,
            new RouterIdsKey(routerId)).build();
    }

    private String getFlowRef(Uint64 dpnId, short tableId, Uint32 id, String ipAddress) {
        String suffixToUse = "";
        if (tableId == NwConstants.INTERNAL_TUNNEL_TABLE) {
            suffixToUse = NatConstants.TST_FLOW_ID_SUFFIX;
        }
        return NatConstants.SNAT_FLOWID_PREFIX + suffixToUse + dpnId.toString() + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + id + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    @Override
    public void update(InstanceIdentifier<Routers> identifier, Routers original, Routers update) {
        if (natMode != NatMode.Controller) {
            return;
        }
        LOG.trace("update : origRouter: {} updatedRouter: {}", original, update);
        String routerName = original.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("update : external router event - Invalid routerId for routerName {}", routerName);
            return;
        }
        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + update.key(), () -> {
            List<ListenableFuture<?>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, writeFlowInvTx -> {
                futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, removeFlowInvTx -> {
                    Uint32 bgpVpnId = NatConstants.INVALID_ID;
                    Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
                    if (bgpVpnUuid != null) {
                        bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                    }
                    //BigInteger dpnId = getPrimaryNaptSwitch(routerName);
                    /* Get Primary Napt Switch for existing router from "router-to-napt-switch" DS.
                     * if dpnId value is null or zero then go for electing new Napt switch for existing router.
                     */
                    Uint64 dpnId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
                    boolean isPrimaryNaptSwitchNotSelected = (dpnId == null || dpnId.equals(Uint64
                            .valueOf(BigInteger.ZERO)));
                    Uuid networkId = original.getNetworkId();
                    // Check if its update on SNAT flag
                    boolean originalSNATEnabled = original.isEnableSnat();
                    boolean updatedSNATEnabled = update.isEnableSnat();
                    LOG.debug("update :called with originalFlag and updatedFlag for SNAT enabled "
                            + "as {} and {} with Elected Dpn {}(isPrimaryNaptSwitchNotSelected:{})",
                        originalSNATEnabled, updatedSNATEnabled, dpnId, isPrimaryNaptSwitchNotSelected);
                    // Cluster Reboot Case Handling
                    // 1. DPN not elected during add event(due to none of the OVS connected)
                    // 2. Update event called with changes of parameters(but enableSnat is not changed)
                    // 3. First Elect dpnId and process other changes with valid dpnId
                    if (originalSNATEnabled != updatedSNATEnabled || isPrimaryNaptSwitchNotSelected) {
                        if (originalSNATEnabled && !updatedSNATEnabled) {
                            if (isPrimaryNaptSwitchNotSelected) {
                                LOG.info("No Action to be taken when SNAT is disabled "
                                    + "with no Napt Switch Election for Router {}", routerName);
                                return;
                            }
                            //SNAT disabled for the router
                            Uuid networkUuid = original.getNetworkId();
                            LOG.info("update : SNAT disabled for Router {}", routerName);
                            Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
                            final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
                            handleDisableSnat(original, networkUuid, externalIps, false, vpnName,
                                    dpnId, routerId, removeFlowInvTx);
                        }  else if (updatedSNATEnabled) {
                            LOG.info("update : SNAT enabled for Router {}", routerName);
                            addOrDelDefFibRouteToSNAT(routerName, routerId, bgpVpnId, bgpVpnUuid,
                                true, writeFlowInvTx);
                            if (isPrimaryNaptSwitchNotSelected) {
                                dpnId = selectNewNAPTSwitch(routerName);
                                if (dpnId != null && !dpnId.equals(Uint64.valueOf(BigInteger.ZERO))) {
                                    handleEnableSnat(update, routerId, dpnId, bgpVpnId, removeFlowInvTx);
                                } else {
                                    LOG.error("update : Failed to elect Napt Switch During update event"
                                        + " of router {}", routerName);
                                }
                            }
                        }
                        LOG.info("update : no need to process external/subnet changes as it's will taken care"
                            + "in handleDisableSnat/handleEnableSnat");
                        return;
                    }
                    if (!Objects.equals(original.getExtGwMacAddress(), update.getExtGwMacAddress())) {
                        NatUtil.installRouterGwFlows(txRunner, vpnManager, original, dpnId, NwConstants.DEL_FLOW);
                        NatUtil.installRouterGwFlows(txRunner, vpnManager, update, dpnId, NwConstants.ADD_FLOW);
                    }

                    if (updatedSNATEnabled != originalSNATEnabled) {
                        LOG.info("update : no need to process external/subnet changes as it's will taken care in "
                                + "handleDisableSnat/handleEnableSnat");
                        return;
                    }
                    //Check if the Update is on External IPs
                    LOG.debug("update : Checking if this is update on External IPs for router {}", routerName);
                    List<String> originalExternalIps = NatUtil.getIpsListFromExternalIps(
                            new ArrayList<ExternalIps>(original.nonnullExternalIps().values()));
                    List<String> updatedExternalIps = NatUtil.getIpsListFromExternalIps(
                            new ArrayList<ExternalIps>(update.nonnullExternalIps().values()));

                    //Check if the External IPs are removed during the update.
                    Set<String> removedExternalIps = new HashSet<>(originalExternalIps);
                    removedExternalIps.removeAll(updatedExternalIps);
                    if (removedExternalIps.size() > 0) {
                        LOG.debug("update : Start processing of the External IPs removal for router {}", routerName);
                        vpnManager.removeArpResponderFlowsToExternalNetworkIps(routerName,
                                removedExternalIps, original.getExtGwMacAddress(),
                                dpnId, networkId);

                        for (String removedExternalIp : removedExternalIps) {
                /*
                    1) Remove the mappings in the IntExt IP model which has external IP.
                    2) Remove the external IP in the ExternalCounter model.
                    3) For the corresponding subnet IDs whose external IP mapping was removed, allocate one of the
                       least loaded external IP.
                       Store the subnet IP and the reallocated external IP mapping in the IntExtIp model.
                    4) Increase the count of the allocated external IP by one.
                    5) Advertise to the BGP if external IP is allocated for the first time for the router
                     i.e. the route for the external IP is absent.
                    6) Remove the NAPT translation entries from Inbound and Outbound NAPT tables for
                     the removed external IPs and also from the model.
                    7) Advertise to the BGP for removing the route for the removed external IPs.
                 */

                            String[] externalIpParts = NatUtil.getExternalIpAndPrefix(removedExternalIp);
                            String externalIp = externalIpParts[0];
                            String externalIpPrefix = externalIpParts[1];
                            String externalIpAddrStr = externalIp + "/" + externalIpPrefix;

                            LOG.debug("update : Clear the routes from the BGP and remove the FIB and TS "
                                    + "entries for removed external IP {}", externalIpAddrStr);
                            Uuid vpnUuId = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                            String vpnName = "";
                            if (vpnUuId != null) {
                                vpnName = vpnUuId.getValue();
                            }
                            clrRtsFromBgpAndDelFibTs(dpnId, routerId, externalIpAddrStr, vpnName, networkId,
                                    update.getExtGwMacAddress(), removeFlowInvTx);

                            LOG.debug("update : Remove the mappings in the IntExtIP model which has external IP.");
                            //Get the internal IPs which are associated to the removed external IPs
                            List<IpMap> ipMaps = naptManager.getIpMapList(dataBroker, routerId);
                            List<String> removedInternalIps = new ArrayList<>();
                            for (IpMap ipMap : ipMaps) {
                                if (ipMap.getExternalIp().equals(externalIpAddrStr)) {
                                    removedInternalIps.add(ipMap.getInternalIp());
                                }
                            }

                            LOG.debug("update : Remove the mappings of the internal IPs from the IntExtIP model.");
                            for (String removedInternalIp : removedInternalIps) {
                                LOG.debug("update : Remove the IP mapping of the internal IP {} for the "
                                                + "router ID {} from the IntExtIP model",
                                        removedInternalIp, routerId);
                                naptManager.removeFromIpMapDS(routerId, removedInternalIp);
                            }

                            LOG.debug("update : Remove the count mapping of the external IP {} for the "
                                            + "router ID {} from the ExternalIpsCounter model.",
                                    externalIpAddrStr, routerId);
                            naptManager.removeExternalIpCounter(routerId, externalIpAddrStr);

                            LOG.debug("update : Allocate the least loaded external IPs to the subnets "
                                    + "whose external IPs were removed.");
                            for (String removedInternalIp : removedInternalIps) {
                                allocateExternalIp(dpnId, update, routerId, routerName, networkId,
                                        removedInternalIp, writeFlowInvTx);
                            }
                            LOG.debug("update : Remove the NAPT translation entries from "
                                    + "Inbound and Outbound NAPT tables for the removed external IPs.");
                            //Get the internalIP and internal Port which were associated to the removed external IP.
                            Map<ProtocolTypes, List<String>> protoTypesIntIpPortsMap = new HashMap<>();
                            InstanceIdentifier<IpPortMapping> ipPortMappingId = InstanceIdentifier
                                    .builder(IntextIpPortMap.class)
                                    .child(IpPortMapping.class, new IpPortMappingKey(routerId)).build();
                            Optional<IpPortMapping> ipPortMapping;
                            try {
                                ipPortMapping = SingleTransactionDataBroker
                                            .syncReadOptional(dataBroker,
                                                    LogicalDatastoreType.CONFIGURATION, ipPortMappingId);
                            } catch (InterruptedException | ExecutionException e) {
                                LOG.error("Failed to read ipPortMapping for router id {}", routerId, e);
                                ipPortMapping = Optional.empty();
                            }

                            if (ipPortMapping.isPresent()) {
                                for (IntextIpProtocolType intextIpProtocolType :
                                        ipPortMapping.get().nonnullIntextIpProtocolType().values()) {
                                    ProtocolTypes protoType = intextIpProtocolType.getProtocol();
                                    for (IpPortMap ipPortMap : intextIpProtocolType.nonnullIpPortMap().values()) {
                                        IpPortExternal ipPortExternal = ipPortMap.getIpPortExternal();
                                        if (ipPortExternal.getIpAddress().equals(externalIp)) {
                                            List<String> removedInternalIpPorts =
                                                    protoTypesIntIpPortsMap.get(protoType);
                                            if (removedInternalIpPorts != null) {
                                                removedInternalIpPorts.add(ipPortMap.getIpPortInternal());
                                                protoTypesIntIpPortsMap.put(protoType, removedInternalIpPorts);
                                            } else {
                                                removedInternalIpPorts = new ArrayList<>();
                                                removedInternalIpPorts.add(ipPortMap.getIpPortInternal());
                                                protoTypesIntIpPortsMap.put(protoType, removedInternalIpPorts);
                                            }
                                        }
                                    }
                                }
                            }

                            //Remove the IP port map from the intext-ip-port-map model, which were containing
                            // the removed external IP.
                            Set<Map.Entry<ProtocolTypes, List<String>>> protoTypesIntIpPorts =
                                    protoTypesIntIpPortsMap.entrySet();
                            Map<String, List<String>> internalIpPortMap = new HashMap<>();
                            for (Map.Entry protoTypesIntIpPort : protoTypesIntIpPorts) {
                                ProtocolTypes protocolType = (ProtocolTypes) protoTypesIntIpPort.getKey();
                                List<String> removedInternalIpPorts = (List<String>) protoTypesIntIpPort.getValue();
                                for (String removedInternalIpPort : removedInternalIpPorts) {
                                    // Remove the IP port map from the intext-ip-port-map model,
                                    // which were containing the removed external IP
                                    naptManager.removeFromIpPortMapDS(routerId, removedInternalIpPort,
                                            protocolType);
                                    //Remove the IP port incomint packer map.
                                    naptPacketInHandler.removeIncomingPacketMap(
                                            routerId + NatConstants.COLON_SEPARATOR + removedInternalIpPort);
                                    String[] removedInternalIpPortParts = removedInternalIpPort
                                            .split(NatConstants.COLON_SEPARATOR);
                                    if (removedInternalIpPortParts.length == 2) {
                                        String removedInternalIp = removedInternalIpPortParts[0];
                                        String removedInternalPort = removedInternalIpPortParts[1];
                                        List<String> removedInternalPortsList =
                                                internalIpPortMap.get(removedInternalPort);
                                        if (removedInternalPortsList != null) {
                                            removedInternalPortsList.add(removedInternalPort);
                                            internalIpPortMap.put(removedInternalIp, removedInternalPortsList);
                                            naptPacketInHandler.removeIncomingPacketMap(routerId
                                                + NatConstants.COLON_SEPARATOR + removedInternalIp
                                                + NatConstants.COLON_SEPARATOR + removedInternalPort);
                                            //Remove the NAPT translation entries from Outbound NAPT table
                                            naptEventHandler.removeNatFlows(dpnId,
                                                NwConstants.OUTBOUND_NAPT_TABLE,
                                                routerId, removedInternalIp,
                                                Integer.parseInt(removedInternalPort),
                                                protocolType.getName());
                                            naptEventHandler.removeNatFlows(dpnId,
                                                NwConstants.INBOUND_NAPT_TABLE,
                                                routerId, removedInternalIp,
                                                Integer.parseInt(removedInternalPort),
                                                protocolType.getName());
                                        } else {
                                            removedInternalPortsList = new ArrayList<>();
                                            removedInternalPortsList.add(removedInternalPort);
                                            internalIpPortMap.put(removedInternalIp, removedInternalPortsList);
                                            naptPacketInHandler.removeIncomingPacketMap(routerId
                                                + NatConstants.COLON_SEPARATOR + removedInternalIp
                                                + NatConstants.COLON_SEPARATOR + removedInternalPort);
                                            //Remove the NAPT translation entries from Outbound NAPT table
                                            naptEventHandler.removeNatFlows(dpnId,
                                                NwConstants.OUTBOUND_NAPT_TABLE,
                                                routerId, removedInternalIp,
                                                Integer.parseInt(removedInternalPort),
                                                protocolType.getName());
                                            naptEventHandler.removeNatFlows(dpnId,
                                                NwConstants.INBOUND_NAPT_TABLE, routerId, removedInternalIp,
                                                Integer.parseInt(removedInternalPort),
                                                protocolType.getName());
                                        }
                                    }
                                }
                            }

                            // Delete the entry from SnatIntIpPortMap DS
                            Set<String> internalIps = internalIpPortMap.keySet();
                            for (String internalIp : internalIps) {
                                LOG.debug("update : Removing IpPort having the internal IP {} from the "
                                        + "model SnatIntIpPortMap", internalIp);
                                naptManager.removeFromSnatIpPortDS(routerId, internalIp);
                            }

                            naptManager.removeNaptPortPool(externalIp);
                        }
                        LOG.debug(
                                "update : End processing of the External IPs removal for router {}", routerName);
                    }

                    //Check if the External IPs are added during the update.
                    Set<String> addedExternalIps = new HashSet<>(updatedExternalIps);
                    addedExternalIps.removeAll(originalExternalIps);
                    if (addedExternalIps.size() != 0) {
                        LOG.debug("update : Start processing of the External IPs addition for router {}",
                            routerName);
                        vpnManager.addArpResponderFlowsToExternalNetworkIps(routerName, addedExternalIps,
                                update.getExtGwMacAddress(), dpnId,
                                update.getNetworkId());

                        for (String addedExternalIp : addedExternalIps) {
                /*
                    1) Do nothing in the IntExtIp model.
                    2) Initialise the count of the added external IP to 0 in the ExternalCounter model.
                 */
                            String[] externalIpParts = NatUtil.getExternalIpAndPrefix(addedExternalIp);
                            String externalIp = externalIpParts[0];
                            String externalIpPrefix = externalIpParts[1];
                            String externalpStr = externalIp + "/" + externalIpPrefix;
                            LOG.debug("update : Initialise the count mapping of the external IP {} for the "
                                            + "router ID {} in the ExternalIpsCounter model.",
                                    externalpStr, routerId);
                            naptManager.initialiseNewExternalIpCounter(routerId, externalpStr);
                            subnetRegisterMapping(update, routerId);
                            LOG.info("update : Installing fib flow fo newly added Ips");
                            handleSnatReverseTraffic(writeFlowInvTx, dpnId, update, routerId, routerName, externalpStr);
                        }
                        LOG.debug(
                                "update : End processing of the External IPs addition during the update operation");
                    }

                    //Check if its Update on subnets
                    LOG.debug("update : Checking if this is update on subnets for router {}", routerName);
                    List<Uuid> originalSubnetIds = original.getSubnetIds();
                    List<Uuid> updatedSubnetIds = update.getSubnetIds();
                    Set<Uuid> addedSubnetIds =
                        updatedSubnetIds != null ? new HashSet<>(updatedSubnetIds) : new HashSet<>();
                    if (originalSubnetIds != null) {
                        addedSubnetIds.removeAll(originalSubnetIds);
                    }

                    //Check if the Subnet IDs are added during the update.
                    if (addedSubnetIds.size() != 0) {
                        LOG.debug(
                                "update : Start processing of the Subnet IDs addition for router {}", routerName);
                        for (Uuid addedSubnetId : addedSubnetIds) {
                /*
                    1) Select the least loaded external IP for the subnet and store the mapping of the
                    subnet IP and the external IP in the IntExtIp model.
                    2) Increase the count of the selected external IP by one.
                    3) Advertise to the BGP if external IP is allocated for the first time for the
                    router i.e. the route for the external IP is absent.
                 */
                            String subnetIp = NatUtil.getSubnetIp(dataBroker, addedSubnetId);
                            if (subnetIp != null) {
                                allocateExternalIp(dpnId, update, routerId, routerName, networkId, subnetIp,
                                        writeFlowInvTx);
                            }
                        }
                        LOG.debug("update : End processing of the Subnet IDs addition for router {}", routerName);
                    }

                    //Check if the Subnet IDs are removed during the update.
                    Set<Uuid> removedSubnetIds = new HashSet<>(originalSubnetIds);
                    removedSubnetIds.removeAll(updatedSubnetIds);
                    if (removedSubnetIds.size() != 0) {
                        LOG.debug(
                                "update : Start processing of the Subnet IDs removal for router {}", routerName);
                        for (Uuid removedSubnetId : removedSubnetIds) {
                            String[] subnetAddr = NatUtil.getSubnetIpAndPrefix(dataBroker, removedSubnetId);
                            if (subnetAddr != null) {
                    /*
                        1) Remove the subnet IP and the external IP in the IntExtIp map
                        2) Decrease the count of the coresponding external IP by one.
                        3) Advertise to the BGP for removing the routes of the corresponding external
                        IP if its not allocated to any other internal IP.
                    */

                                String externalIp = naptManager.getExternalIpAllocatedForSubnet(routerId,
                                        subnetAddr[0] + "/" + subnetAddr[1]);
                                if (externalIp == null) {
                                    LOG.error("update : No mapping found for router ID {} and internal IP {}",
                                            routerId, subnetAddr[0]);
                                    return;
                                }

                                naptManager.updateCounter(routerId, externalIp, false);
                                // Traverse entire model of external-ip counter whether external ip is not
                                // used by any other internal ip in any router
                                if (!isExternalIpAllocated(externalIp)) {
                                    LOG.debug("update : external ip is not allocated to any other "
                                            + "internal IP so proceeding to remove routes");
                                    clrRtsFromBgpAndDelFibTs(dpnId, routerId, networkId,
                                            Collections.singleton(externalIp), null, update.getExtGwMacAddress(),
                                            removeFlowInvTx);
                                    LOG.debug("update : Successfully removed fib entries in switch {} for "
                                                    + "router {} with networkId {} and externalIp {}",
                                            dpnId, routerId, networkId, externalIp);
                                }

                                LOG.debug("update : Remove the IP mapping for the router ID {} and "
                                        + "internal IP {} external IP {}", routerId, subnetAddr[0], externalIp);
                                naptManager.removeIntExtIpMapDS(routerId, subnetAddr[0] + "/" + subnetAddr[1]);
                            }
                        }
                        LOG.debug("update : End processing of the Subnet IDs removal for router {}", routerName);
                    }
                }));
            }));
            return futures;
        }, NatConstants.NAT_DJC_MAX_RETRIES);
    }

    private boolean isExternalIpAllocated(String externalIp) {
        InstanceIdentifier<ExternalIpsCounter> id = InstanceIdentifier.builder(ExternalIpsCounter.class).build();
        Optional<ExternalIpsCounter> externalCountersData;
        try {
            externalCountersData = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read external counters data for ExternalIp {}", externalIp, e);
            externalCountersData = Optional.empty();
        }
        if (externalCountersData.isPresent()) {
            ExternalIpsCounter externalIpsCounters = externalCountersData.get();
            for (ExternalCounters ext : externalIpsCounters.nonnullExternalCounters().values()) {
                for (ExternalIpCounter externalIpCount : ext.nonnullExternalIpCounter().values()) {
                    if (externalIpCount.getExternalIp().equals(externalIp)) {
                        if (externalIpCount.getCounter().toJava() != 0) {
                            return true;
                        }
                        break;
                    }
                }
            }
        }
        return false;
    }

    private void allocateExternalIp(Uint64 dpnId, Routers router, Uint32 routerId, String routerName,
            Uuid networkId, String subnetIp, TypedWriteTransaction<Configuration> writeFlowInvTx) {
        String[] subnetIpParts = NatUtil.getSubnetIpAndPrefix(subnetIp);
        try {
            InetAddress address = InetAddress.getByName(subnetIpParts[0]);
            if (address instanceof Inet6Address) {
                LOG.debug("allocateExternalIp : Skipping ipv6 address {} for the router {}.", address, routerName);
                return;
            }
        } catch (UnknownHostException e) {
            LOG.error("allocateExternalIp : Invalid ip address {}", subnetIpParts[0], e);
            return;
        }
        String leastLoadedExtIpAddr = NatUtil.getLeastLoadedExternalIp(dataBroker, routerId);
        if (leastLoadedExtIpAddr != null) {
            String[] externalIpParts = NatUtil.getExternalIpAndPrefix(leastLoadedExtIpAddr);
            String leastLoadedExtIp = externalIpParts[0];
            String leastLoadedExtIpPrefix = externalIpParts[1];
            IPAddress externalIpAddr = new IPAddress(leastLoadedExtIp, Integer.parseInt(leastLoadedExtIpPrefix));
            subnetIp = subnetIpParts[0];
            String subnetIpPrefix = subnetIpParts[1];
            IPAddress subnetIpAddr = new IPAddress(subnetIp, Integer.parseInt(subnetIpPrefix));
            LOG.debug("allocateExternalIp : Add the IP mapping for the router ID {} and internal "
                    + "IP {} and prefix {} -> external IP {} and prefix {}",
                routerId, subnetIp, subnetIpPrefix, leastLoadedExtIp, leastLoadedExtIpPrefix);
            naptManager.registerMapping(routerId, subnetIpAddr, externalIpAddr);


            // Check if external IP is already assigned a route. (i.e. External IP is previously
            // allocated to any of the subnets)
            // If external IP is already assigned a route, (, do not re-advertise to the BGP
            String leastLoadedExtIpAddrStr = leastLoadedExtIp + "/" + leastLoadedExtIpPrefix;
            Uint32 label = checkExternalIpLabel(routerId, leastLoadedExtIpAddrStr);
            if (label != null) {
                //update
                String internalIp = subnetIpParts[0] + "/" + subnetIpParts[1];
                IpMapKey ipMapKey = new IpMapKey(internalIp);
                LOG.debug("allocateExternalIp : Setting label {} for internalIp {} and externalIp {}",
                    label, internalIp, leastLoadedExtIpAddrStr);
                IpMap newIpm = new IpMapBuilder().withKey(ipMapKey).setInternalIp(internalIp)
                    .setExternalIp(leastLoadedExtIpAddrStr).setLabel(label).build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    naptManager.getIpMapIdentifier(routerId, internalIp), newIpm);
                return;
            }

            // Re-advertise to the BGP for the external IP, which is allocated to the subnet
            // for the first time and hence not having a route.
            //Get the VPN Name using the network ID
            final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
            if (vpnName != null) {
                LOG.debug("allocateExternalIp : Retrieved vpnName {} for networkId {}", vpnName, networkId);
                if (dpnId == null || dpnId.equals(Uint64.valueOf(BigInteger.ZERO))) {
                    LOG.debug("allocateExternalIp : Best effort for getting primary napt switch when router i/f are"
                        + "added after gateway-set");
                    dpnId = NatUtil.getPrimaryNaptfromRouterId(dataBroker, routerId);
                    if (dpnId == null || dpnId.equals(Uint64.valueOf(BigInteger.ZERO))) {
                        LOG.error("allocateExternalIp : dpnId is null or Zero for the router {}", routerName);
                        return;
                    }
                }
                advToBgpAndInstallFibAndTsFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE, vpnName, routerId, routerName,
                    leastLoadedExtIp + "/" + leastLoadedExtIpPrefix, networkId, router,
                    writeFlowInvTx);
            }
        }
    }

    @Nullable
    protected Uint32 checkExternalIpLabel(Uint32 routerId, String externalIp) {
        List<IpMap> ipMaps = naptManager.getIpMapList(dataBroker, routerId);
        for (IpMap ipMap : ipMaps) {
            if (ipMap.getExternalIp().equals(externalIp)) {
                if (ipMap.getLabel() != null) {
                    return ipMap.getLabel();
                }
            }
        }
        LOG.error("checkExternalIpLabel : no ipMaps found for routerID:{} and externalIP:{}", routerId, externalIp);
        return null;
    }

    @Override
    public void remove(InstanceIdentifier<Routers> identifier, Routers router) {
        if (natMode != NatMode.Controller) {
            return;
        }
        LOG.trace("remove : Router delete method");
        /*
        ROUTER DELETE SCENARIO
        1) Get the router ID from the event.
        2) Build the cookie information from the router ID.
        3) Get the primary and secondary switch DPN IDs using the router ID from the model.
        4) Build the flow with the cookie value.
        5) Delete the flows which matches the cookie information from the NAPT outbound, inbound tables.
        6) Remove the flows from the other switches which points to the primary and secondary
         switches for the flows related the router ID.
        7) Get the list of external IP address maintained for the router ID.
        8) Use the NaptMananager removeMapping API to remove the list of IP addresses maintained.
        9) Withdraw the corresponding routes from the BGP.
         */

        if (identifier == null || router == null) {
            LOG.error("remove : returning without processing since routers is null");
            return;
        }

        String routerName = router.getRouterName();
        coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + router.key(),
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                LOG.info("remove : Removing default NAT route from FIB on all dpns part of router {} ",
                        routerName);
                Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
                if (routerId == NatConstants.INVALID_ID) {
                    LOG.error("remove : Remove external router event - Invalid routerId for routerName {}",
                            routerName);
                    return;
                }
                Uint32 bgpVpnId = NatConstants.INVALID_ID;
                Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
                if (bgpVpnUuid != null) {
                    bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                }
                addOrDelDefFibRouteToSNAT(routerName, routerId, bgpVpnId, bgpVpnUuid, false,
                        tx);
                Uuid networkUuid = router.getNetworkId();

                Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
                if (primarySwitchId == null || primarySwitchId.equals(Uint64.ZERO)) {
                    // No NAPT switch for external router, probably because the router is not attached to
                    // any
                    // internal networks
                    LOG.debug(
                            "No NAPT switch for router {}, check if router is attached to any internal "
                                    + "network",
                            routerName);
                    return;
                } else {
                    Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
                    final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkUuid);
                    handleDisableSnat(router, networkUuid, externalIps, true, vpnName, primarySwitchId,
                            routerId, tx);
                }
                if (NatUtil.releaseId(idManager, NatConstants.ODL_VNI_POOL_NAME, routerName)
                    == NatConstants.INVALID_ID) {
                    LOG.error("remove: Unable to release VNI for router - {}", routerName);
                }
            })), NatConstants.NAT_DJC_MAX_RETRIES);
    }

    public void handleDisableSnat(Routers router, Uuid networkUuid, @NonNull Collection<String> externalIps,
                                  boolean routerFlag, @Nullable String vpnName, Uint64 naptSwitchDpnId,
                                  Uint32 routerId, TypedReadWriteTransaction<Configuration> removeFlowInvTx) {
        LOG.info("handleDisableSnat : Entry");
        String routerName = router.getRouterName();
        try {
            if (routerFlag) {
                removeNaptSwitch(routerName);
            } else {
                updateNaptSwitch(routerName, Uint64.valueOf(BigInteger.ZERO));
            }

            LOG.debug("handleDisableSnat : Remove the ExternalCounter model for the router ID {}", routerId);
            naptManager.removeExternalCounter(routerId);

            LOG.debug("handleDisableSnat : got primarySwitch as dpnId {}", naptSwitchDpnId);
            if (naptSwitchDpnId == null || naptSwitchDpnId.equals(Uint64.valueOf(BigInteger.ZERO))) {
                LOG.error("handleDisableSnat : Unable to retrieve the primary NAPT switch for the "
                    + "router ID {} from RouterNaptSwitch model", routerId);
                return;
            }
            ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName,
                    networkUuid);
            if (extNwProvType == null) {
                LOG.error("handleDisableSnat : External Network Provider Type missing");
                return;
            }
            Collection<Uuid> externalSubnetList = NatUtil.getExternalSubnetIdsFromExternalIps(
                    new ArrayList<ExternalIps>(router.nonnullExternalIps().values()));
            removeNaptFlowsFromActiveSwitch(routerId, routerName, naptSwitchDpnId, networkUuid, vpnName, externalIps,
                    externalSubnetList, removeFlowInvTx, extNwProvType);
            removeFlowsFromNonActiveSwitches(routerId, routerName, naptSwitchDpnId, removeFlowInvTx);
            String externalSubnetVpn = null;
            for (Uuid externalSubnetId : externalSubnetList) {
                Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker, externalSubnetId);
                // externalSubnet data model will exist for FLAT/VLAN external netowrk UCs.
                if (externalSubnet.isPresent()) {
                    externalSubnetVpn =  externalSubnetId.getValue();
                    clrRtsFromBgpAndDelFibTs(naptSwitchDpnId, routerId, networkUuid, externalIps, externalSubnetVpn,
                            router.getExtGwMacAddress(), removeFlowInvTx);
                }
            }
            if (externalSubnetVpn == null) {
                clrRtsFromBgpAndDelFibTs(naptSwitchDpnId, routerId, networkUuid, externalIps, vpnName,
                        router.getExtGwMacAddress(), removeFlowInvTx);
            }
            // Use the NaptMananager removeMapping API to remove the entire list of IP addresses maintained
            // for the router ID.
            LOG.debug("handleDisableSnat : Remove the Internal to external IP address maintained for the "
                    + "router ID {} in the DS", routerId);
            naptManager.removeMapping(routerId);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("handleDisableSnat : Exception while handling disableSNAT for router :{}", routerName, e);
        }
        LOG.info("handleDisableSnat : Exit");
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleDisableSnatInternetVpn(String routerName, Uint32 routerId, Uuid networkUuid,
                                             @NonNull Collection<String> externalIps,
                                             String vpnId, TypedReadWriteTransaction<Configuration> writeFlowInvTx) {
        LOG.debug("handleDisableSnatInternetVpn: Started to process handle disable snat for router {} "
                + "with internet vpn {}", routerName, vpnId);
        try {
            Uint64 naptSwitchDpnId = null;
            InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch =
                NatUtil.buildNaptSwitchRouterIdentifier(routerName);
            Optional<RouterToNaptSwitch> rtrToNapt;
            try {
                rtrToNapt = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.CONFIGURATION, routerToNaptSwitch);
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Failed to read NAPT switch for router {}", routerName, e);
                rtrToNapt = Optional.empty();
            }
            if (rtrToNapt.isPresent()) {
                naptSwitchDpnId = rtrToNapt.get().getPrimarySwitchId();
            }
            LOG.debug("handleDisableSnatInternetVpn : got primarySwitch as dpnId{} ", naptSwitchDpnId);

            removeNaptFlowsFromActiveSwitchInternetVpn(routerId, routerName, naptSwitchDpnId, networkUuid, vpnId,
                    writeFlowInvTx);
            try {
                String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
                if (extGwMacAddress != null) {
                    LOG.debug("handleDisableSnatInternetVpn : External Gateway MAC address {} found for "
                            + "External Router ID {}", extGwMacAddress, routerId);
                } else {
                    LOG.error("handleDisableSnatInternetVpn : No External Gateway MAC address found for "
                            + "External Router ID {}", routerId);
                    return;
                }
                clrRtsFromBgpAndDelFibTs(naptSwitchDpnId, routerId, networkUuid, externalIps, vpnId, extGwMacAddress,
                        writeFlowInvTx);
            } catch (Exception ex) {
                LOG.error("handleDisableSnatInternetVpn : Failed to remove fib entries for routerId {} "
                        + "in naptSwitchDpnId {}", routerId, naptSwitchDpnId, ex);
            }
            if (NatUtil.releaseId(idManager, NatConstants.ODL_VNI_POOL_NAME, vpnId) == NatConstants.INVALID_ID) {
                LOG.error("handleDisableSnatInternetVpn : Unable to release VNI for vpnId {} ", vpnId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("handleDisableSnatInternetVpn: Exception while handling disableSNATInternetVpn for router {} "
                    + "with internet vpn {}", routerName, vpnId, e);
        }
        LOG.debug("handleDisableSnatInternetVpn: Processed handle disable snat for router {} with internet vpn {}",
                routerName, vpnId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateNaptSwitch(String routerName, Uint64 naptSwitchId) {
        RouterToNaptSwitch naptSwitch = new RouterToNaptSwitchBuilder().withKey(new RouterToNaptSwitchKey(routerName))
            .setPrimarySwitchId(naptSwitchId).build();
        try {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                NatUtil.buildNaptSwitchRouterIdentifier(routerName), naptSwitch);
        } catch (Exception ex) {
            LOG.error("updateNaptSwitch : Failed to write naptSwitch {} for router {} in ds",
                naptSwitchId, routerName);
        }
        LOG.debug("updateNaptSwitch : Successfully updated naptSwitch {} for router {} in ds",
            naptSwitchId, routerName);
    }

    protected void removeNaptSwitch(String routerName) {
        // Remove router and switch from model
        InstanceIdentifier<RouterToNaptSwitch> id =
            InstanceIdentifier.builder(NaptSwitches.class)
                .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
        LOG.debug("removeNaptSwitch : Removing NaptSwitch and Router for the router {} from datastore", routerName);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        //Release allocated router_lPort_tag from the ID Manager if Router is on L3VPNOverVxlan
        NatEvpnUtil.releaseLPortTagForRouter(dataBroker, idManager, routerName);
    }

    public void removeNaptFlowsFromActiveSwitch(Uint32 routerId, String routerName,
                                                Uint64 dpnId, Uuid networkId, String vpnName,
                                                @NonNull Collection<String> externalIps,
                                                Collection<Uuid> externalSubnetList,
                                                TypedReadWriteTransaction<Configuration> confTx,
                                                ProviderTypes extNwProvType)
            throws InterruptedException, ExecutionException {

        LOG.debug("removeNaptFlowsFromActiveSwitch : Remove NAPT flows from Active switch");
        Uint64 cookieSnatFlow = NatUtil.getCookieNaptFlow(routerId);

        //Remove the PSNAT entry which forwards the packet to Outbound NAPT Table (For the
        // traffic which comes from the  VMs of the NAPT switches)
        String preSnatFlowRef = getFlowRefSnat(dpnId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity preSnatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.PSNAT_TABLE, preSnatFlowRef);

        LOG.info(
            "removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {} "
                + "and router ID {}", NwConstants.PSNAT_TABLE, dpnId, routerId);
        mdsalManager.removeFlow(confTx, preSnatFlowEntity);

        //Remove the Terminating Service table entry which forwards the packet to Outbound NAPT Table (For the
        // traffic which comes from the VMs of the non NAPT switches)
        Uint64 tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, natOverVxlanUtil,
                elanManager, idManager, routerId, routerName);
        String tsFlowRef = getFlowRefTs(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, Uint32.valueOf(tunnelId.longValue()));
        FlowEntity tsNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, tsFlowRef);
        LOG.info(
            "removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {} "
                + "and router ID {}", NwConstants.INTERNAL_TUNNEL_TABLE, dpnId, routerId);
        mdsalManager.removeFlow(confTx, tsNatFlowEntity);

        //Remove the flow table 25->44 from NAPT Switch
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            NatUtil.removePreDnatToSnatTableEntry(confTx, mdsalManager, dpnId);
        }

        //Remove the Outbound flow entry which forwards the packet to FIB Table
        LOG.info(
            "removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {}"
                + " and router ID {}", NwConstants.OUTBOUND_NAPT_TABLE, dpnId, routerId);

        String outboundTcpNatFlowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
            NwConstants.IP_PROT_TCP);
        FlowEntity outboundTcpNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
            outboundTcpNatFlowRef);
        mdsalManager.removeFlow(confTx, outboundTcpNatFlowEntity);

        String outboundUdpNatFlowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
            NwConstants.IP_PROT_UDP);
        FlowEntity outboundUdpNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
            outboundUdpNatFlowRef);
        mdsalManager.removeFlow(confTx, outboundUdpNatFlowEntity);

        String icmpDropFlowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
            NwConstants.IP_PROT_ICMP);
        FlowEntity icmpDropFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
            icmpDropFlowRef);
        mdsalManager.removeFlow(confTx, icmpDropFlowEntity);
        boolean lastRouterOnExternalNetwork =
            !NatUtil.checkForRoutersWithSameExtNetAndNaptSwitch(dataBroker, networkId, routerName, dpnId);
        if (lastRouterOnExternalNetwork) {
            removeNaptFibExternalOutputFlows(routerId, dpnId, networkId, externalIps, confTx);
        }
        //Remove the NAPT PFIB TABLE (47->21) which forwards the incoming packet to FIB Table matching on the
        // External Subnet Vpn Id.
        for (Uuid externalSubnetId : externalSubnetList) {
            Uint32 subnetVpnId = NatUtil.getVpnId(dataBroker, externalSubnetId.getValue());
            if (subnetVpnId != NatConstants.INVALID_ID && !NatUtil.checkForRoutersWithSameExtSubnetAndNaptSwitch(
                dataBroker, externalSubnetId, routerName, dpnId)) {
                String natPfibSubnetFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, subnetVpnId);
                FlowEntity natPfibFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE,
                    natPfibSubnetFlowRef);
                mdsalManager.removeFlow(confTx, natPfibFlowEntity);
                LOG.debug("removeNaptFlowsFromActiveSwitch : Removed the flow in table {} with external subnet "
                          + "Vpn Id {} as metadata on Napt Switch {}", NwConstants.NAPT_PFIB_TABLE,
                    subnetVpnId, dpnId);
            }
        }

        //Remove the NAPT PFIB TABLE which forwards the incoming packet to FIB Table matching on the router ID.
        String natPfibFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        FlowEntity natPfibFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE, natPfibFlowRef);

        LOG.info(
            "removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {} "
            + "and router ID {}", NwConstants.NAPT_PFIB_TABLE, dpnId, routerId);
        mdsalManager.removeFlow(confTx, natPfibFlowEntity);

        if (lastRouterOnExternalNetwork) {
            // Long vpnId = NatUtil.getVpnId(dataBroker, routerId);
            // - This does not work since ext-routers is deleted already - no network info
            //Get the VPN ID from the ExternalNetworks model
            Uint32 vpnId = NatConstants.INVALID_ID;
            if (vpnName == null || vpnName.isEmpty()) {
                // ie called from router delete cases
                Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                LOG.debug("removeNaptFlowsFromActiveSwitch : vpnUuid is {}", vpnUuid);
                if (vpnUuid != null) {
                    vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
                    LOG.debug("removeNaptFlowsFromActiveSwitch : vpnId {} for external  network {} router delete "
                              + "or disableSNAT scenario", vpnId, networkId);
                }
            } else {
                // ie called from disassociate vpn case
                LOG.debug("removeNaptFlowsFromActiveSwitch : This is disassociate nw with vpn case with vpnName {}",
                    vpnName);
                vpnId = NatUtil.getVpnId(dataBroker, vpnName);
                LOG.debug("removeNaptFlowsFromActiveSwitch : vpnId for disassociate nw with vpn scenario {}",
                    vpnId);
            }

            if (vpnId != NatConstants.INVALID_ID) {
                //Remove the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table matching on the VPN ID.
                String natPfibVpnFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, vpnId);
                FlowEntity natPfibVpnFlowEntity =
                    NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE, natPfibVpnFlowRef);
                LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in {} for the active switch with the "
                         + "DPN ID {} and VPN ID {}", NwConstants.NAPT_PFIB_TABLE, dpnId, vpnId);
                mdsalManager.removeFlow(confTx, natPfibVpnFlowEntity);
            }
        }

        //For the router ID get the internal IP , internal port and the corresponding external IP and external Port.
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            LOG.error("removeNaptFlowsFromActiveSwitch : Unable to retrieve the IpPortMapping");
            return;
        }

        for (IntextIpProtocolType intextIpProtocolType : ipPortMapping.nonnullIntextIpProtocolType().values()) {
            String protocol = intextIpProtocolType.getProtocol().name();
            for (IpPortMap ipPortMap : intextIpProtocolType.nonnullIpPortMap().values()) {
                String ipPortInternal = ipPortMap.getIpPortInternal();
                String[] ipPortParts = ipPortInternal.split(":");
                if (ipPortParts.length != 2) {
                    LOG.error("removeNaptFlowsFromActiveSwitch : Unable to retrieve the Internal IP and port");
                    return;
                }
                String internalIp = ipPortParts[0];
                String internalPort = ipPortParts[1];

                //Build the flow for the outbound NAPT table
                naptPacketInHandler.removeIncomingPacketMap(routerId + NatConstants.COLON_SEPARATOR + internalIp
                    + NatConstants.COLON_SEPARATOR + internalPort);
                String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
                    String.valueOf(routerId), internalIp, Integer.parseInt(internalPort), protocol);
                FlowEntity outboundNaptFlowEntity =
                    NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch "
                    + "with the DPN ID {} and router ID {}", NwConstants.OUTBOUND_NAPT_TABLE, dpnId, routerId);
                mdsalManager.removeFlow(confTx, outboundNaptFlowEntity);

                 //Build the flow for the inbound NAPT table
                switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE,
                    String.valueOf(routerId), internalIp, Integer.parseInt(internalPort), protocol);
                FlowEntity inboundNaptFlowEntity =
                    NatUtil.buildFlowEntity(dpnId, NwConstants.INBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active active switch "
                    + "with the DPN ID {} and router ID {}", NwConstants.INBOUND_NAPT_TABLE, dpnId, routerId);
                mdsalManager.removeFlow(confTx, inboundNaptFlowEntity);
            }
        }
    }

    protected void removeNaptFibExternalOutputFlows(Uint32 routerId, Uint64 dpnId, Uuid networkId,
                                                    @NonNull Collection<String> externalIps,
                                                    TypedReadWriteTransaction<Configuration> writeFlowInvTx)
            throws ExecutionException, InterruptedException {
        Uint32 extVpnId = NatConstants.INVALID_ID;
        if (networkId != null) {
            Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
            if (vpnUuid != null) {
                extVpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
            } else {
                LOG.debug("removeNaptFibExternalOutputFlows : vpnUuid is null");
            }
        } else {
            LOG.debug("removeNaptFibExternalOutputFlows : networkId is null");
            extVpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
        }
        if (extVpnId == NatConstants.INVALID_ID) {
            LOG.warn("removeNaptFibExternalOutputFlows : extVpnId not found for routerId {}", routerId);
            extVpnId = routerId;
        }
        for (String ip : externalIps) {
            String extIp = removeMaskFromIp(ip);
            String naptFlowRef = getFlowRefNaptPreFib(dpnId, NwConstants.NAPT_PFIB_TABLE, extVpnId);
            LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in table {} for the active switch"
                + " with the DPN ID {} and router ID {} and IP {} flowRef {}",
                NwConstants.NAPT_PFIB_TABLE, dpnId, routerId, extIp, naptFlowRef);
            FlowEntity natPfibVpnFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE, naptFlowRef);
            mdsalManager.removeFlow(writeFlowInvTx, natPfibVpnFlowEntity);
        }
    }

    private String removeMaskFromIp(String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            return ip.split("/")[0];
        }
        return ip;
    }

    public void removeNaptFlowsFromActiveSwitchInternetVpn(Uint32 routerId, String routerName,
                                                           Uint64 dpnId, Uuid networkId, String vpnName,
                                                           TypedReadWriteTransaction<Configuration> writeFlowInvTx)
            throws ExecutionException, InterruptedException {
        LOG.debug("removeNaptFlowsFromActiveSwitchInternetVpn : Remove NAPT flows from Active switch Internet Vpn");
        Uint64 cookieSnatFlow = NatUtil.getCookieNaptFlow(routerId);

        //Remove the NAPT PFIB TABLE entry
        Uint32 vpnId = NatConstants.INVALID_ID;
        if (vpnName != null) {
            // ie called from disassociate vpn case
            LOG.debug("removeNaptFlowsFromActiveSwitchInternetVpn : This is disassociate nw with vpn case "
                    + "with vpnName {}", vpnName);
            vpnId = NatUtil.getVpnId(dataBroker, vpnName);
            LOG.debug("removeNaptFlowsFromActiveSwitchInternetVpn : vpnId for disassociate nw with vpn scenario {}",
                    vpnId);
        }

        if (vpnId != NatConstants.INVALID_ID && !NatUtil.checkForRoutersWithSameExtNetAndNaptSwitch(dataBroker,
                networkId, routerName, dpnId)) {
            //Remove the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table matching on the VPN ID.
            String natPfibVpnFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, vpnId);
            FlowEntity natPfibVpnFlowEntity =
                NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE, natPfibVpnFlowRef);
            LOG.info("removeNaptFlowsFromActiveSwitchInternetVpn : Remove the flow in the {} for the active switch "
                    + "with the DPN ID {} and VPN ID {}", NwConstants.NAPT_PFIB_TABLE, dpnId, vpnId);
            mdsalManager.removeFlow(writeFlowInvTx, natPfibVpnFlowEntity);

            // Remove IP-PORT active NAPT entries and release port from IdManager
            // For the router ID get the internal IP , internal port and the corresponding
            // external IP and external Port.
            IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
            if (ipPortMapping == null) {
                LOG.error("removeNaptFlowsFromActiveSwitchInternetVpn : Unable to retrieve the IpPortMapping");
                return;
            }
            for (IntextIpProtocolType intextIpProtocolType : ipPortMapping.nonnullIntextIpProtocolType().values()) {
                String protocol = intextIpProtocolType.getProtocol().name();
                for (IpPortMap ipPortMap : intextIpProtocolType.nonnullIpPortMap().values()) {
                    String ipPortInternal = ipPortMap.getIpPortInternal();
                    String[] ipPortParts = ipPortInternal.split(":");
                    if (ipPortParts.length != 2) {
                        LOG.error("removeNaptFlowsFromActiveSwitchInternetVpn : Unable to retrieve the Internal IP "
                                + "and port");
                        return;
                    }
                    String internalIp = ipPortParts[0];
                    String internalPort = ipPortParts[1];

                    //Build the flow for the outbound NAPT table
                    naptPacketInHandler.removeIncomingPacketMap(routerId + NatConstants.COLON_SEPARATOR + internalIp
                            + NatConstants.COLON_SEPARATOR + internalPort);
                    String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
                        String.valueOf(routerId), internalIp, Integer.parseInt(internalPort), protocol);
                    FlowEntity outboundNaptFlowEntity =
                        NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                    LOG.info("removeNaptFlowsFromActiveSwitchInternetVpn : Remove the flow in the {} for the "
                            + "active switch with the DPN ID {} and router ID {}",
                            NwConstants.OUTBOUND_NAPT_TABLE, dpnId, routerId);
                    mdsalManager.removeFlow(writeFlowInvTx, outboundNaptFlowEntity);

                    IpPortExternal ipPortExternal = ipPortMap.getIpPortExternal();
                    final String externalIp = ipPortExternal.getIpAddress();

                    //Build the flow for the inbound NAPT table
                    switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE,
                        String.valueOf(routerId), internalIp, Integer.parseInt(internalPort), protocol);
                    FlowEntity inboundNaptFlowEntity =
                        NatUtil.buildFlowEntity(dpnId, NwConstants.INBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                    LOG.info("removeNaptFlowsFromActiveSwitchInternetVpn : Remove the flow in the {} for the "
                            + "active active switch with the DPN ID {} and router ID {}",
                            NwConstants.INBOUND_NAPT_TABLE, dpnId, routerId);
                    mdsalManager.removeFlow(writeFlowInvTx, inboundNaptFlowEntity);

                    // Finally release port from idmanager
                    String internalIpPort = internalIp + ":" + internalPort;
                    naptManager.removePortFromPool(internalIpPort, externalIp);

                    //Remove sessions from models
                    naptManager.removeIpPortMappingForRouterID(routerId);
                    naptManager.removeIntIpPortMappingForRouterID(routerId);
                }
            }
        } else {
            LOG.error("removeNaptFlowsFromActiveSwitchInternetVpn : Invalid vpnId {}", vpnId);
        }
    }

    public void removeFlowsFromNonActiveSwitches(Uint32 routerId, String routerName,
            Uint64 naptSwitchDpnId, TypedReadWriteTransaction<Configuration> removeFlowInvTx)
            throws ExecutionException, InterruptedException {
        LOG.debug("removeFlowsFromNonActiveSwitches : Remove NAPT related flows from non active switches");

        // Remove the flows from the other switches which points to the primary and secondary switches
        // for the flows related the router ID.
        List<Uint64> allSwitchList = naptSwitchSelector.getDpnsForVpn(routerName);
        if (allSwitchList.isEmpty()) {
            LOG.error("removeFlowsFromNonActiveSwitches : Unable to get the swithces for the router {}", routerName);
            return;
        }
        for (Uint64 dpnId : allSwitchList) {
            if (!naptSwitchDpnId.equals(dpnId)) {
                LOG.info("removeFlowsFromNonActiveSwitches : Handle Ordinary switch");

                //Remove the PSNAT entry which forwards the packet to Terminating Service table
                String preSnatFlowRef = getFlowRefSnat(dpnId, NwConstants.PSNAT_TABLE, String.valueOf(routerName));
                FlowEntity preSnatFlowEntity =
                    NatUtil.buildFlowEntity(dpnId, NwConstants.PSNAT_TABLE, preSnatFlowRef);

                LOG.info("removeFlowsFromNonActiveSwitches : Remove the flow in the {} for the non active switch "
                    + "with the DPN ID {} and router ID {}", NwConstants.PSNAT_TABLE, dpnId, routerId);
                mdsalManager.removeFlow(removeFlowInvTx, preSnatFlowEntity);

                //Remove the group entry which forwards the traffic to the out port (VXLAN tunnel).
                Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
                    NatUtil.getGroupIdKey(routerName));
                if (groupId != NatConstants.INVALID_ID) {
                    LOG.info(
                        "removeFlowsFromNonActiveSwitches : Remove the group {} for the non active switch with "
                            + "the DPN ID {} and router ID {}", groupId, dpnId, routerId);
                    mdsalManager.removeGroup(removeFlowInvTx, dpnId, groupId.longValue());
                } else {
                    LOG.error("removeFlowsFromNonActiveSwitches: Unable to obtained groupID for router:{}", routerName);
                }
            }
        }
    }

    public void clrRtsFromBgpAndDelFibTs(final Uint64 dpnId, Uint32 routerId, @Nullable Uuid networkUuid,
                                         @NonNull Collection<String> externalIps, @Nullable String vpnName,
                                         String extGwMacAddress, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        //Withdraw the corresponding routes from the BGP.
        //Get the network ID using the router ID.
        LOG.debug("clrRtsFromBgpAndDelFibTs : Advertise to BGP and remove routes for externalIps {} with routerId {},"
                + "network Id {} and vpnName {}", externalIps, routerId, networkUuid, vpnName);
        if (networkUuid == null) {
            LOG.error("clrRtsFromBgpAndDelFibTs : networkId is null");
            return;
        }

        if (externalIps.isEmpty()) {
            LOG.error("clrRtsFromBgpAndDelFibTs : externalIps is empty");
            return;
        }

        if (vpnName == null) {
            //Get the VPN Name using the network ID
            vpnName = NatUtil.getAssociatedVPN(dataBroker, networkUuid);
            if (vpnName == null) {
                LOG.error("clrRtsFromBgpAndDelFibTs : No VPN associated with ext nw {} for the router {}",
                    networkUuid, routerId);
                return;
            }
        }
        LOG.debug("clrRtsFromBgpAndDelFibTs : Retrieved vpnName {} for networkId {}", vpnName, networkUuid);

        //Remove custom FIB routes
        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        for (String extIp : externalIps) {
            extIp = NatUtil.validateAndAddNetworkMask(extIp);
            clrRtsFromBgpAndDelFibTs(dpnId, routerId, extIp, vpnName, networkUuid, extGwMacAddress, confTx);
        }
    }

    protected void clrRtsFromBgpAndDelFibTs(final Uint64 dpnId, Uint32 routerId, String extIp, final String vpnName,
                                            final Uuid networkUuid, String extGwMacAddress,
                                            TypedReadWriteTransaction<Configuration> removeFlowInvTx)
            throws ExecutionException, InterruptedException {
        clearBgpRoutes(extIp, vpnName);
        delFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, networkUuid, extGwMacAddress, false,
                removeFlowInvTx);
    }

    protected void delFibTsAndReverseTraffic(final Uint64 dpnId, String routerName, Uint32 routerId, String extIp,
                                             String vpnName, Uuid extNetworkId, Uint32 tempLabel,
                                             String gwMacAddress, boolean switchOver,
                                             TypedReadWriteTransaction<Configuration> removeFlowInvTx)
            throws ExecutionException, InterruptedException {
        LOG.debug("delFibTsAndReverseTraffic : Removing fib entry for externalIp {} in routerId {}", extIp, routerId);
        //String routerName = NatUtil.getRouterName(dataBroker,routerId);
        if (routerName == null) {
            LOG.error("delFibTsAndReverseTraffic : Could not retrieve Router Name from Router ID {} ", routerId);
            return;
        }
        ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName, extNetworkId);
        if (extNwProvType == null) {
            LOG.error("delFibTsAndReverseTraffic : External Network Provider Type Missing");
            return;
        }
        /*  Remove the flow table19->44 and table36->44 entries for SNAT reverse traffic flow if the
         * external network provided type is VxLAN
         */
        if (extNwProvType == ProviderTypes.VXLAN) {
            evpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, gwMacAddress
            );
            return;
        }
        if (tempLabel.longValue() < 0) {
            LOG.error("delFibTsAndReverseTraffic : Label not found for externalIp {} with router id {}",
                    extIp, routerId);
            return;
        }
        final Uint32 label = tempLabel;
        final String externalIp = NatUtil.validateAndAddNetworkMask(extIp);
        RemoveFibEntryInput input = null;
        if (extNwProvType == ProviderTypes.FLAT || extNwProvType == ProviderTypes.VLAN) {
            LOG.debug("delFibTsAndReverseTraffic : Using extSubnetId as vpnName for FLAT/VLAN use-cases");
            Routers extRouter = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
            Uuid externalSubnetId = NatUtil.getExternalSubnetForRouterExternalIp(externalIp,
                    extRouter);

            Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker,
                    externalSubnetId);

            if (externalSubnet.isPresent()) {
                vpnName =  externalSubnetId.getValue();
            }
        }
        final String externalVpn = vpnName;
        if (label != null && label.toJava() <= 0) {
            LOG.error("delFibTsAndReverseTraffic : Label not found for externalIp {} with router id {}",
                extIp, routerId);
            input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(externalIp)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).build();
        } else {
            input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(externalIp).setServiceId(label)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).build();
            removeTunnelTableEntry(dpnId, label, removeFlowInvTx);
            removeLFibTableEntry(dpnId, label, removeFlowInvTx);
        }
        ListenableFuture<RpcResult<RemoveFibEntryOutput>> future = fibService.removeFibEntry(input);

        removeTunnelTableEntry(dpnId, label, removeFlowInvTx);
        removeLFibTableEntry(dpnId, label, removeFlowInvTx);
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            //Remove the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
            NatUtil.removePreDnatToSnatTableEntry(removeFlowInvTx, mdsalManager, dpnId);
        }
        if (!switchOver) {
            ListenableFuture<RpcResult<RemoveVpnLabelOutput>> labelFuture =
                Futures.transformAsync(future, result -> {
                    //Release label
                    if (result.isSuccessful() && label != null && label.toJava() > 0) {
                        NatUtil.removePreDnatToSnatTableEntry(removeFlowInvTx, mdsalManager, dpnId);
                        RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder()
                            .setVpnName(externalVpn).setIpPrefix(externalIp).build();
                        Future<RpcResult<RemoveVpnLabelOutput>> labelFuture1 = vpnService.removeVpnLabel(labelInput);
                        if (labelFuture1.get() == null || !labelFuture1.get().isSuccessful()) {
                            String errMsg = String.format(
                                    "ExternalRoutersListener: RPC call to remove VPN label "
                                            + "on dpn %s for prefix %s failed for vpn %s - %s",
                                    dpnId, externalIp, result.getErrors());
                            LOG.error(errMsg);
                            return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                        }
                        return JdkFutureAdapters.listenInPoolThread(labelFuture1);
                    } else {
                        String errMsg =
                            String.format("RPC call to remove custom FIB entries on dpn %s for "
                                + "prefix %s Failed - %s", dpnId, externalIp, result.getErrors());
                        LOG.error(errMsg);
                        return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                    }
                }, MoreExecutors.directExecutor());

            Futures.addCallback(labelFuture, new FutureCallback<RpcResult<RemoveVpnLabelOutput>>() {

                @Override
                public void onFailure(@NonNull Throwable error) {
                    LOG.error("delFibTsAndReverseTraffic : Error in removing the label:{} or custom fib entries"
                        + "got external ip {}", label, extIp, error);
                }

                @Override
                public void onSuccess(@NonNull RpcResult<RemoveVpnLabelOutput> result) {
                    if (result.isSuccessful()) {
                        LOG.debug("delFibTsAndReverseTraffic : Successfully removed the label for the prefix {} "
                            + "from VPN {}", externalIp, externalVpn);
                    } else {
                        LOG.error("delFibTsAndReverseTraffic : Error in removing the label for prefix {} "
                            + " from VPN {}, {}", externalIp, externalVpn, result.getErrors());
                    }
                }
            }, MoreExecutors.directExecutor());
        } else {
            LOG.debug("delFibTsAndReverseTraffic: switch-over is happened on DpnId {}. No need to release allocated "
                    + "label {} for external fixed ip {} for router {}", dpnId, label, externalIp, routerId);
        }
    }

    private void delFibTsAndReverseTraffic(final Uint64 dpnId, Uint32 routerId, String extIp, final String vpnName,
                                           final Uuid networkUuid, String extGwMacAddress, boolean switchOver,
                                           TypedReadWriteTransaction<Configuration> removeFlowInvTx)
            throws ExecutionException, InterruptedException {
        LOG.debug("delFibTsAndReverseTraffic : Removing fib entry for externalIp {} in routerId {}", extIp, routerId);
        String routerName = NatUtil.getRouterName(dataBroker,routerId);
        if (routerName == null) {
            LOG.error("delFibTsAndReverseTraffic : Could not retrieve Router Name from Router ID {} ", routerId);
            return;
        }
        //Get the external network provider type from networkId
        ProviderTypes extNwProvType = NatUtil.getProviderTypefromNetworkId(dataBroker, networkUuid);
        if (extNwProvType == null) {
            LOG.error("delFibTsAndReverseTraffic : Could not retrieve provider type for external network {} ",
                    networkUuid);
            return;
        }
        /* Remove the flow table19->44 and table36->44 entries for SNAT reverse traffic flow if the
         *  external network provided type is VxLAN
         */
        if (extNwProvType == ProviderTypes.VXLAN) {
            evpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, extGwMacAddress);
            return;
        }
        //Get IPMaps from the DB for the router ID
        List<IpMap> dbIpMaps = NaptManager.getIpMapList(dataBroker, routerId);
        if (dbIpMaps.isEmpty()) {
            LOG.error("delFibTsAndReverseTraffic : IPMaps not found for router {}", routerId);
            return;
        }

        Uint32 tempLabel = NatConstants.INVALID_ID;
        for (IpMap dbIpMap : dbIpMaps) {
            String dbExternalIp = dbIpMap.getExternalIp();
            LOG.debug("delFibTsAndReverseTraffic : Retrieved dbExternalIp {} for router id {}", dbExternalIp, routerId);
            //Select the IPMap, whose external IP is the IP for which FIB is installed
            if (extIp.equals(dbExternalIp)) {
                tempLabel = dbIpMap.getLabel();
                LOG.debug("delFibTsAndReverseTraffic : Retrieved label {} for dbExternalIp {} with router id {}",
                    tempLabel, dbExternalIp, routerId);
                break;
            }
        }
        if (tempLabel == NatConstants.INVALID_ID) {
            LOG.error("delFibTsAndReverseTraffic : Label not found for externalIp {} with router id {}",
                    extIp, routerId);
            return;
        }

        final Uint32 label = tempLabel;
        final String externalIp = NatUtil.validateAndAddNetworkMask(extIp);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder()
                .setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalIp)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).setServiceId(label).build();
        ListenableFuture<RpcResult<RemoveFibEntryOutput>> future = fibService.removeFibEntry(input);

        removeTunnelTableEntry(dpnId, label, removeFlowInvTx);
        removeLFibTableEntry(dpnId, label, removeFlowInvTx);
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            //Remove the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
            NatUtil.removePreDnatToSnatTableEntry(removeFlowInvTx, mdsalManager, dpnId);
        }
        if (!switchOver) {
            ListenableFuture<RpcResult<RemoveVpnLabelOutput>> labelFuture =
                    Futures.transformAsync(future, result -> {
                        //Release label
                        if (result.isSuccessful()) {
                            RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder()
                                    .setVpnName(vpnName).setIpPrefix(externalIp).build();
                            Future<RpcResult<RemoveVpnLabelOutput>> labelFuture1 = vpnService
                                    .removeVpnLabel(labelInput);
                            if (labelFuture1.get() == null || !labelFuture1.get().isSuccessful()) {
                                String errMsg = String.format(
                                        "RPC call to remove VPN label on dpn %s for prefix %s "
                                                + "failed for vpn %s - %s", dpnId, externalIp, vpnName,
                                        result.getErrors());
                                LOG.error(errMsg);
                                return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                            }
                            return JdkFutureAdapters.listenInPoolThread(labelFuture1);
                        } else {
                            String errMsg =
                                    String.format("RPC call to remove custom FIB entries on dpn %s for "
                                            + "prefix %s Failed - %s",
                                            dpnId, externalIp, result.getErrors());
                            LOG.error(errMsg);
                            return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                        }
                    }, MoreExecutors.directExecutor());

            Futures.addCallback(labelFuture, new FutureCallback<RpcResult<RemoveVpnLabelOutput>>() {

                @Override
                public void onFailure(@NonNull Throwable error) {
                    LOG.error("delFibTsAndReverseTraffic : Error in removing the label or custom fib entries", error);
                }

                @Override
                public void onSuccess(@NonNull RpcResult<RemoveVpnLabelOutput> result) {
                    if (result.isSuccessful()) {
                        LOG.debug("delFibTsAndReverseTraffic : Successfully removed the label for the prefix {} "
                            + "from VPN {}", externalIp, vpnName);
                    } else {
                        LOG.error("delFibTsAndReverseTraffic : Error in removing the label for prefix {} "
                            + " from VPN {}, {}", externalIp, vpnName, result.getErrors());
                    }
                }
            }, MoreExecutors.directExecutor());
        } else {
            LOG.debug("delFibTsAndReverseTraffic: switch-over is happened on DpnId {}. No need to release allocated "
                    + "label {} for external fixed ip {} for router {}", dpnId, label, externalIp, routerId);
        }
    }

    protected void clearFibTsAndReverseTraffic(final Uint64 dpnId, Uint32 routerId, Uuid networkUuid,
            List<String> externalIps, @Nullable String vpnName, String extGwMacAddress,
            TypedReadWriteTransaction<Configuration> writeFlowInvTx) throws ExecutionException, InterruptedException {
        //Withdraw the corresponding routes from the BGP.
        //Get the network ID using the router ID.
        LOG.debug("clearFibTsAndReverseTraffic : for externalIps {} with routerId {},"
                + "network Id {} and vpnName {}", externalIps, routerId, networkUuid, vpnName);
        if (networkUuid == null) {
            LOG.error("clearFibTsAndReverseTraffic : networkId is null");
            return;
        }

        if (externalIps == null || externalIps.isEmpty()) {
            LOG.error("clearFibTsAndReverseTraffic : externalIps is null");
            return;
        }

        if (vpnName == null) {
            //Get the VPN Name using the network ID
            vpnName = NatUtil.getAssociatedVPN(dataBroker, networkUuid);
            if (vpnName == null) {
                LOG.error("clearFibTsAndReverseTraffic : No VPN associated with ext nw {} for the router {}",
                    networkUuid, routerId);
                return;
            }
        }
        LOG.debug("Retrieved vpnName {} for networkId {}", vpnName, networkUuid);

        //Remove custom FIB routes
        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        for (String extIp : externalIps) {
            delFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, networkUuid, extGwMacAddress, false,
                    writeFlowInvTx);
        }
    }

    protected void clearBgpRoutes(String externalIp, final String vpnName) {
        //Inform BGP about the route removal
        LOG.info("clearBgpRoutes : Informing BGP to remove route for externalIP {} of vpn {}", externalIp, vpnName);
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        NatUtil.removePrefixFromBGP(bgpManager, fibManager, rd, externalIp, vpnName);
        NatUtil.deletePrefixToInterface(dataBroker, NatUtil.getVpnId(dataBroker, vpnName), externalIp);
    }

    private void removeTunnelTableEntry(Uint64 dpnId, Uint32 serviceId,
            TypedReadWriteTransaction<Configuration> writeFlowInvTx) throws ExecutionException, InterruptedException {
        LOG.info("removeTunnelTableEntry : called with DpnId = {} and label = {}", dpnId, serviceId);
        mdsalManager.removeFlow(writeFlowInvTx, dpnId,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), NwConstants.INTERNAL_TUNNEL_TABLE);
        LOG.debug("removeTunnelTableEntry : dpID {} : label : {} removed successfully", dpnId, serviceId);
    }

    private void removeLFibTableEntry(Uint64 dpnId, Uint32 serviceId,
            TypedReadWriteTransaction<Configuration> writeFlowInvTx) throws ExecutionException, InterruptedException {
        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");
        LOG.debug("removeLFibTableEntry : with flow ref {}", flowRef);
        mdsalManager.removeFlow(writeFlowInvTx, dpnId, flowRef, NwConstants.L3_LFIB_TABLE);
        LOG.debug("removeLFibTableEntry : dpID : {} label : {} removed successfully", dpnId, serviceId);
    }

    /**
     * router association to vpn.
     *
     * @param routerName - Name of router
     * @param routerId - router id
     * @param bgpVpnName BGP VPN name
     */
    public void changeLocalVpnIdToBgpVpnId(String routerName, Uint32 routerId, String extNetwork, String bgpVpnName,
            TypedWriteTransaction<Configuration> writeFlowInvTx, ProviderTypes extNwProvType) {
        LOG.debug("changeLocalVpnIdToBgpVpnId : Router associated to BGP VPN");
        if (chkExtRtrAndSnatEnbl(new Uuid(routerName))) {
            Uint32 bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnName);

            LOG.debug("changeLocalVpnIdToBgpVpnId : BGP VPN ID value {} ", bgpVpnId);

            if (bgpVpnId != NatConstants.INVALID_ID) {
                LOG.debug("changeLocalVpnIdToBgpVpnId : Populate the router-id-name container with the "
                        + "mapping BGP VPN-ID {} -> BGP VPN-NAME {}", bgpVpnId, bgpVpnName);
                RouterIds rtrs = new RouterIdsBuilder().withKey(new RouterIdsKey(bgpVpnId))
                    .setRouterId(bgpVpnId).setRouterName(bgpVpnName).build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getRoutersIdentifier(bgpVpnId), rtrs);

                // Get the allocated Primary NAPT Switch for this router
                LOG.debug("changeLocalVpnIdToBgpVpnId : Update the Router ID {} to the BGP VPN ID {} ",
                        routerId, bgpVpnId);
                addDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, bgpVpnId, writeFlowInvTx);

                // Get the group ID
                Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
                installFlowsWithUpdatedVpnId(primarySwitchId, routerName, bgpVpnId, routerId, new Uuid(extNetwork),
                    true, writeFlowInvTx, extNwProvType);
            }
        }
    }

    /**
     * router disassociation from vpn.
     *
     * @param routerName - Name of router
     * @param routerId - router id
     * @param bgpVpnName BGP VPN name
     */
    public void changeBgpVpnIdToLocalVpnId(String routerName, Uint32 routerId, String bgpVpnName, String extNetwork,
            TypedWriteTransaction<Configuration> writeFlowInvTx, ProviderTypes extNwProvType) {
        LOG.debug("changeBgpVpnIdToLocalVpnId : Router dissociated from BGP VPN");
        if (chkExtRtrAndSnatEnbl(new Uuid(routerName))) {
            Uint32 bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnName);
            LOG.debug("changeBgpVpnIdToLocalVpnId : BGP VPN ID value {} ", bgpVpnId);

            // Get the allocated Primary NAPT Switch for this router
            LOG.debug("changeBgpVpnIdToLocalVpnId : Router ID value {} ", routerId);

            LOG.debug("changeBgpVpnIdToLocalVpnId : Update the BGP VPN ID {} to the Router ID {}", bgpVpnId, routerId);
            addDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, NatConstants.INVALID_ID, writeFlowInvTx);

            // Get the group ID
            Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
            installFlowsWithUpdatedVpnId(primarySwitchId, routerName, NatConstants.INVALID_ID, routerId,
                new Uuid(extNetwork), true, writeFlowInvTx, extNwProvType);
        }
    }

    boolean chkExtRtrAndSnatEnbl(Uuid routerUuid) {
        InstanceIdentifier<Routers> routerInstanceIndentifier =
            InstanceIdentifier.builder(ExtRouters.class)
                .child(Routers.class, new RoutersKey(routerUuid.getValue())).build();
        try {
            Optional<Routers> routerData = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, routerInstanceIndentifier);
            return routerData.isPresent() && routerData.get().isEnableSnat();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to read data for router id {}", routerUuid, e);
            return false;
        }
    }

    public void installFlowsWithUpdatedVpnId(Uint64 primarySwitchId, String routerName, Uint32 bgpVpnId,
                                             Uint32 routerId, Uuid extNwUuid, boolean isSnatCfgd,
                                             TypedWriteTransaction<Configuration> confTx, ProviderTypes extNwProvType) {

        Uint32 changedVpnId = bgpVpnId;
        String idType = "BGP VPN";
        if (bgpVpnId == NatConstants.INVALID_ID) {
            changedVpnId = routerId;
            idType = "router";
        }

        List<Uint64> switches = NatUtil.getDpnsForRouter(dataBroker, routerName);
        if (switches.isEmpty()) {
            LOG.error("installFlowsWithUpdatedVpnId : No switches found for router {}", routerName);
            return;
        }
        for (Uint64 dpnId : switches) {
            // Update the BGP VPN ID in the SNAT miss entry to group
            if (!dpnId.equals(primarySwitchId)) {
                LOG.debug("installFlowsWithUpdatedVpnId : Install group in non NAPT switch {}", dpnId);
                List<BucketInfo> bucketInfoForNonNaptSwitches =
                    getBucketInfoForNonNaptSwitches(dpnId, primarySwitchId, routerName, routerId);
                Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME,
                    NatUtil.getGroupIdKey(routerName));
                if (groupId != NatConstants.INVALID_ID) {
                    if (!isSnatCfgd) {
                        installGroup(dpnId, routerName, groupId, bucketInfoForNonNaptSwitches);
                    }
                    LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the SNAT miss entry pointing to group "
                            + "{} in the non NAPT switch {}", idType, changedVpnId, groupId, dpnId);
                    FlowEntity flowEntity = buildSnatFlowEntityWithUpdatedVpnId(dpnId, routerName,
                        groupId, changedVpnId);
                    mdsalManager.addFlow(confTx, flowEntity);
                } else {
                    LOG.error("installFlowsWithUpdatedVpnId: Unable to get groupId for router:{}", routerName);
                }
            } else {
                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the SNAT miss entry pointing to group "
                                + "in the primary switch {}", idType, changedVpnId, primarySwitchId);
                FlowEntity flowEntity =
                    buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch(primarySwitchId, routerName, changedVpnId);
                mdsalManager.addFlow(confTx, flowEntity);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the Terminating Service table (table "
                                + "ID 36) which forwards the packet to the table 46 in the Primary switch {}",
                        idType, changedVpnId, primarySwitchId);
                installTerminatingServiceTblEntryWithUpdatedVpnId(primarySwitchId, routerName, routerId,
                        changedVpnId, confTx, extNwProvType);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the Outbound NAPT table (table ID 46) "
                                + "which punts the packet to the controller in the Primary switch {}",
                        idType, changedVpnId, primarySwitchId);
                createOutboundTblEntryWithBgpVpn(primarySwitchId, routerId, changedVpnId, confTx);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the NAPT PFIB TABLE which forwards the"
                                + " outgoing packet to FIB Table in the Primary switch {}",
                        idType, changedVpnId, primarySwitchId);
                installNaptPfibEntryWithBgpVpn(primarySwitchId, routerId, changedVpnId, confTx);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the NAPT flows for the Outbound NAPT "
                                + "table (table ID 46) and the INBOUND NAPT table (table ID 44) in the Primary switch"
                                + " {}", idType, changedVpnId, primarySwitchId);
                updateNaptFlowsWithVpnId(primarySwitchId, routerName, routerId, bgpVpnId);

                LOG.debug("installFlowsWithUpdatedVpnId : Installing SNAT PFIB flow in the primary switch {}",
                        primarySwitchId);
                //Get the VPN ID from the ExternalNetworks model
                Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, extNwUuid);
                if (vpnUuid != null) {
                    Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
                    //Install the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table
                    // matching on the VPN ID.
                    if (vpnId != null && vpnId != NatConstants.INVALID_ID) {
                        installNaptPfibEntry(primarySwitchId, vpnId, confTx);
                    }
                } else {
                    LOG.error("NAT Service : vpnUuid is null");
                }
            }
        }
    }

    public void updateNaptFlowsWithVpnId(Uint64 dpnId, String routerName, Uint32 routerId, Uint32 bgpVpnId) {
        //For the router ID get the internal IP , internal port and the corresponding external IP and external Port.
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            LOG.error("updateNaptFlowsWithVpnId : Unable to retrieve the IpPortMapping");
            return;
        }
        // Get the External Gateway MAC Address
        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
        if (extGwMacAddress != null) {
            LOG.debug("updateNaptFlowsWithVpnId : External Gateway MAC address {} found for External Router ID {}",
                    extGwMacAddress, routerId);
        } else {
            LOG.error("updateNaptFlowsWithVpnId : No External Gateway MAC address found for External Router ID {}",
                    routerId);
            return;
        }
        for (IntextIpProtocolType intextIpProtocolType : ipPortMapping.nonnullIntextIpProtocolType().values()) {
            for (IpPortMap ipPortMap : intextIpProtocolType.nonnullIpPortMap().values()) {
                String ipPortInternal = ipPortMap.getIpPortInternal();
                String[] ipPortParts = ipPortInternal.split(":");
                if (ipPortParts.length != 2) {
                    LOG.error("updateNaptFlowsWithVpnId : Unable to retrieve the Internal IP and port");
                    return;
                }
                String internalIp = ipPortParts[0];
                String internalPort = ipPortParts[1];
                LOG.debug("updateNaptFlowsWithVpnId : Found Internal IP {} and Internal Port {}",
                        internalIp, internalPort);
                ProtocolTypes protocolTypes = intextIpProtocolType.getProtocol();
                NAPTEntryEvent.Protocol protocol;
                switch (protocolTypes) {
                    case TCP:
                        protocol = NAPTEntryEvent.Protocol.TCP;
                        break;
                    case UDP:
                        protocol = NAPTEntryEvent.Protocol.UDP;
                        break;
                    default:
                        protocol = NAPTEntryEvent.Protocol.TCP;
                }
                SessionAddress internalAddress = new SessionAddress(internalIp, Integer.parseInt(internalPort));
                SessionAddress externalAddress =
                        naptManager.getExternalAddressMapping(routerId, internalAddress, protocol);
                Uint32 internetVpnid = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
                naptEventHandler.buildAndInstallNatFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE, internetVpnid,
                        routerId, bgpVpnId, externalAddress, internalAddress, protocol, extGwMacAddress);
                naptEventHandler.buildAndInstallNatFlows(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, internetVpnid,
                        routerId, bgpVpnId, internalAddress, externalAddress, protocol, extGwMacAddress);
            }
        }
    }

    public FlowEntity buildSnatFlowEntityWithUpdatedVpnId(Uint64 dpId, String routerName, Uint32 groupId,
                                                          Uint32 changedVpnId) {

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnId : called for dpId {}, routerName {} groupId {} "
            + "changed VPN ID {}", dpId, routerName, groupId, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        Uint64 tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, natOverVxlanUtil,
                elanManager, idManager, changedVpnId, routerName);
        actionsInfo.add(new ActionSetFieldTunnelId(tunnelId));
        LOG.debug("buildSnatFlowEntityWithUpdatedVpnId : Setting the tunnel to the list of action infos {}",
                actionsInfo);
        actionsInfo.add(new ActionGroup(groupId.longValue()));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnId : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    public FlowEntity buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch(Uint64 dpId, String routerName,
                                                                        Uint32 changedVpnId) {

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch : called for dpId {}, routerName {} "
                + "changed VPN ID {}", dpId, routerName, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));

        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    // TODO : Replace this with ITM Rpc once its available with full functionality
    protected void installTerminatingServiceTblEntryWithUpdatedVpnId(Uint64 dpnId, String routerName,
                                                                     Uint32 routerId, Uint32 changedVpnId,
                                                                     TypedWriteTransaction<Configuration> confTx,
                                                                     ProviderTypes extNwProvType) {

        LOG.debug("installTerminatingServiceTblEntryWithUpdatedVpnId : called for switch {}, "
            + "routerName {}, BGP VPN ID {}", dpnId, routerName, changedVpnId);
        FlowEntity flowEntity = buildTsFlowEntityWithUpdatedVpnId(dpnId, routerName, routerId, changedVpnId,
                extNwProvType);
        mdsalManager.addFlow(confTx, flowEntity);
    }

    private FlowEntity buildTsFlowEntityWithUpdatedVpnId(Uint64 dpId, String routerName,
                                                         Uint32 routerIdLongVal, Uint32 changedVpnId,
                                                         ProviderTypes extNwProvType) {
        LOG.debug("buildTsFlowEntityWithUpdatedVpnId : called for switch {}, routerName {}, BGP VPN ID {}",
            dpId, routerName, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        Uint64 tunnelId = Uint64.valueOf(changedVpnId);
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            tunnelId = natOverVxlanUtil.getRouterVni(routerName, changedVpnId);
        }
        matches.add(new MatchTunnelId(tunnelId));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));
        Uint32 routerId = routerIdLongVal;
        String flowRef = getFlowRefTs(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
            NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_TS_TABLE, matches, instructions);
        return flowEntity;
    }

    public void createOutboundTblEntryWithBgpVpn(Uint64 dpnId, Uint32 routerId, Uint32 changedVpnId,
                                                 TypedWriteTransaction<Configuration> writeFlowInvTx) {
        LOG.debug("createOutboundTblEntryWithBgpVpn : called for dpId {} and routerId {}, BGP VPN ID {}",
            dpnId, routerId, changedVpnId);
        FlowEntity tcpFlowEntity = buildOutboundFlowEntityWithBgpVpn(dpnId, routerId, changedVpnId,
                NwConstants.IP_PROT_TCP);
        LOG.debug("createOutboundTblEntryWithBgpVpn : Installing tcp flow {}", tcpFlowEntity);
        mdsalManager.addFlow(writeFlowInvTx, tcpFlowEntity);

        FlowEntity udpFlowEntity = buildOutboundFlowEntityWithBgpVpn(dpnId, routerId, changedVpnId,
                NwConstants.IP_PROT_UDP);
        LOG.debug("createOutboundTblEntryWithBgpVpn : Installing udp flow {}", udpFlowEntity);
        mdsalManager.addFlow(writeFlowInvTx, udpFlowEntity);

        FlowEntity icmpDropFlow = buildIcmpDropFlow(dpnId, routerId, changedVpnId);
        LOG.debug("createOutboundTblEntry: Installing icmp drop flow {}", icmpDropFlow);
        mdsalManager.addFlow(writeFlowInvTx, icmpDropFlow);
    }

    protected FlowEntity buildOutboundFlowEntityWithBgpVpn(Uint64 dpId, Uint32 routerId,
                                                           Uint32 changedVpnId, int protocol) {
        LOG.debug("buildOutboundFlowEntityWithBgpVpn : called for dpId {} and routerId {}, BGP VPN ID {}",
            dpId, routerId, changedVpnId);
        Uint64 cookie = getCookieOutboundFlow(routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpProtocol((short)protocol));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        if (snatPuntTimeout != 0) {
            actionsInfos.add(getLearnActionForPunt(protocol, snatPuntTimeout, cookie));
        }
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = getFlowRefOutbound(dpId, NwConstants.OUTBOUND_NAPT_TABLE, routerId, protocol);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.OUTBOUND_NAPT_TABLE,
            flowRef, 5, flowRef, 0, 0, cookie, matches, instructions);
        LOG.debug("createOutboundTblEntryWithBgpVpn : returning flowEntity {}", flowEntity);
        return flowEntity;
    }

    public void installNaptPfibEntryWithBgpVpn(Uint64 dpnId, Uint32 segmentId, Uint32 changedVpnId,
                                               TypedWriteTransaction<Configuration> writeFlowInvTx) {
        LOG.debug("installNaptPfibEntryWithBgpVpn : called for dpnId {} and segmentId {} ,BGP VPN ID {}",
            dpnId, segmentId, changedVpnId);
        FlowEntity naptPfibFlowEntity = buildNaptPfibFlowEntityWithUpdatedVpnId(dpnId, segmentId, changedVpnId);
        mdsalManager.addFlow(writeFlowInvTx, naptPfibFlowEntity);
    }

    public FlowEntity buildNaptPfibFlowEntityWithUpdatedVpnId(Uint64 dpId, Uint32 segmentId, Uint32 changedVpnId) {

        LOG.debug("buildNaptPfibFlowEntityWithUpdatedVpnId : called for dpId {}, "
            + "segmentId {}, BGP VPN ID {}", dpId, segmentId, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(Uint64.valueOf(BigInteger.ZERO)));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRefTs(dpId, NwConstants.NAPT_PFIB_TABLE, segmentId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.NAPT_PFIB_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);
        LOG.debug("buildNaptPfibFlowEntityWithUpdatedVpnId : Returning NaptPFib Flow Entity {}", flowEntity);
        return flowEntity;
    }

    protected void installNaptPfibEntriesForExternalSubnets(String routerName, Uint64 dpnId,
                                                        @Nullable TypedWriteTransaction<Configuration> writeFlowInvTx) {
        Collection<Uuid> externalSubnetIdsForRouter = NatUtil.getExternalSubnetIdsForRouter(dataBroker,
                routerName);
        for (Uuid externalSubnetId : externalSubnetIdsForRouter) {
            Uint32 subnetVpnId = NatUtil.getVpnId(dataBroker, externalSubnetId.getValue());
            if (subnetVpnId != NatConstants.INVALID_ID) {
                LOG.debug("installNaptPfibEntriesForExternalSubnets : called for dpnId {} "
                    + "and vpnId {}", dpnId, subnetVpnId);
                installNaptPfibEntry(dpnId, subnetVpnId, writeFlowInvTx);
            }
        }
    }
}
