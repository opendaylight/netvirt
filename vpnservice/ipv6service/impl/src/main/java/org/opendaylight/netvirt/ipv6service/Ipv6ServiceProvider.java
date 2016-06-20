/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ServiceProvider implements BindingAwareProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceProvider.class);

    private DataBroker broker;

    public Ipv6ServiceProvider() {
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        broker = session.getSALService(DataBroker.class);
        LOG.info("IPv6 Service Initiated");
    }

    @Override
    public void close() throws Exception {
        LOG.info("IPv6 Service closed");
     }
}
