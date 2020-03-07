/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager.commands;

import java.io.PrintStream;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpManager;

@Command(scope = "odl", name = "associate-dcgw-tep",
         description = "Add or delete DCGW BGP peer-ip to TEP mapping")
public class DcgwTep extends OsgiCommandSupport {
    private static final String DCGW = "--dc-gw";
    private static final String TEP = "--tep";

    @Argument(index = 0, name = "add|del", description = "The desired operation",
              required = true, multiValued = false)
    String action = null;

    @Option(name = DCGW, aliases = {"-d"},
            description = "DCGW BGP IP configured",
            required = true, multiValued = false)
    String dcgw = null;

    @Option(name = TEP, aliases = {"-t"},
            description = "TEP IP configured in the DCGW",
            required = false, multiValued = false)
    String tep = null;

    private final BgpManager bgpManager;

    public DcgwTep(BgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    private Object usage() {
        session.getConsole().println(
            "usage: associate-dcgw-tep" + DCGW + " dcgw-ip" + TEP + " tep-ip <add|del>");
        return null;
    }

    @Override
    protected Object doExecute() throws Exception {
        PrintStream ps = session.getConsole();
        switch (action) {
            case "add" :
                if (dcgw == null) {
                    ps.println("error: " + DCGW + " needed");
                    return null;
                }
                if (!Commands.isValid(session.getConsole(), dcgw, Commands.Validators.IPADDR, DCGW)) {
                    return null;
                }
                if (tep == null) {
                    ps.println("error: " + TEP + " needed");
                    return null;
                }
                if (!Commands.isValid(session.getConsole(), tep, Commands.Validators.IPADDR, TEP)) {
                    return null;
                }
                bgpManager.addDcgwTep(dcgw, tep);
                break;

            case "del" :
                if (dcgw == null) {
                    ps.println("error: " + DCGW + " needed");
                    return null;
                }
                if (!Commands.isValid(session.getConsole(), dcgw, Commands.Validators.IPADDR, DCGW)) {
                    return null;
                }
                if (tep != null
                        && !Commands.isValid(session.getConsole(), tep, Commands.Validators.IPADDR, TEP)) {
                    return null;
                }
                bgpManager.delDcgwTep(dcgw, tep);
                break;
            default :
                return usage();
        }
        return null;
    }
}
