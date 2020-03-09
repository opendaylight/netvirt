/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager.shell;

import com.google.common.cache.Cache;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "show-vrf-event-cache", description = "Displays VRF event log")
public class ShowVrfEventCache extends OsgiCommandSupport {

    @Option(name = "-r", aliases = {
            "--RD" }, description = "Display events for a given RD ", required = false, multiValued = false)
    String rd;

    @Option(name = "-ip", aliases = {
            "--ipAddr" }, description = "Display events for a given IpAddress ", required = false, multiValued = false)
    String ipAddress;

    @Option(name = "-c", aliases = {
            "--clear" }, description = "Clean the cache", required = false, multiValued = false)

    private static final Logger LOG = LoggerFactory.getLogger(ShowVrfEventCache.class);

    private String clear;
    private IFibManager fibManager;
    private Cache<Pair<String, String>, Cache<String, String>> fibeventMap;

    public void setFibManager(IFibManager fibManager) {
        this.fibManager = fibManager;
    }

    @Override
    @SuppressWarnings({"checkstyle:RegexpSinglelineJava", "checkstyle:IllegalCatch"})
    protected Object doExecute() throws Exception {
        try {
            fibeventMap = fibManager.getFibEventMap();
            if (clear != null) {
                if (!fibeventMap.asMap().isEmpty()) {
                    fibeventMap.invalidateAll();
                }
                return null;
            }

            if (!fibeventMap.asMap().isEmpty()) {
                printTitle();
                // If RD alone is given, print all IPs in this RD.
                if (rd != null && ipAddress == null) {
                    printCacheForVpn(rd);
                } else if (rd != null && ipAddress != null) {
                    printCacheForVpnIpAddress(rd, ipAddress);
                } else {
                    printEntireCache();
                }
            } else {
                LOG.trace("doExecute: FibEventMap is totally empty...");
                printTitle();
            }
        } catch (Exception e) {
            System.out.println("Error while fetching FibEventMap for RD {}" + rd + "IpAddress" + ipAddress);
            LOG.error("doExecute: Failed to fetch parameters", e);
        }
        return null;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    void printTitle() {
        System.out.println("-----------------------------------------------------------------------");
        System.out.println(String.format("%s   %10s  %20s  %15s", "RD", "IP-Address", "Event", "TimeStamp"));
        System.out.println("\n-----------------------------------------------------------------------");
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    void printEntireCache() {
        if (!fibeventMap.asMap().isEmpty()) {
            for (Map.Entry<Pair<String, String>, Cache<String, String>> interfaceEntry : fibeventMap.asMap()
                    .entrySet()) {
                Pair<String, String> rdIpPair = interfaceEntry.getKey();
                printCacheForVpnIpAddress(rdIpPair.getKey(), rdIpPair.getValue());
            }

        } else {
            LOG.trace("printEntireCache: No events found ");
        }
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    void printCacheForVpnIpAddress(String rdName, String ip) {
        Pair<String, String> rdIpPair = new ImmutablePair<>(rdName, ip);
        Cache<String, String> eventList = fibeventMap.getIfPresent(rdIpPair);
        if (eventList != null && !eventList.asMap().isEmpty()) {
            for (Map.Entry<String, String> event : eventList.asMap().entrySet()) {
                System.out.println(
                        String.format("%s  %-15s %-15s  %-5s", rdName, ip, event.getKey(), event.getValue()));
            }
        } else {
            LOG.trace("printCacheForVpnIpAddress: No events found for RD {} IpAddress {}", rdName, ip);
        }
    }

    void printCacheForVpn(String rdName) {
        if (!fibeventMap.asMap().isEmpty()) {
            for (Map.Entry<Pair<String, String>, Cache<String, String>> interfaceEntry : fibeventMap.asMap()
                    .entrySet()) {
                Pair<String, String> rdIpPair = interfaceEntry.getKey();
                if (rdIpPair.getKey().equals(rdName)) {
                    printCacheForVpnIpAddress(rdIpPair.getKey(), rdIpPair.getValue());
                }
            }

        } else {
            LOG.trace("printCacheForVpn: No events found ");
        }
    }
}