/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;
import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.MetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.ProtocolMatchFieldsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.VlanMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.ArpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.protocol.match.fields.PbbBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.vlan.match.fields.VlanIdBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.ArpOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.ArpSpa;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.ArpTpa;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.EthDst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.EthSrc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.EthType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.InPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.IpProto;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.Ipv4Dst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.Ipv4Src;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.MatchField;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.Metadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.MplsLabel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.PbbIsid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TcpDst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TcpSrc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.UdpDst;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.UdpSrc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.VlanVid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.TunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TunnelId;

public enum MatchFieldType {
    eth_src {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return EthSrc.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            EthernetMatchBuilder ethernetMatchBuilder = (EthernetMatchBuilder) mapMatchBuilder
                    .get(EthernetMatchBuilder.class);

            if (ethernetMatchBuilder == null) {
                ethernetMatchBuilder = new EthernetMatchBuilder();
                mapMatchBuilder.put(EthernetMatchBuilder.class, ethernetMatchBuilder);
            }

            ethernetMatchBuilder.setEthernetSource(new EthernetSourceBuilder().setAddress(
                    new MacAddress(matchInfo.getStringMatchValues()[0])).build());
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            EthernetMatchBuilder ethernetMatchBuilder = (EthernetMatchBuilder) mapMatchBuilder
                    .remove(EthernetMatchBuilder.class);

            if (ethernetMatchBuilder != null) {
                matchBuilderInOut.setEthernetMatch(ethernetMatchBuilder.build());
            }
        }
    },

    eth_dst {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return EthDst.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            EthernetMatchBuilder ethernetMatchBuilder = (EthernetMatchBuilder) mapMatchBuilder
                    .get(EthernetMatchBuilder.class);

            if (ethernetMatchBuilder == null) {
                ethernetMatchBuilder = new EthernetMatchBuilder();
                mapMatchBuilder.put(EthernetMatchBuilder.class, ethernetMatchBuilder);
            }

            ethernetMatchBuilder.setEthernetDestination(new EthernetDestinationBuilder().setAddress(
                    new MacAddress(matchInfo.getStringMatchValues()[0])).build());
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            EthernetMatchBuilder ethernetMatchBuilder = (EthernetMatchBuilder) mapMatchBuilder
                    .remove(EthernetMatchBuilder.class);

            if (ethernetMatchBuilder != null) {
                matchBuilderInOut.setEthernetMatch(ethernetMatchBuilder.build());
            }
        }
    },

    eth_type {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return EthType.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            EthernetMatchBuilder ethernetMatchBuilder = (EthernetMatchBuilder) mapMatchBuilder
                    .get(EthernetMatchBuilder.class);

            if (ethernetMatchBuilder == null) {
                ethernetMatchBuilder = new EthernetMatchBuilder();
                mapMatchBuilder.put(EthernetMatchBuilder.class, ethernetMatchBuilder);
            }

            ethernetMatchBuilder.setEthernetType(new EthernetTypeBuilder().setType(
                    new EtherType(matchInfo.getMatchValues()[0])).build());
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            EthernetMatchBuilder ethernetMatchBuilder = (EthernetMatchBuilder) mapMatchBuilder
                    .remove(EthernetMatchBuilder.class);

            if (ethernetMatchBuilder != null) {
                matchBuilderInOut.setEthernetMatch(ethernetMatchBuilder.build());
            }
        }
    },

    in_port {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return InPort.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {

            StringBuffer nodeConnectorId = new StringBuffer().append("openflow:").append(matchInfo.getBigMatchValues()[0])
            .append(':').append(matchInfo.getBigMatchValues()[1]);
            matchBuilderInOut.setInPort(new NodeConnectorId(nodeConnectorId.toString()));
        }
    },

    ip_proto {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return IpProto.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            IpMatchBuilder ipMatchBuilder = (IpMatchBuilder) mapMatchBuilder.get(IpMatchBuilder.class);

            if (ipMatchBuilder == null) {
                ipMatchBuilder = new IpMatchBuilder();
                mapMatchBuilder.put(IpMatchBuilder.class, ipMatchBuilder);
            }

            ipMatchBuilder.setIpProtocol(Short.valueOf((short) matchInfo.getMatchValues()[0])).build();
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            IpMatchBuilder ipMatchBuilder = (IpMatchBuilder) mapMatchBuilder.remove(IpMatchBuilder.class);

            if (ipMatchBuilder != null) {
                matchBuilderInOut.setIpMatch(ipMatchBuilder.build());
            }
        }
    },

    ipv4_dst {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return Ipv4Dst.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            Ipv4MatchBuilder ipv4MatchBuilder = (Ipv4MatchBuilder) mapMatchBuilder.get(Ipv4MatchBuilder.class);

            if (ipv4MatchBuilder == null) {
                ipv4MatchBuilder = new Ipv4MatchBuilder();
                mapMatchBuilder.put(Ipv4MatchBuilder.class, ipv4MatchBuilder);
            }

            long[] prefix = matchInfo.getMatchValues();
            ipv4MatchBuilder.setIpv4Destination(new Ipv4Prefix(MDSALUtil.longToIp(prefix[0], prefix[1]))).build();
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            Ipv4MatchBuilder ipv4MatchBuilder = (Ipv4MatchBuilder) mapMatchBuilder.remove(Ipv4MatchBuilder.class);

            if (ipv4MatchBuilder != null) {
                matchBuilderInOut.setLayer3Match(ipv4MatchBuilder.build());
            }
        }
    },

    ipv4_src {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return Ipv4Src.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            Ipv4MatchBuilder ipv4MatchBuilder = (Ipv4MatchBuilder) mapMatchBuilder.get(Ipv4MatchBuilder.class);

            if (ipv4MatchBuilder == null) {
                ipv4MatchBuilder = new Ipv4MatchBuilder();
                mapMatchBuilder.put(Ipv4MatchBuilder.class, ipv4MatchBuilder);
            }

            long[] prefix = matchInfo.getMatchValues();
            ipv4MatchBuilder.setIpv4Source(new Ipv4Prefix(MDSALUtil.longToIp(prefix[0], prefix[1]))).build();
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            Ipv4MatchBuilder ipv4MatchBuilder = (Ipv4MatchBuilder) mapMatchBuilder.remove(Ipv4MatchBuilder.class);

            if (ipv4MatchBuilder != null) {
                matchBuilderInOut.setLayer3Match(ipv4MatchBuilder.build());
            }
        }
    },

    arp_op {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return ArpOp.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ArpMatchBuilder arpMatchBuilder = (ArpMatchBuilder) mapMatchBuilder.get(ArpMatchBuilder.class);

            if (arpMatchBuilder == null) {
                arpMatchBuilder = new ArpMatchBuilder();
                mapMatchBuilder.put(ArpMatchBuilder.class, arpMatchBuilder);
            }

            arpMatchBuilder.setArpOp(Integer.valueOf((int) matchInfo.getMatchValues()[0]));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ArpMatchBuilder arpMatchBuilder = (ArpMatchBuilder) mapMatchBuilder.remove(ArpMatchBuilder.class);

            if (arpMatchBuilder != null) {
                matchBuilderInOut.setLayer3Match(arpMatchBuilder.build());
            }
        }
    },

    arp_tpa {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return ArpTpa.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ArpMatchBuilder arpMatchBuilder = (ArpMatchBuilder) mapMatchBuilder.get(ArpMatchBuilder.class);

            if (arpMatchBuilder == null) {
                arpMatchBuilder = new ArpMatchBuilder();
                mapMatchBuilder.put(ArpMatchBuilder.class, arpMatchBuilder);
            }

            long[] prefix = matchInfo.getMatchValues();
            arpMatchBuilder.setArpTargetTransportAddress(new Ipv4Prefix(MDSALUtil.longToIp(prefix[0], prefix[1])));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ArpMatchBuilder arpMatchBuilder = (ArpMatchBuilder) mapMatchBuilder.remove(ArpMatchBuilder.class);

            if (arpMatchBuilder != null) {
                matchBuilderInOut.setLayer3Match(arpMatchBuilder.build());
            }
        }
    },

    arp_spa {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return ArpSpa.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ArpMatchBuilder arpMatchBuilder = (ArpMatchBuilder) mapMatchBuilder.get(ArpMatchBuilder.class);

            if (arpMatchBuilder == null) {
                arpMatchBuilder = new ArpMatchBuilder();
                mapMatchBuilder.put(ArpMatchBuilder.class, arpMatchBuilder);
            }

            long[] prefix = matchInfo.getMatchValues();
            arpMatchBuilder.setArpSourceTransportAddress(new Ipv4Prefix(MDSALUtil.longToIp(prefix[0], prefix[1])));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ArpMatchBuilder arpMatchBuilder = (ArpMatchBuilder) mapMatchBuilder.remove(ArpMatchBuilder.class);

            if (arpMatchBuilder != null) {
                matchBuilderInOut.setLayer3Match(arpMatchBuilder.build());
            }
        }
    },

    metadata {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return Metadata.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            MetadataBuilder metadataBuilder = (MetadataBuilder) mapMatchBuilder.get(MetadataBuilder.class);

            if (metadataBuilder == null) {
                metadataBuilder = new MetadataBuilder();
                mapMatchBuilder.put(MetadataBuilder.class, metadataBuilder);
            }

            BigInteger[] metadataValues = matchInfo.getBigMatchValues();
            metadataBuilder.setMetadata(metadataValues[0]).setMetadataMask(metadataValues[1]).build();
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            MetadataBuilder metadataBuilder = (MetadataBuilder) mapMatchBuilder.remove(MetadataBuilder.class);

            if (metadataBuilder != null) {
                matchBuilderInOut.setMetadata(metadataBuilder.build());
            }
        }
    },

    mpls_label {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return MplsLabel.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ProtocolMatchFieldsBuilder protocolMatchFieldsBuilder = (ProtocolMatchFieldsBuilder) mapMatchBuilder
                    .get(ProtocolMatchFieldsBuilder.class);

            if (protocolMatchFieldsBuilder == null) {
                protocolMatchFieldsBuilder = new ProtocolMatchFieldsBuilder();
                mapMatchBuilder.put(ProtocolMatchFieldsBuilder.class, protocolMatchFieldsBuilder);
            }

            protocolMatchFieldsBuilder.setMplsLabel(Long.valueOf(matchInfo.getStringMatchValues()[0])).build();
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ProtocolMatchFieldsBuilder protocolMatchFieldsBuilder = (ProtocolMatchFieldsBuilder) mapMatchBuilder
                    .remove(ProtocolMatchFieldsBuilder.class);

            if (protocolMatchFieldsBuilder != null) {
                matchBuilderInOut.setProtocolMatchFields(protocolMatchFieldsBuilder.build());
            }
        }
    },

    pbb_isid {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return PbbIsid.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ProtocolMatchFieldsBuilder protocolMatchFieldsBuilder = (ProtocolMatchFieldsBuilder) mapMatchBuilder
                    .get(ProtocolMatchFieldsBuilder.class);

            if (protocolMatchFieldsBuilder == null) {
                protocolMatchFieldsBuilder = new ProtocolMatchFieldsBuilder();
                mapMatchBuilder.put(ProtocolMatchFieldsBuilder.class, protocolMatchFieldsBuilder);
            }

            protocolMatchFieldsBuilder.setPbb(new PbbBuilder().setPbbIsid(Long.valueOf(matchInfo.getMatchValues()[0]))
                    .build());
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            ProtocolMatchFieldsBuilder protocolMatchFieldsBuilder = (ProtocolMatchFieldsBuilder) mapMatchBuilder
                    .remove(ProtocolMatchFieldsBuilder.class);

            if (protocolMatchFieldsBuilder != null) {
                matchBuilderInOut.setProtocolMatchFields(protocolMatchFieldsBuilder.build());
            }
        }
    },

    tcp_dst {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return TcpDst.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            TcpMatchBuilder tcpMatchBuilder = (TcpMatchBuilder) mapMatchBuilder.get(TcpMatchBuilder.class);

            if (tcpMatchBuilder == null) {
                tcpMatchBuilder = new TcpMatchBuilder();
                mapMatchBuilder.put(TcpMatchBuilder.class, tcpMatchBuilder);
            }

            tcpMatchBuilder.setTcpDestinationPort(new PortNumber(Integer.valueOf((int) matchInfo.getMatchValues()[0])));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            TcpMatchBuilder tcpMatchBuilder = (TcpMatchBuilder) mapMatchBuilder.remove(TcpMatchBuilder.class);

            if (tcpMatchBuilder != null) {
                matchBuilderInOut.setLayer4Match(tcpMatchBuilder.build());
            }
        }
    },

    tcp_src {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return TcpSrc.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            TcpMatchBuilder tcpMatchBuilder = (TcpMatchBuilder) mapMatchBuilder.get(TcpMatchBuilder.class);

            if (tcpMatchBuilder == null) {
                tcpMatchBuilder = new TcpMatchBuilder();
                mapMatchBuilder.put(TcpMatchBuilder.class, tcpMatchBuilder);
            }

            tcpMatchBuilder.setTcpSourcePort(new PortNumber(Integer.valueOf((int) matchInfo.getMatchValues()[0])));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            TcpMatchBuilder tcpMatchBuilder = (TcpMatchBuilder) mapMatchBuilder.remove(TcpMatchBuilder.class);

            if (tcpMatchBuilder != null) {
                matchBuilderInOut.setLayer4Match(tcpMatchBuilder.build());
            }
        }
    },

    udp_dst {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return UdpDst.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            UdpMatchBuilder udpMatchBuilder = (UdpMatchBuilder) mapMatchBuilder.get(UdpMatchBuilder.class);

            if (udpMatchBuilder == null) {
                udpMatchBuilder = new UdpMatchBuilder();
                mapMatchBuilder.put(UdpMatchBuilder.class, udpMatchBuilder);
            }

            udpMatchBuilder.setUdpDestinationPort(new PortNumber(Integer.valueOf((int) matchInfo.getMatchValues()[0])));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            UdpMatchBuilder udpMatchBuilder = (UdpMatchBuilder) mapMatchBuilder.remove(UdpMatchBuilder.class);

            if (udpMatchBuilder != null) {
                matchBuilderInOut.setLayer4Match(udpMatchBuilder.build());
            }
        }
    },

    udp_src {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return UdpSrc.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            UdpMatchBuilder udpMatchBuilder = (UdpMatchBuilder) mapMatchBuilder.get(UdpMatchBuilder.class);

            if (udpMatchBuilder == null) {
                udpMatchBuilder = new UdpMatchBuilder();
                mapMatchBuilder.put(UdpMatchBuilder.class, udpMatchBuilder);
            }

            udpMatchBuilder.setUdpSourcePort(new PortNumber(Integer.valueOf((int) matchInfo.getMatchValues()[0])));
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            UdpMatchBuilder udpMatchBuilder = (UdpMatchBuilder) mapMatchBuilder.remove(UdpMatchBuilder.class);

            if (udpMatchBuilder != null) {
                matchBuilderInOut.setLayer4Match(udpMatchBuilder.build());
            }
        }
    },
    tunnel_id {
        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            TunnelBuilder tunnelBuilder = (TunnelBuilder) mapMatchBuilder.get(TunnelBuilder.class);

            if (tunnelBuilder == null) {
                tunnelBuilder = new TunnelBuilder();
                mapMatchBuilder.put(TunnelBuilder.class, tunnelBuilder);
            }

            BigInteger[] tunnelIdValues = matchInfo.getBigMatchValues();
            tunnelBuilder.setTunnelId(tunnelIdValues[0]);
            if(tunnelIdValues.length > 1){
                tunnelBuilder.setTunnelMask(tunnelIdValues[1]);
            }
            tunnelBuilder.build();
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            TunnelBuilder tunnelBuilder = (TunnelBuilder) mapMatchBuilder.remove(TunnelBuilder.class);

            if (tunnelBuilder != null) {
                matchBuilderInOut.setTunnel(tunnelBuilder.build());
            }
        }

        @Override
        protected Class<? extends MatchField> getMatchType() {
            return TunnelId.class;
        }

    },

    vlan_vid {
        @Override
        protected Class<? extends MatchField> getMatchType() {
            return VlanVid.class;
        }

        @Override
        public void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            VlanMatchBuilder vlanMatchBuilder = (VlanMatchBuilder) mapMatchBuilder.get(VlanMatchBuilder.class);

            if (vlanMatchBuilder == null) {
                vlanMatchBuilder = new VlanMatchBuilder();
                mapMatchBuilder.put(VlanMatchBuilder.class, vlanMatchBuilder);
            }

            vlanMatchBuilder.setVlanId(new VlanIdBuilder()
            .setVlanId(new VlanId(Integer.valueOf((int) matchInfo.getMatchValues()[0])))
            .setVlanIdPresent(((int) matchInfo.getMatchValues()[0] == 0) ? Boolean.FALSE : Boolean.TRUE)
            .build());
        }

        @Override
        public void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder) {
            VlanMatchBuilder vlanMatchBuilder = (VlanMatchBuilder) mapMatchBuilder.remove(VlanMatchBuilder.class);

            if (vlanMatchBuilder != null) {
                matchBuilderInOut.setVlanMatch(vlanMatchBuilder.build());
            }
        }
    };


    public abstract void createInnerMatchBuilder(MatchInfo matchInfo, Map<Class<?>, Object> mapMatchBuilder);

    public abstract void setMatch(MatchBuilder matchBuilderInOut, MatchInfo matchInfo,
            Map<Class<?>, Object> mapMatchBuilder);

    protected abstract Class<? extends MatchField> getMatchType();

    protected boolean hasMatchFieldMask() {
        // Override this to return true
                return false;
    }
}
