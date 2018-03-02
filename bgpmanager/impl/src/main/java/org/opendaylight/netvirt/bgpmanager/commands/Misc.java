/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;

@Command(scope = "odl", name = "bgp-misc",
        description = "Add or delete miscellaneous BGP config options")
public class Misc extends OsgiCommandSupport {
    private static final String LF = "--log-file";
    private static final String LL = "--log-level";
    private static final String SP = "--stalepath-time";

    @Argument(name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    private final String action = null;

    @Option(name = LF, aliases = {"-f"},
            description = "Log file name",
            required = false, multiValued = false)
    private final String file = null;

    @Option(name = LL, aliases = {"-l"},
            description = "Log level", required = false,
            multiValued = false)
    private final String level = null;

    @Option(name = SP, aliases = {"-s"},
            description = "Stale-path time", required = false,
            multiValued = false)
    private final String spt = null;

    private final BgpConfigurationManager bgpConfigurationManager;

    public Misc(BgpConfigurationManager bgpConfigurationManager) {
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    private Object usage() {
        session.getConsole().println(
                "usage: bgp-misc [<" + LF + " name> <" + LL + " level>] ["
                        + SP + " stale-path-time] <add | del>");
        return null;
    }

    private boolean isValidLevel() {
        switch (level) {
            case "emergencies":
            case "alerts":
            case "critical":
            case "errors":
            case "warnings":
            case "notifications":
            case "informational":
            case "debugging":
                return true;
            default:
                break;
        }
        return false;
    }

    @Override
    protected Object doExecute() {
        if (spt == null && file == null && level == null) {
            return usage();
        }
        if (file != null ^ level != null) {
            return usage();
        }
        if (level != null && !isValidLevel()) {
            session.getConsole().println("error: invalid value for " + LL);
            return null;
        }
        switch (action) {
            case "add":
                if (spt != null && Commands.isValid(session.getConsole(), spt, Commands.Validators.INT, SP)) {
                    bgpConfigurationManager.addGracefulRestart(Integer.parseInt(spt));
                }
                if (file != null && level != null) {
                    bgpConfigurationManager.addLogging(file, level);
                }
                break;
            case "del":
                if (spt != null) {
                    bgpConfigurationManager.delGracefulRestart();
                }
                if (file != null && level != null) {
                    bgpConfigurationManager.delLogging();
                }
                break;
            default:
                return usage();
        }
        return null;
    }
}
