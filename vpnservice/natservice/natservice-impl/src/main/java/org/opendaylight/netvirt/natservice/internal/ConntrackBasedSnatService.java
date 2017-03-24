/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.NxMatchFieldType;
import org.opendaylight.genius.mdsalutil.NxMatchInfo;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack.NxCtAction;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldMeta;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatRangePresent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConntrackBasedSnatService extends AbstractSnatService {

    protected final int trackedNewCtState = 0x21;
    protected final int trackedNewCtMask = 0x21;
    protected final int snatCtState = 0x40;
    protected final int snatCtStateMask = 0x40;
    protected final int dnatCtState = 0x80;
    protected final int dnatCtStateMask = 0x80;
    private static final Logger LOG = LoggerFactory.getLogger(ConntrackBasedSnatService.class);

    public ConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager, ItmRpcService itmManager,
            OdlInterfaceRpcService interfaceManager, IdManagerService idManager, NaptManager naptManager,
            NAPTSwitchSelector naptSwitchSelector, IVpnManager vpnManager) {
        super(dataBroker, mdsalManager, itmManager, interfaceManager, idManager, naptManager, naptSwitchSelector,
                vpnManager);
    }

    @Override
    protected void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService: installSnatSpecificEntriesForNaptSwitch for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), dataBroker)
                .getElanTag().intValue();
        /* Install Outbound NAT entries */

        installSnatMissEntryForPrimrySwch(dpnId, routerId, elanId, addOrRemove);
        installTerminatingServiceTblEntry(dpnId, routerId, elanId, addOrRemove);
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        List<ExternalIps> externalIps = routers.getExternalIps();
        createOutboundTblTrackEntry(dpnId, routerId, extNetId,addOrRemove);
        createOutboundTblEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
        installNaptPfibExternalOutputFlow(routers, dpnId, externalIps, addOrRemove);

        //Install Inbound NAT entries

        installInboundEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
        installNaptPfibEntry(dpnId, routerId, addOrRemove);

    }

    @Override
    protected void installSnatSpecificEntriesForNonNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        // Nothing to to do here.

    }

    protected void installSnatMissEntryForPrimrySwch(BigInteger dpnId, Long routerId, int elanId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService : installSnatMissEntryForPrimrySwch for for the primary"
                + " NAPT switch dpnId {} ", dpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId,
                NwConstants.OUTBOUND_NAPT_TABLE,ctActionsList);

        actionsInfos.add(actionNxConntrack);
        instructions.add(new InstructionApplyActions(actionsInfos));

        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        syncFlow(dpnId, NwConstants.PSNAT_TABLE, flowRef, NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installTerminatingServiceTblEntry(BigInteger dpnId, Long  routerId, int elanId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService : creating entry for Terminating Service Table for switch {}, routerId {}",
                dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchTunnelId(BigInteger.valueOf(routerId)));


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

    protected void createOutboundTblTrackEntry(BigInteger dpnId, Long routerId, long extNetId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService : createOutboundTblTrackEntry for switch {}, routerId {}",
                dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[] {snatCtState, snatCtStateMask}));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                .getVpnIdMetadata(extNetId));
        listActionInfo.add(actionSetFieldMeta);
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        flowRef += "trkest";
        syncFlow(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);

    }

    protected void createOutboundTblEntry(BigInteger dpnId, long routerId, long extNetId, List<ExternalIps> externalIps,
            int elanId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService : createOutboundTblEntry dpId {} and routerId {}",
                dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[] {trackedNewCtState, trackedNewCtMask}));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                .getVpnIdMetadata(extNetId));
        actionsInfos.add(actionSetFieldMeta);
        List<NxCtAction> ctActionsListCommit = new ArrayList<>();
        if (externalIps.isEmpty()) {
            LOG.error("ConntrackBasedSnatService : createOutboundTblEntry no externalIP present for routerId {}",
                    routerId);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = externalIps.get(0).getIpAddress();
        int rangePresent = NxActionNatRangePresent.NXNATRANGEIPV4MIN.getIntValue();
        int flags = NxActionNatFlags.NXNATFSRC.getIntValue();
        NxCtAction nxCtActionCommit = new ActionNxConntrack.NxNat(0, flags, rangePresent,
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

    protected void installNaptPfibExternalOutputFlow(Routers routers, BigInteger dpnId, List<ExternalIps> externalIps,
            int addOrRemove) {
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        LOG.info("ConntrackBasedSnatService : buildPostSnatFlowEntity  dpId {}, extNetId {}, srcIp {}",
                dpnId, extNetId, externalIps);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[] {snatCtState, snatCtStateMask}));
        if (externalIps.isEmpty()) {
            LOG.error("ConntrackBasedSnatService : installNaptPfibExternalOutputFlow no externalIP present for "
                    + "routerId {}", routers.getRouterName());
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = externalIps.get(0).getIpAddress();
        matches.add(new MatchIpv4Source(externalIp , "32"));
        List<InstructionInfo> instructions = null;
        Uuid subnetId = getSubnetIdOfIp(externalIp);
        if (addOrRemove == NwConstants.ADD_FLOW && subnetId != null) {
            instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();
            long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(subnetId.getValue()), idManager);
            actionsInfos.add(new ActionGroup(groupId));
            instructions.add(new InstructionApplyActions(actionsInfos));
        }
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, extNetId);
        flowRef += externalIp;
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installInboundEntry(BigInteger dpnId, long routerId, Long extNetId, List<ExternalIps> externalIps,
            int elanId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService : installInboundEntry  dpId {} and routerId {}",
                dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpv4Destination(externalIps.get(0).getIpAddress(),"32"));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNetId), MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
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

    protected void installNaptPfibEntry(BigInteger dpnId, long routerId, int addOrRemove) {
        LOG.info("ConntrackBasedSnatService : installNaptPfibEntry called for dpnId {} and routerId {} ",
                dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchInfo(NxMatchFieldType.ct_state,
                new long[] {dnatCtState, dnatCtStateMask}));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));


        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);
    }

    private Uuid getSubnetIdOfIp(String ip) {
        if (ip != null) {
            IpAddress externalIpv4Address = new IpAddress(new Ipv4Address(ip));
            Port port = NatUtil.getNeutronPortForRouterGetewayIp(dataBroker, externalIpv4Address);
            return NatUtil.getSubnetIdForFloatingIp(port, externalIpv4Address);
        }
        return null;
    }
}