/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.CreateFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.RemoveFibEntryInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.GenerateVpnLabelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveVpnLabelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnFloatingIpHandler implements FloatingIPHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VpnFloatingIpHandler.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final VpnRpcService vpnService;
    private final IBgpManager bgpManager;
    private final FibRpcService fibService;
    private final FloatingIPListener floatingIPListener;
    private final IVpnManager vpnManager;
    private final IFibManager fibManager;
    private final OdlArputilService arpUtilService;
    static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    static final String FLOWID_PREFIX = "NAT.";

    public VpnFloatingIpHandler(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                final VpnRpcService vpnService,
                                final IBgpManager bgpManager,
                                final FibRpcService fibService,
                                final FloatingIPListener floatingIPListener,
                                final IFibManager fibManager,
                                final OdlArputilService arputilService,
                                final IVpnManager vpnManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.vpnService = vpnService;
        this.bgpManager = bgpManager;
        this.fibService = fibService;
        this.floatingIPListener = floatingIPListener;
        this.fibManager = fibManager;
        this.arpUtilService = arputilService;
        this.vpnManager = vpnManager;
    }

    @Override
    public void onAddFloatingIp(final BigInteger dpnId, final String routerId,
                                Uuid networkId, final String interfaceName, final String externalIp,
                                final String internalIp) {
        final String vpnName = getAssociatedVPN(networkId, routerId);
        if (vpnName == null) {
            LOG.info("No VPN associated with ext nw {} to handle add floating ip configuration {} in router {}",
                    networkId, externalIp, routerId);
            return;
        }

        GenerateVpnLabelInput labelInput = new GenerateVpnLabelInputBuilder().setVpnName(vpnName)
                .setIpPrefix(externalIp).build();
        Future<RpcResult<GenerateVpnLabelOutput>> labelFuture = vpnService.generateVpnLabel(labelInput);

        ListenableFuture<RpcResult<Void>> future = Futures.transform(JdkFutureAdapters.listenInPoolThread(labelFuture),
                new AsyncFunction<RpcResult<GenerateVpnLabelOutput>, RpcResult<Void>>() {

            @Override
            public ListenableFuture<RpcResult<Void>> apply(RpcResult<GenerateVpnLabelOutput> result) throws Exception {
                if(result.isSuccessful()) {
                    GenerateVpnLabelOutput output = result.getResult();
                    long label = output.getLabel();
                    LOG.debug("Generated label {} for prefix {}", label, externalIp);
                    floatingIPListener.updateOperationalDS(routerId, interfaceName, label, internalIp, externalIp);

                    //Inform BGP
                    String rd = NatUtil.getVpnRd(dataBroker, vpnName);
                    String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
                    LOG.debug("Nexthop ip for prefix {} is {}", externalIp, nextHopIp);
                    NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, rd, externalIp + "/32", nextHopIp,
                            label, LOG, RouteOrigin.STATIC);

                    List<Instruction> instructions = new ArrayList<>();
                    List<ActionInfo> actionsInfos = new ArrayList<>();
                    actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[] { Integer.toString(NwConstants.PDNAT_TABLE) }));
                    instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos).buildInstruction(0));
                    makeTunnelTableEntry(dpnId, label, instructions);

                    //Install custom FIB routes
                    List<Instruction> customInstructions = new ArrayList<>();
                    customInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.PDNAT_TABLE }).buildInstruction(0));
                    makeLFibTableEntry(dpnId, label, NwConstants.PDNAT_TABLE);
                    CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName).setSourceDpid(dpnId).setInstruction(customInstructions)
                            .setIpAddress(externalIp + "/32").setServiceId(label).setInstruction(customInstructions).build();
                    //Future<RpcResult<java.lang.Void>> createFibEntry(CreateFibEntryInput input);
                    Future<RpcResult<Void>> future = fibService.createFibEntry(input);
                    WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
                    IpAddress externalIpAddress = new IpAddress(new Ipv4Address(externalIp));
                    Port neutronPort = NatUtil.getNeutronPortForFloatingIp(dataBroker, externalIpAddress);
                    if (neutronPort != null && neutronPort.getMacAddress() != null) {
                        vpnManager.setupSubnetMacIntoVpnInstance(vpnName, neutronPort.getMacAddress().getValue(), writeTx, NwConstants.ADD_FLOW);
                    }
                    writeTx.submit();
                    return JdkFutureAdapters.listenInPoolThread(future);
                } else {
                    String errMsg = String.format("Could not retrieve the label for prefix %s in VPN %s, %s", externalIp, vpnName, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            }
        });

        Futures.addCallback(future, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error in generate label or fib install process", error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if(result.isSuccessful()) {
                    LOG.info("Successfully installed custom FIB routes for prefix {}", externalIp);
                } else {
                    LOG.error("Error in rpc call to create custom Fib entries for prefix {} in DPN {}, {}", externalIp, dpnId, result.getErrors());
                }
            }
        });

        // Handle GARP transmission
        final IpAddress extrenalAddress = IpAddressBuilder.getDefaultInstance(externalIp);
        final IpAddress internalAddress = IpAddressBuilder.getDefaultInstance(internalIp);
        Port neutronPortForIp = NatUtil.getNeutronPortForIp(dataBroker,internalAddress,
                NeutronConstants.DEVICE_OWNER_NEUTRON_PORT);
        if (neutronPortForIp == null) {
            LOG.warn("No neutron port was found for external ip {} in router {}", internalIp, routerId);
            NatServiceCounters.port_not_found_for_floating.inc();
            return;
        }
        sendGarpOnInterface(neutronPortForIp.getUuid().getValue(), extrenalAddress, routerId);

    }

    @Override
    public void onRemoveFloatingIp(final BigInteger dpnId, String routerId, Uuid networkId, final String externalIp,
                                   String internalIp, final long label) {
        final String vpnName = getAssociatedVPN(networkId, routerId);
        if (vpnName == null) {
            LOG.info("No VPN associated with ext nw {} to handle remove floating ip configuration {} in router {}",
                    networkId, externalIp, routerId);
            return;
        }
        //Remove floating mac from mymac table
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        IpAddress externalIpAddress = new IpAddress(new Ipv4Address(externalIp));
        Port neutronPort = NatUtil.getNeutronPortForFloatingIp(dataBroker, externalIpAddress);
        if (neutronPort != null && neutronPort.getMacAddress() != null) {
            vpnManager.setupSubnetMacIntoVpnInstance(vpnName, neutronPort.getMacAddress().getValue(), writeTx, NwConstants.DEL_FLOW);
        }
        writeTx.submit();
        //Remove Prefix from BGP
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        NatUtil.removePrefixFromBGP(dataBroker, bgpManager, fibManager, rd, externalIp + "/32", LOG);

        //Remove custom FIB routes
        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalIp + "/32").setServiceId(label).build();
        Future<RpcResult<Void>> future = fibService.removeFibEntry(input);

        ListenableFuture<RpcResult<Void>> labelFuture = Futures.transform(JdkFutureAdapters.listenInPoolThread(future), new AsyncFunction<RpcResult<Void>, RpcResult<Void>>() {

            @Override
            public ListenableFuture<RpcResult<Void>> apply(RpcResult<Void> result) throws Exception {
                //Release label
                if(result.isSuccessful()) {
                    removeTunnelTableEntry(dpnId, label);
                    removeLFibTableEntry(dpnId, label);
                    RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder().setVpnName(vpnName).setIpPrefix(externalIp).build();
                    Future<RpcResult<Void>> labelFuture = vpnService.removeVpnLabel(labelInput);
                    return JdkFutureAdapters.listenInPoolThread(labelFuture);
                } else {
                    String errMsg = String.format("RPC call to remove custom FIB entries on dpn %s for prefix %s Failed - %s", dpnId, externalIp, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            }
        });

        Futures.addCallback(labelFuture, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error in removing the label or custom fib entries", error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if(result.isSuccessful()) {
                    LOG.debug("Successfully removed the label for the prefix {} from VPN {}", externalIp, vpnName);
                } else {
                    LOG.error("Error in removing the label for prefix {} from VPN {}, {}", externalIp, vpnName, result.getErrors());
                }
            }
        });
    }

    void cleanupFibEntries(final BigInteger dpnId, final String vpnName, final String externalIp, final long label ) {
        //Remove Prefix from BGP
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        NatUtil.removePrefixFromBGP(dataBroker, bgpManager, fibManager, rd, externalIp + "/32", LOG);

        //Remove custom FIB routes

        //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
        RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName).setSourceDpid(dpnId).setIpAddress(externalIp + "/32").setServiceId(label).build();
        Future<RpcResult<Void>> future = fibService.removeFibEntry(input);

        ListenableFuture<RpcResult<Void>> labelFuture = Futures.transform(JdkFutureAdapters.listenInPoolThread(future),
            new AsyncFunction<RpcResult<Void>, RpcResult<Void>>() {

            @Override
            public ListenableFuture<RpcResult<Void>> apply(RpcResult<Void> result) throws Exception {
                //Release label
                if(result.isSuccessful()) {
                    removeTunnelTableEntry(dpnId, label);
                    removeLFibTableEntry(dpnId, label);
                    RemoveVpnLabelInput labelInput = new RemoveVpnLabelInputBuilder().setVpnName(vpnName).setIpPrefix(externalIp).build();
                    Future<RpcResult<Void>> labelFuture = vpnService.removeVpnLabel(labelInput);
                    return JdkFutureAdapters.listenInPoolThread(labelFuture);
                } else {
                    String errMsg = String.format("RPC call to remove custom FIB entries on dpn %s for prefix %s Failed - %s", dpnId, externalIp, result.getErrors());
                    LOG.error(errMsg);
                    return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                }
            }
        });

        Futures.addCallback(labelFuture, new FutureCallback<RpcResult<Void>>() {

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error in removing the label or custom fib entries", error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if(result.isSuccessful()) {
                    LOG.debug("Successfully removed the label for the prefix {} from VPN {}", externalIp, vpnName);
                } else {
                    LOG.error("Error in removing the label for prefix {} from VPN {}, {}", externalIp, vpnName, result.getErrors());
                }
            }
        });
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return new StringBuilder(64).append(FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
                .append(id).append(NwConstants.FLOWID_SEPARATOR).append(ipAddress).toString();
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long serviceId) {
        LOG.info("remove terminatingServiceActions called with DpnId = {} and label = {}", dpnId , serviceId);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(serviceId)}));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""),
                5, String.format("%s:%d","TST Flow Entry ",serviceId), 0, 0,
                COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("Terminating service Entry for dpID {} : label : {} removed successfully {}",dpnId, serviceId);
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long serviceId, List<Instruction> customInstructions) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.info("create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}", dpnId , serviceId);

        mkMatches.add(new MatchInfo(MatchFieldType.tunnel_id, new BigInteger[] {BigInteger.valueOf(serviceId)}));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), 5, String.format("%s:%d","TST Flow Entry ",serviceId),
                0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)),mkMatches, customInstructions);

        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
    }

    private void makeLFibTableEntry(BigInteger dpId, long serviceId, long tableId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x8847L }));
        matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(serviceId)}));

        List<Instruction> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
        Instruction writeInstruction = new InstructionInfo(InstructionType.apply_actions, actionsInfos).buildInstruction(0);
        instructions.add(writeInstruction);
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[]{tableId}).buildInstruction(1));

        // Install the flow entry in L3_LFIB_TABLE
        String flowRef = getFlowRef(dpId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
                10, flowRef, 0, 0,
                NwConstants.COOKIE_VM_LFIB_TABLE, matches, instructions);

        mdsalManager.installFlow(dpId, flowEntity);

        LOG.debug("LFIB Entry for dpID {} : label : {} modified successfully {}",dpId, serviceId );
    }

    private void removeLFibTableEntry(BigInteger dpnId, long serviceId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                                  new long[] { 0x8847L }));
        matches.add(new MatchInfo(MatchFieldType.mpls_label, new String[]{Long.toString(serviceId)}));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        LOG.debug("removing LFib entry with flow ref {}", flowRef);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
                                               10, flowRef, 0, 0,
                                               NwConstants.COOKIE_VM_LFIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.debug("LFIB Entry for dpID : {} label : {} removed successfully {}",dpnId, serviceId);
    }

    private String getAssociatedVPN(Uuid networkId, String routerId) {
        String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
        return vpnName != null ? vpnName : routerId;
    }

    private void sendGarpOnInterface(String interfaceName, final IpAddress floatingIpAddress, final String routerId) {
        if (floatingIpAddress.getIpv4Address() == null) {
            LOG.warn("Faild to send GARP for IP. recieved IPv6.");
            NatServiceCounters.garp_sent_ipv6.inc();
            return;
        }

        try {
            Port floatingPort = NatUtil.getNeutronPortForFloatingIp(dataBroker, floatingIpAddress);
            PhysAddress floatingPortMac = new PhysAddress(floatingPort.getMacAddress().getValue());
            List<InterfaceAddress> interfaceAddresses = new ArrayList<>();
            interfaceAddresses.add(new InterfaceAddressBuilder()
                    .setInterface(interfaceName)
                    .setIpAddress(floatingIpAddress)
                    .setMacaddress(floatingPortMac).build());

            SendArpRequestInput sendArpRequestInput = new SendArpRequestInputBuilder().setIpaddress(floatingIpAddress)
                    .setInterfaceAddress(interfaceAddresses).build();
            arpUtilService.sendArpRequest(sendArpRequestInput);
            NatServiceCounters.garp_sent.inc();
        } catch (Exception e) {
            LOG.error("Failed to send GARP request for floating ip {} from interface {}",
                    floatingIpAddress.getIpv4Address().getValue(), interfaceName, e);
            NatServiceCounters.garp_sent_failed.inc();
        }
    }

}
