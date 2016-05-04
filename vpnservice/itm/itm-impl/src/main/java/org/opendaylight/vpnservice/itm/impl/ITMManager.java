/*
 * Copyright (c) 2015, 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.impl;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.apache.commons.net.util.SubnetUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan ;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;

import com.google.common.base.Optional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorEnabled;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorInterval;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorEnabledBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.TunnelMonitorIntervalBuilder;

public class ITMManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ITMManager.class);

    private final DataBroker broker;
    private IMdsalApiManager mdsalManager;
    private NotificationPublishService notificationPublishService;

    List<DPNTEPsInfo> meshedDpnList;

    @Override
    public void close() throws Exception {
        LOG.info("ITMManager Closed");
    }

    public ITMManager(final DataBroker db) {
        broker = db;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }
    protected void initTunnelMonitorDataInConfigDS() {
        new Thread() {
            public void run() {
                boolean readSucceeded = false;
                InstanceIdentifier<TunnelMonitorEnabled> monitorPath = InstanceIdentifier.builder(TunnelMonitorEnabled.class).build();
                while (!readSucceeded) {
                    try {
                        Optional<TunnelMonitorEnabled> storedTunnelMonitor = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, monitorPath, broker);
                        // Store default values only when tunnel monitor data is not initialized
                        if (!storedTunnelMonitor.isPresent()) {
                            TunnelMonitorEnabled monitorEnabled =
                                    new TunnelMonitorEnabledBuilder().setEnabled(ITMConstants.DEFAULT_MONITOR_ENABLED).build();
                            ItmUtils.asyncUpdate(LogicalDatastoreType.CONFIGURATION, monitorPath, monitorEnabled, broker, ItmUtils.DEFAULT_CALLBACK);

                            InstanceIdentifier<TunnelMonitorInterval> intervalPath = InstanceIdentifier.builder(TunnelMonitorInterval.class).build();
                            TunnelMonitorInterval monitorInteval =
                                    new TunnelMonitorIntervalBuilder().setInterval(ITMConstants.DEFAULT_MONITOR_INTERVAL).build();
                            ItmUtils.asyncUpdate(LogicalDatastoreType.CONFIGURATION, intervalPath, monitorInteval, broker, ItmUtils.DEFAULT_CALLBACK);
                        }
                        readSucceeded = true;
                    } catch (Exception e) {
                        LOG.warn("Unable to read monitor enabled info; retrying after some delay");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                }
            }
        }.start();
    }

    protected boolean getTunnelMonitorEnabledFromConfigDS() {
        boolean tunnelMonitorEnabled = true;
        InstanceIdentifier<TunnelMonitorEnabled> path = InstanceIdentifier.builder(TunnelMonitorEnabled.class).build();
        Optional<TunnelMonitorEnabled> storedTunnelMonitor = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, broker);
        if (storedTunnelMonitor.isPresent()) {
            tunnelMonitorEnabled = storedTunnelMonitor.get().isEnabled();
        }
        return tunnelMonitorEnabled;
    }

    protected int getTunnelMonitorIntervalFromConfigDS() {
        int tunnelMonitorInterval = ITMConstants.DEFAULT_MONITOR_INTERVAL;
        InstanceIdentifier<TunnelMonitorInterval> path = InstanceIdentifier.builder(TunnelMonitorInterval.class).build();
        Optional<TunnelMonitorInterval> storedTunnelMonitor = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, path, broker);
        if (storedTunnelMonitor.isPresent()) {
            tunnelMonitorInterval = storedTunnelMonitor.get().getInterval();
        }
        return tunnelMonitorInterval;
    }
}
