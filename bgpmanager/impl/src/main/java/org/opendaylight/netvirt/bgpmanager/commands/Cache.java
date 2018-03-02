/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.commands;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.AsId;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.GracefulRestart;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Logging;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Multipath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfMaxpath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Vrfs;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.AddressFamilies;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.EbgpMultihop;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.UpdateSource;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfs.AddressFamiliesVrf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;

@Command(scope = "odl", name = "bgp-cache",
        description = "Text dump of BGP config cache")
@SuppressFBWarnings("DM_DEFAULT_ENCODING")
public class Cache extends OsgiCommandSupport {
    private static final String LST = "--list";
    private static final String OFL = "--out-file";

    @Argument(name = "dummy", description = "Argument not needed",
            required = false, multiValued = false)
    private final String action = null;

    @Option(name = LST, aliases = {"-l"},
            description = "list vrfs and/or networks",
            required = false, multiValued = true)
    private final List<String> list = null;

    @Option(name = OFL, aliases = {"-o"},
            description = "output file",
            required = false, multiValued = false)
    private final String ofile = null;

    private static final String HTSTR = "Host";
    private static final String PTSTR = "Port";
    private static final String ASSTR = "AS-Number";
    private static final String RISTR = "Router-ID";
    private static final String SPSTR = "Stale-Path-Time";
    private static final String FBSTR = "F-bit";
    private static final String LFSTR = "Log-File";
    private static final String LLSTR = "Log-Level";
    private static final String USSTR = "Update-Source";
    private static final String EBSTR = "EBGP-Multihops";
    private static final String AFSTR = "Address-Families";
    private static final String ERSTR = "Export-RTs";
    private static final String IRSTR = "Import-RTs";
    private static final String NHSTR = "Nexthop";
    private static final String LBSTR = "Label";
    private static final String RDSTR = "RD";
    private static final String MPSTR = "Maxpath";

    private final BgpConfigurationManager bgpConfigurationManager;

    public Cache(BgpConfigurationManager bgpConfigurationManager) {
        this.bgpConfigurationManager = bgpConfigurationManager;
    }

    private Object usage() {
        session.getConsole().println("usage: bgp-cache [" + LST + " vrfs | networks] [" + OFL + " file-name]");
        return null;
    }

    public Object show(CommandSession session) {
        this.session = session;
        return doExecute();
    }

    public Object show() {
        return doExecute();
    }

