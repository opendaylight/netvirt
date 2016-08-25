/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.net.InetAddresses;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.utilities.VpnManagerCounters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetPortFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.*;
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

public class FloatingIpGarpHandler extends AsyncDataTreeChangeListenerBase<RouterPorts, FloatingIpGarpHandler>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(FloatingIpGarpHandler.class);
    private final DataBroker dataBroker;
    private final PacketProcessingService packetService;
    private final IElanService elanService;
    private final OdlInterfaceRpcService intfRpc;

    public FloatingIpGarpHandler(final DataBroker dataBroker, final PacketProcessingService packetService,
                                 final IElanService elanService, final OdlInterfaceRpcService interfaceManager) {
        super(RouterPorts.class, FloatingIpGarpHandler.class);
        this.dataBroker = dataBroker;
        this.packetService = packetService;
        this.elanService = elanService;
        this.intfRpc = interfaceManager;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
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
        VpnManagerCounters.garp_update_notification.inc();
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
            LOG.warn("Faild to send GARP for IP. recieved IPv6.");
            VpnManagerCounters.garp_sent_ipv6.inc();
            return;
        }
        Port floatingIpPort = VpnUtil.getNeutronPortForFloatingIp(dataBroker, ip);
        MacAddress floatingIpMac = floatingIpPort.getMacAddress();
        String extNet = VpnUtil.getAssociatedExternalNetwork(dataBroker, dataObjectModificationAfter.getRouterId());
        Collection<String> interfaces = elanService.getExternalElanInterfaces(extNet);
        for (String externalInterface:interfaces) {
            sendGarpOnInterface(ip, floatingIpMac, externalInterface);
        }
    }

    private void sendGarpOnInterface(IpAddress ip, MacAddress floatingIpMac, String externalInterface) {
        try {
            GetPortFromInterfaceInput getPortFromInterfaceInput = new GetPortFromInterfaceInputBuilder()
                    .setIntfName(externalInterface).build();
            Future<RpcResult<GetPortFromInterfaceOutput>> interfacePort = intfRpc
                    .getPortFromInterface(getPortFromInterfaceInput);
            if (interfacePort == null || !interfacePort.get().isSuccessful()) {
                VpnManagerCounters.garp_interface_rpc_failed.inc();
                return;
            }
            BigInteger dpId = interfacePort.get().getResult().getDpid();
            String portId = interfacePort.get().getResult().getPortno().toString();
            NodeConnectorRef ingress = MDSALUtil.getNodeConnRef(dpId, portId);
            byte[] ipBytes = InetAddresses.forString(ip.getIpv4Address().getValue()).getAddress();
            List<ActionInfo> actionList = new ArrayList<ActionInfo>();
            actionList.add(new ActionInfo(ActionType.output, new String[]{portId}));

            byte[] floatingMac = ArpUtils.getMacInBytes(floatingIpMac.getValue());
            TransmitPacketInput arpRequestInput = ArpUtils.createArpRequestInput(dpId, null,
                    floatingMac, VpnConstants.MAC_Broadcast, ipBytes, ipBytes, ingress, actionList);
            packetService.transmitPacket(arpRequestInput);
            VpnManagerCounters.garp_sent.inc();
        } catch (InterruptedException|ExecutionException e) {
            LOG.warn("Faild to send GARP. rpc call getPortFromInterface did not return with a value.");
            VpnManagerCounters.garp_sent_failed.inc();
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
