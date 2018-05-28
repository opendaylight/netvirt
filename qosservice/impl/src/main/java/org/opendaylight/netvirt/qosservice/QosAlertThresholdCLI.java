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

@Command(scope = "qos", name = "drop-packet-threshold", description = "configure drop packet threshold in %")
public class QosAlertThresholdCLI extends OsgiCommandSupport {

    @Argument(index = 0, name = "<threshold>", description = "threshold value in % 1..100",
            required = true, multiValued = false)
    private String threshold;

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertThresholdCLI.class);

    private QosAlertManager qosAlertManager;

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        this.qosAlertManager = qosAlertManager;
        LOG.trace("Qos manager:{} set", qosAlertManager);
    }

    @Override
    protected Object doExecute() {
        LOG.debug("setting threshold in qos alert manager:{}", threshold);
        qosAlertManager.setThreshold(Short.parseShort(threshold));
        return null;
    }

}
