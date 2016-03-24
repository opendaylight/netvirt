/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.cli;

import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.TransportZones;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Command(scope = "tep", name = "commit",
    description = "commits the configuration so that actual tunnel-building happens")
public class TepCommit extends OsgiCommandSupport {
  private static final Logger logger = LoggerFactory.getLogger(TepCommit.class);

  private IITMProvider itmProvider;

  public void setItmProvider(IITMProvider itmProvider) {
    this.itmProvider = itmProvider;
  }

  @Override
  protected Object doExecute() throws Exception {

    try {
      itmProvider.commitTeps();
      logger.debug("Executing commit TEP command");
    } catch (NullPointerException e) {
      e.printStackTrace();
    }
    return null;
  }
}
