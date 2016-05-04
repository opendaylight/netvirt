/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fcapsmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {
    static Logger s_logger = LoggerFactory.getLogger(Activator.class);
    public void start(BundleContext context) {
        s_logger.info("Starting fcapsSPI bundle");
    }

    public void stop(BundleContext context) {
        s_logger.info("Stopping fcapsSPI bundle");
    }

}