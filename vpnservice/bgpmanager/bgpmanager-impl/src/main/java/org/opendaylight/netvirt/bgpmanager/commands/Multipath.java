/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.omg.CORBA.Object;
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;

@Command(scope = "odl", name = "multipath", description = "Enable/Disable multipaths")
public class Multipath extends OsgiCommandSupport {

    private static final String AF = "--address-family";
    private static final String RD = "--rd";
    private static final String MAXPATH = "--maxpath";
    private static final int MIN_MAXPATH = 1;
    private static final int MAX_MAXPATH = 64;

    @Option(name = RD, aliases = { "-r" },
            description = "rd",
            required = false,
            multiValued = false)
    String rd;

    @Option(name = MAXPATH, aliases = { "-n" },
            description = "max number of paths",
            required = false,
            multiValued = false)
    String maxpath;

    @Option(name = AF, aliases = {"-f"},
            description = "Address family",
            required = true, multiValued = false)

    String addrFamily;


    @Argument(name = "enable|disable|setmaxpath",
            description = "The desired operation",
            required = true, multiValued = false)

    String multipathEnable;

    @Override
    protected Object doExecute() throws Exception {

        if (!Commands.bgpRunning(session.getConsole())) {
            return null;
        }

        BgpManager bm = Commands.getBgpManager();

        af_afi afi = null;
        af_safi safi = null;

        if (addrFamily != null) {
            if (!addrFamily.equals("lu"))  {
                session.getConsole().println("error: " + AF + " must be lu");
                return null;
            }

            // for WP 3 Qbgp, only IP/MPLS_VPN supported
            afi = af_afi.AFI_IP;
            safi = af_safi.SAFI_MPLS_VPN;
        }

        if (maxpath != null) {
            int imaxpath = Integer.parseInt(maxpath);
            if (imaxpath < MIN_MAXPATH || imaxpath > MAX_MAXPATH) {
                session.getConsole().println("error: " + MAXPATH + " range[" + MIN_MAXPATH + " - " + MAX_MAXPATH + "]");
                return null;
            }
        }

        if (multipathEnable != null) {

            switch (multipathEnable) {
                case "enable":
                    bm.enableMultipath(afi, safi);
                    break;
                case "disable":
                    bm.disableMultipath(afi, safi);
                    break;
                case "setmaxpath":
                    if (rd != null && maxpath != null) {
                        bm.multipaths(rd, Integer.parseInt(maxpath));
                    }
                    break;

                default:
                    return usage();
            }
        }

        return null;
    }

    private Object usage() {
        session.getConsole().println("odl:multipath  -f lu <enable|disable> \n"
                + "odl:multipath -f lu -r <rd> -n <maxpath> setmaxpath");
        return null;
    }
}

