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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPopMpls;
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
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
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
    private final IElanService elanService;

    static final BigInteger COOKIE_TUNNEL = new BigInteger("9000000", 16);
    static final String FLOWID_PREFIX = "NAT.";

    public VpnFloatingIpHandler(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                final VpnRpcService vpnService,
                                final IBgpManager bgpManager,
                                final FibRpcService fibService,
                                final FloatingIPListener floatingIPListener,
                                final IFibManager fibManager,
                                final OdlArputilService arputilService,
                                final IVpnManager vpnManager,
                                final IElanService elanService
    ) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.vpnService = vpnService;
        this.bgpManager = bgpManager;
        this.fibService = fibService;
        this.floatingIPListener = floatingIPListener;
        this.fibManager = fibManager;
        this.arpUtilService = arputilService;
        this.vpnManager = vpnManager;
        this.elanService = elanService;
    }

    @Override
    public void onAddFloatingIp(final BigInteger dpnId, final String routerId,
                                final Uuid networkId, final String interfaceName,
                                final InternalToExternalPortMap mapping) {
        String internalIp = mapping.getInternalIp();
        String externalIp = mapping.getExternalIp();
        Uuid floatingIpId = mapping.getExternalId();
        String macAddress = null;
        //Get the FIP MAC address for DNAT
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
        if (vpnName == null) {
            LOG.info("NAT Service : No VPN associated with ext nw {} to handle add floating ip configuration {} "
                    + "in router {}", networkId, externalIp, routerId);
            return;
        }
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null || rd.isEmpty()) {
            LOG.error("NAT Service : Unable to get RD for VPN Name {}", vpnName);
            return;
        }
        VrfEntry.EncapType extNwProviderTypeEncap = NatUtil.getExtNwProviderType(dataBroker, rd);
        LOG.info("NAT Service : Got External Network Provider Encapsulation Type : {} for RD : {}",
                extNwProviderTypeEncap,rd);
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        String nextHopIp = NatUtil.getEndpointIpAddressForDPN(dataBroker, dpnId);
        if (nextHopIp == null) {
            LOG.error("NAT Service : Unable to get nextHopIP for DPN ID {}", dpnId);
            return;
        }
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, networkId);
        if (extNwProviderType == null) {
            LOG.error("NAT Service : Unable to retrieve the Provider Type from External Network ID {} ",networkId);
            return;
        }
        if (extNwProviderType.getName().equals("VXLAN")) {
                /*
                1) Install the flow 36->25 (SNAT VM on DPN1 is responding back to FIP VM on DPN2)
                   {SNAT to DNAT traffic on different Hypervisor}

                2) Install the flow 21->25 (FIP VM1 to FIP VM2 Traffic on Same Hypervisor)
                   {DNAT to DNAT on Same Hypervisor}

                3) Install the flow 19->25 (DC-GW is responding back to FIP VM) {DNAT Reverse traffic})

                */
            LOG.info("NAT Service : Handling External Floating IP {} Add with Provider Type {}",
                    externalIp,extNwProviderType.getName());
            long l3Vni = NatUtil.getL3Vni(dataBroker, rd);
            if (l3Vni == NatConstants.INVALID_ID) {
                LOG.error("NAT Service : Unable to retrieve L3VNI value for Floating IP {} with Provider Type {} ",
                        externalIp,extNwProviderType.getName());
                return;
            }
            LOG.debug("NAT Service : Provider Type : VXLAN, RD : {}, External IP : {}, NextHopIP : {}, L3Vni : {} "
                    + "and floatingIpPortMacAddress : {} to install the routes in the FIB table and advertise the "
                    + "same to the BGP manager",externalIp,rd,nextHopIp,l3Vni,floatingIpPortMacAddress);
            //Inform to FIB and BGP
            NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd, macAddress, externalIp + "/32",
                    nextHopIp, extNwProviderTypeEncap, 0, l3Vni, LOG, floatingIpPortMacAddress,
                    writeTx, RouteOrigin.STATIC, dpnId);

            List<Instruction> instructions = new ArrayList<>();
            List<ActionInfo> actionsInfos = new ArrayList<>();
            actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
            instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));
            //Install the Flow from table 36->25 for SNAT to DNAT reverse traffic for Non-FIP VM on DPN1 to
            // FIP VM on DPN2
            makeTunnelTableEntry(dpnId, l3Vni, instructions);
            //Install the flow table25->44 on NAPT Switch If there is no FIP Match on table 25 (PDNAT_TABLE)
            BigInteger naptDpnId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerId);
            List<Instruction> preDnatToSnatInstructions = new ArrayList<>();
            preDnatToSnatInstructions.add(new InstructionGotoTable(NwConstants.INBOUND_NAPT_TABLE).buildInstruction(0));
            makePreDnatToSnatTableEntry(naptDpnId, preDnatToSnatInstructions);

            //Install the flow from table 19->25 (DNAT reverse traffic: If the traffic is Initiated
            // from DC-GW to FIP VM (DNAT forward traffic))
            long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
            NatUtil.makeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, instructions, mdsalManager);

            //Install the flow from table 21->25 (SNAT to DNAT reverse traffic: If the DPN has both SNAT and
            // DNAT configured )
            List<Instruction> customInstructions = new ArrayList<>();
            customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE).buildInstruction(0));
            CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                    .setSourceDpid(dpnId).setInstruction(customInstructions)
                    .setIpAddress(externalIp + "/32").setServiceId(l3Vni)
                    .setInstruction(customInstructions).build();

            Future<RpcResult<Void>> future1 = fibService.createFibEntry(input);
            ListenableFuture<RpcResult<Void>> futureVxlan = JdkFutureAdapters.listenInPoolThread(future1);
            LOG.debug("NAT Service : Add Floating Ip {} , found associated to fixed port {}",
                    externalIp, interfaceName);
            if (floatingIpPortMacAddress != null) {
                vpnManager.setupSubnetMacIntoVpnInstance(vpnName, floatingIpPortMacAddress, dpnId, writeTx,
                        NwConstants.ADD_FLOW);
                vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerId,
                        Collections.singleton(externalIp),
                        floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.ADD_FLOW);
            }

            Futures.addCallback(futureVxlan, new FutureCallback<RpcResult<Void>>() {

                @Override
                public void onFailure(Throwable error) {
                    LOG.error("NAT Service : Error in custom fib routes install process for Floating "
                            + "IP Prefix {} in DPN {}", externalIp, dpnId, error);
                }

                @Override
                public void onSuccess(RpcResult<Void> result) {
                    if (result.isSuccessful()) {
                        LOG.info("NAT Service : Successfully installed custom FIB routes for Floating "
                                + "IP Prefix {} in DPN {}", externalIp, dpnId);
                    } else {
                        LOG.error("NAT Service : Error in rpc call to create custom Fib entries for Floating "
                                + "IP Prefix {} in DPN {}, {}", externalIp, dpnId, result.getErrors());
                    }
                }
            });
        } else {
            //Handling other than VXLAN provider type
            LOG.info("NAT Service : Handling External Floating IP {} Add with Provider Type {}",
                    externalIp,extNwProviderType.getName());
            GenerateVpnLabelInput labelInput = new GenerateVpnLabelInputBuilder().setVpnName(vpnName)
                    .setIpPrefix(externalIp).build();
            Future<RpcResult<GenerateVpnLabelOutput>> labelFuture = vpnService.generateVpnLabel(labelInput);

            ListenableFuture<RpcResult<Void>> future = Futures.transform(JdkFutureAdapters
                    .listenInPoolThread(labelFuture), (AsyncFunction<RpcResult<GenerateVpnLabelOutput>,
                    RpcResult<Void>>) result -> {
                    if (result.isSuccessful()) {
                        GenerateVpnLabelOutput output = result.getResult();
                        final long label = output.getLabel();
                        LOG.debug("NAT Service : Generated label {} for prefix {}", label, externalIp);
                        floatingIPListener.updateOperationalDS(routerId, interfaceName, label, internalIp,
                            externalIp);
                        LOG.debug("NAT Service : Provider Type : {}, RD : {}, External IP : {}, "
                                + "NextHopIP : {}, label : {} and floatingIpPortMacAddress : {} "
                                + "to install the routes in the FIB table and advertise the same to the BGP manager",
                                extNwProviderType.getName(),externalIp,rd,nextHopIp,label,floatingIpPortMacAddress);
                        //Other than VXLAN Provider Type L3VNI is not Applicable. Hence Default value is zero
                        //Inform BGP
                        NatUtil.addPrefixToBGP(dataBroker, bgpManager, fibManager, vpnName, rd, macAddress,
                            externalIp + "/32", nextHopIp, extNwProviderTypeEncap, label, 0, LOG,
                            floatingIpPortMacAddress, writeTx, RouteOrigin.STATIC, dpnId);

                        List<Instruction> instructions = new ArrayList<>();
                        List<ActionInfo> actionsInfos = new ArrayList<>();
                        actionsInfos.add(new ActionNxResubmit(NwConstants.PDNAT_TABLE));
                        instructions.add(new InstructionApplyActions(actionsInfos).buildInstruction(0));
                        //Install the flow table36->25
                        makeTunnelTableEntry(dpnId, label, instructions);

                        //Install custom FIB routes
                        List<Instruction> customInstructions = new ArrayList<>();
                        customInstructions.add(new InstructionGotoTable(NwConstants.PDNAT_TABLE)
                            .buildInstruction(0));
                        //Install the flow table20->25
                        makeLFibTableEntry(dpnId, label, NwConstants.PDNAT_TABLE);
                        CreateFibEntryInput input = new CreateFibEntryInputBuilder().setVpnName(vpnName)
                            .setSourceDpid(dpnId).setInstruction(customInstructions)
                            .setIpAddress(externalIp + "/32").setServiceId(label)
                            .setInstruction(customInstructions).build();
                        //Future<RpcResult<java.lang.Void>> createFibEntry(CreateFibEntryInput input);
                        Future<RpcResult<Void>> future1 = fibService.createFibEntry(input);
                        LOG.debug("NAT Service : Add Floating Ip {} , found associated to fixed port {}", externalIp,
                               interfaceName);
                        if (floatingIpPortMacAddress != null) {
                            vpnManager.setupSubnetMacIntoVpnInstance(vpnName, floatingIpPortMacAddress,
                                dpnId, writeTx,
                                NwConstants.ADD_FLOW);
                            vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerId,
                                Collections.singleton(externalIp),
                                floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.ADD_FLOW);
                        }
                        return JdkFutureAdapters.listenInPoolThread(future1);
                    } else {
                        String errMsg = String.format("Could not retrieve the label for prefix %s in "
                            + "VPN %s, %s", externalIp, vpnName, result.getErrors());
                        LOG.error(errMsg);
                        return Futures.immediateFailedFuture(new RuntimeException(errMsg));
                    }
                });

            Futures.addCallback(future, new FutureCallback<RpcResult<Void>>() {

                @Override
                public void onFailure(Throwable error) {
                    LOG.error("NAT Service : Error in generate label or fib install process", error);
                }

                @Override
                public void onSuccess(RpcResult<Void> result) {
                    if (result.isSuccessful()) {
                        LOG.info("NAT Service : Successfully installed custom FIB routes for prefix {}", externalIp);
                    } else {
                        LOG.error("NAT Service : Error in rpc call to create custom Fib entries for prefix {} in "
                                + "DPN {}, {}", externalIp, dpnId, result.getErrors());
                    }
                }
            });
        }
        if (writeTx != null) {
            writeTx.submit();
            LOG.debug("NAT Service : Successfully submitted writeTx Object for Adding Floating IP {} in "
                    + "VpnFloatingIpHandler.onAddFloatingIp()",externalIp);
        }
        // Handle GARP transmission
        final IpAddress extrenalAddress = IpAddressBuilder.getDefaultInstance(externalIp);
        sendGarpOnInterface(dpnId, networkId, extrenalAddress, floatingIpPortMacAddress);

    }

    @Override
    public void onRemoveFloatingIp(final BigInteger dpnId, String routerId, final Uuid networkId,
                                   InternalToExternalPortMap mapping, final long label) {
        final String vpnName = NatUtil.getAssociatedVPN(dataBroker, networkId, LOG);
        String externalIp = mapping.getExternalIp();
        Uuid floatingIpId = mapping.getExternalId();


        if (vpnName == null) {
            LOG.error("NAT Service : No VPN associated with ext nw {} to handle remove floating ip "
                    + "configuration {} in router {}", networkId, externalIp, routerId);
            return;
        }

        //Remove floating mac from mymac table
        LOG.debug("NAT Service : Removing FloatingIp {}", externalIp);
        String floatingIpPortMacAddress = NatUtil.getFloatingIpPortMacFromFloatingIpId(dataBroker, floatingIpId);
        if (floatingIpPortMacAddress != null) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            vpnManager.setupSubnetMacIntoVpnInstance(vpnName, floatingIpPortMacAddress, dpnId, writeTx,
                NwConstants.DEL_FLOW);
            vpnManager.setupArpResponderFlowsToExternalNetworkIps(routerId, Collections.singletonList(externalIp),
                floatingIpPortMacAddress, dpnId, networkId, writeTx, NwConstants.DEL_FLOW);
            writeTx.submit();
        }
        removeFromFloatingIpPortInfo(floatingIpId);
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, networkId);
        if (extNwProviderType == null) {
            LOG.error("NAT Service : Unable to retrieve the External Network Provider Type for Removing "
                    + "Floating IP = {}",externalIp);
            return;
        }
        if (extNwProviderType.getName().equals("VXLAN")) {
            String rd = NatUtil.getVpnRd(dataBroker, vpnName);
            long l3Vni = NatUtil.getL3Vni(dataBroker, rd);
            LOG.debug("NAT Service : Retrieved values of vpnName = {} and l3vni = {} for Removing Floating IP = {}",
                    vpnName,l3Vni,externalIp);
            cleanupFibEntries(dpnId, vpnName, externalIp, l3Vni,extNwProviderType,floatingIpPortMacAddress);
        } else {
            LOG.debug("NAT Service : Retrieved values of vpnName = {} and label = {} for Removing Floating IP = {}",
                    vpnName,label,externalIp);
            cleanupFibEntries(dpnId, vpnName, externalIp, label,extNwProviderType,floatingIpPortMacAddress);
        }

    }

    @Override
    public void cleanupFibEntries(final BigInteger dpnId, final String vpnName, final String externalIp,
                                  final long label, final ProviderTypes extNwProviderType,
                                  final String floatingIpPortMacAddress) {
            //Remove Prefix from BGP
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        NatUtil.removePrefixFromBGP(dataBroker, bgpManager, fibManager, rd, externalIp + "/32", LOG);

        if (extNwProviderType.getName().equals("VXLAN")) {
            //Remove the flow for table36->25
            removeTunnelTableEntry(dpnId, label);
            long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
            //Remove the flow for table19->25
            NatUtil.removeL3GwMacTableEntry(dpnId, vpnId, floatingIpPortMacAddress, mdsalManager);
            //Remove the flow for table25->44
            removePreDnatToSnatTableEntry(dpnId);
        } else {
            //Remove custom FIB routes

            //Future<RpcResult<java.lang.Void>> removeFibEntry(RemoveFibEntryInput input);
            RemoveFibEntryInput input = new RemoveFibEntryInputBuilder().setVpnName(vpnName)
                     .setSourceDpid(dpnId).setIpAddress(externalIp + "/32").setServiceId(label).build();
            Future<RpcResult<Void>> future = fibService.removeFibEntry(input);

            ListenableFuture<RpcResult<Void>> labelFuture = Futures.transform(
                    JdkFutureAdapters.listenInPoolThread(future), (AsyncFunction<RpcResult<Void>,
                            RpcResult<Void>>) result -> {
                        //Release label
                    if (result.isSuccessful()) {
                        removeTunnelTableEntry(dpnId, label);
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
    }

    private String getFlowRef(BigInteger dpnId, short tableId, long id, String ipAddress) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + id
                + NwConstants.FLOWID_SEPARATOR + ipAddress;
    }

    private String getFlowRef(BigInteger dpnId, short tableId, String uniqueId) {
        return FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId + NwConstants.FLOWID_SEPARATOR + uniqueId;
    }

    private void removeTunnelTableEntry(BigInteger dpnId, long serviceId) {
        LOG.info("NAT Service : Remove terminatingServiceActions called with DpnId = {} and label = {}",
                dpnId, serviceId);
        List<MatchInfo> mkMatches = new ArrayList<>();
        // Matching metadata
        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(serviceId)));
        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""),
            5, String.format("%s:%d", "TST Flow Entry ", serviceId), 0, 0,
            COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, null);
        mdsalManager.removeFlow(dpnId, flowEntity);
        LOG.debug("NAT Service : Terminating service Entry for dpID {} : label : {} removed successfully {}",
                dpnId, serviceId);
    }

    private void makeTunnelTableEntry(BigInteger dpnId, long serviceId, List<Instruction> customInstructions) {
        List<MatchInfo> mkMatches = new ArrayList<>();

        LOG.info("NAT Service : create terminatingServiceAction on DpnId = {} and serviceId = {} and actions = {}",
                dpnId, serviceId,customInstructions);

        mkMatches.add(new MatchTunnelId(BigInteger.valueOf(serviceId)));

        Flow terminatingServiceTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
            getFlowRef(dpnId, NwConstants.INTERNAL_TUNNEL_TABLE, serviceId, ""), 6,
            String.format("%s:%d", "TST Flow Entry ", serviceId),
            0, 0, COOKIE_TUNNEL.add(BigInteger.valueOf(serviceId)), mkMatches, customInstructions);

        mdsalManager.installFlow(dpnId, terminatingServiceTableFlowEntity);
    }

    private void makePreDnatToSnatTableEntry(BigInteger naptDpnId, List<Instruction> preDnatToSnatInstructions) {
        LOG.info("NAT Service : Create PreDNAT (table=25) table miss entry(redirect to table=44 (INBOUND_NAPT_TABLE))"
                + " if there is no floating IP match in table 25 on NAPT DpnId = {} and actions = {}",
                naptDpnId, preDnatToSnatInstructions );
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        String flowRef = getFlowRef(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE,
                matches, preDnatToSnatInstructions);

        mdsalManager.installFlow(naptDpnId, preDnatToSnatTableFlowEntity);
    }

    private void removePreDnatToSnatTableEntry(BigInteger dpnId) {
        LOG.info("NAT Service : Remove PreDNAT (table=25) table miss entry(redirect to table=44 (INBOUND_NAPT_TABLE))"
                        + " on DpnId = {}", dpnId );
        String flowRef = getFlowRef(dpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE,
                null, null);

        mdsalManager.removeFlow(dpnId, preDnatToSnatTableFlowEntity);
        LOG.debug("NAT Service : Removed PreDNAT (table=25) table miss entry(redirect to table=44 (INBOUND_NAPT_TABLE) "
                + "successfully on DpnId = {}", dpnId);
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

        LOG.debug("NAT Service : LFIB Entry for dpID {} : label : {} modified successfully {}", dpId, serviceId);
    }

    private void removeLFibTableEntry(BigInteger dpnId, long serviceId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.MPLS_UNICAST);
        matches.add(new MatchMplsLabel(serviceId));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_LFIB_TABLE, serviceId, "");

        LOG.debug("NAT Service : removing LFib entry with flow ref {}", flowRef);

        Flow flowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_LFIB_TABLE, flowRef,
            10, flowRef, 0, 0,
            NwConstants.COOKIE_VM_LFIB_TABLE, matches, null);

        mdsalManager.removeFlow(dpnId, flowEntity);

        LOG.debug("NAT Service : LFIB Entry for dpID : {} label : {} removed successfully {}", dpnId, serviceId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void sendGarpOnInterface(final BigInteger dpnId, Uuid networkId, final IpAddress floatingIpAddress,
                                     String floatingIpPortMacAddress) {
        if (floatingIpAddress.getIpv4Address() == null) {
            LOG.info("NAT Service : Failed to send GARP for IP. recieved IPv6.");
            NatServiceCounters.garp_failed_ipv6.inc();
            return;
        }

        String interfaceName = elanService.getExternalElanInterface(networkId.getValue(), dpnId);
        if (interfaceName == null) {
            LOG.warn("NAT Service : Failed to send GARP for IP. Failed to retrieve interface name from "
                    + "network {} and dpn id {}.", networkId.getValue(), dpnId);
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
            LOG.error("NAT Service : Failed to send GARP request for floating ip {} from interface {}",
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
                LOG.debug("NAT Service : Deleting floating IP UUID {} to Floating IP neutron port mapping from "
                        + "Floating IP Port Info Config DS", floatingIpId.getValue());
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            }
        } catch (Exception e) {
            LOG.error("NAT Service : Deleting floating IP UUID {} to Floating IP neutron port mapping from Floating "
                + "IP Port Info Config DS failed with exception {}", floatingIpId.getValue(), e);
        }
    }

}
