/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "qos", name = "display-alert-config",
        description = "display qos alert configurations")
public class QosAlertConfigCLI implements org.apache.karaf.shell.commands.Action {

    private QosAlertManager qosAlertManager;
    private QosNeutronUtils qosNeutronUtils;

    private static final Logger LOG = LoggerFactory.getLogger(QosAlertConfigCLI.class);

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        LOG.trace("Qos manager:{} set", qosAlertManager);
        this.qosAlertManager = qosAlertManager;
    }

    public void setQosNeutronUtils(QosNeutronUtils qosNeutronUtils) {
        LOG.debug("Qos neutron utils :{} set", qosNeutronUtils);
        this.qosNeutronUtils = qosNeutronUtils;
    }

    @Override
    public Object execute(CommandSession session) throws Exception {
        qosAlertManager.displayConfig(session);
        qosNeutronUtils.displayConfig(session);
        return null;
    }

}

