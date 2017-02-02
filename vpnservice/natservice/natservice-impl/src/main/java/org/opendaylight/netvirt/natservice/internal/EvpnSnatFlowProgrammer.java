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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvpnSnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnSnatFlowProgrammer.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final FibRpcService fibService;
    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);

    EvpnSnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final FibRpcService fibService) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.fibService = fibService;
    }

    public void evpnAdvToBgpAndInstallFibAndTsFlows(final BigInteger dpnId, final short tableId,
                                                    final String externalIp, final String vpnName, final String rd,
                                                    final String nextHopIp, final WriteTransaction writeTx,
                                                    final long routerId, final String extProviderType) {
               /*
                1) Install the flow 36->44 (FIP VM on DPN1 is responding back to external fixed IP on DPN2)
                {DNAT to SNAT traffic on different Hypervisor}

                2) Install the flow 19->44 (FIP VM on DPN1 is responding back to external fixed IP on DPN1 itself)
                {DNAT to SNAT traffic on Same Hypervisor}

                3) Install the flow 25->44 (If there is no FIP Match on table 25 (PDNAT_TABLE) then default flow to
                INBOUND_NAPT_TABLE)
                */
        LOG.info("NAT Service : Handling SNAT Reverse Traffic for External Network {} with Provider Type {}",
                externalIp,extProviderType);
        // Get the External Gateway MAC Address which is Router gateway MAC address for SNAT
        String gwMacAddress = NatUtil.getExtGwMacAddFromRouterId(dataBroker, routerId);
        if (gwMacAddress == null) {
            LOG.error("NAT Service : Unable to Retrieve External Gateway MAC address from Router ID {}", routerId);
            return;
        }
        //get l3Vni value for external VPN
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.INVALID_ID) {
            LOG.error("NAT Service : Unable to retrieve L3VNI value for External IP {} with Provider Type {} ",
                    externalIp,extProviderType);
            return;
        }
        LOG.debug("NAT Service : Provider Type : VXLAN, RD : {}, External IP : {}, NextHopIP : {}, L3Vni : {} "
                + "and GwMacAddress : {} to install the routes in the FIB table and advertise the "
                + "same to the BGP manager",externalIp,rd,nextHopIp,l3Vni,gwMacAddress);
        //Inform to BGP
        //As of now neither SNAT nor DNAT will use macaddress while advertising to FIB and BGP instead
        // use only gwMacAddress. Hence default value of macAddress is null
        NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd, externalIp,
                nextHopIp, l3Vni, gwMacAddress, writeTx, RouteOrigin.STATIC, dpnId);

        LOG.debug("NAT Service : Install custom FIB routes for external network prefix {} with "
                + "provider type {}", externalIp, extProviderType);
        //Install custom FIB routes
        List<Instruction> customInstructions = new ArrayList<>();
        customInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
        //Install the flow from table 36->44 (SNAT to DNAT reverse Traffic: If traffic is Initiated from
        // NAPT to FIP VM on different Hypervisor)
        makeTunnelTableEntry(dpnId, l3Vni, customInstructions);
        //Install the flow from table 19->44 (SNAT reverse traffic: If the traffic is Initiated from
        // DC-GW to VM (SNAT Reverse traffic))
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, gwMacAddress, customInstructions, mdsalManager);

        //Install the flow table25->44 If there is no FIP Match on table 25 (PDNAT_TABLE)
        List<Instruction> preDnatToSnatInstructions = new ArrayList<>();
        preDnatToSnatInstructions.add(new InstructionGotoTable(NwConstants.INBOUND_NAPT_TABLE).buildInstruction(0));
        makePreDnatToSnatTableEntry(dpnId, preDnatToSnatInstructions);

    }

    public void evpnDelFibTsAndReverseTraffic(final BigInteger dpnId, final long routerId, final String externalIp,
                                              final String vpnName, final String providerType) {
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null) {
            LOG.error("NAT Service : Could not retrieve RD value from VPN Name {} in "
                    + "EvpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic()", vpnName);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.INVALID_ID) {
            LOG.error("NAT Service : Could not retrieve L3VNI value from RD {} in "
                    + "EvpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic()", rd);
            return;
        }
        String gwMacAddress = NatUtil.getExtGwMacAddFromRouterId(dataBroker, routerId);
        if (gwMacAddress == null) {
            LOG.error("NAT Service : Unable to Get External Gateway MAC address for External Router ID {} in "
                    + "EvpnSnatFlowProgrammer.evpnDelFibTsAndReverseTraffic()", routerId);
            return;
        }
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder()
                .setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalIp).setServiceId(l3Vni).build();
        fibService.removeFibEntry(input);
        LOG.info("NAT Service : Successfully Removed custom FIB entries for external prefix {} with "
                + "external provider type {}", externalIp, providerType);
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        //remove table36->44 flow
        removeTunnelTableEntry(dpnId, l3Vni);
        //remove table19->44 flow
        NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, gwMacAddress, mdsalManager);
        //remove table25->44 flow
        removePreDnatToSnatTableEntry(dpnId);
    }

    private String getFlowRefPreDnatToSnat(BigInteger dpnId, short tableId, String uniqueId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + uniqueId;
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return NatConstants.SNAT_FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants
                .FLOWID_SEPARATOR + id + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    private void makePreDnatToSnatTableEntry(BigInteger naptDpnId, List<Instruction> preDnatToSnatInstructions) {
        LOG.info("NAT Service : Create Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ", NwConstants.PDNAT_TABLE,
                NwConstants.INBOUND_NAPT_TABLE, naptDpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE,
                matches, preDnatToSnatInstructions);

        mdsalManager.installFlow(naptDpnId, preDnatToSnatTableFlowEntity);
        LOG.debug("NAT Service : Successfully installed Pre-DNAT flow {} on NAPT DpnId {} ",
                preDnatToSnatTableFlowEntity,  naptDpnId);
    }

    private void removePreDnatToSnatTableEntry(BigInteger naptDpnId) {
        LOG.info("NAT Service : Remove Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ", NwConstants.PDNAT_TABLE,
                NwConstants.INBOUND_NAPT_TABLE, naptDpnId);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE, null, null);
        mdsalManager.removeFlow(naptDpnId, preDnatToSnatTableFlowEntity);
        LOG.debug("NAT Service : Successfully removed Pre-DNAT flow {} on NAPT DpnId = {}",
                preDnatToSnatTableFlowEntity, naptDpnId);
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long l3Vni, List<Instruction> customInstructions) {
        LOG.info("NAT Service : Create terminating service table {} --> table {} flow on NAPT DpnId {} with l3Vni {} "
                + "as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.INBOUND_NAPT_TABLE, dpnId,
                l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, ""), 5,
                String.format("%s:%d", "TST Flow Entry ", l3Vni),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, customInstructions);
        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
        LOG.debug("NAT Service : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long l3Vni) {
        LOG.info("NAT Service : Remove terminating service table {} --> table {} flow on NAPT DpnId {} with l3Vni {} "
                        + "as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.INBOUND_NAPT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, ""),
                5, String.format("%s:%d", "TST Flow Entry ", l3Vni), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("NAT Service : Successfully removed terminating service table flow {} on DpnId {}", flowEntity,
                dpnId);
    }
}
