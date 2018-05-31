/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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

@Command(scope = "qos", name = "show-cache", description = "qos cache display")
public class QosCacheCLI extends OsgiCommandSupport {

    @Argument(index = 0, name = "<cache>", description = "qos cache display",
            required = false, multiValued = false)

    private QosAlertManager qosAlertManager;
    private QosNeutronUtils qosNeutronUtils;

    private static final Logger LOG = LoggerFactory.getLogger(QosCacheCLI.class);

    public void setQosAlertManager(QosAlertManager qosAlertManager) {
        LOG.debug("Qos manager:{} set", qosAlertManager);
        this.qosAlertManager = qosAlertManager;
    }

    public void setQosNeutronUtils(QosNeutronUtils qosNeutronUtils) {
        LOG.debug("Qos neutron utils :{} set", qosNeutronUtils);
        this.qosNeutronUtils = qosNeutronUtils;
    }

    @Override
    protected Object doExecute() throws Exception {
        qosAlertManager.displayCache(session);
        qosNeutronUtils.displayCache(session);
        return null;
    }
}
