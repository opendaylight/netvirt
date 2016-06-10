/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.netvirt.aclservice.api.AclService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclServiceProvider implements BindingAwareProvider, AclService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AclServiceProvider.class);

    private DataBroker broker;

    public AclServiceProvider() {
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        broker = session.getSALService(DataBroker.class);
        LOG.info("ACL Service Initiated");
    }

    @Override
    public void close() throws Exception {
        LOG.info("ACL Service closed");
     }
}