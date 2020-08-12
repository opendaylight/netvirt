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


@Command(scope = "qos", name = "enable-qos-alert",
                        description = "configuration to enable or disable QoS packet drop alert")
public class QosAlertEnableCLI extends OsgiCommandSupport {

    @Argument(index = 0, name = "<value>", description = "true|false - enable/disable QoS packet drop alert",
                                                                              required = true, multiValued = false)
    private String value;

    private QosAlertManager qosAlertManager;

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertEnableCLI.class);

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        this.qosAlertManager = qosAlertManager;
        LOG.trace("Qos manager:{} set", qosAlertManager);
    }

    @Override
    protected Object doExecute() {
        LOG.debug("Setting poll enable in qos alert manager:{}", value);
        qosAlertManager.setEnable(Boolean.parseBoolean(value));
        return null;
    }

}
