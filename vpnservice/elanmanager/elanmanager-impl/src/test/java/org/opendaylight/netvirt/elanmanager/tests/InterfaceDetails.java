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

import org.opendaylight.genius.interfacemanager.IfmConstants;
import org.opendaylight.genius.interfacemanager.IfmUtil;
import org.opendaylight.genius.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Other;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


public class InterfaceDetails {

    String elan;
    String name;
    BigInteger dpId;
    int portno;
    String mac;
    String prefix;
    int lportTag;
    String parentName;

    Interface iface;
    InstanceIdentifier<Interface> ifaceIid;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface ifState;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId;

    public InterfaceDetails(String elan, String name, BigInteger dpId, int portno, String mac,
                            String prefix, int lportTag) {
        this.dpId = dpId;
        this.elan = elan;
        this.lportTag = lportTag;
        this.mac = mac;
        this.name = name;
        this.portno = portno;
        this.prefix = prefix;
        parentName = "tap" + name.substring(0, 12);
        iface = ExpectedObjects.newInterfaceConfig(name, parentName);
        ifaceIid = ItmUtils.buildId(name);
        ifState = addStateEntry(iface, name, new PhysAddress(mac), new NodeConnectorId("openflow:"
                + dpId.toString() + ":" + portno));
        ifStateId = IfmUtil.buildStateInterfaceId(name);
    }

    public Interface getIface() {
        return iface;
    }

    public void setIface(Interface iface) {
        this.iface = iface;
    }

    public InstanceIdentifier<Interface> getIfaceIid() {
        return ifaceIid;
    }

    public void setIfaceIid(InstanceIdentifier<Interface> ifaceIid) {
        this.ifaceIid = ifaceIid;
    }

    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface getIfState() {
        return ifState;
    }

    public void setIfState(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                                   .ietf.interfaces.rev140508.interfaces.state.Interface ifState) {
        this.ifState = ifState;
    }

    public InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> getIfStateId() {
        return ifStateId;
    }

    public void setIfStateId(InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId) {
        this.ifStateId = ifStateId;
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

    public BigInteger getDpId() {
        return dpId;
    }

    public void setDpId(BigInteger dpId) {
        this.dpId = dpId;
    }

    public String getElan() {
        return elan;
    }

    public void setElan(String elan) {
        this.elan = elan;
    }

    public int getLportTag() {
        return lportTag;
    }

    public void setLportTag(int lportTag) {
        this.lportTag = lportTag;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPortno() {
        return portno;
    }

    public void setPortno(int portno) {
        this.portno = portno;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
