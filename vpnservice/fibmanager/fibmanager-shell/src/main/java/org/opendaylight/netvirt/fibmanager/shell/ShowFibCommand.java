/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.shell;

import java.io.PrintStream;
import java.util.List;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.ExpectedDataObjectNotFoundException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "fib-show", description = "Displays fib entries")
public class ShowFibCommand extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ShowFibCommand.class);

    private static final String TABULAR_FORMAT = "   %-7s  %-20s  %-20s  %-7s  %-7s";
    private static final String HEADER = String.format(TABULAR_FORMAT, "RD", "Prefix", "NextHop", "Label", "Origin")
                                           + "\n   -------------------------------------------------------------------";

    private SingleTransactionDataBroker singleTxDb;
    private DataBroker dataBroker;

    public void setDataBroker(DataBroker dataBroker) {
        this.singleTxDb = new SingleTransactionDataBroker(dataBroker);
    }

    @Override
    protected Object doExecute() throws Exception {

        PrintStream console = session.getConsole();
        console.println(HEADER);

        InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
        try {
            FibEntries fibEntries = singleTxDb.syncRead(LogicalDatastoreType.CONFIGURATION, id);

            List<VrfTables> vrfTablesList = fibEntries.getVrfTables();
            if (vrfTablesList == null || vrfTablesList.isEmpty()) {
                console.println(" No Fib entries found");
                return null;
            }

            for (VrfTables vrfTable : vrfTablesList) {
                printVrfTable(vrfTable, console);
            }
        } catch (ExpectedDataObjectNotFoundException e404) {
            String errMsg = "FATAL: fib-entries container is missing from MD-SAL";
            console.println("\n" + errMsg);
            LOG.error(errMsg, e404);
        } catch (ReadFailedException rfe) {
            String errMsg = "Internal Error occurred while processing vpnservice:fib-show command";
            console.println("\n" + errMsg);
            LOG.error(errMsg, rfe);
        }
        return null;
    }

    private void printVrfTable(VrfTables vrfTable, PrintStream console) {
        List<VrfEntry> vrfEntries = vrfTable.getVrfEntry();
        if (vrfEntries == null) {
            LOG.warn("Null vrfEntries found for VPN with rd={}", vrfTable.getRouteDistinguisher());
            return;
        }

        for (VrfEntry vrfEntry : vrfEntries) {
            List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
            if (routePaths == null || routePaths.isEmpty()) {
                console.println(String.format(TABULAR_FORMAT,
                                              vrfTable.getRouteDistinguisher(), vrfEntry.getDestPrefix(),
                                              "local", routePaths == null ? "<not set>" : "<empty>",
                                              vrfEntry.getOrigin()));
                continue;
            }
            for (RoutePaths routePath : routePaths) {
                console.println(String.format(TABULAR_FORMAT,
                                              vrfTable.getRouteDistinguisher(), vrfEntry.getDestPrefix(),
                                              routePath.getNexthopAddress(), routePath.getLabel(),
                                              vrfEntry.getOrigin()));
            }
        }
    }

}
