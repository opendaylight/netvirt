/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.infrautils.utils.function.InterruptibleCheckedConsumer;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadTransaction;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput.ArpReponderInputBuilder;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.populator.input.L3vpnInput;
import org.opendaylight.netvirt.vpnmanager.populator.registry.L3vpnRegistry;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetsAssociatedToRouteTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.RouteTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.RouteTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.AssociatedSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.associated.subnet.AssociatedVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnManagerImpl implements IVpnManager {

    private static final Logger LOG = LoggerFactory.getLogger(VpnManagerImpl.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final IMdsalApiManager mdsalManager;
    private final IElanService elanService;
    private final IInterfaceManager interfaceManager;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;
    private final OdlInterfaceRpcService ifaceMgrRpcService;
    private final IVpnLinkService ivpnLinkService;
    private final IFibManager fibManager;
    private final IBgpManager bgpManager;
    private final InterVpnLinkCache interVpnLinkCache;
    private final DataTreeEventCallbackRegistrar eventCallbacks;
    private final UpgradeState upgradeState;
    private final ItmRpcService itmRpcService;
    private final VpnUtil vpnUtil;

    @Inject
    public VpnManagerImpl(final DataBroker dataBroker,
                          final IdManagerService idManagerService,
                          final IMdsalApiManager mdsalManager,
                          final IElanService elanService,
                          final IInterfaceManager interfaceManager,
                          final VpnSubnetRouteHandler vpnSubnetRouteHandler,
                          final OdlInterfaceRpcService ifaceMgrRpcService,
                          final IVpnLinkService ivpnLinkService,
                          final IFibManager fibManager,
                          final IBgpManager bgpManager,
                          final InterVpnLinkCache interVpnLinkCache,
                          final DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar,
                          final UpgradeState upgradeState,
                          final ItmRpcService itmRpcService,
                          final VpnUtil vpnUtil) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idManagerService;
        this.mdsalManager = mdsalManager;
        this.elanService = elanService;
        this.interfaceManager = interfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
        this.ifaceMgrRpcService = ifaceMgrRpcService;
        this.ivpnLinkService = ivpnLinkService;
        this.fibManager = fibManager;
        this.bgpManager = bgpManager;
        this.interVpnLinkCache = interVpnLinkCache;
        this.eventCallbacks = dataTreeEventCallbackRegistrar;
        this.upgradeState = upgradeState;
        this.itmRpcService = itmRpcService;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        createIdPool();
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
            .setLow(VpnConstants.VPN_IDPOOL_LOW)
            .setHigh(VpnConstants.VPN_IDPOOL_HIGH)
            .build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("Created IdPool for VPN Service");
            } else {
                LOG.error("createIdPool: Unable to create ID pool for VPNService");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for VPN Service", e);
        }

        // Now an IdPool for InterVpnLink endpoint's pseudo ports
        CreateIdPoolInput createPseudoLporTagPool =
            new CreateIdPoolInputBuilder().setPoolName(VpnConstants.PSEUDO_LPORT_TAG_ID_POOL_NAME)
                .setLow(VpnConstants.LOWER_PSEUDO_LPORT_TAG)
                .setHigh(VpnConstants.UPPER_PSEUDO_LPORT_TAG)
                .build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPseudoLporTagPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Created IdPool for Pseudo Port tags");
            } else {
                StringBuilder errMsg = new StringBuilder();
                if (result != null && result.get() != null) {
                    Collection<RpcError> errors = result.get().getErrors();
                    for (RpcError err : errors) {
                        errMsg.append(err.getMessage()).append("\n");
                    }
                }
                LOG.error("IdPool creation for PseudoPort tags failed. Reasons: {}", errMsg);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for Pseudo Port tags", e);
        }
    }

    @Override
    public void addExtraRoute(String vpnName, String destination, String nextHop, String rd, @Nullable String routerID,
        Uint32 l3vni, RouteOrigin origin, @Nullable String intfName, @Nullable Adjacency operationalAdj,
        VrfEntry.EncapType encapType, Set<String> prefixListForRefreshFib,
        @NonNull TypedWriteTransaction<Configuration> confTx) {
        //add extra route to vpn mapping; advertise with nexthop as tunnel ip
        vpnUtil.syncUpdate(LogicalDatastoreType.OPERATIONAL,
                VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, rd != null ? rd : routerID,
                        destination),
                VpnUtil.getVpnToExtraroute(destination, Collections.singletonList(nextHop)));

        Uint64 dpnId = null;
        if (intfName != null && !intfName.isEmpty()) {
            dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.error("addExtraRoute: NextHop for interface {} is null / empty."
                        + " Failed advertising extra route for rd {} prefix {} dpn {}", intfName, rd, destination,
                        dpnId);
                return;
            }
            nextHop = nextHopIp;
        }

        String primaryRd = vpnUtil.getPrimaryRd(vpnName);

        // TODO: This is a limitation to be stated in docs. When configuring static route to go to
        // another VPN, there can only be one nexthop or, at least, the nexthop to the interVpnLink should be in
        // first place.
        Optional<InterVpnLinkDataComposite> optVpnLink = interVpnLinkCache.getInterVpnLinkByEndpoint(nextHop);
        if (optVpnLink.isPresent() && optVpnLink.get().isActive()) {
            InterVpnLinkDataComposite interVpnLink = optVpnLink.get();
            // If the nexthop is the endpoint of Vpn2, then prefix must be advertised to Vpn1 in DC-GW, with nexthops
            // pointing to the DPNs where Vpn1 is instantiated. LFIB in these DPNS must have a flow entry, with lower
            // priority, where if Label matches then sets the lportTag of the Vpn2 endpoint and goes to LportDispatcher
            // This is like leaking one of the Vpn2 routes towards Vpn1
            String srcVpnUuid = interVpnLink.getVpnNameByIpAddress(nextHop);
            String dstVpnUuid = interVpnLink.getOtherVpnNameByIpAddress(nextHop);
            String dstVpnRd = vpnUtil.getVpnRd(dstVpnUuid);
            Uint32 newLabel = vpnUtil.getUniqueId(VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(dstVpnRd, destination));
            if (newLabel.longValue() == VpnConstants.INVALID_LABEL) {
                LOG.error("addExtraRoute: Unable to fetch label from Id Manager. Bailing out of adding intervpnlink"
                        + " route for destination {}", destination);
                return;
            }
            ivpnLinkService.leakRoute(interVpnLink, srcVpnUuid, dstVpnUuid, destination, newLabel, RouteOrigin.STATIC);
        } else {
            Optional<Routes> optVpnExtraRoutes = VpnExtraRouteHelper
                    .getVpnExtraroutes(dataBroker, vpnName, rd != null ? rd : routerID, destination);
            if (optVpnExtraRoutes.isPresent()) {
                List<String> nhList = optVpnExtraRoutes.get().getNexthopIpList();
                if (nhList != null && nhList.size() > 1) {
                    // If nhList is greater than one for vpnextraroute, a call to populatefib doesn't update vrfentry.
                    prefixListForRefreshFib.add(destination);
                } else {
                    L3vpnInput input = new L3vpnInput().setNextHop(operationalAdj).setNextHopIp(nextHop)
                            .setL3vni(l3vni.longValue())
                            .setPrimaryRd(primaryRd).setVpnName(vpnName).setDpnId(dpnId)
                            .setEncapType(encapType).setRd(rd).setRouteOrigin(origin);
                    L3vpnRegistry.getRegisteredPopulator(encapType).populateFib(input, intfName, null, confTx);
                }
            }
        }
    }

    @Override
    public void delExtraRoute(String vpnName, String destination, String nextHop, String rd, @Nullable String routerID,
                              @Nullable String intfName, @NonNull TypedWriteTransaction<Configuration> confTx,
                              @NonNull TypedWriteTransaction<Operational> operTx) {
        Uint64 dpnId = null;
        String tunnelIp = nextHop;
        if (intfName != null && !intfName.isEmpty()) {
            dpnId = InterfaceUtils.getDpnForInterface(ifaceMgrRpcService, intfName);
            String nextHopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId);
            if (nextHopIp == null || nextHopIp.isEmpty()) {
                LOG.error("delExtraRoute: NextHop for interface {} is null / empty."
                        + " Failed advertising extra route for rd {} prefix {} dpn {}", intfName, rd, destination,
                        dpnId);
            }
            tunnelIp = nextHopIp;
        }
        if (rd != null) {
            String primaryRd = vpnUtil.getVpnRd(vpnName);
            removePrefixFromBGP(vpnName, primaryRd, rd, intfName, destination,
                                nextHop, tunnelIp, dpnId, confTx, operTx);
            LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName, rd);
        } else {
            // add FIB route directly
            fibManager.removeOrUpdateFibEntry(routerID, destination, tunnelIp, intfName, null, confTx);
            LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName,
                    routerID);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removePrefixFromBGP(String vpnName, String primaryRd, String extraRouteRd, String vpnInterfaceName,
                                    String prefix, String nextHop, String nextHopTunnelIp, Uint64 dpnId,
                                    TypedWriteTransaction<Configuration> confTx,
                                    TypedWriteTransaction<Operational> operTx) {
        String vpnNamePrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
        // FIXME: separate out to somehow?
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(vpnNamePrefixKey);
        LOG.info("removing prefix {} for nexthop {} in VPN {} rd {}", prefix, nextHop, vpnName, extraRouteRd);
        try {
            lock.lock();
            if (vpnUtil.removeOrUpdateDSForExtraRoute(vpnName, primaryRd, extraRouteRd, vpnInterfaceName, prefix,
                    nextHop, nextHopTunnelIp, operTx)) {
                return;
            }
            fibManager.removeOrUpdateFibEntry(primaryRd, prefix, nextHopTunnelIp, vpnInterfaceName,
                    null, confTx);
            if (VpnUtil.isEligibleForBgp(extraRouteRd, vpnName, dpnId, null /*networkName*/)) {
                // TODO: Might be needed to include nextHop here
                bgpManager.withdrawPrefix(extraRouteRd, prefix);
            }
            LOG.info("removePrefixFromBGP: VPN WITHDRAW: Removed Fib Entry rd {} prefix {} nexthop {}", extraRouteRd,
                    prefix, nextHop);
        } catch (RuntimeException e) {
            LOG.error("removePrefixFromBGP: Delete prefix {} rd {} nextHop {} failed", prefix, extraRouteRd, nextHop);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isVPNConfigured() {
        InstanceIdentifier<VpnInstances> vpnsIdentifier = InstanceIdentifier.builder(VpnInstances.class).build();
        try {
            Optional<VpnInstances> optionalVpns =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnsIdentifier);
            if (!optionalVpns.isPresent()
                    || optionalVpns.get().getVpnInstance() == null
                    || optionalVpns.get().getVpnInstance().isEmpty()) {
                LOG.trace("isVPNConfigured: No VPNs configured.");
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error reading VPN " + vpnsIdentifier, e);
        }
        LOG.trace("isVPNConfigured: VPNs are configured on the system.");
        return true;
    }

    @Override
    public void addSubnetMacIntoVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            Uint64 dpnId, TypedWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        setupSubnetMacInVpnInstance(vpnName, subnetVpnName, srcMacAddress, dpnId,
            (vpnId, dpId, subnetVpnId) -> addGwMac(srcMacAddress, confTx, vpnId, dpId, subnetVpnId));
    }

    @Override
    public void removeSubnetMacFromVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            Uint64 dpnId, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        setupSubnetMacInVpnInstance(vpnName, subnetVpnName, srcMacAddress, dpnId,
            (vpnId, dpId, subnetVpnId) -> removeGwMac(srcMacAddress, confTx, vpnId, dpId, subnetVpnId));
    }

    @FunctionalInterface
    private interface VpnInstanceSubnetMacSetupMethod {
        void process(Uint32 vpnId, Uint64 dpId, Uint32 subnetVpnId) throws InterruptedException, ExecutionException;
    }

    private void setupSubnetMacInVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            Uint64 dpnId, VpnInstanceSubnetMacSetupMethod consumer)
            throws ExecutionException, InterruptedException {
        if (vpnName == null) {
            LOG.warn("Cannot setup subnet MAC {} on DPN {}, null vpnName", srcMacAddress, dpnId);
            return;
        }

        Uint32 vpnId = vpnUtil.getVpnId(vpnName);
        Uint32 subnetVpnId = vpnUtil.getVpnId(subnetVpnName);
        if (dpnId.equals(Uint64.ZERO)) {
            /* Apply the MAC on all DPNs in a VPN */
            for (Uint64 dpId : vpnUtil.getDpnsOnVpn(vpnName)) {
                consumer.process(vpnId, dpId, subnetVpnId);
            }
        } else {
            consumer.process(vpnId, dpnId, subnetVpnId);
        }
    }

    private void addGwMac(String srcMacAddress, TypedWriteTransaction<Configuration> tx, Uint32 vpnId, Uint64 dpId,
        Uint32 subnetVpnId) {
        FlowEntity flowEntity = vpnUtil.buildL3vpnGatewayFlow(dpId, srcMacAddress, vpnId, subnetVpnId);
        mdsalManager.addFlow(tx, flowEntity);
    }

    // TODO skitt Fix the exception handling here
    @SuppressWarnings("checkstyle:IllegalCatch")
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private void removeGwMac(String srcMacAddress, TypedReadWriteTransaction<Configuration> tx, Uint32 vpnId,
            Uint64 dpId, Uint32 subnetVpnId) throws ExecutionException, InterruptedException {
        mdsalManager.removeFlow(tx, dpId,
            VpnUtil.getL3VpnGatewayFlowRef(NwConstants.L3_GW_MAC_TABLE, dpId, vpnId, srcMacAddress, subnetVpnId),
            NwConstants.L3_GW_MAC_TABLE);
    }

    @Override
    public void addRouterGwMacFlow(String routerName, String routerGwMac, Uint64 dpnId, Uuid extNetworkId,
            String subnetVpnName, TypedWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        setupRouterGwMacFlow(routerName, routerGwMac, dpnId, extNetworkId,
            vpnId -> addSubnetMacIntoVpnInstance(vpnId, subnetVpnName, routerGwMac, dpnId, confTx), "Installing");
    }

    @Override
    public void removeRouterGwMacFlow(String routerName, String routerGwMac, Uint64 dpnId, Uuid extNetworkId,
            String subnetVpnName, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        setupRouterGwMacFlow(routerName, routerGwMac, dpnId, extNetworkId,
            vpnId -> removeSubnetMacFromVpnInstance(vpnId, subnetVpnName, routerGwMac, dpnId, confTx), "Removing");
    }

    private void setupRouterGwMacFlow(String routerName, String routerGwMac, Uint64 dpnId, Uuid extNetworkId,
            InterruptibleCheckedConsumer<String, ExecutionException> consumer, String operation)
            throws ExecutionException, InterruptedException {
        if (routerGwMac == null) {
            LOG.warn("Failed to handle router GW flow in GW-MAC table. MAC address is missing for router-id {}",
                routerName);
            return;
        }

        if (dpnId == null || Uint64.ZERO.equals(dpnId)) {
            LOG.info("setupRouterGwMacFlow: DPN id is missing for router-id {}",
                routerName);
            return;
        }

        Uuid vpnId = vpnUtil.getExternalNetworkVpnId(extNetworkId);
        if (vpnId == null) {
            LOG.warn("Network {} is not associated with VPN", extNetworkId.getValue());
            return;
        }

        LOG.info("{} router GW MAC flow for router-id {} on switch {}", operation, routerName, dpnId);
        consumer.accept(vpnId.getValue());
    }

    @Override
    public void addArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            Uint64 dpnId, Uuid extNetworkId) {

        if (dpnId == null || Uint64.ZERO.equals(dpnId)) {
            LOG.warn("Failed to install arp responder flows for router {}. DPN id is missing.", id);
            return;
        }

        String extInterfaceName = elanService.getExternalElanInterface(extNetworkId.getValue(), dpnId);
        if (extInterfaceName != null) {
            doAddArpResponderFlowsToExternalNetworkIps(
                    id, fixedIps, macAddress, dpnId, extInterfaceName);
            return;
        }

        LOG.warn("Failed to install responder flows for {}. No external interface found for DPN id {}", id, dpnId);

        if (!upgradeState.isUpgradeInProgress()) {
            return;
        }

        // The following through the end of the function deals with an upgrade scenario where the neutron configuration
        // is restored before the OVS switches reconnect. In such a case, the elan-dpn-interfaces entries will be
        // missing from the operational data store. In order to mitigate this we use DataTreeEventCallbackRegistrar
        // to wait for the exact operational md-sal object we need to contain the external interface we need.

        LOG.info("Upgrade in process, waiting for an external interface to appear on dpn {} for elan {}",
                dpnId, extNetworkId.getValue());

        InstanceIdentifier<DpnInterfaces> dpnInterfacesIid =
                            elanService.getElanDpnInterfaceOperationalDataPath(extNetworkId.getValue(), dpnId);

        eventCallbacks.onAddOrUpdate(LogicalDatastoreType.OPERATIONAL, dpnInterfacesIid, (unused, alsoUnused) -> {
            LOG.info("Reattempting write of arp responder for external interfaces for external network {}",
                    extNetworkId);
            DpnInterfaces dpnInterfaces = elanService.getElanInterfaceInfoByElanDpn(extNetworkId.getValue(), dpnId);
            if (dpnInterfaces == null) {
                LOG.error("Could not retrieve DpnInterfaces for {}, {}", extNetworkId.getValue(), dpnId);
                return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
            }

            String extIfc = null;
            @Nullable List<String> interfaces = dpnInterfaces.getInterfaces();
            if (interfaces != null) {
                for (String dpnInterface : interfaces) {
                    if (interfaceManager.isExternalInterface(dpnInterface)) {
                        extIfc = dpnInterface;
                        break;
                    }
                }
            }

            if (extIfc == null) {
                if (upgradeState.isUpgradeInProgress()) {
                    LOG.info("External interface not found yet in elan {} on dpn {}, keep waiting",
                            extNetworkId.getValue(), dpnInterfaces);
                    return DataTreeEventCallbackRegistrar.NextAction.CALL_AGAIN;
                } else {
                    return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                }
            }

            final String extIfcFinal = extIfc;
            doAddArpResponderFlowsToExternalNetworkIps(id, fixedIps, macAddress, dpnId, extIfcFinal);

            return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
        });

    }

    @Override
    public void addArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            Uint64 dpnId, String extInterfaceName, int lportTag) {
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.debug("No external IPs defined for {}", id);
            return;
        }

        LOG.info("Installing ARP responder flows for {} fixed-ips {} on switch {}", id, fixedIps, dpnId);

        for (String fixedIp : fixedIps) {
            IpVersionChoice ipVersionChoice = VpnUtil.getIpVersionFromString(fixedIp);
            if (ipVersionChoice == IpVersionChoice.IPV6) {
                continue;
            }
            installArpResponderFlowsToExternalNetworkIp(macAddress, dpnId, extInterfaceName, lportTag,
                    fixedIp);
        }
    }

    private void doAddArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            Uint64 dpnId, String extInterfaceName) {
        Interface extInterfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, extInterfaceName);
        if (extInterfaceState == null) {
            LOG.debug("No interface state found for interface {}. Delaying responder flows for {}", extInterfaceName,
                    id);
            return;
        }

        Integer lportTag = extInterfaceState.getIfIndex();
        if (lportTag == null) {
            LOG.debug("No Lport tag found for interface {}. Delaying flows for router-id {}", extInterfaceName, id);
            return;
        }

        if (macAddress == null) {

            LOG.debug("Failed to install arp responder flows for router-gateway-ip {} for router {}."
                    + "External Gw MacAddress is missing.", fixedIps,  id);
            return;
        }

        addArpResponderFlowsToExternalNetworkIps(id, fixedIps, macAddress, dpnId, extInterfaceName, lportTag);
    }

    @Override
    public void removeArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            Uint64 dpnId, Uuid extNetworkId) {

        if (dpnId == null || Uint64.ZERO.equals(dpnId)) {
            LOG.warn("Failed to remove arp responder flows for router {}. DPN id is missing.", id);
            return;
        }

        String extInterfaceName = elanService.getExternalElanInterface(extNetworkId.getValue(), dpnId);
        if (extInterfaceName == null) {
            LOG.warn("Failed to remove responder flows for {}. No external interface found for DPN id {}", id, dpnId);
            return;
        }

        Interface extInterfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, extInterfaceName);
        if (extInterfaceState == null) {
            LOG.debug("No interface state found for interface {}. Delaying responder flows for {}", extInterfaceName,
                    id);
            return;
        }

        Integer lportTag = extInterfaceState.getIfIndex();
        if (lportTag == null) {
            LOG.debug("No Lport tag found for interface {}. Delaying flows for router-id {}", extInterfaceName, id);
            return;
        }

        if (macAddress == null) {

            LOG.debug("Failed to remove arp responder flows for router-gateway-ip {} for router {}."
                    + "External Gw MacAddress is missing.", fixedIps,  id);
            return;
        }

        removeArpResponderFlowsToExternalNetworkIps(id, fixedIps, dpnId,
                extInterfaceName, lportTag);
    }

    @Override
    public void removeArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps,
            Uint64 dpnId, String extInterfaceName, int lportTag) {
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.debug("No external IPs defined for {}", id);
            return;
        }

        LOG.info("Removing ARP responder flows for {} fixed-ips {} on switch {}", id, fixedIps, dpnId);

        for (String fixedIp : fixedIps) {
            removeArpResponderFlowsToExternalNetworkIp(dpnId, lportTag, fixedIp, extInterfaceName);
        }
    }

    @Override
    public String getPrimaryRdFromVpnInstance(VpnInstance vpnInstance) {
        return VpnUtil.getPrimaryRd(vpnInstance);
    }

    private void installArpResponderFlowsToExternalNetworkIp(String macAddress, Uint64 dpnId,
            String extInterfaceName, int lportTag, String fixedIp) {
        // reset the split-horizon bit to allow traffic to be sent back to the
        // provider port
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(
                new InstructionWriteMetadata(Uint64.ZERO,
                        MetaDataUtil.METADATA_MASK_SH_FLAG).buildInstruction(1));
        instructions.addAll(
                ArpResponderUtil.getExtInterfaceInstructions(interfaceManager, itmRpcService, extInterfaceName,
                        fixedIp, macAddress));
        ArpReponderInputBuilder builder = new ArpReponderInputBuilder().setDpId(dpnId.toJava())
                .setInterfaceName(extInterfaceName).setSpa(fixedIp).setSha(macAddress).setLportTag(lportTag);
        builder.setInstructions(instructions);
        elanService.addArpResponderFlow(builder.buildForInstallFlow());
    }

    private void removeArpResponderFlowsToExternalNetworkIp(Uint64 dpnId, Integer lportTag, String fixedIp,
            String extInterfaceName) {
        ArpResponderInput arpInput = new ArpReponderInputBuilder()
                .setDpId(dpnId.toJava()).setInterfaceName(extInterfaceName)
                .setSpa(fixedIp).setLportTag(lportTag).buildForRemoveFlow();
        elanService.removeArpResponderFlow(arpInput);
    }

    @Override
    public void onSubnetAddedToVpn(Subnetmap subnetmap, boolean isBgpVpn, Long elanTag) {
        vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, isBgpVpn, elanTag);
    }

    @Override
    public void onSubnetDeletedFromVpn(Subnetmap subnetmap, boolean isBgpVpn) {
        vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmap, isBgpVpn);
    }

    @Override
    @Nullable
    public VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        return vpnUtil.getVpnInstance(vpnInstanceName);
    }

    @Override
    public String getVpnRd(TypedReadTransaction<Configuration> confTx, String vpnName) {
        return VpnUtil.getVpnRd(confTx, vpnName);
    }

    @Override
    public String getVpnRd(DataBroker broker, String vpnName) {
        return vpnUtil.getVpnRd(vpnName);
    }

    @Override
    public VpnPortipToPort getNeutronPortFromVpnPortFixedIp(TypedReadTransaction<Configuration> confTx, String vpnName,
        String fixedIp) {
        return VpnUtil.getNeutronPortFromVpnPortFixedIp(confTx, vpnName, fixedIp);
    }

    @Override
    @Nullable
    public VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        return vpnUtil.getNeutronPortFromVpnPortFixedIp(vpnName, fixedIp);
    }

    @Override
    public Set<VpnTarget> getRtListForVpn(String vpnName) {
        return vpnUtil.getRtListForVpn(dataBroker, vpnName);
    }

    @Override
    public void updateRouteTargetsToSubnetAssociation(Set<VpnTarget> routeTargets, String cidr, String vpnName) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
            for (VpnTarget rt : routeTargets) {
                String rtValue = rt.getVrfRTValue();
                switch (rt.getVrfRTType()) {
                    case ImportExtcommunity:
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.IRT, cidr,  vpnName,
                                tx, false/*isAssociationRemoved*/);
                        break;
                    case ExportExtcommunity:
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.ERT, cidr, vpnName,
                                tx, false/*isAssociationRemoved*/);
                        break;
                    case Both:
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.IRT, cidr, vpnName,
                                tx, false/*isAssociationRemoved*/);
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.ERT, cidr, vpnName,
                                tx, false/*isAssociationRemoved*/);
                        break;
                    default:
                        LOG.error("updateRouteTargetsToSubnetAssociation: Invalid rt-type {} for vpn {}"
                                + " subnet-cidr {}", rt.getVrfRTType(), vpnName, cidr);
                        break;
                }
            }
        }), LOG, "updateRouteTargetsToSubnetAssociation: Failed for vpn {} subnet-cidr {}", vpnName, cidr);
        LOG.info("updateRouteTargetsToSubnetAssociation: Updated RT to subnet association for vpn {} cidr {}",
                vpnName, cidr);
    }

    @Override
    public void removeRouteTargetsToSubnetAssociation(Set<VpnTarget> routeTargets, String cidr, String vpnName) {
        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
            for (VpnTarget rt : routeTargets) {
                String rtValue = rt.getVrfRTValue();
                switch (rt.getVrfRTType()) {
                    case ImportExtcommunity:
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.IRT, cidr, vpnName, tx,
                                true/*isAssociationRemoved*/);
                        break;
                    case ExportExtcommunity:
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.ERT, cidr, vpnName, tx,
                                true/*isAssociationRemoved*/);
                        break;
                    case Both:
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.IRT, cidr, vpnName, tx,
                                true/*isAssociationRemoved*/);
                        addSubnetAssociationOperationToTx(rtValue, RouteTarget.RtType.ERT, cidr, vpnName, tx,
                                true/*isAssociationRemoved*/);
                        break;
                    default:
                        LOG.error("removeRouteTargetsToSubnetAssociation: Invalid route-target type {} for vpn {}"
                                + " subnet-cidr {}", rt.getVrfRTType(), vpnName, cidr);
                        break;
                }
            }
        }), LOG, "removeRouteTargetsToSubnetAssociation: Failed for vpn {} subnet-cidr {}", vpnName, cidr);
        LOG.info("removeRouteTargetsToSubnetAssociation: vpn {} cidr {}", vpnName, cidr);
    }

    private boolean doesExistingVpnsHaveConflictingSubnet(Set<VpnTarget> routeTargets, String subnetCidr) {
        Set<RouteTarget> routeTargetSet = vpnUtil.getRouteTargetSet(routeTargets);
        for (RouteTarget routerTarget : routeTargetSet) {
            if (routerTarget.getAssociatedSubnet() != null) {
                for (int i = 0; i < routerTarget.getAssociatedSubnet().size(); i++) {
                    AssociatedSubnet associatedSubnet =
                            new ArrayList<AssociatedSubnet>(routerTarget.nonnullAssociatedSubnet().values()).get(i);
                    if (VpnUtil.areSubnetsOverlapping(associatedSubnet.getCidr(), subnetCidr)) {
                        return true;
                    }
                    if (routerTarget.getRtType() == RouteTarget.RtType.ERT) {
                        /* Check if there are multiple exports for the input iRT value (iRT in routeTargets)
                         *  Example : (1) iRT=A eRT=B subnet-range=S1; OK
                         *            (2) iRT=A eRT=B subnet-range=S1; OK
                         *            (3) iRT=B eRT=A subnet-range=S2; NOK
                         * Check if (1) and (2) are importing the same subnet-range routes to (3) */
                        List<AssociatedVpn> multipleAssociatedVpn
                                = new ArrayList<AssociatedVpn>(associatedSubnet.nonnullAssociatedVpn().values());
                        if (multipleAssociatedVpn != null && multipleAssociatedVpn.size() > 1) {
                            LOG.error("doesExistingVpnsHaveConflictingSubnet: There is an indirect complete  overlap"
                                    + " for subnet CIDR {} for rt {} rtType {}", subnetCidr, routerTarget.getRt(),
                                    routerTarget.getRtType());
                            return true;
                        }
                        for (int j = i + 1; j < routerTarget.getAssociatedSubnet().size(); j++) {
                            if (VpnUtil.areSubnetsOverlapping(associatedSubnet.getCidr(),
                                    new ArrayList<AssociatedSubnet>(routerTarget.nonnullAssociatedSubnet()
                                            .values()).get(j).getCidr())) {
                                LOG.error("doesExistingVpnsHaveConflictingSubnet: There is an indirect paartial"
                                                + " overlap for subnet CIDR {} for rt {} rtType {}", subnetCidr,
                                        routerTarget.getRt(), routerTarget.getRtType());
                                return true;
                            }
                        }
                    }
                }
                /* Check if there are indirect EXPORTS for the eRT value (eRT in routeTargets)
                 *  Example : (1) iRT=A eRT=B subnet-range=S1; OK
                 *            (2) iRT=B eRT=A subnet-range=S2; OK
                 *            (3) iRT=A eRT=B subnet-range=S1; NOK
                 * If associatedSubnet is non-null for a routeTarget in (2),
                 * it may have already imported routes from (1) */
                if (routerTarget.getRtType() == RouteTarget.RtType.IRT) {
                    try {
                        Optional<RouteTarget> indirectRts = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.OPERATIONAL, VpnUtil.getRouteTargetsIdentifier(
                                        routerTarget.getRt(), RouteTarget.RtType.ERT));
                        if (indirectRts.isPresent() && indirectRts.get().getAssociatedSubnet() != null
                                && routerTarget.getAssociatedSubnet() != null) {
                            for (AssociatedSubnet associatedSubnet : indirectRts.get().getAssociatedSubnet().values()) {
                                if (VpnUtil.areSubnetsOverlapping(associatedSubnet.getCidr(), subnetCidr)) {
                                    LOG.error("doesExistingVpnsHaveConflictingSubnet: There is an indirect overlap for"
                                            + " subnet CIDR {} for rt {} rtType {}", subnetCidr, routerTarget.getRt(),
                                            routerTarget.getRtType());
                                    return true;
                                }
                            }
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        LOG.error("doesExistingVpnsHaveConflictingSubnet: Failed to read route targets to subnet"
                                + "association for rt {} type {} subnet-cidr {}", routerTarget.getRt(),
                                RouteTarget.RtType.ERT, subnetCidr);
                        return true; //Fail subnet association to avoid further damage to the data-stores
                    }
                }
            } else {
                LOG.info("doesExistingVpnsHaveConflictingSubnet: No prior associated subnets for rtVal {} rtType {}",
                        routerTarget.getRt(), routerTarget.getRtType());
            }
        }
        return false;
    }

    private void addSubnetAssociationOperationToTx(String rt, RouteTarget.RtType rtType, String cidr,
                                                   String vpnName, TypedReadWriteTransaction<Operational> tx,
                                                   boolean isAssociationRemoved)
            throws InterruptedException, ExecutionException {
        if (isAssociationRemoved) {
            //Remove RT-Subnet-Vpn Association
            Optional<AssociatedSubnet> associatedSubnet =
                tx.read(VpnUtil.getAssociatedSubnetIdentifier(rt, rtType, cidr)).get();
            boolean deleteParent = false;
            if (associatedSubnet.isPresent()) {
                List<AssociatedVpn> associatedVpns
                        = new ArrayList<>(associatedSubnet.get().nonnullAssociatedVpn().values());
                if (associatedVpns == null || associatedVpns.isEmpty()) {
                    deleteParent = true;
                } else {
                    for (Iterator<AssociatedVpn> iterator = associatedVpns.iterator(); iterator.hasNext();) {
                        AssociatedVpn associatedVpn = iterator.next();
                        if (Objects.equals(associatedVpn.getName(), vpnName)) {
                            iterator.remove();
                            break;
                        }
                    }
                    if (associatedVpns.isEmpty()) {
                        deleteParent = true;
                    }
                }
            }
            if (deleteParent) {
                deleteParentForSubnetToVpnAssociation(rt, rtType, cidr, tx);
            } else {
                //Some other VPNs are also part of this rtVal, rtType and subnetCidr combination.
                //Delete only this AssociatedVpn Object
                tx.delete(VpnUtil.getAssociatedSubnetAndVpnIdentifier(rt, rtType, cidr, vpnName));
                LOG.debug("addSubnetAssocOperationToTx: Removed vpn {} from association rt {} rtType {} cidr {}",
                        vpnName, rt, rtType, cidr);
            }
        } else {
            //Add RT-Subnet-Vpn Association
            tx.mergeParentStructurePut(VpnUtil.getAssociatedSubnetAndVpnIdentifier(rt, rtType, cidr, vpnName),
                    VpnUtil.buildAssociatedSubnetAndVpn(vpnName));
        }
    }

    private void deleteParentForSubnetToVpnAssociation(String rt, RouteTarget.RtType rtType,
                                                String cidr, TypedReadWriteTransaction<Operational> tx)
            throws InterruptedException, ExecutionException {
        //Check if you need to delete rtVal+rtType or just the subnetCidr
        InstanceIdentifier<RouteTarget> rtIdentifier = InstanceIdentifier
                .builder(SubnetsAssociatedToRouteTargets.class).child(RouteTarget.class,
                        new RouteTargetKey(rt, rtType)).build();
        Optional<RouteTarget> rtToSubnetsAssociation = tx.read(rtIdentifier).get();
        if (rtToSubnetsAssociation.isPresent()) {
            List<AssociatedSubnet> associatedSubnets = new ArrayList<>(rtToSubnetsAssociation.get()
                    .nonnullAssociatedSubnet().values());
            if (associatedSubnets != null && !associatedSubnets.isEmpty()) {
                for (Iterator<AssociatedSubnet> iterator = associatedSubnets.iterator(); iterator.hasNext(); ) {
                    if (Objects.equals(iterator.next().getCidr(), cidr)) {
                        iterator.remove();
                        break;
                    }
                }
                if (associatedSubnets.isEmpty()) {
                    //The entire rt to subnet association is empty
                    //Delete the RouteTarget object
                    tx.delete(rtIdentifier);
                    LOG.debug("deleteParentForSubnetToVpnAssociation: Removed rt {} rtType {} from association,",
                            rt, rtType);
                } else {
                    //Some other VPNs are also part of this rtVal, rtType combination
                    //Delete only this AssociatedSubnet
                    tx.delete(VpnUtil.getAssociatedSubnetIdentifier(rt, rtType, cidr));
                    LOG.debug("deleteParentForSubnetToVpnAssociation: Removed cidr {} from association rt {}"
                            + " rtType {}", cidr, rt, rtType);
                }
            }
        }
    }

    @Override
    public boolean checkForOverlappingSubnets(Uuid network, List<Subnetmap> subnetmapList, Uuid vpn,
                                       Set<VpnTarget> routeTargets, List<String> failedNwList) {
        for (Subnetmap subnetmap : subnetmapList) {
            //Check if any other subnet that is already part of a different vpn with same rt, has overlapping CIDR
            if (checkExistingSubnetWithSameRoutTargets(routeTargets, vpn, subnetmap, failedNwList)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkExistingSubnetWithSameRoutTargets(Set<VpnTarget> routeTargets, Uuid vpn, Subnetmap subnetmap,
                                                   List<String> failedNwList) {
        String cidr = String.valueOf(subnetmap.getSubnetIp());
        boolean subnetExistsWithSameRt = doesExistingVpnsHaveConflictingSubnet(routeTargets, cidr);
        if (subnetExistsWithSameRt) {
            failedNwList.add(String.format("Another subnet with the same/overlapping cidr %s as subnet %s"
                    + " is already associated to a vpn with routeTargets %s. Ignoring subnet addition to vpn"
                    + " %s", cidr, subnetmap.getId().getValue(), routeTargets.toString(), vpn.getValue()));
        }
        return subnetExistsWithSameRt;
    }
}
