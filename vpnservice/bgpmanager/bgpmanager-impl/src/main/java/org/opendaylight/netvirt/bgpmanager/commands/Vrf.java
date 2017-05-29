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
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;

@Command(scope = "odl", name = "bgp-vrf",
        description = "Add or delete BGP VRFs")
public class Vrf extends OsgiCommandSupport {
    private static final String RD = "--rd";
    private static final String IR = "--import-rts";
    private static final String ER = "--export-rts";
    private static final String AFI = "--afi";
    private static final String SAFI = "--safi";

    @Argument(name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    private String action = null;

    @Option(name = RD, aliases = {"-r"},
            description = "Route distinguisher",
            required = false, multiValued = false)
    private String rd = null;

    @Option(name = IR, aliases = {"-i"},
            description = "Import route-targets",
            required = false, multiValued = true)
    private List<String> irts = null;

    @Option(name = ER, aliases = {"-e"},
            description = "Export route-targets",
            required = false, multiValued = true)
    private List<String> erts = null;

    @Option(name = AFI, aliases = {"-a"},
            description = "AFI 1=IPv4, 2=IPv6",
            required = false, multiValued = false)
    private String afi = null;

    @Option(name = SAFI, aliases = {"-s"},
            description = "SAFI type, 5=mpls, 6=evpn",
            required = false, multiValued = false)
    private String safi = null;

    private Object usage() {
        session.getConsole().println(
                "usage: bgp-vrf [" + RD + " rd] [<" + IR + " | " + ER + "> rt1] .. [<" + IR + " | " + ER
                        + "> rtN] [" + AFI + " afi] [" + SAFI + " safi] <add|del>");
        return null;
    }

    @Override
    protected Object doExecute() throws Exception {
        if (!Commands.bgpRunning(session.getConsole())) {
            return null;
        }
        BgpManager bm = Commands.getBgpManager();
        long afiVal = afi != null ? Long.valueOf(afi) : 1L;
        long safiVal = safi != null ? Long.valueOf(safi) : 5L;
        switch (action) {
            case "add":
                if (rd == null || irts == null || erts == null) {
                    session.getConsole().println("error: all options needed");
                    return null;
                }
                // check: rd exists? rd & rt's in format?
                LayerType layerType = LayerType.LAYER3;
                bm.addVrf(rd, irts, erts, layerType, afiVal, safiVal);
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
                bm.deleteVrf(rd, true, afiVal, safiVal);
                break;
            default:
                return usage();
        }
        return null;
    }
}
