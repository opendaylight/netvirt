/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.shell;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.stfw.api.IStfwService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "stfw", name = "delete-VM", description = "Delete all VMs per subnet")
public class DeleteVm extends OsgiCommandSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteVm.class);
    private final IStfwService stfwService;

    public DeleteVm(IStfwService stfwService) {
        this.stfwService = stfwService;
    }

    @Override
    protected Object doExecute() throws Exception {
        LOGGER.debug("START :: Executing deleteVMs");
        stfwService.deleteVMs();
        LOGGER.debug("END :: Executing deleteVMs");
        return null;
    }

}

