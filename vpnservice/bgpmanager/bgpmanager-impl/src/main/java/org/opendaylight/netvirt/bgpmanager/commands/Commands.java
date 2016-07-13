/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.*;

public class Commands {
    private static BgpManager bm;
    public static final int IPADDR = 1;
    public static final int INT = 2;

    public Commands(BgpManager bgpm) {
        bm = bgpm;
    }

    public static BgpManager getBgpManager() {
        return bm;
    }

    public static boolean isValid(String val, int type, String name) {
        switch (type) {
            case INT : 
                try {
                    int i = Integer.parseInt(val);
                } catch (NumberFormatException nme) {
                    System.err.println("error: value of "+name+" is not an integer");
                    return false;
                }
                break;
            case IPADDR:
                try {
                    Ipv4Address addr = new Ipv4Address(val);
                } catch (Exception e) {
                    System.err.println("error: value of "+name+" is not an IP address");
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }

    public static boolean bgpRunning() {
        if (getBgpManager() == null) {
            System.err.println("error: cannot run command, BgpManager not started");
            return false;
        }
        return true;
    }
}

