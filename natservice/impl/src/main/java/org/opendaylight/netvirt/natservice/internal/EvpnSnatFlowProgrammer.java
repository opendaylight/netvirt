/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnSnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnSnatFlowProgrammer.class);

    private static final Uint64 COOKIE_TUNNEL = Uint64.valueOf("9000000", 16).intern();

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final FibRpcService fibService;
    private final NatOverVxlanUtil natOverVxlanUtil;

    @Inject
    public EvpnSnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final FibRpcService fibService,
                           final NatOverVxlanUtil natOverVxlanUtil) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.fibService = fibService;
        this.natOverVxlanUtil = natOverVxlanUtil;
    }

    public void evpnAdvToBgpAndInstallFibAndTsFlows(final Uint64 dpnId, final short tableId,
        final String externalIp, final String vpnName, final String rd, final String nextHopIp,
        final Uint32 routerId, final String routerName, final Uuid extNetworkId,
        TypedWriteTransaction<Configuration> confTx) {
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
        Uint32 l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("evpnAdvToBgpAndInstallFibAndTsFlows : L3VNI value is not configured in Internet VPN {}"
                    + " and RD {} Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with "
                    + "installing SNAT flows for External Fixed IP {}", vpnName, rd, externalIp);
            l3Vni = natOverVxlanUtil.getInternetVpnVni(vpnName, routerId);
        }

        Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnName);
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
                nextHopIp, l3Vni, null /*InterfaceName*/, gwMacAddress, confTx, RouteOrigin.STATIC,
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

        final Uint32 finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {
            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("evpnAdvToBgpAndInstallFibAndTsFlows : Error in custom fib routes install process for "
                        + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for RouterId {}",
                        externalIp, dpnId, finalL3Vni, vpnName, routerId, error);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<CreateFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LOG.info("evpnAdvToBgpAndInstallFibAndTsFlows : Successfully installed custom FIB routes for "
                            + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for RouterId {}",
                            externalIp, dpnId, finalL3Vni, vpnName, routerId);

                 /* Install the flow INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44)
                  * (SNAT to DNAT reverse Traffic: If traffic is Initiated from NAPT to FIP VM on different Hypervisor)
                  */
                    makeTunnelTableEntry(dpnId, finalL3Vni, customInstructions, tableId, confTx);
                 /* Install the flow L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44)
                  * (SNAT reverse traffic: If the traffic is Initiated from DC-GW to VM (SNAT Reverse traffic))
                  */
                    NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, gwMacAddress, customInstructions, mdsalManager,
                            confTx);

                 /* Install the flow PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44)
                  * If there is no FIP Match on table 25 (PDNAT_TABLE)
                  */
                    NatUtil.makePreDnatToSnatTableEntry(mdsalManager, dpnId, tableId, confTx);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    public void evpnDelFibTsAndReverseTraffic(final Uint64 dpnId, final Uint32 routerId, final String externalIp,
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
      * 4) Remove the flow L3_FIB_TABLE (table=21)-> INBOUND_NAPT_TABLE (table=44)
      *    (FIP VM on DPN1 is responding back to external fixed Ip on DPN1 itself. ie. same Hypervisor)
      *    {DNAT to SNAT Intra DC traffic}
      */
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null) {
            LOG.error("evpnDelFibTsAndReverseTraffic : Could not retrieve RD value from VPN Name {}", vpnName);
            return;
        }
        Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("evpnDelFibTsAndReverseTraffic : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        if (extGwMacAddress == null) {
            LOG.error("evpnDelFibTsAndReverseTraffic : Unable to Get External Gateway MAC address for "
                    + "External Router ID {} ", routerId);
            return;
        }
        Uint32 l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("evpnDelFibTsAndReverseTraffic : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "SNAT flows for External Fixed IP {}", vpnName, rd, externalIp);
            l3Vni = natOverVxlanUtil.getInternetVpnVni(vpnName, routerId);
        }

        final String externalFixedIp = NatUtil.validateAndAddNetworkMask(externalIp);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder()
                .setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalFixedIp)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.ExternalFixedIP).setServiceId(l3Vni).build();
        LOG.debug("evpnDelFibTsAndReverseTraffic : Removing custom FIB table {} --> table {} flow on "
                        + "NAPT Switch {} with l3Vni {}, ExternalFixedIp {}, ExternalVpnName {} for RouterId {}",
                NwConstants.L3_FIB_TABLE, NwConstants.INBOUND_NAPT_TABLE, dpnId, l3Vni, externalIp, vpnName, routerId);

        ListenableFuture<RpcResult<RemoveFibEntryOutput>> futureVxlan = fibService.removeFibEntry(input);
        final Uint32 finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<RemoveFibEntryOutput>>() {
            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("evpnDelFibTsAndReverseTraffic : Error in custom fib routes remove process for "
                        + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for RouterId {}",
                        externalIp, dpnId, finalL3Vni, vpnName, routerId, error);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<RemoveFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                        innerConfTx -> {
                            LOG.info("evpnDelFibTsAndReverseTraffic : Successfully removed custom FIB routes for "
                                + "External Fixed IP {} on DPN {} with l3Vni {}, ExternalVpnName {} for "
                                + "RouterId {}", externalIp, dpnId, finalL3Vni, vpnName, routerId);

                            //remove INTERNAL_TUNNEL_TABLE (table=36)-> INBOUND_NAPT_TABLE (table=44) flow
                            removeTunnelTableEntry(dpnId, finalL3Vni, innerConfTx);
                            //remove L3_GW_MAC_TABLE (table=19)-> INBOUND_NAPT_TABLE (table=44) flow
                            NatUtil.removePreDnatToSnatTableEntry(innerConfTx, mdsalManager, dpnId);
                            //remove PDNAT_TABLE (table=25)-> INBOUND_NAPT_TABLE (table=44) flow
                            NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, extGwMacAddress, mdsalManager,
                                innerConfTx);
                        }), LOG, "Error removing EVPN SNAT table entries");
                }
            }
        }, MoreExecutors.directExecutor());
    }

    public void makeTunnelTableEntry(Uint64 dpnId, Uint32 l3Vni, List<Instruction> customInstructions,
                                     short tableId, TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("makeTunnelTableEntry : Create terminating service table {} --> table {} flow on NAPT DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, tableId, dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(l3Vni)));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.SNAT_FLOW_NAME),
                NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY,
                String.format("%s:%s", "TST Flow Entry ", l3Vni),
                0, 0, Uint64.valueOf(COOKIE_TUNNEL.toJava().add(BigInteger.valueOf(l3Vni.longValue()))),
                mkMatches, customInstructions);
        mdsalManager.addFlow(confTx, dpnId, terminatingServiceTableFlowEntity);
        LOG.debug("makeTunnelTableEntry : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    public void removeTunnelTableEntry(Uint64 dpnId, Uint32 l3Vni, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        LOG.debug("removeTunnelTableEntry : Remove terminating service table {} --> table {} flow on NAPT DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE,
                NwConstants.INBOUND_NAPT_TABLE, dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.SNAT_FLOW_NAME),
                NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY,
                String.format("%s:%s", "TST Flow Entry ", l3Vni), 0, 0,
                Uint64.valueOf(COOKIE_TUNNEL.toJava().add(BigInteger.valueOf(l3Vni.longValue()))), mkMatches, null);
        mdsalManager.removeFlow(confTx, dpnId, flowEntity);
        LOG.debug("removeTunnelTableEntry : Successfully removed terminating service table flow {} on DpnId {}",
                flowEntity, dpnId);
    }
}
