/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.commands.Cache;

@Command(scope = "odl", name = "display-bgp-config", description="")
public class DisplayBgpConfigCli extends OsgiCommandSupport {

    protected Object doExecute() throws Exception {
        Cache cache = new Cache();
        return cache.show();
    } 
}
