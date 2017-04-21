/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.shell;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.vpnmanager.LdhDataTreeChangeListenerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "ldh", name = "debug",
        description = "get counters for Listener Dependency Helper")
public class LdhCommandHelper extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LdhDataTreeChangeListenerBase.class);
    @Option(name = "--detail", description = "print all HashMaps and lists", required = false, multiValued = false)
    String detailString;

    @Override
    protected Object doExecute() {
        LOG.debug("LDHdebugHelper: debug ");
        LdhDataTreeChangeListenerBase.ldhDebug(detailString, session);
        return null;
    }

}

