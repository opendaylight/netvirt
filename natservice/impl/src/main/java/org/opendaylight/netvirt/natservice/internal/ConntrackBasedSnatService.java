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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
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
import org.opendaylight.genius.mdsalutil.actions.ActionNxCtClear;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadMetadata;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.natservice.ha.NatDataUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.dpns.IpAddresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatRangePresent;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
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
                                     IFibManager fibManager, NatDataUtil natDataUtil,
                                     DataTreeEventCallbackRegistrar eventCallbacks) {
        super(dataBroker, mdsalManager, itmManager, odlInterfaceRpcService, idManager, naptSwitchSelector,
                interfaceManager, vpnFootprintService, fibManager, natDataUtil, eventCallbacks);
    }

    @Override
    protected void addSnatSpecificEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
        Routers routers, Uint64 dpnId) {
        LOG.info("installSnatSpecificEntriesForNaptSwitch: called for router {}",
            routers.getRouterName());
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(confTx, routerName);
        int elanId = NatUtil.getElanInstanceByName(confTx, routers.getNetworkId().getValue())
            .getElanTag().intValue();
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("InvalidRouterId: unable to installSnatSpecificEntriesForNaptSwitch on dpn {}", dpnId);
            return;
        }
        /* Install Outbound NAT entries */

        addSnatMissEntryForPrimrySwch(confTx, dpnId, routerId, elanId);

        String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterName(confTx, routerName);
        addOutboundTblTrackEntry(confTx, dpnId, routerId, extGwMacAddress);
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            //The logic now handle only one external IP per router, others if present will be ignored.
            Uint32 extSubnetId = NatUtil.getExternalSubnetVpnId(confTx, externalIp.getSubnetId());
            addOutboundTblEntry(confTx, dpnId, routerId, externalIp.getIpAddress(), elanId, extGwMacAddress);
            addNaptPfibFlow(confTx, routers, dpnId, routerId, extSubnetId);

            //Install Inbound NAT entries
            addInboundEntry(confTx, dpnId, routerId, externalIp.getIpAddress(), elanId, extSubnetId);
            addNaptPfibEntry(confTx, dpnId, routerId);

            String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp.getIpAddress());
            Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(confTx, externalIp.getSubnetId());
            if (externalSubnet.isPresent()) {
                String externalVpn =  externalIp.getSubnetId().getValue();
                String vpnRd = NatUtil.getVpnRd(confTx, externalVpn);
                vpnFootprintService.updateVpnToDpnMapping(dpnId, externalVpn, vpnRd,
                        null /* interfaceName*/, new ImmutablePair<>(IpAddresses
                                .IpAddressSource.ExternalFixedIP, fibExternalIp), true);
            }
            break;
        }
    }

    @Override
    protected void removeSnatSpecificEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Routers routers, Uint64 dpnId) throws ExecutionException, InterruptedException {
        LOG.info("installSnatSpecificEntriesForNaptSwitch: called for router {}",
            routers.getRouterName());
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(confTx, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("InvalidRouterId: unable to installSnatSpecificEntriesForNaptSwitch on dpn {}", dpnId);
            return;
        }
        /* Remove Outbound NAT entries */

        removeSnatMissEntryForPrimrySwch(confTx, dpnId, routerId);

        removeOutboundTblTrackEntry(confTx, dpnId, routerId);
        for (ExternalIps externalIp : routers.nonnullExternalIps().values()) {
            if (!NWUtil.isIpv4Address(externalIp.getIpAddress())) {
                // In this class we handle only IPv4 use-cases.
                continue;
            }
            //The logic now handle only one external IP per router, others if present will be ignored.
            removeOutboundTblEntry(confTx, dpnId, routerId);
            removeNaptPfibFlow(confTx, routers, dpnId, routerId);

            //Install Inbound NAT entries
            removeInboundEntry(confTx, dpnId, routerId);
            removeNaptPfibEntry(confTx, dpnId, routerId);

            String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp.getIpAddress());
            Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(confTx, externalIp.getSubnetId());
            if (externalSubnet.isPresent()) {
                String externalVpn =  externalIp.getSubnetId().getValue();
                String vpnRd = NatUtil.getVpnRd(confTx, externalVpn);
                vpnFootprintService.updateVpnToDpnMapping(dpnId, externalVpn, vpnRd, null /* interfaceName*/,
                    new ImmutablePair<>(IpAddresses.IpAddressSource.ExternalFixedIP, fibExternalIp),
                    false);
            }
            break;
        }
    }

    @Override
    protected void addSnatSpecificEntriesForNonNaptSwitch() {
        // Nothing to to do here
    }

    @Override
    protected void removeSnatSpecificEntriesForNonNaptSwitch() {
        // Nothing to to do here
    }

    protected void addSnatMissEntryForPrimrySwch(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                 Uint32 routerId, int elanId) {
        LOG.info("installSnatSpecificEntriesForNaptSwitch : called for the primary NAPT switch dpnId {}", dpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
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
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches,
                instructions);
    }

    protected void removeSnatMissEntryForPrimrySwch(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                    Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("installSnatSpecificEntriesForNaptSwitch : called for the primary NAPT switch dpnId {}", dpnId);

        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef);
    }

    protected void addOutboundTblTrackEntry(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                            Uint32 routerId, String extGwMacAddress) {
        LOG.info("createOutboundTblTrackEntry : called for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(SNAT_CT_STATE, SNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        listActionInfo.add(new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress)));
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId) + "trkest";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches,
                instructionInfo);
    }

    protected void removeOutboundTblTrackEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                               Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("createOutboundTblTrackEntry : called for switch {}, routerId {}", dpnId, routerId);

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId) + "trkest";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef);
    }

    protected void addOutboundTblEntry(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId, Uint32 routerId,
        String externalIp, int elanId, String extGwMacAddress) {
        LOG.info("createOutboundTblEntry : dpId {} and routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(TRACKED_NEW_CT_STATE, TRACKED_NEW_CT_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress)));
        List<NxCtAction> ctActionsListCommit = new ArrayList<>();
        int rangePresent = NxActionNatRangePresent.NXNATRANGEIPV4MIN.getIntValue();
        int flags = NxActionNatFlags.NXNATFSRC.getIntValue();
        NxCtAction nxCtActionCommit = new ActionNxConntrack.NxNat(0, flags, rangePresent,
            IpPrefixOrAddressBuilder.getDefaultInstance(externalIp).getIpAddress(), null,0, 0);
        ctActionsListCommit.add(nxCtActionCommit);
        int ctCommitFlag = 1;
        ActionNxConntrack actionNxConntrackSubmit = new ActionNxConntrack(ctCommitFlag, 0, elanId,
            NwConstants.NAPT_PFIB_TABLE, ctActionsListCommit);
        actionsInfos.add(actionNxConntrackSubmit);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NatConstants.SNAT_NEW_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeOutboundTblEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                          Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("createOutboundTblEntry : dpId {} and routerId {}", dpnId, routerId);
        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef);
    }

    protected void addNaptPfibFlow(TypedReadWriteTransaction<Configuration> confTx, Routers routers, Uint64 dpnId,
                                   Uint32 routerId, Uint32 extSubnetId) {
        Uint32 extNetId = NatUtil.getVpnId(confTx, routers.getNetworkId().getValue());
        LOG.info("installNaptPfibFlow : dpId {}, extNetId {}", dpnId, extNetId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(SNAT_CT_STATE, SNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> listActionInfo = new ArrayList<>();
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("installNaptPfibFlow : external subnet id is invalid.");
            return;
        }
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
            .getVpnIdMetadata(extSubnetId.longValue()), LOAD_START, LOAD_END);
        listActionInfo.add(actionLoadMeta);
        listActionInfo.add(new ActionNxLoadInPort(Uint64.valueOf(BigInteger.ZERO)));
        listActionInfo.add(new ActionNxCtClear());
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(listActionInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef = flowRef + "OUTBOUND";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeNaptPfibFlow(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 dpnId, Uint32 routerId) throws ExecutionException, InterruptedException {
        Uint32 extNetId = NatUtil.getVpnId(confTx, routers.getNetworkId().getValue());
        LOG.info("installNaptPfibFlow : dpId {}, extNetId {}", dpnId, extNetId);
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId) + "OUTBOUND";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef);
    }

    protected void addInboundEntry(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId, Uint32 routerId,
        String externalIp, int elanId, Uint32 extSubnetId) {
        LOG.info("installInboundEntry : dpId {} and routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchIpv4Destination(externalIp,"32"));
        if (extSubnetId == NatConstants.INVALID_ID) {
            LOG.error("installInboundEntry : external subnet id is invalid.");
            return;
        }
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extSubnetId.longValue()),
            MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ActionNxLoadMetadata actionLoadMeta = new ActionNxLoadMetadata(MetaDataUtil
            .getVpnIdMetadata(routerId.longValue()), LOAD_START, LOAD_END);
        actionsInfos.add(actionLoadMeta);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId, NwConstants
            .NAPT_PFIB_TABLE,ctActionsList);

        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        flowRef = flowRef + "OUTBOUND";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeInboundEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                      Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("installInboundEntry : dpId {} and routerId {}", dpnId, routerId);

        String flowRef = getFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId) + "OUTBOUND";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef);
    }

    protected void addNaptPfibEntry(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId, Uint32 routerId) {
        LOG.info("installNaptPfibEntry : called for dpnId {} and routerId {} ", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(DNAT_CT_STATE, DNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(Uint64.valueOf(BigInteger.ZERO)));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId) + "INBOUND";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches,
                instructionInfo);
    }

    protected void removeNaptPfibEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                       Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("installNaptPfibEntry : called for dpnId {} and routerId {} ", dpnId, routerId);
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId) + "INBOUND";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef);
    }
}
