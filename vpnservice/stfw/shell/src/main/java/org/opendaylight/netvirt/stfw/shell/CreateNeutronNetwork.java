/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.shell;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.stfw.api.IStfwService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "stfw", name = "create-network", description = "Create N number of neutron network")
public class CreateNeutronNetwork extends OsgiCommandSupport {

    @Argument(index = 0, name = "number", description = "number of neutron network", required = true,
        multiValued = false)
    private int count;

    @Argument(index = 1, name = "vpn", description = "create-vpn/delete-vpn", required = false, multiValued = false)
    boolean vpn;

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateNeutronNetwork.class);
    private final IStfwService stfwService;

    public CreateNeutronNetwork(IStfwService stfwService) {
        this.stfwService = stfwService;
    }

    @Override
    protected Object doExecute() throws Exception {
        LOGGER.debug("START :: Executing createNeutronNetwork");
        LOGGER.debug(" Create VPN Yes/No {}" + vpn);
        stfwService.createNetwork(count, vpn);
        LOGGER.debug("END :: Executing createNeutronNetwork");
        return null;
    }
}
