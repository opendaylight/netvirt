/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManagerProvider implements BindingAwareProvider, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FibManagerProvider.class);

  private FibManager fibManager;

  @Override
  public void onSessionInitiated(ProviderContext session) {
    LOG.info("FibManagerProvider Session Initiated");
    try {
      final  DataBroker dataBroker = session.getSALService(DataBroker.class);
      fibManager = new FibManager(dataBroker);
    } catch (Exception e) {
      LOG.error("Error initializing services", e);
    }
  }

  @Override
  public void close() throws Exception {
    LOG.info("FibManagerProvider Closed");
    fibManager.close();
  }

}