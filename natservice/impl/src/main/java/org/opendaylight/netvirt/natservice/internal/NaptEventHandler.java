/*
 * Copyright © 2016, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.common.util.Arguments;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionOutput;
import org.opendaylight.genius.mdsalutil.actions.ActionPushVlan;
import org.opendaylight.genius.mdsalutil.actions.ActionSetDestinationIp;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldVlanVid;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIp;
import org.opendaylight.genius.mdsalutil.actions.ActionSetTcpDestinationPort;
import org.opendaylight.genius.mdsalutil.actions.ActionSetTcpSourcePort;
import org.opendaylight.genius.mdsalutil.actions.ActionSetUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.actions.ActionSetUdpSourcePort;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpSourcePort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.genius.mdsalutil.packet.TCP;
import org.opendaylight.genius.mdsalutil.packet.UDP;
import org.opendaylight.infrautils.utils.concurrent.JdkFutures;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.natservice.internal.NaptPacketInHandler.NatPacketProcessingState;
import org.opendaylight.openflowplugin.libraries.liblldp.NetUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NaptEventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NaptEventHandler.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final PacketProcessingService pktService;
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final NaptManager naptManager;
    private final IElanService elanManager;
    private final IdManagerService idManager;
    private final IInterfaceManager interfaceManager;
    private final SalFlowService salFlowServiceRpc;

    @Inject
    public NaptEventHandler(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                            final NaptManager naptManager,
                            final PacketProcessingService pktService,
                            final OdlInterfaceRpcService interfaceManagerRpc,
                            final IInterfaceManager interfaceManager,
                            final IElanService elanManager,
                            final IdManagerService idManager,
                            final SalFlowService salFlowServiceRpc) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.naptManager = naptManager;
        this.pktService = pktService;
        this.interfaceManagerRpc = interfaceManagerRpc;
        this.interfaceManager = interfaceManager;
        this.elanManager = elanManager;
        this.idManager = idManager;
        this.salFlowServiceRpc = salFlowServiceRpc;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void handleEvent(final NAPTEntryEvent naptEntryEvent) {
    /*
            Flow programming logic of the OUTBOUND NAPT TABLE :
            1) Get the internal IP address, port number, router ID from the event.
            2) Use the NAPT service getExternalAddressMapping() to get the External IP and the port.
            3) Build the flow for replacing the Internal IP and port with the External IP and port.
              a) Write the matching criteria.
              b) Match the router ID in the metadata.
              d) Write the VPN ID to the metadata.
              e) Write the other data.
              f) Set the apply actions instruction with the action setfield.
            4) Write the flow to the OUTBOUND NAPT Table and forward to FIB table for routing the traffic.

            Flow programming logic of the INBOUND NAPT TABLE :
            Same as Outbound table logic except that :
            1) Build the flow for replacing the External IP and port with the Internal IP and port.
            2) Match the VPN ID in the metadata.
            3) Write the router ID to the metadata.
            5) Write the flow to the INBOUND NAPT Table and forward to FIB table for routing the traffic.
    */
        try {
            Long routerId = naptEntryEvent.getRouterId();
            String internalIpAddress = naptEntryEvent.getIpAddress();
            int internalPort = naptEntryEvent.getPortNumber();
            String sourceIPPortKey = routerId + NatConstants.COLON_SEPARATOR
                + internalIpAddress + NatConstants.COLON_SEPARATOR + internalPort;
            LOG.trace("handleEvent : Time Elapsed before procesing snat ({}:{}) packet is {} ms,routerId: {},"
                    + "isPktProcessed:{}",
                              internalIpAddress, internalPort,
                              (System.currentTimeMillis() - naptEntryEvent.getObjectCreationTime()), routerId,
                              naptEntryEvent.isPktProcessed());
            //Get the DPN ID
            BigInteger dpnId = NatUtil.getPrimaryNaptfromRouterId(dataBroker, routerId);
            long bgpVpnId = NatConstants.INVALID_ID;
            if (dpnId == null) {
                LOG.warn("handleEvent : dpnId is null. Assuming the router ID {} as the BGP VPN ID and "
                    + "proceeding....", routerId);
                bgpVpnId = routerId;
                LOG.debug("handleEvent : BGP VPN ID {}", bgpVpnId);
                String vpnName = NatUtil.getRouterName(dataBroker, bgpVpnId);
                String routerName = NatUtil.getRouterIdfromVpnInstance(dataBroker, vpnName);
                if (routerName == null) {
                    NaptPacketInHandler.removeIncomingPacketMap(sourceIPPortKey);
                    LOG.error("handleEvent: Unable to find router for VpnName {}. Droping packet for SNAT ({})"
                        + "session", vpnName, sourceIPPortKey);
                    return;
                }
                routerId = NatUtil.getVpnId(dataBroker, routerName);
                LOG.debug("handleEvent : Router ID {}", routerId);
                dpnId = NatUtil.getPrimaryNaptfromRouterId(dataBroker, routerId);
                if (dpnId == null) {
                    NaptPacketInHandler.removeIncomingPacketMap(sourceIPPortKey);
                    LOG.error("handleEvent: Unable to find router for VpnName {}. Droping packet for SNAT ({})"
                        + "session", vpnName, sourceIPPortKey);
                    return;
                }
            }
            if (naptEntryEvent.getOperation() == NAPTEntryEvent.Operation.ADD) {
                LOG.debug("handleEvent : Inside Add operation of NaptEventHandler");

                // Build and install the NAPT translation flows in the Outbound and Inbound NAPT tables
                if (!naptEntryEvent.isPktProcessed()) {

                    // Get the External Gateway MAC Address
                    String extGwMacAddress = NatUtil.getExtGwMacAddFromRouterId(dataBroker, routerId);
                    if (extGwMacAddress != null) {
                        LOG.debug("handleEvent : External Gateway MAC address {} found for External Router ID {}",
                                  extGwMacAddress, routerId);
                    } else {
                        NaptPacketInHandler.removeIncomingPacketMap(sourceIPPortKey);
                        LOG.error("handleEvent: No External Gateway MAC address found for External Router ID {}."
                            + "Droping packet for SNAT ({}) session", routerId, sourceIPPortKey);
                        return;
                    }

                    //Get the external network ID from the ExternalRouter model
                    Uuid networkId = NatUtil.getNetworkIdFromRouterId(dataBroker, routerId);
                    if (networkId == null) {
                        NaptPacketInHandler.removeIncomingPacketMap(sourceIPPortKey);
                        LOG.error("handleEvent: networkId is null. Droping packet for SNAT ({}) session",
                                 sourceIPPortKey);
                        return;
                    }

                    //Get the VPN ID from the ExternalNetworks model
                    Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
                    if (vpnUuid == null) {
                        NaptPacketInHandler.removeIncomingPacketMap(sourceIPPortKey);
                        LOG.error("handleEvent: vpnUuid is null. Droping packet for SNAT ({}) session",
                                 sourceIPPortKey);
                        return;
                    }
                    Long vpnId = NatUtil.getVpnId(dataBroker, vpnUuid.getValue());

                    SessionAddress internalAddress = new SessionAddress(internalIpAddress, internalPort);
                    NAPTEntryEvent.Protocol protocol = naptEntryEvent.getProtocol();

                    //Get the external IP address for the corresponding internal IP address
                    SessionAddress externalAddress =
                            naptManager.getExternalAddressMapping(routerId, internalAddress,
                                    naptEntryEvent.getProtocol());
                    if (externalAddress == null) {
                        NaptPacketInHandler.removeIncomingPacketMap(sourceIPPortKey);
                        LOG.error("handleEvent: externalAddress is null. Droping packet for SNAT ({}) session",
                                  sourceIPPortKey);
                        return;
                    }

                    Long vpnIdFromExternalSubnet = getVpnIdFromExternalSubnet(routerId,
                            externalAddress.getIpAddress());
                    if (vpnIdFromExternalSubnet != NatConstants.INVALID_ID) {
                        vpnId = vpnIdFromExternalSubnet;
                    }

                    // Added External Gateway MAC Address
                    Future<RpcResult<AddFlowOutput>> addFlowResult =
                            buildAndInstallNatFlowsOptionalRpc(dpnId, NwConstants.INBOUND_NAPT_TABLE, vpnId, routerId,
                                    bgpVpnId, externalAddress, internalAddress, protocol, extGwMacAddress, true);
                    final BigInteger finalDpnId = dpnId;
                    final Long finalVpnId = vpnId;
                    final Long finalRouterId = routerId;
                    final long finalBgpVpnId = bgpVpnId;
                    Futures.addCallback(JdkFutureAdapters.listenInPoolThread(addFlowResult),
                                        new FutureCallback<RpcResult<AddFlowOutput>>() {

                                @Override
                                public void onSuccess(@Nullable RpcResult<AddFlowOutput> result) {
                                    LOG.debug("handleEvent : Configured inbound rule for {} to {}",
                                             internalAddress, externalAddress);
                                    Future<RpcResult<AddFlowOutput>> addFlowResult =
                                            buildAndInstallNatFlowsOptionalRpc(finalDpnId,
                                                    NwConstants.OUTBOUND_NAPT_TABLE, finalVpnId, finalRouterId,
                                                    finalBgpVpnId, internalAddress, externalAddress, protocol,
                                                    extGwMacAddress, true);
                                    Futures.addCallback(JdkFutureAdapters.listenInPoolThread(addFlowResult),
                                            new FutureCallback<RpcResult<AddFlowOutput>>() {

                                            @Override
                                            public void onSuccess(@Nullable RpcResult<AddFlowOutput> result) {
                                                LOG.debug("handleEvent : Configured outbound rule, sending packet out"
                                                        + "from {} to {}", internalAddress, externalAddress);
                                                prepareAndSendPacketOut(naptEntryEvent, finalRouterId);
                                            }

                                            @Override
                                            public void onFailure(@Nonnull Throwable throwable) {
                                                LOG.error("handleEvent : Error configuring outbound "
                                                        + "SNAT flows using RPC for SNAT connection from {} to {}",
                                                                  internalAddress, externalAddress);
                                            }
                                        }, MoreExecutors.directExecutor());
                                }

                                @Override
                                public void onFailure(@Nonnull Throwable throwable) {
                                    LOG.error("handleEvent : Error configuring inbound SNAT flows "
                                            + "using RPC for SNAT connection from {} to {}",
                                            internalAddress, externalAddress);
                                }
                            }, MoreExecutors.directExecutor());

                    NatPacketProcessingState state = naptEntryEvent.getState();
                    if (state != null) {
                        state.setFlowInstalledTime(System.currentTimeMillis());
                    }
                } else {
                    prepareAndSendPacketOut(naptEntryEvent, routerId);
                }
                LOG.trace("handleEvent : Time elapsed after Processsing snat ({}:{}) packet: {}ms,isPktProcessed:{} ",
                        naptEntryEvent.getIpAddress(), naptEntryEvent.getPortNumber(),
                        System.currentTimeMillis() - naptEntryEvent.getObjectCreationTime(),
                        naptEntryEvent.isPktProcessed());
            } else {
                LOG.debug("handleEvent : Inside delete Operation of NaptEventHandler");
                removeNatFlows(dpnId, NwConstants.INBOUND_NAPT_TABLE, routerId, naptEntryEvent.getIpAddress(),
                    naptEntryEvent.getPortNumber());
                LOG.info("handleEvent : exited for removeEvent for IP {}, port {}, routerID : {}",
                        naptEntryEvent.getIpAddress(), naptEntryEvent.getPortNumber(), routerId);
            }
        } catch (Exception e) {
            LOG.error("handleEvent :Exception in NaptEventHandler.handleEvent() payload {}", naptEntryEvent, e);
        }
    }

    private void prepareAndSendPacketOut(NAPTEntryEvent naptEntryEvent, Long routerId) {
        //Send Packetout - tcp or udp packets which got punted to controller.
        BigInteger metadata = naptEntryEvent.getPacketReceived().getMatch().getMetadata().getMetadata();
        byte[] inPayload = naptEntryEvent.getPacketReceived().getPayload();
        Ethernet ethPkt = new Ethernet();
        if (inPayload != null) {
            try {
                ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NUM_BITS_IN_A_BYTE);
            } catch (PacketException e) {
                LOG.error("prepareAndSendPacketOut : Failed to decode Packet", e);
                return;
            }
        }

        long portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
        LOG.debug("prepareAndSendPacketOut : portTag from incoming packet is {}", portTag);
        List<ActionInfo> actionInfos = new ArrayList<>();
        String interfaceName = getInterfaceNameFromTag(portTag);
        BigInteger dpnID = null;
        int portNum = -1;
        if (interfaceName != null) {
            LOG.debug("prepareAndSendPacketOut : interfaceName fetched from portTag is {}", interfaceName);
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                    .interfaces.Interface iface = null;
            int vlanId = 0;
            iface = interfaceManager.getInterfaceInfoFromConfigDataStore(interfaceName);
            if (iface == null) {
                LOG.error("prepareAndSendPacketOut : Unable to read interface {} from config DataStore", interfaceName);
                return;
            }

            IfL2vlan ifL2vlan = iface.augmentation(IfL2vlan.class);
            if (ifL2vlan != null && ifL2vlan.getVlanId() != null) {
                vlanId = ifL2vlan.getVlanId().getValue() == null ? 0 : ifL2vlan.getVlanId().getValue();
            }
            InterfaceInfo infInfo = interfaceManager.getInterfaceInfoFromOperationalDataStore(interfaceName);
            if (infInfo == null) {
                LOG.error("prepareAndSendPacketOut : error in getting interfaceInfo from Operation DS");
                return;
            }
            dpnID = infInfo.getDpId();
            portNum = infInfo.getPortNo();
            if (ethPkt.getEtherType() != (short) NwConstants.ETHTYPE_802_1Q) {
                // VLAN Access port
                LOG.debug("prepareAndSendPacketOut : vlanId is {}", vlanId);
                if (vlanId != 0) {
                    // Push vlan
                    actionInfos.add(new ActionPushVlan(0));
                    actionInfos.add(new ActionSetFieldVlanVid(1, vlanId));
                } else {
                    LOG.debug("prepareAndSendPacketOut : No vlanId {}, may be untagged", vlanId);
                }
            } else {
                // VLAN Trunk Port
                LOG.debug("prepareAndSendPacketOut : This is VLAN Trunk port case - need not do VLAN tagging again");
            }
        } else {
            // This case will be hit for packets send from non-napt switch.
            LOG.info("prepareAndSendPacketOut : interfaceName is not available.Retrieve from packet received");
            NodeConnectorId nodeId = naptEntryEvent.getPacketReceived().getMatch().getInPort();
            portNum = Integer.parseInt(nodeId.getValue());
            LOG.debug("prepareAndSendPacketOut : in_port portNum : {}", portNum);
            //List<PathArgument> dpnNodes = naptEntryEvent.getPacketReceived().getIngress().getValue().getPath();
            Iterable<PathArgument> outArgs = naptEntryEvent.getPacketReceived().getIngress().getValue()
                    .getPathArguments();
            PathArgument pathArgument = Iterables.get(outArgs, 2);
            LOG.debug("prepareAndSendPacketOut : pathArgument : {}", pathArgument);
            InstanceIdentifier.IdentifiableItem<?, ?> item = Arguments.checkInstanceOf(pathArgument,
                    InstanceIdentifier.IdentifiableItem.class);
            NodeConnectorKey key = Arguments.checkInstanceOf(item.getKey(), NodeConnectorKey.class);
            LOG.info("prepareAndSendPacketOut : NodeConnectorKey key : {}", key.getId().getValue());
            String dpnKey = key.getId().getValue();
            if (dpnKey.contains(NatConstants.COLON_SEPARATOR)) {
                dpnID = new BigInteger(dpnKey.split(NatConstants.COLON_SEPARATOR)[1]);
            }
        }
        byte[] pktOut = buildNaptPacketOut(ethPkt);

        if (pktOut != null) {
            String routerName = NatUtil.getRouterName(dataBroker, routerId);
            long tunId = NatUtil.getTunnelIdForNonNaptToNaptFlow(dataBroker, elanManager, idManager, routerId,
                    routerName);
            sendNaptPacketOut(pktOut, dpnID, portNum, actionInfos, tunId);
        } else {
            LOG.warn("prepareAndSendPacketOut : Unable to send Packet Out");
        }
    }

    public void buildAndInstallNatFlows(BigInteger dpnId, short tableId, long vpnId, long routerId,
                                               long bgpVpnId, SessionAddress actualSourceAddress,
                                               SessionAddress translatedSourceAddress,
                                               NAPTEntryEvent.Protocol protocol, String extGwMacAddress) {
        buildAndInstallNatFlowsOptionalRpc(dpnId, tableId, vpnId, routerId, bgpVpnId, actualSourceAddress,
                translatedSourceAddress, protocol, extGwMacAddress, false);
    }

    private Future<RpcResult<AddFlowOutput>> buildAndInstallNatFlowsOptionalRpc(
            BigInteger dpnId, short tableId, long vpnId, long routerId, long bgpVpnId,
            SessionAddress actualSourceAddress, SessionAddress translatedSourceAddress,
            NAPTEntryEvent.Protocol protocol, String extGwMacAddress,
            boolean sendRpc) {
        LOG.debug("buildAndInstallNatFlowsOptionalRpc : Build and install table={} flow on dpnId {} and routerId {}",
                tableId, dpnId, routerId);
        //Build the flow for replacing the actual IP and port with the translated IP and port.
        int idleTimeout = 0;
        if (tableId == NwConstants.OUTBOUND_NAPT_TABLE) {
            idleTimeout = NatConstants.DEFAULT_NAPT_IDLE_TIMEOUT;
        }
        long intranetVpnId;
        if (bgpVpnId != NatConstants.INVALID_ID) {
            intranetVpnId = bgpVpnId;
        } else {
            intranetVpnId = routerId;
        }
        LOG.debug("buildAndInstallNatFlowsOptionalRpc : Intranet VPN ID {} Router ID {}", intranetVpnId, routerId);
        String translatedIp = translatedSourceAddress.getIpAddress();
        int translatedPort = translatedSourceAddress.getPortNumber();
        String actualIp = actualSourceAddress.getIpAddress();
        int actualPort = actualSourceAddress.getPortNumber();
        String switchFlowRef =
            NatUtil.getNaptFlowRef(dpnId, tableId, String.valueOf(routerId), actualIp, actualPort);

        FlowEntity snatFlowEntity = new FlowEntityBuilder()
            .setDpnId(dpnId)
            .setTableId(tableId)
            .setFlowId(switchFlowRef)
            .setPriority(NatConstants.DEFAULT_NAPT_FLOW_PRIORITY)
            .setFlowName(NatConstants.NAPT_FLOW_NAME)
            .setIdleTimeOut(idleTimeout)
            .setHardTimeOut(0)
            .setCookie(NatUtil.getCookieNaptFlow(routerId))
            .setMatchInfoList(buildAndGetMatchInfo(actualIp, actualPort, tableId, protocol, intranetVpnId))
            .setInstructionInfoList(buildAndGetSetActionInstructionInfo(translatedIp, translatedPort,
                                            intranetVpnId, vpnId, tableId, protocol, extGwMacAddress))
            .setSendFlowRemFlag(true)
            .build();

        // Install flows using RPC to prevent race with future packet-out that depends on this flow
        Future<RpcResult<AddFlowOutput>> addFlowResult = null;
        if (sendRpc) {
            Flow flow = snatFlowEntity.getFlowBuilder().build();
            NodeRef nodeRef = getNodeRef(dpnId);
            FlowRef flowRef = getFlowRef(dpnId, flow);
            AddFlowInput addFlowInput = new AddFlowInputBuilder(flow).setFlowRef(flowRef).setNode(nodeRef).build();
            long startTime = System.currentTimeMillis();
            addFlowResult = salFlowServiceRpc.addFlow(addFlowInput);
            LOG.debug("buildAndInstallNatFlowsOptionalRpc : Time elapsed for salFlowServiceRpc table {}: {}ms ",
                    tableId, System.currentTimeMillis() - startTime);
         // Keep flow installation through MDSAL as well to be able to handle switch failures
            startTime = System.currentTimeMillis();
            mdsalManager.installFlow(snatFlowEntity);
            LOG.trace("buildAndInstallNatFlowsOptionalRpc : Time Elapsed while installing table-{} "
                    + "flow on DPN:{} for snat packet({},{}): {}ms", tableId, dpnId,
                    actualSourceAddress.getIpAddress(),actualSourceAddress.getPortNumber(),
                    System.currentTimeMillis() - startTime);
        } else {
            long startTime = System.currentTimeMillis();
            mdsalManager.syncInstallFlow(snatFlowEntity);
            LOG.trace("buildAndInstallNatFlowsOptionalRpc : Time Elapsed while installing table-{} "
                    + "flow on DPN:{} for snat packet({},{}): {}ms", tableId, dpnId,
                    actualSourceAddress.getIpAddress(),actualSourceAddress.getPortNumber(),
                    System.currentTimeMillis() - startTime);
        }
        LOG.trace("buildAndInstallNatFlowsOptionalRpc : Exited");

        return addFlowResult;
    }

    private static Node buildInventoryDpnNode(BigInteger dpnId) {
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).withKey(new NodeKey(nodeId)).build();
        return nodeDpn;
    }

    private static NodeRef getNodeRef(BigInteger dpnId) {
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        return new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).build());
    }

    public static FlowRef getFlowRef(BigInteger dpId, Flow flow) {
        FlowKey flowKey = new FlowKey(new FlowId(flow.getId()));
        Node nodeDpn = buildInventoryDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId =
                InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.key()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId()))
                .child(Flow.class, flowKey)
                .build();
        return new FlowRef(flowInstanceId);
    }

    private static List<MatchInfo> buildAndGetMatchInfo(String ip, int port, short tableId,
                                                        NAPTEntryEvent.Protocol protocol, long segmentId) {
        MatchInfo ipMatchInfo = null;
        MatchInfo portMatchInfo = null;
        MatchInfo protocolMatchInfo = null;
        InetAddress ipAddress = null;
        String ipAddressAsString = null;
        try {
            ipAddress = InetAddress.getByName(ip);
            ipAddressAsString = ipAddress.getHostAddress();

        } catch (UnknownHostException e) {
            LOG.error("buildAndGetMatchInfo : UnknowHostException in buildAndGetMatchInfo."
                    + "Failed  to build NAPT Flow for ip {}", ip, e);
            return null;
        }

        MatchInfo metaDataMatchInfo = null;
        if (tableId == NwConstants.OUTBOUND_NAPT_TABLE) {
            ipMatchInfo = new MatchIpv4Source(ipAddressAsString, "32");
            if (protocol == NAPTEntryEvent.Protocol.TCP) {
                protocolMatchInfo = MatchIpProtocol.TCP;
                portMatchInfo = new MatchTcpSourcePort(port);
            } else if (protocol == NAPTEntryEvent.Protocol.UDP) {
                protocolMatchInfo = MatchIpProtocol.UDP;
                portMatchInfo = new MatchUdpSourcePort(port);
            }
            metaDataMatchInfo =
                    new MatchMetadata(MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID);
        } else {
            ipMatchInfo = new MatchIpv4Destination(ipAddressAsString, "32");
            if (protocol == NAPTEntryEvent.Protocol.TCP) {
                protocolMatchInfo = MatchIpProtocol.TCP;
                portMatchInfo = new MatchTcpDestinationPort(port);
            } else if (protocol == NAPTEntryEvent.Protocol.UDP) {
                protocolMatchInfo = MatchIpProtocol.UDP;
                portMatchInfo = new MatchUdpDestinationPort(port);
            }
            //metaDataMatchInfo = new MatchMetadata(BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID);
        }
        ArrayList<MatchInfo> matchInfo = new ArrayList<>();
        matchInfo.add(MatchEthernetType.IPV4);
        matchInfo.add(ipMatchInfo);
        matchInfo.add(protocolMatchInfo);
        matchInfo.add(portMatchInfo);
        if (tableId == NwConstants.OUTBOUND_NAPT_TABLE) {
            matchInfo.add(metaDataMatchInfo);
        }
        return matchInfo;
    }

    private static List<InstructionInfo> buildAndGetSetActionInstructionInfo(String ipAddress, int port,
                                                                             long segmentId, long vpnId,
                                                                             short tableId,
                                                                             NAPTEntryEvent.Protocol protocol,
                                                                             String extGwMacAddress) {
        ActionInfo ipActionInfo = null;
        ActionInfo macActionInfo = null;
        ActionInfo portActionInfo = null;
        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        switch (tableId) {
            case NwConstants.OUTBOUND_NAPT_TABLE:
                ipActionInfo = new ActionSetSourceIp(ipAddress);
                // Added External Gateway MAC Address
                macActionInfo = new ActionSetFieldEthernetSource(new MacAddress(extGwMacAddress));
                if (protocol == NAPTEntryEvent.Protocol.TCP) {
                    portActionInfo = new ActionSetTcpSourcePort(port);
                } else if (protocol == NAPTEntryEvent.Protocol.UDP) {
                    portActionInfo = new ActionSetUdpSourcePort(port);
                }
                // reset the split-horizon bit to allow traffic from tunnel to be sent back to the provider port
                instructionInfo.add(new InstructionWriteMetadata(MetaDataUtil.getVpnIdMetadata(vpnId),
                    MetaDataUtil.METADATA_MASK_VRFID.or(MetaDataUtil.METADATA_MASK_SH_FLAG)));
                break;

            case NwConstants.INBOUND_NAPT_TABLE:
                ipActionInfo = new ActionSetDestinationIp(ipAddress);
                if (protocol == NAPTEntryEvent.Protocol.TCP) {
                    portActionInfo = new ActionSetTcpDestinationPort(port);
                } else if (protocol == NAPTEntryEvent.Protocol.UDP) {
                    portActionInfo = new ActionSetUdpDestinationPort(port);
                }
                instructionInfo.add(new InstructionWriteMetadata(
                        MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID));
                break;

            default:
                LOG.error("buildAndGetSetActionInstructionInfo : Neither OUTBOUND_NAPT_TABLE nor "
                        + "INBOUND_NAPT_TABLE matches with input table id {}", tableId);
                return null;
        }

        listActionInfo.add(ipActionInfo);
        listActionInfo.add(portActionInfo);
        if (macActionInfo != null) {
            listActionInfo.add(macActionInfo);
            LOG.debug("buildAndGetSetActionInstructionInfo : External GW MAC Address {} is found  ", macActionInfo);
        }
        instructionInfo.add(new InstructionApplyActions(listActionInfo));
        instructionInfo.add(new InstructionGotoTable(NwConstants.NAPT_PFIB_TABLE));

        return instructionInfo;
    }

    void removeNatFlows(BigInteger dpnId, short tableId ,long segmentId, String ip, int port) {
        if (dpnId == null || dpnId.equals(BigInteger.ZERO)) {
            LOG.error("removeNatFlows : DPN ID {} is invalid" , dpnId);
            return;
        }
        LOG.debug("removeNatFlows : Remove NAPT flows for dpnId {}, segmentId {}, ip {} and port {} ",
            dpnId, segmentId, ip, port);

        //Build the flow with the port IP and port as the match info.
        String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, tableId, String.valueOf(segmentId), ip, port);
        FlowEntity snatFlowEntity = NatUtil.buildFlowEntity(dpnId, tableId, switchFlowRef);
        LOG.debug("removeNatFlows : Remove the flow in the table {} for the switch with the DPN ID {}",
            NwConstants.INBOUND_NAPT_TABLE, dpnId);
        long startTime = System.currentTimeMillis();
        mdsalManager.removeFlow(snatFlowEntity);
        LOG.trace("removeNatFlows : Time Elapsed for removing table-{} flow from switch with DPN ID:{} "
                + "for SNAT ({}:{}) session:{}ms", tableId, dpnId, ip, port, System.currentTimeMillis() - startTime);
    }

    @SuppressFBWarnings("PZLA_PREFER_ZERO_LENGTH_ARRAYS")
    protected byte[] buildNaptPacketOut(Ethernet etherPkt) {
        LOG.debug("removeNatFlows : About to build Napt Packet Out");
        if (etherPkt.getPayload() instanceof IPv4) {
            byte[] rawPkt;
            IPv4 ipPkt = (IPv4) etherPkt.getPayload();
            if (ipPkt.getPayload() instanceof TCP || ipPkt.getPayload() instanceof UDP) {
                try {
                    rawPkt = etherPkt.serialize();
                    return rawPkt;
                } catch (PacketException e2) {
                    LOG.error("failed to build NAPT Packet out ", e2);
                    return null;
                }
            } else {
                LOG.error("removeNatFlows : Unable to build NaptPacketOut since its neither TCP nor UDP");
                return null;
            }
        }
        LOG.error("removeNatFlows : Unable to build NaptPacketOut since its not IPv4 packet");
        return null;
    }

    private void sendNaptPacketOut(byte[] pktOut, BigInteger dpnID, int portNum,
            List<ActionInfo> actionInfos, Long tunId) {
        LOG.trace("sendNaptPacketOut: Sending packet out DpId {}, interface {}", dpnID, portNum);
        // set inPort, and action as OFPP_TABLE so that it starts from table 0 (lowest table as per spec)
        actionInfos.add(new ActionSetFieldTunnelId(2, BigInteger.valueOf(tunId)));
        actionInfos.add(new ActionOutput(3, new Uri("0xfffffff9")));
        NodeConnectorRef inPort = MDSALUtil.getNodeConnRef(dpnID, String.valueOf(portNum));
        LOG.debug("sendNaptPacketOut : inPort for packetout is being set to {}", portNum);
        TransmitPacketInput output = MDSALUtil.getPacketOut(actionInfos, pktOut, dpnID.longValue(), inPort);
        LOG.debug("sendNaptPacketOut : Transmitting packet: {}, inPort {}", output, inPort);

        JdkFutures.addErrorLogging(pktService.transmitPacket(output), LOG, "Transmit packet");
    }

    private String getInterfaceNameFromTag(long portTag) {
        String interfaceName = null;
        GetInterfaceFromIfIndexInput input =
            new GetInterfaceFromIfIndexInputBuilder().setIfIndex((int) portTag).build();
        Future<RpcResult<GetInterfaceFromIfIndexOutput>> futureOutput =
            interfaceManagerRpc.getInterfaceFromIfIndex(input);
        try {
            GetInterfaceFromIfIndexOutput output = futureOutput.get().getResult();
            if (output != null) {
                interfaceName = output.getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getInterfaceNameFromTag : Error while retrieving the interfaceName from tag using "
                + "getInterfaceFromIfIndex RPC");
        }
        LOG.trace("getInterfaceNameFromTag : Returning interfaceName {} for tag {} form getInterfaceNameFromTag",
            interfaceName, portTag);
        return interfaceName;
    }

    private long getVpnIdFromExternalSubnet(Long routerId, String externalIpAddress) {
        String routerName = NatUtil.getRouterName(dataBroker, routerId);
        if (routerName != null) {
            Routers extRouter = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
            if (extRouter != null) {
                return NatUtil.getExternalSubnetVpnIdForRouterExternalIp(dataBroker, externalIpAddress, extRouter);
            }
        }

        return NatConstants.INVALID_ID;
    }
}
