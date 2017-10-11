/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service.shell;

import java.util.List;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.infrautils.utils.TablePrinter;
import org.opendaylight.netvirt.ipv6service.IfMgr;
import org.opendaylight.netvirt.ipv6service.VirtualNetwork;
import org.opendaylight.netvirt.ipv6service.VirtualPort;
import org.opendaylight.netvirt.ipv6service.VirtualRouter;
import org.opendaylight.netvirt.ipv6service.VirtualSubnet;
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
        TablePrinter tp = new TablePrinter();

        if (listResource != null) {
            if (listResource.equalsIgnoreCase("networks")
                    || (listResource.equalsIgnoreCase("net"))) {
                tp.setTitle("Network Cache List");
                tp.setColumnNames("Sno", "NetworkId", "dpnId");
                int count = 1;
                List<VirtualNetwork> vnetworks = ifMgr.getNetworkCache();
                for (VirtualNetwork vnet: vnetworks) {
                    tp.addRow(count++, String.valueOf(vnet.getNetworkUuid().getValue()), vnet.getDpnsHostingNetwork());
                }
                session.getConsole().print(tp.toString());
            } else if (listResource.equalsIgnoreCase("subnets")
                    || (listResource.equalsIgnoreCase("subnet"))) {
                tp.setTitle("Subnet Cache List");
                tp.setColumnNames("Sno", "SubnetId", "SubnetCIDR", "ipVersion");
                int count = 1;
                List<VirtualSubnet> vsubnets = ifMgr.getSubnetCache();
                for (VirtualSubnet vsubnet : vsubnets) {
                    tp.addRow(count++,   String.valueOf(vsubnet.getSubnetUUID().getValue()),
                            String.valueOf(vsubnet.getSubnetCidr().getValue()),
                            vsubnet.getIpVersion());
                }
                session.getConsole().print(tp.toString());
            } else if (listResource.equalsIgnoreCase("routers")
                    || (listResource.equalsIgnoreCase("router"))) {
                tp.setTitle("Router Cache List");
                tp.setColumnNames("Sno", "RouterId");
                List<VirtualRouter> vrouters = ifMgr.getRouterCache();
                int count = 1;
                for (VirtualRouter vrouter : vrouters) {
                    tp.addRow(count++, String.valueOf(vrouter.getRouterUUID().getValue()));
                }
                session.getConsole().print(tp.toString());
            }
        } else {
            tp.setTitle("Interface Cache List");
            tp.setColumnNames("Sno", "PortId", "Mac Address", "Owner", "dpnId", "FixedIPs");
            List<VirtualPort> vports = ifMgr.getInterfaceCache();
            int count = 1;
            for (VirtualPort vport: vports) {
                String str = vport.getDeviceOwner();
                tp.addRow(count++, String.valueOf(vport.getIntfUUID().getValue()), vport.getMacAddress(),
                        (str.startsWith("network:")) ? str.substring(str.lastIndexOf(':') + 1) : "compute",
                        vport.getDpId(), getPortIpv6Addresses(vport));
            }
            session.getConsole().print(tp.toString());
        }
        return null;
    }
}
