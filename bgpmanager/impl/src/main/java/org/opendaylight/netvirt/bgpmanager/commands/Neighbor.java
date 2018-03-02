/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.TcpMd5SignaturePasswordType;

@Command(scope = "odl", name = "bgp-nbr",
        description = "Add or delete BGP neighbor")
public class Neighbor extends OsgiCommandSupport {
    private static final String IP = "--ip-address";
    private static final String AS = "--as-number";
    private static final String MH = "--ebgp-multihop";
    private static final String US = "--update-source";
    private static final String AF = "--address-family";
    private static final String MP = "--tcp-md5-password";

    private static final String USAGE = new StringBuilder("usage: bgp-nbr")
            .append(" [").append(IP).append(" nbr-ip-address]")
            .append(" [").append(AS).append(" asnum]")
            .append(" [").append(MP).append(" md5-shared-secret]")
            .append(" [").append(MH).append(" hops]")
            .append(" [").append(US).append(" source]")
            .append(" [").append(AF).append(" lu/evpn/vpnv4/vpnv6]")
            .append(" <add|del>").toString();

    @Argument(index = 0, name = "add|del", description = "The desired operation",
            required = true, multiValued = false)
    String action = null;

    @Option(name = IP, aliases = {"-i"},
            description = "Neighbor's IP address",
            required = false, multiValued = false)
    String nbrIp = null;

    @Option(name = AS, aliases = {"-a"},
            description = "AS number",
            required = false, multiValued = false)
    String asNum = null;

    @Option(name = MP, aliases = {"-p"},
            description = "TCP MD5 Signature Option shared secret",
            required = false, multiValued = false)
    String md5PasswordOption = null;

    @Option(name = MH, aliases = {"-e"},
            description = "EBGP-multihop hops",
            required = false, multiValued = false)
    String multiHops = null;

    @Option(name = US, aliases = {"-u"},
            description = "Update source address",
            required = false, multiValued = false)
    String srcIp = null;

    @Option(name = AF, aliases = {"-f"},
            description = "Address family",
            required = false, multiValued = false)
    String addrFamily = null;

    private final BgpConfigurationManager bgpConfigurationManager;

    public Neighbor(BgpConfigurationManager bgpConfigurationManager) {
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    private Object usage() {
        session.getConsole().println(USAGE);
        return null;
    }

    @Override
    protected Object doExecute() {
        switch (action) {
            case "add":
                if (nbrIp == null) {
                    session.getConsole().println("error: " + IP + " needed");
                    return null;
                }
                if (bgpConfigurationManager.getConfig() == null) {
                    session.getConsole().println("error: Bgp config is not present");
                    return null;
                }
                long asn = 0;
                int hops = 0;
                if (!Commands.isValid(session.getConsole(), nbrIp, Commands.Validators.IPADDR, IP)) {
                    return null;
                }
                if (asNum != null) {
                    if (!Commands.isValid(session.getConsole(), asNum, Commands.Validators.ASNUM, AS)) {
                        return null;
                    }
                    asn = Long.parseLong(asNum);

                }
                TcpMd5SignaturePasswordType md5Secret = null;
                if (md5PasswordOption != null) {
                    try {
                        md5Secret = new TcpMd5SignaturePasswordType(md5PasswordOption);
                    } catch (IllegalArgumentException e) {
                        session.getConsole().println(
                                new StringBuilder("error: invalid MD5 password ").append(e.getMessage()).toString());
                        return null;
                    }
                }
                bgpConfigurationManager.addNeighbor(nbrIp, asn, md5Secret);

                if (multiHops != null) {
                    if (!Commands.isValid(session.getConsole(), multiHops, Commands.Validators.INT, MH)) {
                        return null;
                    } else {
                        hops = Integer.parseInt(multiHops);
                    }
                    bgpConfigurationManager.addEbgpMultihop(nbrIp, hops);
                }
                if (srcIp != null) {
                    if (!Commands.isValid(session.getConsole(), srcIp, Commands.Validators.IPADDR, US)) {
                        return null;
                    }
                    bgpConfigurationManager.addUpdateSource(nbrIp, srcIp);
                }
                if (addrFamily != null) {
                    if (!addrFamily.equals("lu") && !addrFamily.equals("vpnv4")
                            && !addrFamily.equals("vpnv6")
                            && !addrFamily.equals("evpn")) {
                        session.getConsole().println("error: " + AF + " must be lu/evpn/vpnv4/vpnv6 ");
                        return null;
                    }
                    af_afi afi;
                    af_safi safi;
                    switch (addrFamily) {
                        case "vpnv6":
                            afi = af_afi.findByValue(2);
                            safi = af_safi.findByValue(5);
                            break;
                        case "evpn":
                            afi = af_afi.findByValue(3);
                            safi = af_safi.findByValue(6);
                            break;
                        case "lu":
                            afi = af_afi.findByValue(1);
                            safi = af_safi.findByValue(4);
                            break;
                        default:  // vpnv4
                            afi = af_afi.findByValue(1);
                            safi = af_safi.findByValue(5);
                            break;
                    }
                    if (afi != null && safi != null) {
                        bgpConfigurationManager.addAddressFamily(nbrIp, afi.getValue(), safi.getValue());
                    }
                }
                break;
            case "del":
                if (nbrIp == null) {
                    session.getConsole().println("error: " + IP + " needed");
                    return null;
                }
                if (!Commands.isValid(session.getConsole(), nbrIp, Commands.Validators.IPADDR, IP)) {
                    return null;
                }
                if (asNum != null || multiHops != null || srcIp != null
                        || addrFamily != null) {
                    session.getConsole().println("note: some option(s) not needed; ignored");
                }
                bgpConfigurationManager.delNeighbor(nbrIp);
                break;
            default:
                return usage();
        }
        return null;
    }
}
