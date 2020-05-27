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
import java.util.Map;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.AsId;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.GracefulRestart;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Logging;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.multipathcontainer.Multipath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.multipathcontainer.MultipathKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighborscontainer.Neighbors;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighborscontainer.NeighborsKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighborscontainer.neighbors.AddressFamilies;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighborscontainer.neighbors.AddressFamiliesKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighborscontainer.neighbors.EbgpMultihop;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighborscontainer.neighbors.UpdateSource;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.networkscontainer.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.networkscontainer.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfmaxpathcontainer.VrfMaxpath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfmaxpathcontainer.VrfMaxpathKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfscontainer.Vrfs;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfscontainer.VrfsKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfscontainer.vrfs.AddressFamiliesVrf;
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

    private final BgpManager bgpManager;

    public Cache(BgpManager bgpManager) {
        this.bgpManager = bgpManager;
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

    @SuppressWarnings({"resource", "checkstyle:RegexpSinglelineJava"})
    @Override
    protected Object doExecute() {
        boolean listVrfs = false;
        boolean listNets = false;
        PrintStream ps = System.out;

        if (action != null) {
            return usage();
        }

        PrintStream fileStream = null;
        try {
            if (ofile != null) {
                try {
                    fileStream = new PrintStream(ofile);
                    ps = fileStream;
                } catch (FileNotFoundException e) {
                    System.out.println("error: cannot create file " + ofile + "; exception: " + e);
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
                            System.out.println("error: unknown value for " + LST + ": " + item);
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
            String configHost = bgpManager.getConfigHost();
            int configPort = bgpManager.getConfigPort();
            ps.printf("%nConfiguration Server%n\t%s  %s%n\t%s  %d%n",
                    HTSTR, configHost, PTSTR, configPort);
            Bgp config = bgpManager.getConfig();
            if (config == null) {
                if (fileStream != null) {
                    fileStream.close();
                }

                return null;
            }
            AsId asId = config.getAsId();
            if (asId != null) {
                Long asNum = asId.getLocalAs().longValue();
                IpAddress routerId = asId.getRouterId();
                Long spt = asId.getStalepathTime().toJava();
                Boolean afb = asId.isAnnounceFbit();
                String rid = routerId == null ? "<n/a>" : routerId.stringValue();
                //F-bit is always set to ON (hardcoded), in SDN even though the controller is down
                //forwarding state shall be retained.
                String bit = "ON";

                GracefulRestart gracefulRestart = config.getGracefulRestart();
                if (gracefulRestart != null) {
                    spt = gracefulRestart.getStalepathTime().toJava();
                }
                ps.printf("%nBGP Router%n");
                ps.printf("\t%-15s  %s%n\t%-15s  %s%n\t%-15s  %s%n\t%-15s  %s%n",
                        ASSTR, asNum.toString(), RISTR, rid, SPSTR,
                        spt == null || spt == 0 ? "default" : spt.toString(), FBSTR, bit);
            }

            Logging logging = config.getLogging();
            if (logging != null) {
                ps.printf("\t%-15s  %s%n\t%-15s  %s%n", LFSTR, logging.getFile(),
                        LLSTR, logging.getLevel());
            }

            Map<NeighborsKey, Neighbors> keyNeighborsMap = (config.getNeighborsContainer() ==  null) ? null
                    : config.getNeighborsContainer().getNeighbors();
            if (keyNeighborsMap != null) {
                ps.printf("%nNeighbors%n");
                for (Neighbors nbr : keyNeighborsMap.values()) {
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
                    Map<AddressFamiliesKey, AddressFamilies> keyAddressFamiliesMap = nbr.getAddressFamilies();
                    if (keyAddressFamiliesMap != null) {
                        for (AddressFamilies af : keyAddressFamiliesMap.values()) {
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
                Map<VrfsKey, Vrfs> keyVrfsMap
                        = (config.getVrfsContainer() == null) ? null : config.getVrfsContainer().getVrfs();
                if (keyVrfsMap != null) {
                    ps.printf("%nVRFs%n");
                    for (Vrfs vrf : keyVrfsMap.values()) {
                        ps.printf("\t%s%n", vrf.getRd());
                        ps.printf("\t\t%s  ", IRSTR);
                        for (String rt : vrf.getImportRts()) {
                            ps.printf("%s ", rt);
                        }
                        ps.printf("%n\t\t%s  ", ERSTR);
                        for (String rt : vrf.getExportRts()) {
                            ps.printf("%s ", rt);
                        }
                        for (AddressFamiliesVrf adf : vrf.getAddressFamiliesVrf().values()) {
                            ps.printf("%n\t\tafi %d safi %d", adf.getAfi(), adf.getSafi());
                        }
                        ps.printf("%n");
                    }
                }
            }

            if (listNets) {
                Map<NetworksKey, Networks> keyNetworksMap = (config.getNetworksContainer() == null) ? null
                        : config.getNetworksContainer().getNetworks();
                if (keyNetworksMap != null) {
                    ps.printf("%nNetworks%n");
                    for (Networks net : keyNetworksMap.values()) {
                        String rd = net.getRd();
                        String pfxlen = net.getPrefixLen();
                        String nh = net.getNexthop().getValue();
                        int label = net.getLabel().intValue();
                        ps.printf("\t%s%n\t\t%-7s  %s%n\t\t%-7s  %s%n\t\t%-7s  %d%n",
                                pfxlen, RDSTR, rd, NHSTR, nh, LBSTR, label);
                    }
                }
            }

            Map<MultipathKey, Multipath> keyMultipathMap = config.getMultipathContainer() == null ? null
                    : config.getMultipathContainer().getMultipath();
            Map<VrfMaxpathKey, VrfMaxpath> keyVrfMaxpathMap = config.getVrfMaxpathContainer() == null ? null
                    : config.getVrfMaxpathContainer().getVrfMaxpath();
            if (keyMultipathMap != null) {
                ps.printf("%nMultipath%n");
                for (Multipath multipath : keyMultipathMap.values()) {
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
                        if (keyVrfMaxpathMap != null) {
                            ps.printf("\t%-16s  %s%n", RDSTR, MPSTR);
                            for (VrfMaxpath vrfMaxpath : keyVrfMaxpathMap.values()) {
                                String rd = vrfMaxpath.getRd();
                                int maxpath = vrfMaxpath.getMaxpaths().toJava();
                                ps.printf("\t%-16s  %d%n", rd, maxpath);
                            }
                        }
                    }
                }
            }
        } finally {
            if (fileStream != null) {
                fileStream.close();
            }
        }
        return null;
    }
}
