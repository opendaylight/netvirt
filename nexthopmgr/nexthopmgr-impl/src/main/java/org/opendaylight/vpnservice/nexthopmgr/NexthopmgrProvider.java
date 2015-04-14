/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.vpnservice.nexthopmgr.NexthopManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexthopmgrProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NexthopmgrProvider.class);
    private VpnInterfaceChangeListener vpnIfListener;
    private OdlInterfaceChangeListener odlIfListener;
    private NexthopManager nhManager;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        final  DataBroker dbx = session.getSALService(DataBroker.class);
        nhManager = new NexthopManager(dbx);
        vpnIfListener = new VpnInterfaceChangeListener(dbx, nhManager);
        odlIfListener = new OdlInterfaceChangeListener(dbx, nhManager);
        LOG.info("NexthopmgrProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        vpnIfListener.close();
        odlIfListener.close();
        nhManager.close();
        LOG.info("NexthopmgrProvider Closed");
    }

}
