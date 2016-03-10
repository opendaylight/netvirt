/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;
import java.net.InetAddress;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.GroupActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PopMplsActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PopPbbActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PopVlanActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushMplsActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushPbbActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.group.action._case.GroupActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.pop.mpls.action._case.PopMplsActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.pop.pbb.action._case.PopPbbActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.pop.vlan.action._case.PopVlanActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.mpls.action._case.PushMplsActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.pbb.action._case.PushPbbActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.vlan.action._case.PushVlanActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.set.field._case.SetFieldBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.ProtocolMatchFieldsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.TunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.VlanMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.protocol.match.fields.PbbBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.vlan.match.fields.VlanIdBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmitBuilder;

public enum ActionType {
    group {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            long groupId = Long.parseLong(actionInfo.getActionValues()[0]);

            return new ActionBuilder().setAction(
                            new GroupActionCaseBuilder().setGroupAction(
                                    new GroupActionBuilder().setGroupId(groupId).build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    output {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            String port = actionValues[0];
            int maxLength = 0;

            if (actionValues.length == 2) {
                maxLength = Integer.valueOf(actionValues[1]);
            }

            return new ActionBuilder().setAction(
                    new OutputActionCaseBuilder().setOutputAction(
                            new OutputActionBuilder().setMaxLength(Integer.valueOf(maxLength))
                                            .setOutputNodeConnector(new Uri(port)).build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    pop_mpls {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder().setAction(
                    new PopMplsActionCaseBuilder().setPopMplsAction(
                            new PopMplsActionBuilder().setEthernetType(
                                    Integer.valueOf(NwConstants.ETHTYPE_IPV4)).build()).build())

                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    pop_pbb {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder()
                    .setAction(new PopPbbActionCaseBuilder().setPopPbbAction(new PopPbbActionBuilder().build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    pop_vlan {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder().setAction(
                    new PopVlanActionCaseBuilder().setPopVlanAction(new PopVlanActionBuilder().build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    push_mpls {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder().setAction(new PushMplsActionCaseBuilder().setPushMplsAction(
                                    new PushMplsActionBuilder().setEthernetType(
                                            Integer.valueOf(NwConstants.ETHTYPE_MPLS_UC)).build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    push_pbb {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder().setAction(
                    new PushPbbActionCaseBuilder().setPushPbbAction(
                                    new PushPbbActionBuilder()
                                            .setEthernetType(Integer.valueOf(NwConstants.ETHTYPE_PBB)).build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    push_vlan {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder().setAction(
                    new PushVlanActionCaseBuilder().setPushVlanAction(
                                    new PushVlanActionBuilder().setEthernetType(
                                            Integer.valueOf(NwConstants.ETHTYPE_802_1Q)).build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    set_field_mpls_label {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            long label = Long.valueOf(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(new SetFieldBuilder().setProtocolMatchFields(
                                            new ProtocolMatchFieldsBuilder().setMplsLabel(label).build()).build())
                                    .build()).setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    set_field_pbb_isid {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            long label = Long.valueOf(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setProtocolMatchFields(
                                            new ProtocolMatchFieldsBuilder().setPbb(
                                                    new PbbBuilder().setPbbIsid(label).build()).build()).build())
                                    .build()).setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    set_field_vlan_vid {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            int vlanId = Integer.valueOf(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setVlanMatch(
                                    new VlanMatchBuilder().setVlanId(
                                                    new VlanIdBuilder().setVlanId(new VlanId(vlanId))
                                                            .setVlanIdPresent(true).build()).build()).build()).build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
        }
    },

    set_field_tunnel_id {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            BigInteger [] actionValues = actionInfo.getBigActionValues();
            if (actionValues.length == 2) {
                return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                        new SetFieldBuilder()
                            .setTunnel(new TunnelBuilder().setTunnelId(actionValues[0])
                                           .setTunnelMask(actionValues[1]).build()).build())
                        .build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
            } else {
                return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                        new SetFieldBuilder()
                            .setTunnel(new TunnelBuilder().setTunnelId(actionValues[0])
                                           .build()).build())
                        .build())
                    .setKey(new ActionKey(actionInfo.getActionKey())).build();
            }

        }

    },

    set_field_eth_dest {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            MacAddress mac = new MacAddress(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setEthernetMatch(
                                    new EthernetMatchBuilder().setEthernetDestination(
                                                    new EthernetDestinationBuilder().setAddress(mac).build()).build())
                                            .build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }

    },

    set_udp_protocol {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setIpMatch(
                                    new IpMatchBuilder().setIpProtocol((short) 17).build()).
                                    build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }

    },
    punt_to_controller {
        @Override
        public Action buildAction(ActionInfo actionInfo) {
            ActionBuilder ab = new ActionBuilder();
            OutputActionBuilder output = new OutputActionBuilder();
            output.setMaxLength(0xffff);
            Uri value = new Uri(OutputPortValues.CONTROLLER.toString());
            output.setOutputNodeConnector(value);
            ab.setAction(new OutputActionCaseBuilder().setOutputAction(output.build()).build());
            ab.setKey(new ActionKey(actionInfo.getActionKey()));
            return ab.build();
        }

    },
    set_destination_port_field {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            Integer portNumber = new Integer(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setLayer4Match(
                                    new UdpMatchBuilder().setUdpDestinationPort(
                                            new PortNumber(portNumber)).build())
                            .build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }

    },
    set_source_port_field {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            Integer portNumber = new Integer(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setLayer4Match(
                                    new UdpMatchBuilder().setUdpSourcePort(
                                            new PortNumber(portNumber)).build())
                            .build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }

    },
    set_source_ip {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            InetAddress sourceIp = null;
            try{
                sourceIp = InetAddress.getByName(actionValues[0]);
            } catch (Exception e){
                e.printStackTrace();
            }
            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setLayer3Match(
                                    new Ipv4MatchBuilder().setIpv4Source(
                                            new Ipv4Prefix(sourceIp.getHostAddress())).build()).
                                            build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }

    },
    set_destination_ip {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            InetAddress sourceIp = null;
            try{
                sourceIp = InetAddress.getByName(actionValues[0]);
            } catch (Exception e){
                e.printStackTrace();
            }
            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setLayer3Match(
                                    new Ipv4MatchBuilder().setIpv4Destination(
                                            new Ipv4Prefix(sourceIp.getHostAddress())).build()).
                                            build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }

    },
    set_field_eth_src {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            MacAddress mac = new MacAddress(actionValues[0]);

            return new ActionBuilder().setAction(
                    new SetFieldCaseBuilder().setSetField(
                            new SetFieldBuilder().setEthernetMatch(
                                    new EthernetMatchBuilder().setEthernetSource(
                                                    new EthernetSourceBuilder().setAddress(mac).build()).build())
                                            .build()).build()).setKey(new ActionKey(actionInfo.getActionKey())).build();

        }
    },

    drop_action {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            DropActionBuilder dab = new DropActionBuilder();
            DropAction dropAction = dab.build();
            ActionBuilder ab = new ActionBuilder();
            ab.setAction(new DropActionCaseBuilder().setDropAction(dropAction).build());
            ab.setKey(new ActionKey(actionInfo.getActionKey())).build();
            return ab.build();
        }
    },

    nx_resubmit {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            String[] actionValues = actionInfo.getActionValues();
            NxResubmitBuilder nxarsb = new NxResubmitBuilder();
            nxarsb.setTable(Short.parseShort(actionValues[0]));
            ActionBuilder ab = new ActionBuilder();
            ab.setAction(new NxActionResubmitRpcAddGroupCaseBuilder().setNxResubmit(nxarsb.build()).build());
            ab.setKey(new ActionKey(actionInfo.getActionKey()));
            return ab.build();
        }
    },

    goto_table {

        @Override
        public Action buildAction(ActionInfo actionInfo) {
            ActionBuilder ab = new ActionBuilder();
            return null;
        }
    };

    private static final int RADIX_HEX = 16;
    public abstract Action buildAction(ActionInfo actionInfo);
}
