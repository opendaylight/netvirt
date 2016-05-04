/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.arputil.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.controller.liblldp.Packet;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.NWUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.mdsalutil.packet.ARP;
import org.opendaylight.vpnservice.mdsalutil.packet.Ethernet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Metadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.ArpRequestReceivedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.ArpResponseReceivedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.GetMacInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.GetMacOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.GetMacOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.MacChangedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpResponseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.BaseIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetPortFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetPortFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.SettableFuture;

public class ArpUtilImpl implements OdlArputilService,
        PacketProcessingListener, AutoCloseable {

    private static final String FAILED_TO_GET_SRC_IP_FOR_INTERFACE = "Failed to get src ip for %s";

    private static final String FAILED_TO_GET_SRC_MAC_FOR_INTERFACE = "Failed to get src mac for interface %s iid %s ";

    private static final String FAILED_TO_SEND_ARP_REQ_FOR_INTERFACE = "failed to send arp req for interface ";

    private static final String UNKNOWN_IP_ADDRESS_SUPPLIED = "unknown ip address supplied";

    private static final String NODE_CONNECTOR_NOT_FOUND_ERROR = "Node connector id not found for interface %s";

    private static final String DPN_NOT_FOUND_ERROR = "dpn not found for interface %s ";

    private static final short ARP_REQUEST_OP = (short) 1;

    private static final short ARP_RESPONSE_OP = (short) 2;

    private static final short ETH_TYPE_ARP = 0x0806;

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ArpUtilImpl.class);

    static OdlInterfaceRpcService intfRpc;
    
    ExecutorService threadPool = Executors.newFixedThreadPool(1);

    DataBroker dataBroker;
    PacketProcessingService packetProcessingService;
    NotificationPublishService notificationPublishService;
    NotificationService notificationService;
    IMdsalApiManager mdsalMgr;

    RpcProviderRegistry rpc;
    ListenerRegistration<ArpUtilImpl> listenerRegistration;

    ConcurrentMap<String, String> macsDB = new ConcurrentHashMap<>();
    ConcurrentMap<String, SettableFuture<RpcResult<GetMacOutput>>> getMacFutures = new ConcurrentHashMap<>();

    public ArpUtilImpl(DataBroker db,
            PacketProcessingService packetProcessingService,
            NotificationPublishService notificationPublishService,
            NotificationService notificationService,
            IMdsalApiManager mdsalApiManager,
            RpcProviderRegistry rpc) {

        this.dataBroker = db;
        this.packetProcessingService = packetProcessingService;
        this.notificationPublishService = notificationPublishService;
        this.mdsalMgr = mdsalApiManager;
        this.notificationService = notificationService;
        this.rpc = rpc;
        listenerRegistration = notificationService
                .registerNotificationListener(this);
        LOGGER.info("ArpUtil Manager Initialized ");
    }

    OdlInterfaceRpcService getInterfaceRpcService() {
        if (intfRpc == null ) {
            intfRpc = rpc.getRpcService(OdlInterfaceRpcService.class);
        }
        return intfRpc;
    }

    @Override
    public void close() throws Exception {
        listenerRegistration.close();
        LOGGER.trace("ArpUtil manager Closed");
    }

    String getIpAddressInString(IpAddress ipAddress)
            throws UnknownHostException {
        return InetAddress.getByName(ipAddress.getIpv4Address().getValue())
                .getHostAddress();
    }

    public Future<RpcResult<GetMacOutput>> getMac(GetMacInput input) {

        try {
            final String dstIpAddress = getIpAddressInString(input
                    .getIpaddress());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("getMac rpc invoked for ip " + dstIpAddress);
            }
            if (getMacFutures.get(dstIpAddress) != null) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("get mac already in progress for the ip "
                            + dstIpAddress);
                }
                return getMacFutures.get(dstIpAddress);
            }
            SendArpRequestInputBuilder builder = new SendArpRequestInputBuilder()
                    .setInterfaceAddress(input.getInterfaceAddress())
                    .setIpaddress(input.getIpaddress());
            Future<RpcResult<Void>> arpReqFt = sendArpRequest(builder.build());
            final SettableFuture<RpcResult<GetMacOutput>> ft = SettableFuture
                    .create();

            Futures.addCallback(
                    JdkFutureAdapters.listenInPoolThread(arpReqFt, threadPool),
                    new FutureCallback<RpcResult<Void>>() {
                        @Override
                        public void onFailure(Throwable e) {
                            RpcResultBuilder<GetMacOutput> resultBuilder = RpcResultBuilder
                                    .<GetMacOutput> failed().withError(
                                            ErrorType.APPLICATION,
                                            e.getMessage(), e);
                            ft.set(resultBuilder.build());
                        }

                        @Override
                        public void onSuccess(RpcResult<Void> result) {
                            LOGGER.trace("Successfully sent the arp pkt out for ip "
                                    + dstIpAddress);
                        }
                    });

            getMacFutures.put(dstIpAddress, ft);
            return ft;
        } catch (Exception e) {
            LOGGER.trace("failed to handle getMac request for {} {}",
                    input.getIpaddress(), e);
            RpcResultBuilder<GetMacOutput> resultBuilder = RpcResultBuilder
                    .<GetMacOutput> failed().withError(ErrorType.APPLICATION,
                            e.getMessage(), e);
            return Futures.immediateFuture(resultBuilder.build());
        }
    }

    byte[] getIpAddressBytes(IpAddress ip) throws UnknownHostException {
        return InetAddress.getByName(ip.getIpv4Address().getValue())
                .getAddress();
    }

    @Override
    public Future<RpcResult<Void>> sendArpRequest(
            SendArpRequestInput arpReqInput) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("rpc sendArpRequest invoked for ip "
                    + arpReqInput.getIpaddress());
        }
        BigInteger dpnId;
        long groupId;
        byte payload[];
        String interfaceName = null;
        byte srcIpBytes[];
        byte[] dstIpBytes = null;

        RpcResultBuilder<Void> failureBuilder = RpcResultBuilder
                .<Void> failed();
        RpcResultBuilder<Void> successBuilder = RpcResultBuilder
                .<Void> success();

        try {
            dstIpBytes = getIpAddressBytes(arpReqInput.getIpaddress());
        } catch (Exception e) {
            failureBuilder.withError(ErrorType.APPLICATION,
                    UNKNOWN_IP_ADDRESS_SUPPLIED);
            return Futures.immediateFuture(failureBuilder.build());
        }

        int localErrorCount = 0;
        for (InterfaceAddress interfaceAddress : arpReqInput
                .getInterfaceAddress()) {
            try {
                interfaceName = interfaceAddress.getInterface();
                srcIpBytes = getIpAddressBytes(interfaceAddress.getIpAddress());

                NodeConnectorId id = getNodeConnectorFromInterfaceName(interfaceName);

                GetPortFromInterfaceOutput portResult = getPortFromInterface(interfaceName);
                dpnId = portResult.getDpid();
                Long portid = portResult.getPortno();
                checkArgument(null != dpnId && BigInteger.ZERO != dpnId,
                        DPN_NOT_FOUND_ERROR, interfaceName);

                NodeConnectorRef ref = MDSALUtil.getNodeConnRef(dpnId,
                        portid.toString());
                checkNotNull(ref, NODE_CONNECTOR_NOT_FOUND_ERROR, interfaceName);

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(
                            "sendArpRequest received dpnId {} out interface {}",
                            dpnId, interfaceName);
                }
                byte srcMac[] = MDSALUtil.getMacAddressForNodeConnector(
                        dataBroker,
                        (InstanceIdentifier<NodeConnector>) ref.getValue());
                checkNotNull(srcMac, FAILED_TO_GET_SRC_MAC_FOR_INTERFACE,
                        interfaceName, ref.getValue());
                checkNotNull(srcIpBytes, FAILED_TO_GET_SRC_IP_FOR_INTERFACE,
                        interfaceName);

                payload = ArpPacketUtil.getPayload(ARP_REQUEST_OP, srcMac,
                        srcIpBytes, ArpPacketUtil.EthernetDestination_Broadcast,
                        dstIpBytes);

                sendPacketOut(dpnId, payload, ref);

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("sent arp request for "
                            + arpReqInput.getIpaddress());
                }
            } catch (Throwable e) {
                LOGGER.trace("failed to send arp req for {} on interface {}",
                        arpReqInput.getIpaddress(), interfaceName);

                failureBuilder
                        .withError(ErrorType.APPLICATION,
                                FAILED_TO_SEND_ARP_REQ_FOR_INTERFACE
                                        + interfaceName, e);
                successBuilder
                        .withError(ErrorType.APPLICATION,
                                FAILED_TO_SEND_ARP_REQ_FOR_INTERFACE
                                        + interfaceName, e);
                localErrorCount++;
            }
        }
        if (localErrorCount == arpReqInput.getInterfaceAddress().size()) {
            // All the requests failed
            return Futures.immediateFuture(failureBuilder.build());
        }
        return Futures.immediateFuture(successBuilder.build());
    }

    public Future<RpcResult<Void>> sendPacketOut(BigInteger dpnId,
            byte[] payload, NodeConnectorRef ref) {

        NodeConnectorRef nodeConnectorRef = MDSALUtil.getNodeConnRef(dpnId,
                "0xfffffffd");
        return packetProcessingService
                .transmitPacket(new TransmitPacketInputBuilder()
                        .setPayload(payload)
                        .setNode(
                                new NodeRef(InstanceIdentifier
                                        .builder(Nodes.class)
                                        .child(Node.class,
                                                new NodeKey(new NodeId(
                                                        "openflow:" + dpnId)))
                                        .toInstance()))
                        .setIngress(nodeConnectorRef).setEgress(ref).build());
    }

    @Override
    public Future<RpcResult<Void>> sendArpResponse(SendArpResponseInput input) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("sendArpResponse rpc invoked");
        }
        BigInteger dpnId;
        long groupId;
        byte payload[];

        try {
            String interfaceName = input.getInterface();
            GetPortFromInterfaceOutput portResult = getPortFromInterface(interfaceName);
            dpnId = portResult.getDpid();
            Long portid = portResult.getPortno();
            NodeConnectorRef ref = MDSALUtil.getNodeConnRef(dpnId,
                    portid.toString());
            checkArgument(null != dpnId && BigInteger.ZERO != dpnId,
                    DPN_NOT_FOUND_ERROR, interfaceName);
            checkNotNull(ref, NODE_CONNECTOR_NOT_FOUND_ERROR, interfaceName);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "sendArpRequest received dpnId {} out interface {}",
                        dpnId, interfaceName);
            }

            byte[] srcIpBytes = getIpAddressBytes(input.getSrcIpAddress());
            byte[] dstIpBytes = getIpAddressBytes(input.getIpaddress());
            byte srcMac[] = MDSALUtil.getMacAddressForNodeConnector(dataBroker,
                    (InstanceIdentifier<NodeConnector>) ref.getValue());
            byte[] dstMac = NWUtil.parseMacAddress(input.getMacaddress()
                    .getValue());
            checkNotNull(srcIpBytes, FAILED_TO_GET_SRC_IP_FOR_INTERFACE,
                    interfaceName);
            payload = ArpPacketUtil.getPayload(ARP_RESPONSE_OP, srcMac, srcIpBytes,
                    dstMac, dstIpBytes);

            sendPacketOut(dpnId, payload, ref);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("sent the arp response for "
                        + input.getSrcIpAddress());
            }
        } catch (Throwable e) {
            LOGGER.trace("failed to send arp response for {} {}",
                    input.getSrcIpAddress(), e);
            return RpcResultBuilder.<Void> failed()
                    .withError(ErrorType.APPLICATION, e.getMessage(), e)
                    .buildFuture();
        }
        RpcResultBuilder<Void> rpcResultBuilder = RpcResultBuilder.success();
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

    @Override
    public void onPacketReceived(PacketReceived packetReceived) {
        Class<? extends PacketInReason> pktInReason = packetReceived
                .getPacketInReason();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Packet Received {}", packetReceived);
        }

        if (pktInReason == SendToController.class) {

            try {
                int tableId = packetReceived.getTableId().getValue();

                byte[] data = packetReceived.getPayload();
                Ethernet ethernet = new Ethernet();

                ethernet.deserialize(data, 0, data.length
                        * NetUtils.NumBitsInAByte);
                if (ethernet.getEtherType() != ETH_TYPE_ARP) {
                    return;
                }

                Packet pkt = ethernet.getPayload();
                ARP arp = (ARP) pkt;
                InetAddress srcInetAddr = InetAddress.getByAddress(arp
                        .getSenderProtocolAddress());
                InetAddress dstInetAddr = InetAddress.getByAddress(arp
                        .getTargetProtocolAddress());
                byte[] srcMac = ethernet.getSourceMACAddress();

                NodeConnectorRef ref = packetReceived.getIngress();

                Metadata metadata  = packetReceived.getMatch().getMetadata();
                String interfaceName = getInterfaceName(ref,metadata, dataBroker);
                
                checkAndFireMacChangedNotification(interfaceName, srcInetAddr,
                        srcMac);

                macsDB.put(interfaceName + "-" + srcInetAddr.getHostAddress(),
                        NWUtil.toStringMacAddress(srcMac));

                if (arp.getOpCode() == ARP_REQUEST_OP) {
                    fireArpReqRecvdNotification(interfaceName, srcInetAddr,
                            srcMac, dstInetAddr, tableId);
                } else {
                    fireArpRespRecvdNotification(interfaceName, srcInetAddr,
                            srcMac, tableId);
                }
                if (getMacFutures.get(srcInetAddr.getHostAddress()) != null) {
                    threadPool.submit(new MacResponderTask(arp));
                }

            } catch (Throwable e) {
                LOGGER.trace("Failed to decode packet: {}", e);
            }
        }
    }

    GetPortFromInterfaceOutput getPortFromInterface(String interfaceName) throws Throwable {
        GetPortFromInterfaceInputBuilder getPortFromInterfaceInputBuilder = new GetPortFromInterfaceInputBuilder();
        getPortFromInterfaceInputBuilder.setIntfName(interfaceName);;
        Future<RpcResult<GetPortFromInterfaceOutput>> portFromInterface = intfRpc.getPortFromInterface(getPortFromInterfaceInputBuilder.build());
        GetPortFromInterfaceOutput result = portFromInterface.get().getResult();
        LOGGER.trace("getPortFromInterface rpc result is {} ", result);
        if (result != null) {
            LOGGER.trace("getPortFromInterface rpc result is {} {} ", result.getDpid(), result.getPortno());
        }
        return result;
    }
    
    private String getInterfaceName(NodeConnectorRef ref, Metadata metadata, DataBroker dataBroker2) throws Throwable {
        LOGGER.debug("metadata received is {} ", metadata);
    	
        GetInterfaceFromIfIndexInputBuilder ifIndexInputBuilder = new GetInterfaceFromIfIndexInputBuilder();
        BigInteger lportTag = MetaDataUtil.getLportFromMetadata(metadata.getMetadata());
    	
        ifIndexInputBuilder.setIfIndex(lportTag.intValue());
        GetInterfaceFromIfIndexInput input = ifIndexInputBuilder.build();
        OdlInterfaceRpcService intfRpc = getInterfaceRpcService();

        Future<RpcResult<GetInterfaceFromIfIndexOutput>> interfaceFromIfIndex = intfRpc.getInterfaceFromIfIndex(input);
        GetInterfaceFromIfIndexOutput interfaceFromIfIndexOutput = interfaceFromIfIndex.get().getResult();
        return interfaceFromIfIndexOutput.getInterfaceName();
    }

	class MacResponderTask implements Runnable {
        ARP arp;

        MacResponderTask(ARP arp) {
            this.arp = arp;
        }

        @Override
        public void run() {
            InetAddress srcAddr;
            GetMacOutputBuilder outputBuilder;
            String srcMac;
            SettableFuture<RpcResult<GetMacOutput>> future = null;
            RpcResultBuilder<GetMacOutput> resultBuilder;
            try {
                srcAddr = InetAddress.getByAddress(arp
                        .getSenderProtocolAddress());
                srcMac = NWUtil.toStringMacAddress(arp
                        .getSenderHardwareAddress());
                future = getMacFutures.remove(srcAddr.getHostAddress());
                if (future == null) {
                    LOGGER.trace("There are no pending mac requests.");
                    return;
                }
                outputBuilder = new GetMacOutputBuilder()
                        .setMacaddress(new PhysAddress(srcMac));
                resultBuilder = RpcResultBuilder.success(outputBuilder.build());
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("sent the mac response for ip {}",
                            srcAddr.getHostAddress());
                }
            } catch (Exception e) {
                LOGGER.trace("failed to send mac response {} ", e);
                resultBuilder = RpcResultBuilder.<GetMacOutput> failed()
                        .withError(ErrorType.APPLICATION, e.getMessage(), e);
            }
            future.set(resultBuilder.build());
        }
    }

    private void fireArpRespRecvdNotification(String interfaceName,
            InetAddress inetAddr, byte[] macAddressBytes, int tableId)
            throws InterruptedException {

        IpAddress ip = new IpAddress(inetAddr.getHostAddress().toCharArray());
        String macAddress = NWUtil.toStringMacAddress(macAddressBytes);
        PhysAddress mac = new PhysAddress(macAddress);
        ArpResponseReceivedBuilder builder = new ArpResponseReceivedBuilder();
        builder.setInterface(interfaceName);
        builder.setIpaddress(ip);
        builder.setOfTableId((long) tableId);
        builder.setMacaddress(mac);
        notificationPublishService.putNotification(builder.build());
    }

    private void fireArpReqRecvdNotification(String interfaceName,
            InetAddress srcInetAddr, byte[] srcMac, InetAddress dstInetAddr,
            int tableId) throws InterruptedException {
        String macAddress = NWUtil.toStringMacAddress(srcMac);
        ArpRequestReceivedBuilder builder = new ArpRequestReceivedBuilder();
        builder.setInterface(interfaceName);
        builder.setOfTableId((long) tableId);
        builder.setSrcIpaddress(new IpAddress(srcInetAddr.getHostAddress()
                .toCharArray()));
        builder.setDstIpaddress(new IpAddress(dstInetAddr.getHostAddress()
                .toCharArray()));
        builder.setSrcMac(new PhysAddress(macAddress));
        notificationPublishService.putNotification(builder.build());
    }

    private void checkAndFireMacChangedNotification(String interfaceName,
            InetAddress inetAddr, byte[] macAddressBytes)
            throws InterruptedException {

        IpAddress ip = new IpAddress(inetAddr.getHostAddress().toCharArray());
        String macAddress = NWUtil.toStringMacAddress(macAddressBytes);
        PhysAddress mac = new PhysAddress(macAddress);

        if (!macAddress.equals(macsDB.get(interfaceName + "-"
                + inetAddr.getHostAddress()))) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("mac address changed for " + inetAddr);
            }
            MacChangedBuilder builder = new MacChangedBuilder();
            builder.setInterface(interfaceName);
            builder.setIpaddress(ip);
            builder.setMacaddress(mac);
            notificationPublishService.putNotification(builder.build());
        }
    }

    private InstanceIdentifier<Interface> buildInterfaceId(String interfaceName) {
        InstanceIdentifierBuilder<Interface> idBuilder = InstanceIdentifier
                .builder(Interfaces.class).child(Interface.class,
                        new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        return id;
    }


    private NodeConnectorId getNodeConnectorFromInterfaceName(String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                        .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class,
                                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(interfaceName));
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId = idBuilder.build();

        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateOptional = MDSALUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL,
                ifStateId);

        if (ifStateOptional.isPresent()) {
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState = ifStateOptional.get();
            List<String> lowerLayerIf = ifState.getLowerLayerIf();
            if (!lowerLayerIf.isEmpty()) {
                return new NodeConnectorId(lowerLayerIf.get(0));
            }
        }
        return null;
    }
}
