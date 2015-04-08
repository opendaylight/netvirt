package org.opendaylight.bgpmanager.thrift.server.implementation;

import org.opendaylight.bgpmanager.thrift.gen.BgpUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class BgpUpdateHandler implements BgpUpdater.Iface {

    private static final Logger logger = LoggerFactory.getLogger(BgpUpdateHandler.class);

    public BgpUpdateHandler() {}

    public void onUpdatePushRoute(String rd, String prefix, int plen,
                                String nexthop, int label) {
       logger.info("Route add ** " + rd + " ** " + prefix + "/" + plen
               + " ** " + nexthop + " ** " + label);
        //Write to FIB in Data Store

   }

   public void onUpdateWithdrawRoute(String rd, String prefix, int plen) {
       logger.info("Route del ** " + rd + " ** " + prefix + "/" + plen);
       //Write to FIB in Data Store

   }

   public void onStartConfigResyncNotification() {
       logger.info("BGP (re)started");

        //Reconfigure BGP
   }

}

