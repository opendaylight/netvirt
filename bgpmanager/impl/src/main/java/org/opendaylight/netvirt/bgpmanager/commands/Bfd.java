/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import static org.opendaylight.netvirt.bgpmanager.oam.BgpConstants.MAX_DETECT_MULT;
import static org.opendaylight.netvirt.bgpmanager.oam.BgpConstants.MIN_DETECT_MULT;
import static org.opendaylight.netvirt.bgpmanager.oam.BgpConstants.MIN_RX_MAX;
import static org.opendaylight.netvirt.bgpmanager.oam.BgpConstants.MIN_RX_MIN;
import static org.opendaylight.netvirt.bgpmanager.oam.BgpConstants.MIN_TX_MAX;
import static org.opendaylight.netvirt.bgpmanager.oam.BgpConstants.MIN_TX_MIN;

import java.io.PrintStream;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.netvirt.bgpmanager.oam.BgpConstants;

@Command(scope = "odl", name = "bfd-config",
         description = "Add or delete BFD neighbor")
public class Bfd extends OsgiCommandSupport {
    private static final String RX = "--min-rx";
    private static final String TX = "--min-tx";
    private static final String DM = "--detect-mult";
    private static final String MH = "--multi-hop";

    @Argument(index = 0, name = "add|del", description = "The desired operation",
              required = true, multiValued = false)
    String action = null;

    @Option(name = RX, aliases = {"-r"},
            description = "Minimum BFD receive interval in millisec"
                    + "<" + MIN_RX_MIN + "-" + MIN_RX_MAX + ">",
            required = false, multiValued = false)
    String minRX = null;

    @Option(name = TX, aliases = {"-t"},
            description = "Minimum BFD transmit interval in millisec"
            + "<" + MIN_TX_MIN + "-" + MIN_TX_MAX + ">",
            required = false, multiValued = false)
    String minTX = null;

    @Option(name = DM, aliases = {"-d"},
            description = "No of packet miss for marking session down"
            + "<" + MIN_DETECT_MULT + "-" + MAX_DETECT_MULT + ">",
            required = false, multiValued = false)
    String detectMult = null;

    @Option(name = MH, aliases = {"-m"},
            description = "Multi-Hop or Single-Hop BFD"
            + "<true/false>",
            required = false, multiValued = false)
    String multiHop = null;

    private final BgpManager bgpManager;

    public Bfd(BgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    private Object usage() {
        session.getConsole().println(
            "usage: bgp-config [" + RX + " min-rx-interval] [" + TX + " min-tx-interval] ["
            + DM + " detect-multiplier] [" + MH + " true|false] <add|del>");
        return null;
    }

    @Override
    protected Object doExecute() throws Exception {
        PrintStream ps = session.getConsole();
        switch (action) {
            case "add" :
                int minrx = BgpConstants.BFD_DEFAULT_MIN_RX;
                int mintx = BgpConstants.BFD_DEFAULT_MIN_TX;
                int detectmult = BgpConstants.BFD_DEFAULT_DETECT_MULT;
                boolean multihop = true;
                if (minRX != null) {
                    if (!Commands.isValid(ps, minRX, Commands.Validators.INT, RX)) {
                        return null;
                    } else {
                        minrx = Integer.parseInt(minRX);
                        if (minrx < MIN_RX_MIN || minrx > MIN_RX_MAX) {
                            ps.println("error: value of RX should be between 50 and 50000");
                            return null;
                        }
                    }
                }

                if (minTX != null) {
                    if (!Commands.isValid(ps, minTX, Commands.Validators.INT, TX)) {
                        return null;
                    } else {
                        mintx = Integer.parseInt(minTX);
                        if (mintx < MIN_TX_MIN || mintx > MIN_TX_MAX) {
                            ps.println("error: value of TX should be between 1000 and 4294000");
                            return null;
                        }
                    }
                }

                if (detectMult != null) {
                    if (!Commands.isValid(ps, detectMult, Commands.Validators.INT, DM)) {
                        return null;
                    } else {
                        detectmult = Integer.parseInt(detectMult);
                        if (detectmult < MIN_DETECT_MULT || detectmult > MAX_DETECT_MULT) {
                            ps.println("error: value of detectMult should be between 2 to 255");
                            return null;
                        }
                    }
                }

                if (multiHop != null) {
                    if (!multiHop.equals("true") && !multiHop.equals("false")) {
                        ps.println("error: " + MH + "must be true or false");
                        return null;
                    }
                    if (multiHop.equals("false")) {
                        multihop = false;
                    }
                }

                bgpManager.startBfd(detectmult, minrx, mintx, multihop);

                break;
            case "del" :
                if (detectMult != null || minRX != null || minTX != null || multiHop != null) {
                    session.getConsole().println("note: some option(s) not needed; ignored");
                }
                bgpManager.stopBfd();
                break;
            default :
                return usage();
        }
        return null;
    }
}
