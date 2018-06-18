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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
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
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
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
    protected final JobCoordinator jobCoordinator;

    protected Ipv6ForwardingService(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                  final ItmRpcService itmManager,
                                  final OdlInterfaceRpcService odlInterfaceRpcService,
                                  final IdManagerService idManager,
                                  final NAPTSwitchSelector naptSwitchSelector,
                                  final IInterfaceManager interfaceManager,
                                  final JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.idManager = idManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
    }

    public void init() {
        LOG.info("Ipv6ForwardingService: {} init", getClass().getSimpleName());
    }

    @Override
    public boolean handleSnatAllSwitch(Routers routers, BigInteger primarySwitchId, int addOrRemove) {
        String routerName = routers.getRouterName();
        LOG.info("handleSnatAllSwitch : invoked for router {} with NAPTSwitch {} for {} flows",
                routerName, primarySwitchId, (addOrRemove == NwConstants.ADD_FLOW) ? "installing" : "removing");
        List<BigInteger> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        /*
         * Primary switch handled separately since the pseudo port created may
         * not be present in the switch list on delete.
         */
        handleSnat(routers, primarySwitchId, primarySwitchId, addOrRemove);
        for (BigInteger dpnId : switches) {
            if (primarySwitchId != dpnId) {
                handleSnat(routers, primarySwitchId, dpnId, addOrRemove);
            }
        }
        return true;
    }

    @Override
    public boolean handleSnat(Routers routers, BigInteger primarySwitchId, BigInteger dpnId,  int addOrRemove) {
        Long routerId = NatUtil.getVpnId(dataBroker, routers.getRouterName());
        BigInteger routerMetadata = MetaDataUtil.getVpnIdMetadata(routerId);

        if (!dpnId.equals(primarySwitchId)) {
            LOG.info("handleSnat (non-NAPTSwitch) : {} flows on switch {} for router {}",
                    (addOrRemove == NwConstants.ADD_FLOW) ? "Installing" : "Removing", dpnId, routers.getRouterName());
            // Program default flow from FIB_TABLE(21) to PSNAT_TABLE(26) (egress direction)
            installIpv6DefaultFibRoute(dpnId, routerId, routerMetadata, addOrRemove);

            // Currently we are only programming flows when ext-net has an IPv6Subnet
            if (routerHasIpv6ExtSubnet(routers)) {
                // Program flows on non-NAPTSwitch to send N/S packets to the NAPTSwitch
                installIpv6PsNatMissEntryNonNaptSwitch(dpnId, routerId, routers.getRouterName(),
                        primarySwitchId, addOrRemove);
            }
        } else {
            LOG.info("handleSnat (NAPTSwitch) : {} flows on switch {} for router {}",
                    (addOrRemove == NwConstants.ADD_FLOW) ? "Installing" : "Removing", dpnId, routers.getRouterName());
            // Program default flow from FIB_TABLE(21) to PSNAT_TABLE(26) (egress direction)
            installIpv6DefaultFibRoute(dpnId, routerId, routerMetadata, addOrRemove);

            // Program flows from PSNAT_TABLE(26) to OUTBOUND_NAPT_TABLE(46) (egress direction)
            installIpv6SnatMissEntryForNaptSwitch(dpnId, routerId, routerMetadata, addOrRemove);

            // Program flows in INTERNAL_TUNNEL_TABLE(36) for packets coming from non-NAPTSwitch (egress direction)
            installIpv6TerminatingServiceTblEntry(dpnId, routerId, routerMetadata, addOrRemove);

            // Program flows from NAPT_PFIB_TABLE(47) to FIB_TABLE(21) (ingress direction)
            installIpv6NaptPfibInboundFlow(dpnId, routerId, routerMetadata, addOrRemove);

            // Now installing flows that use SubnetInfo
            String jobKey = NatUtil.getIpv6JobKey(routers.getRouterName());
            Ipv6SubnetFlowProgrammer addWorker = new Ipv6SubnetFlowProgrammer(dataBroker, mdsalManager, dpnId,
                    routers, routerId, routerMetadata, addOrRemove);
            jobCoordinator.enqueueJob(jobKey, addWorker, NatConstants.NAT_DJC_MAX_RETRIES);
        }
        return true;
    }

    public boolean handleRouterUpdate(Routers origRouter, Routers updatedRouter) {
        LOG.info("handleRouterUpdate : originalRouter {}, updatedRouter {}", origRouter, updatedRouter);
        String routerName = origRouter.getRouterName();
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        BigInteger routerMetadata = MetaDataUtil.getVpnIdMetadata(routerId);

        // If the external network is updated with an IPv6Subnet, program the necessary flows on non-NAPTSwitch
        if (!routerHasIpv6ExtSubnet(origRouter) && routerHasIpv6ExtSubnet(updatedRouter)) {
            List<BigInteger> switches = naptSwitchSelector.getDpnsForVpn(routerName);
            for (BigInteger dpnId : switches) {
                if (primarySwitchId != dpnId) {
                    LOG.info("handleRouterUpdate (non-NAPTSwitch) : Installing flows on switch {} for router {}",
                            dpnId, routerName);
                    installIpv6PsNatMissEntryNonNaptSwitch(dpnId, routerId, routerName,
                            primarySwitchId, NwConstants.ADD_FLOW);
                }
            }
        }

        String jobKey = NatUtil.getIpv6JobKey(routerName);
        Ipv6SubnetFlowProgrammer addWorker = new Ipv6SubnetFlowProgrammer(dataBroker, mdsalManager, primarySwitchId,
                origRouter, routerId, routerMetadata, NwConstants.DEL_FLOW);
        jobCoordinator.enqueueJob(jobKey, addWorker, NatConstants.NAT_DJC_MAX_RETRIES);

        LOG.info("handleRouterUpdate (NAPTSwitch): Installing updated flows on Switch {}", primarySwitchId);

        addWorker = new Ipv6SubnetFlowProgrammer(dataBroker, mdsalManager, primarySwitchId,
                updatedRouter, routerId, routerMetadata, NwConstants.ADD_FLOW);
        jobCoordinator.enqueueJob(jobKey, addWorker, NatConstants.NAT_DJC_MAX_RETRIES);
        return true;
    }

    protected void installIpv6DefaultFibRoute(BigInteger dpnId, Long routerId,
                                              BigInteger routerMetadata, int addOrRemove) {
        LOG.debug("installIpv6DefaultFibRoute : Installing default FIB route to PSNAT_TABLE on {}", dpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.L3_FIB_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installIpv6PsNatMissEntryNonNaptSwitch(BigInteger dpnId, Long routerId, String routerName,
                                                          BigInteger primarySwitchId, int addOrRemove) {
        LOG.debug("installIpv6PsNatMissEntryNonNaptSwitch : On Non-Napt Switch, installing SNAT miss entry in"
                + " switch {} for router {}", dpnId, routerName);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        List<BucketInfo> listBucketInfo = new ArrayList<>();

        String ifNamePrimary = NatUtil.getTunnelInterfaceName(dpnId, primarySwitchId, itmManager);
        if (ifNamePrimary != null) {
            LOG.debug("installIpv6PsNatMissEntryNonNaptSwitch : On Non-Napt Switch, Primary Tunnel interface is {}",
                    ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId);
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
        if (addOrRemove == NwConstants.ADD_FLOW) {
            LOG.debug("installing the PSNAT to NAPTSwitch GroupEntity:{} with GroupId: {}", groupEntity, groupId);
            mdsalManager.installGroup(groupEntity);
        } else {
            LOG.debug("removing the PSNAT to NAPTSwitch GroupEntity:{} with GroupId: {}", groupEntity, groupId);
            mdsalManager.syncRemoveGroup(groupEntity);
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        actionsInfo.add(new ActionSetFieldTunnelId(BigInteger.valueOf(routerId)));
        actionsInfo.add(new ActionGroup(groupId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installIpv6SnatMissEntryForNaptSwitch(BigInteger dpnId, Long routerId,
                                                         BigInteger routerMetadata, int addOrRemove) {
        LOG.debug("installIpv6SnatMissEntryForNaptSwitch {} called for routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.OUTBOUND_NAPT_TABLE));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installIpv6TerminatingServiceTblEntry(BigInteger dpnId, Long  routerId, BigInteger routerMetadata,
                                                         int addOrRemove) {
        LOG.debug("installIpv6TerminatingServiceTblEntry : creating entry for Terminating Service Table "
                + "for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchTunnelId(BigInteger.valueOf(routerId)));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(routerMetadata, LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(new ActionNxResubmit(NwConstants.OUTBOUND_NAPT_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        flowRef += ".Outbound";
        NatUtil.syncFlow(mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);

    }

    protected void installIpv6NaptPfibInboundFlow(BigInteger dpnId, long routerId,
                                                  BigInteger routerMetadata, int addOrRemove) {
        LOG.debug("installIpv6NaptPfibInboundFlow : called for dpnId {} and routerId {} ", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchMetadata(routerMetadata, MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = NatUtil.getIpv6FlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += ".Inbound";
        NatUtil.syncFlow(mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE,
                matches, instructionInfo, addOrRemove);
    }

    protected long createGroupIdForIpv6Router(String groupIdKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(NatConstants.SNAT_IDPOOL_NAME).setIdKey(groupIdKey)
                .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
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
        LOG.debug("router {}, does not have an external IPv6 subnet {}", routers.getRouterName());
        return false;
    }
}
