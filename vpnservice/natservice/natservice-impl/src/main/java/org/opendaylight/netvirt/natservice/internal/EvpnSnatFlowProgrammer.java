/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnSnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnSnatFlowProgrammer.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final IdManagerService idManager;
    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);

    @Inject
    public EvpnSnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.idManager = idManager;
    }

    public void evpnAdvToBgpAndInstallFibAndTsFlows(final BigInteger dpnId, final short tableId,
                                                    final String externalIp, final String vpnName, final String rd,
                                                    final String nextHopIp, final WriteTransaction writeTx,
                                                    final long routerId) {
     /*
      * 1) Install the flow INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed IP on DPN2) {DNAT to SNAT traffic on
      *     different Hypervisor}
      *
      * 2) Install the flow L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed IP on DPN1 itself){DNAT to SNAT traffic on
      *     Same Hypervisor}
      *
      * 3) Install the flow PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44)
      *    (If there is no FIP Match on table 25 (PDNAT_TABLE) then default flow to INBOUND_NAPT_TABLE (table=44))
      *
      */
        LOG.info("NAT Service : Handling SNAT Reverse Traffic for External Network {} ", externalIp);
        // Get the External Gateway MAC Address which is Router gateway MAC address for SNAT
        String gwMacAddress = NatUtil.getExtGwMacAddFromRouterId(dataBroker, routerId);
        if (gwMacAddress == null) {
            LOG.error("NAT Service : Unable to Retrieve External Gateway MAC address from Router ID {}", routerId);
            return;
        }
        //get l3Vni value for external VPN
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("NAT Service : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "SNAT flows for External Fixed IP {}", vpnName, rd, externalIp);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }
        /* As of now neither SNAT nor DNAT will use macaddress while advertising to FIB and BGP instead
         * use only gwMacAddress. Hence default value of macAddress is null
         */
        //Inform to BGP
        NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd, externalIp,
                nextHopIp, l3Vni, null /*InterfaceName*/, gwMacAddress, writeTx, RouteOrigin.STATIC, dpnId);

        List<Instruction> customInstructions = new ArrayList<>();
        customInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
        /* Install the flow INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44)
         * (SNAT to DNAT reverse Traffic: If traffic is Initiated from NAPT to FIP VM on different Hypervisor)
         */
        makeTunnelTableEntry(dpnId, l3Vni, customInstructions, tableId);
        /* Install the flow L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44)
         * (SNAT reverse traffic: If the traffic is Initiated from DC-GW to VM (SNAT Reverse traffic))
         */
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : Invalid Vpn Id is found for Vpn Name {}", vpnName);
        }
        NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, gwMacAddress, customInstructions, mdsalManager);

        /* Install the flow PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44)
         * If there is no FIP Match on table 25 (PDNAT_TABLE)
         */
        NatUtil.makePreDnatToSnatTableEntry(mdsalManager, dpnId, tableId);
    }

    public void evpnDelFibTsAndReverseTraffic(final BigInteger dpnId, final long routerId, final String externalIp,
                                              final String vpnName, String extGwMacAddress) {
     /*
      * 1) Remove the flow INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed IP on DPN2) {DNAT to SNAT traffic on
      *     different Hypervisor}
      *
      * 2) Remove the flow L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed IP on DPN1 itself){DNAT to SNAT traffic on
      *     Same Hypervisor}
      *
      * 3) Remove the flow PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44)
      *    (If there is no FIP Match on table 25 (PDNAT_TABLE) then default flow to INBOUND_NAPT_TABLE (table=44))
      *
      */
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null) {
            LOG.error("NAT Service : Could not retrieve RD value from VPN Name {}  ", vpnName);
            return;
        }
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("NAT Service : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("NAT Service : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "SNAT flows for External Fixed IP {}", vpnName, rd, externalIp);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }
        //remove INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44) flow
        removeTunnelTableEntry(dpnId, l3Vni);
        //remove L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44) flow
        NatUtil.removePreDnatToSnatTableEntry(mdsalManager, dpnId);
        //remove PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44) flow
        if (extGwMacAddress == null) {
            LOG.error("NAT Service : Unable to Get External Gateway MAC address for External Router ID {} ", routerId);
            return;
        }
        NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, extGwMacAddress, mdsalManager);
    }

    public void makeTunnelTableEntry(BigInteger dpnId, long l3Vni, List<Instruction> customInstructions,
                                     short tableId) {
        LOG.debug("NAT Service : Create terminating service table {} --> table {} flow on NAPT DpnId {} with l3Vni {} "
                + "as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, tableId, dpnId,
                l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.SNAT_FLOW_NAME), 5,
                String.format("%s:%d", "TST Flow Entry ", l3Vni),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, customInstructions);
        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
        LOG.debug("NAT Service : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    public void removeTunnelTableEntry(BigInteger dpnId, long l3Vni) {
        LOG.debug("NAT Service : Remove terminating service table {} --> table {} flow on NAPT DpnId {} with l3Vni {} "
                        + "as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.INBOUND_NAPT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.SNAT_FLOW_NAME),
                5, String.format("%s:%d", "TST Flow Entry ", l3Vni), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("NAT Service : Successfully removed terminating service table flow {} on DpnId {}", flowEntity,
                dpnId);
    }
}
