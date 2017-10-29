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
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.qbgpConstants;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "odl", name = "bgp-network",
        description = "Add or delete BGP static routes")
public class Network extends OsgiCommandSupport {
    private static final String RD = "--rd";
    private static final String PFX = "--prefix";
    private static final String NH = "--nexthop";
    private static final String LB = "--label";
    private static final String AFI = "--afi";

    private static final Logger LOG = LoggerFactory.getLogger(Network.class);

    @Argument(name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    private final String action = null;

    @Option(name = RD, aliases = {"-r"},
            description = "Route distinguisher",
            required = false, multiValued = false)
    private final String rd = null;

    @Option(name = PFX, aliases = {"-p"},
            description = "prefix/length",
            required = false, multiValued = false)
    private final String pfx = null;

    @Option(name = NH, aliases = {"-n"},
            description = "Nexthop",
            required = false, multiValued = true)
    private final List<String> nh = null;

    @Option(name = LB, aliases = {"-l"},
            description = "Label",
            required = false, multiValued = false)
    private final String lbl = null;

    @Option(name = AFI, aliases = {"-a"},
            description = "Address Family",
            required = false, multiValued = false)
    private final String afi = "1";

    private final RouteOrigin staticOrigin = RouteOrigin.STATIC;

    private Object usage() {
        session.getConsole().println(
                "usage: bgp-network [" + RD + " rd] [" + PFX + " prefix/len] ["
                        + NH + " nexthop] [" + LB + " label] ["
                        + AFI + " afi] <add|del>");
        return null;
    }

    @Override
    protected Object doExecute() throws Exception {
        if (!Commands.bgpRunning(session.getConsole())) {
            return null;
        }
        BgpManager bm = Commands.getBgpManager();
        switch (action) {
            case "add":
                int label = qbgpConstants.LBL_EXPLICIT_NULL;
                if (pfx == null) {
                    session.getConsole().println("error: " + PFX + " is needed");
                    return null;
                }
                if (nh == null) {
                    session.getConsole().println("error: " + NH + " is needed");
                    return null;
                }
                //TODO: syntactic validation of prefix
                for (String nextHop : nh) {
                    if (!Commands.isValid(session.getConsole(), nextHop, Commands.Validators.IPADDR, NH)) {
                        return null;
                    }
                }
                if (lbl != null) {
                    if (!Commands.isValid(session.getConsole(), lbl, Commands.Validators.INT, LB)) {
                        return null;
                    } else {
                        label = Integer.parseInt(lbl);
                    }
                }
                if (!Commands.isValid(session.getConsole(), afi, Commands.Validators.AFI, AFI)) {
                    session.getConsole().println("error: " + AFI + " must be 1 (IPv4) or 2 (IPv6). Default is 1");
                    return null;
                }
                LOG.info("ADD: Adding Fib entry rd {} prefix {} nexthop {} label {}", rd, pfx, nh, label);
                bm.addPrefix(rd, null /*maAddress*/, pfx, nh,
                        VrfEntry.EncapType.Mplsgre, label, 0 /*l3vni*/, null /*gatewayMacAddress*/, staticOrigin);
                LOG.info("ADD: Added Fib entry rd {} prefix {} nexthop {} label {}", rd, pfx, nh, label);
                break;
            case "del":
                if (pfx == null) {
                    session.getConsole().println("error: " + PFX + " is needed");
                    return null;
                }
                if (nh != null || lbl != null) {
                    session.getConsole().println("note: some option(s) not needed as " + NH + " and/or " + LB
                            + "; will be ignored");
                }
                if (!Commands.isValid(session.getConsole(), afi, Commands.Validators.AFI, AFI)) {
                    session.getConsole().println("error: " + AFI + " must be 1 (IPv4) or 2 (IPv6). Default is 1");
                    return null;
                }
                LOG.info("REMOVE: Removing Fib entry rd {} prefix {}", rd, pfx);
                bm.deletePrefix(rd, pfx);
                LOG.info("REMOVE: Removed Fib entry rd {} prefix {}", rd, pfx);
                break;
            default:
                return usage();
        }
        return null;
    }
}
