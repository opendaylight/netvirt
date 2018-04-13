/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnSnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnSnatFlowProgrammer.class);

    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final FibRpcService fibService;
    private final IdManagerService idManager;

    @Inject
    public EvpnSnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final FibRpcService fibService,
                           final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.fibService = fibService;
        this.idManager = idManager;
    }

    public void evpnAdvToBgpAndInstallFibAndTsFlows(final BigInteger dpnId, final short tableId,
                                                    final String externalIp, final String vpnName, final String rd,
                                                    final String nextHopIp, final WriteTransaction writeTx,
                                                    final long routerId, final String routerName,
                                                    final Uuid extNetworkId,
                                                    WriteTransaction writeFlowInvTx) {
     /*
      * 1) Install the flow INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed IP on DPN2) {DNAT to SNAT traffic on
      *     different Hypervisor}
      *
      * 2) Install the flow L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed IP on beyond DC-GW VM){DNAT to SNAT Inter DC traffic}
      *
      * 3) Install the flow PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44)
      *    (If there is no FIP Match on table 25 (PDNAT_TABLE) then default flow to INBOUND_NAPT_TABLE (table=44))
      *
      * 4) Install the flow L3_FIB_TABLE (table=21)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed Ip on DPN1 itself. ie. same Hypervisor)
      *    {DNAT to SNAT Intra DC traffic}
      */
        LOG.info("evpnAdvToBgpAndInstallFibAndTsFlows : Handling SNAT Reverse Traffic for External Fixed IP {} for "
                + "RouterId {}", externalIp, routerId);
        // Get the External Gateway MAC Address which is Router gateway MAC address for SNAT
        String gwMacAddress = NatUtil.getExtGwMacAddFromRouterName(dataBroker, routerName);
        if (gwMacAddress == null) {
            LOG.error("evpnAdvToBgpAndInstallFibAndTsFlows : Unable to Retrieve External Gateway MAC address "
                    + "from Router ID {}", routerId);
            return;
        }
        //get l3Vni value for external VPN
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("evpnAdvToBgpAndInstallFibAndTsFlows : L3VNI value is not configured in Internet VPN {}"
                    + " and RD {} Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with "
                    + "installing SNAT flows for External Fixed IP {}", vpnName, rd, externalIp);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }

        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("evpnAdvToBgpAndInstallFibAndTsFlows : Invalid Vpn Id is found for Vpn Name {}",
                    vpnName);
            return;
        }
        /* As of now neither SNAT nor DNAT will use mac-address while advertising to FIB and BGP instead
         * use only gwMacAddress. Hence default value of macAddress is null
         */
        //Inform to BGP
        NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd, externalIp,
                nextHopIp, l3Vni, null /*InterfaceName*/, gwMacAddress, writeTx, RouteOrigin.STATIC,
                dpnId, extNetworkId);

        //Install custom FIB routes - FIB table.
        List<Instruction> customInstructions = new ArrayList<>();
        customInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
        final String externalFixedIp = NatUtil.validateAndAddNetworkMask(externalIp);

        CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(externalFixedIp)
                .setServiceId(l3Vni).setIpAddressSource(CreateFibEntryInput.IpAddressSource.ExternalFixedIP)
                .setInstruction(customInstructions).build();
        LOG.debug("evpnAdvToBgpAndInstallFibAndTsFlows : Installing custom FIB table {} --> table {} flow on "
                + "NAPT Switch {} with l3Vni {}, ExternalFixedIp {}, ExternalVpnName {} for RouterId {}",
                NwConstants.L3_FIB_TABLE, tableId, dpnId, l3Vni, externalIp, vpnName, routerId);

        ListenableFuture<RpcResult<CreateFibEntryOutput>> futureVxlan = fibService.createFibEntry(input);

        final long finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {
            @Override
            public void onFailure(@Nonnull Throwable error) {
                LOG.error("evpnAdvToBgpAndInstallFibAndTsFlows : Error in custom fib routes install process for "
                        + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for RouterId {}",
                        externalIp, dpnId, finalL3Vni, vpnName, routerId, error);
            }

            @Override
            public void onSuccess(@Nonnull RpcResult<CreateFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LOG.info("evpnAdvToBgpAndInstallFibAndTsFlows : Successfully installed custom FIB routes for "
                            + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for RouterId {}",
                            externalIp, dpnId, finalL3Vni, vpnName, routerId);

                 /* Install the flow INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44)
                  * (SNAT to DNAT reverse Traffic: If traffic is Initiated from NAPT to FIP VM on different Hypervisor)
                  */
                    makeTunnelTableEntry(dpnId, finalL3Vni, customInstructions, tableId, writeFlowInvTx);
                 /* Install the flow L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44)
                  * (SNAT reverse traffic: If the traffic is Initiated from DC-GW to VM (SNAT Reverse traffic))
                  */
                    NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, gwMacAddress, customInstructions, mdsalManager,
                            writeFlowInvTx);

                 /* Install the flow PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44)
                  * If there is no FIP Match on table 25 (PDNAT_TABLE)
                  */
                    NatUtil.makePreDnatToSnatTableEntry(mdsalManager, dpnId, tableId, writeFlowInvTx);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    public void evpnDelFibTsAndReverseTraffic(final BigInteger dpnId, final long routerId, final String externalIp,
                                              final String vpnName, String extGwMacAddress,
                                              WriteTransaction removeFlowInvTx) {
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
      * 4) Remove the flow L3_FIB_TABLE (table=21)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed Ip on DPN1 itself. ie. same Hypervisor)
      *    {DNAT to SNAT Intra DC traffic}
      */
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null) {
            LOG.error("evpnDelFibTsAndReverseTraffic : Could not retrieve RD value from VPN Name {}", vpnName);
            return;
        }
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("evpnDelFibTsAndReverseTraffic : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        if (extGwMacAddress == null) {
            LOG.error("evpnDelFibTsAndReverseTraffic : Unable to Get External Gateway MAC address for "
                    + "External Router ID {} ", routerId);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("evpnDelFibTsAndReverseTraffic : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "SNAT flows for External Fixed IP {}", vpnName, rd, externalIp);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }

        final String externalFixedIp = NatUtil.validateAndAddNetworkMask(externalIp);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder()
                .setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalFixedIp)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).setServiceId(l3Vni).build();
        LOG.debug("evpnDelFibTsAndReverseTraffic : Removing custom FIB table {} --> table {} flow on "
                        + "NAPT Switch {} with l3Vni {}, ExternalFixedIp {}, ExternalVpnName {} for RouterId {}",
                NwConstants.L3_FIB_TABLE, NwConstants.INBOUND_NAPT_TABLE, dpnId, l3Vni, externalIp, vpnName, routerId);

        ListenableFuture<RpcResult<RemoveFibEntryOutput>> futureVxlan = fibService.removeFibEntry(input);
        final long finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<RemoveFibEntryOutput>>() {
            @Override
            public void onFailure(@Nonnull Throwable error) {
                LOG.error("evpnDelFibTsAndReverseTraffic : Error in custom fib routes remove process for "
                        + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for RouterId {}",
                        externalIp, dpnId, finalL3Vni, vpnName, routerId, error);
            }

            @Override
            public void onSuccess(@Nonnull RpcResult<RemoveFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LOG.info("evpnDelFibTsAndReverseTraffic : Successfully removed custom FIB routes for "
                            + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for "
                            + "RouterId {}", externalIp, dpnId, finalL3Vni, vpnName, routerId);

                    //remove INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44) flow
                    removeTunnelTableEntry(dpnId, finalL3Vni, removeFlowInvTx);
                    //remove L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44) flow
                    NatUtil.removePreDnatToSnatTableEntry(mdsalManager, dpnId, removeFlowInvTx);
                    //remove PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44) flow
                    NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, extGwMacAddress, mdsalManager, removeFlowInvTx);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    public void makeTunnelTableEntry(BigInteger dpnId, long l3Vni, List<Instruction> customInstructions,
                                     short tableId, WriteTransaction writeFlowTx) {
        LOG.debug("makeTunnelTableEntry : Create terminating service table {} --> table {} flow on NAPT DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, tableId, dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.SNAT_FLOW_NAME), 5,
                String.format("%s:%d", "TST Flow Entry ", l3Vni),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, customInstructions);
        mdsalManager.addFlowToTx(dpnId, terminatingServiceTableFlowEntity, writeFlowTx);
        LOG.debug("makeTunnelTableEntry : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    public void removeTunnelTableEntry(BigInteger dpnId, long l3Vni, WriteTransaction removeFlowInvTx) {
        LOG.debug("removeTunnelTableEntry : Remove terminating service table {} --> table {} flow on NAPT DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE,
                NwConstants.INBOUND_NAPT_TABLE, dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.SNAT_FLOW_NAME),
                5, String.format("%s:%d", "TST Flow Entry ", l3Vni), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, null);
        mdsalManager.removeFlowToTx(dpnId, flowEntity, removeFlowInvTx);
        LOG.debug("removeTunnelTableEntry : Successfully removed terminating service table flow {} on DpnId {}",
                flowEntity, dpnId);
    }
}
