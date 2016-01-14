/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.globals;

import java.math.BigInteger;

public class VlanInterfaceInfo extends InterfaceInfo {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private short vlanId;
    private boolean isVlanTransparent;

    public VlanInterfaceInfo(BigInteger dpId,
            String portName, short vlanId) {
        super(dpId, portName);
        this.vlanId = vlanId;
    }

    public VlanInterfaceInfo(String portName, short vlanId) {
        super(portName);
        this.vlanId = vlanId;
    }

    public short getVlanId() {
        return vlanId;
    }

    public void setVlanId(short vlanId) {
        this.vlanId = vlanId;
    }

    public boolean isVlanTransparent() {
        return isVlanTransparent;
    }

    public void setVlanTransparent(boolean isVlanTransparent) {
        this.isVlanTransparent = isVlanTransparent;
    }
}
