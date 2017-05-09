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
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack.NxCtAction;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadMetadata;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatRangePresent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConntrackBasedSnatService extends AbstractSnatService {

    protected final int trackedNewCtState = 0x21;
    protected final int trackedNewCtMask = 0x21;
    protected final int snatCtState = 0x40;
    protected final int snatCtStateMask = 0x40;
    protected final int dnatCtState = 0x80;
    protected final int dnatCtStateMask = 0x80;
    protected final int loadStart = mostSignificantBit(MetaDataUtil.METADATA_MASK_SH_FLAG.intValue());
    protected final int loadEnd = mostSignificantBit(MetaDataUtil.METADATA_MASK_VRFID.intValue() | MetaDataUtil
            .METADATA_MASK_SH_FLAG.intValue());
    private static final Logger LOG = LoggerFactory.getLogger(ConntrackBasedSnatService.class);

    public ConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager, ItmRpcService itmManager,
            OdlInterfaceRpcService interfaceManager, IdManagerService idManager, NaptManager naptManager,
            NAPTSwitchSelector naptSwitchSelector, IVpnManager vpnManager) {
        super(dataBroker, mdsalManager, itmManager, interfaceManager, idManager, naptManager, naptSwitchSelector,
                vpnManager);
    }

    @Override
    protected void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        LOG.info("installSnatSpecificEntriesForNaptSwitch: called for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), dataBroker)
                .getElanTag().intValue();
        /* Install Outbound NAT entries */

        installSnatMissEntryForPrimrySwch(dpnId, routerId, elanId, addOrRemove);
        installTerminatingServiceTblEntry(dpnId, routerId, elanId, addOrRemove);
        List<ExternalIps> externalIps = routers.getExternalIps();
        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterId(dataBroker, routerId);
        createOutboundTblTrackEntry(dpnId, routerId, externalIps, extGwMacAddress, addOrRemove);
        createOutboundTblEntry(dpnId, routerId, externalIps, elanId, extGwMacAddress, addOrRemove);
        installNaptPfibFlow(routers, dpnId, externalIps, routerName, addOrRemove);

        //Install Inbound NAT entries
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        installInboundEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
        installNaptPfibEntry(dpnId, routerId, addOrRemove);

    }

    @Override
    protected void installSnatSpecificEntriesForNonNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        // Nothing to to do here.

    }

    protected void installSnatMissEntryForPrimrySwch(BigInteger dpnId, Long routerId, int elanId, int addOrRemove) {
        LOG.info("installSnatSpecificEntriesForNaptSwitch : called for the primary NAPT switch dpnId {}", dpnId);
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
        LOG.info("installTerminatingServiceTblEntry : creating entry for Terminating Service Table "
                + "for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchTunnelId(BigInteger.valueOf(routerId)));


        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId, NwConstants
                .OUTBOUND_NAPT_TABLE,ctActionsList);
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                .getVpnIdMetadata(routerId.longValue()), loadStart, loadEnd);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId.longValue());
        syncFlow(dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef, NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                 NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);

    }

    protected void createOutboundTblTrackEntry(BigInteger dpnId, Long routerId,
            List<ExternalIps> externalIps, String extGwMacAddress, int addOrRemove) {
        LOG.info("createOutboundTblTrackEntry : called for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(snatCtState, snatCtStateMask));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        if (externalIps.isEmpty()) {
            LOG.error("createOutboundTblTrackEntry : no externalIP present for routerId {}",
                    routerId);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = externalIps.get(0).getIpAddress();
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        String routerName = NatUtil.getRouterName(dataBroker, routerId);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            long extSubnetId = NatUtil.getVpnIdFromExternalSubnet(dataBroker, routerName, externalIp);
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("createOutboundTblTrackEntry : external subnet id is invalid.");
                return;
            }
            ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                    .getVpnIdMetadata(extSubnetId), loadStart, loadEnd);
            listActionInfo.add(actionLoadMeta);
            listActionInfo.add(new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress)));
        }
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        flowRef += "trkest";
        syncFlow(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);

    }

    protected void createOutboundTblEntry(BigInteger dpnId, long routerId, List<ExternalIps> externalIps,
            int elanId, String extGwMacAddress,  int addOrRemove) {
        LOG.info("createOutboundTblEntry : dpId {} and routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(trackedNewCtState, trackedNewCtMask));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        if (externalIps.isEmpty()) {
            LOG.error("createOutboundTblEntry : no externalIP present for routerId {}", routerId);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = externalIps.get(0).getIpAddress();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        String routerName = NatUtil.getRouterName(dataBroker, routerId);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            long extSubnetId = NatUtil.getVpnIdFromExternalSubnet(dataBroker, routerName, externalIp);
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("createOutboundTblEntry : external subnet id is invalid.");
                return;
            }
            ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                    .getVpnIdMetadata(extSubnetId), loadStart, loadEnd);
            actionsInfos.add(actionLoadMeta);
            actionsInfos.add(new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress)));
        }
        List<NxCtAction> ctActionsListCommit = new ArrayList<>();
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

    protected void installNaptPfibFlow(Routers routers, BigInteger dpnId, List<ExternalIps> externalIps,
            String routerName, int addOrRemove) {
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        LOG.info("installNaptPfibFlow : dpId {}, extNetId {}, srcIp {}", dpnId, extNetId, externalIps);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            //The logic now handle only one external IP per router, others if present will be ignored.
            String externalIp = externalIps.get(0).getIpAddress();
            long extSubnetId = NatUtil.getVpnIdFromExternalSubnet(dataBroker, routerName, externalIp);
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("installNaptPfibFlow : external subnet id is invalid.");
                return;
            }

            BigInteger subnetMetadata = MetaDataUtil.getVpnIdMetadata(extSubnetId);
            matches.add(new MatchMetadata(subnetMetadata, MetaDataUtil.METADATA_MASK_VRFID));
        }
        if (externalIps.isEmpty()) {
            LOG.error("installNaptPfibFlow : no externalIP present for routerId {}", routers.getRouterName());
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(listActionInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, extNetId);
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installInboundEntry(BigInteger dpnId, long routerId, Long extNetId, List<ExternalIps> externalIps,
            int elanId, int addOrRemove) {
        LOG.info("installInboundEntry : dpId {} and routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        if (externalIps.isEmpty()) {
            LOG.error("installInboundEntry : no externalIP present for routerId {}", routerId);
            return;
        }
        String externalIp = externalIps.get(0).getIpAddress();
        matches.add(new MatchIpv4Destination(externalIp,"32"));
        String routerName = NatUtil.getRouterName(dataBroker, routerId);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            long extSubnetId = NatUtil.getVpnIdFromExternalSubnet(dataBroker, routerName, externalIp);
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("installInboundEntry : external subnet id is invalid.");
                return;
            }
            matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extSubnetId),
                    MetaDataUtil.METADATA_MASK_VRFID));
        }
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                .getVpnIdMetadata(routerId), loadStart, loadEnd);
        actionsInfos.add(actionLoadMeta);
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
        LOG.info("installNaptPfibEntry : called for dpnId {} and routerId {} ", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(dnatCtState, dnatCtStateMask));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));


        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);
    }

    private int mostSignificantBit(int value) {
        int mask = 1 << 31;
        for (int bitIndex = 31; bitIndex >= 0; bitIndex--) {
            if ((value & mask) != 0) {
                return bitIndex;
            }
            mask >>>= 1;
        }
        return -1;
    }
}