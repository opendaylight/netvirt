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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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


public class EvpnDnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnDnatFlowProgrammer.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final FibRpcService fibService;
    private final IVpnManager vpnManager;
    private final FloatingIPListener floatingIPListener;

    static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);

    public EvpnDnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final FibRpcService fibService,
                           final IVpnManager vpnManager,
                           final FloatingIPListener floatingIPListener) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.fibService = fibService;
        this.vpnManager = vpnManager;
        this.floatingIPListener = floatingIPListener;
    }

    public void onAddFloatingIp(final BigInteger dpnId, final String routerName, final String vpnName,
                                final String internalIp, final String externalIp, final Uuid networkId,
                                final String interfaceName,
                                final String floatingIpInterface,
                                final String floatingIpPortMacAddress,
                                final String rd,
                                final String nextHopIp, final WriteTransaction writeTx) {
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
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.error("NAT Service : Unable to retrieve L3VNI value for Floating IP {} ", externalIp);
            return;
        }
        floatingIPListener.updateOperationalDS(routerName, interfaceName, NatConstants.DEFAULT_LABEL_VALUE,
                internalIp, externalIp);
        //Inform to FIB
        NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd, externalIp + "/32",
                nextHopIp, l3Vni, floatingIpInterface, floatingIpPortMacAddress,
                writeTx, RouteOrigin.STATIC, dpnId);

        List<Instruction> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<Instruction> customInstructions = new ArrayList<>();
        customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(0));
        actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));
        /* Install the Flow table INTERNAL_TUNNEL_TABLE (table=36)-> PDNAT_TABLE (table=25) for SNAT to DNAT
         * reverse traffic for Non-FIP VM on DPN1 to FIP VM on DPN2
         */
        makeTunnelTableEntry(dpnId, l3Vni, instructions);
        /* Install the flow table L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25)
         * (SNAT to DNAT reverse traffic: If the DPN has both SNAT and  DNAT configured )
         */
        List<ActionInfo> actionInfoFib = new ArrayList<>();
        actionInfoFib.add(new ActionSetFieldEthernetDestination(new MacAddress(floatingIpPortMacAddress)));
        List<Instruction> instructionsFib = new ArrayList<>();
        instructionsFib.add(new InstructionApplyActions(actionInfoFib).buildInstruction(0));
        instructionsFib.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(1));

        CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(externalIp + "/32").setServiceId(l3Vni)
                .setInstruction(instructionsFib).build();

        Future<RpcResult<Void>> future1 = fibService.createFibEntry(input);
        ListenableFuture<RpcResult<Void>> futureVxlan = JdkFutureAdapters.listenInPoolThread(future1);
        LOG.debug("NAT Service : Add Floating Ip {} , found associated to fixed port {}",
                externalIp, interfaceName);
        if (floatingIpPortMacAddress != null) {
            vpnManager.setupSubnetMacIntoVpnInstance(vpnName, floatingIpPortMacAddress, dpnId, writeTx,
                    NwConstants.ADD_FLOW);
            vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerName,
                    Collections.singleton(externalIp),
                    floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.ADD_FLOW);
        }

        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("NAT Service : Error {} in custom fib routes install process for Floating "
                        + "IP Prefix {} on DPN {}", error, externalIp, dpnId);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.info("NAT Service : Successfully installed custom FIB routes for Floating "
                            + "IP Prefix {} on DPN {}", externalIp, dpnId);
                } else {
                    LOG.error("NAT Service : Error {} in rpc call to create custom Fib entries for Floating "
                            + "IP Prefix {} on DPN {}, {}", result.getErrors(), externalIp, dpnId);
                }
            }
        });
        /* Install the flow L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
         * (DNAT reverse traffic: If the traffic is Initiated from DC-GW to FIP VM (DNAT forward traffic))
         */
        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, customInstructions, mdsalManager);

        //Read the FIP vpn-interface details from Configuration l3vpn:vpn-interfaces model and write into Operational DS
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NatUtil.getVpnInterfaceIdentifier(floatingIpInterface);
        Optional<VpnInterface> optionalVpnInterface = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                vpnIfIdentifier);
        if (optionalVpnInterface.isPresent()) {
            VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get());
            Adjacencies adjs = vpnIfBuilder.getAugmentation(Adjacencies.class);
            List<Adjacency> adjacencyList = (adjs != null) ? adjs.getAdjacency() : new ArrayList<>();
            Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(adjacencyList).build();
            vpnIfBuilder.addAugmentation(Adjacencies.class, adjacencies);

            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            LOG.debug("NAT Service : Add vpnInterface {} to Operational l3vpn:vpn-interfaces ", floatingIpInterface);
            writeOperTxn.put(LogicalDatastoreType.OPERATIONAL, vpnIfIdentifier, vpnIfBuilder.build(), true);
            if (writeOperTxn != null) {
                writeOperTxn.submit();
            }
        } else {
            LOG.debug("NAT Service : No vpnInterface {} found in Configuration l3vpn:vpn-interfaces ",
                    floatingIpInterface);
        }
    }

    public void onRemoveFloatingIp(final BigInteger dpnId, final String vpnName, final String externalIp,
                                       final String floatingIpInterface, final String floatingIpPortMacAddress) {
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
            LOG.error("NAT Service : Could not retrieve RD value from VPN Name {}  ", vpnName);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.error("NAT Service : Could not retrieve L3VNI value from RD {} in ", rd);
            return;
        }
        //Remove Prefix from BGP
        NatUtil.removePrefixFromBGP(dataBroker, bgpManager, fibManager, rd, externalIp + "/32", vpnName, LOG);

       //Remove the flow for INTERNAL_TUNNEL_TABLE (table=36)-> PDNAT_TABLE (table=25)
        removeTunnelTableEntry(dpnId, l3Vni);

        //Remove custom FIB routes flow for L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25)
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(externalIp + "/32").setServiceId(l3Vni).build();
        Future<RpcResult<Void>> future = fibService.removeFibEntry(input);
        ListenableFuture<RpcResult<Void>> futureVxlan = JdkFutureAdapters.listenInPoolThread(future);
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("NAT Service : Error {} in custom fib routes remove process for Floating "
                        + "IP Prefix {} on DPN {}", error, externalIp, dpnId);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.info("NAT Service : Successfully removed custom FIB routes for Floating "
                            + "IP Prefix {} on DPN {}", externalIp, dpnId);
                } else {
                    LOG.error("NAT Service : Error {} in rpc call to remove custom Fib entries for Floating "
                            + "IP Prefix {} on DPN {}, {}", result.getErrors(), externalIp, dpnId);
                }
            }
        });

        long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.warn("NAT Service : Invalid Vpn Id is found for Vpn Name {}", vpnName);
        }
        //Remove the flow for L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
        NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, mdsalManager);

        //Read the FIP vpn-interface details from Operational l3vpn:vpn-interfaces model and delete from Operational DS
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NatUtil.getVpnInterfaceIdentifier(floatingIpInterface);
        Optional<VpnInterface> optionalVpnInterface = NatUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                vpnIfIdentifier);
        if (optionalVpnInterface.isPresent()) {
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            LOG.debug("NAT Service : Remove vpnInterface {} to Operational l3vpn:vpn-interfaces ", floatingIpInterface);
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, vpnIfIdentifier);
            if (writeOperTxn != null) {
                writeOperTxn.submit();
            }
        } else {
            LOG.debug("NAT Service : No vpnInterface {} found in Operational l3vpn:vpn-interfaces ",
                    floatingIpInterface);
        }
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long l3Vni, List<Instruction> customInstructions) {
        LOG.debug("NAT Service : Create terminating service table {} --> table {} flow on DpnId {} with l3Vni {} "
                        + "as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.PDNAT_TABLE, dpnId,
                l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.DNAT_FLOW_NAME), 6,
                String.format("%s:%d", "TST Flow Entry ", l3Vni),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, customInstructions);
        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
        LOG.debug("NAT Service : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long l3Vni) {
        LOG.debug("NAT Service : Remove terminating service table {} --> table {} flow on DpnId {} with l3Vni {} "
                        + "as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.PDNAT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.DNAT_FLOW_NAME),
                6, String.format("%s:%d", "TST Flow Entry ", l3Vni), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(l3Vni)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("NAT Service : Successfully removed terminating service table flow {} on DpnId {}", flowEntity,
                dpnId);
    }
}
