/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.tests;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.opendaylight.genius.interfacemanager.IfmConstants;
import org.opendaylight.genius.interfacemanager.IfmUtil;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Other;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class TunnelInterfaceDetails {
    String src;
    String dst;
    String dstIp;
    int lportTag;
    int portno;
    String trunkInterfaceName;
    String mac;
    String portName;
    Random random = new Random();
    Interface iface;
    InstanceIdentifier<Interface> ifaceIid;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface ifState;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId;

    public TunnelInterfaceDetails(String src, int portno, String dst, String srcIp, String dstIp, int lportTag) {
        this.dst = dst;
        this.dstIp = dstIp;
        this.lportTag = lportTag;
        this.portno = portno;
        this.src = src;
        this.mac = getDummyMac();
        this.portName = "port" + random.nextInt(9) + "" + random.nextInt(9) + "" + random.nextInt(9);

        BigInteger srcDpnId = new BigInteger(src);
        boolean isDpn = false;
        if (isDpnDestination()) {
            isDpn = true;
        }
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        String interfaceName = ItmUtils.getInterfaceName(new BigInteger(src), portName, 0);
        trunkInterfaceName = ItmUtils.getTrunkInterfaceName(null, interfaceName ,srcIp, dstIp, tunType.toString());
        IpAddress srcIpAddr = new IpAddress(new Ipv4Address(srcIp));
        IpAddress dstIpAddr = new IpAddress(new Ipv4Address(dstIp));
        IpAddress gwIpAddr = new IpAddress(new Ipv4Address("0.0.0.0"));
        iface = ItmUtils.buildTunnelInterface(srcDpnId, trunkInterfaceName,
                String.format("%s %s",ItmUtils.convertTunnelTypetoString(tunType), "Trunk Interface"),
                true, tunType, srcIpAddr, dstIpAddr, gwIpAddr, 0, isDpn,
                false, ITMConstants.DEFAULT_MONITOR_PROTOCOL, null, false, null, null);
        ifaceIid = ItmUtils.buildId(trunkInterfaceName);
        ifState = addStateEntry(iface, trunkInterfaceName, new PhysAddress(mac), new NodeConnectorId("openflow:"
                + src + ":" + portno));
        ifStateId = IfmUtil.buildStateInterfaceId(trunkInterfaceName);
    }

    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface addStateEntry(
            Interface interfaceInfo, String interfaceName,
            PhysAddress physAddress, NodeConnectorId nodeConnectorId) {

        InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setType(Other.class)
                .setIfIndex(IfmConstants.DEFAULT_IFINDEX);
        Integer ifIndex;
        ifaceBuilder.setIfIndex(lportTag);
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId = IfmUtil
                .buildStateInterfaceId(interfaceName);
        List<String> childLowerLayerIfList = new ArrayList<>();
        if (nodeConnectorId != null) {
            childLowerLayerIfList.add(0, nodeConnectorId.getValue());
        }
        ifaceBuilder
                .setAdminStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                        .interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up)
                .setOperStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                        .interfaces.rev140508.interfaces.state.Interface.OperStatus.Up)
                .setLowerLayerIf(childLowerLayerIfList);
        if (physAddress != null) {
            ifaceBuilder.setPhysAddress(physAddress);
        }
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceName));
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .ietf.interfaces.rev140508.interfaces.state.Interface ifState = ifaceBuilder
                .build();
        BigInteger dpId = IfmUtil.getDpnFromNodeConnectorId(nodeConnectorId);
        return ifState;
    }

    public String getTrunkInterfaceName() {
        return trunkInterfaceName;
    }

    public String getMac() {
        return mac;
    }

    String getDummyMac() {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(9));
            sb.append(random.nextInt(9));
            if (i < 5) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    private boolean isDpnDestination() {
        //BigInteger dpnId = new BigInteger(dst);
        return true;
    }

    public String getDst() {
        return dst;
    }

    public String getDstIp() {
        return dstIp;
    }

    public int getLportTag() {
        return lportTag;
    }

    public int getPortno() {
        return portno;
    }

    public String getSrc() {
        return src;
    }

    public Interface getIface() {
        return iface;
    }

    public InstanceIdentifier<Interface> getIfaceIid() {
        return ifaceIid;
    }

    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface getIfState() {
        return ifState;
    }

    public InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> getIfStateId() {
        return ifStateId;
    }
}
