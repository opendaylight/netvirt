/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.types.rev160517.IpPrefixOrAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.action.rev140421.NxActionNatRangePresent;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VxlanGreConntrackBasedSnatService extends ConntrackBasedSnatService {

    private static final Logger LOG = LoggerFactory.getLogger(VxlanGreConntrackBasedSnatService.class);
    private final ExternalRoutersListener externalRouterListener;
    private final IElanService elanManager;
    private final NatOverVxlanUtil natOverVxlanUtil;

    public VxlanGreConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                             ItmRpcService itmManager, OdlInterfaceRpcService odlInterfaceRpcService,
                                             IdManagerService idManager, NAPTSwitchSelector naptSwitchSelector,
                                             ExternalRoutersListener externalRouterListener, IElanService elanManager,
                                             IInterfaceManager interfaceManager,
                                             IVpnFootprintService vpnFootprintService,
                                             IFibManager fibManager, NatDataUtil natDataUtil,
                                             DataTreeEventCallbackRegistrar eventCallbacks,
                                             NatOverVxlanUtil natOverVxlanUtil) {
        super(dataBroker, mdsalManager, itmManager, idManager, naptSwitchSelector, odlInterfaceRpcService,
                interfaceManager, vpnFootprintService, fibManager, natDataUtil, eventCallbacks);
        this.externalRouterListener = externalRouterListener;
        this.elanManager = elanManager;
        this.natOverVxlanUtil = natOverVxlanUtil;
    }

    @Override
    public boolean addSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 primarySwitchId) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(confTx, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnatAllSwitch ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleSnatAllSwitch : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.addSnatAllSwitch(confTx, routers, primarySwitchId);
    }

    @Override
    public boolean removeSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId) throws ExecutionException, InterruptedException {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(confTx, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnatAllSwitch ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleSnatAllSwitch : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.removeSnatAllSwitch(confTx, routers, primarySwitchId);
    }

    public boolean addCentralizedRouterAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleCentralizedRouterAllSwitch ProviderTypes {}",
                extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleCentralizedRouterAllSwitch : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.addCentralizedRouterAllSwitch(confTx, routers, primarySwitchId);
    }

    public boolean removeCentralizedRouterAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId)  throws ExecutionException, InterruptedException {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleCentralizedRouterAllSwitch ProviderTypes {}",
                extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleCentralizedRouterAllSwitch : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.removeCentralizedRouterAllSwitch(confTx, routers, primarySwitchId);
    }

    public boolean addCentralizedRouter(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleCentralizedRouter ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleCentralizedRouter : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.addCentralizedRouter(confTx, routers, primarySwitchId, dpnId);
    }

    public boolean removeCentralizedRouter(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId)  throws ExecutionException, InterruptedException {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleCentralizedRouter ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleCentralizedRouter : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.removeCentralizedRouter(confTx, routers, primarySwitchId, dpnId);
    }

    @Override
    public boolean addSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 primarySwitchId, Uint64 dpnId) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(confTx, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnat ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleSnat : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.addSnat(confTx, routers, primarySwitchId, dpnId);
    }

    @Override
    public boolean removeSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) throws ExecutionException, InterruptedException {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(confTx, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnat ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            LOG.debug("handleSnat : Skip FLAT/VLAN provider networks.");
            return true;
        }
        return super.removeSnat(confTx, routers, primarySwitchId, dpnId);
    }

    @Override
    protected void addSnatSpecificEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 dpnId) {

        LOG.info("installSnatSpecificEntriesForNaptSwitch for router {}",
            routers.getRouterName());
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), dataBroker)
            .getElanTag().intValue();
        /* Install Outbound NAT entries */

        addSnatMissEntryForPrimrySwch(confTx, dpnId, routerId, elanId);
        addTerminatingServiceTblEntryForVxlanGre(confTx, dpnId, routerName, routerId, elanId);
        //Long extNetVpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, routers.getNetworkId());
        if (vpnUuid == null) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: Unable to retrieve external vpn_id for "
                + "external network {} with routerId {}", routers.getNetworkId(), routerId);
            return;
        }
        Uint32 extNetVpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
        LOG.info("installSnatSpecificEntriesForNaptSwitch: external network vpn_id {} for router {}",
            extNetVpnId, routers.getRouterName());
        List<ExternalIps> externalIps = routers.getExternalIps();
        addOutboundTblTrackEntryForVxlanGre(confTx, dpnId, routerId, extNetVpnId);
        addOutboundTblEntryForVxlanGre(confTx, dpnId, routerId, extNetVpnId, externalIps, elanId);
        addNaptPfibFlowForVxlanGre(confTx, routers, dpnId, extNetVpnId);
        addNaptPfibEntry(confTx, dpnId, routerId);

        //Install Inbound NAT entries
        addInboundEntryForVxlanGre(confTx, dpnId, routerId, extNetVpnId, externalIps, elanId);
        if (externalIps.isEmpty()) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: No externalIP present for router {}",
                routerName);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = NatUtil.validateAndAddNetworkMask(externalIps.get(0).getIpAddress());
        externalRouterListener.handleSnatReverseTraffic(confTx, dpnId, routers, routerId, routerName, externalIp);
    }

    @Override
    protected void removeSnatSpecificEntriesForNaptSwitch(TypedReadWriteTransaction<Configuration> confTx,
            Routers routers, Uint64 dpnId) throws ExecutionException, InterruptedException {

        LOG.info("installSnatSpecificEntriesForNaptSwitch for router {}",
            routers.getRouterName());
        String routerName = routers.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);

        /* Remove Outbound NAT entries */
        removeSnatMissEntryForPrimrySwch(confTx, dpnId, routerId);
        removeTerminatingServiceTblEntryForVxlanGre(confTx, dpnId, routerId);
        //Long extNetVpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, routers.getNetworkId());
        if (vpnUuid == null) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: Unable to retrieve external vpn_id for "
                + "external network {} with routerId {}", routers.getNetworkId(), routerId);
            return;
        }
        Uint32 extNetVpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());
        LOG.info("installSnatSpecificEntriesForNaptSwitch: external network vpn_id {} for router {}",
            extNetVpnId, routers.getRouterName());
        List<ExternalIps> externalIps = routers.getExternalIps();
        removeOutboundTblTrackEntryForVxlanGre(confTx, dpnId, routerId);
        removeOutboundTblEntryForVxlanGre(confTx, dpnId, routerId, externalIps);
        removeNaptPfibFlowForVxlanGre(confTx, routers, dpnId, extNetVpnId);
        removeNaptPfibEntry(confTx, dpnId, routerId);

        //Install Inbound NAT entries
        removeInboundEntryForVxlanGre(confTx, dpnId, routerId, externalIps);
        if (externalIps.isEmpty()) {
            LOG.error("installSnatSpecificEntriesForNaptSwitch: No externalIP present for router {}",
                routerName);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = NatUtil.validateAndAddNetworkMask(externalIps.get(0).getIpAddress());
        externalRouterListener.clearFibTsAndReverseTraffic(dpnId, routerId, routers.getNetworkId(),
            Collections.singletonList(externalIp), null, routers.getExtGwMacAddress(), confTx);
    }

    protected void addOutboundTblTrackEntryForVxlanGre(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                       Uint32 routerId, Uint32 extNetVpnId) {
        LOG.info("createOutboundTblTrackEntryForVxlanGre: Install Outbound tracking table flow on dpId {} for "
                + "routerId {}", dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(SNAT_CT_STATE, SNAT_CT_STATE_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(
                MetaDataUtil.getVpnIdMetadata(extNetVpnId.longValue()));
        listActionInfo.add(actionSetFieldMeta);
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.NAPT_PFIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId) + "trkest";
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo);

    }

    protected void removeOutboundTblTrackEntryForVxlanGre(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("createOutboundTblTrackEntryForVxlanGre: Install Outbound tracking table flow on dpId {} for "
            + "routerId {}", dpnId, routerId);

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId) + "trkest";
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef);

    }

    protected void addOutboundTblEntryForVxlanGre(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                  Uint32 routerId, Uint32 extNetVpnId, List<ExternalIps> externalIps,
                                                  int elanId) {
        LOG.info("createOutboundTblEntryForVxlanGre: Install Outbound table flow on dpId {} for routerId {}", dpnId,
                routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(TRACKED_NEW_CT_STATE, TRACKED_NEW_CT_MASK));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        if (externalIps.isEmpty()) {
            LOG.error("createOutboundTblEntryForVxlanGre: No externalIP present for routerId {}",
                    routerId);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String externalIp = externalIps.get(0).getIpAddress();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                .getVpnIdMetadata(extNetVpnId.longValue()));
        actionsInfos.add(actionSetFieldMeta);
        List<ActionNxConntrack.NxCtAction> ctActionsListCommit = new ArrayList<>();
        int rangePresent = NxActionNatRangePresent.NXNATRANGEIPV4MIN.getIntValue();
        int flags = NxActionNatFlags.NXNATFSRC.getIntValue();
        ActionNxConntrack.NxCtAction nxCtActionCommit = new ActionNxConntrack.NxNat(0, flags, rangePresent,
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

    protected void removeOutboundTblEntryForVxlanGre(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                     Uint32 routerId, List<ExternalIps> externalIps)
            throws ExecutionException, InterruptedException {
        LOG.info("createOutboundTblEntryForVxlanGre: Install Outbound table flow on dpId {} for routerId {}", dpnId,
            routerId);
        if (externalIps.isEmpty()) {
            LOG.error("createOutboundTblEntryForVxlanGre: No externalIP present for routerId {}",
                routerId);
            return;
        }
        //The logic now handle only one external IP per router, others if present will be ignored.
        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef);
    }

    protected void addNaptPfibFlowForVxlanGre(TypedWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 dpnId, Uint32 extNetVpnId) {
        LOG.info("installNaptPfibFlowForVxlanGre: Install Napt preFibFlow on dpId {} with matching extNetVpnId {} "
                + "for router {}", dpnId, extNetVpnId, routers.getRouterName());
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNetVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructions = new ArrayList<>();
        listActionInfo.add(new ActionNxLoadInPort(Uint64.ZERO));
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructions.add(new InstructionApplyActions(listActionInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, extNetVpnId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef,
                NatConstants.SNAT_TRK_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeNaptPfibFlowForVxlanGre(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 dpnId, Uint32 extNetVpnId) throws ExecutionException, InterruptedException {
        LOG.info("installNaptPfibFlowForVxlanGre: Install Napt preFibFlow on dpId {} with matching extNetVpnId {} "
            + "for router {}", dpnId, extNetVpnId, routers.getRouterName());
        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, extNetVpnId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef);
    }

    protected void addInboundEntryForVxlanGre(TypedWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                              Uint32 routerId, Uint32 extNeVpnId, List<ExternalIps> externalIps,
                                              int elanId) {
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
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNeVpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<ActionNxConntrack.NxCtAction> ctActionsList = new ArrayList<>();
        ActionNxConntrack.NxCtAction nxCtAction = new ActionNxConntrack.NxNat(0, 0, 0,null, null,0, 0);
        ActionSetFieldMeta actionSetFieldMeta = new ActionSetFieldMeta(MetaDataUtil
                .getVpnIdMetadata(routerId.longValue()));
        actionsInfos.add(actionSetFieldMeta);
        ctActionsList.add(nxCtAction);
        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(0, 0, elanId, NwConstants
                .NAPT_PFIB_TABLE,ctActionsList);

        actionsInfos.add(actionNxConntrack);
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));
        String flowRef = getFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);
    }

    protected void removeInboundEntryForVxlanGre(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                                 Uint32 routerId, List<ExternalIps> externalIps)
            throws ExecutionException, InterruptedException {
        LOG.info("removeInboundEntryForVxlanGre: remove Inbound table entry on dpId {} for routerId {}",
            dpnId, routerId);
        if (externalIps.isEmpty()) {
            LOG.error("removeInboundEntryForVxlanGre : createInboundTblEntry no externalIP present for routerId {}",
                routerId);
            return;
        }

        String flowRef = getFlowRef(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.INBOUND_NAPT_TABLE, flowRef);
    }

    protected void addTerminatingServiceTblEntryForVxlanGre(TypedWriteTransaction<Configuration> confTx,
        Uint64 dpnId, String routerName, Uint32 routerId, int elanId) {
        LOG.info("installTerminatingServiceTblEntryForVxlanGre : creating entry for"
                + "Terminating Service Table for switch {}, routerId {}", dpnId, routerId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        Uint64 tunnelId = Uint64.valueOf(routerId);
        if (elanManager.isOpenStackVniSemanticsEnforced()) {
            tunnelId = natOverVxlanUtil.getRouterVni(routerName, routerId);
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
        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, flowRef,
                NatConstants.DEFAULT_TS_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE, matches, instructions);

    }

    protected void removeTerminatingServiceTblEntryForVxlanGre(TypedReadWriteTransaction<Configuration> confTx,
            Uint64 dpnId, Uint32 routerId) throws ExecutionException, InterruptedException {
        LOG.info("removeTerminatingServiceTblEntryForVxlanGre : removing entry for"
            + "Terminating Service Table for switch {}, routerId {}", dpnId, routerId);

        String flowRef = getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId,  NwConstants.INTERNAL_TUNNEL_TABLE, flowRef);

    }

    @Override
    protected void addSnatMissEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId, Uint32 routerId,
        String routerName, Uint64 primarySwitchId) {
        LOG.debug("addSnatMissEntry : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = NatUtil.getTunnelInterfaceName(dpnId, primarySwitchId, itmManager);
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        if (ifNamePrimary != null) {
            LOG.debug("addSnatMissEntry : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(odlInterfaceRpcService, itmManager,
                    interfaceManager, ifNamePrimary, routerId, true);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);
        LOG.debug("addSnatMissEntry : addSnatMissEntry called for dpnId {} with primaryBucket {} ", dpnId,
                listBucketInfo.get(0));
        // Install the select group
        Uint32 groupId = NatUtil.getUniqueId(idManager, NatConstants.SNAT_IDPOOL_NAME, getGroupIdKey(routerName));
        if (groupId != NatConstants.INVALID_ID) {
            GroupEntity groupEntity = MDSALUtil
                .buildGroupEntity(dpnId, groupId.longValue(), routerName, GroupTypes.GroupAll,
                    listBucketInfo);
            LOG.debug("addSnatMissEntry : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
            mdsalManager.addGroup(confTx, groupEntity);
            // Install miss entry pointing to group
            LOG.debug(
                "addSnatMissEntry : buildSnatFlowEntity is called for dpId {}, routerName {} and groupId {}",
                dpnId, routerName, groupId);
            List<MatchInfo> matches = new ArrayList<>();
            matches.add(new MatchEthernetType(0x0800L));
            matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));

            List<ActionInfo> actionsInfo = new ArrayList<>();

            Uint64 tunnelId = Uint64.valueOf(routerId);
            if (elanManager.isOpenStackVniSemanticsEnforced()) {
                tunnelId = natOverVxlanUtil.getRouterVni(routerName, routerId);
            }

            actionsInfo.add(new ActionSetFieldTunnelId(tunnelId));
            LOG.debug("addSnatMissEntry : Setting the tunnel to the list of action infos {}",
                actionsInfo);
            actionsInfo.add(new ActionGroup(groupId.longValue()));
            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionApplyActions(actionsInfo));
            String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
            NatUtil.addFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, NwConstants.COOKIE_SNAT_TABLE,
                matches,
                instructions);
        } else {
            LOG.error("installSnatMissEntry: Unable to get groupId for router:{}", routerName);
        }
    }

    @Override
    protected void removeSnatMissEntry(TypedReadWriteTransaction<Configuration> confTx, Uint64 dpnId,
                                       Uint32 routerId, String routerName)
            throws ExecutionException, InterruptedException {
        LOG.debug("installSnatMissEntry : Removing SNAT miss entry in switch {}", dpnId);

        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        NatUtil.removeFlow(confTx, mdsalManager, dpnId, NwConstants.PSNAT_TABLE, flowRef);
    }

}
