/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import java.util.List;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;

@Command(scope = "odl", name = "bgp-rtr",
        description = "Add or delete BGP router instance")
public class Router extends OsgiCommandSupport {
    private static final String AS = "--as-number";
    private static final String RID = "--router-id";
    private static final String SP = "--stale-path-time";
    private static final String FB = "--f-bit";

    @Argument(name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    private final String action = null;

    @Option(name = AS, aliases = {"-a"},
            description = "AS number",
            required = false, multiValued = false)
    private final String asNum = null;

    @Option(name = RID, aliases = {"-r"},
            description = "Router ID",
            required = false, multiValued = false)
    private final String rid = null;

    @Option(name = SP, aliases = {"-s"},
            description = "Stale-path time",
            required = false, multiValued = false)
    private final String spt = null;

    @Option(name = FB, aliases = {"-f"},
            description = "F-bit",
            required = false, multiValued = false)
    private final String fbit = null;

    private final BgpConfigurationManager bgpConfigurationManager;

    public Router(BgpConfigurationManager bgpConfigurationManager) {
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    private Object usage() {
        session.getConsole().println(
                "usage: bgp-rtr [" + AS + " as-number] [" + RID + " router-id] ["
                        + SP + " stale-path-time] [" + FB + " on|off] <add | del>");
        return null;
    }

    @Override
    protected Object doExecute() {
        switch (action) {
            case "add":
                // check: rtr already running?
                long asn = 0;
                int stalePath = 0;
                boolean fb = false;
                if (asNum == null) {
                    session.getConsole().println("error: " + AS + " is needed");
                    return null;
                }
                if (!Commands.isValid(session.getConsole(), asNum, Commands.Validators.ASNUM, AS)) {
                    return null;
                }
                asn = Long.parseLong(asNum);
                if (rid != null && !Commands.isValid(session.getConsole(), rid, Commands.Validators.IPADDR, RID)) {
                    return null;
                }
                if (spt != null) {
                    if (!Commands.isValid(session.getConsole(), spt, Commands.Validators.INT, SP)) {
                        return null;
                    } else {
                        stalePath = Integer.parseInt(spt);
                    }
                }
                if (fbit != null) {
                    switch (fbit) {
                        case "on":
                            fb = true;
                            break;
                        case "off":
                            fb = false;
                            break;
                        default:
                            session.getConsole().println("error: " + FB + " must be on or off");
                            return null;
                    }
                }
                bgpConfigurationManager.startBgp(asn, rid, stalePath, fb);
                break;
            case "del":
                // check: nothing to stop?
                if (asNum != null || rid != null || spt != null || fbit != null) {
                    session.getConsole().println("note: option(s) not needed; ignored");
                }
                Bgp conf = bgpConfigurationManager.getConfig();
                if (conf == null) {
                    session.getConsole().println("error : no BGP configs present");
                    break;
                }
                List<Neighbors> nbrs = conf.getNeighbors();
                if (nbrs != null && nbrs.size() > 0) {
                    session.getConsole().println("error: all BGP congiguration must be deleted "
                            + "before stopping the router instance");
                    break;
                }
                bgpConfigurationManager.stopBgp();
                break;
            default:
                return usage();
        }
        return null;
    }
}
