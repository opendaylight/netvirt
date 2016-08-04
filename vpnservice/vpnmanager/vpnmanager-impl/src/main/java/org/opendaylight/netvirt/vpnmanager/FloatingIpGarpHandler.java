/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

public class FloatingIpGarpHandler extends AsyncDataTreeChangeListenerBase<RouterPorts, FloatingIpGarpHandler>
        implements AutoCloseable {

    private DataBroker broker;
    private PacketProcessingService packetService;
    OdlInterfaceRpcService intfRpc;
    private static final Logger s_logger = LoggerFactory.getLogger(FloatingIpGarpHandler.class);

    public FloatingIpGarpHandler(DataBroker broker) {
        super(RouterPorts.class, FloatingIpGarpHandler.class);
        this.broker = broker;
    }

    public void setPacketService(PacketProcessingService packetService) {
        this.packetService = packetService;
    }

    @Override
    protected InstanceIdentifier<RouterPorts> getWildCardPath() {
        return InstanceIdentifier.create(FloatingIpInfo.class).child(RouterPorts.class);
    }

    @Override
    protected void remove(InstanceIdentifier<RouterPorts> key, RouterPorts dataObjectModification) {
    }

    @Override
    protected void update(InstanceIdentifier<RouterPorts> key, RouterPorts dataObjectModificationBefore,
            RouterPorts dataObjectModificationAfter) {
        sendGarpForFloatingIps(dataObjectModificationAfter);
    }

    private void sendGarpForFloatingIps(RouterPorts dataObjectModificationAfter) {
        for (Ports port : dataObjectModificationAfter.getPorts()) {
            for (IpMapping ipMapping : port.getIpMapping()) {
                IpAddress ip = IpAddressBuilder.getDefaultInstance(ipMapping.getExternalIp());
                sendGarpForIp(dataObjectModificationAfter, ip);
            }
        }
    }

    private void sendGarpForIp(RouterPorts dataObjectModificationAfter, IpAddress ip) {
        if (ip.getIpv4Address() == null) {
            s_logger.warn("Faild to send GARP for IP. recieved IPv6.");
            return;
        }
        Port floatingIpPort = VpnUtil.getNeutronPortForFloatingIp(broker, ip);
        MacAddress floatingIpMac = floatingIpPort.getMacAddress();
        String extNet = VpnUtil.getAssociatedExternalNetwork(broker, dataObjectModificationAfter.getRouterId());
        Collection<String> interfaces = VpnmanagerServiceAccessor. getElanProvider().getExternalElanInterfaces(extNet);
        for (String externalInterface:interfaces) {
            sendGarpOnInterface(ip, floatingIpMac, externalInterface);
            
        }
    }

    private void sendGarpOnInterface(IpAddress ip, MacAddress floatingIpMac, String externalInterface) {
        GetPortFromInterfaceInput getPortFromInterfaceInput = new GetPortFromInterfaceInputBuilder().setIntfName(externalInterface).build();
        Future<RpcResult<GetPortFromInterfaceOutput>> interfacePort = intfRpc.getPortFromInterface(getPortFromInterfaceInput);
        try {
            BigInteger dpId = interfacePort.get().getResult().getDpid();
            String portName = interfacePort.get().getResult().getPortname();
            NodeConnectorRef ingress = MDSALUtil.getNodeConnRef(dpId, portName);
            byte[] ipBytes = InetAddresses.forString(ip.getIpv4Address().getValue()).getAddress();
            TransmitPacketInput arpRequestInput = ArpUtils.createArpRequestInput(dpId, ArpUtils.getMacInBytes(floatingIpMac.getValue()), ipBytes, ipBytes, ingress);
            packetService.transmitPacket(arpRequestInput);
        } catch (InterruptedException e) {
            s_logger.warn("Faild to send GARP. rpc call getPortFromInterface did not return with a value.");
        } catch (ExecutionException e) {
            s_logger.warn("Faild to send GARP. rpc call getPortFromInterface did not return with a value.");
        }
    }


    @Override
    protected void add(InstanceIdentifier<RouterPorts> key, RouterPorts dataObjectModification) {
        sendGarpForFloatingIps(dataObjectModification);
    }

    @Override
    protected FloatingIpGarpHandler getDataTreeChangeListener() {
        return this;
    }
}
