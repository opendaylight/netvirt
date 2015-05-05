package org.opendaylight.bgpmanager.thrift.server.implementation;

import org.opendaylight.bgpmanager.BgpManager;
import org.opendaylight.bgpmanager.FibDSWriter;
import org.opendaylight.bgpmanager.thrift.gen.BgpUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BgpUpdateHandler implements BgpUpdater.Iface {

    private static final Logger LOGGER = LoggerFactory.getLogger(BgpUpdateHandler.class);
    private BgpManager bgpManager;
    private FibDSWriter fibDSWriter;

    public BgpUpdateHandler(BgpManager bgpMgr, FibDSWriter dsWriter) {
        bgpManager = bgpMgr;
        fibDSWriter = dsWriter;
    }

    public void onUpdatePushRoute(String rd, String prefix, int plen,
                                String nexthop, int label) {

       LOGGER.info("Route add ** " + rd + " ** " + prefix + "/" + plen
               + " ** " + nexthop + " ** " + label);
        //Write to FIB in Data Store
        fibDSWriter.addFibEntryToDS(rd, prefix + "/" + plen, nexthop, label);

   }

   public void onUpdateWithdrawRoute(String rd, String prefix, int plen) {
       LOGGER.info("Route del ** " + rd + " ** " + prefix + "/" + plen);
       fibDSWriter.removeFibEntryFromDS(rd, prefix + "/" + plen);

   }

   public void onStartConfigResyncNotification() {
       LOGGER.info("BGP (re)started");
       bgpManager.reInitConn();
   }

}

