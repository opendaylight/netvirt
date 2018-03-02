/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.TcpMd5SignaturePasswordType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;

@Command(scope = "odl", name = "configure-bgp", description = "")
public class ConfigureBgpCli extends OsgiCommandSupport {
    private static final long AS_MIN = 0;
    private static final long AS_MAX = 4294967295L;//2^32-1

    @Option(name = "-op", aliases = {"--operation", "--op"}, description = "[start-bgp-server, stop-bgp-server, "
            + "add-neighbor, delete-neighbor, add-route, delete-route,graceful-restart, enable-log ]",
            required = false, multiValued = false)
    String op;

    //exec configure-bgp  add-neighbor --ip <neighbor-ip> --as-num <as-num> --address-family <af> --use-source-ip
    // <sip> --ebgp-multihops <em> --next-hop <nh>
    //exec configure-bgp --op add-route/delete-route --rd <rd> --prefix <prefix> --nexthop <nexthop>
    // --mac <mac> --l2vni <l2vni> --l3vni <l3vni>

    @Option(name = "--as-num", description = "as number of the bgp neighbor", required = false, multiValued = false)
    String asNumber = null;

    @Option(name = "--ip", description = "ip of the bgp neighbor", required = false, multiValued = false)
    String ip = null;

    @Option(name = "--tcp-md5-password", description = "RFC2385 TCP MD5 Signature Option shared secret",
            required = false, multiValued = false)
    String md5passwordOption = null;

    @Option(name = "--address-family", description = "address family of the bgp neighbor "
            + "SAFI_IPV4_LABELED_UNICAST|SAFI_MPLS_VPN",
            required = false, multiValued = false)
    String addressFamily = null;

    @Option(name = "--use-source-ip", description = "source ip to be used for neighborship connection establishment",
            required = false, multiValued = false)
    String sourceIp = null;

    @Option(name = "--ebgp-multihops", description = "ebgp multihops of the bgp neighbor",
            required = false, multiValued = false)
    String ebgpMultihops = null;

    @Option(name = "--router-id", description = "router id of the bgp instance",
            required = false, multiValued = false)
    String routerId = null;

    @Option(name = "--rd", description = "rd of the route",
            required = false, multiValued = false)
    String rd = null;

    @Option(name = "--prefix", description = "prefix of the route",
            required = false, multiValued = false)
    String prefix = null;

    @Option(name = "--nexthop", description = "nexthop of the route",
            required = false, multiValued = false)
    String nexthop = null;

    @Option(name = "--mac", description = "mac of the route",
            required = false, multiValued = false)
    String mac = null;

    @Option(name = "--l2vni", description = "l2vni of the route",
            required = false, multiValued = false)
    int l2vni = 0;

    @Option(name = "--l3vni", description = "l3vni",
            required = false, multiValued = false)
    int l3vni = 0;

    @Option(name = "--stalepath-time", description = "the time delay after bgp restart stalepaths are cleaned",
            required = false, multiValued = false)
    String stalePathTime = null;

    @Option(name = "--log-file-path", description = "bgp log file path",
            required = false, multiValued = false)
    String logFile = null;

    @Option(name = "--log-level", description = "log level emergencies,alerts,critical,errors,warnings,notifications,"
            + "informational,debugging",
            required = false, multiValued = false)
    String logLevel = null;

    enum LogLevels {
        emergencies, alerts, critical, errors, warnings, notifications, informational, debugging
    }

    private final BgpConfigurationManager bgpConfigurationManager;

