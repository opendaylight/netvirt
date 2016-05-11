/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.server;

import java.util.*;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.opendaylight.netvirt.bgpmanager.FibDSWriter;
import org.opendaylight.netvirt.bgpmanager.BgpManager;
import org.opendaylight.netvirt.bgpmanager.BgpConfigurationManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.BgpUpdater;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpThriftService {
    int ourPort;
    BgpManager bgpManager;
    FibDSWriter fibDSWriter;
    TServer server;
    // to store copy fo FIB-VRF tables on QBGP restart.
    public List<VrfTables> stale_vrfTables;

    private static final Logger LOGGER =
        LoggerFactory.getLogger(BgpThriftService.class);
     
    public BgpThriftService(int ourPort, BgpManager bm) {
        this.ourPort = ourPort;
        bgpManager = bm;
        fibDSWriter = bm.getFibWriter();
    }
 
    public class BgpUpdateServer implements Runnable, BgpUpdater.Iface {

        public void BgpUpdateServer() {
        }

        public void run() {
            try {
                BgpUpdater.Processor processor = new BgpUpdater.Processor(this);
                TServerTransport transport = new TServerSocket(ourPort);
                server = new TSimpleServer(new Args(transport).processor(processor));
                server.serve();
            } catch (Exception e) {
                LOGGER.error("Exception in BGP Updater server"+e);
            }
        }

        public void onUpdatePushRoute(String rd, String prefix, int plen,
                                                 String nexthop, int label) {
            try {
                BgpConfigurationManager.onUpdatePushRoute(rd, prefix, plen, nexthop, label);
            } catch (Throwable e) {
                LOGGER.error("failed to handle update route " ,e);
            }
        }

        public void onUpdateWithdrawRoute(String rd, String prefix, int plen) {
            LOGGER.debug("Route del ** {} ** {}/{} ", rd, prefix, plen);
            try {
                fibDSWriter.removeFibEntryFromDS(rd, prefix + "/" + plen);
            } catch (Throwable e) {
                LOGGER.error("failed to handle withdraw route " ,e);
            }
        }

        public void onStartConfigResyncNotification() {
            LOGGER.info("BGP (re)started");
            try {
                bgpManager.bgpRestarted();
            } catch (Throwable e) {
                LOGGER.error("failed to handle onStartConfigResyncNotification " ,e);
            }
        }

        public void onNotificationSendEvent(String prefix, byte errCode,
                                                           byte errSubcode) {
            int code = errCode;
            int subCode = errSubcode;
            bgpManager.sendNotificationEvent(prefix, errCode, errSubcode);
        }

    }

    Thread thread;

    public void start() {
        thread = new Thread(new BgpUpdateServer());
        thread.start();
    }

    public void stop() {
        server.stop();
        thread.stop();
    }
} 
 
