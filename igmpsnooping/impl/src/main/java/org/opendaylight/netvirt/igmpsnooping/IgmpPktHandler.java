/*
 * Copyright © 2018 Red Hat and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.igmpsnooping;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.igmpsnooping.config.rev180315.IgmpsnoopingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class IgmpPktHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(IgmpPktHandler.class);

    /*
    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final PacketProcessingService pktService;
    private final IInterfaceManager interfaceManager;
    private final IgmpsnoopingConfig config;
    private final DataBroker broker;
    */
    private final PacketProcessingService pktService;

    @Inject
    public IgmpPktHandler(//final OdlInterfaceRpcService interfaceManagerRpc,
                          final PacketProcessingService pktService) {
                          //final IInterfaceManager interfaceManager,
                          //final IgmpsnoopingConfig config,
                          //final DataBroker dataBroker) {
        //this.interfaceManagerRpc = interfaceManagerRpc;
        this.pktService = pktService;
        //this.interfaceManager = interfaceManager;
        //this.config = config;
        //this.broker = dataBroker;
    }

    //TODO: Handle this in a separate thread
    @Override
    public void onPacketReceived(PacketReceived packet) {
        //if (!config.isControllerIgmpSnoopingEnabled()) {
         //   return;
        //}
        Class<? extends PacketInReason> pktInReason = packet.getPacketInReason();
        LOG.trace("pkt rcvd {} reason {}", packet, pktInReason);
       /*
        short tableId = packet.getTableId().getValue();
        if ((tableId == NwConstants.IGMP_TABLE || tableId == NwConstants.IGMP_TABLE_EXTERNAL_TUNNEL)
                && isPktInReasonSendtoCtrl(pktInReason)) {
            byte[] inPayload = packet.getPayload();
            Ethernet ethPkt = new Ethernet();
            try {
                ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NUM_BITS_IN_A_BYTE);
            } catch (PacketException e) {
                LOG.warn("Failed to decode IGMP Packet.", e);
                LOG.trace("Received packet {}", packet);
                return;
            }
            LOG.trace("pkt rcvd {}", packet);
        }
        */
    }


    private void sendPacketOut(byte[] pktOut, BigInteger dpnId, String interfaceName, BigInteger tunnelId) {
        /*
        List<Action> action = getEgressAction(interfaceName, tunnelId);
        TransmitPacketInput output = MDSALUtil.getPacketOut(action, pktOut, dpnId);
        LOG.trace("Transmitting packet: {}", output);
        JdkFutures.addErrorLogging(pktService.transmitPacket(output), LOG, "Transmit packet");
        */
    }

    private boolean isPktInReasonSendtoCtrl(Class<? extends PacketInReason> pktInReason) {
        return pktInReason == SendToController.class;
    }

}
