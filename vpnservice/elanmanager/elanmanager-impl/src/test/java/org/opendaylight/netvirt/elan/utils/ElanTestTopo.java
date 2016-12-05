/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo.InterfaceAdminState;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo.InterfaceOpState;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.BaseIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.BaseIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;

/**
 * Gathers information about a simulated topology consisting in 2 CSSs and 2
 * TORs with 2 ports each.
 */
public class ElanTestTopo {

    // Topology stuff
    public static final BigInteger DPN1ID = BigInteger.valueOf(1L);
    public static final BigInteger DPN2ID = BigInteger.valueOf(2L);
    public static final String DPN1TEPIP = "10.10.10.51";
    public static final String DPN2TEPIP = "10.10.10.52";
    public static final String GWIP = "20.20.20.1";
    public static final NodeId DPN1NODEID = new NodeId("openflow:1");
    public static final NodeId DPN2NODEID = new NodeId("openflow:2");

    public static final NodeId TOR1NODEID = new NodeId("hwvtep:1");
    public static final String TOR1TEPIP = "10.10.10.40";
    public static final NodeId TOR2NODEID = new NodeId("hwvtep:2");
    public static final String TOR2TEPIP = "10.10.10.45";

    // Interfaces Stuff
    public static final String TAP_PORT1_NAME = uuidToTapPortName("007d796a-4aea-43ea-9e16-5ffad29aeee5");
    public static final String TAP_PORT2_NAME = uuidToTapPortName("120a7cb4-8872-4957-a747-6a1c6807181f");
    public static final String TAP_PORT3_NAME = uuidToTapPortName("fd1deb94-901b-4f1c-875c-0cf7a1006603");
    public static final String TAP_PORT4_NAME = uuidToTapPortName("16711cd0-0247-4d5c-97b5-270d1a5cca18");
    public static final String TAP_PORT5_NAME = uuidToTapPortName("90a8a8c8-11c8-4434-9d3d-95f20a21e422");
    public static final String TAP_PORT6_NAME = uuidToTapPortName("2edd69aa-c234-46f4-bc0a-979f04d47ea0");

    public static final String DPN11IFACENAME = "dpn1.1";
    public static final String DPN12IFACENAME = "dpn1.2";
    public static final String DPN21IFACENAME = "dpn2.1";
    public static final String DPN22IFACENAME = "dpn2.2";
    public static final String DPN1TODPN2TRUNKIFACENAME = "INT-TUNNEL12";
    public static final String DPN2TODPN1TRUNKIFACENAME = "INT-TUNNEL21";
    public static final String DPN1TOTOR1TRUNKIFACENAME = "EXT-TUNNEL11";
    public static final String DPN1TOTOR2TRUNKIFACENAME = "EXT-TUNNEL12";
    public static final String DPN2TOTOR1TRUNKIFACENAME = "EXT-TUNNEL21";
    public static final String DPN2TOTOR2TRUNKIFACENAME = "EXT-TUNNEL22";

    public static HashMap<String, Integer> lportTagsPerName = new HashMap<String, Integer>();

    static {
        lportTagsPerName.put(DPN11IFACENAME, 10);
        lportTagsPerName.put(DPN12IFACENAME, 11);
        lportTagsPerName.put(DPN21IFACENAME, 12);
        lportTagsPerName.put(DPN22IFACENAME, 13);
        lportTagsPerName.put(DPN1TODPN2TRUNKIFACENAME, 20);
        lportTagsPerName.put(DPN2TODPN1TRUNKIFACENAME, 21);
        lportTagsPerName.put(DPN1TOTOR1TRUNKIFACENAME, 22);
        lportTagsPerName.put(DPN1TOTOR2TRUNKIFACENAME, 23);
        lportTagsPerName.put(DPN2TOTOR1TRUNKIFACENAME, 24);
        lportTagsPerName.put(DPN2TOTOR2TRUNKIFACENAME, 25);
    }

