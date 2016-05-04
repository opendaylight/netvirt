/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "tep", name = "monitor-interval", description = "configuring tunnel monitoring time interval")
public class TepMonitor extends OsgiCommandSupport {

  @Argument(index = 0, name = "interval", description = "monitoring interval", required = true,
      multiValued = false)
  private Integer interval;

  private static final Logger logger = LoggerFactory.getLogger(TepMonitor.class);
  private IITMProvider itmProvider;

  public void setItmProvider(IITMProvider itmProvider) {
    this.itmProvider = itmProvider;
  }

  @Override
  protected Object doExecute() {
    try {
      logger.debug("Executing TEP monitor command with interval: " + "\t" + interval);
      if(!(interval >= 5 && interval <=30)){
          System.out.println("Monitoring Interval must be in the range 5 - 30");
      }
      else {
          itmProvider.configureTunnelMonitorInterval(interval);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
