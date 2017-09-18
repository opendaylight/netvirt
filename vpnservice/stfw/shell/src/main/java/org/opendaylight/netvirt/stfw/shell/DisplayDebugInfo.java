/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.shell;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.stfw.api.IStfwService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "stfw", name = "display-debug-info", description = "Display the debug/stats info")
public class DisplayDebugInfo extends OsgiCommandSupport {

    @Option(name = "-op", aliases = {"--options"}, description = "interface-count/flow-count",
        required = true, multiValued = false)
    String op;


    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayDebugInfo.class);
    private final IStfwService stfwService;

    public DisplayDebugInfo(IStfwService stfwService) {
        this.stfwService = stfwService;
    }

    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    protected Object doExecute() throws Exception {
        System.out.print("------------------------------------------------------------\n");
        System.out.print("                      DEBUG-INFO                            \n");
        switch (op) {
            case "interface-count":
                stfwService.displayInterfacesCount();
                break;
            case "flow-count":
                stfwService.displayFlowCount();
                break;
            default:
                break;
        }
        System.out.print("------------------------------------------------------------\n");
        return null;
    }

}