    public static final Interface DPN11IFACE = createVlanInterface(DPN1ID, 1, TAP_PORT1_NAME, DPN11IFACENAME, null,
            true);
    public static final Interface DPN12IFACE = createVlanInterface(DPN1ID, 2, TAP_PORT2_NAME, DPN12IFACENAME, null,
            true);
    public static final Interface DPN21IFACE = createVlanInterface(DPN2ID, 1, TAP_PORT3_NAME, DPN21IFACENAME, null,
            true);
    public static final Interface DPN22IFACE = createVlanInterface(DPN2ID, 2, TAP_PORT4_NAME, DPN22IFACENAME, null,
            true);

    public static final InterfaceInfo DPN11INFO = createVlanInterfaceInfo(DPN11IFACE);
    public static final InterfaceInfo DPN12INFO = createVlanInterfaceInfo(DPN12IFACE);
    public static final InterfaceInfo DPN21INFO = createVlanInterfaceInfo(DPN21IFACE);
    public static final InterfaceInfo DPN22INFO = createVlanInterfaceInfo(DPN22IFACE);

    public static final Interface DPN1TODPN2TUNNELIF = createTunnelIface(DPN1ID, DPN1TEPIP, DPN2TEPIP,
            DPN1TODPN2TRUNKIFACENAME, 0, /* internal */true, 3);
    public static final InterfaceInfo DPN1TODPN2TUNNELINFO = createTunnelIfInfo(DPN1ID, DPN1TODPN2TUNNELIF,
            DPN1TODPN2TRUNKIFACENAME);

    public static final Interface DPN2TODPN1TUNNELIF = createTunnelIface(DPN2ID, DPN2TEPIP, DPN1TEPIP,
            DPN2TODPN1TRUNKIFACENAME, 0, /* internal */true, 3);
    public static final InterfaceInfo DPN2TODPN1TUNNELIFINFO = createTunnelIfInfo(DPN2ID, DPN2TODPN1TUNNELIF,
            DPN2TODPN1TRUNKIFACENAME);

    public static final Interface DPN1TOTOR1TUNNELIF = createTunnelIface(DPN1ID, DPN1TEPIP, TOR1TEPIP,
            DPN1TOTOR1TRUNKIFACENAME, 0, /* internal */false, 5);
    public static final InterfaceInfo DPN1TOTOR1TUNNELIFINFO = createTunnelIfInfo(DPN1ID, DPN1TOTOR1TUNNELIF,
            DPN1TOTOR1TRUNKIFACENAME);

    public static final Interface DPN1TOTOR2TUNNELIF = createTunnelIface(DPN1ID, DPN1TEPIP, TOR1TEPIP,
            DPN1TOTOR2TRUNKIFACENAME, 0, /* internal */false, 6);
    public static final InterfaceInfo DPN1TOTOR2TUNNELIFINFO = createTunnelIfInfo(DPN1ID, DPN1TOTOR2TUNNELIF,
            DPN1TOTOR2TRUNKIFACENAME);

    public static final HashMap<String, InterfaceInfo> INTERFACEINFOMAP = new HashMap<String, InterfaceInfo>();

    static {
        INTERFACEINFOMAP.put(DPN11IFACENAME, DPN11INFO);
        INTERFACEINFOMAP.put(DPN12IFACENAME, DPN12INFO);
        INTERFACEINFOMAP.put(DPN21IFACENAME, DPN21INFO);
        INTERFACEINFOMAP.put(DPN22IFACENAME, DPN22INFO);
        INTERFACEINFOMAP.put(DPN1TODPN2TRUNKIFACENAME, DPN1TODPN2TUNNELINFO);
        INTERFACEINFOMAP.put(DPN2TODPN1TRUNKIFACENAME, DPN2TODPN1TUNNELIFINFO);
    }