    @SuppressWarnings("resource")
    @Override
    protected Object doExecute() {
        boolean listVrfs = false;
        boolean listNets = false;
        PrintStream ps = session.getConsole();

        if (action != null) {
            return usage();
        }

        PrintStream fileStream = null;
        if (ofile != null) {
            try {
                fileStream = new PrintStream(ofile);
                ps = fileStream;
            } catch (FileNotFoundException e) {
                session.getConsole().println("error: cannot create file " + ofile + "; exception: " + e);
                return null;
            }
        }
        if (list != null) {
            for (String item : list) {
                switch (item) {
                    case "vrfs":
                        listVrfs = true;
                        break;
                    case "networks":
                        listNets = true;
                        break;
                    default:
                        session.getConsole().println("error: unknown value for " + LST + ": " + item);
                        if (fileStream != null) {
                            fileStream.close();
                        }
                        return null;
                }
            }
        }
        // we'd normally read this directly from 'config' but
        // legacy behaviour forces to check for a connection
        // that's initiated by default at startup without
        // writing to config.
        String configHost = bgpConfigurationManager.getConfigHost();
        int configPort = bgpConfigurationManager.getConfigPort();
        ps.printf("%nConfiguration Server%n\t%s  %s%n\t%s  %d%n",
                HTSTR, configHost, PTSTR, configPort);
        Bgp config = bgpConfigurationManager.getConfig();
        if (config == null) {
            if (fileStream != null) {
                fileStream.close();
            }

            return null;
        }
        AsId asId = config.getAsId();
        if (asId != null) {
            long asNum = asId.getLocalAs().longValue();
            IpAddress routerId = asId.getRouterId();
            Long spt = asId.getStalepathTime();
            Boolean afb = asId.isAnnounceFbit();
            String rid = routerId == null ? "<n/a>" : new String(routerId.getValue());
            //F-bit is always set to ON (hardcoded), in SDN even though the controller is down
            //forwarding state shall be retained.
            String bit = "ON";

            GracefulRestart gracefulRestart = config.getGracefulRestart();
            if (gracefulRestart != null) {
                spt = gracefulRestart.getStalepathTime();
            }
            ps.printf("%nBGP Router%n");
            ps.printf("\t%-15s  %d%n\t%-15s  %s%n\t%-15s  %s%n\t%-15s  %s%n",
                    ASSTR, asNum, RISTR, rid, SPSTR, spt == null || spt == 0 ? "default" : spt.toString(), FBSTR,
                    bit);
        }

        Logging logging = config.getLogging();
        if (logging != null) {
            ps.printf("\t%-15s  %s%n\t%-15s  %s%n", LFSTR, logging.getFile(),
                    LLSTR, logging.getLevel());
        }

        List<Neighbors> neighbors = config.getNeighbors();
        if (neighbors != null) {
            ps.printf("%nNeighbors%n");
            for (Neighbors nbr : neighbors) {
                ps.printf("\t%s%n\t\t%-16s  %d%n", nbr.getAddress().getValue(),
                        ASSTR, nbr.getRemoteAs());
                EbgpMultihop en = nbr.getEbgpMultihop();
                if (en != null) {
                    ps.printf("\t\t%-16s  %d%n", EBSTR, en.getNhops().intValue());
                }
                UpdateSource us = nbr.getUpdateSource();
                if (us != null) {
                    ps.printf("\t\t%-16s  %s%n", USSTR, us.getSourceIp().getValue());
                }
                ps.printf("\t\t%-16s  IPv4-Labeled-VPN", AFSTR);
                List<AddressFamilies> afs = nbr.getAddressFamilies();
                if (afs != null) {
                    for (AddressFamilies af : afs) {
                         // Should not print "unknown" in vpnv4 case
                        if (!(af.getSafi().intValue() == 5 && af.getAfi().intValue() == 1)) {
                            if (af.getSafi().intValue() == 4 && af.getAfi().intValue() == 1) {
                                ps.printf(" %s", "IPv4-Labeled-Unicast");
                            } else if (af.getSafi().intValue() == 5 && af.getAfi().intValue() == 2) {
                                ps.printf(" %s", "IPv6-Labeled-VPN");
                            } else if (af.getSafi().intValue() == 6) {
                                ps.printf(" %s", "Ethernet-VPN");
                            }  else {
                                ps.printf(" %s", "Unknown");
                            }
                        }
                    }
                }
                ps.printf("%n");
            }
        }

        if (listVrfs) {
            List<Vrfs> vrfs = config.getVrfs();
            if (vrfs != null) {
                ps.printf("%nVRFs%n");
                for (Vrfs vrf : vrfs) {
                    ps.printf("\t%s%n", vrf.getRd());
                    ps.printf("\t\t%s  ", IRSTR);
                    for (String rt : vrf.getImportRts()) {
                        ps.printf("%s ", rt);
                    }
                    ps.printf("%n\t\t%s  ", ERSTR);
                    for (String rt : vrf.getExportRts()) {
                        ps.printf("%s ", rt);
                    }
                    for (AddressFamiliesVrf adf : vrf.getAddressFamiliesVrf()) {
                        ps.printf("%n\t\tafi %d safi %d", adf.getAfi(), adf.getSafi());
                    }
                    ps.printf("%n");
                }
            }
        }

        if (listNets) {
            List<Networks> ln = config.getNetworks();
            if (ln != null) {
                ps.printf("%nNetworks%n");
                for (Networks net : ln) {
                    String rd = net.getRd();
                    String pfxlen = net.getPrefixLen();
                    String nh = net.getNexthop().getValue();
                    int label = net.getLabel().intValue();
                    ps.printf("\t%s%n\t\t%-7s  %s%n\t\t%-7s  %s%n\t\t%-7s  %d%n",
                            pfxlen, RDSTR, rd, NHSTR, nh, LBSTR, label);
                }
            }
        }

        List<Multipath> mp = config.getMultipath();
        List<VrfMaxpath> vrfm = config.getVrfMaxpath();
        if (mp != null) {
            ps.printf("%nMultipath%n");
            for (Multipath multipath : mp) {
                int afi = multipath.getAfi().intValue();
                int safi = multipath.getSafi().intValue();
                Boolean enabled = multipath.isMultipathEnabled();
                if (enabled) {
                    if (afi == 1 && safi == 5) {
                        ps.printf("\t%-16s  %s%n%n", AFSTR, "vpnv4");
                    } else if (afi == 2 && safi == 5) {
                        ps.printf("\t%-16s  %s%n%n", AFSTR, "vpnv6");
                    } else if (afi == 3 && safi == 6) {
                        ps.printf("\t%-16s  %s%n%n", AFSTR, "evpn");
                    } else {
                        ps.printf("\t%-16s  %s%n%n", AFSTR, "Unknown");
                    }
                    if (vrfm != null) {
                        ps.printf("\t%-16s  %s%n", RDSTR, MPSTR);
                        for (VrfMaxpath vrfMaxpath : vrfm) {
                            String rd = vrfMaxpath.getRd();
                            int maxpath = vrfMaxpath.getMaxpaths();
                            ps.printf("\t%-16s  %d%n", rd, maxpath);
                        }
                    }
                }
            }
        }
        if (fileStream != null) {
            fileStream.close();
        }
        return null;
    }
}
