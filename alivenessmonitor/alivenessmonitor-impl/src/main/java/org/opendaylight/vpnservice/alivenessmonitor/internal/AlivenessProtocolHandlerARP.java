/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

import static org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessMonitorUtil.toStringIpAddress;
import static org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessMonitorConstants.*;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.controller.liblldp.Packet;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.packet.ARP;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.EndpointType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.OdlArputilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpRequestInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.SendArpRequestInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.interfaces.InterfaceAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.arputil.rev151126.interfaces.InterfaceAddressBuilder;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;

public class AlivenessProtocolHandlerARP extends AbstractAlivenessProtocolHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AlivenessProtocolHandlerARP.class);
    private OdlArputilService arpService;

    public AlivenessProtocolHandlerARP(ServiceProvider serviceProvider) {
        super(serviceProvider);
    }

    void setArpManagerService(OdlArputilService arpService) {
        this.arpService = arpService;
    }

    @Override
    public Class<?> getPacketClass() {
        return ARP.class;
    }

    @Override
    public String handlePacketIn(Packet protocolPacket, PacketReceived packetReceived) {
        ARP packet = (ARP) protocolPacket;
        short tableId = packetReceived.getTableId().getValue();
        int arpType = packet.getOpCode();

        if (LOG.isTraceEnabled()) {
            LOG.trace("packet: {}, tableId {}, arpType {}", packetReceived, tableId, arpType);
        }

        if (tableId == AlivenessMonitorConstants.L3_INTERFACE_TABLE) {
            if (arpType == ARP.REPLY) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("packet: {}, monitorKey {}", packetReceived);
                }

                BigInteger metadata = packetReceived.getMatch().getMetadata().getMetadata();
                int portTag = MetaDataUtil.getLportFromMetadata(metadata).intValue();
                String interfaceName = null;
                NodeConnectorId connId = packetReceived.getMatch().getInPort();
//                try {
//                    interfaceName = serviceProvider.getInterfaceManager().getInterfaceNameForInterfaceTag(portTag);
//                } catch(InterfaceNotFoundException e) {
//                    LOG.warn("Error retrieving interface Name for tag {}", portTag, e);
//                }
                if(!Strings.isNullOrEmpty(interfaceName)) {
                    String sourceIp = toStringIpAddress(packet.getSenderProtocolAddress());
                    String targetIp = toStringIpAddress(packet.getTargetProtocolAddress());
                    return getMonitoringKey(interfaceName, targetIp, sourceIp);
                } else {
                    LOG.debug("No interface associated with tag {} to interpret the received ARP Reply", portTag);
                }
            }
        }
        return null;
    }

    @Override
    public void sendPacketOut(MonitoringInfo monitorInfo) {
        if(arpService == null) {
            LOG.debug("ARP Service not available to send the packet");
            return;
        }
        EndpointType source = monitorInfo.getSource().getEndpointType();
        final String sourceInterface = Preconditions.checkNotNull(getInterfaceName(source),
                                       "Source interface is required to send ARP Packet for monitoring");

        final String srcIp = Preconditions.checkNotNull(getIpAddress(source),
                                    "Source Ip address is required to send ARP Packet for monitoring");

        EndpointType target = monitorInfo.getDestination().getEndpointType();
        final String targetIp = Preconditions.checkNotNull(getIpAddress(target),
                                      "Target Ip address is required to send ARP Packet for monitoring");

        if (LOG.isTraceEnabled()) {
            LOG.trace("sendArpRequest interface {}, senderIPAddress {}, targetAddress {}", sourceInterface, srcIp, targetIp);
        }

        List<InterfaceAddress> addresses = Collections.singletonList(
                           new InterfaceAddressBuilder().setInterface(sourceInterface)
                                                        .setIpAddress(IpAddressBuilder.getDefaultInstance(srcIp)).build());
        SendArpRequestInput input = new SendArpRequestInputBuilder().setInterfaceAddress(addresses)
                                                                    .setIpaddress(IpAddressBuilder.getDefaultInstance(targetIp)).build();
        Future<RpcResult<Void>> future = arpService.sendArpRequest(input);

        final String msgFormat = String.format("Send ARP Request on interface %s to destination %s", sourceInterface, targetIp);
        Futures.addCallback(JdkFutureAdapters.listenInPoolThread(future), new FutureCallback<RpcResult<Void>>() {
            @Override
            public void onFailure(Throwable error) {
                LOG.error("Error - {}", msgFormat, error);
            }

            @Override
            public void onSuccess(RpcResult<Void> result) {
                if(!result.isSuccessful()) {
                    LOG.warn("Rpc call to {} failed {}", msgFormat, getErrorText(result.getErrors()));
                } else {
                    LOG.debug("Successful RPC Result - {}", msgFormat);
                }
            }
        });
    }

    private String getErrorText(Collection<RpcError> errors) {
        StringBuilder errorText = new StringBuilder();
        for(RpcError error : errors) {
            errorText.append(",").append(error.getErrorType()).append("-")
                     .append(error.getMessage());
        }
        return errorText.toString();
    }

    @Override
    public String getUniqueMonitoringKey(MonitoringInfo monitorInfo) {
        String interfaceName = getInterfaceName(monitorInfo.getSource().getEndpointType());
        String sourceIp = getIpAddress(monitorInfo.getSource().getEndpointType());
        String targetIp = getIpAddress(monitorInfo.getDestination().getEndpointType());
        return getMonitoringKey(interfaceName, sourceIp, targetIp);
    }

    private String getMonitoringKey(String interfaceName, String sourceIp, String targetIp) {
        return new StringBuilder().append(interfaceName).append(SEPERATOR).append(sourceIp)
                .append(SEPERATOR).append(targetIp).append(SEPERATOR).append(EtherTypes.Arp).toString();
    }

    private String getIpAddress(EndpointType source) {
        String ipAddress = null;
        if( source instanceof IpAddress) {
            ipAddress = ((IpAddress) source).getIpAddress().getIpv4Address().getValue();
        } else if (source instanceof Interface) {
            ipAddress = ((Interface)source).getInterfaceIp().getIpv4Address().getValue();
        }
        return ipAddress;
    }

    private String getInterfaceName(EndpointType endpoint) {
        String interfaceName = null;
        if(endpoint instanceof Interface) {
            interfaceName = ((Interface)endpoint).getInterfaceName();
        }
        return interfaceName;
    }

}
