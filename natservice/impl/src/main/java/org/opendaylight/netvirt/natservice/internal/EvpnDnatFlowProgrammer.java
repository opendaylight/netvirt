/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
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
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOpBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnDnatFlowProgrammer {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnDnatFlowProgrammer.class);

    private static final Uint64 COOKIE_TUNNEL = Uint64.valueOf("9000000", 16).intern();

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;
    private final FibRpcService fibService;
    private final IVpnManager vpnManager;
    private final NatOverVxlanUtil natOverVxlanUtil;

    @Inject
    public EvpnDnatFlowProgrammer(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                           final IBgpManager bgpManager,
                           final IFibManager fibManager,
                           final FibRpcService fibService,
                           final IVpnManager vpnManager,
                           final NatOverVxlanUtil natOverVxlanUtil) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.bgpManager = bgpManager;
        this.fibManager = fibManager;
        this.fibService = fibService;
        this.vpnManager = vpnManager;
        this.natOverVxlanUtil = natOverVxlanUtil;
    }

    public void onAddFloatingIp(final Uint64 dpnId, final String routerName, final Uint32 routerId,
                                final String vpnName,
                                final String internalIp, final String externalIp, final Uuid networkId,
                                final String interfaceName,
                                final String floatingIpInterface,
                                final String floatingIpPortMacAddress,
                                final String rd,
                                final String nextHopIp, final TypedReadWriteTransaction<Configuration> confTx) {
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
        Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("onAddFloatingIp : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        Uint32 l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("onAddFloatingIp : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "DNAT flows for FloatingIp {}", vpnName, rd, externalIp);
            l3Vni = natOverVxlanUtil.getInternetVpnVni(vpnName, routerId);
        }
        FloatingIPListener.updateOperationalDS(dataBroker, routerName, interfaceName, NatConstants.DEFAULT_LABEL_VALUE,
                internalIp, externalIp);
        String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
        //Inform to FIB and BGP
        NatEvpnUtil.addRoutesForVxLanProvType(dataBroker, bgpManager, fibManager, vpnName, rd, fibExternalIp,
                nextHopIp, l3Vni, floatingIpInterface, floatingIpPortMacAddress,
                confTx, RouteOrigin.STATIC, dpnId, networkId);

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

        ListenableFuture<RpcResult<CreateFibEntryOutput>> futureVxlan = fibService.createFibEntry(input);
        LOG.debug("onAddFloatingIp : Add Floating Ip {} , found associated to fixed port {}",
                externalIp, interfaceName);
        if (floatingIpPortMacAddress != null) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                vpnManager.addSubnetMacIntoVpnInstance(vpnName, null, floatingIpPortMacAddress, dpnId, tx);
                vpnManager.addArpResponderFlowsToExternalNetworkIps(routerName,
                        Collections.singleton(externalIp),
                        floatingIpPortMacAddress, dpnId, networkId);
            }), LOG, "Error processing floating IP port with MAC address {}", floatingIpPortMacAddress);
        }
        final Uint32 finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {

            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("onAddFloatingIp : Error {} in custom fib routes install process for Floating "
                        + "IP Prefix {} on DPN {}", error, externalIp, dpnId);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<CreateFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LoggingFutures.addErrorLogging(
                        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, innerConfTx -> {
                            LOG.info("onAddFloatingIp : Successfully installed custom FIB routes for Floating "
                                + "IP Prefix {} on DPN {}", externalIp, dpnId);
                            List<Instruction> instructions = new ArrayList<>();
                            List<ActionInfo> actionsInfos = new ArrayList<>();
                            List<Instruction> customInstructions = new ArrayList<>();
                            customInstructions.add(
                                new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(0));
                            actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
                            instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));
                            /* If more than one floatingIp is available in vpn-to-dpn-list for given dpn id, do not
                            call for
                             * installing INTERNAL_TUNNEL_TABLE (table=36) -> PDNAT_TABLE (table=25) flow entry with
                             * same tunnel_id
                             * again and again.
                             */
                            if (!NatUtil.isFloatingIpPresentForDpn(dataBroker, dpnId, rd, vpnName, externalIp, true)) {
                                makeTunnelTableEntry(dpnId, finalL3Vni, instructions, innerConfTx);
                            }
                            /* Install the flow L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
                             * (DNAT reverse traffic: If the traffic is Initiated from DC-GW to FIP VM (DNAT forward
                             * traffic))
                             */
                            NatEvpnUtil.makeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress,
                                customInstructions,
                                mdsalManager, innerConfTx);
                        }), LOG, "Error installing DNAT flows");
                } else {
                    LOG.error("onAddFloatingIp : Error {} in rpc call to create custom Fib entries for Floating "
                            + "IP Prefix {} on DPN {}", result.getErrors(), externalIp, dpnId);
                }
            }
        }, MoreExecutors.directExecutor());

        //Read the FIP vpn-interface details from Configuration l3vpn:vpn-interfaces model and write into Operational DS
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NatUtil.getVpnInterfaceIdentifier(floatingIpInterface);
        Optional<VpnInterface> optionalVpnInterface =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier);
        if (optionalVpnInterface.isPresent()) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
                for (VpnInstanceNames vpnInstance : optionalVpnInterface.get().nonnullVpnInstanceNames().values()) {
                    if (!vpnName.equals(vpnInstance.getVpnName())) {
                        continue;
                    }
                    VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get());
                    Adjacencies adjs = vpnIfBuilder.augmentation(Adjacencies.class);
                    VpnInterfaceOpDataEntryBuilder vpnIfOpDataEntryBuilder = new VpnInterfaceOpDataEntryBuilder();
                    vpnIfOpDataEntryBuilder.withKey(new VpnInterfaceOpDataEntryKey(interfaceName, vpnName));

                    Map<AdjacencyKey, Adjacency> keyAdjacencyMap =
                        adjs != null && adjs.getAdjacency() != null ? adjs.nonnullAdjacency()
                                : new HashMap<>();
                    List<Adjacency> adjacencyListToImport = new ArrayList<>();
                    for (Adjacency adj : keyAdjacencyMap.values()) {
                        Subnetmap sn = VpnHelper.getSubnetmapFromItsUuid(dataBroker, adj.getSubnetId());
                        if (!VpnHelper.isSubnetPartOfVpn(sn, vpnName)) {
                            continue;
                        }
                        adjacencyListToImport.add(adj);
                    }
                    AdjacenciesOp adjacenciesOp = new AdjacenciesOpBuilder()
                            .setAdjacency(adjacencyListToImport).build();
                    vpnIfOpDataEntryBuilder.addAugmentation(adjacenciesOp);

                    LOG.debug("onAddFloatingIp : Add vpnInterface {} to Operational l3vpn:vpn-interfaces-op-data ",
                            floatingIpInterface);
                    InstanceIdentifier<VpnInterfaceOpDataEntry> vpnIfIdentifierOpDataEntry =
                            NatUtil.getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                    tx.mergeParentStructurePut(vpnIfIdentifierOpDataEntry, vpnIfOpDataEntryBuilder.build());
                    break;
                }
            }), LOG, "onAddFloatingIp : Could not write Interface {}, vpnName {}", interfaceName, vpnName);
        } else {
            LOG.debug("onAddFloatingIp : No vpnInterface {} found in Configuration l3vpn:vpn-interfaces ",
                           floatingIpInterface);
        }
    }

    public void onRemoveFloatingIp(final Uint64 dpnId, final String vpnName, final String externalIp,
                                   final String floatingIpInterface, final String floatingIpPortMacAddress,
                                   final Uint32 routerId) {
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
        Uint32 vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("onRemoveFloatingIp : Invalid Vpn Id is found for Vpn Name {}", vpnName);
            return;
        }
        Uint32 l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("onRemoveFloatingIp : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue with installing "
                    + "DNAT flows for FloatingIp {}", vpnName, rd, externalIp);
            l3Vni = natOverVxlanUtil.getInternetVpnVni(vpnName, routerId);
        }
        String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);

        //Remove Prefix from BGP
        NatUtil.removePrefixFromBGP(bgpManager, fibManager, rd, fibExternalIp, vpnName);

        //Remove custom FIB routes flow for L3_FIB_TABLE (table=21)-> PDNAT_TABLE (table=25)
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                .setSourceDpid(dpnId).setIpAddress(fibExternalIp).setServiceId(l3Vni)
                .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.FloatingIP).build();
        ListenableFuture<RpcResult<RemoveFibEntryOutput>> futureVxlan = fibService.removeFibEntry(input);
        final Uint32 finalL3Vni = l3Vni;
        Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<RemoveFibEntryOutput>>() {

            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("onRemoveFloatingIp : Error {} in custom fib routes remove process for Floating "
                        + "IP Prefix {} on DPN {}", error, externalIp, dpnId);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<RemoveFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                        innerConfTx -> {
                            LOG.info("onRemoveFloatingIp : Successfully removed custom FIB routes for Floating "
                                + "IP Prefix {} on DPN {}", externalIp, dpnId);
                            /*  check if any floating IP information is available in vpn-to-dpn-list for given dpn id.
                             *  If exist any floating IP then do not remove
                             *  INTERNAL_TUNNEL_TABLE (table=36) -> PDNAT_TABLE (table=25) flow entry.
                             */
                            if (!NatUtil.isFloatingIpPresentForDpn(dataBroker, dpnId, rd, vpnName, externalIp, false)) {
                                //Remove the flow for INTERNAL_TUNNEL_TABLE (table=36)-> PDNAT_TABLE (table=25)
                                removeTunnelTableEntry(dpnId, finalL3Vni, innerConfTx);
                            }
                            //Remove the flow for L3_GW_MAC_TABLE (table=19)-> PDNAT_TABLE (table=25)
                            NatEvpnUtil.removeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, mdsalManager,
                                innerConfTx);
                        }), LOG, "Error removing flows");
                } else {
                    LOG.error("onRemoveFloatingIp : Error {} in rpc call to remove custom Fib entries for Floating "
                            + "IP Prefix {} on DPN {}", result.getErrors(), externalIp, dpnId);
                }
            }
        }, MoreExecutors.directExecutor());
        //Read the FIP vpn-interface details from Operational l3vpn:vpn-interfaces model and delete from Operational DS
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NatUtil.getVpnInterfaceIdentifier(floatingIpInterface);
        Optional<VpnInterface> optionalVpnInterface =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, vpnIfIdentifier);
        if (optionalVpnInterface.isPresent()) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> {
                for (VpnInstanceNames vpnInstance : optionalVpnInterface.get().nonnullVpnInstanceNames().values()) {
                    if (!vpnName.equals(vpnInstance.getVpnName())) {
                        continue;
                    }
                    InstanceIdentifier<VpnInterfaceOpDataEntry> vpnOpIfIdentifier = NatUtil
                            .getVpnInterfaceOpDataEntryIdentifier(floatingIpInterface, vpnName);
                    tx.delete(vpnOpIfIdentifier);
                    break;
                }
            }), LOG, "onRemoveFloatingIp : Could not remove vpnInterface {}, vpnName {} from Operational "
                    + "odl-l3vpn:vpn-interface-op-data", floatingIpInterface, vpnName);

            LOG.debug("onRemoveFloatingIp : Remove vpnInterface {} vpnName {} "
                     + "to Operational odl-l3vpn:vpn-interface-op-data", floatingIpInterface, vpnName);
        } else {
            LOG.debug("onRemoveFloatingIp : No vpnInterface {} found "
                    + "in Operational odl-l3vpn:vpn-interface-op-data", floatingIpInterface);
        }
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void makeTunnelTableEntry(Uint64 dpnId, Uint32 l3Vni, List<Instruction> customInstructions,
                                      TypedWriteTransaction<Configuration> confTx) {
        LOG.debug("makeTunnelTableEntry : Create terminating service table {} --> table {} flow on DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.PDNAT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(l3Vni)));
        Map<InstructionKey, Instruction> customInstructionsMap = new HashMap<>();
        int instructionKey = 0;
        for (Instruction instructionObj : customInstructions) {
            customInstructionsMap.put(new InstructionKey(++instructionKey), instructionObj);
        }
        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.DNAT_FLOW_NAME),
                NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY + 1,
                String.format("%s:%s", "TST Flow Entry ", l3Vni),
                0, 0,
                Uint64.valueOf(COOKIE_TUNNEL.toJava().add(BigInteger.valueOf(l3Vni.longValue()))),
                mkMatches, customInstructionsMap);
        mdsalManager.addFlow(confTx, dpnId, terminatingServiceTableFlowEntity);
        LOG.debug("makeTunnelTableEntry : Successfully installed terminating service table flow {} on DpnId {}",
                terminatingServiceTableFlowEntity, dpnId);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void removeTunnelTableEntry(Uint64 dpnId, Uint32 l3Vni, TypedReadWriteTransaction<Configuration> confTx)
            throws ExecutionException, InterruptedException {
        LOG.debug("removeTunnelTableEntry : Remove terminating service table {} --> table {} flow on DpnId {} "
                + "with l3Vni {} as matching parameter", NwConstants.INTERNAL_TUNNEL_TABLE, NwConstants.PDNAT_TABLE,
                dpnId, l3Vni);
        List<MatchInfo> mkMatches = new ArrayList<>();
        mkMatches.add(new MatchTunnelId(Uint64.valueOf(l3Vni)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                NatEvpnUtil.getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, l3Vni, NatConstants.DNAT_FLOW_NAME),
                NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY + 1,
                String.format("%s:%s", "TST Flow Entry ", l3Vni), 0, 0,
                Uint64.valueOf(COOKIE_TUNNEL.toJava().add(BigInteger.valueOf(l3Vni.longValue()))),
                mkMatches, null);
        mdsalManager.removeFlow(confTx, dpnId, flowEntity);
        LOG.debug("removeTunnelTableEntry : Successfully removed terminating service table flow {} on DpnId {}",
                flowEntity, dpnId);
    }
}
