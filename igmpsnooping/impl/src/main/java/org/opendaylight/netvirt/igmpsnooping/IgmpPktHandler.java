/*
 * Copyright © 2018 Red Hat and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.igmpsnooping;

//import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.genius.mdsalutil.packet.IEEE8021Q;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.genius.mdsalutil.packet.IPv4;
import org.opendaylight.genius.mdsalutil.packet.UDP;
import org.opendaylight.netvirt.igmpsnooping.api.IGMP;
import org.opendaylight.netvirt.igmpsnooping.api.IGMPConstants;
import org.opendaylight.openflowplugin.libraries.liblldp.EtherTypes;
import org.opendaylight.openflowplugin.libraries.liblldp.NetUtils;
import org.opendaylight.openflowplugin.libraries.liblldp.PacketException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketInReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.SendToController;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.igmpsnooping.config.rev180315.IgmpsnoopingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class IgmpPktHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(IgmpPktHandler.class);

    private final OdlInterfaceRpcService interfaceManagerRpc;
    private final PacketProcessingService pktService;
    private final IInterfaceManager interfaceManager;
    private final IgmpsnoopingConfig config;
    private final DataBroker broker;

    @Inject
    public IgmpPktHandler(final OdlInterfaceRpcService interfaceManagerRpc,
                          final PacketProcessingService pktService,
                          final IInterfaceManager interfaceManager,
                          final IgmpsnoopingConfig config,
                          final DataBroker dataBroker) {
        this.interfaceManagerRpc = interfaceManagerRpc;
        this.pktService = pktService;
        this.interfaceManager = interfaceManager;
        this.config = config;
        this.broker = dataBroker;
    }

    //TODO: Handle this in a separate thread
    @Override
    public void onPacketReceived(PacketReceived packet) {
        //if (!config.isControllerIgmpSnoopingEnabled()) {
         //   return;
        //}
        Class<? extends PacketInReason> pktInReason = packet.getPacketInReason();
        LOG.trace("pkt rcvd {} reason {}", packet, pktInReason);
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
            IGMP pktIn;
            pktIn = getIgmpPktIn(ethPkt);
            if (pktIn != null) {
                LOG.trace("IGMP pkt rcvd {}", pktIn);
                LOG.trace("Received packet: {}", packet);
            }
        }
    }

    private IGMP getIgmpPktIn(Ethernet ethernetPacket) {
        Ethernet ethPkt = ethernetPacket;
        if (ethPkt.getEtherType() == (short)NwConstants.ETHTYPE_802_1Q) {
            ethPkt = (Ethernet)ethPkt.getPayload();
        }
        if (ethPkt.getPayload() instanceof IPv4) {
            IPv4 ipPkt = (IPv4) ethPkt.getPayload();
            byte protType = ipPkt.getProtocol();
            if (protType == NwConstants.IP_PROT_IGMP) {
                IGMP igmp = new IGMP();
                byte[] rawIgmpPayload = ipPkt.getRawPayload();
                igmp.deserialize(rawIgmpPayload,0, rawIgmpPayload.length);
                return igmp;
            }
        }
        return null;
    }


    private boolean isPktInReasonSendtoCtrl(Class<? extends PacketInReason> pktInReason) {
        return pktInReason == SendToController.class;
    }

}

