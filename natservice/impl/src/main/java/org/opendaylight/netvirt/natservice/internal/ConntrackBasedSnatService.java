/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
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
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatRangePresent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConntrackBasedSnatService extends AbstractSnatService {
    private static final Logger LOG = LoggerFactory.getLogger(ConntrackBasedSnatService.class);

    protected static final int TRACKED_NEW_CT_STATE = 0x21;
    protected static final int TRACKED_NEW_CT_MASK = 0x21;
    protected static final int SNAT_CT_STATE = 0x40;
    protected static final int SNAT_CT_STATE_MASK = 0x40;
    protected static final int DNAT_CT_STATE = 0x80;
    protected static final int DNAT_CT_STATE_MASK = 0x80;

    public ConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager, ItmRpcService itmManager,
                                     IdManagerService idManager, NAPTSwitchSelector naptSwitchSelector,
                                     OdlInterfaceRpcService odlInterfaceRpcService,
                                     IInterfaceManager interfaceManager, IVpnFootprintService vpnFootprintService,
                                     IFibManager fibManager) {
        super(dataBroker, mdsalManager, itmManager, odlInterfaceRpcService, idManager, naptSwitchSelector,
                interfaceManager, vpnFootprintService, fibManager);
    }

    @Override
    protected void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        LOG.info("installSnatSpecificEntriesForNaptSwitch: called for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(getDataBroker(), routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), getDataBroker())
                .getElanTag().intValue();
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("InvalidRouterId: unable to installSnatSpecificEntriesForNaptSwitch on dpn {}", dpnId);
            return;
        }
        /* Install Outbound NAT entries */

        installSnatMissEntryForPrimrySwch(dpnId, routerId, elanId, addOrRemove);
        installTerminatingServiceTblEntry(dpnId, routerId, elanId, addOrRemove);

        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(getDataBroker(), routerName);
        createOutboundTblTrackEntry(dpnId, routerId, extGwMacAddress, addOrRemove);
        for (ExternalIps externalIp : routers.getExternalIps()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            //The logic now handle only one external IP per router, others if present will be ignored.
            long extSubnetId = NatConstants.INVALID_ID;
            if (addOrRemove == NwConstants.ADD_FLOW) {
                extSubnetId = NatUtil.getExternalSubnetVpnId(getDataBroker(), externalIp.getSubnetId());
            }
            createOutboundTblEntry(dpnId, routerId, externalIp.getIpAddress(), elanId, extGwMacAddress, addOrRemove);
            installNaptPfibFlow(routers, dpnId, routerId, extSubnetId, addOrRemove);

            //Install Inbound NAT entries
            installInboundEntry(dpnId, routerId, externalIp.getIpAddress(), elanId, extSubnetId, addOrRemove);
            installNaptPfibEntry(dpnId, routerId, addOrRemove);

            String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp.getIpAddress());
            Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker, externalIp.getSubnetId());
            if (externalSubnet.isPresent()) {
                String externalVpn =  externalIp.getSubnetId().getValue();
                String vpnRd = NatUtil.getVpnRd(dataBroker, externalVpn);
                vpnFootprintService.updateVpnToDpnMapping(dpnId, externalVpn, vpnRd, null /* interfaceName*/,
                        new ImmutablePair<>(IpAddresses.IpAddressSource.ExternalFixedIP, fibExternalIp),
                        addOrRemove == NwConstants.ADD_FLOW);
            }
            break;
        }
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
                .getVpnIdMetadata(routerId.longValue()), LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId.longValue());
        syncFlow(dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef, NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                 NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);

    }

    protected void createOutboundTblTrackEntry(BigInteger dpnId, Long routerId, String extGwMacAddress,
            int addOrRemove) {
        LOG.info("createOutboundTblTrackEntry : called for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(SNAT_CT_STATE, SNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
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

    protected void createOutboundTblEntry(BigInteger dpnId, long routerId, String externalIp,
            int elanId, String extGwMacAddress,  int addOrRemove) {
        LOG.info("createOutboundTblEntry : dpId {} and routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(TRACKED_NEW_CT_STATE, TRACKED_NEW_CT_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
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

    protected void installNaptPfibFlow(Routers routers, BigInteger dpnId, long routerId,
            long extSubnetId, int addOrRemove) {
        Long extNetId = NatUtil.getVpnId(getDataBroker(), routers.getNetworkId().getValue());
        LOG.info("installNaptPfibFlow : dpId {}, extNetId {}", dpnId, extNetId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(SNAT_CT_STATE, SNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> listActionInfo = new ArrayList<>();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            if (extSubnetId == NatConstants.INVALID_ID) {
                LOG.error("installNaptPfibFlow : external subnet id is invalid.");
                return;
            }
            ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
                    .getVpnIdMetadata(extSubnetId), LOAD_START, LOAD_END);
            listActionInfo.add(actionLoadMeta);
        }
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(listActionInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef = flowRef + "OUTBOUND";
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.SNAT_TRK_FLOW_PRIORITY,
                flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installInboundEntry(BigInteger dpnId, long routerId, String externalIp, int elanId, long extSubnetId,
            int addOrRemove) {
        LOG.info("installInboundEntry : dpId {} and routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpv4Destination(externalIp,"32"));
        if (addOrRemove == NwConstants.ADD_FLOW) {
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
                .getVpnIdMetadata(routerId), LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId, NwConstants
                .NAPT_PFIB_TABLE,ctActionsList);

        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        flowRef = flowRef + "OUTBOUND";
        syncFlow(dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef, NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installNaptPfibEntry(BigInteger dpnId, long routerId, int addOrRemove) {
        LOG.info("installNaptPfibEntry : called for dpnId {} and routerId {} ", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(DNAT_CT_STATE, DNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(BigInteger.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));


        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef = flowRef + "INBOUND";
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);
    }
}
