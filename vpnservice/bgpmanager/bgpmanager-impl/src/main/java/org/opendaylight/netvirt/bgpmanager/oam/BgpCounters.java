/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.oam;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.annotation.Nonnull;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BgpCounters implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(BgpCounters.class);
    private static BgpCountersBroadcaster bgpStatsBroadcaster = null;
    private MBeanServer bgpStatsServer = null;
    private final Map<String, String> countersMap = new HashMap<>();
    private String bgpSdncMip = "127.0.0.1";
    public static final String BGP_VPNV6_FILE = "cmd_ip_bgp_vpnv6_all.txt";
    public static final String BGP_VPNV4_FILE = "cmd_ip_bgp_vpnv4_all.txt";
    public static final String BGP_EVPN_FILE = "cmd_bgp_l2vpn_evpn_all.txt";
    public static final String BGP_VPNV6_SUMMARY_FILE = "cmd_ip_bgp_vpnv6_all_summary.txt";
    public static final String BGP_VPNV4_SUMMARY_FILE = "cmd_ip_bgp_vpnv4_all_summary.txt";
    public static final String BGP_EVPN_SUMMARY_FILE = "cmd_bgp_evpn_all_summary.txt";

    public BgpCounters(String mipAddress) {
        bgpSdncMip = mipAddress;
    }

    @Override
    public void run() {
        try {
            LOG.debug("Fetching counters from BGP");
            resetCounters();
            fetchCmdOutputs("cmd_ip_bgp_summary.txt", "show ip bgp summary");
            fetchCmdOutputs("cmd_bgp_ipv4_unicast_statistics.txt", "show bgp ipv4 unicast statistics");
            fetchCmdOutputs(BGP_VPNV4_FILE, "show ip bgp vpnv4 all");
            fetchCmdOutputs(BGP_VPNV6_FILE, "show ip bgp vpnv6 all");
            fetchCmdOutputs(BGP_EVPN_FILE, "show bgp l2vpn evpn all");
            parseIpBgpSummary();
            parseIpBgpVpnv4All();
            parseIpBgpVpnv6All();
            parseIpBgpVpnv6All();
            parseBgpL2vpnEvpnAll();
            if (LOG.isDebugEnabled()) {
                dumpCounters();
            }
            if (bgpStatsBroadcaster == null) {
                //First time execution
                try {
                    bgpStatsBroadcaster = new BgpCountersBroadcaster();
                    bgpStatsServer = ManagementFactory.getPlatformMBeanServer();
                    ObjectName bgpStatsObj = new ObjectName("SDNC.PM:type=BgpCountersBroadcaster");
                    bgpStatsServer.registerMBean(bgpStatsBroadcaster, bgpStatsObj);
                    LOG.info("BGP Counters MBean Registered :::");
                } catch (JMException e) {
                    LOG.error("Adding a NotificationBroadcaster failed.", e);
                    return;
                }
            }
            bgpStatsBroadcaster.setBgpCountersMap(countersMap);
            LOG.debug("Finished updating the counters from BGP");
        } catch (IOException e) {
            LOG.error("Failed to publish bgp counters ", e);
        }
    }

    private void dumpCounters() {
        for (Map.Entry<String, String> entry : countersMap.entrySet()) {
            LOG.debug("{}, Value = {}", entry.getKey(), entry.getValue());
        }
    }

    void fetchCmdOutputs(String filename, String cmdName) throws IOException {
        try (Socket socket = new Socket(bgpSdncMip, 2605);
             PrintWriter toRouter = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader fromRouter = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter toFile = new BufferedWriter(new FileWriter(filename, true))) {
            socket.setSoTimeout(2 * 1000);

            // Wait for the password prompt
            StringBuilder sb = new StringBuilder();
            int read;
            char[] cbuf = new char[10];
            while (!sb.toString().contains("Password:")) {
                if ((read = fromRouter.read(cbuf)) == -1) {
                    LOG.error("Connection closed by BGPd.");
                    return;
                }
                sb.append(cbuf, 0, read);
            }

            // Send the password
            toRouter.println(BgpConstants.QBGP_VTY_PASSWORD);

            // Wait for the prompt (ending with '>' or '#')
            sb = new StringBuilder();
            String prompt = null;
            while (prompt == null) {
                switch (read = fromRouter.read()) {
                    case -1:
                        LOG.error("Connection closed by BGPd, read {}", sb.toString());
                        return;
                    case '>':
                        // Fall through
                    case '#':
                        sb.append((char) read);
                        prompt = sb.toString().trim();
                        break;
                    default:
                        sb.append((char) read);
                        break;
                }
            }

            // Enable
            toRouter.println("en");

            // Wait for '#'
            while ((read = fromRouter.read()) != '#') {
                if (read == -1) {
                    LOG.error("Connection closed by BGPd, read {}", sb.toString());
                    return;
                }
            }

            // Send the command
            toRouter.println(cmdName);

            // Read all the router's output
            sb = new StringBuilder();
            cbuf = new char[1024];
            while ((read = fromRouter.read(cbuf)) != -1) {
                sb.append(cbuf, 0, read);
                if (sb.toString().trim().endsWith(prompt)) {
                    break;
                }
            }

            // Only keep output up to the last prompt
            int lastPromptIndex = sb.lastIndexOf(prompt);
            if (lastPromptIndex >= 0) {
                sb.delete(lastPromptIndex, sb.length());
            }

            // Store in the file
            toFile.write(sb.toString().trim());
        } catch (UnknownHostException e) {
            LOG.error("Unknown host {}", bgpSdncMip, e);
        } catch (SocketTimeoutException e) {
            LOG.error("Socket timeout", e);
        } catch (IOException e) {
            LOG.error("I/O error", e);
        }
    }

    private static boolean validate(@Nonnull final String ip, af_afi afi) {
        if (ip.equals("")) {
            return false;
        }
        int identifiedAFI = 0;
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address instanceof Inet6Address) {
                identifiedAFI = af_afi.AFI_IPV6.getValue();
            } else if (address instanceof Inet4Address) {
                identifiedAFI = af_afi.AFI_IP.getValue();
            }
        } catch (java.net.UnknownHostException e) {
            /*if exception is catched then the prefix is not an IPv6 and IPv4*/
            LOG.error("Unrecognized ip address ipAddress: {}", ip);
        }
        return identifiedAFI == afi.getValue() ? true : false;
    }

    /*
     * The below function parses the output of "show ip bgp summary" saved in a file.
     * Below is the snippet for the same :-
        <output>
        BGP router identifier 10.183.254.53, local AS number 101
        .....
        Neighbor        V    AS MsgRcvd MsgSent   TblVer  InQ OutQ Up/Down  State/PfxRcd
        10.183.254.76   4   100       3       4        0    0    0 00:01:27        0
        .........
        Total number of neighbors 1
        </output>
     */

    private void parseIpBgpSummary() {
        File file = new File("cmd_ip_bgp_summary.txt");

        try (Scanner scanner = new Scanner(file)) {
            boolean startEntries = false;
            while (scanner.hasNextLine()) {
                String str = scanner.nextLine();
                if (str.contains("State/PfxRcd")) {
                    startEntries = true;
                } else if (startEntries) {
                    String[] result = str.split("\\s+");
                    if (result.length < 5) {
                        return;
                    }
                    String strIp = result[0].trim();
                    if (!validate(strIp, af_afi.AFI_IP)) {
                        return;
                    }
                    final String as = result[2];
                    final String rx = result[3];
                    final String tx = result[4];

                    countersMap.put(
                            BgpConstants.BGP_COUNTER_NBR_PKTS_RX + ":BGP_Nbr_IP_" + strIp + "_AS_" + as
                                    + "_PktsReceived",
                            rx);
                    countersMap.put(
                            BgpConstants.BGP_COUNTER_NBR_PKTS_TX + ":BGP_Nbr_IP_" + strIp + "_AS_" + as + "_PktsSent",
                            tx);
                }
            }
        } catch (IOException e) {
            LOG.error("Could not process the file {}", file.getAbsolutePath());
        }
    }
    /*
     * The below function parses the output of "show bgp ipv4 unicast statistics" saved in a file.
     * Below is the sample output for the same :-
        <output>
        BGP IPv4 Unicast RIB statistics
        ...
        Total Prefixes                :            8
        ......
        </output>
     */

    private void parseBgpIpv4UnicastStatistics() {
        File file = new File("cmd_bgp_ipv4_unicast_statistics.txt");
        String totPfx = "";
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String instr = scanner.nextLine();
                if (instr.contains("Total Prefixes")) {
                    String[] result = instr.split(":");
                    if (result.length > 1) {
                        totPfx = result[1].trim();
                    } else {
                        totPfx = "0";
                    }
                    break;
                }
            }
        } catch (IOException e) {
            LOG.error("Could not process the file {}", file.getAbsolutePath());
            return;
        }
        countersMap.put(BgpConstants.BGP_COUNTER_TOTAL_PFX, totPfx);
    }

    /*
     *  The below function parses the output of "show ip bgp vpnv4 all" saved in a file.
     *  Below is the sample output for the same :-
     *  show ip bgp vpnv4 all
        <output>
        BGP table version is 0, local router ID is 10.183.181.21
        ......
        Route Distinguisher: 100:1
        *>i15.15.15.15/32   10.183.181.25            0    100      0 ?
        *>i17.18.17.17/32   10.183.181.25            0    100      0 ?
        *>i17.18.17.17/32   10.183.181.25            0    100      0 ?
        *>i17.18.17.17/32   10.183.181.25            0    100      0 ?
        Route Distinguisher: 100:2
        *>i16.16.16.16/32   10.183.181.25            0    100      0 ?
        *>i18.18.18.18/32   10.183.181.25            0    100      0 ?
        *>i17.18.17.17/32   10.183.181.25            0    100      0 ?
        </output>
     */
    private void parseIpBgpVpnv4All() {
        File file = new File(BGP_VPNV4_FILE);
        List<String> inputStrs = new ArrayList<>();

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                inputStrs.add(scanner.nextLine());
            }
        } catch (IOException e) {
            LOG.error("Could not process the file {}", file.getAbsolutePath());
            return;
        }
        for (int i = 0; i < inputStrs.size(); i++) {
            String instr = inputStrs.get(i);
            if (instr.contains("Route Distinguisher")) {
                String[] result = instr.split(":");
                String rd = result[1].trim() + "_" + result[2].trim();
                i = processRouteCount(rd + "_VPNV4", i + 1, inputStrs);
            }
        }
    }

    /*
     *  The below function parses the output of "show ip bgp vpnv6 all" saved in a file.
     *  Below is the sample output for the same :-
     *  show ip bgp vpnv6 all
        <output>
        BGP table version is 0, local router ID is 10.183.181.21
        ......
        Route Distinguisher: 100:1
        *>i2001:db8:0:2::/128   10.183.181.25            0    100      0 ?
        *>i2001:db8:0:2::/128   10.183.181.25            0    100      0 ?
        *>i2001:db8:0:2::/128   10.183.181.25            0    100      0 ?
        *>i2001:db8:0:2::/128   10.183.181.25            0    100      0 ?
        Route Distinguisher: 100:2
        *>i2001:db9:0:3::/128   10.183.181.25            0    100      0 ?
        *>i2001:db9:0:3::/128   10.183.181.25            0    100      0 ?
        *>i2001:db9:0:3::/128   10.183.181.25            0    100      0 ?
        </output>
     */
    private void parseIpBgpVpnv6All() {
        File file = new File(BGP_VPNV6_FILE);
        List<String> inputStrs = new ArrayList<>();

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                inputStrs.add(scanner.nextLine());
            }
        } catch (IOException e) {
            LOG.error("Could not process the file {}", file.getAbsolutePath());
            return;
        }
        for (int i = 0; i < inputStrs.size(); i++) {
            String instr = inputStrs.get(i);
            if (instr.contains("Route Distinguisher")) {
                String[] result = instr.split(":");
                String rd = result[1].trim() + "_" + result[2].trim();
                i = processRouteCount(rd + "_VPNV6", i + 1, inputStrs);
            }
        }
    }

    private void parseBgpL2vpnEvpnAll() {
        File file = new File(BGP_EVPN_FILE);
        List<String> inputStrs = new ArrayList<>();

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                inputStrs.add(scanner.nextLine());
            }
        } catch (IOException e) {
            LOG.error("Could not process the file {}", file.getAbsolutePath());
            return;
        }
        for (int i = 0; i < inputStrs.size(); i++) {
            String instr = inputStrs.get(i);
            if (instr.contains("Route Distinguisher")) {
                String[] result = instr.split(":");
                String rd = result[1].trim() + "_" + result[2].trim();
                i = processRouteCount(rd + "_EVPN", i + 1, inputStrs);
            }
        }
        /*populate the "BgpTotalPrefixes" counter by combining
        the prefixes that are calculated per RD basis*/
        int bgpTotalPfxs = calculateBgpTotalPrefixes();
        LOG.trace("BGP Total Prefixes:{}",bgpTotalPfxs);
        countersMap.put(BgpConstants.BGP_COUNTER_TOTAL_PFX,String.valueOf(bgpTotalPfxs));
    }

    private int processRouteCount(String rd, int startIndex, List<String> inputStrs) {
        int num = startIndex;
        int routeCount = 0;
        String key = BgpConstants.BGP_COUNTER_RD_ROUTE_COUNT + ":BGP_RD_" + rd + "_route_count";

        for (String str = inputStrs.get(num); str != null && !str.trim().equals("") && num < inputStrs.size();
                str = inputStrs.get(num)) {
            if (str.contains("Route Distinguisher")) {
                countersMap.put(key, Integer.toString(routeCount));
                return num - 1;
            }
            routeCount++;
            num++;
            if (num == inputStrs.size()) {
                break;
            }
        }
        if (routeCount == 0) {
            // Erroneous condition, should never happen.
            // Implies that the file contains marker for RD  without routes.
            // will form an infinite loop if not broken
            // by sending a big number back.
            return Integer.MAX_VALUE;
        }
        countersMap.put(key, Integer.toString(routeCount));
        return num - 1;
    }

    private int calculateBgpTotalPrefixes() {
        return countersMap.entrySet().stream().filter(entry -> entry.getKey().contains(BgpConstants
                .BGP_COUNTER_RD_ROUTE_COUNT)).map(Map.Entry::getValue).mapToInt(Integer::parseInt).sum();
    }

    private void resetCounters() {
        countersMap.clear();
        resetFile("cmd_ip_bgp_summary.txt");
        resetFile("cmd_bgp_ipv4_unicast_statistics.txt");
        resetFile(BGP_VPNV4_FILE);
        resetFile(BGP_VPNV6_FILE);
        resetFile(BGP_EVPN_FILE);
    }

    static void resetFile(String fileName) {
        File file = new File(fileName);
        if (!file.delete()) {
            try (PrintWriter pw = new PrintWriter(file)) {
                pw.print("");
            } catch (FileNotFoundException e) {
                // Ignored
            }
        }
    }

    static Map<String, String> parseIpBgpVpnAllSummary(Map<String, String> countMap,
                                                       String cmdFile,
                                                       af_afi afi) {
        File file = new File(cmdFile);

        try (Scanner scanner = new Scanner(file)) {
            boolean startEntries = false;
            while (scanner.hasNextLine()) {
                String str = scanner.nextLine();
                LOG.trace("str is:: {}", str);
                if (str.contains("State/PfxRcd")) {
                    startEntries = true;
                } else if (startEntries) {
                    String[] result = str.split("\\s+");
                    if (result.length > 9) {
                        String strIp = result[0].trim();
                        LOG.trace("strIp " + strIp);

                        if (!validate(strIp, afi)) {
                            break;
                        }
                        String statePfxRcvd = result[9];
                        countMap.put(strIp, statePfxRcvd);
                    }
                }
            }
        } catch (IOException e) {
            LOG.trace("Could not process the file {}", file.getAbsolutePath());
            return null;
        }

        return countMap;
    }

    static Map<String, String> parseIpBgpVpnv4AllSummary(Map<String, String> countMap) {
        return BgpCounters.parseIpBgpVpnAllSummary(countMap,
                                                   BGP_VPNV4_SUMMARY_FILE,
                                                   af_afi.AFI_IP);
    }

    static Map<String, String> parseIpBgpVpnv6AllSummary(Map<String, String> countMap) {
        return BgpCounters.parseIpBgpVpnAllSummary(countMap,
                                                   BGP_VPNV6_SUMMARY_FILE,
                                                   af_afi.AFI_IP);
    }

    static Map<String, String> parseBgpL2vpnEvpnAllSummary(Map<String, String> countMap) {
        return BgpCounters.parseIpBgpVpnAllSummary(countMap,
                                                   BGP_EVPN_SUMMARY_FILE,
                                                   af_afi.AFI_IP);
    }
}
