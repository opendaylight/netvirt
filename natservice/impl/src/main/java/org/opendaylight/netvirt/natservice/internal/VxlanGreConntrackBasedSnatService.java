/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack.NxCtAction;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldMeta;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.natservice.ha.NatDataUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatRangePresent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VxlanGreConntrackBasedSnatService extends ConntrackBasedSnatService {

    private static final Logger LOG = LoggerFactory.getLogger(VxlanGreConntrackBasedSnatService.class);
    private final ExternalRoutersListener externalRouterListener;
    private final IElanService elanManager;
    private final ManagedNewTransactionRunner txRunner;

    public VxlanGreConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                             ItmRpcService itmManager, OdlInterfaceRpcService odlInterfaceRpcService,
                                             IdManagerService idManager, NAPTSwitchSelector naptSwitchSelector,
                                             ExternalRoutersListener externalRouterListener, IElanService elanManager,
                                             IInterfaceManager interfaceManager,
                                             IVpnFootprintService vpnFootprintService,
                                             IFibManager fibManager, NatDataUtil natDataUtil) {
        super(dataBroker, mdsalManager, itmManager, idManager, naptSwitchSelector, odlInterfaceRpcService,
                interfaceManager, vpnFootprintService, fibManager, natDataUtil);
        this.externalRouterListener = externalRouterListener;
        this.elanManager = elanManager;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public boolean handleSnatAllSwitch(Routers routers, BigInteger primarySwitchId,  int addOrRemove) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnatAllSwitch ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleSnatAllSwitch : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.handleSnatAllSwitch(routers, primarySwitchId, addOrRemove);
    }

    @Override
    public boolean handleSnat(Routers routers, BigInteger primarySwitchId, BigInteger dpnId,  int addOrRemove) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnat ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleSnat : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.handleSnat(routers, primarySwitchId, dpnId, addOrRemove);
    }

    @Override
    protected void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        LOG.info("installSnatSpecificEntriesForNaptSwitch for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), dataBroker)
                .getElanTag().intValue();
        /* Install Outbound NAT entries */

        installSnatMissEntryForPrimrySwch(dpnId, routerId, elanId, addOrRemove);
        installTerminatingServiceTblEntryForVxlanGre(dpnId, routerName, routerId, elanId, addOrRemove);
        //Long extNetVpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, routers.getNetworkId());
        if (vpnUuid == null) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: Unable to retrieve external vpn_id for "
                    + "external network {} with routerId {}", routers.getNetworkId(), routerId);
            return;
        }
        Long extNetVpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
        /*//Long extNetVpnId = NatUtil.getAssociatedVPN(dataBroker, routers.getNetworkId(), LOG);
        if (extNetVpnId == NatConstants.INVALID_ID && addOrRemove == NwConstants.ADD_FLOW) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: Unable to retrieve external vpn_id for "
                    + "external network {} with routerId {}", routers.getNetworkId(), routerId);
            return;
        }*/
        LOG.info("installSnatSpecificEntriesForNaptSwitch: external network vpn_id {} for router {}",
                extNetVpnId, routers.getRouterName());
        List<ExternalIps> externalIps = routers.getExternalIps();
        createOutboundTblTrackEntryForVxlanGre(dpnId, routerId, extNetVpnId, addOrRemove);
        createOutboundTblEntryForVxlanGre(dpnId, routerId, extNetVpnId, externalIps, elanId, addOrRemove);
        installNaptPfibFlowForVxlanGre(routers, dpnId, extNetVpnId, addOrRemove);
        installNaptPfibEntry(dpnId, routerId, addOrRemove);

        //Install Inbound NAT entries
        installInboundEntryForVxlanGre(dpnId, routerId, extNetVpnId, externalIps, elanId, addOrRemove);
        if (externalIps.isEmpty()) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: No externalIP present for router {}",
                    routerName);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = NatUtil.validateAndAddNetworkMask(externalIps.get(0).getIpAddress());
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
            if (addOrRemove == NwConstants.ADD_FLOW) {
                externalRouterListener.handleSnatReverseTraffic(dpnId, routers, routerId, routerName, externalIp, tx);
            } else {
                externalRouterListener.clearFibTsAndReverseTraffic(dpnId, routerId, routers.getNetworkId(),
                        Collections.singletonList(externalIp), null, routers.getExtGwMacAddress(), tx);
            }
        }), LOG, "Error installing SNAT-specific entries for NAPT switch");
    }

    protected void createOutboundTblTrackEntryForVxlanGre(BigInteger dpnId, Long routerId, Long extNetVpnId,
                                               int addOrRemove) {
        LOG.info("createOutboundTblTrackEntryForVxlanGre: Install Outbound tracking table flow on dpId {} for "
                + "routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(SNAT_CT_STATE, SNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                    .getVpnIdMetadata(extNetVpnId));
            listActionInfo.add(actionSetFieldMeta);
        }
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        flowRef += "trkest";
        syncFlow(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);

    }

    protected void createOutboundTblEntryForVxlanGre(BigInteger dpnId, long routerId, Long extNetVpnId,
                                                     List<ExternalIps> externalIps, int elanId, int addOrRemove) {
        LOG.info("createOutboundTblEntryForVxlanGre: Install Outbound table flow on dpId {} for routerId {}", dpnId,
                routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(TRACKED_NEW_CT_STATE, TRACKED_NEW_CT_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        if (externalIps.isEmpty()) {
            LOG.error("createOutboundTblEntryForVxlanGre: No externalIP present for routerId {}",
                    routerId);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = externalIps.get(0).getIpAddress();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                    .getVpnIdMetadata(extNetVpnId));
            actionsInfos.add(actionSetFieldMeta);
        }
        List<ActionNxConntrack.NxCtAction> ctActionsListCommit = new ArrayList<>();
        int rangePresent = NxActionNatRangePresent.NXNATRANGEIPV4MIN.getIntValue();
        int flags = NxActionNatFlags.NXNATFSRC.getIntValue();
        ActionNxConntrack.NxCtAction nxCtActionCommit = new ActionNxConntrack.NxNat(0, flags, rangePresent,
                new IpPrefixOrAddress(externalIp.toCharArray()).getIpAddress(),
                null,0, 0);
        ctActionsListCommit.add(nxCtActionCommit);
        int ctCommitFlag = 1;
        ActionNxConntrack actionNxConntrackSubmit = new ActionNxConntrack(ctCommitFlag, 0, elanId,
                NwConstants.NAPT_PFIB_TABLE, ctActionsListCommit);
        actionsInfos.add(actionNxConntrackSubmit);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        syncFlow(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,  NatConstants.SNAT_NEW_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installNaptPfibFlowForVxlanGre(Routers routers, BigInteger dpnId, Long extNetVpnId,
                                                  int addOrRemove) {
        LOG.info("installNaptPfibFlowForVxlanGre: Install Napt preFibFlow on dpId {} with matching extNetVpnId {} "
                + "for router {}", dpnId, extNetVpnId, routers.getRouterName());
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNetVpnId),
                    MetaDataUtil.METADATA_MASK_VRFID));
        }
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(listActionInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, extNetVpnId);
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installInboundEntryForVxlanGre(BigInteger dpnId, long routerId, Long extNeVpnId,
                                                  List<ExternalIps> externalIps, int elanId, int addOrRemove) {
        LOG.info("installInboundEntryForVxlanGre:  Install Inbound table entry on dpId {} for routerId {}",
                dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        if (externalIps.isEmpty()) {
            LOG.error("installInboundEntryForVxlanGre : createInboundTblEntry no externalIP present for routerId {}",
                    routerId);
            return;
        }
        String externalIp = externalIps.get(0).getIpAddress();
        matches.add(new MatchIpv4Destination(externalIp,"32"));
        if (addOrRemove == NwConstants.ADD_FLOW) {
            matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNeVpnId),
                    MetaDataUtil.METADATA_MASK_VRFID));
        }
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<ActionNxConntrack.NxCtAction> ctActionsList = new ArrayList<>();
        ActionNxConntrack.NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                .getVpnIdMetadata(routerId));
        actionsInfos.add(actionSetFieldMeta);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId, NwConstants
                .NAPT_PFIB_TABLE,ctActionsList);

        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        syncFlow(dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef, NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installTerminatingServiceTblEntryForVxlanGre(BigInteger dpnId, String routerName,
            Long  routerId, int elanId, int addOrRemove) {
        LOG.info("installTerminatingServiceTblEntryForVxlanGre : creating entry for"
                + "Terminating Service Table for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        BigInteger tunnelId = BigInteger.valueOf(routerId);
        if (elanManager.isOpenStackVniSemanticsEnforced()) {
            tunnelId = NatOverVxlanUtil.getRouterVni(idManager, routerName, routerId);
        }
        matches.add(new MatchTunnelId(tunnelId));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId, NwConstants
                .OUTBOUND_NAPT_TABLE,ctActionsList);
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                .getVpnIdMetadata(routerId.longValue()));
        actionsInfos.add(actionSetFieldMeta);
        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId.longValue());
        syncFlow(dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef, NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                 NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);

    }

    protected void installSnatMissEntry(BigInteger dpnId, Long routerId, String routerName, BigInteger primarySwitchId,
            int addOrRemove) {
        LOG.debug("installSnatMissEntry : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName(dpnId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        if (ifNamePrimary != null) {
            LOG.debug("installSnatMissEntry : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);
        LOG.debug("installSnatMissEntry : installSnatMissEntry called for dpnId {} with primaryBucket {} ", dpnId,
                listBucketInfo.get(0));
        // Install the select group
        long groupId = createGroupId(getGroupIdKey(routerName));
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll,
                listBucketInfo);
        LOG.debug("installSnatMissEntry : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
        mdsalManager.installGroup(groupEntity);
        // Install miss entry pointing to group
        LOG.debug("installSnatMissEntry : buildSnatFlowEntity is called for dpId {}, routerName {} and groupId {}",
                dpnId, routerName, groupId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchEthernetType(0x0800L));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        List<ActionInfo> actionsInfo = new ArrayList<>();

        BigInteger tunnelId = BigInteger.valueOf(routerId);
        if (elanManager.isOpenStackVniSemanticsEnforced()) {
            tunnelId = NatOverVxlanUtil.getRouterVni(idManager, routerName, routerId);
        }

        actionsInfo.add(new ActionSetFieldTunnelId(tunnelId));
        LOG.debug("AbstractSnatService : Setting the tunnel to the list of action infos {}", actionsInfo);
        actionsInfo.add(new ActionGroup(groupId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        syncFlow(dpnId, NwConstants.PSNAT_TABLE, flowRef,  NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

}
