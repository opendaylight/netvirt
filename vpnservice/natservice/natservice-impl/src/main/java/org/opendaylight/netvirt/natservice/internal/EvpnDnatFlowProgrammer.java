/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnDnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnDnatFlowProgrammer.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final FibRpcService fibService;
    private final IVpnManager vpnManager;
    private final IdManagerService idManager;
    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);

    @Inject
    public EvpnDnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final FibRpcService fibService,
                           final IVpnManager vpnManager,
                           final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.fibService = fibService;
        this.vpnManager = vpnManager;
        this.idManager = idManager;
    }

    public void onAddFloatingIp(final BigInteger dpnId, final String routerName, final long routerId,
                                final String vpnName,
                                final String internalIp, final String externalIp, final Uuid networkId,
                                final String interfaceName,
                                final String floatingIpInterface,
                                final String floatingIpPortMacAddress,
                                final String rd,
                                final String nextHopIp, final WriteTransaction writeFlowInvTx) {
    /*
     *  1) Install the flow INTERNAL_TUNNEL_TABLE (table=36)-> PDNAT_TABLE (table=25) (SNAT VM on DPN1 is
     *     responding back to FIP VM on DPN2) {SNAT to DNAT traffic on different Hypervisor}
     *
     *  2) Install the flow L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25) (FIP VM1 to FIP VM2
     *    Traffic on Same Hypervisor) {DNAT to DNAT on Same Hypervisor}
     *
     *  3) Install the flow L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
     *    (DC-GW is responding back to FIP VM) {DNAT Reverse traffic})
     *
     */
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("onAddFloatingIp : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("onAddFloatingIp : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "DNAT flows for FloatingIp {}", vpnName, rd, externalIp);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }
        FloatingIPListener.updateOperationalDS(dataBroker, routerName, interfaceName, NatConstants.DEFAULT_LABEL_VALUE,
                internalIp, externalIp);
        String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
        //Inform to FIB and BGP
        NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd, fibExternalIp,
                nextHopIp, l3Vni, floatingIpInterface, floatingIpPortMacAddress,
                writeFlowInvTx, RouteOrigin.STATIC, dpnId);

        /* Install the flow table L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25)
         * (SNAT to DNAT reverse traffic: If the DPN has both SNAT and  DNAT configured )
         */
        List<ActionInfo> actionInfoFib = new ArrayList<>();
        actionInfoFib.add(new ActionSetFieldEthernetDestination(new MacAddress(floatingIpPortMacAddress)));
        List<Instruction> instructionsFib = new ArrayList<>();
        instructionsFib.add(new InstructionApplyActions(actionInfoFib).buildInstruction(0));
        instructionsFib.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(1));

        CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(fibExternalIp)
                .setServiceId(l3Vni).setIpAddressSource(CreateFibEntryInput.IpAddressSource.FloatingIP)
                .setInstruction(instructionsFib).build();

        Future<RpcResult<Void>> future1 = fibService.createFibEntry(input);
        ListenableFuture<RpcResult<Void>> futureVxlan = JdkFutureAdapters.listenInPoolThread(future1);
        LOG.debug("onAddFloatingIp : Add Floating Ip {} , found associated to fixed port {}",
                externalIp, interfaceName);
        if (floatingIpPortMacAddress != null) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            vpnManager.setupSubnetMacIntoVpnInstance(vpnName, null /* subnet-vpn-name */, floatingIpPortMacAddress,
                    dpnId, writeTx, NwConstants.ADD_FLOW);
            vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerName,
                    Collections.singleton(externalIp),
                    floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.ADD_FLOW);
            writeTx.submit();
        }
        final long finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(@Nonnull Throwable error) {
                LOG.error("onAddFloatingIp : Error {} in custom fib routes install process for Floating "
                        + "IP Prefix {} on DPN {}", error, externalIp, dpnId);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.info("onAddFloatingIp : Successfully installed custom FIB routes for Floating "
                            + "IP Prefix {} on DPN {}", externalIp, dpnId);
                    List<Instruction> instructions = new ArrayList<>();
                    List<ActionInfo> actionsInfos = new ArrayList<>();
                    List<Instruction> customInstructions = new ArrayList<>();
                    customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(0));
                    actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
                    instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));
                 /* If more than one floatingIp is available in vpn-to-dpn-list for given dpn id, do not call for
                  * installing INTERNAL_TUNNEL_TABLE (table=36) -> PDNAT_TABLE (table=25) flow entry with same tunnel_id
                  * again and again.
                  */
                    if (!NatUtil.isFloatingIpPresentForDpn(dataBroker, dpnId, rd, vpnName, externalIp, true)) {
                        makeTunnelTableEntry(dpnId, finalL3Vni, instructions, writeFlowInvTx);
                    }
                 /* Install the flow L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
                  * (DNAT reverse traffic: If the traffic is Initiated from DC-GW to FIP VM (DNAT forward traffic))
                  */
                    NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, customInstructions,
                            mdsalManager, writeFlowInvTx);
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    //final submit call for writeFlowInvTx
                    futures.add(NatUtil.waitForTransactionToComplete(writeFlowInvTx));
                } else {
                    LOG.error("onAddFloatingIp : Error {} in rpc call to create custom Fib entries for Floating "
                            + "IP Prefix {} on DPN {}, {}", result.getErrors(), externalIp, dpnId);
                }
            }
        }, MoreExecutors.directExecutor());

        //Read the FIP vpn-interface details from Configuration l3vpn:vpn-interfaces model and write into Operational DS
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NatUtil.getVpnInterfaceIdentifier(floatingIpInterface);
        Optional<VpnInterface> optionalVpnInterface =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier);
        if (optionalVpnInterface.isPresent()) {
            VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get());
            Adjacencies adjs = vpnIfBuilder.getAugmentation(Adjacencies.class);
            List<Adjacency> adjacencyList = (adjs != null) ? adjs.getAdjacency() : new ArrayList<>();
            Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(adjacencyList).build();
            vpnIfBuilder.addAugmentation(Adjacencies.class, adjacencies);

            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            LOG.debug("onAddFloatingIp : Add vpnInterface {} to Operational l3vpn:vpn-interfaces ",
                    floatingIpInterface);
            writeOperTxn.put(LogicalDatastoreType.OPERATIONAL, vpnIfIdentifier, vpnIfBuilder.build(),
                    WriteTransaction.CREATE_MISSING_PARENTS);
            writeOperTxn.submit();
        } else {
            LOG.debug("onAddFloatingIp : No vpnInterface {} found in Configuration l3vpn:vpn-interfaces ",
                    floatingIpInterface);
        }
    }

    public void onRemoveFloatingIp(final BigInteger dpnId, final String vpnName, final String externalIp,
                                   final String floatingIpInterface, final String floatingIpPortMacAddress,
                                   final String routerName, final long routerId, WriteTransaction removeFlowInvTx) {
    /*
     *  1) Remove the flow INTERNAL_TUNNEL_TABLE (table=36)-> PDNAT_TABLE (table=25) (SNAT VM on DPN1 is
     *     responding back to FIP VM on DPN2) {SNAT to DNAT traffic on different Hypervisor}
     *
     *  2) Remove the flow L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25) (FIP VM1 to FIP VM2
     *    Traffic on Same Hypervisor) {DNAT to DNAT on Same Hypervisor}
     *
     *  3) Remove the flow L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
     *    (DC-GW is responding back to FIP VM) {DNAT Reverse traffic})
     *
     */
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null) {
            LOG.error("onRemoveFloatingIp : Could not retrieve RD value from VPN Name {}  ", vpnName);
            return;
        }
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("onRemoveFloatingIp : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("onRemoveFloatingIp : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "DNAT flows for FloatingIp {}", vpnName, rd, externalIp);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }
        String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);

        //Remove Prefix from BGP
        NatUtil.removePrefixFromBGP(dataBroker, bgpManager, fibManager, rd, fibExternalIp, vpnName, LOG);

        //Remove custom FIB routes flow for L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25)
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(fibExternalIp).setServiceId(l3Vni)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.FloatingIP).build();
        Future<RpcResult<Void>> future = fibService.removeFibEntry(input);
        ListenableFuture<RpcResult<Void>> futureVxlan = JdkFutureAdapters.listenInPoolThread(future);
        final long finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(@Nonnull Throwable error) {
                LOG.error("onRemoveFloatingIp : Error {} in custom fib routes remove process for Floating "
                        + "IP Prefix {} on DPN {}", error, externalIp, dpnId);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.info("onRemoveFloatingIp : Successfully removed custom FIB routes for Floating "
                            + "IP Prefix {} on DPN {}", externalIp, dpnId);
                     /*  check if any floating IP information is available in vpn-to-dpn-list for given dpn id.
                      *  If exist any floating IP then do not remove
                      *  INTERNAL_TUNNEL_TABLE (table=36) -> PDNAT_TABLE (table=25) flow entry.
                      */
                    if (!NatUtil.isFloatingIpPresentForDpn(dataBroker, dpnId, rd, vpnName, externalIp, false)) {
                        //Remove the flow for INTERNAL_TUNNEL_TABLE (table=36)-> PDNAT_TABLE (table=25)
                        removeTunnelTableEntry(dpnId, finalL3Vni, removeFlowInvTx);
                    }
                    //Remove the flow for L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
                    NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, mdsalManager,
                            removeFlowInvTx);
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    //final submit call for writeFlowInvTx
                    futures.add(NatUtil.waitForTransactionToComplete(removeFlowInvTx));
                } else {
                    LOG.error("onRemoveFloatingIp : Error {} in rpc call to remove custom Fib entries for Floating "
                            + "IP Prefix {} on DPN {}, {}", result.getErrors(), externalIp, dpnId);
                }
            }
        }, MoreExecutors.directExecutor());
        //Read the FIP vpn-interface details from Operational l3vpn:vpn-interfaces model and delete from Operational DS
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NatUtil.getVpnInterfaceIdentifier(floatingIpInterface);
        Optional<VpnInterface> optionalVpnInterface =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, vpnIfIdentifier);
        if (optionalVpnInterface.isPresent()) {
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            LOG.debug("onRemoveFloatingIp : Remove vpnInterface {} to Operational l3vpn:vpn-interfaces ",
                    floatingIpInterface);
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, vpnIfIdentifier);
            writeOperTxn.submit();
        } else {
            LOG.debug("onRemoveFloatingIp : No vpnInterface {} found in Operational l3vpn:vpn-interfaces ",
                    floatingIpInterface);
        }
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long l3Vni, List<Instruction> customInstructions,
                                      WriteTransaction writeFlowInvTx) {
        LOG.debug("makeTunnelTableEntry : Create terminating service table {} --> table {} flow on DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.PDNAT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.DNAT_FLOW_NAME), 6,
                String.format("%s:%d", "TST Flow Entry ", l3Vni),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, customInstructions);
        mdsalManager.addFlowToTx(dpnId, terminatingServiceTableFlowEntity, writeFlowInvTx);
        LOG.debug("makeTunnelTableEntry : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long l3Vni, WriteTransaction removeFlowInvTx) {
        LOG.debug("removeTunnelTableEntry : Remove terminating service table {} --> table {} flow on DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.PDNAT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.DNAT_FLOW_NAME),
                6, String.format("%s:%d", "TST Flow Entry ", l3Vni), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, null);
        mdsalManager.removeFlowToTx(dpnId, flowEntity, removeFlowInvTx);
        LOG.debug("removeTunnelTableEntry : Successfully removed terminating service table flow {} on DpnId {}",
                flowEntity, dpnId);
    }
}
