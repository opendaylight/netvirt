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
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;

@Command(scope = "odl", name = "bgp-vrf",
        description = "Add or delete BGP VRFs")
public class Vrf extends OsgiCommandSupport {
    private static final String RD = "--rd";
    private static final String IR = "--import-rts";
    private static final String ER = "--export-rts";
    private static final String ADDRF = "--addr-family";

    @Argument(name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    private final String action = null;

    @Option(name = RD, aliases = {"-r"},
            description = "Route distinguisher",
            required = false, multiValued = false)
    private final String rd = null;

    @Option(name = IR, aliases = {"-i"},
            description = "Import route-targets",
            required = false, multiValued = true)
    private final List<String> irts = null;

    @Option(name = ER, aliases = {"-e"},
            description = "Export route-targets",
            required = false, multiValued = true)
    private final List<String> erts = null;


    @Option(name = ADDRF, aliases = {"-af"},
            description = "AddressFamily IPV4 or IPV6 or L2VPN (IPv(4 or 6) is on MPLS, L2VPN uses IPV4)",
            required = false, multiValued = false)
    private final String addrf = null;

    private final BgpManager bgpManager;

    public Vrf(BgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    private Object usage() {
        session.getConsole().println(
                "usage: bgp-vrf [" + RD + " rd] [<" + IR + " | " + ER + "> rt1] .. [<" + IR + " | " + ER
                        + "> rtN] [" + ADDRF + " AddressFamily] <add|del>");
        return null;
    }

    @Override
    protected Object doExecute() {
        AddressFamily af = null;
        if (addrf.compareToIgnoreCase("IPV_4") == 0) {
            af = AddressFamily.IPV4;
        } else if (addrf.compareToIgnoreCase("IPV_6") == 0) {
            af = AddressFamily.IPV6;
        } else if (addrf.compareToIgnoreCase("L2VPN") == 0) {
            af = AddressFamily.L2VPN;
        } else {
            return usage();
        }
        switch (action) {
            case "add":
                if (rd == null || irts == null || erts == null) {
                    session.getConsole().println("error: all options needed");
                    return null;
                }
                // check: rd exists? rd & rt's in format?
                bgpManager.addVrf(rd, irts, erts, af);
                break;
            case "del":
                if (rd == null) {
                    session.getConsole().println("error: " + RD + " needed");
                    return null;
                }
                if (irts != null || erts != null) {
                    session.getConsole().println("error: some option(s) not needed; ignored");
                }
                // check: rd exists? in format?
                bgpManager.deleteVrf(rd, true, af);
                break;
            default:
                return usage();
        }
        return null;
    }
}
