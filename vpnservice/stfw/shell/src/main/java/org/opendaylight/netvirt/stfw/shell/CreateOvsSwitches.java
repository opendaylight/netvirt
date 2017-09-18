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

@Command(scope = "stfw", name = "create-ovs", description = "Create N number of OVS Switches")
public class CreateOvsSwitches extends OsgiCommandSupport {

    @Argument(index = 0, name = "number", description = "number of OVS switches", required = true, multiValued = false)
    private int count;

    @Argument(index = 1, name = "auto-mesh", description = "auto-create full mesh", required = false,
        multiValued = false)
    private boolean autoMesh;

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateOvsSwitches.class);
    private final IStfwService stfwService;

    public CreateOvsSwitches(IStfwService stfwService) {
        this.stfwService = stfwService;
    }

    @Override
    protected Object doExecute() throws Exception {
        LOGGER.debug("START:: Executing createOvsSwitches");
        stfwService.createOvsSwitches(count, autoMesh);
        LOGGER.debug("END:: Executing createOvsSwitches");
        return null;
    }

}