    // Default Egress Actions for regular Interfaces
    public static Action dpn11EgressAction1 = new ActionInfo(ActionType.output,
            new String[] { String.valueOf(DPN11INFO.getPortNo()) }, 0).buildAction();
    public static List<Action> dpn11EgressActions = Arrays.asList(dpn11EgressAction1);
    public static Action dpn12EgressAction1 = new ActionInfo(ActionType.output,
            new String[] { String.valueOf(DPN12INFO.getPortNo()) }, 0).buildAction();
    public static List<Action> dpn12EgressActions = Arrays.asList(dpn12EgressAction1);
    public static Action dpn21EgressAction1 = new ActionInfo(ActionType.output,
            new String[] { String.valueOf(DPN21INFO.getPortNo()) }, 0).buildAction();
    public static List<Action> dpn21EgressActions = Arrays.asList(dpn21EgressAction1);
    public static Action dpn22EgressAction1 = new ActionInfo(ActionType.output,
            new String[] { String.valueOf(DPN22INFO.getPortNo()) }, 0).buildAction();
    public static List<Action> dpn22EgressActions = Arrays.asList(dpn21EgressAction1);

    public static final HashMap<String, List<Action>> LOCALIFACTIONMAP = new HashMap<String, List<Action>>();

    static {
        LOCALIFACTIONMAP.put(DPN11IFACENAME, dpn11EgressActions);
        LOCALIFACTIONMAP.put(DPN12IFACENAME, dpn12EgressActions);
        LOCALIFACTIONMAP.put(DPN21IFACENAME, dpn21EgressActions);
        LOCALIFACTIONMAP.put(DPN22IFACENAME, dpn22EgressActions);
    }

    // Default Egress Actions for Trunk Interfaces
    // result.add(new ActionInfo(ActionType.set_field_tunnel_id,
    // new BigInteger[] { BigInteger.valueOf(tunnelKey.longValue()) },
    // actionKeyStart) );
    // result.add(new ActionInfo(ActionType.output, new String[] { portNo},
    // actionKeyStart));

    /*
     * *************************************************************************
     * *********************** Utilities
     **************************************************************************************************/

    /**
     * Egress actions when the lportTag of the remote Interface is the tunnelKey
     * to be used through the tunnel. That is, for inter-CSSs tunnels (Internal
     * tunnels).
     *
     * @param tunnelIfName tunnel interface name
     * @param remoteIfName remote interface name
     * @return list of actions
     */
    public static List<Action> getTrunkEgressActions(String tunnelIfName, String remoteIfName) {
        InterfaceInfo remoteIfInfo = INTERFACEINFOMAP.get(remoteIfName);
        return getTrunkEgressActions(tunnelIfName, Long.valueOf(remoteIfInfo.getInterfaceTag()));
    }

    public static List<Action> getTrunkEgressActions(String tunnelIfName, Long tunnelKey) {
        InterfaceInfo trunkIfInfo = INTERFACEINFOMAP.get(tunnelIfName);
        List<Action> result = new ArrayList<Action>();
        result.add(new ActionInfo(ActionType.set_field_tunnel_id, new BigInteger[] { BigInteger.valueOf(tunnelKey) }, 0)
                .buildAction());
        result.add(new ActionInfo(ActionType.output, new String[] { Integer.toString(trunkIfInfo.getPortNo()) }, 1)
                .buildAction());

        return result;
    }

    public static final InterfaceInfo getInterfaceInfo(String ifaceName) {
        return INTERFACEINFOMAP.get(ifaceName);
    }

    private static String uuidToTapPortName(String uuid) {
        return new StringBuilder().append("tap").append(uuid.substring(0, 11)).toString();
    }

