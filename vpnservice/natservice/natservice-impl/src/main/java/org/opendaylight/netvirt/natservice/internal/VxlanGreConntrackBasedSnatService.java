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
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchCtState;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VxlanGreConntrackBasedSnatService extends ConntrackBasedSnatService {

    private static final Logger LOG = LoggerFactory.getLogger(VxlanGreConntrackBasedSnatService.class);

    public VxlanGreConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager,
            ItmRpcService itmManager, OdlInterfaceRpcService interfaceManager, IdManagerService idManager,
            NaptManager naptManager, NAPTSwitchSelector naptSwitchSelector, IVpnManager vpnManager) {
        super(dataBroker, mdsalManager, itmManager, interfaceManager, idManager, naptManager, naptSwitchSelector,
                vpnManager);
    }

    @Override
    public boolean handleSnatAllSwitch(Routers routers, BigInteger primarySwitchId,  int addOrRemove) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnatAllSwitch ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            return false;
        }
        return super.handleSnatAllSwitch(routers, primarySwitchId, addOrRemove);
    }

    @Override
    public boolean handleSnat(Routers routers, BigInteger primarySwitchId, BigInteger dpnId,  int addOrRemove) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("VxlanGreConntrackBasedSnatService: handleSnat ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.FLAT || extNwProviderType == ProviderTypes.VLAN) {
            return false;
        }
        return super.handleSnat(routers, primarySwitchId, dpnId, addOrRemove);
    }

    @Override
    protected void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        LOG.info("VxlanGreConntrackBasedSnatService: installSnatSpecificEntriesForNaptSwitch for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), dataBroker)
                .getElanTag().intValue();
        /* Install Outbound NAT entries */

        installSnatMissEntryForPrimrySwch(dpnId, routerId, elanId, addOrRemove);
        installTerminatingServiceTblEntry(dpnId, routerId, elanId, addOrRemove);
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        if (extNetId == NatConstants.INVALID_ID) {
            Uuid bgpVpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, routers.getNetworkId());
            if (bgpVpnUuid != null) {
                extNetId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                LOG.info("VxlanGreConntrackBasedSnatService: installSnatSpecificEntriesForNaptSwitch for router {} and "
                        + "VPN_ID {}", routers.getRouterName(), extNetId);
            }
        }
        List<ExternalIps> externalIps = routers.getExternalIps();
        createOutboundTblTrackEntry(dpnId, routerId, extNetId,addOrRemove);
        createOutboundTblEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
        installNaptPfibEntry(dpnId, routerId, addOrRemove);
        installNaptPfibEntryForVxlanGre(dpnId, routerId, addOrRemove);
        //Install Inbound NAT entries
        installInboundEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
    }

    protected void installNaptPfibEntryForVxlanGre(BigInteger dpnId, long routerId, int addOrRemove) {
        LOG.info("VxlanGreConntrackBasedSnatService : installNaptPfibEntry called for dpnId {} and routerId {} ",
                dpnId, routerId);
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new NxMatchCtState(snatCtState, snatCtStateMask));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.NAPT_PFIB_TABLE, routerId);
        flowRef += "vxlangre";
        syncFlow(dpnId, NwConstants.NAPT_PFIB_TABLE, flowRef, NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);
    }

    @Override
    protected void installSnatCommonEntriesForNaptSwitch(Routers routers, BigInteger dpnId,  int addOrRemove) {
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        if (extNetId == NatConstants.INVALID_ID) {
            Uuid bgpVpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, routers.getNetworkId());
            if (bgpVpnUuid != null) {
                extNetId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
                LOG.info("VxlanGreConntrackBasedSnatService: installSnatCommonEntriesForNaptSwitch for router {} and"
                        + " VPN_ID {}", routers.getRouterName(), extNetId);
            }
        }
        installDefaultFibRouteForSNAT(dpnId, routerId, addOrRemove);
        List<ExternalIps> externalIps = routers.getExternalIps();
        installInboundFibEntry(dpnId, extNetId,  externalIps, addOrRemove);
    }
}