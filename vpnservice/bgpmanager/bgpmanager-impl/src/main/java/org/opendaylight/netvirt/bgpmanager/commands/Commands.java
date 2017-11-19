/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;

public class Commands {
    private static BgpManager bm;
    private static final long AS_MIN = 0;
    private static final long AS_MAX = 4294967295L;//2^32-1

    enum Validators {
        IPADDR, INT, ASNUM, AFI
    }

    // Suppress this for now - BgpManager should be injected instead of accessing statically
    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public Commands(BgpManager bgpm) {
        bm = bgpm;
    }

    public static BgpManager getBgpManager() {
        return bm;
    }

    public static boolean isValid(PrintStream ps, String val, Validators type, String name) {
        switch (type) {
            case INT:
                try {
                    Integer.parseInt(val);
                } catch (NumberFormatException nme) {
                    ps.println("error: value of " + name + " is not an integer");
                    return false;
                }
                break;
            case IPADDR:
                try {
                    new Ipv4Address(val);
                } catch (IllegalArgumentException | NullPointerException e) {
                    ps.println("error: value of " + name + " is not an IP address");
                    return false;
                }
                break;
            case ASNUM:
                if (!validateAsNumber(ps, val)) {
                    return false;
                }
                break;
            case AFI:
                try {
                    int afiValue = Integer.parseInt(val);
                    if (afiValue < 1 || afiValue > af_afi.values().length) {
                        ps.println("error: value of " + name
                                + " is not an integer between 1(ipv4) and 2(ipv6), its value is " + val);
                        return false;
                    }
                } catch (NumberFormatException nme) {
                    ps.println("error: value of " + name + " is not an integer");
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }

    public static boolean bgpRunning(PrintStream ps) {
        if (getBgpManager() == null) {
            ps.println("error: cannot run command, BgpManager not started");
            return false;
        }
        return true;
    }

    private static boolean validateAsNumber(PrintStream ps, String strAsnum) {

        try {
            long asNum = Long.parseLong(strAsnum);
            if ((int) asNum == 0 || (int) asNum == 65535 || (int) asNum == 23456) {
                ps.println("Reserved AS Number supplied ");
                return false;
            }
            if (asNum <= AS_MIN || asNum > AS_MAX) {
                ps.println("Invalid AS Number , supported range [1," + AS_MAX + "]");
                return false;
            }
        } catch (NumberFormatException e) {
            ps.println("Invalid AS Number ");
            return false;
        }
        return true;
    }
}

