/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
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
import org.opendaylight.genius.mdsalutil.UpgradeState;
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
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
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
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalRoutersListener extends AsyncDataTreeChangeListenerBase<Routers, ExternalRoutersListener> {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalRoutersListener.class);

    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    private static final BigInteger COOKIE_VM_LFIB_TABLE = new BigInteger("8000022", 16);

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
    private final CentralizedSwitchScheduler  centralizedSwitchScheduler;
    private final NatMode natMode;
    private final INeutronVpnManager nvpnManager;
    private final IElanService elanManager;
    private final JobCoordinator coordinator;
    private final UpgradeState upgradeState;
    private final IInterfaceManager interfaceManager;
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
                                   final INeutronVpnManager nvpnManager,
                                   final CentralizedSwitchScheduler centralizedSwitchScheduler,
                                   final NatserviceConfig config,
                                   final IElanService elanManager,
                                   final JobCoordinator coordinator,
                                   final UpgradeState upgradeState,
                                   final IInterfaceManager interfaceManager) {
        super(Routers.class, ExternalRoutersListener.class);
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
        this.nvpnManager = nvpnManager;
        this.elanManager = elanManager;
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
        this.coordinator = coordinator;
        this.upgradeState = upgradeState;
        this.interfaceManager = interfaceManager;
        if (config != null) {
            this.natMode = config.getNatMode();
            this.snatPuntTimeout = config.getSnatPuntTimeout().intValue();
        } else {
            this.natMode = NatMode.Controller;
            this.snatPuntTimeout = 0;
        }
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        createGroupIdPool();
    }

    @Override
    protected InstanceIdentifier<Routers> getWildCardPath() {
        return InstanceIdentifier.create(ExtRouters.class).child(Routers.class);
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Routers> identifier, Routers routers) {
        // Populate the router-id-name container
        String routerName = routers.getRouterName();
        LOG.info("add : external router event for {}", routerName);
        long routerId = NatUtil.getVpnId(dataBroker, routerName);
        NatUtil.createRouterIdsConfigDS(dataBroker, routerId, routerName);
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (natMode == NatMode.Conntrack && !upgradeState.isUpgradeInProgress()) {
            if (bgpVpnUuid != null) {
                return;
            }
            List<ExternalIps> externalIps = routers.getExternalIps();
            // Allocate Primary Napt Switch for this router
            if (routers.isEnableSnat() && externalIps != null && !externalIps.isEmpty()) {
                centralizedSwitchScheduler.scheduleCentralizedSwitch(routers);
            }
            //snatServiceManger.notify(routers, null, Action.ADD);
        } else {
            try {
                coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + routers.key(),
                    () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                        LOG.info("add : Installing NAT default route on all dpns part of router {}", routerName);
                        long bgpVpnId = NatConstants.INVALID_ID;
                        if (bgpVpnUuid != null) {
                            bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                        }
                        addOrDelDefFibRouteToSNAT(routerName, routerId, bgpVpnId, bgpVpnUuid, true, tx);
                        // Allocate Primary Napt Switch for this router
                        BigInteger primarySwitchId = getPrimaryNaptSwitch(routerName);
                        if (primarySwitchId != null && !primarySwitchId.equals(BigInteger.ZERO)) {
                            if (!routers.isEnableSnat()) {
                                LOG.info("add : SNAT is disabled for external router {} ", routerName);
                                /* If SNAT is disabled on ext-router though L3_FIB_TABLE(21) -> PSNAT_TABLE(26) flow
                                 * is required for DNAT. Hence writeFlowInvTx object submit is required.
                                 */
                                return;
                            }
                            handleEnableSnat(routers, routerId, primarySwitchId, bgpVpnId, tx);
                        }
                    })), NatConstants.NAT_DJC_MAX_RETRIES);
            } catch (Exception ex) {
                LOG.error("add : Exception while Installing NAT flows on all dpns as part of router {}",
                        routerName, ex);
            }
        }
    }

    public void handleEnableSnat(Routers routers, long routerId, BigInteger primarySwitchId, long bgpVpnId,
                                 WriteTransaction writeFlowInvTx) {
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
            installFlowsWithUpdatedVpnId(primarySwitchId, routerName, bgpVpnId, routerId, false, writeFlowInvTx,
                    extNwProvType);
        } else {
            // write metadata and punt
            installOutboundMissEntry(routerName, routerId, primarySwitchId, writeFlowInvTx);
            // Now install entries in SNAT tables to point to Primary for each router
            List<BigInteger> switches = naptSwitchSelector.getDpnsForVpn(routerName);
            for (BigInteger dpnId : switches) {
                // Handle switches and NAPT switches separately
                if (!dpnId.equals(primarySwitchId)) {
                    LOG.debug("handleEnableSnat : Handle Ordinary switch");
                    handleSwitches(dpnId, routerName, routerId, primarySwitchId);
                } else {
                    LOG.debug("handleEnableSnat : Handle NAPT switch");
                    handlePrimaryNaptSwitch(dpnId, routerName, routerId, writeFlowInvTx);
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
                handleSnatReverseTraffic(primarySwitchId, routers, routerId, routerName, externalIpAddrPrefix,
                        writeFlowInvTx);
            }
        }
        LOG.debug("handleEnableSnat : Exit");
    }

    private BigInteger getPrimaryNaptSwitch(String routerName) {
        // Allocate Primary Napt Switch for this router
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        if (primarySwitchId != null && !primarySwitchId.equals(BigInteger.ZERO)) {
            LOG.debug("handleEnableSnat : Primary NAPT switch with DPN ID {} is already elected for router {}",
                primarySwitchId, routerName);
            return primarySwitchId;
        }
        // Validating and creating VNI pool during when NAPT switch is selected.
        // With Assumption this might be the first NAT service comes up.
        NatOverVxlanUtil.validateAndCreateVxlanVniPool(dataBroker, nvpnManager, idManager,
                NatConstants.ODL_VNI_POOL_NAME);
        // Allocated an id from VNI pool for the Router.
        NatOverVxlanUtil.getRouterVni(idManager, routerName, NatConstants.INVALID_ID);
        primarySwitchId = naptSwitchSelector.selectNewNAPTSwitch(routerName);
        LOG.debug("handleEnableSnat : Primary NAPT switch DPN ID {}", primarySwitchId);

        return primarySwitchId;
    }

    protected void installNaptPfibExternalOutputFlow(String routerName, Long routerId, BigInteger dpnId,
                                                     WriteTransaction writeFlowInvTx) {
        Long extVpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
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
                long subnetVpnId = NatUtil.getExternalSubnetVpnId(dataBroker, subnetId);
                if (subnetVpnId != NatConstants.INVALID_ID) {
                    extVpnId = subnetVpnId;
                }
                LOG.debug("installNaptPfibExternalOutputFlow - dpnId {} extVpnId {} subnetId {}",
                    dpnId, extVpnId, subnetId);
                FlowEntity postNaptFlowEntity = buildNaptPfibFlowEntity(dpnId, extVpnId);
                mdsalManager.addFlowToTx(postNaptFlowEntity, writeFlowInvTx);
            }
        }
    }

    private Uuid getSubnetIdForFixedIp(String ip) {
        if (ip != null) {
            IpAddress externalIpv4Address = new IpAddress(new Ipv4Address(ip));
            Port port = NatUtil.getNeutronPortForRouterGetewayIp(dataBroker, externalIpv4Address);
            Uuid subnetId = NatUtil.getSubnetIdForFloatingIp(port, externalIpv4Address);
            return subnetId;
        }
        LOG.error("getSubnetIdForFixedIp : ip is null");
        return null;
    }

    protected void subnetRegisterMapping(Routers routerEntry, Long segmentId) {
        List<Uuid> subnetList = null;
        List<String> externalIps = null;
        LOG.debug("subnetRegisterMapping : Fetching values from extRouters model");
        subnetList = routerEntry.getSubnetIds();
        externalIps = NatUtil.getIpsListFromExternalIps(routerEntry.getExternalIps());
        int counter = 0;
        int extIpCounter = externalIps.size();
        LOG.debug("subnetRegisterMapping : counter values before looping counter {} and extIpCounter {}",
                counter, extIpCounter);
        for (Uuid subnet : subnetList) {
            LOG.debug("subnetRegisterMapping : Looping internal subnets for subnet {}", subnet);
            InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier
                .builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnet))
                .build();
            Optional<Subnetmap> sn = read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetmapId);
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

    private void addOrDelDefFibRouteToSNAT(String routerName, long routerId, long bgpVpnId,
            Uuid bgpVpnUuid, boolean create, WriteTransaction writeFlowInvTx) {
        //Check if BGP VPN exists. If exists then invoke the new method.
        if (bgpVpnId != NatConstants.INVALID_ID) {
            if (bgpVpnUuid != null) {
                String bgpVpnName = bgpVpnUuid.getValue();
                LOG.debug("Populate the router-id-name container with the mapping BGP VPN-ID {} -> BGP VPN-NAME {}",
                    bgpVpnId, bgpVpnName);
                RouterIds rtrs = new RouterIdsBuilder().withKey(new RouterIdsKey(bgpVpnId))
                    .setRouterId(bgpVpnId).setRouterName(bgpVpnName).build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getRoutersIdentifier(bgpVpnId), rtrs);
            }
            addOrDelDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, bgpVpnId, create, writeFlowInvTx);
            return;
        }

        //Router ID is used as the internal VPN's name, hence the vrf-id in VpnInstance Op DataStore
        addOrDelDefaultFibRouteForSNAT(routerName, routerId, create, writeFlowInvTx);
    }

    private void addOrDelDefaultFibRouteForSNAT(String routerName, long routerId, boolean create,
            WriteTransaction writeFlowInvTx) {
        List<BigInteger> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        if (switches.isEmpty()) {
            LOG.info("addOrDelDefaultFibRouteForSNAT : No switches found for router {}", routerName);
            return;
        }
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("addOrDelDefaultFibRouteForSNAT : Could not retrieve router Id for {} to program "
                    + "default NAT route in FIB", routerName);
            return;
        }
        for (BigInteger dpnId : switches) {
            if (create) {
                LOG.debug("addOrDelDefaultFibRouteForSNAT : installing default NAT route for router {} in dpn {} "
                        + "for the internal vpn-id {}", routerId, dpnId, routerId);
                defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, routerId, writeFlowInvTx);
            } else {
                LOG.debug("addOrDelDefaultFibRouteForSNAT : removing default NAT route for router {} in dpn {} "
                        + "for the internal vpn-id {}", routerId, dpnId, routerId);
                defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, routerId, writeFlowInvTx);
            }
        }
    }

    private void addOrDelDefaultFibRouteForSnatWithBgpVpn(String routerName, long routerId,
            long bgpVpnId, boolean create,WriteTransaction writeFlowInvTx) {
        List<BigInteger> dpnIds = NatUtil.getDpnsForRouter(dataBroker, routerName);
        if (dpnIds.isEmpty()) {
            LOG.error("addOrDelDefaultFibRouteForSNATWIthBgpVpn: No dpns are part of router {} to program "
                    + "default NAT flows for BGP-VPN {}", routerName, bgpVpnId);
            return;
        }
        for (BigInteger dpnId : dpnIds) {
            if (create) {
                if (bgpVpnId != NatConstants.INVALID_ID) {
                    LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : installing default NAT route for router {} "
                            + "in dpn {} for the BGP vpnID {}", routerId, dpnId, bgpVpnId);
                    defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, bgpVpnId, routerId, writeFlowInvTx);
                } else {
                    LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : installing default NAT route for router {} "
                            + "in dpn {} for the internal vpn", routerId, dpnId);
                    defaultRouteProgrammer.installDefNATRouteInDPN(dpnId, routerId,writeFlowInvTx);
                }
            } else {
                if (bgpVpnId != NatConstants.INVALID_ID) {
                    LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : removing default NAT route for router {} "
                            + "in dpn {} for the BGP vpnID {}", routerId, dpnId, bgpVpnId);
                    defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, bgpVpnId, routerId, writeFlowInvTx);
                } else {
                    LOG.debug("addOrDelDefaultFibRouteForSnatWithBgpVpn : removing default NAT route for router {} "
                            + "in dpn {} for the internal vpn", routerId, dpnId);
                    defaultRouteProgrammer.removeDefNATRouteInDPN(dpnId, routerId, writeFlowInvTx);
                }
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        try {
            return tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void installOutboundMissEntry(String routerName, long routerId, BigInteger primarySwitchId,
                                            WriteTransaction writeFlowInvTx) {
        LOG.debug("installOutboundMissEntry : Router ID from getVpnId {}", routerId);
        if (routerId != NatConstants.INVALID_ID) {
            LOG.debug("installOutboundMissEntry : Creating miss entry on primary {}, for router {}",
                    primarySwitchId, routerId);
            createOutboundTblEntry(primarySwitchId, routerId, writeFlowInvTx);
        } else {
            LOG.error("installOutboundMissEntry : Unable to fetch Router Id  for RouterName {}, failed to "
                + "createAndInstallMissEntry", routerName);
        }
    }

    public String getFlowRefOutbound(BigInteger dpnId, short tableId, long routerID, int protocol) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID + NatConstants.FLOWID_SEPARATOR + protocol;
    }

    private String getFlowRefNaptPreFib(BigInteger dpnId, short tableId, long vpnId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + vpnId;
    }

    public BigInteger getCookieOutboundFlow(long routerId) {
        return NwConstants.COOKIE_OUTBOUND_NAPT_TABLE.add(new BigInteger("0110001", 16)).add(
            BigInteger.valueOf(routerId));
    }

    private ActionLearn getLearnActionForPunt(int protocol, int hardTimeout, BigInteger cookie) {
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

    private FlowEntity buildIcmpDropFlow(BigInteger dpnId, long routerId, long vpnId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(MatchIpProtocol.ICMP);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

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

    protected FlowEntity buildOutboundFlowEntity(BigInteger dpId, long routerId, int protocol) {
        LOG.debug("buildOutboundFlowEntity : called for dpId {} and routerId{}", dpId, routerId);
        BigInteger cookie = getCookieOutboundFlow(routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpProtocol((short)protocol));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

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

    public void createOutboundTblEntry(BigInteger dpnId, long routerId, WriteTransaction writeFlowInvTx) {
        LOG.debug("createOutboundTblEntry : called for dpId {} and routerId {}", dpnId, routerId);
        FlowEntity tcpflowEntity = buildOutboundFlowEntity(dpnId, routerId, NwConstants.IP_PROT_TCP);
        LOG.debug("createOutboundTblEntry : Installing tcp flow {}", tcpflowEntity);
        mdsalManager.addFlowToTx(tcpflowEntity, writeFlowInvTx);

        FlowEntity udpflowEntity = buildOutboundFlowEntity(dpnId, routerId, NwConstants.IP_PROT_UDP);
        LOG.debug("createOutboundTblEntry : Installing udp flow {}", udpflowEntity);
        mdsalManager.addFlowToTx(udpflowEntity, writeFlowInvTx);

        FlowEntity icmpDropFlow = buildIcmpDropFlow(dpnId, routerId, routerId);
        LOG.debug("createOutboundTblEntry: Installing icmp drop flow {}", icmpDropFlow);
        mdsalManager.addFlowToTx(icmpDropFlow, writeFlowInvTx);
    }

    protected String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
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

    protected void installSnatMissEntryForPrimrySwch(BigInteger dpnId, String routerName, long routerId,
                                                    WriteTransaction writeFlowInvTx) {
        LOG.debug("installSnatMissEntry : called for for the primary NAPT switch dpnId {} ", dpnId);
        // Install miss entry pointing to group
        FlowEntity flowEntity = buildSnatFlowEntityForPrmrySwtch(dpnId, routerName, routerId);
        mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
    }

    protected void installSnatMissEntry(BigInteger dpnId, List<BucketInfo> bucketInfo,
            String routerName, long routerId) {
        LOG.debug("installSnatMissEntry : called for dpnId {} with primaryBucket {} ",
            dpnId, bucketInfo.get(0));
        // Install the select group
        long groupId = createGroupId(getGroupIdKey(routerName));
        GroupEntity groupEntity =
            MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll, bucketInfo);
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

    long installGroup(BigInteger dpnId, String routerName, List<BucketInfo> bucketInfo) {
        long groupId = createGroupId(getGroupIdKey(routerName));
        GroupEntity groupEntity =
            MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll, bucketInfo);
        LOG.debug("installGroup : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
        mdsalManager.syncInstallGroup(groupEntity);
        return groupId;
    }

    private FlowEntity buildSnatFlowEntity(BigInteger dpId, String routerName, long routerId, long groupId) {
        LOG.debug("buildSnatFlowEntity : called for dpId {}, routerName {} and groupId {}",
                dpId, routerName, groupId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        long tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, elanManager, idManager, routerId,
                routerName);
        actionsInfo.add(new ActionSetFieldTunnelId(BigInteger.valueOf(tunnelId)));
        LOG.debug("buildSnatFlowEntity : Setting the tunnel to the list of action infos {}", actionsInfo);
        actionsInfo.add(new ActionGroup(groupId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("buildSnatFlowEntity : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    private FlowEntity buildSnatFlowEntityForPrmrySwtch(BigInteger dpId, String routerName, long routerId) {

        LOG.debug("buildSnatFlowEntityForPrmrySwtch : called for primary NAPT switch dpId {}, routerName {}", dpId,
            routerName);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

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
    protected void installTerminatingServiceTblEntry(BigInteger dpnId, String routerName, long routerId,
                                                     WriteTransaction writeFlowInvTx) {
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
        mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);

    }

    private FlowEntity buildTsFlowEntity(BigInteger dpId, String routerName, long routerId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        long tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, elanManager, idManager, routerId,
                routerName);
        matches.add(new MatchTunnelId(BigInteger.valueOf(tunnelId)));
        String flowRef = getFlowRefTs(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, tunnelId);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(routerId),
                MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, 0, 0, NwConstants.COOKIE_TS_TABLE, matches,
                instructions);
        return flowEntity;
    }

    public String getFlowRefTs(BigInteger dpnId, short tableId, long routerID) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }

    public static String getFlowRefSnat(BigInteger dpnId, short tableId, String routerID) {
        return NatConstants.SNAT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }

    private String getGroupIdKey(String routerName) {
        return "snatmiss." + routerName;
    }

    protected long createGroupId(String groupIdKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NatConstants.SNAT_IDPOOL_NAME).setIdKey(groupIdKey)
            .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Exception While allocating id for group: {}", groupIdKey, e);
        }
        return 0;
    }

    protected void createGroupIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(NatConstants.SNAT_IDPOOL_NAME)
            .setLow(NatConstants.SNAT_ID_LOW_VALUE)
            .setHigh(NatConstants.SNAT_ID_HIGH_VALUE)
            .build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("createGroupIdPool : GroupIdPool created successfully");
            } else {
                LOG.error("createGroupIdPool : Unable to create GroupIdPool");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("createGroupIdPool : Failed to create PortPool for NAPT Service", e);
        }
    }

    protected void handleSwitches(BigInteger dpnId, String routerName, long routerId, BigInteger primarySwitchId) {
        LOG.debug("handleSwitches : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName(dpnId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<>();

        if (ifNamePrimary != null) {
            LOG.debug("handleSwitches : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId);
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

    List<BucketInfo> getBucketInfoForNonNaptSwitches(BigInteger nonNaptSwitchId,
                                                     BigInteger primarySwitchId, String routerName, long routerId) {
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName(nonNaptSwitchId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<>();

        if (ifNamePrimary != null) {
            LOG.debug("getBucketInfoForNonNaptSwitches : On Non- Napt switch , Primary Tunnel interface is {}",
                    ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId);
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

    protected void handlePrimaryNaptSwitch(BigInteger dpnId, String routerName, long routerId,
            WriteTransaction writeFlowInvTx) {
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

        installSnatMissEntryForPrimrySwch(dpnId, routerName, routerId, writeFlowInvTx);
        installTerminatingServiceTblEntry(dpnId, routerName, routerId, writeFlowInvTx);
        //Install the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table matching on the router ID.
        installNaptPfibEntry(dpnId, routerId, writeFlowInvTx);
        Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
        if (networkId != null) {
            Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
            if (vpnUuid != null) {
                Long vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
                coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + networkId, () -> {
                    installNaptPfibEntriesForExternalSubnets(routerName, dpnId, null);
                    //Install the NAPT PFIB TABLE which forwards outgoing packet to FIB Table matching on the VPN ID.
                    if (vpnId != null && vpnId != NatConstants.INVALID_ID) {
                        installNaptPfibEntry(dpnId, vpnId, null);
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

    List<BucketInfo> getBucketInfoForPrimaryNaptSwitch() {
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        listActionInfoPrimary.add(new ActionNxResubmit(NwConstants.INTERNAL_TUNNEL_TABLE));
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);
        return listBucketInfo;
    }

    public void installNaptPfibEntry(BigInteger dpnId, long segmentId, WriteTransaction writeFlowInvTx) {
        LOG.debug("installNaptPfibEntry : called for dpnId {} and segmentId {} ", dpnId, segmentId);
        FlowEntity naptPfibFlowEntity = buildNaptPfibFlowEntity(dpnId, segmentId);
        if (writeFlowInvTx != null) {
            mdsalManager.addFlowToTx(naptPfibFlowEntity, writeFlowInvTx);
        } else {
            mdsalManager.installFlow(naptPfibFlowEntity);
        }
    }

    public FlowEntity buildNaptPfibFlowEntity(BigInteger dpId, long segmentId) {

        LOG.debug("buildNaptPfibFlowEntity : called for dpId {}, segmentId {}", dpId, segmentId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRefTs(dpId, NwConstants.NAPT_PFIB_TABLE, segmentId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.NAPT_PFIB_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);
        LOG.debug("buildNaptPfibFlowEntity : Returning NaptPFib Flow Entity {}", flowEntity);
        return flowEntity;
    }

    public void handleSnatReverseTraffic(BigInteger dpnId, Routers router, long routerId, String routerName,
            String externalIp, WriteTransaction writeFlowInvTx) {
        LOG.debug("handleSnatReverseTraffic : entry for DPN ID {}, routerId {}, externalIp: {}",
            dpnId, routerId, externalIp);
        Uuid networkId = router.getNetworkId();
        if (networkId == null) {
            LOG.error("handleSnatReverseTraffic : networkId is null for the router ID {}", routerId);
            return;
        }
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId);
        if (vpnName == null) {
            LOG.error("handleSnatReverseTraffic : No VPN associated with ext nw {} to handle add external ip "
                    + "configuration {} in router {}", networkId, externalIp, routerId);
            return;
        }
        advToBgpAndInstallFibAndTsFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE, vpnName, routerId, routerName,
            externalIp, networkId, router, writeFlowInvTx);
        LOG.debug("handleSnatReverseTraffic : exit for DPN ID {}, routerId {}, externalIp : {}",
            dpnId, routerId, externalIp);
    }

    public void advToBgpAndInstallFibAndTsFlows(final BigInteger dpnId, final short tableId, final String vpnName,
                                                final long routerId, final String routerName, final String externalIp,
                                                final Uuid extNetworkId, final Routers router,
                                                final WriteTransaction writeFlowInvTx) {
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
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                tx -> evpnSnatFlowProgrammer.evpnAdvToBgpAndInstallFibAndTsFlows(dpnId, tableId, externalIp,
                        vpnName, rd, nextHopIp, tx, routerId, routerName, writeFlowInvTx)), LOG,
                "Error installing FIB and TS flows");
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
                final long label = output.getLabel();

                int externalIpInDsFlag = 0;
                //Get IPMaps from the DB for the router ID
                List<IpMap> dbIpMaps = NaptManager.getIpMapList(dataBroker, routerId);
                if (dbIpMaps != null) {
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
                } else {
                    LOG.error("advToBgpAndInstallFibAndTsFlows : Failed to write label {} for externalIp {} for"
                            + " routerId {} in DS", label, externalIp, routerId);
                }
                //Inform BGP
                long l3vni = 0;
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    l3vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, l3vni).longValue();
                }
                Routers extRouter = router != null ? router :
                    NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
                Uuid externalSubnetId = NatUtil.getExternalSubnetForRouterExternalIp(externalIp,
                        extRouter);
                NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd, externalSubnetId,
                    externalIp, nextHopIp, extRouter.getNetworkId().getValue(), null, label, l3vni,
                    RouteOrigin.STATIC, dpnId);

                //Install custom FIB routes
                List<Instruction> tunnelTableCustomInstructions = new ArrayList<>();
                tunnelTableCustomInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
                makeTunnelTableEntry(dpnId, label, l3vni, tunnelTableCustomInstructions, writeFlowInvTx,
                        extNwProvType);
                makeLFibTableEntry(dpnId, label, tableId, writeFlowInvTx);

                //Install custom FIB routes - FIB table.
                List<Instruction> fibTableCustomInstructions = createFibTableCustomInstructions(tableId,
                        routerName, externalIp);
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
                    //Install the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
                    NatUtil.makePreDnatToSnatTableEntry(mdsalManager, dpnId,
                            NwConstants.INBOUND_NAPT_TABLE,writeFlowInvTx);
                }
                String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
                Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker,
                        externalSubnetId);
                String externalVpn = vpnName;
                if (externalSubnet.isPresent()) {
                    externalVpn =  externalSubnetId.getValue();
                }
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
            public void onFailure(@Nonnull Throwable error) {
                LOG.error("advToBgpAndInstallFibAndTsFlows : Error in generate label or fib install process", error);
            }

            @Override
            public void onSuccess(@Nonnull RpcResult<CreateFibEntryOutput> result) {
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
        long externalSubnetVpnId = NatUtil.getExternalSubnetVpnIdForRouterExternalIp(dataBroker,
                externalIp, router);
        int instructionIndex = 0;
        if (externalSubnetVpnId != NatConstants.INVALID_ID) {
            BigInteger subnetIdMetaData = MetaDataUtil.getVpnIdMetadata(externalSubnetVpnId);
            fibTableCustomInstructions.add(new InstructionWriteMetadata(subnetIdMetaData,
                    MetaDataUtil.METADATA_MASK_VRFID).buildInstruction(instructionIndex));
            instructionIndex++;
        }

        fibTableCustomInstructions.add(new InstructionGotoTable(tableId).buildInstruction(instructionIndex));
        return fibTableCustomInstructions;
    }

    private void makeLFibTableEntry(BigInteger dpId, long serviceId, short tableId,
                                    WriteTransaction writeFlowInvTx) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId));

        List<Instruction> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        //NAT is required for IPv4 only. Hence always etherType will be IPv4
        actionsInfos.add(new ActionPopMpls(NwConstants.ETHTYPE_IPV4));
        Instruction writeInstruction = new InstructionApplyActions(actionsInfos).buildInstruction(0);
        instructions.add(writeInstruction);
        instructions.add(new InstructionGotoTable(tableId).buildInstruction(1));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            COOKIE_VM_LFIB_TABLE, matches, instructions);

        mdsalManager.addFlowToTx(dpId, flowEntity, writeFlowInvTx);

        LOG.debug("makeLFibTableEntry : LFIB Entry for dpID {} : label : {} modified successfully", dpId, serviceId);
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long serviceId, long l3Vni,
             List<Instruction> customInstructions, WriteTransaction writeFlowInvTx, ProviderTypes extNwProvType) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.debug("makeTunnelTableEntry : DpnId = {} and serviceId = {} and actions = {}",
            dpnId, serviceId, customInstructions);

        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        } else {
            mkMatches.add(new MatchTunnelId(BigInteger.valueOf(serviceId)));
        }

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), 5,
            String.format("%s:%d", "TST Flow Entry ", serviceId),
            0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, customInstructions);

        mdsalManager.addFlowToTx(dpnId, terminatingServiceTableFlowEntity, writeFlowInvTx);
    }

    protected InstanceIdentifier<RouterIds> getRoutersIdentifier(long routerId) {
        InstanceIdentifier<RouterIds> id = InstanceIdentifier.builder(
            RouterIdName.class).child(RouterIds.class, new RouterIdsKey(routerId)).build();
        return id;
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return NatConstants.SNAT_FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants
                .FLOWID_SEPARATOR + id + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    @Override
    protected void update(InstanceIdentifier<Routers> identifier, Routers original, Routers update) {
        String routerName = original.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("update : external router event - Invalid routerId for routerName {}", routerName);
            return;
        }
        // Check if its update on SNAT flag
        boolean originalSNATEnabled = original.isEnableSnat();
        boolean updatedSNATEnabled = update.isEnableSnat();
        LOG.debug("update :called with originalFlag and updatedFlag for SNAT enabled "
            + "as {} and {}", originalSNATEnabled, updatedSNATEnabled);
        if (natMode == NatMode.Conntrack && !upgradeState.isUpgradeInProgress()) {
            if (originalSNATEnabled != updatedSNATEnabled) {
                BigInteger primarySwitchId;
                if (originalSNATEnabled) {
                    //SNAT disabled for the router
                    centralizedSwitchScheduler.releaseCentralizedSwitch(update);
                } else {
                    centralizedSwitchScheduler.scheduleCentralizedSwitch(update);
                }
            } else if (updatedSNATEnabled) {
                centralizedSwitchScheduler.updateCentralizedSwitch(original,update);
            }
            List<ExternalIps> originalExternalIps = original.getExternalIps();
            List<ExternalIps> updateExternalIps = update.getExternalIps();
            if (!Objects.equals(originalExternalIps, updateExternalIps)) {
                if (originalExternalIps == null || originalExternalIps.isEmpty()) {
                    centralizedSwitchScheduler.scheduleCentralizedSwitch(update);
                }
            }
        } else {
            /* Get Primary Napt Switch for existing router from "router-to-napt-switch" DS.
             * if dpnId value is null or zero then go for electing new Napt switch for existing router.
             */
            long bgpVpnId = NatConstants.INVALID_ID;
            Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
            if (bgpVpnUuid != null) {
                bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
            }
            BigInteger dpnId = getPrimaryNaptSwitch(routerName);
            if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
                // Router has no interface attached
                return;
            }
            final long finalBgpVpnId = bgpVpnId;
            coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + update.key(), () -> {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeFlowInvTx -> {
                    futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(removeFlowInvTx -> {
                        Uuid networkId = original.getNetworkId();
                        if (originalSNATEnabled != updatedSNATEnabled) {
                            if (originalSNATEnabled) {
                                //SNAT disabled for the router
                                Uuid networkUuid = original.getNetworkId();
                                LOG.info("update : SNAT disabled for Router {}", routerName);
                                Collection<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker, routerId);
                                handleDisableSnat(original, networkUuid, externalIps, false, null, dpnId, routerId,
                                        removeFlowInvTx);
                            } else {
                                LOG.info("update : SNAT enabled for Router {}", original.getRouterName());
                                handleEnableSnat(original, routerId, dpnId, finalBgpVpnId, removeFlowInvTx);
                            }
                        }
                        if (!Objects.equals(original.getExtGwMacAddress(), update.getExtGwMacAddress())) {
                            NatUtil.installRouterGwFlows(txRunner, vpnManager, original, dpnId, NwConstants.DEL_FLOW);
                            NatUtil.installRouterGwFlows(txRunner, vpnManager, update, dpnId, NwConstants.ADD_FLOW);
                        }

                        //Check if the Update is on External IPs
                        LOG.debug("update : Checking if this is update on External IPs");
                        List<String> originalExternalIps = NatUtil.getIpsListFromExternalIps(original.getExternalIps());
                        List<String> updatedExternalIps = NatUtil.getIpsListFromExternalIps(update.getExternalIps());

                        //Check if the External IPs are added during the update.
                        Set<String> addedExternalIps = new HashSet<>(updatedExternalIps);
                        addedExternalIps.removeAll(originalExternalIps);
                        if (addedExternalIps.size() != 0) {
                            LOG.debug("update : Start processing of the External IPs addition during the update "
                                    + "operation");
                            vpnManager.addArpResponderFlowsToExternalNetworkIps(routerName, addedExternalIps,
                                    update.getExtGwMacAddress(), dpnId,
                                    update.getNetworkId(), null);

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
                            }
                            LOG.debug(
                                    "update : End processing of the External IPs addition during the update operation");
                        }

                        //Check if the External IPs are removed during the update.
                        Set<String> removedExternalIps = new HashSet<>(originalExternalIps);
                        removedExternalIps.removeAll(updatedExternalIps);
                        if (removedExternalIps.size() > 0) {
                            LOG.debug("update : Start processing of the External IPs removal during the update "
                                    + "operation");
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
                                List<Integer> externalPorts = new ArrayList<>();
                                Map<ProtocolTypes, List<String>> protoTypesIntIpPortsMap = new HashMap<>();
                                InstanceIdentifier<IpPortMapping> ipPortMappingId = InstanceIdentifier
                                        .builder(IntextIpPortMap.class)
                                        .child(IpPortMapping.class, new IpPortMappingKey(routerId)).build();
                                Optional<IpPortMapping> ipPortMapping =
                                        MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, ipPortMappingId);
                                if (ipPortMapping.isPresent()) {
                                    List<IntextIpProtocolType> intextIpProtocolTypes = ipPortMapping.get()
                                            .getIntextIpProtocolType();
                                    for (IntextIpProtocolType intextIpProtocolType : intextIpProtocolTypes) {
                                        ProtocolTypes protoType = intextIpProtocolType.getProtocol();
                                        List<IpPortMap> ipPortMaps = intextIpProtocolType.getIpPortMap();
                                        for (IpPortMap ipPortMap : ipPortMaps) {
                                            IpPortExternal ipPortExternal = ipPortMap.getIpPortExternal();
                                            if (ipPortExternal.getIpAddress().equals(externalIp)) {
                                                externalPorts.add(ipPortExternal.getPortNum());
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
                                            } else {
                                                removedInternalPortsList = new ArrayList<>();
                                                removedInternalPortsList.add(removedInternalPort);
                                                internalIpPortMap.put(removedInternalIp, removedInternalPortsList);
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

                                LOG.debug("update : Remove the NAPT translation entries from Inbound NAPT tables for "
                                        + "the removed external IP {}", externalIp);
                                for (Integer externalPort : externalPorts) {
                                    //Remove the NAPT translation entries from Inbound NAPT table
                                    naptEventHandler.removeNatFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE,
                                            routerId, externalIp, externalPort);
                                }

                                Set<Map.Entry<String, List<String>>> internalIpPorts = internalIpPortMap.entrySet();
                                for (Map.Entry<String, List<String>> internalIpPort : internalIpPorts) {
                                    String internalIp = internalIpPort.getKey();
                                    LOG.debug("update : Remove the NAPT translation entries from Outbound NAPT tables "
                                            + "for the removed internal IP {}", internalIp);
                                    List<String> internalPorts = internalIpPort.getValue();
                                    for (String internalPort : internalPorts) {
                                        //Remove the NAPT translation entries from Outbound NAPT table
                                        naptPacketInHandler.removeIncomingPacketMap(
                                                routerId + NatConstants.COLON_SEPARATOR + internalIp
                                                        + NatConstants.COLON_SEPARATOR + internalPort);
                                        naptEventHandler.removeNatFlows(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
                                                routerId, internalIp, Integer.parseInt(internalPort));
                                    }
                                }
                            }
                            LOG.debug(
                                    "update : End processing of the External IPs removal during the update operation");
                        }

                        //Check if its Update on subnets
                        LOG.debug("update : Checking if this is update on subnets");
                        List<Uuid> originalSubnetIds = original.getSubnetIds();
                        List<Uuid> updatedSubnetIds = update.getSubnetIds();
                        Set<Uuid> addedSubnetIds = new HashSet<>(updatedSubnetIds);
                        addedSubnetIds.removeAll(originalSubnetIds);

                        //Check if the Subnet IDs are added during the update.
                        if (addedSubnetIds.size() != 0) {
                            LOG.debug(
                                    "update : Start processing of the Subnet IDs addition during the update operation");
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
                            LOG.debug("update : End processing of the Subnet IDs addition during the update operation");
                        }

                        //Check if the Subnet IDs are removed during the update.
                        Set<Uuid> removedSubnetIds = new HashSet<>(originalSubnetIds);
                        removedSubnetIds.removeAll(updatedSubnetIds);
                        if (removedSubnetIds.size() != 0) {
                            LOG.debug(
                                    "update : Start processing of the Subnet IDs removal during the update operation");
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
                            LOG.debug("update : End processing of the Subnet IDs removal during the update operation");
                        }
                    }));
                }));
                return futures;
            }, NatConstants.NAT_DJC_MAX_RETRIES);
        } //end of controller based SNAT
    }

    private boolean isExternalIpAllocated(String externalIp) {
        InstanceIdentifier<ExternalIpsCounter> id = InstanceIdentifier.builder(ExternalIpsCounter.class).build();
        Optional<ExternalIpsCounter> externalCountersData =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (externalCountersData.isPresent()) {
            ExternalIpsCounter externalIpsCounters = externalCountersData.get();
            List<ExternalCounters> externalCounters = externalIpsCounters.getExternalCounters();
            for (ExternalCounters ext : externalCounters) {
                for (ExternalIpCounter externalIpCount : ext.getExternalIpCounter()) {
                    if (externalIpCount.getExternalIp().equals(externalIp)) {
                        if (externalIpCount.getCounter() != 0) {
                            return true;
                        }
                        break;
                    }
                }
            }
        }
        return false;
    }

    private void allocateExternalIp(BigInteger dpnId, Routers router, long routerId, String routerName,
            Uuid networkId, String subnetIp, WriteTransaction writeFlowInvTx) {
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
            Long label = checkExternalIpLabel(routerId, leastLoadedExtIpAddrStr);
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
                if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
                    LOG.debug("allocateExternalIp : Best effort for getting primary napt switch when router i/f are"
                        + "added after gateway-set");
                    dpnId = NatUtil.getPrimaryNaptfromRouterId(dataBroker, routerId);
                    if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
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

    protected Long checkExternalIpLabel(long routerId, String externalIp) {
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
    protected void remove(InstanceIdentifier<Routers> identifier, Routers router) {
        LOG.trace("remove : Router delete method");
        {
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
            if (natMode == NatMode.Conntrack) {
                if (router.isEnableSnat()) {
                    centralizedSwitchScheduler.releaseCentralizedSwitch(router);
                }
            } else {
                coordinator.enqueueJob(NatConstants.NAT_DJC_PREFIX + router.key(),
                    () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                        LOG.info("remove : Removing default NAT route from FIB on all dpns part of router {} ",
                                routerName);
                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
                        if (routerId == NatConstants.INVALID_ID) {
                            LOG.error("remove : Remove external router event - Invalid routerId for routerName {}",
                                    routerName);
                            return;
                        }
                        long bgpVpnId = NatConstants.INVALID_ID;
                        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
                        if (bgpVpnUuid != null) {
                            bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                        }
                        addOrDelDefFibRouteToSNAT(routerName, routerId, bgpVpnId, bgpVpnUuid, false,
                                tx);
                        Uuid networkUuid = router.getNetworkId();

                        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
                        if (primarySwitchId == null || primarySwitchId.equals(BigInteger.ZERO)) {
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
                            handleDisableSnat(router, networkUuid, externalIps, true, null, primarySwitchId,
                                    routerId, tx);
                        }
                        NatOverVxlanUtil.releaseVNI(routerName, idManager);
                    })), NatConstants.NAT_DJC_MAX_RETRIES);
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleDisableSnat(Routers router, Uuid networkUuid, @Nonnull Collection<String> externalIps,
                                  boolean routerFlag, String vpnName, BigInteger naptSwitchDpnId,
                                  long routerId, WriteTransaction removeFlowInvTx) {
        LOG.info("handleDisableSnat : Entry");
        String routerName = router.getRouterName();
        try {
            if (routerFlag) {
                removeNaptSwitch(routerName);
            } else {
                updateNaptSwitch(routerName, BigInteger.ZERO);
            }

            LOG.debug("handleDisableSnat : Remove the ExternalCounter model for the router ID {}", routerId);
            naptManager.removeExternalCounter(routerId);

            LOG.debug("handleDisableSnat : got primarySwitch as dpnId {}", naptSwitchDpnId);
            if (naptSwitchDpnId == null || naptSwitchDpnId.equals(BigInteger.ZERO)) {
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
            Collection<Uuid> externalSubnetList = NatUtil.getExternalSubnetIdsFromExternalIps(router.getExternalIps());
            removeNaptFlowsFromActiveSwitch(routerId, routerName, naptSwitchDpnId, networkUuid, vpnName, externalIps,
                    externalSubnetList, removeFlowInvTx, extNwProvType);
            removeFlowsFromNonActiveSwitches(routerId, routerName, naptSwitchDpnId, removeFlowInvTx);
            try {
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
            } catch (Exception ex) {
                LOG.error("handleDisableSnat : Failed to remove fib entries for routerId {} in naptSwitchDpnId {}",
                    routerId, naptSwitchDpnId, ex);
            }
            // Use the NaptMananager removeMapping API to remove the entire list of IP addresses maintained
            // for the router ID.
            LOG.debug("handleDisableSnat : Remove the Internal to external IP address maintained for the "
                    + "router ID {} in the DS", routerId);
            naptManager.removeMapping(routerId);
        } catch (Exception ex) {
            LOG.error("handleDisableSnat : Exception while handling disableSNAT for router :{}", routerName, ex);
        }
        LOG.info("handleDisableSnat : Exit");
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleDisableSnatInternetVpn(String routerName, long routerId, Uuid networkUuid,
                                             @Nonnull Collection<String> externalIps,
                                             String vpnId, WriteTransaction writeFlowInvTx) {
        LOG.debug("handleDisableSnatInternetVpn: Started to process handle disable snat for router {} "
                + "with internet vpn {}", routerName, vpnId);
        try {
            BigInteger naptSwitchDpnId = null;
            InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch =
                NatUtil.buildNaptSwitchRouterIdentifier(routerName);
            Optional<RouterToNaptSwitch> rtrToNapt =
                read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerToNaptSwitch);
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
            NatOverVxlanUtil.releaseVNI(vpnId, idManager);
        } catch (Exception ex) {
            LOG.error("handleDisableSnatInternetVpn: Exception while handling disableSNATInternetVpn for router {} "
                    + "with internet vpn {}", routerName, vpnId, ex);
        }
        LOG.debug("handleDisableSnatInternetVpn: Processed handle disable snat for router {} with internet vpn {}",
                routerName, vpnId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateNaptSwitch(String routerName, BigInteger naptSwitchId) {
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

    public void removeNaptFlowsFromActiveSwitch(long routerId, String routerName,
                                                BigInteger dpnId, Uuid networkId, String vpnName,
                                                @Nonnull Collection<String> externalIps,
                                                Collection<Uuid> externalSubnetList,
                                                WriteTransaction removeFlowInvTx, ProviderTypes extNwProvType) {
        LOG.debug("removeNaptFlowsFromActiveSwitch : Remove NAPT flows from Active switch");
        BigInteger cookieSnatFlow = NatUtil.getCookieNaptFlow(routerId);

        //Remove the PSNAT entry which forwards the packet to Outbound NAPT Table (For the
        // traffic which comes from the  VMs of the NAPT switches)
        String preSnatFlowRef = getFlowRefSnat(dpnId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity preSnatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.PSNAT_TABLE, preSnatFlowRef);

        LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {} "
                + "and router ID {}", NwConstants.PSNAT_TABLE, dpnId, routerId);
        mdsalManager.removeFlowToTx(preSnatFlowEntity, removeFlowInvTx);

        //Remove the Terminating Service table entry which forwards the packet to Outbound NAPT Table (For the
        // traffic which comes from the VMs of the non NAPT switches)
        long tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, elanManager, idManager, routerId,
                routerName);
        String tsFlowRef = getFlowRefTs(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, tunnelId);
        FlowEntity tsNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, tsFlowRef);
        LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {} "
                + "and router ID {}", NwConstants.INTERNAL_TUNNEL_TABLE, dpnId, routerId);
        mdsalManager.removeFlowToTx(tsNatFlowEntity, removeFlowInvTx);

        //Remove the flow table 25->44 from NAPT Switch
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            NatUtil.removePreDnatToSnatTableEntry(mdsalManager, dpnId, removeFlowInvTx);
        }

        //Remove the Outbound flow entry which forwards the packet to FIB Table
        LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {}"
                + " and router ID {}", NwConstants.OUTBOUND_NAPT_TABLE, dpnId, routerId);

        String outboundTcpNatFlowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
                NwConstants.IP_PROT_TCP);
        FlowEntity outboundTcpNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
            outboundTcpNatFlowRef);
        mdsalManager.removeFlowToTx(outboundTcpNatFlowEntity, removeFlowInvTx);

        String outboundUdpNatFlowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
                NwConstants.IP_PROT_UDP);
        FlowEntity outboundUdpNatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
                outboundUdpNatFlowRef);
        mdsalManager.removeFlowToTx(outboundUdpNatFlowEntity, removeFlowInvTx);

        String icmpDropFlowRef = getFlowRefOutbound(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId,
                NwConstants.IP_PROT_ICMP);
        FlowEntity icmpDropFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE,
                icmpDropFlowRef);
        mdsalManager.removeFlowToTx(icmpDropFlowEntity, removeFlowInvTx);

        removeNaptFibExternalOutputFlows(routerId, dpnId, networkId, externalIps, removeFlowInvTx);
        //Remove the NAPT PFIB TABLE (47->21) which forwards the incoming packet to FIB Table matching on the
        // External Subnet Vpn Id.
        for (Uuid externalSubnetId : externalSubnetList) {
            long subnetVpnId = NatUtil.getVpnId(dataBroker, externalSubnetId.getValue());
            if (subnetVpnId != -1) {
                String natPfibSubnetFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, subnetVpnId);
                FlowEntity natPfibFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE,
                        natPfibSubnetFlowRef);
                mdsalManager.removeFlowToTx(natPfibFlowEntity, removeFlowInvTx);
                LOG.debug("removeNaptFlowsFromActiveSwitch : Removed the flow in table {} with external subnet "
                        + "Vpn Id {} as metadata on Napt Switch {}", NwConstants.NAPT_PFIB_TABLE,
                        subnetVpnId, dpnId);
            }
        }

        //Remove the NAPT PFIB TABLE which forwards the incoming packet to FIB Table matching on the router ID.
        String natPfibFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        FlowEntity natPfibFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE, natPfibFlowRef);

        LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch with the DPN ID {} "
                + "and router ID {}", NwConstants.NAPT_PFIB_TABLE, dpnId, routerId);
        mdsalManager.removeFlowToTx(natPfibFlowEntity, removeFlowInvTx);

        // Long vpnId = NatUtil.getVpnId(dataBroker, routerId);
        // - This does not work since ext-routers is deleted already - no network info
        //Get the VPN ID from the ExternalNetworks model
        long vpnId = -1;
        if (vpnName == null || vpnName.isEmpty()) {
            // ie called from router delete cases
            Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
            LOG.debug("removeNaptFlowsFromActiveSwitch : vpnUuid is {}", vpnUuid);
            if (vpnUuid != null) {
                vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
                LOG.debug("removeNaptFlowsFromActiveSwitch : vpnId {} for external  network {} router delete or "
                        + "disableSNAT scenario", vpnId, networkId);
            }
        } else {
            // ie called from disassociate vpn case
            LOG.debug("removeNaptFlowsFromActiveSwitch : This is disassociate nw with vpn case with vpnName {}",
                    vpnName);
            vpnId = NatUtil.getVpnId(dataBroker, vpnName);
            LOG.debug("removeNaptFlowsFromActiveSwitch : vpnId for disassociate nw with vpn scenario {}", vpnId);
        }

        if (vpnId != NatConstants.INVALID_ID) {
            //Remove the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table matching on the VPN ID.
            String natPfibVpnFlowRef = getFlowRefTs(dpnId, NwConstants.NAPT_PFIB_TABLE, vpnId);
            FlowEntity natPfibVpnFlowEntity =
                NatUtil.buildFlowEntity(dpnId, NwConstants.NAPT_PFIB_TABLE, natPfibVpnFlowRef);
            LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in {} for the active switch with the DPN ID {} "
                    + "and VPN ID {}", NwConstants.NAPT_PFIB_TABLE, dpnId, vpnId);
            mdsalManager.removeFlowToTx(natPfibVpnFlowEntity, removeFlowInvTx);
        }

        //For the router ID get the internal IP , internal port and the corresponding external IP and external Port.
        IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
        if (ipPortMapping == null) {
            LOG.error("removeNaptFlowsFromActiveSwitch : Unable to retrieve the IpPortMapping");
            return;
        }

        List<IntextIpProtocolType> intextIpProtocolTypes = ipPortMapping.getIntextIpProtocolType();
        for (IntextIpProtocolType intextIpProtocolType : intextIpProtocolTypes) {
            List<IpPortMap> ipPortMaps = intextIpProtocolType.getIpPortMap();
            for (IpPortMap ipPortMap : ipPortMaps) {
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
                    String.valueOf(routerId), internalIp, Integer.parseInt(internalPort));
                FlowEntity outboundNaptFlowEntity =
                    NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active switch "
                        + "with the DPN ID {} and router ID {}", NwConstants.OUTBOUND_NAPT_TABLE, dpnId, routerId);
                mdsalManager.removeFlowToTx(outboundNaptFlowEntity, removeFlowInvTx);

                IpPortExternal ipPortExternal = ipPortMap.getIpPortExternal();
                String externalIp = ipPortExternal.getIpAddress();
                int externalPort = ipPortExternal.getPortNum();

                //Build the flow for the inbound NAPT table
                switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE,
                    String.valueOf(routerId), externalIp, externalPort);
                FlowEntity inboundNaptFlowEntity =
                    NatUtil.buildFlowEntity(dpnId, NwConstants.INBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                LOG.info("removeNaptFlowsFromActiveSwitch : Remove the flow in the {} for the active active switch "
                        + "with the DPN ID {} and router ID {}", NwConstants.INBOUND_NAPT_TABLE, dpnId, routerId);
                mdsalManager.removeFlowToTx(inboundNaptFlowEntity, removeFlowInvTx);
            }
        }
    }

    protected void removeNaptFibExternalOutputFlows(long routerId, BigInteger dpnId, Uuid networkId,
                                                    @Nonnull Collection<String> externalIps,
                                                    WriteTransaction writeFlowInvTx) {
        long extVpnId = NatConstants.INVALID_ID;
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
            mdsalManager.removeFlowToTx(natPfibVpnFlowEntity, writeFlowInvTx);
        }
    }

    private String removeMaskFromIp(String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            return ip.split("/")[0];
        }
        return ip;
    }

    public void removeNaptFlowsFromActiveSwitchInternetVpn(long routerId, String routerName,
                                                           BigInteger dpnId, Uuid networkId, String vpnName,
                                                           WriteTransaction writeFlowInvTx) {
        LOG.debug("removeNaptFlowsFromActiveSwitchInternetVpn : Remove NAPT flows from Active switch Internet Vpn");
        BigInteger cookieSnatFlow = NatUtil.getCookieNaptFlow(routerId);

        //Remove the NAPT PFIB TABLE entry
        long vpnId = -1;
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
            mdsalManager.removeFlowToTx(natPfibVpnFlowEntity, writeFlowInvTx);

            // Remove IP-PORT active NAPT entries and release port from IdManager
            // For the router ID get the internal IP , internal port and the corresponding
            // external IP and external Port.
            IpPortMapping ipPortMapping = NatUtil.getIportMapping(dataBroker, routerId);
            if (ipPortMapping == null) {
                LOG.error("removeNaptFlowsFromActiveSwitchInternetVpn : Unable to retrieve the IpPortMapping");
                return;
            }
            List<IntextIpProtocolType> intextIpProtocolTypes = ipPortMapping.getIntextIpProtocolType();
            for (IntextIpProtocolType intextIpProtocolType : intextIpProtocolTypes) {
                List<IpPortMap> ipPortMaps = intextIpProtocolType.getIpPortMap();
                for (IpPortMap ipPortMap : ipPortMaps) {
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
                        String.valueOf(routerId), internalIp, Integer.parseInt(internalPort));
                    FlowEntity outboundNaptFlowEntity =
                        NatUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                    LOG.info("removeNaptFlowsFromActiveSwitchInternetVpn : Remove the flow in the {} for the "
                            + "active switch with the DPN ID {} and router ID {}",
                            NwConstants.OUTBOUND_NAPT_TABLE, dpnId, routerId);
                    mdsalManager.removeFlowToTx(outboundNaptFlowEntity, writeFlowInvTx);

                    IpPortExternal ipPortExternal = ipPortMap.getIpPortExternal();
                    String externalIp = ipPortExternal.getIpAddress();
                    int externalPort = ipPortExternal.getPortNum();

                    //Build the flow for the inbound NAPT table
                    switchFlowRef = NatUtil.getNaptFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE,
                        String.valueOf(routerId), externalIp, externalPort);
                    FlowEntity inboundNaptFlowEntity =
                        NatUtil.buildFlowEntity(dpnId, NwConstants.INBOUND_NAPT_TABLE, cookieSnatFlow, switchFlowRef);

                    LOG.info("removeNaptFlowsFromActiveSwitchInternetVpn : Remove the flow in the {} for the "
                            + "active active switch with the DPN ID {} and router ID {}",
                            NwConstants.INBOUND_NAPT_TABLE, dpnId, routerId);
                    mdsalManager.removeFlowToTx(inboundNaptFlowEntity, writeFlowInvTx);

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

    public void removeFlowsFromNonActiveSwitches(long routerId, String routerName,
            BigInteger naptSwitchDpnId, WriteTransaction removeFlowInvTx) {
        LOG.debug("removeFlowsFromNonActiveSwitches : Remove NAPT related flows from non active switches");

        // Remove the flows from the other switches which points to the primary and secondary switches
        // for the flows related the router ID.
        List<BigInteger> allSwitchList = naptSwitchSelector.getDpnsForVpn(routerName);
        if (allSwitchList.isEmpty()) {
            LOG.error("removeFlowsFromNonActiveSwitches : Unable to get the swithces for the router {}", routerName);
            return;
        }
        for (BigInteger dpnId : allSwitchList) {
            if (!naptSwitchDpnId.equals(dpnId)) {
                LOG.info("removeFlowsFromNonActiveSwitches : Handle Ordinary switch");

                //Remove the PSNAT entry which forwards the packet to Terminating Service table
                String preSnatFlowRef = getFlowRefSnat(dpnId, NwConstants.PSNAT_TABLE, String.valueOf(routerName));
                FlowEntity preSnatFlowEntity = NatUtil.buildFlowEntity(dpnId, NwConstants.PSNAT_TABLE, preSnatFlowRef);

                LOG.info("removeFlowsFromNonActiveSwitches : Remove the flow in the {} for the non active switch "
                        + "with the DPN ID {} and router ID {}", NwConstants.PSNAT_TABLE, dpnId, routerId);
                mdsalManager.removeFlowToTx(preSnatFlowEntity, removeFlowInvTx);

                //Remove the group entry which forwards the traffic to the out port (VXLAN tunnel).
                long groupId = createGroupId(getGroupIdKey(routerName));
                List<BucketInfo> listBucketInfo = new ArrayList<>();
                GroupEntity preSnatGroupEntity =
                    MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll, listBucketInfo);

                LOG.info("removeFlowsFromNonActiveSwitches : Remove the group {} for the non active switch with "
                        + "the DPN ID {} and router ID {}", groupId, dpnId, routerId);
                mdsalManager.removeGroup(preSnatGroupEntity);

            }
        }
    }

    public void clrRtsFromBgpAndDelFibTs(final BigInteger dpnId, Long routerId, Uuid networkUuid,
                                         @Nonnull Collection<String> externalIps, String vpnName,
                                         String extGwMacAddress, WriteTransaction removeFlowInvTx) {
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
            clrRtsFromBgpAndDelFibTs(dpnId, routerId, extIp, vpnName, networkUuid, extGwMacAddress, removeFlowInvTx);
        }
    }

    protected void clrRtsFromBgpAndDelFibTs(final BigInteger dpnId, long routerId, String extIp, final String vpnName,
                                            final Uuid networkUuid, String extGwMacAddress,
                                            WriteTransaction removeFlowInvTx) {
        clearBgpRoutes(extIp, vpnName);
        delFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, networkUuid, extGwMacAddress, false,
                removeFlowInvTx);
    }

    protected void delFibTsAndReverseTraffic(final BigInteger dpnId, long routerId, String extIp,
                                             final String vpnName, Uuid extNetworkId, long tempLabel,
                                             String gwMacAddress, boolean switchOver,
                                             WriteTransaction removeFlowInvTx) {
        LOG.debug("delFibTsAndReverseTraffic : Removing fib entry for externalIp {} in routerId {}", extIp, routerId);
        String routerName = NatUtil.getRouterName(dataBroker,routerId);
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
            evpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, gwMacAddress,
                    removeFlowInvTx);
            return;
        }
        if (tempLabel < 0) {
            LOG.error("delFibTsAndReverseTraffic : Label not found for externalIp {} with router id {}",
                    extIp, routerId);
            return;
        }

        final long label = tempLabel;
        final String externalIp = NatUtil.validateAndAddNetworkMask(extIp);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(externalIp).setServiceId(label)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).build();
        ListenableFuture<RpcResult<RemoveFibEntryOutput>> future = fibService.removeFibEntry(input);

        removeTunnelTableEntry(dpnId, label, removeFlowInvTx);
        removeLFibTableEntry(dpnId, label, removeFlowInvTx);
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            //Remove the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
            NatUtil.removePreDnatToSnatTableEntry(mdsalManager, dpnId, removeFlowInvTx);
        }
        if (!switchOver) {
            ListenableFuture<RpcResult<RemoveVpnLabelOutput>> labelFuture =
                    Futures.transformAsync(future, result -> {
                                //Release label
                        if (result.isSuccessful()) {
                            NatUtil.removePreDnatToSnatTableEntry(mdsalManager, dpnId, removeFlowInvTx);
                            RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder()
                                    .setVpnName(vpnName).setIpPrefix(externalIp).build();
                            return vpnService.removeVpnLabel(labelInput);
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
                public void onFailure(@Nonnull Throwable error) {
                    LOG.error("delFibTsAndReverseTraffic : Error in removing the label:{} or custom fib entries"
                        + "got external ip {}", label, extIp, error);
                }

                @Override
                public void onSuccess(@Nonnull RpcResult<RemoveVpnLabelOutput> result) {
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

    private void delFibTsAndReverseTraffic(final BigInteger dpnId, long routerId, String extIp, final String vpnName,
                                           final Uuid networkUuid, String extGwMacAddress, boolean switchOver,
                                           WriteTransaction removeFlowInvTx) {
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
            evpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic(dpnId, routerId, extIp, vpnName, extGwMacAddress,
                    removeFlowInvTx);
            return;
        }
        //Get IPMaps from the DB for the router ID
        List<IpMap> dbIpMaps = NaptManager.getIpMapList(dataBroker, routerId);
        if (dbIpMaps.isEmpty()) {
            LOG.error("delFibTsAndReverseTraffic : IPMaps not found for router {}", routerId);
            return;
        }

        long tempLabel = NatConstants.INVALID_ID;
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

        final long label = tempLabel;
        final String externalIp = NatUtil.validateAndAddNetworkMask(extIp);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder()
                .setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalIp)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).setServiceId(label).build();
        ListenableFuture<RpcResult<RemoveFibEntryOutput>> future = fibService.removeFibEntry(input);

        removeTunnelTableEntry(dpnId, label, removeFlowInvTx);
        removeLFibTableEntry(dpnId, label, removeFlowInvTx);
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            //Remove the flow table 25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
            NatUtil.removePreDnatToSnatTableEntry(mdsalManager, dpnId, removeFlowInvTx);
        }
        if (!switchOver) {
            ListenableFuture<RpcResult<RemoveVpnLabelOutput>> labelFuture =
                    Futures.transformAsync(future, result -> {
                        //Release label
                        if (result.isSuccessful()) {
                            RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder()
                                    .setVpnName(vpnName).setIpPrefix(externalIp).build();
                            return vpnService.removeVpnLabel(labelInput);
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
                public void onFailure(@Nonnull Throwable error) {
                    LOG.error("delFibTsAndReverseTraffic : Error in removing the label or custom fib entries", error);
                }

                @Override
                public void onSuccess(@Nonnull RpcResult<RemoveVpnLabelOutput> result) {
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

    protected void clearFibTsAndReverseTraffic(final BigInteger dpnId, Long routerId, Uuid networkUuid,
                                               List<String> externalIps, String vpnName, String extGwMacAddress,
                                               WriteTransaction writeFlowInvTx) {
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
        NatUtil.removePrefixFromBGP(bgpManager, fibManager, rd, externalIp, vpnName, LOG);
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long serviceId, WriteTransaction writeFlowInvTx) {
        LOG.info("removeTunnelTableEntry : called with DpnId = {} and label = {}", dpnId, serviceId);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(serviceId)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""),
            5, String.format("%s:%d", "TST Flow Entry ", serviceId), 0, 0,
            COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, null);
        mdsalManager.removeFlowToTx(dpnId, flowEntity, writeFlowInvTx);
        LOG.debug("removeTunnelTableEntry : dpID {} : label : {} removed successfully", dpnId, serviceId);
    }

    private void removeLFibTableEntry(BigInteger dpnId, long serviceId, WriteTransaction writeFlowInvTx) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        LOG.debug("removeLFibTableEntry : with flow ref {}", flowRef);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            COOKIE_VM_LFIB_TABLE, matches, null);

        mdsalManager.removeFlowToTx(dpnId, flowEntity, writeFlowInvTx);

        LOG.debug("removeLFibTableEntry : dpID : {} label : {} removed successfully", dpnId, serviceId);
    }

    /**
     * router association to vpn.
     *
     * @param routerName - Name of router
     * @param routerId - router id
     * @param bgpVpnName BGP VPN name
     */
    public void changeLocalVpnIdToBgpVpnId(String routerName, long routerId, String bgpVpnName,
            WriteTransaction writeFlowInvTx, ProviderTypes extNwProvType) {
        LOG.debug("changeLocalVpnIdToBgpVpnId : Router associated to BGP VPN");
        if (chkExtRtrAndSnatEnbl(new Uuid(routerName))) {
            long bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnName);

            LOG.debug("changeLocalVpnIdToBgpVpnId : BGP VPN ID value {} ", bgpVpnId);

            if (bgpVpnId != NatConstants.INVALID_ID) {
                LOG.debug("changeLocalVpnIdToBgpVpnId : Populate the router-id-name container with the "
                        + "mapping BGP VPN-ID {} -> BGP VPN-NAME {}", bgpVpnId, bgpVpnName);
                RouterIds rtrs = new RouterIdsBuilder().withKey(new RouterIdsKey(bgpVpnId))
                    .setRouterId(bgpVpnId).setRouterName(bgpVpnName).build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getRoutersIdentifier(bgpVpnId), rtrs);

                // Get the allocated Primary NAPT Switch for this router
                LOG.debug("changeLocalVpnIdToBgpVpnId : Router ID value {} ", routerId);

                LOG.debug("changeLocalVpnIdToBgpVpnId : Update the Router ID {} to the BGP VPN ID {} ",
                        routerId, bgpVpnId);
                addOrDelDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, bgpVpnId, true, writeFlowInvTx);

                // Get the group ID
                BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
                createGroupId(getGroupIdKey(routerName));
                installFlowsWithUpdatedVpnId(primarySwitchId, routerName, bgpVpnId, routerId, true, writeFlowInvTx,
                        extNwProvType);
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
    public void changeBgpVpnIdToLocalVpnId(String routerName, long routerId, String bgpVpnName,
            WriteTransaction writeFlowInvTx, ProviderTypes extNwProvType) {
        LOG.debug("changeBgpVpnIdToLocalVpnId : Router dissociated from BGP VPN");
        if (chkExtRtrAndSnatEnbl(new Uuid(routerName))) {
            long bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnName);
            LOG.debug("changeBgpVpnIdToLocalVpnId : BGP VPN ID value {} ", bgpVpnId);

            // Get the allocated Primary NAPT Switch for this router
            LOG.debug("changeBgpVpnIdToLocalVpnId : Router ID value {} ", routerId);

            LOG.debug("changeBgpVpnIdToLocalVpnId : Update the BGP VPN ID {} to the Router ID {}", bgpVpnId, routerId);
            addOrDelDefaultFibRouteForSnatWithBgpVpn(routerName, routerId, NatConstants.INVALID_ID,
                    true, writeFlowInvTx);

            // Get the group ID
            createGroupId(getGroupIdKey(routerName));
            BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
            installFlowsWithUpdatedVpnId(primarySwitchId, routerName, NatConstants.INVALID_ID, routerId, true,
                    writeFlowInvTx, extNwProvType);
        }
    }

    boolean chkExtRtrAndSnatEnbl(Uuid routerUuid) {
        InstanceIdentifier<Routers> routerInstanceIndentifier =
            InstanceIdentifier.builder(ExtRouters.class)
                .child(Routers.class, new RoutersKey(routerUuid.getValue())).build();
        Optional<Routers> routerData = read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerInstanceIndentifier);
        return routerData.isPresent() && routerData.get().isEnableSnat();
    }

    public void installFlowsWithUpdatedVpnId(BigInteger primarySwitchId, String routerName, long bgpVpnId,
                                             long routerId, boolean isSnatCfgd, WriteTransaction writeFlowInvTx,
                                             ProviderTypes extNwProvType) {
        long changedVpnId = bgpVpnId;
        String idType = "BGP VPN";
        if (bgpVpnId == NatConstants.INVALID_ID) {
            changedVpnId = routerId;
            idType = "router";
        }

        List<BigInteger> switches = NatUtil.getDpnsForRouter(dataBroker, routerName);
        if (switches.isEmpty()) {
            LOG.error("installFlowsWithUpdatedVpnId : No switches found for router {}", routerName);
            return;
        }
        for (BigInteger dpnId : switches) {
            // Update the BGP VPN ID in the SNAT miss entry to group
            if (!dpnId.equals(primarySwitchId)) {
                LOG.debug("installFlowsWithUpdatedVpnId : Install group in non NAPT switch {}", dpnId);
                List<BucketInfo> bucketInfoForNonNaptSwitches =
                    getBucketInfoForNonNaptSwitches(dpnId, primarySwitchId, routerName, routerId);
                long groupId = createGroupId(getGroupIdKey(routerName));
                if (!isSnatCfgd) {
                    groupId = installGroup(dpnId, routerName, bucketInfoForNonNaptSwitches);
                }

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the SNAT miss entry pointing to group "
                                + "{} in the non NAPT switch {}", idType, changedVpnId, groupId, dpnId);
                FlowEntity flowEntity = buildSnatFlowEntityWithUpdatedVpnId(dpnId, routerName, groupId, changedVpnId);
                mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
            } else {
                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the SNAT miss entry pointing to group "
                                + "in the primary switch {}", idType, changedVpnId, primarySwitchId);
                FlowEntity flowEntity =
                    buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch(primarySwitchId, routerName, changedVpnId);
                mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the Terminating Service table (table "
                                + "ID 36) which forwards the packet to the table 46 in the Primary switch {}",
                        idType, changedVpnId, primarySwitchId);
                installTerminatingServiceTblEntryWithUpdatedVpnId(primarySwitchId, routerName, routerId,
                        changedVpnId, writeFlowInvTx, extNwProvType);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the Outbound NAPT table (table ID 46) "
                                + "which punts the packet to the controller in the Primary switch {}",
                        idType, changedVpnId, primarySwitchId);
                createOutboundTblEntryWithBgpVpn(primarySwitchId, routerId, changedVpnId, writeFlowInvTx);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the NAPT PFIB TABLE which forwards the"
                                + " outgoing packet to FIB Table in the Primary switch {}",
                        idType, changedVpnId, primarySwitchId);
                installNaptPfibEntryWithBgpVpn(primarySwitchId, routerId, changedVpnId, writeFlowInvTx);

                LOG.debug(
                        "installFlowsWithUpdatedVpnId : Update the {} ID {} in the NAPT flows for the Outbound NAPT "
                                + "table (table ID 46) and the INBOUND NAPT table (table ID 44) in the Primary switch"
                                + " {}", idType, changedVpnId, primarySwitchId);
                updateNaptFlowsWithVpnId(primarySwitchId, routerName, routerId, bgpVpnId);

                LOG.debug("installFlowsWithUpdatedVpnId : Installing SNAT PFIB flow in the primary switch {}",
                        primarySwitchId);
                Long vpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
                //Install the NAPT PFIB TABLE which forwards the outgoing packet to FIB Table matching on the VPN ID.
                if (vpnId != NatConstants.INVALID_ID) {
                    installNaptPfibEntry(primarySwitchId, vpnId, writeFlowInvTx);
                }
            }
        }
    }

    public void updateNaptFlowsWithVpnId(BigInteger dpnId, String routerName, long routerId, long bgpVpnId) {
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
        List<IntextIpProtocolType> intextIpProtocolTypes = ipPortMapping.getIntextIpProtocolType();
        for (IntextIpProtocolType intextIpProtocolType : intextIpProtocolTypes) {
            List<IpPortMap> ipPortMaps = intextIpProtocolType.getIpPortMap();
            for (IpPortMap ipPortMap : ipPortMaps) {
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
                long internetVpnid = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
                naptEventHandler.buildAndInstallNatFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE, internetVpnid,
                        routerId, bgpVpnId, externalAddress, internalAddress, protocol, extGwMacAddress);
                naptEventHandler.buildAndInstallNatFlows(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, internetVpnid,
                        routerId, bgpVpnId, internalAddress, externalAddress, protocol, extGwMacAddress);
            }
        }
    }

    public FlowEntity buildSnatFlowEntityWithUpdatedVpnId(BigInteger dpId, String routerName, long groupId,
                                                          long changedVpnId) {

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnId : called for dpId {}, routerName {} groupId {} "
            + "changed VPN ID {}", dpId, routerName, groupId, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        long tunnelId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, elanManager, idManager, changedVpnId,
                routerName);
        actionsInfo.add(new ActionSetFieldTunnelId(BigInteger.valueOf(tunnelId)));
        LOG.debug("buildSnatFlowEntityWithUpdatedVpnId : Setting the tunnel to the list of action infos {}",
                actionsInfo);
        actionsInfo.add(new ActionGroup(groupId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRefSnat(dpId, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnId : Returning SNAT Flow Entity {}", flowEntity);
        return flowEntity;
    }

    public FlowEntity buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch(BigInteger dpId, String routerName,
                                                                        long changedVpnId) {

        LOG.debug("buildSnatFlowEntityWithUpdatedVpnIdForPrimrySwtch : called for dpId {}, routerName {} "
                + "changed VPN ID {}", dpId, routerName, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId), MetaDataUtil.METADATA_MASK_VRFID));

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
    protected void installTerminatingServiceTblEntryWithUpdatedVpnId(BigInteger dpnId, String routerName,
            long routerId, long changedVpnId, WriteTransaction writeFlowInvTx, ProviderTypes extNwProvType) {
        LOG.debug("installTerminatingServiceTblEntryWithUpdatedVpnId : called for switch {}, "
            + "routerName {}, BGP VPN ID {}", dpnId, routerName, changedVpnId);
        FlowEntity flowEntity = buildTsFlowEntityWithUpdatedVpnId(dpnId, routerName, routerId, changedVpnId,
                extNwProvType);
        mdsalManager.addFlowToTx(flowEntity, writeFlowInvTx);
    }

    private FlowEntity buildTsFlowEntityWithUpdatedVpnId(BigInteger dpId, String routerName,
            long routerIdLongVal, long changedVpnId, ProviderTypes extNwProvType) {
        LOG.debug("buildTsFlowEntityWithUpdatedVpnId : called for switch {}, routerName {}, BGP VPN ID {}",
            dpId, routerName, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        BigInteger tunnelId = BigInteger.valueOf(changedVpnId);
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanManager, extNwProvType)) {
            tunnelId = NatOverVxlanUtil.getRouterVni(idManager, routerName, changedVpnId);
        }
        matches.add(new MatchTunnelId(tunnelId));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId),
            MetaDataUtil.METADATA_MASK_VRFID));
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));
        BigInteger routerId = BigInteger.valueOf(routerIdLongVal);
        String flowRef = getFlowRefTs(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId.longValue());
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
            NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_TS_TABLE, matches, instructions);
        return flowEntity;
    }

    public void createOutboundTblEntryWithBgpVpn(BigInteger dpnId, long routerId, long changedVpnId,
                                                 WriteTransaction writeFlowInvTx) {
        LOG.debug("createOutboundTblEntryWithBgpVpn : called for dpId {} and routerId {}, BGP VPN ID {}",
            dpnId, routerId, changedVpnId);
        FlowEntity tcpFlowEntity = buildOutboundFlowEntityWithBgpVpn(dpnId, routerId, changedVpnId,
                NwConstants.IP_PROT_TCP);
        LOG.debug("createOutboundTblEntryWithBgpVpn : Installing tcp flow {}", tcpFlowEntity);
        mdsalManager.addFlowToTx(tcpFlowEntity, writeFlowInvTx);

        FlowEntity udpFlowEntity = buildOutboundFlowEntityWithBgpVpn(dpnId, routerId, changedVpnId,
                NwConstants.IP_PROT_UDP);
        LOG.debug("createOutboundTblEntryWithBgpVpn : Installing udp flow {}", udpFlowEntity);
        mdsalManager.addFlowToTx(udpFlowEntity, writeFlowInvTx);

        FlowEntity icmpDropFlow = buildIcmpDropFlow(dpnId, routerId, changedVpnId);
        LOG.debug("createOutboundTblEntry: Installing icmp drop flow {}", icmpDropFlow);
        mdsalManager.addFlowToTx(icmpDropFlow, writeFlowInvTx);
    }

    protected FlowEntity buildOutboundFlowEntityWithBgpVpn(BigInteger dpId, long routerId,
                                                           long changedVpnId, int protocol) {
        LOG.debug("buildOutboundFlowEntityWithBgpVpn : called for dpId {} and routerId {}, BGP VPN ID {}",
            dpId, routerId, changedVpnId);
        BigInteger cookie = getCookieOutboundFlow(routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpProtocol((short)protocol));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId), MetaDataUtil.METADATA_MASK_VRFID));

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

    public void installNaptPfibEntryWithBgpVpn(BigInteger dpnId, long segmentId, long changedVpnId,
                                               WriteTransaction writeFlowInvTx) {
        LOG.debug("installNaptPfibEntryWithBgpVpn : called for dpnId {} and segmentId {} ,BGP VPN ID {}",
            dpnId, segmentId, changedVpnId);
        FlowEntity naptPfibFlowEntity = buildNaptPfibFlowEntityWithUpdatedVpnId(dpnId, segmentId, changedVpnId);
        mdsalManager.addFlowToTx(naptPfibFlowEntity, writeFlowInvTx);
    }

    public FlowEntity buildNaptPfibFlowEntityWithUpdatedVpnId(BigInteger dpId, long segmentId, long changedVpnId) {

        LOG.debug("buildNaptPfibFlowEntityWithUpdatedVpnId : called for dpId {}, "
            + "segmentId {}, BGP VPN ID {}", dpId, segmentId, changedVpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(changedVpnId), MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRefTs(dpId, NwConstants.NAPT_PFIB_TABLE, segmentId);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.NAPT_PFIB_TABLE, flowRef,
            NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,
            NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);
        LOG.debug("buildNaptPfibFlowEntityWithUpdatedVpnId : Returning NaptPFib Flow Entity {}", flowEntity);
        return flowEntity;
    }

    @Override
    protected ExternalRoutersListener getDataTreeChangeListener() {
        return ExternalRoutersListener.this;
    }

    protected void installNaptPfibEntriesForExternalSubnets(String routerName, BigInteger dpnId,
                                                            WriteTransaction writeFlowInvTx) {
        Collection<Uuid> externalSubnetIdsForRouter = NatUtil.getExternalSubnetIdsForRouter(dataBroker,
                routerName);
        for (Uuid externalSubnetId : externalSubnetIdsForRouter) {
            long subnetVpnId = NatUtil.getVpnId(dataBroker, externalSubnetId.getValue());
            if (subnetVpnId != -1) {
                LOG.debug("installNaptPfibEntriesForExternalSubnets : called for dpnId {} "
                    + "and vpnId {}", dpnId, subnetVpnId);
                installNaptPfibEntry(dpnId, subnetVpnId, writeFlowInvTx);
            }
        }
    }
}
