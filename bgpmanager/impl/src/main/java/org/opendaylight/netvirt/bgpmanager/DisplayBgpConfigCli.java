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
import org.opendaylight.ovsdb.utils.mdsal.utils.TransactionHistory;


@Command(scope = "odl", name = "display-bgp-config", description = "")
public class DisplayBgpConfigCli extends OsgiCommandSupport {

    @Option(name = "--debug", description = "print debug time stamps",
            required = false, multiValued = false)
    Boolean debug = false;

    @Option(name = "--history", description = "print bgp updates",
            required = false, multiValued = false)
    Boolean showHistory = false;

    private final BgpManager bgpManager;

    public DisplayBgpConfigCli(BgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    @Override
    protected Object doExecute() throws Exception {
        PrintStream ps = session.getConsole();

        if (debug) {
            ps.printf("%nis ODL Connected to Q-BGP: %s%n", bgpManager.isBgpConnected() ? "TRUE" : "FALSE");
            final TTransport transport = bgpManager.getBgpConfigurationManager().getTransport();
            if (transport != null) {
                ps.printf("%nODL BGP Router transport is open: %s%n",
                        transport.isOpen() ? "TRUE" : "FALSE");
            } else {
                ps.printf("%nODL BGP Router transport is NULL%n");
            }
            //last ODL connection attempted TS
            ps.printf("Last ODL connection attempt TS: %s%n", new Date(bgpManager.getConnectTS()));
            //last successful connected TS
            ps.printf("Last Successful connection TS: %s%n", new Date(bgpManager.getLastConnectedTS()));
            //last ODL started BGP due to configuration trigger TS
            ps.printf("Last ODL started BGP at: %s%n", new Date(bgpManager.getStartTS()));
            //last Quagga attempted to RESTART the connection
            ps.printf("Last Quagga BGP, sent reSync at: %s%n", new Date(bgpManager.getQbgprestartTS()));

            //stale cleanup start - end TS
            ps.printf("Time taken to create stale fib : %s ms%n",
                    bgpManager.getStaleEndTime() - bgpManager.getStaleStartTime());

            //Config replay start - end TS
            ps.printf("Time taken to create replay configuration : %s ms%n",
                    bgpManager.getCfgReplayEndTime() - bgpManager.getCfgReplayStartTime());

            //Stale cleanup time
            ps.printf("Time taken for Stale FIB cleanup : %s ms%n", bgpManager.getStaleCleanupTime());

            ps.printf("Total stale entries created %d %n", bgpManager.getBgpConfigurationManager()
                    .getTotalStaledCount());
            ps.printf("Total stale entries cleared %d %n", bgpManager.getBgpConfigurationManager().getTotalCleared());
        }

        if (showHistory) {
            TransactionHistory bgpUpdatesHistory = bgpManager.getBgpConfigurationManager().getBgpUpdatesHistory();
            bgpUpdatesHistory.getElements().forEach(update -> {
                ps.println(update.getData());
            });
        }
        Cache cache = new Cache(bgpManager);
        return cache.show(session);
    }
}
