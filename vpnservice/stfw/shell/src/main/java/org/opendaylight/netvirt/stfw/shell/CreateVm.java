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

@Command(scope = "stfw", name = "create-VM", description = "Create N number of VMs per subnet")
public class CreateVm extends OsgiCommandSupport {

    @Argument(index = 0, name = "number", description = "number of VMs per subnet", required = true,
        multiValued = false)
    private int count;

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateVm.class);
    private final IStfwService stfwService;

    public CreateVm(IStfwService stfwService) {
        this.stfwService = stfwService;
    }

    @Override
    protected Object doExecute() throws Exception {
        LOGGER.debug("START :: Executing createVMs");
        stfwService.createVMs(count);
        LOGGER.debug("END :: Executing createVMs");
        return null;
    }

}

