/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import java.io.PrintStream;
import java.util.Date;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.thrift.transport.TTransport;
import org.opendaylight.netvirt.bgpmanager.commands.Cache;


@Command(scope = "odl", name = "display-bgp-config", description = "")
public class DisplayBgpConfigCli extends OsgiCommandSupport {

    @Option(name = "--debug", description = "print debug time stamps",
            required = false, multiValued = false)
    Boolean debug = false;

    private final BgpManager bgpManager;
    private final BgpConfigurationManager bgpConfigurationManager;

    public DisplayBgpConfigCli(BgpManager bgpManager, BgpConfigurationManager bgpConfigurationManager) {
        this.bgpManager = bgpManager;
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    @Override
    protected Object doExecute() {
        PrintStream ps = session.getConsole();

        if (debug) {
            ps.printf("%nis ODL Connected to Q-BGP: %s%n", bgpConfigurationManager.isBgpConnected() ? "TRUE" : "FALSE");
            final TTransport transport = bgpConfigurationManager.getTransport();
            if (transport != null) {
                ps.printf("%nODL BGP Router transport is open: %s%n",
                        transport.isOpen() ? "TRUE" : "FALSE");
            } else {
                ps.printf("%nODL BGP Router transport is NULL%n");
            }
            //last ODL connection attempted TS
            ps.printf("Last ODL connection attempt TS: %s%n", new Date(bgpConfigurationManager.getConnectTS()));
            //last successful connected TS
            ps.printf("Last Successful connection TS: %s%n", new Date(bgpConfigurationManager.getLastConnectedTS()));
            //last ODL started BGP due to configuration trigger TS
            ps.printf("Last ODL started BGP at: %s%n", new Date(bgpConfigurationManager.getStartTS()));
            //last Quagga attempted to RESTART the connection
            ps.printf("Last Quagga BGP, sent reSync at: %s%n", new Date(bgpManager.getQbgprestartTS()));

            //stale cleanup start - end TS
            ps.printf("Time taken to create stale fib : %s ms%n",
                    bgpConfigurationManager.getStaleEndTime() - bgpConfigurationManager.getStaleStartTime());

            //Config replay start - end TS
            ps.printf("Time taken to create replay configuration : %s ms%n",
                    bgpConfigurationManager.getCfgReplayEndTime() - bgpConfigurationManager.getCfgReplayStartTime());

            //Stale cleanup time
            ps.printf("Time taken for Stale FIB cleanup : %s ms%n", bgpConfigurationManager.getStaleCleanupTime());

            ps.printf("Total stale entries created %d %n", bgpConfigurationManager.getTotalStaledCount());
            ps.printf("Total stale entries cleared %d %n", bgpConfigurationManager.getTotalCleared());
        }
        Cache cache = new Cache(bgpConfigurationManager);
        return cache.show(session);
    }
}
