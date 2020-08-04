/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.netvirt.natservice.internal.NatUtil.buildfloatingIpIdToPortMappingIdentifier;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPopMpls;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMplsLabel;
import org.opendaylight.genius.mdsalutil.matches.MatchTunnelId;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnFloatingIpHandler implements FloatingIPHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VpnFloatingIpHandler.class);
    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    private static final String FLOWID_PREFIX = "NAT.";

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalManager;
    private final VpnRpcService vpnService;
    private final IBgpManager bgpManager;
    private final FibRpcService fibService;
    private final IVpnManager vpnManager;
    private final IFibManager fibManager;
    private final OdlArputilService arpUtilService;
    private final IElanService elanService;
    private final EvpnDnatFlowProgrammer evpnDnatFlowProgrammer;
    private final NatServiceCounters natServiceCounters;
    private final NatOverVxlanUtil natOverVxlanUtil;

    @Inject
    public VpnFloatingIpHandler(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                final VpnRpcService vpnService,
                                final IBgpManager bgpManager,
                                final FibRpcService fibService,
                                final IFibManager fibManager,
                                final OdlArputilService arputilService,
                                final IVpnManager vpnManager,
                                final IElanService elanService,
                                final EvpnDnatFlowProgrammer evpnDnatFlowProgrammer,
                                final NatOverVxlanUtil natOverVxlanUtil,
                                NatServiceCounters natServiceCounters) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalManager = mdsalManager;
        this.vpnService = vpnService;
        this.bgpManager = bgpManager;
        this.fibService = fibService;
        this.fibManager = fibManager;
        this.arpUtilService = arputilService;
        this.vpnManager = vpnManager;
        this.elanService = elanService;
        this.evpnDnatFlowProgrammer = evpnDnatFlowProgrammer;
        this.natServiceCounters = natServiceCounters;
        this.natOverVxlanUtil = natOverVxlanUtil;
    }

    @Override
    public void onAddFloatingIp(final Uint64 dpnId, final String routerUuid, final Uint32 routerId,
                                final Uuid networkId, final String interfaceName,
                                final InternalToExternalPortMap mapping, final String rd,
                                TypedReadWriteTransaction<Configuration> confTx) {
        String externalIp = mapping.getExternalIp();
        String internalIp = mapping.getInternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        Uuid subnetId = NatUtil.getFloatingIpPortSubnetIdFromFloatingIpId(dataBroker, floatingIpId);
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (floatingIpPortMacAddress == null) {
            LOG.error("onAddFloatingIp: Unable to retrieve floatingIp port MAC address from floatingIpId {} for "
                    + "router {} to handle floatingIp {}", floatingIpId, routerUuid, externalIp);
            return;
        }
        Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker, subnetId);
        final String vpnName = externalSubnet.isPresent() ? subnetId.getValue() :
            NatUtil.getAssociatedVPN(dataBroker, networkId);
        final String subnetVpnName = externalSubnet.isPresent() ? subnetId.getValue() : null;
        if (vpnName == null) {
            LOG.error("onAddFloatingIp: No VPN is associated with ext nw {} to handle add floating ip {} configuration "
                    + "for router {}", networkId, externalIp, routerId);
            return;
        }
        if (rd == null) {
            LOG.error("onAddFloatingIp: Unable to retrieve external (internet) VPN RD from external VPN {} for "
                    + "router {} to handle floatingIp {}", vpnName, routerId, externalIp);
            return;
        }
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerUuid, networkId);
        if (provType == null) {
            return;
        }
        /*
         *  For external network of type GRE, it is required to use "Internet VPN VNI" for intra-DC
         *  communication, but we still require "MPLS labels" to reach SNAT/DNAT VMs from external
         *  entities via MPLSOverGRE.
         *
         *  MPLSOverGRE based external networks, the ``opendaylight-vni-ranges`` pool will be
         *  used to carve out a unique VNI per Internet VPN (GRE-provider-type) to be used in the
         *  datapath for traffic forwarding for ``SNAT-to-DNAT`` and ``DNAT-to-DNAT`` cases within the
         *  DataCenter.
        */
        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
        LOG.debug("onAddFloatingIp: Nexthop ip for prefix {} is {}", externalIp, nextHopIp);
        if (provType == ProviderTypes.VXLAN) {
            Uuid floatingIpInterface = NatEvpnUtil.getFloatingIpInterfaceIdFromFloatingIpId(dataBroker, floatingIpId);
            evpnDnatFlowProgrammer.onAddFloatingIp(dpnId, routerUuid, routerId, vpnName, internalIp,
                    externalIp, networkId, interfaceName, floatingIpInterface.getValue(), floatingIpPortMacAddress,
                    rd, nextHopIp, confTx);
            return;
        }
        /*
         *  MPLS label will be used to advertise prefixes and in "L3_LFIB_TABLE" (table 20) taking the packet
         *  to "INBOUND_NAPT_TABLE" (table 44) and "PDNAT_TABLE" (table 25).
         */
        GenerateVpnLabelInput labelInput = new GenerateVpnLabelInputBuilder().setVpnName(vpnName)
            .setIpPrefix(externalIp).build();
        ListenableFuture<RpcResult<GenerateVpnLabelOutput>> labelFuture = vpnService.generateVpnLabel(labelInput);

        ListenableFuture<RpcResult<CreateFibEntryOutput>> future = Futures.transformAsync(labelFuture, result -> {
            if (result.isSuccessful()) {
                GenerateVpnLabelOutput output = result.getResult();
                Uint32 label = output.getLabel();
                LOG.debug("onAddFloatingIp : Generated label {} for prefix {}", label, externalIp);
                FloatingIPListener.updateOperationalDS(dataBroker, routerUuid, interfaceName, label,
                        internalIp, externalIp);
                /*
                 * For external network of type VXLAN all packets going from VMs within the DC, towards the
                 * external gateway device via the External VXLAN Tunnel,we are setting the VXLAN Tunnel ID to
                 * the L3VNI value of VPNInstance to which the VM belongs to.
                 */
                Uint32 l3vni = Uint32.ZERO;
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanService, provType)) {
                    l3vni = natOverVxlanUtil.getInternetVpnVni(vpnName, l3vni);
                }
                String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
                //Inform BGP
                NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd,
                    fibExternalIp, nextHopIp, networkId.getValue(), floatingIpPortMacAddress,
                        label, l3vni, RouteOrigin.STATIC, dpnId);

                List<Instruction> instructions = new ArrayList<>();
                List<ActionInfo> actionsInfos = new ArrayList<>();
                actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
                instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));

                List<ActionInfo> actionInfoFib = new ArrayList<>();
                List<Instruction> customInstructions = new ArrayList<>();
                actionInfoFib.add(new ActionSetFieldEthernetDestination(new MacAddress(floatingIpPortMacAddress)));
                customInstructions.add(new InstructionApplyActions(actionInfoFib).buildInstruction(0));
                customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(1));

                LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                    innerConfTx -> {
                        makeTunnelTableEntry(vpnName, dpnId, label, instructions, innerConfTx, provType);
                        makeLFibTableEntry(dpnId, label, floatingIpPortMacAddress, NwConstants.PDNAT_TABLE,
                            innerConfTx);
                    }), LOG, "Error adding tunnel or FIB table entries");

                CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                        .setSourceDpid(dpnId).setInstruction(customInstructions)
                        .setIpAddress(fibExternalIp).setServiceId(label)
                        .setIpAddressSource(CreateFibEntryInput.IpAddressSource.FloatingIP)
                        .setInstruction(customInstructions).build();
                //Future<RpcResult<java.lang.Void>> createFibEntry(CreateFibEntryInput input);
                ListenableFuture<RpcResult<CreateFibEntryOutput>> future1 = fibService.createFibEntry(input);
                LOG.debug("onAddFloatingIp : Add Floating Ip {} , found associated to fixed port {}",
                        externalIp, interfaceName);
                String networkVpnName =  NatUtil.getAssociatedVPN(dataBroker, networkId);
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                    vpnManager.addSubnetMacIntoVpnInstance(networkVpnName, subnetVpnName,
                            floatingIpPortMacAddress, dpnId, tx);
                    vpnManager.addArpResponderFlowsToExternalNetworkIps(routerUuid,
                            Collections.singleton(externalIp),
                            floatingIpPortMacAddress, dpnId, networkId);
                });
                return future1;
            } else {
                String errMsg = String.format("onAddFloatingIp : Could not retrieve the label for prefix %s "
                        + "in VPN %s, %s", externalIp, vpnName, result.getErrors());
                LOG.error(errMsg);
                return Futures.immediateFailedFuture(new RuntimeException(errMsg));
            }
        }, MoreExecutors.directExecutor());

        Futures.addCallback(future, new FutureCallback<RpcResult<CreateFibEntryOutput>>() {

            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("onAddFloatingIp : Error in generate label or fib install process", error);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<CreateFibEntryOutput> result) {
                if (result.isSuccessful()) {
                    LOG.info("onAddFloatingIp : Successfully installed custom FIB routes for prefix {}", externalIp);
                } else {
                    LOG.error("onAddFloatingIp : Error in rpc call to create custom Fib entries for prefix {} "
                            + "in DPN {}, {}", externalIp, dpnId, result.getErrors());
                }
            }
        }, MoreExecutors.directExecutor());

        // Handle GARP transmission
        final IpAddress extrenalAddress = IpAddressBuilder.getDefaultInstance(externalIp);
        sendGarpOnInterface(dpnId, networkId, extrenalAddress, floatingIpPortMacAddress);

    }

    @Override
    public void onRemoveFloatingIp(final Uint64 dpnId, String routerUuid, Uint32 routerId, final Uuid networkId,
                                   InternalToExternalPortMap mapping, final Uint32 label, final String vrfId,
                                   TypedReadWriteTransaction<Configuration> confTx) {
        String externalIp = mapping.getExternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        Uuid subnetId = NatUtil.getFloatingIpPortSubnetIdFromFloatingIpId(confTx, floatingIpId);
        Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(confTx, subnetId);
        final String vpnName = externalSubnet.isPresent() ? subnetId.getValue() :
            NatUtil.getAssociatedVPN(dataBroker, networkId);
        if (vpnName == null) {
            LOG.error("onRemoveFloatingIp: No VPN associated with ext nw {} to remove floating ip {} configuration "
                    + "for router {}", networkId, externalIp, routerUuid);
            return;
        }

        //Remove floating mac from mymac table
        LOG.debug("onRemoveFloatingIp: Removing FloatingIp {}", externalIp);
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(confTx, floatingIpId);
        if (floatingIpPortMacAddress == null) {
            LOG.error("onRemoveFloatingIp: Unable to retrieve floatingIp port MAC address from floatingIpId {} for "
                    + "router {} to remove floatingIp {}", floatingIpId, routerUuid, externalIp);
            return;
        }

        LoggingFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            String networkVpnName =  NatUtil.getAssociatedVPN(tx, networkId);
            vpnManager.removeSubnetMacFromVpnInstance(networkVpnName, subnetId.getValue(), floatingIpPortMacAddress,
                    dpnId, tx);
            vpnManager.removeArpResponderFlowsToExternalNetworkIps(routerUuid, Collections.singletonList(externalIp),
                    floatingIpPortMacAddress, dpnId, networkId);
        }), LOG, "onRemoveFloatingIp");

        removeFromFloatingIpPortInfo(floatingIpId);
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerUuid, networkId);
        if (provType == null) {
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            Uuid floatingIpInterface = NatEvpnUtil.getFloatingIpInterfaceIdFromFloatingIpId(dataBroker, floatingIpId);
            evpnDnatFlowProgrammer.onRemoveFloatingIp(dpnId, vpnName, externalIp, floatingIpInterface.getValue(),
                    floatingIpPortMacAddress, routerId);
            return;
        }
        cleanupFibEntries(dpnId, vpnName, externalIp, label, vrfId, confTx, provType);
    }

    @Override
    public void cleanupFibEntries(Uint64 dpnId, String vpnName, String externalIp,
                                  Uint32 label, final String rd, TypedReadWriteTransaction<Configuration> confTx,
                                  ProviderTypes provType) {
        //Remove Prefix from BGP
        String fibExternalIp = NatUtil.validateAndAddNetworkMask(externalIp);
        NatUtil.removePrefixFromBGP(bgpManager, fibManager, rd, fibExternalIp, vpnName);
        NatUtil.deletePrefixToInterface(dataBroker, NatUtil.getVpnId(dataBroker, vpnName), fibExternalIp);
        //Remove custom FIB routes

        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
            .setSourceDpid(dpnId).setIpAddress(fibExternalIp).setServiceId(label)
            .setIpAddressSource(RemoveFibEntryInput.IpAddressSource.FloatingIP).build();
        ListenableFuture<RpcResult<RemoveFibEntryOutput>> future = fibService.removeFibEntry(input);

        ListenableFuture<RpcResult<RemoveVpnLabelOutput>> labelFuture = Futures.transformAsync(future, result -> {
            //Release label
            if (result.isSuccessful()) {
                /*  check if any floating IP information is available in vpn-to-dpn-list for given dpn id. If exist any
                 *  floating IP then do not remove INTERNAL_TUNNEL_TABLE (table=36) -> PDNAT_TABLE (table=25) flow entry
                 */
                Boolean removeTunnelFlow = Boolean.TRUE;
                if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanService, provType)) {
                    if (NatUtil.isFloatingIpPresentForDpn(dataBroker, dpnId, rd, vpnName, externalIp,
                            false)) {
                        removeTunnelFlow = Boolean.FALSE;
                    }
                }
                if (removeTunnelFlow) {
                    removeTunnelTableEntry(dpnId, label, confTx);
                }
                removeLFibTableEntry(dpnId, label, confTx);
                RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder()
                        .setVpnName(vpnName).setIpPrefix(externalIp).build();
                Future<RpcResult<RemoveVpnLabelOutput>> labelFuture1 = vpnService.removeVpnLabel(labelInput);
                if (labelFuture1.get() == null || !labelFuture1.get().isSuccessful()) {
                    String errMsg = String.format(
                            "VpnFloatingIpHandler: RPC call to remove VPN label on dpn %s "
                                    + "for prefix %s failed for vpn %s - %s",
                            dpnId, externalIp, vpnName, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
                return JdkFutureAdapters.listenInPoolThread(labelFuture1);
            } else {
                String errMsg = String.format("onRemoveFloatingIp :RPC call to remove custom FIB entries "
                        + "on dpn %s for prefix %s Failed - %s", dpnId, externalIp, result.getErrors());
                LOG.error(errMsg);
                return Futures.immediateFailedFuture(new RuntimeException(errMsg));
            }
        }, MoreExecutors.directExecutor());

        Futures.addCallback(labelFuture, new FutureCallback<RpcResult<RemoveVpnLabelOutput>>() {

            @Override
            public void onFailure(@NonNull Throwable error) {
                LOG.error("onRemoveFloatingIp : Error in removing the label or custom fib entries", error);
            }

            @Override
            public void onSuccess(@NonNull RpcResult<RemoveVpnLabelOutput> result) {
                if (result.isSuccessful()) {
                    LOG.debug("onRemoveFloatingIp : Successfully removed the label for the prefix {} from VPN {}",
                            externalIp, vpnName);
                } else {
                    LOG.error("onRemoveFloatingIp : Error in removing the label for prefix {} from VPN {}, {}",
                        externalIp, vpnName, result.getErrors());
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private String getFlowRef(Uint64 dpnId, short tableId, Uint32 id, String ipAddress) {
        String suffixToUse = "";
        if (tableId == NwConstants.INTERNAL_TUNNEL_TABLE) {
            suffixToUse = NatConstants.TST_FLOW_ID_SUFFIX;
        }
        return FLOWID_PREFIX + suffixToUse + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + id
                + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    private void removeTunnelTableEntry(Uint64 dpnId, Uint32 serviceId,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {

        LOG.debug("removeTunnelTableEntry : called with DpnId = {} and label = {}", dpnId, serviceId);
        mdsalManager.removeFlow(confTx, dpnId,
            new FlowKey(new FlowId(getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""))),
            NwConstants.INTERNAL_TUNNEL_TABLE);
        LOG.debug("removeTunnelTableEntry : Terminating service Entry for dpID {} : label : {} removed successfully",
                dpnId, serviceId);
    }

    private void makeTunnelTableEntry(String vpnName, Uint64 dpnId, Uint32 serviceId,
            List<Instruction> customInstructions, TypedWriteTransaction<Configuration> confTx, ProviderTypes provType) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.info("makeTunnelTableEntry on DpnId = {} and serviceId = {}", dpnId, serviceId);
        int flowPriority = NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY;
        // Increased the 36->25 flow priority. If SNAT is also configured on the same
        // DPN, then the traffic will be hijacked to DNAT and if there are no DNAT match,
        // then handled back to using using flow 25->44(which will be installed as part of SNAT)
        if (NatUtil.isOpenStackVniSemanticsEnforcedForGreAndVxlan(elanService, provType)) {
            mkMatches.add(new MatchTunnelId(Uint64.valueOf(
                    natOverVxlanUtil.getInternetVpnVni(vpnName, serviceId).longValue())));
            flowPriority = NatConstants.DEFAULT_VPN_INTERNAL_TUNNEL_TABLE_PRIORITY + 1;
        } else {
            mkMatches.add(new MatchTunnelId(Uint64.valueOf(serviceId)));
        }
        Map<InstructionKey, Instruction> customInstructionsMap = new HashMap<>();
        int instructionKey = 0;
        for (Instruction instructionObj : customInstructions) {
            customInstructionsMap.put(new InstructionKey(++instructionKey), instructionObj);
        }
        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), flowPriority,
            String.format("%s:%s", "TST Flow Entry ", serviceId),
            0, 0, Uint64.valueOf(COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId.longValue()))),
                mkMatches, customInstructionsMap);

        mdsalManager.addFlow(confTx, dpnId, terminatingServiceTableFlowEntity);
    }

    private void makeLFibTableEntry(Uint64 dpId, Uint32 serviceId, String floatingIpPortMacAddress, short tableId,
                                    TypedWriteTransaction<Configuration> confTx) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId.longValue()));

        Map<InstructionKey, Instruction> instructionMap = new HashMap<>();
        int instructionKey = 0;
        List<ActionInfo> actionsInfos = new ArrayList<>();
        //NAT is required for IPv4 only. Hence always etherType will be IPv4
        actionsInfos.add(new ActionPopMpls(NwConstants.ETHTYPE_IPV4));
        actionsInfos.add(new ActionSetFieldEthernetDestination(new MacAddress(floatingIpPortMacAddress)));
        Instruction writeInstruction = new InstructionApplyActions(actionsInfos).buildInstruction(0);
        instructionMap.put(new InstructionKey(++instructionKey), writeInstruction);
        instructionMap.put(new InstructionKey(++instructionKey),
                new InstructionGotoTable(tableId).buildInstruction(1));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructionMap);

        mdsalManager.addFlow(confTx, dpId, flowEntity);

        LOG.debug("makeLFibTableEntry : LFIB Entry for dpID {} : label : {} modified successfully", dpId, serviceId);
    }

    private void removeLFibTableEntry(Uint64 dpnId, Uint32 serviceId,
            TypedReadWriteTransaction<Configuration> confTx) throws ExecutionException, InterruptedException {

        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        LOG.debug("removeLFibTableEntry : removing LFib entry with flow ref {}", flowRef);

        mdsalManager.removeFlow(confTx, dpnId, new FlowKey(new FlowId(flowRef)), NwConstants.L3_LFIB_TABLE);

        LOG.debug("removeLFibTableEntry : LFIB Entry for dpID : {} label : {} removed successfully",
                dpnId, serviceId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void sendGarpOnInterface(final Uint64 dpnId, Uuid networkId, final IpAddress floatingIpAddress,
                                     String floatingIpPortMacAddress) {
        if (floatingIpAddress.getIpv4Address() == null) {
            LOG.error("sendGarpOnInterface : Failed to send GARP for IP. recieved IPv6.");
            natServiceCounters.garpFailedIpv6();
            return;
        }

        String interfaceName = elanService.getExternalElanInterface(networkId.getValue(), dpnId);
        if (interfaceName == null) {
            LOG.warn("sendGarpOnInterface : Failed to send GARP for IP. Failed to retrieve interface name "
                    + "from network {} and dpn id {}.", networkId.getValue(), dpnId);
            natServiceCounters.garpFailedMissingInterface();
            return;
        }

        try {
            // find the external network interface name for dpn
            List<InterfaceAddress> interfaceAddresses = new ArrayList<>();
            interfaceAddresses.add(new InterfaceAddressBuilder()
                .setInterface(interfaceName)
                .setIpAddress(floatingIpAddress)
                .setMacaddress(new PhysAddress(floatingIpPortMacAddress)).build());

            SendArpRequestInput sendArpRequestInput = new SendArpRequestInputBuilder().setIpaddress(floatingIpAddress)
                .setInterfaceAddress(interfaceAddresses).build();

            LoggingFutures.addErrorLogging(arpUtilService.sendArpRequest(sendArpRequestInput), LOG, "sendArpRequest");
            natServiceCounters.garpSent();
        } catch (Exception e) {
            LOG.error("sendGarpOnInterface : Failed to send GARP request for floating ip {} from interface {}",
                floatingIpAddress.getIpv4Address().getValue(), interfaceName, e);
            natServiceCounters.garpFailedSend();
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeFromFloatingIpPortInfo(Uuid floatingIpId) {
        InstanceIdentifier id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        try {
            Optional<FloatingIpIdToPortMapping> optFloatingIpIdToPortMapping =
                    SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, id);
            if (optFloatingIpIdToPortMapping.isPresent() && optFloatingIpIdToPortMapping.get().isFloatingIpDeleted()) {
                LOG.debug("Deleting floating IP UUID {} to Floating IP neutron port mapping from Floating "
                    + "IP Port Info Config DS", floatingIpId.getValue());
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            }
        } catch (Exception e) {
            LOG.error("removeFromFloatingIpPortInfo : Deleting floating IP UUID {} to Floating IP neutron port "
                    + "mapping from Floating IP Port Info Config DS failed", floatingIpId.getValue(), e);
        }
    }
}
