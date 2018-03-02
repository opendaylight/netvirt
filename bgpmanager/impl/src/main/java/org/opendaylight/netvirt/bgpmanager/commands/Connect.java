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

@Command(scope = "odl", name = "bgp-connect",
        description = "Add or delete client connection to BGP Config Server")
public class Connect extends OsgiCommandSupport {
    private static final String HOST = "--host";
    private static final String PORT = "--port";

    @Argument(name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    String action = null;

    @Option(name = HOST, aliases = {"-h"},
            description = "IP address of the server",
            required = false, multiValued = false)
    String host = null;

    @Option(name = PORT, aliases = {"-p"},
            description = "Thrift port", required = false,
            multiValued = false)
    String port = null;

    private final BgpConfigurationManager bgpConfigurationManager;

    public Connect(BgpConfigurationManager bgpConfigurationManager) {
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    private Object usage() {
        session.getConsole().println(
                "usage: bgp-connect [" + HOST + " h] [" + PORT + " p] <add | del>");
        return null;
    }

    @Override
    protected Object doExecute() {
        switch (action) {
            case "add":
                if (host == null || port == null) {
                    session.getConsole().println("error: " + HOST + " and " + PORT + " needed");
                    return null;
                }
                if (!Commands.isValid(session.getConsole(), host, Commands.Validators.IPADDR, HOST)
                        || !Commands.isValid(session.getConsole(), port, Commands.Validators.INT, PORT)) {
                    return null;
                }
                // check: already connected?
                bgpConfigurationManager.startConfig(host, Integer.parseInt(port));
                break;
            case "del":
                if (host != null || port != null) {
                    session.getConsole().println("note: option(s) not needed; ignored");
                }
                // check: nothing to stop?
                bgpConfigurationManager.stopConfig();
                break;
            default:
                return usage();
        }
        return null;
    }
}
