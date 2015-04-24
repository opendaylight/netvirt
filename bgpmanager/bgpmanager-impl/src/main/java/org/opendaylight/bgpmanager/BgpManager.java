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

    private static final Logger s_logger = LoggerFactory.getLogger(BgpManager.class);
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
                s_logger.error("InterruptedException while waiting for Bgp connection to initialize");
                return;
            }
        }
    }

    public void startBgpService() throws TException {
        if(bgpThriftClient == null) {
            s_logger.info("Start Bgp Service - bgpThriftClient is null. Unable to start BGP service.");
            return;
        }

        // Now try start bgp - if bgp is already Active, it will tell us, nothing to do then
        try {
            bgpThriftClient.startBgp((int)bgpConfiguration.getAsNum(), bgpConfiguration.getRouterId());
            s_logger.info("Started BGP with AS number " + (int)bgpConfiguration.getAsNum() + " and router id " + bgpConfiguration.getRouterId());
        } catch (BgpRouterException be) {
            if(be.getErrorCode() == BgpRouterException.BGP_ERR_ACTIVE) {
                s_logger.info("bgp server already active");
                return;
            }
            else if(be.getErrorCode() == BgpRouterException.BGP_ERR_NOT_INITED) {
                s_logger.error("bgp server connection not initialized.");
                reInitConn();
                return;
            }
            else {
                s_logger.error("application error while starting bgp server " + be.getErrorCode());
                return;
            }

        }  catch (TException t) {
            s_logger.error("Could not set up thrift connection with bgp server");
            //s_logger.trace("Transport error while starting bgp server ", t);
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error("Error while starting bgp server");
            //s_logger.trace("Bgp Service not started due to exception", e);
            return;
        }

        hasBgpServiceStarted = true;

    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        s_logger.info("BgpManager Session Initiated");
        try {
            final DataBroker dataBroker = session.getSALService(DataBroker.class);
            bgpConfigurationMgr = new BgpConfigurationManager(dataBroker, bgpConfiguration, this);
            fibDSWriter = new FibDSWriter(dataBroker);
        } catch (Exception e) {
            s_logger.error("Error initializing services", e);
        }

        initializeBGPCommunication();
    }


   @Override
    public void close() throws Exception {
        s_logger.info("BgpManager Closed");

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
            s_logger.info("Add BGP Neighbor - bgpThriftClient is null. Unable to add BGP Neighbor.");
            return;
        }

        try {
            bgpThriftClient.addNeighbor(ipAddress, (int) asNum);
        } catch (BgpRouterException b) {
            s_logger.error("Failed to add BGP neighbor " + ipAddress + "due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
        } catch (TException t) {
            s_logger.error(String.format("Failed adding neighbor %s due to Transport error", ipAddress));
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error(String.format("Failed adding neighbor %s", ipAddress));
        }
    }


    protected void deleteNeighbor(String ipAddress) throws TException {
        if(bgpThriftClient == null) {
            s_logger.info("Delete BGP Neighbor - bgpThriftClient is null. Unable to delete BGP Neighbor.");
            return;
        }

        try {
            bgpThriftClient.delNeighbor(ipAddress);
        } catch (BgpRouterException b) {
            s_logger.error("Failed to delete BGP neighbor " + ipAddress + "due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
        }catch (TException t) {
            s_logger.error(String.format("Failed deleting neighbor %s due to Transport error", ipAddress));
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error(String.format("Failed deleting neighbor %s", ipAddress));
        }
    }


    @Override
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts) throws Exception {
        if(bgpThriftClient == null) {
            s_logger.info("Add BGP vrf - bgpThriftClient is null. Unable to add BGP vrf.");
            return;
        }
        try {
            bgpThriftClient.addVrf(rd, new ArrayList<>(importRts), new ArrayList<>(exportRts));
        } catch (BgpRouterException b) {
            s_logger.error("Failed to add BGP vrf " + rd + "due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            s_logger.error(String.format("Failed adding vrf %s due to Transport error", rd));
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error(String.format("Failed adding vrf %s", rd));
            throw e;
        }
    }

    @Override
    public void deleteVrf(String rd) throws Exception {
        if(bgpThriftClient == null) {
            s_logger.info("Delete BGP vrf - bgpThriftClient is null. Unable to delete BGP vrf.");
            return;
        }
        try {
            bgpThriftClient.delVrf(rd);
        } catch (BgpRouterException b) {
            s_logger.error("Failed to delete BGP vrf " + rd + "due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            s_logger.error(String.format("Failed deleting vrf %s due to Transport error", rd));
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error(String.format("Failed deleting vrf %s", rd));
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
            s_logger.error("Failed to add BGP prefix " + prefix + "due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            s_logger.error(String.format("Failed adding prefix entry <vrf:prefix:nexthop:vpnlabel> %s:%s:%s:%d due to Transport error",
                rd, prefix, nextHop, vpnLabel));
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error(String.format("Failed adding prefix entry <vrf:prefix:nexthop:vpnlabel> %s:%s:%s:%d",
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
            s_logger.error("Failed to delete BGP prefix " + prefix + "due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            s_logger.error(String.format("Failed deleting prefix entry <vrf:prefix> %s:%s due to Transport error",
                rd, prefix));
            reInitConn();
            throw t;
        } catch (Exception e) {
            s_logger.error(String.format("Failed deleting prefix entry <vrf:prefix> %s:%s",
                rd, prefix));
            throw e;
        }
    }

    private void connectToServer(String host, int port) throws Exception {

        bgpHost = host;
        bgpPort = port;

        if(bgpThriftClient == null) {
            s_logger.error("Failed to connect to BGP server since Bgp Thrift Client is not initialized yet.");
            return;
        }
        try {
            bgpThriftClient.connect(host, port);
            s_logger.info("Connected to BGP server " + host + " on port " + port);
        } catch (BgpRouterException b) {
            s_logger.error("Failed to connect to BGP server " + host + " on port " + port + " due to BgpRouter Exception number " + b.getErrorCode());
            //_logger.error("BgpRouterException trace ", b);
            throw b;
        } catch (TException t) {
            s_logger.error("Failed to initialize BGP Connection due to Transport error ");
            throw t;
        }
        catch (Exception e) {
            s_logger.error("Failed to initialize BGP Connection ");
            throw e;
        }
    }

    public void configureBgp(long asNum, String routerId) {
        try {
            bgpConfiguration.setAsNum(asNum);
            bgpConfiguration.setRouterId(routerId);
        } catch(Throwable e) {
            s_logger.error("failed configuring bgp ",e);
        }
    }

    public synchronized void reInitConn() {

        try {
            bgpThriftClient.reInit();
            s_logger.info("Reinitialized connection to BGP Server " + bgpHost);
        } catch (BgpRouterException b) {
            s_logger.error("Failed to reinitialize connection to BGP server " + bgpHost + " on port " + bgpPort + " due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
        } catch (TException t) {
            s_logger.error("Failed to reinitialize BGP Connection due to Transport error.");
        }
        catch (Exception e) {
            s_logger.error("Failed to reinitialize BGP Connection.", e);
        }
    }

    /*public synchronized void startBgpSync() {
        boolean getRoutes = true;
        readBgpConfiguration();
        try {
            pushConfigurationToBgp();

        } catch (BgpRouterException b) {
            s_logger.error("Failed to push configuration to BGP due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
            if(b.getErrorCode() == BgpRouterException.BGP_ERR_INACTIVE)
                getRoutes = false;
        } catch (Exception e) {
            s_logger.error("Failed to push configuration to bgp ", e);
        }
        if(getRoutes == true)
            pullConfigurationFromBgp();
        //controllerResyncLatch.countDown();
    }*/

    /*public void waitForControllerBgpResync() {
        try {
            controllerResyncLatch.await();
        } catch (InterruptedException e) {
        }
    }*/

    /*private void pullConfigurationFromBgp() {
        //get routes from bgp server
        s_logger.info("Starting bgp route sync");
        try {
            bgpThriftClient.doRouteSync();
        } catch (BgpRouterException b) {
            s_logger.error("Failed BGP Route sync due to BgpRouter Exception number " + b.getErrorCode());
            s_logger.error("BgpRouterException trace ", b);
        } catch (Exception e) {
            s_logger.error("Failed to pull configuration from bgp ", e);
        }
    }*/

    /*private BgpConfiguration readBgpConfiguration() {
        if (cache != null) {
            bgpConfiguration = cache.get("bgpConfiguration");
            if (bgpConfiguration == null) {
                s_logger.info("Created bgp configuration cache");
                bgpConfiguration = new BgpConfiguration();
                cache.put("bgpConfiguration", bgpConfiguration);
            } else {
                s_logger.info("Using bgp configuration cache");
            }
        }
        return bgpConfiguration;
    }*/

    /*public synchronized void pushConfigurationToBgp() throws Exception {
        if (bgpConfiguration.getAsNum() == 0) {
            s_logger.error("No as num configured, Skipping the push configuration to bgp ");
            throw new BgpRouterException(BgpRouterException.BGP_ERR_INACTIVE);
            //return;
        }
        if(bgpThriftClient == null) {
            s_logger.error("bgpThriftClient is null. Skipping the push configuration to bgp.");
            throw new BgpRouterException(BgpRouterException.BGP_ERR_INACTIVE);
            //return;
        }

        try {
            bgpThriftClient.startBgp((int)bgpConfiguration.getAsNum(), bgpConfiguration.getRouterId());
            s_logger.info("Started BGP with AS number " + (int)bgpConfiguration.getAsNum() + " and router id " + bgpConfiguration.getRouterId());
        } catch (BgpRouterException be) {
            if(be.getErrorCode() == BgpRouterException.BGP_ERR_ACTIVE) {
                s_logger.info("bgp server already active");
                return;		//the assumption here is that bgp server is configured already with neighbor, vrfs and routes as well
            } if(be.getErrorCode() == BgpRouterException.BGP_ERR_INACTIVE) {
                s_logger.info("bgp server inactive");
                throw be;
            }

            else {
                s_logger.error("application error while starting bgp server %d", be.getErrorCode());
                return;
            }

        } catch (SocketTimeoutException to) {
            s_logger.error("Socket Timeout error while starting bgp server", to);
            return;
        } catch (TException t) {
            s_logger.error("Transport error while starting bgp server ", t);
            return;
        } catch (Exception e) {
            s_logger.error("Error while starting bgp server", e);
        }

        if (bgpConfiguration.getNeighbourIp().trim().length() > 0) {
            try {
                bgpThriftClient.addNeighbor(bgpConfiguration.getNeighbourIp(), bgpConfiguration.getNeighbourAsNum());
            } catch (TException t) {
                s_logger.error("Failed to push vrf to bgp due to Transport error" );
                //retry connection
                reInitConn();
                addNeighbor(bgpConfiguration.getNeighbourIp(), bgpConfiguration.getNeighbourAsNum());
            } catch (Exception e) {
                s_logger.error("Error while starting bgp server", e);
            }
        }

        Tenant tenant;
        try {
            tenant = tenantManager.getTenant("NEUTRON");
        } catch (TenantNotFoundException e) {
            s_logger.error("Tenant not found. Skipping push configuration to bgp.");
            return;
        }
        if (tenant != null) {
            int tenantId = tenant.getTenantId();

            Set<VpnInstanceInfo> vpnInfos = l3Manager.getVpnInstanceManager().getVpnsForTenant(tenantId);
            s_logger.info("Number of vpns to configure is "+vpnInfos.size());
            for (VpnInstanceInfo vpnInfo: vpnInfos) {
                try {
                    bgpThriftClient.addVrf(vpnInfo.getRouteDistinguisher(),
                        new ArrayList<>(vpnInfo.getRtImportList()),
                        new ArrayList<>(vpnInfo.getRtExportList()));
                } catch (TException t) {
                    s_logger.error("Failed to push vrf to bgp due to Transport error" );
                    //retry connection
                    reInitConn();
                    addVrf(vpnInfo.getRouteDistinguisher(), new ArrayList<>(vpnInfo.getRtImportList()),
                        new ArrayList<>(vpnInfo.getRtExportList()));
                } catch (Exception e) {
                    s_logger.error("Failed to push vrf to bgp ", e);
                }
            }
            for (VpnInstanceInfo vpnInfo: vpnInfos) {
                ConcurrentMap<FibInfo, Object>  fibInfos = l3Manager.getVpnInstanceManager().
                    getLocalFibInfosForRdCache(vpnInfo.getRouteDistinguisher());
                s_logger.info("Number of fib infos to configure is "+fibInfos.size());
                for (FibInfo fibInfo : fibInfos.keySet()) {
                    try {
                        bgpThriftClient.addPrefix(vpnInfo.getRouteDistinguisher(), fibInfo.getDestinationPrefix(),
                            fibInfo.getNextHopPrefix(), (int) fibInfo.getLabel());
                    } catch (TException t) {
                        s_logger.error("Failed to push route to bgp due to Transport error" );
                        reInitConn();
                        addPrefix(vpnInfo.getRouteDistinguisher(), fibInfo.getDestinationPrefix(),
                            fibInfo.getNextHopPrefix(), (int) fibInfo.getLabel());
                    } catch (Exception e) {
                        s_logger.error("Failed to push route to bgp ", e);
                    }
                }
            }
        }

    }
    */

    public void disconnect() {
        bgpThriftClient.disconnect();
    }


}
