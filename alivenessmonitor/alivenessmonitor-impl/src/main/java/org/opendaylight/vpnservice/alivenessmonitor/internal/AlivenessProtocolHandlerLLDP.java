/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.liblldp.EtherTypes;
import org.opendaylight.controller.liblldp.LLDP;
import org.opendaylight.controller.liblldp.LLDPTLV;
import org.opendaylight.controller.liblldp.LLDPTLV.TLVType;
import org.opendaylight.controller.liblldp.Packet;
import org.opendaylight.controller.liblldp.PacketException;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.packet.Ethernet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.EndpointType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
//import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetPortFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetPortFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class AlivenessProtocolHandlerLLDP extends AbstractAlivenessProtocolHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AlivenessProtocolHandlerLLDP.class);
    private AtomicInteger packetId = new AtomicInteger(0);

    public AlivenessProtocolHandlerLLDP(ServiceProvider serviceProvider) {
        super(serviceProvider);
    }

    @Override
    public Class<?> getPacketClass() {
        return LLDP.class;
    }

    @Override
    public String handlePacketIn(Packet protocolPacket, PacketReceived packetReceived) {
        String sSourceDpnId = null;
        String sPortNumber = null;
        int nServiceId = -1;
        int packetId = 0;

        String sTmp = null;

        byte lldpTlvTypeCur;

        LLDP lldpPacket = (LLDP)protocolPacket;

        LLDPTLV lldpTlvCur = lldpPacket.getSystemNameId();
        if (lldpTlvCur != null) {
            sSourceDpnId = new String(lldpTlvCur.getValue(), Charset.defaultCharset());
        }

        lldpTlvCur = lldpPacket.getPortId();
        if (lldpTlvCur != null) {
            sPortNumber = new String(lldpTlvCur.getValue(), Charset.defaultCharset());
        }

        for (LLDPTLV lldpTlv : lldpPacket.getOptionalTLVList()) {
            lldpTlvTypeCur = lldpTlv.getType();

            if (lldpTlvTypeCur == LLDPTLV.TLVType.SystemName.getValue()) {
                sSourceDpnId = new String(lldpTlvCur.getValue(), Charset.defaultCharset());
            }
        }

        for (LLDPTLV lldpTlv : lldpPacket.getCustomTlvList()) {
            lldpTlvTypeCur = lldpTlv.getType();

            if (lldpTlvTypeCur == LLDPTLV.TLVType.Custom.getValue()) {
                sTmp = new String(lldpTlv.getValue());
                nServiceId = 0;
            }
        }

        String interfaceName = null;

        //TODO: Check if the below fields are required
        if (!Strings.isNullOrEmpty(sTmp) && sTmp.contains("#")) {
            String[] asTmp = sTmp.split("#");
            interfaceName = asTmp[0];
            packetId = Integer.parseInt(asTmp[1]);
            LOG.debug("Custom LLDP Value on received packet: " + sTmp);
        }

        BigInteger metadata = packetReceived.getMatch().getMetadata().getMetadata();
        int portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
        if(portTag == 0) {
            LOG.trace("Ignoring Packet-in for interface tag 0");
            return null;
        }

        String destInterfaceName = null;
        //TODO: Use latest interface RPC
//        try {
//            destInterfaceName = serviceProvider.getInterfaceManager().getInterfaceNameForInterfaceTag(portTag);
//        } catch(InterfaceNotFoundException e) {
//            LOG.warn("Error retrieving interface Name for tag {}", portTag, e);
//        }

        if(!Strings.isNullOrEmpty(interfaceName)) {
            String monitorKey = new StringBuilder().append(interfaceName).append(EtherTypes.LLDP).toString();
            return monitorKey;
        } else {
            LOG.debug("No interface associated with tag {} to handle received LLDP Packet", portTag);
        }
        return null;
    }

    @Override
    public void sendPacketOut(MonitoringInfo monitorInfo) {
        String sourceInterface;

        EndpointType source = monitorInfo.getSource().getEndpointType();
        if( source instanceof Interface) {
            Interface intf = (Interface)source;
            sourceInterface = intf.getInterfaceName();
        } else {
            LOG.warn("Invalid source endpoint. Could not retrieve source interface to send LLDP Packet");
            return;
        }

        //Get Mac Address for the source interface
        byte[] sourceMac = getMacAddress(sourceInterface);
        if(sourceMac == null) {
            LOG.error("Could not read mac address for the source interface {} from the Inventory. "
                    + "LLDP packet cannot be send.", sourceInterface);
            return;
        }

        OdlInterfaceRpcService interfaceService = serviceProvider.getInterfaceManager();

        long nodeId = -1, portNum = -1;
        try {
            GetDpidFromInterfaceInput dpIdInput = new GetDpidFromInterfaceInputBuilder().setIntfName(sourceInterface).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>> dpIdOutput = interfaceService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if(dpIdResult.isSuccessful()) {
                GetDpidFromInterfaceOutput output = dpIdResult.getResult();
                nodeId = output.getDpid().longValue();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", sourceInterface);
                return;
            }


            GetPortFromInterfaceInput input = new GetPortFromInterfaceInputBuilder().setIntfName(sourceInterface).build();
            Future<RpcResult<GetPortFromInterfaceOutput>> portOutput = interfaceService.getPortFromInterface(input);
            RpcResult<GetPortFromInterfaceOutput> result = portOutput.get();
            if(result.isSuccessful()) {
                GetPortFromInterfaceOutput output = result.getResult();
                portNum = output.getPortno();
            } else {
                LOG.error("Could not retrieve port number for interface {}", sourceInterface);
                return;
            }
        }catch(InterruptedException | ExecutionException e) {
            LOG.error("Failed to retrieve RPC Result ", e);
            return;
        }

        Ethernet ethenetLLDPPacket = makeLLDPPacket(Long.toString(nodeId), portNum, 0, sourceMac, sourceInterface);

        try {
            List<ActionInfo> actions = getInterfaceActions(sourceInterface);
            if(actions.isEmpty()) {
                LOG.error("No interface actions to send packet out over interface {}", sourceInterface);
                return;
            }
            TransmitPacketInput transmitPacketInput = MDSALUtil.getPacketOut(actions,
                    ethenetLLDPPacket.serialize(), nodeId, MDSALUtil.getNodeConnRef(BigInteger.valueOf(nodeId), "0xfffffffd"));
            serviceProvider.getPacketProcessingService().transmitPacket(transmitPacketInput);
        } catch (Exception  e) {
            LOG.error("Error while sending LLDP Packet", e);
        }
    }

    private List<ActionInfo> getInterfaceActions(String interfaceName) throws InterruptedException, ExecutionException {

        OdlInterfaceRpcService interfaceService = serviceProvider.getInterfaceManager();

        long portNum = -1;
        GetPortFromInterfaceInput input = new GetPortFromInterfaceInputBuilder().setIntfName(interfaceName).build();
        Future<RpcResult<GetPortFromInterfaceOutput>> portOutput = interfaceService.getPortFromInterface(input);
        RpcResult<GetPortFromInterfaceOutput> result = portOutput.get();
        if(result.isSuccessful()) {
            GetPortFromInterfaceOutput output = result.getResult();
            portNum = output.getPortno();
        } else {
            LOG.error("Could not retrieve port number for interface {} to construct actions", interfaceName);
            return Collections.emptyList();
        }

        Class<? extends InterfaceType> intfType = null;
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface interfaceInfo =
                                                                                                     getInterfaceFromConfigDS(interfaceName);
        if(interfaceInfo != null) {
            intfType = interfaceInfo.getType();
        } else {
            LOG.error("Could not retrieve port type for interface {} to construct actions", interfaceName);
            return Collections.emptyList();
        }

        List<ActionInfo> actionInfos  = new ArrayList<ActionInfo>();

        if(Tunnel.class.equals(intfType)) {
            actionInfos.add(new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] {
                                         MetaDataUtil.getTunnelIdWithValidVniBitAndVniSet(0x08000000),
                                         MetaDataUtil.METADA_MASK_VALID_TUNNEL_ID_BIT_AND_TUNNEL_ID}));
        }
        actionInfos.add(new ActionInfo(ActionType.output, new String[] { Long.toString(portNum) }));
        return actionInfos;
    }

    private static LLDPTLV buildLLDTLV(LLDPTLV.TLVType tlvType,byte[] abyTLV) {
        return new LLDPTLV().setType(tlvType.getValue()).setLength((short) abyTLV.length).setValue(abyTLV);
    }

    private int getPacketId() {
        int id = packetId.incrementAndGet();
        if(id > 16000) {
            LOG.debug("Resetting the LLDP Packet Id counter");
            packetId.set(0);
        }

        return id;
    }

    public Ethernet makeLLDPPacket(String nodeId,
            long portNum, int serviceId, byte[] srcMac, String sourceInterface) {

        // Create LLDP TTL TLV
        LLDPTLV lldpTlvTTL = buildLLDTLV(LLDPTLV.TLVType.TTL, new byte[] { (byte) 0, (byte) 120 });

        LLDPTLV lldpTlvChassisId = buildLLDTLV(LLDPTLV.TLVType.ChassisID, LLDPTLV.createChassisIDTLVValue(colonize(StringUtils
                .leftPad(Long.toHexString(MDSALUtil.getDpnIdFromNodeName(nodeId).longValue()), 16,
                        "0"))));
        LLDPTLV lldpTlvSystemName = buildLLDTLV(TLVType.SystemName, LLDPTLV.createSystemNameTLVValue(nodeId));

        LLDPTLV lldpTlvPortId = buildLLDTLV(TLVType.PortID, LLDPTLV.createPortIDTLVValue(
                Long.toHexString(portNum)));

        String customValue = sourceInterface + "#" + getPacketId();

        LOG.debug("Sending LLDP packet, custome value " + customValue);

        LLDPTLV lldpTlvCustom = buildLLDTLV(TLVType.Custom, customValue.getBytes());

        List<LLDPTLV> lstLLDPTLVCustom = new ArrayList<>();
        lstLLDPTLVCustom.add(lldpTlvCustom);

        LLDP lldpDiscoveryPacket = new LLDP();
        lldpDiscoveryPacket.setChassisId(lldpTlvChassisId)
                           .setPortId(lldpTlvPortId)
                           .setTtl(lldpTlvTTL)
                           .setSystemNameId(lldpTlvSystemName)
                           .setOptionalTLVList(lstLLDPTLVCustom);

        byte[] destMac = LLDP.LLDPMulticastMac;

        Ethernet ethernetPacket = new Ethernet();
        ethernetPacket.setSourceMACAddress(srcMac)
                      .setDestinationMACAddress(destMac)
                      .setEtherType(EtherTypes.LLDP.shortValue())
                      .setPayload(lldpDiscoveryPacket);

        return ethernetPacket;
    }

    private String colonize(String orig) {
        return orig.replaceAll("(?<=..)(..)", ":$1");
    }

    @Override
    public String getUniqueMonitoringKey(MonitoringInfo monitorInfo) {
        String interfaceName = getInterfaceName(monitorInfo.getSource().getEndpointType());
        return new StringBuilder().append(interfaceName).append(EtherTypes.LLDP).toString();
    }

    private String getInterfaceName(EndpointType endpoint) {
        String interfaceName = null;
        if(endpoint instanceof Interface) {
            interfaceName = ((Interface)endpoint).getInterfaceName();
        }
        return interfaceName;
    }

}
