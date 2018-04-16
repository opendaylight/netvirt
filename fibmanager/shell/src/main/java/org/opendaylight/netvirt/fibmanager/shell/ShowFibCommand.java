/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.shell;

import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.ExpectedDataObjectNotFoundException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase.EncapType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "fib-show", description = "Displays fib entries.\t"
        + "To get more help use cli fib-show fullHelp")
public class ShowFibCommand extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ShowFibCommand.class);

    private static final String TABULAR_FORMAT = "   %-7s  %-20s  %-20s  %-7s  %-7s";
    private static final String HEADER = String.format(TABULAR_FORMAT, "RD", "Prefix", "NextHop", "Label", "Origin")
                                           + "\n   -------------------------------------------------------------------";
    private static final String ADDRFAMILY = "--addr-family";
    private static final String SUBNET = "--subnet";

    @Argument(name = "addFam|fullHelp", description = "type of address families to show, or full help cli",
            required = false, multiValued = false)
    private final String options = null;

    @Option(name = ADDRFAMILY, aliases = {"-af"},
            description = "show address family ipv4 and/or ipv6 and/or l2vpn",
            required = false, multiValued = true)
    private final List<String> addrFamList = null;

    @Option(name = SUBNET, aliases = {"-sub"},
            description = "show only one IP or subnet sorted by mask ex \"x.x.x.x/32\" or \"2001::1/128\"",
            required = false, multiValued = true)
    private final String prefixOrSubnetOption = null;
    private String prefixOrSubnet = null;

    private SingleTransactionDataBroker singleTxDb;

    public void setDataBroker(DataBroker dataBroker) {
        this.singleTxDb = new SingleTransactionDataBroker(dataBroker);
    }

    @Override
    protected Object doExecute() {
        PrintStream console = session.getConsole();
        if (prefixOrSubnetOption != null && prefixOrSubnetOption.length() > 0) {
            prefixOrSubnet = prefixOrSubnetOption.replace("[", "");
            prefixOrSubnet = prefixOrSubnet.replace("]", "");
            if (!prefixOrSubnet.contains("/")) {
                String maskFull = null;
                try {
                    Inet4Address tempAdd = (Inet4Address) InetAddress.getByName(prefixOrSubnet);
                    maskFull = "/32";
                } catch (SecurityException | UnknownHostException | ClassCastException e) {
                    maskFull = null;
                }
                if (maskFull == null) {
                    try {
                        Inet6Address tempAdd = (Inet6Address) InetAddress.getByName(prefixOrSubnet);
                        maskFull = "/128";
                    } catch (SecurityException | UnknownHostException | ClassCastException e) {
                        maskFull = null;
                    }
                }
                if (maskFull == null) {
                    console.println("a part of cli " + SUBNET + " is wrong => " + prefixOrSubnet);
                    return usage(console);
                }
                prefixOrSubnet += maskFull;
            }
        }
        console.println(HEADER);
        if (options == null && prefixOrSubnet == null && (addrFamList == null || addrFamList.isEmpty())) {
            InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
            try {
                FibEntries fibEntries = singleTxDb.syncRead(LogicalDatastoreType.CONFIGURATION, id);

                List<VpnInstanceNames> vpnInstanceList = fibEntries.getVpnInstanceNames();
                if (vpnInstanceList == null || vpnInstanceList.isEmpty()) {
                    console.println(" No vpnInstances found");
                    return null;
                }

                for (VpnInstanceNames vpnInstanceName : vpnInstanceList) {
                    List<VrfTables> vrfTablesList = vpnInstanceName.getVrfTables();
                    if (vrfTablesList == null || vrfTablesList.isEmpty()) {
                        continue;
                    }
                    for (VrfTables vrfTable : vrfTablesList) {
                        printVrfTable(vpnInstanceName.getVpnInstanceName(), vrfTable, console);
                    }
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
        } else {
            String optionsLowerCase = options != null ? options.toLowerCase(Locale.getDefault()) : "";
            switch (optionsLowerCase) {
                case "fullhelp":
                    return usage(console);
                default :
            }

            if ((addrFamList == null || addrFamList.isEmpty()) && (prefixOrSubnet == null
                    || prefixOrSubnet.indexOf("/") < 5)) {
                console.println("any address family is requiered or " + SUBNET + " is wrong");
                usage(console);
            } else {
                boolean isIpv4 = false;
                boolean isIpv6 = false;
                boolean isL2vpn = false;
                if (addrFamList != null && addrFamList.size() > 0) {
                    for (String addF : addrFamList) {
                        switch (addF.toLowerCase(Locale.getDefault())) {
                            case "ipv4":
                                isIpv4 = true;
                                break;
                            case "ipv6":
                                isIpv6 = true;
                                break;
                            case "l2vpn":
                                isL2vpn = true;
                                break;
                            default :
                        }
                    }
                }
                InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);
                try {
                    FibEntries fibEntries = singleTxDb.syncRead(LogicalDatastoreType.CONFIGURATION, id);

                    List<VpnInstanceNames> vpnInstanceList = fibEntries.getVpnInstanceNames();
                    if (vpnInstanceList == null || vpnInstanceList.isEmpty()) {
                        console.println(" No vpnInstances found");
                        return null;
                    }

                    for (VpnInstanceNames vpnInstanceName : vpnInstanceList) {
                        List<VrfTables> vrfTablesList = vpnInstanceName.getVrfTables();
                        if (vrfTablesList == null || vrfTablesList.isEmpty()) {
                            continue;
                        }
                        for (VrfTables vrfTable : vrfTablesList) {
                            printVrfTable(vpnInstanceName.getVpnInstanceName(),vrfTable, console,
                                    isIpv4, isIpv6, isL2vpn, prefixOrSubnet);
                        }
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
        }
        return null;
    }

    private void printVrfTable(String vpnInstanceName, VrfTables vrfTable, PrintStream console) {
        printVrfTable(vpnInstanceName, vrfTable, console, true, true, true, null);
    }

    private void printVrfTable(String vpnInstanceName, VrfTables vrfTable, PrintStream console, boolean isIpv4, boolean isIpv6,
            boolean isL2vpn, String inputPrefixOrSubnet) {

        List<VrfEntry> vrfEntries = vrfTable.getVrfEntry();
        if (vrfEntries == null) {
            LOG.warn("Null vrfEntries found for VPN with rd={}", vrfTable.getRouteDistinguisher());
            return;
        }

        for (VrfEntry vrfEntry : vrfEntries) {
            boolean showIt = false;
            if (isIpv4 && isIpv6 && isL2vpn) {
                showIt = true;
            }
            if (!showIt && isIpv4) {
                LOG.debug("is ipv4 address family=> vrfEntry.getDestPrefix() = {}", vrfEntry.getDestPrefix());
                showIt = FibHelper.isIpv4Prefix(vrfEntry.getDestPrefix());
            }
            if (!showIt && isIpv6) {
                LOG.debug("is ipv6 address family=> vrfEntry.getDestPrefix() = {}", vrfEntry.getDestPrefix());
                showIt = FibHelper.isIpv6Prefix(vrfEntry.getDestPrefix());
            }
            if (!showIt && isL2vpn) {
                if (vrfEntry.getEncapType() != null && !EncapType.Mplsgre.equals(vrfEntry.getEncapType())) {
                    LOG.debug("is l2vpn address family=> vrfEntry.getEncapType() = {}", vrfEntry.getEncapType());
                    showIt = true;
                }
            }
            if (!showIt && inputPrefixOrSubnet != null) {
                showIt = FibHelper.isBelongingPrefix(vrfEntry.getDestPrefix(), inputPrefixOrSubnet);
            }
            if (!showIt) {
                continue;
            }
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

    private Object usage(PrintStream console) {
        String nl = System.getProperty("line.separator");
        console.println("===================================================");
        console.println("usage cli =>" + nl);
        console.println("fib-show --help => to get the current help");
        console.println("fib-show fullHelp => to get the FULL help" + nl);
        console.println("fib-show -af ipv4  => to get ipv4 address family");
        console.println("fib-show -af ipv4 -af ipv6 => to get ipv4 and ipv6 address family");
        console.println("fib-show -af ipv4 -af ipv6 -af l2vpn => to get ipv4 and ipv6 and l2vpn address family");
        console.println("---------------------------------------------------");
        console.println("fib-show -sub 40.1.1.0/24 => to get all IPv4 from fib belonging to this subnet");
        console.println("fib-show -sub 40.1.1.1/32 => to get the IPv4 from fib");
        console.println("---------------------------------------------------");
        console.println("fib-show -sub 2001::1/64 => to get all IPv6 from fib belonging to this subnet");
        console.println("fib-show -sub 2001::1/128 => to get all IPv6 from fib");
        console.println("===================================================");
        return null;
    }


}
