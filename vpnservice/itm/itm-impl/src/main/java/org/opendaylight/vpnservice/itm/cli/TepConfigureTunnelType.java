/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Command(scope = "tep", name = "configure-tunnelType",
        description = "configuring the tunnel type for a transport zone")
public class TepConfigureTunnelType extends OsgiCommandSupport {

    @Argument(index = 0, name = "TransportZoneName", description = "TransportZoneName", required = true, multiValued = false)
    private String tZoneName;
    @Argument(index = 1, name = "TunnelType", description = "Tunnel-Type", required = true, multiValued = false)
    private String tunnelType;


    private static final Logger LOG = LoggerFactory.getLogger(TepConfigureTunnelType.class);

    private IITMProvider itmProvider;

    public void setItmProvider(IITMProvider itmProvider) {
        this.itmProvider = itmProvider;
    }

    @Override
    protected Object doExecute() {
        try {
            LOG.debug("TepConfigureTunnelType: configureTunnelType {} for transportZone {}", tunnelType, tZoneName);
            itmProvider.configureTunnelType(tZoneName, tunnelType);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            LOG.error("Exception occurred during execution of command \"tep:configure-tunnelType\": ", e);
        }
        return null;
    }

}
