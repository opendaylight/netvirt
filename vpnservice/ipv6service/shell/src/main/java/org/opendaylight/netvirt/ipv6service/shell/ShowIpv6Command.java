/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service.shell;

import java.math.BigInteger;
import java.util.List;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.ipv6service.IfMgr;
import org.opendaylight.netvirt.ipv6service.VirtualNetwork;
import org.opendaylight.netvirt.ipv6service.VirtualPort;
import org.opendaylight.netvirt.ipv6service.VirtualRouter;
import org.opendaylight.netvirt.ipv6service.VirtualSubnet;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;

@Command(scope = "ipv6service", name = "ipv6CacheShow", description = "Displays the IPv6Service Cache info")
public class ShowIpv6Command extends OsgiCommandSupport {
    private IfMgr ifMgr = null;

    @Argument(name = "resource", description = "List the various resource specific cache, where resource "
            + "could be <networks/subnets/routers>", required = false, multiValued = false)
    private final String listResource = null;

    private String getPortIpv6Addresses(VirtualPort vport) {
        List<Ipv6Address> ipv6Addresses = vport.getIpv6Addresses();
        StringBuffer str = new StringBuffer();
        for (Ipv6Address ipaddr: ipv6Addresses) {
            str.append(ipaddr.getValue());
            str.append("  ");
        }
        return str.toString();
    }

    @Override
    protected Object doExecute() throws Exception {
        ifMgr = IfMgr.getIfMgrInstance();

        if (listResource != null) {
            if (listResource.equalsIgnoreCase("networks")
                    || (listResource.equalsIgnoreCase("net"))) {
                session.getConsole().println("+----------------------------------------------------------------------"
                        + "--------------------+");
                List<VirtualNetwork> vnetworks = ifMgr.getNetworkCache();
                int count = 1;
                session.getConsole().println("|Sno | NetworkId                            |                         "
                        + "                     |");
                session.getConsole().println("+----------------------------------------------------------------------"
                        + "--------------------+");
                for (VirtualNetwork vnet: vnetworks) {
                    session.getConsole().println(String.format("|%-3d | %36s | dpnId              | IPAM Flows"
                                    + " Programmed?  |", count++,
                            String.valueOf(vnet.getNetworkUuid().getValue())));
                    List<BigInteger> dpnIdList = vnet.getDpnsHostingNetwork();
                    for (BigInteger dpnId : dpnIdList) {
                        session.getConsole().println(String.format("|                                           | %-18s"
                                        + " | %2s                      |", dpnId,
                                ((vnet.getRSPuntFlowStatusOnDpnId(dpnId)) == Ipv6Constants.FLOWS_NOT_CONFIGURED)
                                        ? "No" : "Yes"));
                    }
                    session.getConsole().println("+-----------------------------------------------------------------"
                            + "-------------------------+");
                }
            } else if (listResource.equalsIgnoreCase("subnets")
                    || (listResource.equalsIgnoreCase("subnet"))) {
                session.getConsole().println("+----------------------------------------------------------------------"
                        + "-----------------+");
                session.getConsole().println("|Sno | SubnetId                             | SubnetCIDR               "
                        + "    | ipVersion  |");
                session.getConsole().println("+----------------------------------------------------------------------"
                        + "-----------------+");
                int count = 1;
                List<VirtualSubnet> vsubnets = ifMgr.getSubnetCache();
                for (VirtualSubnet vsubnet : vsubnets) {
                    session.getConsole().println(String.format("|%-3d | %36s | %-10s           | %s       |",
                            count++,
                            String.valueOf(vsubnet.getSubnetUUID().getValue()),
                            String.valueOf(vsubnet.getSubnetCidr().getValue()),
                            vsubnet.getIpVersion()));
                }
                session.getConsole().println("+----------------------------------------------------------------------"
                        + "-----------------+");
            } else if (listResource.equalsIgnoreCase("routers")
                    || (listResource.equalsIgnoreCase("router"))) {
                session.getConsole().println("+-------------------------------------------+");
                session.getConsole().println("|Sno | RouterID                             |");
                session.getConsole().println("+-------------------------------------------+");
                List<VirtualRouter> vrouters = ifMgr.getRouterCache();
                int count = 1;
                for (VirtualRouter vrouter : vrouters) {
                    session.getConsole().println(String.format("|%-3d | %36s |",
                            count++,
                            String.valueOf(vrouter.getRouterUUID().getValue())));
                }
                session.getConsole().println("+-------------------------------------------+");
            }
        } else {
            session.getConsole().println("+----------------------------------------------------------------------"
                    + "------------------------------------------------------------------------------------------+");
            session.getConsole().println("|Sno | PortID                               | macAddress        | Owner"
                    + "            | dpnID           | FixedIps                                                  |");
            session.getConsole().println("+----------------------------------------------------------------------"
                    + "------------------------------------------------------------------------------------------+");
            List<VirtualPort> vports = ifMgr.getInterfaceCache();
            int count = 1;
            for (VirtualPort vport: vports) {
                String str = vport.getDeviceOwner();
                session.getConsole().println(String.format("|%-3d | %36s | %-17s | %-16s | %-15s | %-57s |",
                        count++,
                        String.valueOf(vport.getIntfUUID().getValue()),
                        vport.getMacAddress(),
                        (str.startsWith("network:")) ? str.substring(str.lastIndexOf(':') + 1) : "compute",
                        vport.getDpId(), getPortIpv6Addresses(vport)));
            }
            session.getConsole().println("+----------------------------------------------------------------------"
                    + "------------------------------------------------------------------------------------------+");
        }
        return null;
    }
}
