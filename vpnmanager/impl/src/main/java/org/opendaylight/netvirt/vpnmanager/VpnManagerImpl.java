/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.UpgradeState;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
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
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
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
                          final ItmRpcService itmRpcService) {
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
            if (result.get().isSuccessful()) {
                LOG.debug("Created IdPool for Pseudo Port tags");
            } else {
                Collection<RpcError> errors = result.get().getErrors();
                StringBuilder errMsg = new StringBuilder();
                for (RpcError err : errors) {
                    errMsg.append(err.getMessage()).append("\n");
                }
                LOG.error("IdPool creation for PseudoPort tags failed. Reasons: {}", errMsg);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for Pseudo Port tags", e);
        }
    }

    @Override
    public void addExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
        int label,RouteOrigin origin) {
        LOG.info("Adding extra route with destination {}, nextHop {}, label{} and origin {}",
            destination, nextHop, label, origin);
        VpnInstanceOpDataEntry vpnOpEntry = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
        Boolean isVxlan = VpnUtil.isL3VpnOverVxLan(vpnOpEntry.getL3vni());
        VrfEntry.EncapType encapType = VpnUtil.getEncapType(isVxlan);
        addExtraRoute(vpnName, destination, nextHop, rd, routerID, vpnOpEntry.getL3vni(),
                origin,/*intfName*/ null, null /*Adjacency*/, encapType, null);
    }

    @Override
    public void addExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
            Long l3vni, RouteOrigin origin, String intfName, Adjacency operationalAdj,
            VrfEntry.EncapType encapType, WriteTransaction writeConfigTxn) {
        if (writeConfigTxn == null) {
            String finalNextHop = nextHop;
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
                addExtraRoute(vpnName, destination, finalNextHop, rd, routerID, l3vni, origin, intfName, operationalAdj,
                        encapType, tx)),
                    LOG, "Error adding extra route");
            return;
        }

        //add extra route to vpn mapping; advertise with nexthop as tunnel ip
        VpnUtil.syncUpdate(
                dataBroker,
                LogicalDatastoreType.OPERATIONAL,
                VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, rd != null ? rd : routerID,
                        destination),
                VpnUtil.getVpnToExtraroute(destination, Collections.singletonList(nextHop)));

        BigInteger dpnId = null;
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

        String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);

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
            String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
            long newLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                    VpnUtil.getNextHopLabelKey(dstVpnRd, destination));
            if (newLabel == 0) {
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
                    fibManager.refreshVrfEntry(primaryRd, destination);
                } else {
                    L3vpnInput input = new L3vpnInput().setNextHop(operationalAdj).setNextHopIp(nextHop).setL3vni(l3vni)
                            .setPrimaryRd(primaryRd).setVpnName(vpnName).setDpnId(dpnId)
                            .setEncapType(encapType).setRd(rd).setRouteOrigin(origin);
                    L3vpnRegistry.getRegisteredPopulator(encapType).populateFib(input, writeConfigTxn);
                }
            }
        }
    }

    @Override
    public void delExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID) {
        LOG.info("Deleting extra route with destination {} and nextHop {}", destination, nextHop);
        delExtraRoute(vpnName, destination, nextHop, rd, routerID, null, null);
    }

    @Override
    public void delExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
            String intfName, WriteTransaction writeConfigTxn) {
        if (writeConfigTxn == null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
                delExtraRoute(vpnName, destination, nextHop, rd, routerID, intfName, tx)),
                    LOG, "Error deleting extra route");
            return;
        }
        BigInteger dpnId = null;
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
            String primaryRd = VpnUtil.getVpnRd(dataBroker, vpnName);
            removePrefixFromBGP(primaryRd, rd, vpnName, destination, nextHop, tunnelIp, dpnId, writeConfigTxn);
            LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName, rd);
        } else {
            // add FIB route directly
            fibManager.removeOrUpdateFibEntry(routerID, destination, tunnelIp, writeConfigTxn);
            LOG.info("delExtraRoute: Removed extra route {} from interface {} for rd {}", destination, intfName,
                    routerID);
        }
    }

 // TODO Clean up the exception handling
    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removePrefixFromBGP(String primaryRd, String rd, String vpnName, String prefix, String nextHop,
                                     String tunnelIp, BigInteger dpnId, WriteTransaction writeConfigTxn) {
        try {
            LOG.info("removePrefixFromBGP: VPN WITHDRAW: Removing Fib Entry rd {} prefix {} nexthop {}", rd, prefix,
                    nextHop);
            String vpnNamePrefixKey = VpnUtil.getVpnNamePrefixKey(vpnName, prefix);
            synchronized (vpnNamePrefixKey.intern()) {
                Optional<Routes> optVpnExtraRoutes = VpnExtraRouteHelper
                        .getVpnExtraroutes(dataBroker, vpnName, rd, prefix);
                if (optVpnExtraRoutes.isPresent()) {
                    List<String> nhList = optVpnExtraRoutes.get().getNexthopIpList();
                    if (nhList != null && nhList.size() > 1) {
                        // If nhList is more than 1, just update vpntoextraroute and prefixtointerface DS
                        // For other cases, remove the corresponding tep ip from fibentry and withdraw prefix
                        nhList.remove(nextHop);
                        VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, rd, prefix),
                                VpnUtil.getVpnToExtraroute(prefix, nhList));
                        MDSALUtil.syncDelete(dataBroker,
                                LogicalDatastoreType.CONFIGURATION, VpnExtraRouteHelper.getUsedRdsIdentifier(
                                VpnUtil.getVpnId(dataBroker, vpnName), prefix, nextHop));
                        LOG.debug("removePrefixFromBGP: Removed vpn-to-extraroute with rd {} prefix {} nexthop {}",
                                rd, prefix, nextHop);
                        fibManager.refreshVrfEntry(primaryRd, prefix);
                        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
                        Optional<Prefixes> prefixToInterface = VpnUtil.getPrefixToInterface(dataBroker, vpnId, nextHop);
                        if (prefixToInterface.isPresent()) {
                            writeConfigTxn.delete(LogicalDatastoreType.OPERATIONAL,
                                    VpnUtil.getAdjacencyIdentifier(prefixToInterface.get().getVpnInterfaceName(),
                                            prefix));
                        }
                        LOG.info("VPN WITHDRAW: removePrefixFromBGP: Removed Fib Entry rd {} prefix {} nexthop {}",
                                rd, prefix, tunnelIp);
                        return;
                    }
                }
                fibManager.removeOrUpdateFibEntry(primaryRd, prefix, tunnelIp, writeConfigTxn);
                if (VpnUtil.isEligibleForBgp(primaryRd, vpnName, dpnId, null /*networkName*/)) {
                    // TODO: Might be needed to include nextHop here
                    bgpManager.withdrawPrefix(rd, prefix);
                }
            }
            LOG.info("removePrefixFromBGP: VPN WITHDRAW: Removed Fib Entry rd {} prefix {} nexthop {}", rd, prefix,
                    nextHop);
        } catch (RuntimeException e) {
            LOG.error("removePrefixFromBGP: Delete prefix {} rd {} nextHop {} failed", prefix, rd, nextHop);
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
        } catch (ReadFailedException e) {
            throw new RuntimeException("Error reading VPN " + vpnsIdentifier, e);
        }
        LOG.trace("isVPNConfigured: VPNs are configured on the system.");
        return true;
    }

    @Override
    public List<BigInteger> getDpnsOnVpn(String vpnInstanceName) {
        return VpnUtil.getDpnsOnVpn(dataBroker, vpnInstanceName);
    }

    @Override
    public boolean existsVpn(String vpnName) {
        return VpnUtil.getVpnInstance(dataBroker, vpnName) != null;
    }

    @Override
    public void addSubnetMacIntoVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, WriteTransaction tx) {
        setupSubnetMacInVpnInstance(vpnName, subnetVpnName, srcMacAddress, dpnId,
            (vpnId, dpId, subnetVpnId) -> addGwMac(srcMacAddress, tx, vpnId, dpId, subnetVpnId));
    }

    @Override
    public void removeSubnetMacFromVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, WriteTransaction tx) {
        setupSubnetMacInVpnInstance(vpnName, subnetVpnName, srcMacAddress, dpnId,
            (vpnId, dpId, subnetVpnId) -> removeGwMac(srcMacAddress, tx, vpnId, dpId, subnetVpnId));
    }

    @FunctionalInterface
    private interface VpnInstanceSubnetMacSetupMethod {
        void process(long vpnId, BigInteger dpId, long subnetVpnId);
    }

    private void setupSubnetMacInVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, VpnInstanceSubnetMacSetupMethod consumer) {
        if (vpnName == null) {
            LOG.warn("Cannot setup subnet MAC {} on DPN {}, null vpnName", srcMacAddress, dpnId);
            return;
        }

        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        long subnetVpnId = VpnUtil.getVpnId(dataBroker, subnetVpnName);
        if (dpnId.equals(BigInteger.ZERO)) {
            /* Apply the MAC on all DPNs in a VPN */
            for (BigInteger dpId : VpnUtil.getDpnsOnVpn(dataBroker, vpnName)) {
                consumer.process(vpnId, dpId, subnetVpnId);
            }
        } else {
            consumer.process(vpnId, dpnId, subnetVpnId);
        }
    }

    private void addGwMac(String srcMacAddress, WriteTransaction tx, long vpnId, BigInteger dpId, long subnetVpnId) {
        FlowEntity flowEntity = VpnUtil.buildL3vpnGatewayFlow(dataBroker, dpId, srcMacAddress, vpnId, subnetVpnId);
        mdsalManager.addFlowToTx(flowEntity, tx);
    }

    private void removeGwMac(String srcMacAddress, WriteTransaction tx, long vpnId, BigInteger dpId, long subnetVpnId) {
        FlowEntity flowEntity = VpnUtil.buildL3vpnGatewayFlow(dataBroker, dpId, srcMacAddress, vpnId, subnetVpnId);
        mdsalManager.removeFlowToTx(flowEntity, tx);
    }

    @Override
    public void addRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            String subnetVpnName, WriteTransaction writeTx) {
        setupRouterGwMacFlow(routerName, routerGwMac, dpnId, extNetworkId, writeTx,
            (vpnId, tx) -> addSubnetMacIntoVpnInstance(vpnId, subnetVpnName, routerGwMac, dpnId, tx), "Installing");
    }

    @Override
    public void removeRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            String subnetVpnName, WriteTransaction writeTx) {
        setupRouterGwMacFlow(routerName, routerGwMac, dpnId, extNetworkId, writeTx,
            (vpnId, tx) -> removeSubnetMacFromVpnInstance(vpnId, subnetVpnName, routerGwMac, dpnId, tx), "Removing");
    }

    @FunctionalInterface
    private interface RouterGwMacFlowSetupMethod {
        void process(String vpnId, WriteTransaction tx);
    }

    private void setupRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            WriteTransaction writeTx, RouterGwMacFlowSetupMethod consumer, String operation) {
        if (routerGwMac == null) {
            LOG.warn("Failed to handle router GW flow in GW-MAC table. MAC address is missing for router-id {}",
                    routerName);
            return;
        }

        if (dpnId == null || BigInteger.ZERO.equals(dpnId)) {
            LOG.info("setupRouterGwMacFlow: DPN id is missing for router-id {}",
                    routerName);
            return;
        }

        Uuid vpnId = VpnUtil.getExternalNetworkVpnId(dataBroker, extNetworkId);
        if (vpnId == null) {
            LOG.warn("Network {} is not associated with VPN", extNetworkId.getValue());
            return;
        }

        LOG.info("{} router GW MAC flow for router-id {} on switch {}", operation, routerName, dpnId);
        if (writeTx == null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                tx -> consumer.process(vpnId.getValue(), tx)), LOG, "Commit transaction");
        } else {
            consumer.process(vpnId.getValue(), writeTx);
        }
    }

    @Override
    public void addArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, Uuid extNetworkId, WriteTransaction writeTx) {

        if (dpnId == null || BigInteger.ZERO.equals(dpnId)) {
            LOG.warn("Failed to install arp responder flows for router {}. DPN id is missing.", id);
            return;
        }

        String extInterfaceName = elanService.getExternalElanInterface(extNetworkId.getValue(), dpnId);
        if (extInterfaceName != null) {
            doAddArpResponderFlowsToExternalNetworkIps(
                    id, fixedIps, macAddress, dpnId, extNetworkId, writeTx, extInterfaceName);
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
            for (String dpnInterface : dpnInterfaces.getInterfaces()) {
                if (interfaceManager.isExternalInterface(dpnInterface)) {
                    extIfc = dpnInterface;
                    break;
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
            ListenableFuture<Void> listenableFuture =
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> doAddArpResponderFlowsToExternalNetworkIps(
                    id, fixedIps, macAddress, dpnId, extNetworkId, tx, extIfcFinal));
            ListenableFutures.addErrorLogging(listenableFuture, LOG,
                    "Error while configuring arp responder for ext. interface");

            return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
        });

    }

    @Override
    public void addArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, String extInterfaceName, int lportTag, WriteTransaction writeTx) {
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.debug("No external IPs defined for {}", id);
            return;
        }

        LOG.info("Installing ARP responder flows for {} fixed-ips {} on switch {}", id, fixedIps, dpnId);

        if (writeTx == null) {
            ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                tx -> {
                    for (String fixedIp : fixedIps) {
                        IpVersionChoice ipVersionChoice = VpnUtil.getIpVersionFromString(fixedIp);
                        if (ipVersionChoice == IpVersionChoice.IPV6) {
                            continue;
                        }
                        installArpResponderFlowsToExternalNetworkIp(macAddress, dpnId, extInterfaceName, lportTag,
                                fixedIp);
                    }
                });
            ListenableFutures.addErrorLogging(future, LOG, "Commit transaction");
        } else {
            for (String fixedIp : fixedIps) {
                IpVersionChoice ipVersionChoice = VpnUtil.getIpVersionFromString(fixedIp);
                if (ipVersionChoice == IpVersionChoice.IPV6) {
                    continue;
                }
                installArpResponderFlowsToExternalNetworkIp(macAddress, dpnId, extInterfaceName, lportTag,
                        fixedIp);
            }
        }
    }

    private void doAddArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
                            BigInteger dpnId, Uuid extNetworkId, WriteTransaction writeTx, String extInterfaceName) {
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

        addArpResponderFlowsToExternalNetworkIps(id, fixedIps, macAddress, dpnId, extInterfaceName, lportTag,
                writeTx);
    }

    @Override
    public void removeArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, Uuid extNetworkId) {

        if (dpnId == null || BigInteger.ZERO.equals(dpnId)) {
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
            BigInteger dpnId, String extInterfaceName, int lportTag) {
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

    @Override
    public List<MatchInfoBase> getEgressMatchesForVpn(String vpnName) {
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == VpnConstants.INVALID_ID) {
            LOG.warn("No VPN id found for {}", vpnName);
            return Collections.emptyList();
        }

        return Collections
                .singletonList(new NxMatchRegister(VpnConstants.VPN_REG_ID, vpnId, MetaDataUtil.getVpnIdMaskForReg()));
    }

    private void installArpResponderFlowsToExternalNetworkIp(String macAddress, BigInteger dpnId,
            String extInterfaceName, int lportTag, String fixedIp) {
        // reset the split-horizon bit to allow traffic to be sent back to the
        // provider port
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(
                new InstructionWriteMetadata(BigInteger.ZERO, MetaDataUtil.METADATA_MASK_SH_FLAG).buildInstruction(1));
        instructions.addAll(
                ArpResponderUtil.getExtInterfaceInstructions(interfaceManager, itmRpcService, extInterfaceName,
                        fixedIp, macAddress));
        ArpReponderInputBuilder builder = new ArpReponderInputBuilder().setDpId(dpnId)
                .setInterfaceName(extInterfaceName).setSpa(fixedIp).setSha(macAddress).setLportTag(lportTag);
        builder.setInstructions(instructions);
        elanService.addArpResponderFlow(builder.buildForInstallFlow());
    }

    private void removeArpResponderFlowsToExternalNetworkIp(BigInteger dpnId, Integer lportTag, String fixedIp,
            String extInterfaceName) {
        ArpResponderInput arpInput = new ArpReponderInputBuilder().setDpId(dpnId).setInterfaceName(extInterfaceName)
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
    public VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        return VpnUtil.getVpnInstance(broker, vpnInstanceName);
    }

    @Override
    public String getVpnRd(DataBroker broker, String vpnName) {
        return VpnUtil.getVpnRd(broker, vpnName);
    }

    @Override
    public VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        return VpnUtil.getNeutronPortFromVpnPortFixedIp(broker, vpnName, fixedIp);
    }
}