    public ConfigureBgpCli(BgpConfigurationManager bgpConfigurationManager) {
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    @Override
    protected Object doExecute() {
        if (op == null) {
            session.getConsole().println("Please provide valid operation");
            usage();
            session.getConsole().println(
                    "exec configure-bgp -op [start-bgp-server | stop-bgp-server | add-neighbor | delete-neighbor|"
                            + " add-route | delete-route | graceful-restart| enable-log ]");
        }
        switch (op) {
            case "start-bgp-server":
                startBgp();
                break;
            case "stop-bgp-server":
                stopBgp();
                break;
            case "add-neighbor":
                addNeighbor();
                break;
            case "delete-neighbor":
                deleteNeighbor();
                break;
            case "add-route":
                addRoute();
                break;
            case "delete-route":
                deleteRoute();
                break;
            case "graceful-restart":
                configureGR();
                break;
            case "enable-log":
                enableBgpLogLevel();
                break;
            default:
                session.getConsole().println("invalid operation");
                usage();
                session.getConsole().println(
                        "exec configure-bgp -op [start-bgp-server | stop-bgp-server | add-neighbor | "
                                + "delete-neighbor| graceful-restart| enable-log ]");
        }
        return null;
    }

    public boolean validateStalepathTime() {
        try {
            int time = Integer.parseInt(stalePathTime);
            if (time < 30 || time > 3600) {
                session.getConsole().println("invalid stale path time valid range [30-3600]");
                printGracefulRestartHelp();
                return false;
            }
        } catch (NumberFormatException e) {
            session.getConsole().println("invalid stale path time");
            printGracefulRestartHelp();
            return false;
        }
        return true;
    }

    private void configureGR() {
        boolean validStalepathTime = validateStalepathTime();
        if (!validStalepathTime) {
            return;
        }
        bgpConfigurationManager.addGracefulRestart(Integer.parseInt(stalePathTime));
    }

    private void deleteNeighbor() {
        if (ip == null || !validateIp(ip)) {
            session.getConsole().println("invalid neighbor ip");
            printDeleteNeighborHelp();
            return;
        }
        long asNo = getAsNumber(ip);
        if (asNo < 0) {
            session.getConsole().println("neighbor does not exist");
            printDeleteNeighborHelp();
            return;
        }
        bgpConfigurationManager.delNeighbor(ip);
    }

    public long getAsNumber(String nbrIp) {
        Bgp conf = bgpConfigurationManager.getConfig();
        if (conf == null) {
            return -1;
        }
        List<Neighbors> nbrs = conf.getNeighbors();
        if (nbrs == null) {
            return -1;
        }
        for (Neighbors nbr : nbrs) {
            if (nbrIp.equals(nbr.getAddress().getValue())) {
                return nbr.getRemoteAs();
            }
        }
        return -1;
    }

    private void stopBgp() {
        Bgp conf = bgpConfigurationManager.getConfig();
        if (conf == null) {
            return;
        }
        List<Neighbors> nbrs = conf.getNeighbors();
        if (nbrs != null && nbrs.size() > 0) {
            session.getConsole().println(
                    "error: all BGP congiguration must be deleted before stopping the router instance");
            return;
        }
        bgpConfigurationManager.stopBgp();
    }

    private void usage() {
        session.getConsole().println("usage:");
    }

    private void printStartBgpHelp() {
        usage();
        session.getConsole().println(
                "exec configure-bgp -op start-bgp-server --as-num <asnum> --router-id <routerid> [--stalepath-time "
                        + "<time>]");
    }

    private void printAddNeighborHelp() {
        usage();
        session.getConsole().println(
                "exec configure-bgp -op add-neighbor --ip <neighbor-ip> --as-num <as-num> [--address-family <af>] "
                        + "[--tcp-md5-password <password>] "
                        + "[--use-source-ip <sip>] [--ebgp-multihops <em> ]");
    }

    private void printDeleteNeighborHelp() {
        usage();
        session.getConsole().println("exec configure-bgp -op delete-neighbor --ip <neighbor-ip>");
    }

    void printEnableLogHelp() {
        usage();
        session.getConsole().println(
                "exec configure-bgp -op enable-logging --filename <filename> --log-level "
                        + "[emergencies|alerts|critical|errors|warnings|notifications|informational|debugging]");
    }

    private void printGracefulRestartHelp() {
        usage();
        session.getConsole().println("exec configure-bgp -op graceful-restart --stalepath-time <30-3600>");
    }

    private void startBgp() {
        boolean validRouterId = false;

        if (bgpConfigurationManager.getConfig() != null && bgpConfigurationManager.getConfig().getAsId() != null) {
            session.getConsole().println("bgp is already started please use stop-bgp-server and start again");
            return;
        }
        if (!validateAsNumber(asNumber)) {
            printStartBgpHelp();
            return;
        }
        validRouterId = validateIp(routerId);
        if (!validRouterId) {
            session.getConsole().println("invalid router id please supply valid ip address");
            printStartBgpHelp();
            return;
        }

        if (stalePathTime != null) {
            boolean validStalepathTime = validateStalepathTime();
            if (!validStalepathTime) {
                return;
            }
        }
        bgpConfigurationManager.startBgp(Integer.parseInt(asNumber), routerId,
                stalePathTime == null ? 0 : Integer.parseInt(stalePathTime), false);
    }

    protected void addNeighbor() {
        if (!validateAsNumber(asNumber)) {
            printAddNeighborHelp();
            return;
        }

        boolean validIp = validateIp(ip);
        if (!validIp) {
            session.getConsole().println("invalid neighbor ip");
            printAddNeighborHelp();
            return;
        }

        TcpMd5SignaturePasswordType md5secret = null;
        if (md5passwordOption != null) {
            try {
                md5secret = new TcpMd5SignaturePasswordType(md5passwordOption);
            } catch (IllegalArgumentException e) {
                session.getConsole().println(
                        new StringBuilder("invalid MD5 password: ").append(e.getMessage()).toString());
                printAddNeighborHelp();
                return;
            }
        }

        if (sourceIp != null) {
            validIp = validateIp(sourceIp);
            if (!validIp) {
                session.getConsole().println("invalid source ip");
                printAddNeighborHelp();
                return;
            }
        }

        if (ebgpMultihops != null) {
            try {
                long val = Long.parseLong(ebgpMultihops);
                if (val < 1 || val > 255) {
                    session.getConsole().println("invalid ebgpMultihops number , valid range [1,255] ");
                    printAddNeighborHelp();
                    return;
                }
            } catch (NumberFormatException e) {
                session.getConsole().println("invalid ebgpMultihops number, valid range [1-255]");
                printAddNeighborHelp();
                return;
            }
        }
        if (addressFamily != null) {
            if (!addressFamily.equals("lu") && !addressFamily.equals("vpnv4")
                    && !addressFamily.equals("vpnv6")
                    && !addressFamily.equals("evpn")) {
                session.getConsole().println("error: Address family must be lu/evpn/vpnv4/vpnv6 ");
                return;
            }

            af_afi afi ;
            af_safi safi ;
            if (addressFamily.equals("vpnv6")) {
                afi = af_afi.findByValue(2);
                safi = af_safi.findByValue(5);
            } else if (addressFamily.equals("evpn")) {
                afi = af_afi.findByValue(3);
                safi = af_safi.findByValue(6);
            } else if (addressFamily.equals("lu")) {
                afi = af_afi.findByValue(1);
                safi = af_safi.findByValue(4);
            } else if  (addressFamily.equals("vpnv4")) {
                afi = af_afi.findByValue(1);
                safi = af_safi.findByValue(5);
            } else {
                session.getConsole().println(
                        "invalid addressFamily valid values SAFI_IPV4_LABELED_UNICAST | SAFI_MPLS_VPN");
                printAddNeighborHelp();
                return ;
            }
            bgpManager.addAddressFamily(ip, afi, safi);

        }
        if (getAsNumber(ip) != -1) {
            session.getConsole().println("neighbor with ip " + ip + " already exists");
            return;
        }
        bgpConfigurationManager.addNeighbor(ip, Long.parseLong(asNumber), md5secret);
        if (addressFamily != null) {
            bgpConfigurationManager.addAddressFamily(ip, af_afi.AFI_IP.getValue(),
                    af_safi.valueOf(addressFamily).getValue());
        }
        if (ebgpMultihops != null) {
            bgpConfigurationManager.addEbgpMultihop(ip, Integer.parseInt(ebgpMultihops));
        }
        if (sourceIp != null) {
            bgpConfigurationManager.addUpdateSource(ip, sourceIp);
        }
    }

    protected void addRoute() {
        bgpConfigurationManager.onUpdatePushRoute(protocol_type.PROTOCOL_EVPN, rd, prefix,
                0, nexthop, mac, l3vni, l2vni, null, null);
    }

    protected void deleteRoute() {
        bgpConfigurationManager.onUpdateWithdrawRoute(protocol_type.PROTOCOL_EVPN, rd, prefix,
                0, nexthop, mac);
    }

    private boolean validateIp(String inputIp) {
        boolean validIp = false;
        try {
            if (inputIp != null) {
                InetAddress addr = InetAddress.getByName(inputIp);
                if (addr.isMulticastAddress()) {
                    session.getConsole().println("ip cannot be multicast address");
                    return false;
                }
                if (addr.isLoopbackAddress()) {
                    session.getConsole().println("ip cannot be loopback address");
                    return false;
                }
                byte[] addrBytes = addr.getAddress();
                int lastPart = addrBytes[3] & 0xFF;
                int firstPart = addrBytes[0] & 0xFF;
                if (firstPart == 0) {
                    return false;//cannot start with 0 "0.1.2.3"
                }
                if (lastPart == 0 || lastPart == 255) {
                    return false;
                }
                validIp = true;
            }
        } catch (UnknownHostException e) {
            // Ignored?
        }
        return validIp;
    }

    private void enableBgpLogLevel() {
        if (logFile == null) {
            session.getConsole().println("Please provide log file name ");
            usage();
            session.getConsole().println(
                    "exec configure-bgp -op enable-log --log-file-path <logfile> --log-level <level>");
            return;
        }
        boolean validLoglevel = false;
        try {
            LogLevels.valueOf(logLevel);
            validLoglevel = true;
        } catch (IllegalArgumentException e) {
            // Ignored?
        }
        if (!validLoglevel) {
            session.getConsole().println(
                    "Please provide valid log level "
                            + "emergencies|alerts|critical|errors|warnings|notifications|informational|debugging");
            usage();
            session.getConsole().println(
                    "exec configure-bgp -op enable-log --log-file-path <logfile> --log-level <level>");
            return;
        }
        bgpConfigurationManager.addLogging(logFile, logLevel);
    }

    private boolean validateAsNumber(String strAsnum) {

        try {
            long asNum = Long.parseLong(strAsnum);
            if (asNum == 0L || asNum == 65535L || asNum == 23456L) {
                session.getConsole().println("reserved AS Number supplied ");
                return false;
            }
            if (asNum <= AS_MIN || asNum > AS_MAX) {
                session.getConsole().println("invalid AS Number , supported range [1," + AS_MAX + "]");
                return false;
            }
        } catch (NumberFormatException e) {
            session.getConsole().println("invalid AS Number ");
            return false;
        }
        return true;
    }
}
