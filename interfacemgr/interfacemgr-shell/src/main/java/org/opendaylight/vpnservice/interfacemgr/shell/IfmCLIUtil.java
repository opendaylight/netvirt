/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.shell;

import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo.InterfaceOpState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;

import java.util.Formatter;

public class IfmCLIUtil {
    private static final String VLAN_OUTPUT_FORMAT_LINE1 = "%-55s";
    private static final String VLAN_OUTPUT_FORMAT = "%-24s %-20s %-15s %-24s";
    private static final String VXLAN_OUTPUT_FORMAT = "%-24s %-24s %-18s %-5s";
    private static final String VXLAN_OUTPUT_FORMAT_LINE1 = "%-49s %-45s";
    private static final String UNSET = "N/A";

    public static void showVlanHeaderOutput() {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT_LINE1, "Name"));
        sb.setLength(0);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT, "", "Dpn", "PortName",
                "Vlan-Id"));
        sb.setLength(0);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT, "Tag", "PortNo",
                "AdmState", "OpState"));
        sb.setLength(0);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT, "Description", "", "", ""));
        sb.setLength(0);
        System.out.println(fmt.format("--------------------------------------------------------------------------------"));
        sb.setLength(0);
        fmt.close();
    }

    public static void showVlanOutput(InterfaceInfo ifaceInfo, Interface iface) {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        IfL2vlan l2vlan = iface.getAugmentation(IfL2vlan.class);
        int vlanId = l2vlan != null ? l2vlan.getVlanId() != null ? l2vlan.getVlanId().getValue() : 0 : 0;
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT_LINE1,
                iface.getName()));
        sb.setLength(0);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT,
                "", (ifaceInfo == null) ? UNSET : ifaceInfo.getDpId(),
                (ifaceInfo == null) ? UNSET : ifaceInfo.getPortName(), vlanId));
        sb.setLength(0);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT,
                (ifaceInfo == null) ? UNSET : ifaceInfo.getInterfaceTag(),
                (ifaceInfo == null) ? UNSET  : ifaceInfo.getPortNo(),
                (ifaceInfo == null) ? UNSET : ifaceInfo.getAdminState(),
                (ifaceInfo == null) ? UNSET : ifaceInfo.getOpState()));
        sb.setLength(0);
        System.out.println(fmt.format(VLAN_OUTPUT_FORMAT + "\n",
                iface.getDescription(), "", "", ""));
        sb.setLength(0);
        fmt.close();
    }

    public static void showVxlanHeaderOutput() {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        System.out.println(fmt
                .format(VXLAN_OUTPUT_FORMAT_LINE1, "Name", "Description"));
        sb.setLength(0);
        System.out.println(fmt.format(VXLAN_OUTPUT_FORMAT, "Local IP",
                "Remote IP", "Gateway IP", "AdmState"));
        sb.setLength(0);
        System.out.println(fmt.format(VXLAN_OUTPUT_FORMAT, "OpState", "Parent",
                "Tag", ""));
        sb.setLength(0);
        System.out.println(fmt
                .format("--------------------------------------------------------------------------------"));
        fmt.close();
    }

    public static void showVxlanOutput(Interface iface, InterfaceInfo interfaceInfo) {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        System.out.println(fmt.format(VXLAN_OUTPUT_FORMAT_LINE1,
                iface.getName(),
                iface.getDescription() == null ? UNSET : iface.getDescription()));
        sb.setLength(0);
        IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
        System.out.println(fmt.format(VXLAN_OUTPUT_FORMAT,
                ifTunnel.getTunnelSource().getIpv4Address().getValue(),
                ifTunnel.getTunnelDestination().getIpv4Address().getValue(),
                ifTunnel.getTunnelGateway() == null ? UNSET : ifTunnel.getTunnelGateway().getIpv4Address().getValue(),
                (interfaceInfo == null) ? InterfaceInfo.InterfaceAdminState.DISABLED : interfaceInfo.getAdminState()));
        sb.setLength(0);
        ParentRefs parentRefs = iface.getAugmentation(ParentRefs.class);
        System.out.println(fmt.format(VXLAN_OUTPUT_FORMAT + "\n",
                (interfaceInfo == null) ? InterfaceOpState.DOWN : interfaceInfo.getOpState(),
                String.format("%s/%s", parentRefs.getDatapathNodeIdentifier(),
                parentRefs.getParentInterface()),
                (interfaceInfo == null) ? UNSET : interfaceInfo.getInterfaceTag(), ""));
        fmt.close();
    }
}
