/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.globals;

import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;

public class LogicalGroupInterfaceInfo extends InterfaceInfo {

    /*
         List of vxlan/GRE physical tunnel interfaces makes a logical tunnel interface
         between a pair of DPNs

     */

    private List<String> parentInterfaceNames;

    public LogicalGroupInterfaceInfo(String portName, BigInteger srcDpId,List<String> pInterfaces) {
        super(srcDpId,portName);

        parentInterfaceNames = new ArrayList(pInterfaces);
    }

    public List<String> getParentInterfaceNames() {
        return parentInterfaceNames;
    }

    public void addParentInterfaceName(String parentIfname) {
        parentInterfaceNames.add(parentIfname);
    }

    public int getTotalParentInterfaces() {
        return parentInterfaceNames.size();
    }

    public void deleteParentInterfaceName(String parentIfname) {
        parentInterfaceNames.remove(parentIfname);
    }

}

