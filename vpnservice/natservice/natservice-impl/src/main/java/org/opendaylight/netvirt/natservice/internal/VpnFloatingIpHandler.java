/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.netvirt.natservice.internal.NatUtil.buildfloatingIpIdToPortMappingIdentifier;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnFloatingIpHandler implements FloatingIPHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VpnFloatingIpHandler.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final VpnRpcService vpnService;
    private final IBgpManager bgpManager;
    private final FibRpcService fibService;
    private final IVpnManager vpnManager;
    private final IFibManager fibManager;
    private final OdlArputilService arpUtilService;
    private final IElanService elanService;
    private final EvpnDnatFlowProgrammer evpnDnatFlowProgrammer;
    private final INeutronVpnManager nvpnManager;
    private final IdManagerService idManager;
    private static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    private static final String FLOWID_PREFIX = "NAT.";

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
                                final INeutronVpnManager nvpnManager,
                                final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.vpnService = vpnService;
        this.bgpManager = bgpManager;
        this.fibService = fibService;
        this.fibManager = fibManager;
        this.arpUtilService = arputilService;
        this.vpnManager = vpnManager;
        this.elanService = elanService;
        this.evpnDnatFlowProgrammer = evpnDnatFlowProgrammer;
        this.nvpnManager = nvpnManager;
        this.idManager = idManager;
    }

    @Override
    public void onAddFloatingIp(final BigInteger dpnId, final String routerId,
                                final Uuid networkId, final String interfaceName,
                                final InternalToExternalPortMap mapping) {
        String externalIp = mapping.getExternalIp();
        String internalIp = mapping.getInternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        Uuid subnetId = NatUtil.getFloatingIpPortSubnetIdFromFloatingIpId(dataBroker, floatingIpId);
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker, subnetId);
        final String vpnName = externalSubnet.isPresent() ? subnetId.getValue() :
            NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
        final String subnetVpnName = externalSubnet.isPresent() ? subnetId.getValue() : null;
        if (vpnName == null) {
            LOG.info("No VPN associated with ext nw {} to handle add floating ip configuration {} in router {}",
                networkId, externalIp, routerId);
            return;
        }
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
        LOG.debug("Nexthop ip for prefix {} is {}", externalIp, nextHopIp);
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerId);
        if (provType == null) {
            return;
        }
        NatOverVxlanUtil.validateAndCreateVxlanVniPool(dataBroker, nvpnManager, idManager,
                NatConstants.ODL_VNI_POOL_NAME);
        if (provType == ProviderTypes.VXLAN) {
            Uuid floatingIpInterface = NatEvpnUtil.getFloatingIpInterfaceIdFromFloatingIpId(dataBroker, floatingIpId);
            evpnDnatFlowProgrammer.onAddFloatingIp(dpnId, routerId, vpnName, internalIp, externalIp, networkId,
                    interfaceName, floatingIpInterface.getValue(), floatingIpPortMacAddress, rd, nextHopIp, writeTx);
            if (writeTx != null) {
                writeTx.submit();
            }
            return;
        }
        GenerateVpnLabelInput labelInput = new GenerateVpnLabelInputBuilder().setVpnName(vpnName)
            .setIpPrefix(externalIp).build();
        Future<RpcResult<GenerateVpnLabelOutput>> labelFuture = vpnService.generateVpnLabel(labelInput);

        ListenableFuture<RpcResult<Void>> future = Futures.transform(JdkFutureAdapters.listenInPoolThread(labelFuture),
            (AsyncFunction<RpcResult<GenerateVpnLabelOutput>, RpcResult<Void>>) result -> {
                if (result.isSuccessful()) {
                    GenerateVpnLabelOutput output = result.getResult();
                    long label = output.getLabel();
                    LOG.debug("Generated label {} for prefix {}", label, externalIp);
                    FloatingIPListener.updateOperationalDS(dataBroker, routerId, interfaceName, label,
                        internalIp, externalIp);
                    //Inform BGP
                    long l3vni = 0;
                    if (nvpnManager.getEnforceOpenstackSemanticsConfig()) {
                        l3vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, l3vni).longValue();
                    }
                    NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd, subnetId,
                            externalIp + "/32", nextHopIp, networkId.getValue(), floatingIpPortMacAddress,
                            label, l3vni, LOG, RouteOrigin.STATIC, dpnId);

                    List<Instruction> instructions = new ArrayList<>();
                    List<ActionInfo> actionsInfos = new ArrayList<>();
                    actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
                    instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));
                    makeTunnelTableEntry(vpnName, dpnId, label, instructions);

                    //Install custom FIB routes
                    List<ActionInfo> actionInfoFib = new ArrayList<>();
                    List<Instruction> customInstructions = new ArrayList<>();
                    actionInfoFib.add(new ActionSetFieldEthernetDestination(new MacAddress(floatingIpPortMacAddress)));
                    customInstructions.add(new InstructionApplyActions(actionInfoFib).buildInstruction(0));
                    customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(1));

                    makeLFibTableEntry(dpnId, label, NwConstants.PDNAT_TABLE);
                    CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                        .setSourceDpid(dpnId).setInstruction(customInstructions)
                        .setIpAddress(externalIp + "/32").setServiceId(label)
                        .setIpAddressSource(CreateFibEntryInput.IpAddressSource.FloatingIP)
                        .setInstruction(customInstructions).build();
                    //Future<RpcResult<java.lang.Void>> createFibEntry(CreateFibEntryInput input);
                    Future<RpcResult<Void>> future1 = fibService.createFibEntry(input);
                    LOG.debug("Add Floating Ip {} , found associated to fixed port {}", externalIp, interfaceName);
                    if (floatingIpPortMacAddress != null) {
                        String networkVpnName =  NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
                        vpnManager.setupSubnetMacIntoVpnInstance(networkVpnName, subnetVpnName,
                                floatingIpPortMacAddress, dpnId, writeTx, NwConstants.ADD_FLOW);
                        vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerId,
                                Collections.singleton(externalIp),
                            floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.ADD_FLOW);
                        writeTx.submit();
                    }
                    return JdkFutureAdapters.listenInPoolThread(future1);
                } else {
                    String errMsg = String.format("Could not retrieve the label for prefix %s in VPN %s, %s",
                        externalIp, vpnName, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            });

        Futures.addCallback(future, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error in generate label or fib install process", error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.info("Successfully installed custom FIB routes for prefix {}", externalIp);
                } else {
                    LOG.error("Error in rpc call to create custom Fib entries for prefix {} in DPN {}, {}",
                        externalIp, dpnId, result.getErrors());
                }
            }
        });

        // Handle GARP transmission
        final IpAddress extrenalAddress = IpAddressBuilder.getDefaultInstance(externalIp);
        sendGarpOnInterface(dpnId, networkId, extrenalAddress, floatingIpPortMacAddress);

    }

    @Override
    public void onRemoveFloatingIp(final BigInteger dpnId, String routerId, final Uuid networkId,
                                   InternalToExternalPortMap mapping, final long label) {
        String externalIp = mapping.getExternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        Uuid subnetId = NatUtil.getFloatingIpPortSubnetIdFromFloatingIpId(dataBroker, floatingIpId);
        Optional<Subnets> externalSubnet = NatUtil.getOptionalExternalSubnets(dataBroker, subnetId);
        final String vpnName = externalSubnet.isPresent() ? subnetId.getValue() :
            NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
        if (vpnName == null) {
            LOG.info("No VPN associated with ext nw {} to handle remove floating ip configuration {} in router {}",
                networkId, externalIp, routerId);
            return;
        }

        //Remove floating mac from mymac table
        LOG.debug("Removing FloatingIp {}", externalIp);
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (floatingIpPortMacAddress != null) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            vpnManager.setupSubnetMacIntoVpnInstance(vpnName, subnetId.getValue(), floatingIpPortMacAddress,
                    dpnId, writeTx, NwConstants.DEL_FLOW);
            vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerId, Collections.singletonList(externalIp),
                floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.DEL_FLOW);
            writeTx.submit();
        }
        removeFromFloatingIpPortInfo(floatingIpId);
        ProviderTypes provType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerId);
        if (provType == null) {
            return;
        }
        if (provType == ProviderTypes.VXLAN) {
            Uuid floatingIpInterface = NatEvpnUtil.getFloatingIpInterfaceIdFromFloatingIpId(dataBroker, floatingIpId);
            evpnDnatFlowProgrammer.onRemoveFloatingIp(dpnId, vpnName, externalIp, floatingIpInterface.getValue(),
                    floatingIpPortMacAddress, routerId);
            return;
        }
        cleanupFibEntries(dpnId, vpnName, externalIp, label);
    }

    @Override
    public void cleanupFibEntries(final BigInteger dpnId, final String vpnName, final String externalIp,
                                  final long label) {
        //Remove Prefix from BGP
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        NatUtil.removePrefixFromBGP(dataBroker, bgpManager, fibManager, rd, externalIp + "/32", vpnName, LOG);

        //Remove custom FIB routes

        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
            .setSourceDpid(dpnId).setIpAddress(externalIp + "/32").setServiceId(label).build();
        Future<RpcResult<Void>> future = fibService.removeFibEntry(input);

        ListenableFuture<RpcResult<Void>> labelFuture = Futures.transform(JdkFutureAdapters.listenInPoolThread(future),
            (AsyncFunction<RpcResult<Void>, RpcResult<Void>>) result -> {
                //Release label
                if (result.isSuccessful()) {
                /*  check if any floating IP information is available in vpn-to-dpn-list for given dpn id. If exist any
                 *  floating IP then do not remove INTERNAL_TUNNEL_TABLE (table=36) -> PDNAT_TABLE (table=25) flow entry
                 */
                    Boolean removeTunnelFlow = Boolean.TRUE;
                    if (nvpnManager.getEnforceOpenstackSemanticsConfig()) {
                        if (NatUtil.isFloatingIpPresentForDpn(dataBroker, dpnId, rd, vpnName, externalIp)) {
                            removeTunnelFlow = Boolean.FALSE;
                        }
                    }
                    if (removeTunnelFlow) {
                        removeTunnelTableEntry(dpnId, label);
                    }
                    removeLFibTableEntry(dpnId, label);
                    RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder()
                        .setVpnName(vpnName).setIpPrefix(externalIp).build();
                    Future<RpcResult<Void>> labelFuture1 = vpnService.removeVpnLabel(labelInput);
                    return JdkFutureAdapters.listenInPoolThread(labelFuture1);
                } else {
                    String errMsg = String.format("RPC call to remove custom FIB entries on dpn %s for "
                        + "prefix %s Failed - %s", dpnId, externalIp, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            });

        Futures.addCallback(labelFuture, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error in removing the label or custom fib entries", error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if (result.isSuccessful()) {
                    LOG.debug("Successfully removed the label for the prefix {} from VPN {}", externalIp, vpnName);
                } else {
                    LOG.error("Error in removing the label for prefix {} from VPN {}, {}",
                        externalIp, vpnName, result.getErrors());
                }
            }
        });
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + id
                + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long serviceId) {
        LOG.info("remove terminatingServiceActions called with DpnId = {} and label = {}", dpnId, serviceId);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(serviceId)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""),
            5, String.format("%s:%d", "TST Flow Entry ", serviceId), 0, 0,
            COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully {}", dpnId, serviceId);
    }

    private void makeTunnelTableEntry(String vpnName, BigInteger dpnId, long serviceId,
            List<Instruction> customInstructions) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.info("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}", dpnId, serviceId);
        int flowPriority = 5;
        // Increased the 36->25 flow priority. If SNAT is also configured on the same
        // DPN, then the traffic will be hijacked to DNAT and if there are no DNAT match,
        // then handled back to using using flow 25->44(which will be installed as part of SNAT)
        if (nvpnManager.getEnforceOpenstackSemanticsConfig()) {
            mkMatches.add(new MatchTunnelId(NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, serviceId)));
            flowPriority = 6;
        } else {
            mkMatches.add(new MatchTunnelId(BigInteger.valueOf(serviceId)));
        }

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), flowPriority,
            String.format("%s:%d", "TST Flow Entry ", serviceId),
            0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, customInstructions);

        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
    }

    private void makeLFibTableEntry(BigInteger dpId, long serviceId, short tableId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId));

        List<Instruction> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPopMpls());
        Instruction writeInstruction = new InstructionApplyActions(actionsInfos).buildInstruction(0);
        instructions.add(writeInstruction);
        instructions.add(new InstructionGotoTable(tableId).buildInstruction(1));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructions);

        mdsalManager.installFlow(dpId, flowEntity);

        LOG.debug("LFIB Entry for dpID {} : label : {} modified successfully {}", dpId, serviceId);
    }

    private void removeLFibTableEntry(BigInteger dpnId, long serviceId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        LOG.debug("removing LFib entry with flow ref {}", flowRef);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            NwConstants.COOKIE_VM_LFIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.debug("LFIB Entry for dpID : {} label : {} removed successfully {}", dpnId, serviceId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void sendGarpOnInterface(final BigInteger dpnId, Uuid networkId, final IpAddress floatingIpAddress,
                                     String floatingIpPortMacAddress) {
        if (floatingIpAddress.getIpv4Address() == null) {
            LOG.info("Failed to send GARP for IP. recieved IPv6.");
            NatServiceCounters.garp_failed_ipv6.inc();
            return;
        }

        String interfaceName = elanService.getExternalElanInterface(networkId.getValue(), dpnId);
        if (interfaceName == null) {
            LOG.warn("Failed to send GARP for IP. Failed to retrieve interface name from network {} and dpn id {}.",
                networkId.getValue(), dpnId);
            NatServiceCounters.garp_failed_missing_interface.inc();
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
            arpUtilService.sendArpRequest(sendArpRequestInput);
            NatServiceCounters.garp_sent.inc();
        } catch (Exception e) {
            LOG.error("Failed to send GARP request for floating ip {} from interface {}",
                floatingIpAddress.getIpv4Address().getValue(), interfaceName, e);
            NatServiceCounters.garp_failed_send.inc();
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeFromFloatingIpPortInfo(Uuid floatingIpId) {
        InstanceIdentifier id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        try {
            Optional<FloatingIpIdToPortMapping> optFloatingIpIdToPortMapping =
                NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (optFloatingIpIdToPortMapping.isPresent() && optFloatingIpIdToPortMapping.get().isFloatingIpDeleted()) {
                LOG.debug("Deleting floating IP UUID {} to Floating IP neutron port mapping from Floating "
                    + "IP Port Info Config DS", floatingIpId.getValue());
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            }
        } catch (Exception e) {
            LOG.error("Deleting floating IP UUID {} to Floating IP neutron port mapping from Floating "
                + "IP Port Info Config DS failed with exception {}", floatingIpId.getValue(), e);
        }
    }

}
