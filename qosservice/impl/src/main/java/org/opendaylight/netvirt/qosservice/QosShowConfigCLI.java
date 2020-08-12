/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Command(scope = "qos", name = "show-config", description = "qos show config")
public class QosShowConfigCLI implements org.apache.karaf.shell.commands.Action {

    @Argument(index = 0, name = "<show-config>", description = "qos show config",
            required = false, multiValued = false)


    private QosNeutronUtils qosNeutronUtils;
    private QosAlertManager qosAlertManager;

    private static final Logger LOG = LoggerFactory.getLogger(QosShowConfigCLI.class);

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        this.qosAlertManager = qosAlertManager;
        LOG.trace("Qos manager:{} set", qosAlertManager);
    }

    public void setQosNeutronUtils(QosNeutronUtils qosNeutronUtils) {
        this.qosNeutronUtils = qosNeutronUtils;
        LOG.trace("Qos neutron utils:{} set", qosNeutronUtils);
    }


    @Override
    public Object execute(CommandSession session) throws Exception {
        qosAlertManager.displayQosConfig(session);
        qosNeutronUtils.displayQosConfig(session);
        return null;
    }
}
