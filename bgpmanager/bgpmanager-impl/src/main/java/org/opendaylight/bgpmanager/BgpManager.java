/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.bgpmanager;


import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

import org.apache.thrift.TException;
import org.opendaylight.bgpmanager.thrift.client.globals.Route;
import org.opendaylight.bgpmanager.thrift.client.implementation.BgpRouter;
import org.opendaylight.bgpmanager.thrift.server.implementation.BgpThriftService;
import org.opendaylight.bgpmanager.thrift.exceptions.BgpRouterException;
import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.bgpmanager.globals.BgpConfiguration;
import org.opendaylight.bgpmanager.globals.BgpConstants;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpManager implements BindingAwareProvider, AutoCloseable, IBgpManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BgpManager.class);
    private BgpConfigurationManager bgpConfigurationMgr;
    private FibDSWriter fibDSWriter;
    private BgpConfiguration 	bgpConfiguration = new BgpConfiguration();
    private BgpRouter           bgpThriftClient;
    private BgpThriftService    bgpThriftService;
    private boolean             isBgpInitialized = false;
    private boolean             hasBgpServiceStarted = false;
    private String				bgpHost;
    private int					bgpPort;


    private String getCustomConfig(String var, String def) {
        Bundle b = FrameworkUtil.getBundle(this.getClass());
        BundleContext context = null;
        if (b != null) {
            context = b.getBundleContext();
        }
        if (context != null)
            return context.getProperty(var);
        else
            return def;

    }

    private void initializeBGPCommunication() {
        //start our side of thrift server
        bgpThriftService = new BgpThriftService(this, fibDSWriter);
        bgpThriftService.start();

        //start bgp thrift client connection
        bgpThriftClient = new BgpRouter();

        bgpHost = getCustomConfig(BgpConstants.BGP_SPEAKER_HOST_NAME, BgpConstants.DEFAULT_BGP_HOST_NAME);
        bgpPort = BgpConstants.DEFAULT_BGP_THRIFT_PORT;

        configureBgpServer(bgpHost, bgpPort);
        try {
            connectToServer(bgpHost, bgpPort);
        } catch (Exception e) {
            return;
        }

        isBgpInitialized = true;
        //notify();       //notify all threads waiting for bgp init

    }

    public synchronized void waitForBgpInit() {
        if(!isBgpInitialized) {
            try {
                wait();
            } catch (InterruptedException e) {
                LOGGER.error("InterruptedException while waiting for Bgp connection to initialize");
                return;
            }
        }
    }

    public void startBgpService() throws TException {
        if(bgpThriftClient == null) {
            LOGGER.error("Start Bgp Service - bgpThriftClient is null. Unable to start BGP service.");
            return;
        }

        // Now try start bgp - if bgp is already Active, it will tell us, nothing to do then
        try {
            bgpThriftClient.startBgp((int)bgpConfiguration.getAsNum(), bgpConfiguration.getRouterId());
            LOGGER.debug("Started BGP with AS number " + (int)bgpConfiguration.getAsNum() + " and router id " + bgpConfiguration.getRouterId());
        } catch (BgpRouterException be) {
            if(be.getErrorCode() == BgpRouterException.BGP_ERR_ACTIVE) {
                LOGGER.info("bgp server already active");
                return;
            }
            else if(be.getErrorCode() == BgpRouterException.BGP_ERR_NOT_INITED) {
                LOGGER.error("bgp server connection not initialized.");
                reInitConn();
                return;
            }
            else {
                LOGGER.error("application error while starting bgp server " + be.getErrorCode());
                return;
            }

        }  catch (TException t) {
            LOGGER.error("Could not set up thrift connection with bgp server");
            LOGGER.debug("Transport error while starting bgp server ", t);
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error("Error while starting bgp server");
            LOGGER.debug("Bgp Service not started due to exception", e);
            return;
        }

        hasBgpServiceStarted = true;

    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOGGER.info("BgpManager Session Initiated");
        try {
            final DataBroker dataBroker = session.getSALService(DataBroker.class);
            bgpConfigurationMgr = new BgpConfigurationManager(dataBroker, bgpConfiguration, this);
            fibDSWriter = new FibDSWriter(dataBroker);
        } catch (Exception e) {
            LOGGER.error("Error initializing services", e);
        }

        initializeBGPCommunication();
    }


   @Override
    public void close() throws Exception {
        LOGGER.info("BgpManager Closed");

       //close the client and server ends of the thrift communication
       if(bgpThriftClient != null)
           bgpThriftClient.disconnect();
       bgpThriftService.stop();


   }

    private void setBgpServerDetails() {
        if(bgpThriftClient != null)
            bgpThriftClient.setBgpServer(bgpHost, bgpPort);
    }

    private void configureBgpServer(String bgpServer, int bgpPort) {
        bgpConfiguration.setBgpServer(bgpServer);
        bgpConfiguration.setBgpPort(bgpPort);
        setBgpServerDetails();
    }

    protected void addNeighbor(String ipAddress, long asNum) throws TException {
        if(bgpThriftClient == null) {
            LOGGER.error("Add BGP Neighbor - bgpThriftClient is null. Unable to add BGP Neighbor.");
            return;
        }

        try {
            bgpThriftClient.addNeighbor(ipAddress, (int) asNum);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to add BGP neighbor " + ipAddress + " due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
        } catch (TException t) {
            LOGGER.error(String.format("Failed adding neighbor %s due to Transport error", ipAddress));
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error(String.format("Failed adding neighbor %s", ipAddress));
        }
    }


    protected void deleteNeighbor(String ipAddress) throws TException {
        if(bgpThriftClient == null) {
            LOGGER.error("Delete BGP Neighbor - bgpThriftClient is null. Unable to delete BGP Neighbor.");
            return;
        }

        try {
            bgpThriftClient.delNeighbor(ipAddress);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to delete BGP neighbor " + ipAddress + "due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
        }catch (TException t) {
            LOGGER.error(String.format("Failed deleting neighbor %s due to Transport error", ipAddress));
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error(String.format("Failed deleting neighbor %s", ipAddress));
        }
    }


    @Override
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts) throws Exception {
        if(bgpThriftClient == null) {
            LOGGER.error("Add BGP vrf - bgpThriftClient is null. Unable to add BGP vrf.");
            return;
        }
        try {
            bgpThriftClient.addVrf(rd, new ArrayList<>(importRts), new ArrayList<>(exportRts));
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to add BGP vrf " + rd + "due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            LOGGER.error(String.format("Failed adding vrf %s due to Transport error", rd));
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error(String.format("Failed adding vrf %s", rd));
            throw e;
        }
    }

    @Override
    public void deleteVrf(String rd) throws Exception {
        if(bgpThriftClient == null || !hasBgpServiceStarted) {
            LOGGER.debug("Delete BGP vrf - Unable to delete BGP vrf in BGP Server. Removing Vrf from local DS");
            fibDSWriter.removeVrfFromDS(rd);
            return;
        }

        try {
            bgpThriftClient.delVrf(rd);
            fibDSWriter.removeVrfFromDS(rd);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to delete BGP vrf " + rd + "due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            LOGGER.error(String.format("Failed deleting vrf %s due to Transport error", rd));
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error(String.format("Failed deleting vrf %s", rd));
            throw e;
        }
    }

    @Override
    public void addPrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception {

        if(bgpThriftClient == null || !hasBgpServiceStarted) {
            fibDSWriter.addFibEntryToDS(rd, prefix, nextHop, vpnLabel);
            return;
        }

        try {
            bgpThriftClient.addPrefix(rd, prefix, nextHop, vpnLabel);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to add BGP prefix " + prefix + "due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            LOGGER.error(String.format("Failed adding prefix entry <vrf:prefix:nexthop:vpnlabel> %s:%s:%s:%d due to Transport error",
                rd, prefix, nextHop, vpnLabel));
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error(String.format("Failed adding prefix entry <vrf:prefix:nexthop:vpnlabel> %s:%s:%s:%d",
                rd, prefix, nextHop, vpnLabel));
            throw e;
        }
    }


    @Override
    public void deletePrefix(String rd, String prefix) throws Exception {
        if(bgpThriftClient == null || !hasBgpServiceStarted) {
            fibDSWriter.removeFibEntryFromDS(rd, prefix);
            return;
        }

        try {
            bgpThriftClient.delPrefix(rd, prefix);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to delete BGP prefix " + prefix + "due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            LOGGER.error(String.format("Failed deleting prefix entry <vrf:prefix> %s:%s due to Transport error",
                rd, prefix));
            reInitConn();
            throw t;
        } catch (Exception e) {
            LOGGER.error(String.format("Failed deleting prefix entry <vrf:prefix> %s:%s",
                rd, prefix));
            throw e;
        }
    }

    private void connectToServer(String host, int port) throws Exception {

        bgpHost = host;
        bgpPort = port;

        if(bgpThriftClient == null) {
            LOGGER.error("Failed to connect to BGP server since Bgp Thrift Client is not initialized yet.");
            return;
        }
        try {
            bgpThriftClient.connect(host, port);
            LOGGER.debug("Connected to BGP server {} on port {} ", host, port);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to connect to BGP server " + host + " on port " + port + " due to BgpRouter Exception number " + b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            LOGGER.error("Failed to initialize BGP Connection due to Transport error ");
            throw t;
        }
        catch (Exception e) {
            LOGGER.error("Failed to initialize BGP Connection ");
            throw e;
        }
    }

    public void configureBgp(long asNum, String routerId) {
        try {
            bgpConfiguration.setAsNum(asNum);
            bgpConfiguration.setRouterId(routerId);
        } catch(Exception e) {
            LOGGER.error("failed configuring bgp ",e);
        }
    }

    public synchronized void reInitConn() {

        try {
            bgpThriftClient.reInit();
            LOGGER.debug("Reinitialized connection to BGP Server {}", bgpHost);
        } catch (BgpRouterException b) {
            LOGGER.error("Failed to reinitialize connection to BGP server {} on port {} due to BgpRouter Exception number {}", bgpHost, bgpPort, b.getErrorCode());
            LOGGER.debug("BgpRouterException trace ", b);
        } catch (TException t) {
            LOGGER.error("Failed to reinitialize BGP Connection due to Transport error.");
        }
        catch (Exception e) {
            LOGGER.error("Failed to reinitialize BGP Connection.", e);
        }
    }

    public void disconnect() {
        bgpThriftClient.disconnect();
    }


}
