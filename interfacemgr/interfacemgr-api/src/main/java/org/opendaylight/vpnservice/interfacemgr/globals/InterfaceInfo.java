/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.globals;

import java.io.Serializable;
import java.math.BigInteger;

public class InterfaceInfo implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public enum InterfaceType  {
        VLAN_INTERFACE,
        VXLAN_TRUNK_INTERFACE,
        GRE_TRUNK_INTERFACE,
        VXLAN_VNI_INTERFACE,
        MPLS_OVER_GRE,
        LOGICAL_GROUP_INTERFACE,
        UNKNOWN_INTERFACE;
    }

    public enum InterfaceAdminState {
        ENABLED,
        DISABLED
    }

    public enum InterfaceOpState {
        UP,
        DOWN
    }

    protected InterfaceType interfaceType;
    protected int interfaceTag;
    protected BigInteger dpId = IfmConstants.INVALID_DPID;
    protected InterfaceAdminState adminState = InterfaceAdminState.ENABLED;
    protected InterfaceOpState opState;
    protected long groupId;
    protected long l2domainGroupId;
    protected int portNo = IfmConstants.INVALID_PORT_NO;
    protected String portName;
    protected String interfaceName;
    protected boolean isUntaggedVlan;

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public InterfaceInfo(BigInteger dpId, String portName) {
        this.dpId = dpId;
        this.portName = portName;
    }

    public InterfaceInfo(String portName) {
        this.portName = portName;
    }

    public boolean isOperational() {
        return adminState == InterfaceAdminState.ENABLED && opState == InterfaceOpState.UP;
    }

    public InterfaceType getInterfaceType() {
        return interfaceType;
    }
    public void setInterfaceType(InterfaceType lportType) {
        this.interfaceType = lportType;
    }
    public int getInterfaceTag() {
        return interfaceTag;
    }
    public void setInterfaceTag(int interfaceTag) {
        this.interfaceTag = interfaceTag;
    }
    public void setUntaggedVlan(boolean isUntaggedVlan) {
        this.isUntaggedVlan = isUntaggedVlan;
    }
    public boolean isUntaggedVlan() {
        return  isUntaggedVlan;
    }
    public BigInteger getDpId() {
        return dpId;
    }
    public void setDpId(BigInteger dpId) {
        this.dpId = dpId;
    }
    public InterfaceAdminState getAdminState() {
        return adminState;
    }
    public void setAdminState(InterfaceAdminState adminState) {
        this.adminState = adminState;
    }
    public InterfaceOpState getOpState() {
        return opState;
    }
    public void setOpState(InterfaceOpState opState) {
        this.opState = opState;
    }
    public long getGroupId() {
        return groupId;
    }
    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }
    public long getL2domainGroupId() {
        return l2domainGroupId;
    }
    public void setL2domainGroupId(long l2domainGroupId) {
        this.l2domainGroupId = l2domainGroupId;
    }

    public int getPortNo() {
        return portNo;
    }

    public void setPortNo(int portNo) {
        this.portNo = portNo;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }
    public String getPortName(){
        return this.portName;
    }
}