    public static Interface createTunnelIface(BigInteger srcDpnId, String srcTepIp, String dstTepIp, String ifName,
            int vlanId, boolean internal, int portNo) {
        InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(ifName)).setName(ifName)
                .setDescription(ifName).setEnabled(true).setType(Tunnel.class).addAugmentation(BaseIds.class,
                        new BaseIdsBuilder().setOfPortId(new NodeConnectorId(Integer.toString(portNo))).build());
        ParentRefs parentRefs = new ParentRefsBuilder().setDatapathNodeIdentifier(srcDpnId).build();
        builder.addAugmentation(ParentRefs.class, parentRefs);
        if (vlanId > 0) {
            IfL2vlan l2vlan = new IfL2vlanBuilder().setVlanId(new VlanId(vlanId)).build();
            builder.addAugmentation(IfL2vlan.class, l2vlan);
        }
        IfTunnel tunnel = new IfTunnelBuilder().setTunnelDestination(IpAddressBuilder.getDefaultInstance(dstTepIp))
                .setTunnelGateway(IpAddressBuilder.getDefaultInstance(GWIP))
                .setTunnelSource(IpAddressBuilder.getDefaultInstance(srcTepIp))
                .setTunnelInterfaceType(TunnelTypeVxlan.class).setInternal(internal).build();
        builder.addAugmentation(IfTunnel.class, tunnel);
        return builder.build();
    }

    public static InterfaceInfo createTunnelIfInfo(BigInteger dpId, Interface tunnelIf, String ifaceName) {
        InterfaceInfo result = new InterfaceInfo(dpId, "port_" + ifaceName);
        result.setInterfaceName(ifaceName);
        result.setInterfaceTag(lportTagsPerName.get(ifaceName));
        result.setAdminState(InterfaceAdminState.ENABLED);
        result.setOpState(InterfaceOpState.UP);
        result.setInterfaceType(InterfaceInfo.InterfaceType.VXLAN_TRUNK_INTERFACE);
        result.setPortNo(Integer.parseInt(tunnelIf.getAugmentation(BaseIds.class).getOfPortId().getValue()));
        return result;
    }

    public static Interface createVlanInterface(BigInteger dpId, int portNo, String parentPort, String ifaceName,
            Integer vlanId, boolean isVlanTransparent) {

        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();

        IfL2vlan.L2vlanMode l2VlanMode = (isVlanTransparent) ? IfL2vlan.L2vlanMode.Transparent
                : IfL2vlan.L2vlanMode.Trunk;

        if (!isVlanTransparent) {
            l2VlanMode = IfL2vlan.L2vlanMode.TrunkMember;
            ifL2vlanBuilder.setVlanId(new VlanId(vlanId));
        }
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);

        Interface result = new InterfaceBuilder().setEnabled(true).setName(ifaceName).setType(L2vlan.class)
                .addAugmentation(BaseIds.class,
                        new BaseIdsBuilder().setOfPortId(new NodeConnectorId(Integer.toString(portNo))).build())
                .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build())
                .addAugmentation(ParentRefs.class,
                        new ParentRefsBuilder().setParentInterface(parentPort).setDatapathNodeIdentifier(dpId).build())
                .build();

        return result;
    }

    public static InterfaceInfo createVlanInterfaceInfo(Interface iface) {
        return createVlanInterfaceInfo(iface.getAugmentation(ParentRefs.class).getDatapathNodeIdentifier(),
                iface.getAugmentation(ParentRefs.class).getParentInterface(), iface.getName(),
                InterfaceInfo.InterfaceAdminState.ENABLED, InterfaceInfo.InterfaceOpState.UP,
                Integer.parseInt(iface.getAugmentation(BaseIds.class).getOfPortId().getValue()));

    }

    public static InterfaceInfo createVlanInterfaceInfo(BigInteger dpId, String portName, String interfaceName,
            InterfaceInfo.InterfaceAdminState adminState, InterfaceInfo.InterfaceOpState opState, int portNo) {
        InterfaceInfo result = new InterfaceInfo(dpId, portName);
        result.setInterfaceName(interfaceName);
        result.setInterfaceTag(lportTagsPerName.get(interfaceName));
        result.setAdminState(adminState);
        result.setOpState(opState);
        result.setInterfaceType(InterfaceInfo.InterfaceType.VLAN_INTERFACE);
        result.setPortNo(portNo);
        return result;
    }
}
