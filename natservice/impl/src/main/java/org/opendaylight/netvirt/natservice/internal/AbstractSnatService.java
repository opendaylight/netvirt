/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.opendaylight.genius.datastoreutils.ExpectedDataObjectNotFoundException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadMetadata;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.natservice.api.SnatServiceListener;
import org.opendaylight.netvirt.natservice.ha.NatDataUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSnatService implements SnatServiceListener {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSnatService.class);

    static final int LOAD_START = mostSignificantBit(MetaDataUtil.METADATA_MASK_SH_FLAG.intValue());
    static final int LOAD_END = mostSignificantBit(MetaDataUtil.METADATA_MASK_VRFID.intValue() | MetaDataUtil
            .METADATA_MASK_SH_FLAG.intValue());

    protected final DataBroker dataBroker;
    protected final ManagedNewTransactionRunner txRunner;
    protected final IMdsalApiManager mdsalManager;
    protected final IdManagerService idManager;
    private final NAPTSwitchSelector naptSwitchSelector;
    final ItmRpcService itmManager;
    protected final OdlInterfaceRpcService odlInterfaceRpcService;
    protected final IInterfaceManager interfaceManager;
    final IVpnFootprintService vpnFootprintService;
    protected final IFibManager fibManager;
    private final NatDataUtil natDataUtil;
    private final DataTreeEventCallbackRegistrar eventCallbacks;

    AbstractSnatService(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
        final ItmRpcService itmManager, final OdlInterfaceRpcService odlInterfaceRpcService,
        final IdManagerService idManager, final NAPTSwitchSelector naptSwitchSelector,
        final IInterfaceManager interfaceManager,
        final IVpnFootprintService vpnFootprintService,
        final IFibManager fibManager, final NatDataUtil natDataUtil,
        final DataTreeEventCallbackRegistrar eventCallbacks) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.interfaceManager = interfaceManager;
        this.idManager = idManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.vpnFootprintService = vpnFootprintService;
        this.fibManager = fibManager;
        this.natDataUtil = natDataUtil;
        this.eventCallbacks = eventCallbacks;
    }

    protected DataBroker getDataBroker() {
        return dataBroker;
    }

    @Override
    public boolean addSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 primarySwitchId) {
        LOG.info("addSnatAllSwitch : Handle Snat in all switches for router {}", routers.getRouterName());
        String routerName = routers.getRouterName();
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        /*
         * Primary switch handled separately since the pseudo port created may
         * not be present in the switch list on delete.
         */
        addSnat(confTx, routers, primarySwitchId, primarySwitchId);
        for (Uint64 dpnId : switches) {
            if (!Objects.equals(primarySwitchId, dpnId)) {
                addSnat(confTx, routers, primarySwitchId, dpnId);
            }
        }
        return true;
    }

    @Override
    public boolean removeSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId) throws ExecutionException, InterruptedException {
        LOG.info("removeSnatAllSwitch : Handle Snat in all switches for router {}", routers.getRouterName());
        String routerName = routers.getRouterName();
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        /*
         * Primary switch handled separately since the pseudo port created may
         * not be present in the switch list on delete.
         */
        removeSnat(confTx, routers, primarySwitchId, primarySwitchId);
        for (Uint64 dpnId : switches) {
            if (!Objects.equals(primarySwitchId, dpnId)) {
                removeSnat(confTx, routers, primarySwitchId, dpnId);
            }
        }
        return true;
    }

    @Override
    public boolean addSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers, Uint64 primarySwitchId,
                           Uint64 dpnId) {

        // Handle non NAPT switches and NAPT switches separately
        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("addSnat : Handle non NAPT switch {} for router {}", dpnId, routers.getRouterName());
            addSnatCommonEntriesForNonNaptSwitch();
            addSnatSpecificEntriesForNonNaptSwitch();
        } else {
            LOG.info("addSnat : Handle NAPT switch {} for router {}", dpnId, routers.getRouterName());
            addSnatCommonEntriesForNaptSwitch(confTx, routers, dpnId);
            addSnatSpecificEntriesForNaptSwitch(confTx, routers, dpnId);
        }
        return true;
    }

    @Override
    public boolean removeSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) throws ExecutionException, InterruptedException {

        // Handle non NAPT switches and NAPT switches separately
        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("removeSnat : Handle non NAPT switch {} for router {}", dpnId, routers.getRouterName());
            removeSnatCommonEntriesForNonNaptSwitch();
            removeSnatSpecificEntriesForNonNaptSwitch();
        } else {
            LOG.info("removeSnat : Handle NAPT switch {} for router {}", dpnId, routers.getRouterName());
            removeSnatCommonEntriesForNaptSwitch(confTx, routers, dpnId);
            removeSnatSpecificEntriesForNaptSwitch(confTx, routers, dpnId);

        }
        return true;
    }

    @Override
    public boolean addCentralizedRouterAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId) {
        LOG.info("addCentralizedRouterAllSwitch : Handle Snat in all switches for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        addCentralizedRouter(confTx, routers, primarySwitchId, primarySwitchId);
        for (Uint64 dpnId : switches) {
            if (!Objects.equals(primarySwitchId, dpnId)) {
                addCentralizedRouter(confTx, routers, primarySwitchId, dpnId);
            }
        }
        return true;
    }

    @Override
    public boolean removeCentralizedRouterAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId)  throws ExecutionException, InterruptedException {
        LOG.info("removeCentralizedRouterAllSwitch : Handle Snat in all switches for router {}",
                routers.getRouterName());
        boolean isLastRouterDelete = false;
        isLastRouterDelete = NatUtil.isLastExternalRouter(routers.getNetworkId()
                .getValue(), routers.getRouterName(), natDataUtil);
        LOG.info("removeCentralizedRouterAllSwitch : action is delete for router {} and isLastRouterDelete is {}",
                routers.getRouterName(), isLastRouterDelete);
        removeCentralizedRouter(confTx, routers, primarySwitchId, primarySwitchId);
        String routerName = routers.getRouterName();
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        for (Uint64 dpnId : switches) {
            if (!Objects.equals(primarySwitchId, dpnId)) {
                removeCentralizedRouter(confTx, routers, primarySwitchId, dpnId);
            }
        }
        if (isLastRouterDelete) {
            removeLearntIpPorts(routers);
            removeMipAdjacencies(routers);
        }
        return true;
    }

    @Override
    public boolean addCentralizedRouter(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) {
        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("addCentralizedRouter : Handle non NAPT switch {} for router {}",
                    dpnId, routers.getRouterName());
            addCommonEntriesForNonNaptSwitch(confTx, routers, primarySwitchId, dpnId);
        } else {
            LOG.info("addCentralizedRouter : Handle NAPT switch {} for router {}", dpnId, routers.getRouterName());
            addCommonEntriesForNaptSwitch(confTx, routers, dpnId);
        }
        return true;
    }

    @Override
    public boolean removeCentralizedRouter(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) throws ExecutionException, InterruptedException {
        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("removeCentralizedRouter : Handle non NAPT switch {} for router {}",
                    dpnId, routers.getRouterName());
            removeCommonEntriesForNonNaptSwitch(confTx, routers, dpnId);
        } else {
            LOG.info("removeCentralizedRouter : Handle NAPT switch {} for router {}", dpnId, routers.getRouterName());
            removeCommonEntriesForNaptSwitch(confTx, routers, dpnId);
        }
        return true;
    }

    @Override
    public boolean handleRouterUpdate(TypedReadWriteTransaction<Configuration> confTx,
            Routers origRouter, Routers updatedRouter) throws ExecutionException, InterruptedException {
        return true;
    }

    private void addCommonEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 dpnId) {
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        addDefaultFibRouteForSNAT(confTx, dpnId, routerId);
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            //The logic now handle only one external IP per router, others if present will be ignored.
            Uint32 extSubnetId = NatUtil.getExternalSubnetVpnId(dataBroker, externalIp.getSubnetId());
            addInboundTerminatingServiceTblEntry(confTx, dpnId, routerId, extSubnetId);
            addTerminatingServiceTblEntry(confTx, dpnId, routerId);
            break;
        }
    }

    private void removeCommonEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 dpnId) throws ExecutionException, InterruptedException {
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        removeDefaultFibRouteForSNAT(confTx, dpnId, routerId);
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            removeInboundTerminatingServiceTblEntry(confTx, dpnId, routerId);
            removeTerminatingServiceTblEntry(confTx, dpnId, routerId);
            break;
        }
    }

    private void addSnatCommonEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 dpnId) {
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        String externalGwMac = routers.getExtGwMacAddress();
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            //The logic now handle only one external IP per router, others if present will be ignored.
            Uint32 extSubnetId = NatUtil.getExternalSubnetVpnId(dataBroker, externalIp.getSubnetId());
            addInboundFibEntry(confTx, dpnId, externalIp.getIpAddress(), routerId, extSubnetId,
                routers.getNetworkId().getValue(), externalIp.getSubnetId().getValue(), externalGwMac);
            break;
        }
    }

    private void removeSnatCommonEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
        Routers routers, Uint64 dpnId) throws ExecutionException, InterruptedException {
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(confTx, routerName);
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            //The logic now handle only one external IP per router, others if present will be ignored.
            removeInboundFibEntry(confTx, dpnId, externalIp.getIpAddress(), routerId,
                externalIp.getSubnetId().getValue());
            break;
        }
    }


    private void addCommonEntriesForNonNaptSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 primarySwitchId, Uint64 dpnId) {
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        addSnatMissEntry(confTx, dpnId, routerId, routerName, primarySwitchId);
        addDefaultFibRouteForSNAT(confTx, dpnId, routerId);
    }

    private void removeCommonEntriesForNonNaptSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 dpnId) throws ExecutionException, InterruptedException {
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        removeSnatMissEntry(confTx, dpnId, routerId, routerName);
        removeDefaultFibRouteForSNAT(confTx, dpnId, routerId);
    }

    private void addSnatCommonEntriesForNonNaptSwitch() {
        /* Nothing to do here*/
    }

    private void removeSnatCommonEntriesForNonNaptSwitch() {
        /* Nothing to do here*/
    }

    protected abstract void addSnatSpecificEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
        Routers routers, Uint64 dpnId);

    protected abstract void removeSnatSpecificEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
        Routers routers, Uint64 dpnId) throws ExecutionException, InterruptedException;

    protected abstract void addSnatSpecificEntriesForNonNaptSwitch();

    protected abstract void removeSnatSpecificEntriesForNonNaptSwitch();

    private void addInboundFibEntry(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId, String externalIp,
                                    Uint32 routerId, Uint32 extSubnetId, String externalNetId,
                                    String subNetId, String routerMac) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("ConntrackBasedSnatService : installInboundFibEntry : external subnet id is invalid.");
            return;
        }
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extSubnetId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIpv4Destination(externalIp, "32"));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.INBOUND_NAPT_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef = flowRef + "inbound" + externalIp;
        NatUtil.addFlow(confTx, mdsalManager,dpnId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches,
                instructionInfo);
        String rd = NatUtil.getVpnRd(dataBroker, subNetId);
        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
        String ipPrefix = externalIp + "/32";
        NatUtil.addPrefixToInterface(dataBroker, NatUtil.getVpnId(dataBroker, subNetId),
                null, ipPrefix, externalNetId, dpnId, Prefixes.PrefixCue.Nat);

        fibManager.addOrUpdateFibEntry(rd, routerMac, ipPrefix,
                Collections.singletonList(nextHopIp), VrfEntry.EncapType.Mplsgre, extSubnetId,
                Uint32.ZERO, null, externalNetId, RouteOrigin.STATIC, null,
                null, null);
    }

    private void removeInboundFibEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
        String externalIp, Uint32 routerId, String subNetId) throws ExecutionException, InterruptedException {
        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef = flowRef + "inbound" + externalIp;
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef);
        String rd = NatUtil.getVpnRd(dataBroker, subNetId);
        String ipPrefix = externalIp + "/32";
        fibManager.removeFibEntry(rd, ipPrefix, null, null, confTx);
        NatUtil.deletePrefixToInterface(dataBroker, NatUtil.getVpnId(dataBroker, subNetId), ipPrefix);
    }


    private void addTerminatingServiceTblEntry(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                               Uint32 routerId) {
        LOG.info("addTerminatingServiceTblEntry : creating entry for Terminating Service Table "
                + "for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchTunnelId(Uint64.valueOf(routerId)));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                .getVpnIdMetadata(routerId.longValue()), LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(new ActionNxResubmit(NwConstants.PSNAT_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE,
                matches, instructions);
    }

    private void removeTerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                  Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("removeTerminatingServiceTblEntry : creating entry for Terminating Service Table "
            + "for switch {}, routerId {}", dpnId, routerId);

        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef);
    }

    protected void addSnatMissEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
        Uint32 routerId, String routerName, Uint64 primarySwitchId)  {
        LOG.debug("installSnatMissEntry : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = NatUtil.getTunnelInterfaceName(dpnId, primarySwitchId, itmManager);
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        if (ifNamePrimary != null) {
            LOG.debug("installSnatMissEntry : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                interfaceManager, ifNamePrimary, routerId, true);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);
        LOG.debug("installSnatMissEntry : installSnatMissEntry called for dpnId {} with primaryBucket {} ", dpnId,
            listBucketInfo.get(0));
        // Install the select group
        Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME, getGroupIdKey(routerName));
        if (groupId != NatConstants.INVALID_ID) {
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId.longValue(), routerName,
                    GroupTypes.GroupAll, listBucketInfo);
            LOG.debug("installing the PSNAT to NAPTSwitch GroupEntity:{} with GroupId: {}", groupEntity, groupId);
            mdsalManager.addGroup(confTx, groupEntity);

            // Add the flow to send the packet to the group only after group is available in Config datastore
            eventCallbacks.onAddOrUpdate(LogicalDatastoreType.CONFIGURATION,
                    NatUtil.getGroupInstanceId(dpnId, groupId), (unused, newGroupId) -> {
                    LOG.info("group {} is created in the config", groupId);
                    LoggingFutures.addErrorLogging(
                            txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                                innerConfTx -> addSnatMissFlowForGroup(innerConfTx, dpnId, routerId, groupId)),
                            LOG, "Error adding flow for the group {}",groupId);
                    return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                }, Duration.ofSeconds(5), iid -> LOG.error("groupId {} not found in config datastore", groupId));
        } else {
            LOG.error("installSnatMissEntry: Unable to get groupId for routerName:{}", routerName);
        }
    }

    private void addSnatMissFlowForGroup(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId, Uint32 groupId) {
        // Install miss entry pointing to group
        LOG.debug("installSnatMissEntry : buildSnatFlowEntity is called for dpId {}, routerId {} and groupId {}",
            dpnId, routerId, groupId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchEthernetType(0x0800L));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfo = new ArrayList<>();
        actionsInfo.add(new ActionSetFieldTunnelId(Uint64.valueOf(routerId)));
        LOG.debug("installSnatMissEntry : Setting the tunnel to the list of action infos {}", actionsInfo);
        actionsInfo.add(new ActionGroup(groupId.longValue()));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches,
                instructions);
    }

    protected void removeSnatMissEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                       Uint32 routerId, String routerName)
            throws ExecutionException, InterruptedException {
        LOG.debug("removeSnatMissEntry : Removing SNAT miss entry from switch {}", dpnId);
        // Install the select group
        Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME, getGroupIdKey(routerName));
        if (groupId != NatConstants.INVALID_ID) {
            LOG.debug("removeSnatMissEntry : removing the PSNAT to NAPTSwitch on DPN {} with GroupId: {}", dpnId,
                groupId);
            mdsalManager.removeGroup(confTx, dpnId, groupId.longValue());
        } else {
            LOG.error("removeSnatMissEntry: Unable to get groupId for routerName:{}", routerName);
        }
        // Install miss entry pointing to group
        LOG.debug("removeSnatMissEntry : buildSnatFlowEntity is called for dpId {}, routerName {} and groupId {}",
            dpnId, routerName, groupId);

        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef);
    }

    private void addInboundTerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx,
        Uint64 dpnId, Uint32 routerId, Uint32 extSubnetId) {

        //Install the tunnel table entry in NAPT switch for inbound traffic to SNAT IP from a non a NAPT switch.
        LOG.info("installInboundTerminatingServiceTblEntry : creating entry for Terminating Service Table "
                + "for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("installInboundTerminatingServiceTblEntry : external subnet id is invalid.");
            return;
        }
        matches.add(new MatchTunnelId(Uint64.valueOf(extSubnetId)));
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                .getVpnIdMetadata(extSubnetId.longValue()), LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId) + "INBOUND";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    private void removeInboundTerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx,
        Uint64 dpnId, Uint32 routerId) throws ExecutionException, InterruptedException {
        //Install the tunnel table entry in NAPT switch for inbound traffic to SNAT IP from a non a NAPT switch.
        LOG.info("installInboundTerminatingServiceTblEntry : creating entry for Terminating Service Table "
            + "for switch {}, routerId {}", dpnId, routerId);
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId) + "INBOUND";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef);
    }

    private void addDefaultFibRouteForSNAT(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                           Uint32 extNetId) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNetId.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = "DefaultFibRouteForSNAT" + getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, extNetId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches,
                instructions);
    }

    private void removeDefaultFibRouteForSNAT(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
        Uint32 extNetId) throws ExecutionException, InterruptedException {
        String flowRef = "DefaultFibRouteForSNAT" + getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, extNetId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef);
    }

    protected String getFlowRef(Uint64 dpnId, short tableId, Uint32 routerID) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR
            + tableId + NatConstants.FLOWID_SEPARATOR + routerID;
    }

    protected String getGroupIdKey(String routerName) {
        return "snatmiss." + routerName;
    }

    private void removeMipAdjacencies(Routers routers) {
        LOG.info("removeMipAdjacencies for router {}", routers.getRouterName());
        String externalSubNetId  = null;
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            externalSubNetId = externalIp.getSubnetId().getValue();
            break;
        }
        if (externalSubNetId == null) {
            LOG.info("removeMipAdjacencies no external Ipv4 address present on router {}",
                    routers.getRouterName());
            return;
        }
        InstanceIdentifier<VpnInterfaces> vpnInterfacesId =
                InstanceIdentifier.builder(VpnInterfaces.class).build();
        try {
            VpnInterfaces vpnInterfaces = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, vpnInterfacesId);
            List<VpnInterface> updatedVpnInterface = new ArrayList<>();
            for (VpnInterface vpnInterface : vpnInterfaces.nonnullVpnInterface().values()) {
                List<Adjacency> updatedAdjacencies = new ArrayList<>();
                Adjacencies adjacencies = vpnInterface.augmentation(Adjacencies.class);
                if (null != adjacencies) {
                    for (Adjacency adjacency : adjacencies.nonnullAdjacency().values()) {
                        if (!adjacency.getSubnetId().getValue().equals(externalSubNetId)) {
                            updatedAdjacencies.add(adjacency);
                        }
                    }
                }
                AdjacenciesBuilder adjacenciesBuilder = new AdjacenciesBuilder();
                adjacenciesBuilder.setAdjacency(updatedAdjacencies);
                VpnInterfaceBuilder vpnInterfaceBuilder = new VpnInterfaceBuilder(vpnInterface);
                vpnInterfaceBuilder.addAugmentation(Adjacencies.class, adjacenciesBuilder.build());
                updatedVpnInterface.add(vpnInterfaceBuilder.build());
            }
            VpnInterfacesBuilder vpnInterfacesBuilder = new VpnInterfacesBuilder();
            vpnInterfacesBuilder.setVpnInterface(updatedVpnInterface);

            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    vpnInterfacesId, vpnInterfacesBuilder.build());
        } catch (ExpectedDataObjectNotFoundException e) {
            LOG.warn("Failed to read removeMipAdjacencies with error {}", e.getMessage());
        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to remove removeMipAdjacencies with error {}", e.getMessage());
        }
    }

    private void removeLearntIpPorts(Routers routers) {
        LOG.info("removeLearntIpPorts for router {} and network {}", routers.getRouterName(), routers.getNetworkId());
        String networkId = routers.getNetworkId().getValue();
        LearntVpnVipToPortData learntVpnVipToPortData = NatUtil.getLearntVpnVipToPortData(dataBroker);
        if (learntVpnVipToPortData == null) {
            LOG.info("removeLearntIpPorts, no learned ports present");
            return;
        }
        LearntVpnVipToPortDataBuilder learntVpnVipToPortDataBuilder = new LearntVpnVipToPortDataBuilder();
        List<LearntVpnVipToPort> learntVpnVipToPortList = new ArrayList<>();
        for (LearntVpnVipToPort learntVpnVipToPort : learntVpnVipToPortData.nonnullLearntVpnVipToPort().values()) {
            if (!networkId.equals(learntVpnVipToPort.getVpnName())) {
                LOG.info("The learned port belongs to Vpn {} hence not removing", learntVpnVipToPort.getVpnName());
                learntVpnVipToPortList.add(learntVpnVipToPort);
            } else {
                String externalSubNetId = null;
                for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
                    if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                        // In this class we handle only IPv4 use-cases.
                        continue;
                    }
                    externalSubNetId = externalIp.getSubnetId().getValue();
                    break;
                }
                if (externalSubNetId == null) {
                    LOG.info("removeLearntIpPorts no external Ipv4 address present on router {}",
                            routers.getRouterName());
                    return;
                }
                String prefix = learntVpnVipToPort.getPortFixedip() + "/32";
                NatUtil.deletePrefixToInterface(dataBroker, NatUtil.getVpnId(dataBroker,
                        externalSubNetId), prefix);
            }
        }

        try {
            learntVpnVipToPortDataBuilder.setLearntVpnVipToPort(learntVpnVipToPortList);
            InstanceIdentifier<LearntVpnVipToPortData> learntVpnVipToPortDataId = NatUtil
                    .getLearntVpnVipToPortDataId();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    learntVpnVipToPortDataId, learntVpnVipToPortDataBuilder.build());

        } catch (TransactionCommitFailedException e) {
            LOG.warn("Failed to remove removeLearntIpPorts with error {}", e.getMessage());
        }
    }

    static int mostSignificantBit(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }
}
