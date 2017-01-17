/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosalert;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Command(scope = "qos", name = "alert-log-file-name", description = "set Qos alert log file name")
public class QosAlertLogFileNameCLI extends OsgiCommandSupport {

    @Argument(index = 0, name = "<file-name>", description = "relative or full path of qos alert log file",
            required = true, multiValued = false)
    private String fileName;
    private static final Logger LOG = LoggerFactory.getLogger(
            org.opendaylight.netvirt.qosalert.QosAlertLogFileNameCLI.class);

    private QosAlertManager qosAlertManager;

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        this.qosAlertManager = qosAlertManager;
        LOG.info("Qos manager:{} set", qosAlertManager);
    }

    @Override
    protected Object doExecute() throws Exception {
        LOG.info("setting alert file name in qos alert manager:{}", fileName);
        qosAlertManager.setQosAlertLogFileName(fileName);
        return null;
    }
}