/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.shell;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.dhcpservice.api.IDhcpExternalTunnelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "dhcp", name = "show-cache", description = "Displays dhcp cache")
public class DhcpCacheCli extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpCacheCli.class);
    private IDhcpExternalTunnelManager dhcpExternalTunnelManager;

    public void setDhcpExternalTunnelManager(IDhcpExternalTunnelManager dhcpExternalTunnelManager) {
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
    }

    @Override
    protected Object doExecute() throws Exception {
        session.getConsole().println("Printing Designated Dpns To TunnelIp and ElanName cache "
                + "(DesignatedDpnsToTunnelIpElanNameCache)");
        dhcpExternalTunnelManager.getDesignatedDpnsToTunnelIpElanNameCache().forEach(
            (dpnId, tunnelIpAndElanName) -> {
                session.getConsole().println("   DPN id : " + print(dpnId));
                tunnelIpAndElanName.forEach(item -> {
                    session.getConsole().println("       Ip address : " + print(item.getLeft()));
                    session.getConsole().println("       Elan name : " + print(item.getRight()));
                });
            }
        );
        session.getConsole().println();
        session.getConsole().println("Printing TunnelIp and ElanName To VmMac Cache "
                + "(tunnelIpElanNameToVmMacCache)");
        dhcpExternalTunnelManager.getTunnelIpElanNameToVmMacCache().forEach(
            (tunnelIpAndElanName, vmMac) -> {
                session.getConsole().println("   Tunnel Ip Address : " + print(tunnelIpAndElanName.getLeft()));
                session.getConsole().println("   Elan Name : " + print(tunnelIpAndElanName));
                vmMac.forEach(item -> session.getConsole().println("        VM Macs : " + print(item)));
            }
        );
        session.getConsole().println();
        session.getConsole().println("Printing available Vm Cache (availableVMCache)");
        dhcpExternalTunnelManager.getAvailableVMCache().forEach(
            (tunnelIpAndElanName, availableVm) -> {
                session.getConsole().println("   Tunnel Ip Address : " + print(tunnelIpAndElanName.getLeft()));
                session.getConsole().println("   Elan Name : " + print(tunnelIpAndElanName.getRight()));
                availableVm.forEach(item -> session.getConsole().println("        VM Macs : " + print(item)));
            }
        );
        session.getConsole().println();
        session.getConsole().println("Vni and Mac addresses to Port cache (VniMacAddressToPortCache)");
        dhcpExternalTunnelManager.getVniMacAddressToPortCache().forEach(
            (vniAndMac, port) -> {
                session.getConsole().println("   VNI : " + print(vniAndMac.getLeft()));
                session.getConsole().println("   Mac address : " + print(vniAndMac.getRight()));
                session.getConsole().println("   Port : " + print(port));
            }
        );
        return null;
    }

    Object print(Object input) {
        return input != null ? input : " ";
    }
}
