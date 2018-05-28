/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "qos", name = "alert-poll-interval", description = "configure polling interval in minutes")
public class QosAlertPollIntervalCLI extends OsgiCommandSupport {

    @Argument(index = 0, name = "<interval>", description = "polling interval in minutes",
            required = true, multiValued = false)
    private String interval;

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertPollIntervalCLI.class);

    private QosAlertManager qosAlertManager;

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        this.qosAlertManager = qosAlertManager;
        LOG.trace("Qos manager:{} set", qosAlertManager);
    }

    @Override
    protected Object doExecute() {
        LOG.debug("setting threshold in qos alert manager:{}", interval);
        qosAlertManager.setPollInterval(Integer.parseInt(interval));
        return null;
    }

}
