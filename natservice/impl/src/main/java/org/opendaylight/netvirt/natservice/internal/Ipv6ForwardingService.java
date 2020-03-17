/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.netvirt.natservice.internal.AbstractSnatService.LOAD_END;
import static org.opendaylight.netvirt.natservice.internal.AbstractSnatService.LOAD_START;
import static org.opendaylight.netvirt.natservice.internal.NatUtil.getGroupIdKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadMetadata;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.natservice.api.SnatServiceListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ForwardingService implements SnatServiceListener {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ForwardingService.class);

    protected final DataBroker dataBroker;
    protected final IMdsalApiManager mdsalManager;
    protected final IdManagerService idManager;
    protected final NAPTSwitchSelector naptSwitchSelector;
    protected final ItmRpcService itmManager;
    protected final OdlInterfaceRpcService odlInterfaceRpcService;
    protected final IInterfaceManager interfaceManager;
    protected final Ipv6SubnetFlowProgrammer ipv6SubnetFlowProgrammer;

    public Ipv6ForwardingService(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                  final ItmRpcService itmManager,
                                  final OdlInterfaceRpcService odlInterfaceRpcService,
                                  final IdManagerService idManager,
                                  final NAPTSwitchSelector naptSwitchSelector,
                                  final IInterfaceManager interfaceManager,
                                  final Ipv6SubnetFlowProgrammer ipv6SubnetFlowProgrammer) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.idManager = idManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.interfaceManager = interfaceManager;
        this.ipv6SubnetFlowProgrammer = ipv6SubnetFlowProgrammer;
    }

    @Override
    public boolean addCentralizedRouterAllSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Routers routers, Uint64 primarySwitchId) {
        String routerName = routers.getRouterName();
        LOG.info("handleSnatAllSwitch : invoked for router {} with NAPTSwitch {} for {} flows",
                routerName, primarySwitchId, "installing");
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        /*
         * Primary switch handled separately since the pseudo port created may
         * not be present in the switch list on delete.
         */
        addCentralizedRouter(confTx, routers, primarySwitchId, primarySwitchId);
        for (Uint64 dpnId : switches) {
            if (!Objects.equals(primarySwitchId, dpnId)) {
                addCentralizedRouter(confTx, routers, primarySwitchId, dpnId);
            }
        }
        return true;
    }

    @Override
    public boolean addCentralizedRouter(TypedReadWriteTransaction<Configuration> confTx,
            Routers routers, Uint64 primarySwitchId, Uint64 dpnId) {
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routers.getRouterName());
        Uint64 routerMetadata = MetaDataUtil.getVpnIdMetadata(routerId.longValue());

        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("handleSnat (non-NAPTSwitch) : {} flows on switch {} for router {}",
                    "Installing", dpnId, routers.getRouterName());
            // Program default flow from FIB_TABLE(21) to PSNAT_TABLE(26) (egress direction)
            addIpv6DefaultFibRoute(confTx, dpnId, routerId, routerMetadata);

            // Currently we are only programming flows when ext-net has an IPv6Subnet
            if (routerHasIpv6ExtSubnet(routers)) {
                // Program flows on non-NAPTSwitch to send N/S packets to the NAPTSwitch
                addIpv6PsNatMissEntryNonNaptSwitch(confTx, dpnId, routerId, routers.getRouterName(),
                        primarySwitchId);
            }
        } else {
            LOG.info("handleSnat (NAPTSwitch) : {} flows on switch {} for router {}",
                    "Installing", dpnId, routers.getRouterName());
            // Program default flow from FIB_TABLE(21) to PSNAT_TABLE(26) (egress direction)
            addIpv6DefaultFibRoute(confTx, dpnId, routerId, routerMetadata);

            // Program flows from PSNAT_TABLE(26) to OUTBOUND_NAPT_TABLE(46) (egress direction)
            addIpv6SnatMissEntryForNaptSwitch(confTx, dpnId, routerId, routerMetadata);

            // Program flows in INTERNAL_TUNNEL_TABLE(36) for packets coming from non-NAPTSwitch (egress direction)
            addIpv6TerminatingServiceTblEntry(confTx, dpnId, routerId, routerMetadata);

            // Program flows from NAPT_PFIB_TABLE(47) to FIB_TABLE(21) (ingress direction)
            addIpv6NaptPfibInboundFlow(confTx, dpnId, routerId, routerMetadata);

            // Now installing flows that use SubnetInfo
            ipv6SubnetFlowProgrammer.addSubnetSpecificFlows(confTx, dpnId, routerId, routers, routerMetadata);
        }
        return true;
    }

    @Override
    public boolean removeCentralizedRouterAllSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Routers routers, Uint64 primarySwitchId) throws ExecutionException, InterruptedException {
        String routerName = routers.getRouterName();
        LOG.info("handleSnatAllSwitch : invoked for router {} with NAPTSwitch {} for {} flows",
                routerName, primarySwitchId, "removing");
        List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        /*
         * Primary switch handled separately since the pseudo port created may
         * not be present in the switch list on delete.
         */
        removeCentralizedRouter(confTx, routers, primarySwitchId, primarySwitchId);
        for (Uint64 dpnId : switches) {
            if (!Objects.equals(primarySwitchId, dpnId)) {
                removeCentralizedRouter(confTx, routers, primarySwitchId, dpnId);
            }
        }
        return true;
    }

    @Override
    public boolean removeCentralizedRouter(TypedReadWriteTransaction<Configuration> confTx,
            Routers routers, Uint64 primarySwitchId, Uint64 dpnId)
                    throws ExecutionException, InterruptedException {
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routers.getRouterName());
        Uint64 routerMetadata = MetaDataUtil.getVpnIdMetadata(routerId.longValue());

        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("handleSnat (non-NAPTSwitch) : {} flows on switch {} for router {}",
                    "Removing", dpnId, routers.getRouterName());
            // Program default flow from FIB_TABLE(21) to PSNAT_TABLE(26) (egress direction)
            addIpv6DefaultFibRoute(confTx, dpnId, routerId, routerMetadata);

            // Currently we are only programming flows when ext-net has an IPv6Subnet
            if (routerHasIpv6ExtSubnet(routers)) {
                // Program flows on non-NAPTSwitch to send N/S packets to the NAPTSwitch
                addIpv6PsNatMissEntryNonNaptSwitch(confTx, dpnId, routerId, routers.getRouterName(),
                        primarySwitchId);
            }
        } else {
            LOG.info("handleSnat (NAPTSwitch) : {} flows on switch {} for router {}",
                    "Removing", dpnId, routers.getRouterName());
            // Program default flow from FIB_TABLE(21) to PSNAT_TABLE(26) (egress direction)
            removeIpv6DefaultFibRoute(confTx, dpnId, routerId);

            // Program flows from PSNAT_TABLE(26) to OUTBOUND_NAPT_TABLE(46) (egress direction)
            removeIpv6SnatMissEntryForNaptSwitch(confTx, dpnId, routerId);

            // Program flows in INTERNAL_TUNNEL_TABLE(36) for packets coming from non-NAPTSwitch (egress direction)
            removeIpv6TerminatingServiceTblEntry(confTx, dpnId, routerId);

            // Program flows from NAPT_PFIB_TABLE(47) to FIB_TABLE(21) (ingress direction)
            removeIpv6NaptPfibInboundFlow(confTx, dpnId, routerId);

            // Now installing flows that use SubnetInfo
            ipv6SubnetFlowProgrammer.removeSubnetSpecificFlows(confTx, dpnId, routerId, routers);
        }
        return true;
    }

    @Override
    public boolean handleRouterUpdate(TypedReadWriteTransaction<Configuration> confTx,
            Routers origRouter, Routers updatedRouter) throws ExecutionException, InterruptedException {
        LOG.info("handleRouterUpdate : originalRouter {}, updatedRouter {}", origRouter, updatedRouter);
        String routerName = origRouter.getRouterName();
        Uint64 primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        Uint64 routerMetadata = MetaDataUtil.getVpnIdMetadata(routerId.longValue());

        // If the external network is updated with an IPv6Subnet, program the necessary flows on non-NAPTSwitch
        if (!routerHasIpv6ExtSubnet(origRouter) && routerHasIpv6ExtSubnet(updatedRouter)) {
            List<Uint64> switches = naptSwitchSelector.getDpnsForVpn(routerName);
            for (Uint64 dpnId : switches) {
                if (!Objects.equals(primarySwitchId, dpnId)) {
                    LOG.info("handleRouterUpdate (non-NAPTSwitch) : Installing flows on switch {} for router {}",
                            dpnId, routerName);
                    addIpv6PsNatMissEntryNonNaptSwitch(confTx, dpnId, routerId, routerName,
                            primarySwitchId);
                }
            }
        }

        ipv6SubnetFlowProgrammer.removeSubnetSpecificFlows(confTx, primarySwitchId, routerId, origRouter);
        ipv6SubnetFlowProgrammer.addSubnetSpecificFlows(confTx, primarySwitchId, routerId, updatedRouter,
                routerMetadata);
        return true;
    }

    @Override
    public boolean addSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId) {
        return true;
    }

    @Override
    public boolean addSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) {
        return true;
    }

    @Override
    public boolean removeSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId)  throws ExecutionException, InterruptedException {
        return true;
    }

    @Override
    public boolean removeSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) throws ExecutionException, InterruptedException {
        return true;
    }


    protected void addIpv6DefaultFibRoute(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                          Uint32 routerId, Uint64 routerMetadata) {
        LOG.debug("installIpv6DefaultFibRoute : Installing default FIB route to PSNAT_TABLE on {}", dpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeIpv6DefaultFibRoute(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                             Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.debug("installIpv6DefaultFibRoute : Installing default FIB route to PSNAT_TABLE on {}", dpnId);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef);
    }

    protected void addIpv6PsNatMissEntryNonNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId, String routerName, Uint64 primarySwitchId) {
        LOG.debug("installIpv6PsNatMissEntryNonNaptSwitch : On Non-Napt Switch, installing SNAT miss entry in"
                + " switch {} for router {}", dpnId, routerName);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        List<BucketInfo> listBucketInfo = new ArrayList<>();

        String ifNamePrimary = NatUtil.getTunnelInterfaceName(dpnId, primarySwitchId, itmManager);
        if (ifNamePrimary != null) {
            LOG.debug("installIpv6PsNatMissEntryNonNaptSwitch : On Non-Napt Switch, Primary Tunnel interface is {}",
                    ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId, true);
        } else {
            LOG.warn("installIpv6PsNatMissEntryNonNaptSwitch: could not get tunnelInterface for {} on Switch {}",
                    primarySwitchId, dpnId);
        }

        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);

        LOG.debug("installIpv6PsNatMissEntryNonNaptSwitch : installSnatMissEntry called for dpnId {} with"
                + " primaryBucket {} ", dpnId, listBucketInfo.get(0));

        long groupId = createGroupIdForIpv6Router(getGroupIdKey(routerName + "IPv6"));
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll,
                listBucketInfo);
        LOG.debug("installing the PSNAT to NAPTSwitch GroupEntity:{} with GroupId: {}", groupEntity, groupId);
        mdsalManager.addGroup(confTx, groupEntity);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        actionsInfo.add(new ActionSetFieldTunnelId(Uint64.valueOf(routerId)));
        actionsInfo.add(new ActionGroup(groupId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void addIpv6SnatMissEntryForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId, Uint64 routerMetadata) {
        LOG.debug("installIpv6SnatMissEntryForNaptSwitch {} called for routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeIpv6SnatMissEntryForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId)
                    throws ExecutionException, InterruptedException {
        LOG.debug("installIpv6SnatMissEntryForNaptSwitch {} called for routerId {}", dpnId, routerId);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef);
    }

    protected void addIpv6TerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32  routerId, Uint64 routerMetadata) {
        LOG.debug("installIpv6TerminatingServiceTblEntry : creating entry for Terminating Service Table "
                + "for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchTunnelId(Uint64.valueOf(routerId)));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(routerMetadata, LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(new ActionNxResubmit(NwConstants.OUTBOUND_NAPT_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

    }

    protected void removeIpv6TerminatingServiceTblEntry(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.debug("installIpv6TerminatingServiceTblEntry : creating entry for Terminating Service Table "
                + "for switch {}, routerId {}", dpnId, routerId);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef);

    }

    protected void addIpv6NaptPfibInboundFlow(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                              Uint32 routerId, Uint64 routerMetadata) {
        LOG.debug("installIpv6NaptPfibInboundFlow : called for dpnId {} and routerId {} ", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(Uint64.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += ".Inbound";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE,
                matches, instructionInfo);
    }

    protected void removeIpv6NaptPfibInboundFlow(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                 Uint32 routerId)
                    throws ExecutionException, InterruptedException {
        LOG.debug("installIpv6NaptPfibInboundFlow : called for dpnId {} and routerId {} ", dpnId, routerId);
        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += ".Inbound";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef);
    }

    protected long createGroupIdForIpv6Router(String groupIdKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(NatConstants.SNAT_IDPOOL_NAME).setIdKey(groupIdKey)
                .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue().toJava();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("createGroupIdForIPv6Router: Exception while creating group with key : {}", groupIdKey, e);
        }
        return 0;
    }

    protected boolean routerHasIpv6ExtSubnet(Routers routers) {
        for (ExternalIps externalIp : routers.getExternalIps()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                LOG.debug("router {}, has an external IPv6 subnet {}",
                        routers.getRouterName(), externalIp.getIpAddress());
                return true;
            }
        }
        LOG.debug("router {}, does not have an external IPv6 subnet", routers.getRouterName());
        return false;
    }
}
