/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.impl;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.itm.listeners.TransportZoneListener;
import org.opendaylight.vpnservice.itm.rpc.ItmManagerRpcService;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.GetTunnelIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.GetTunnelIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.ItmStateService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.rev150701.transport.zones.transport.zone.*;;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItmProvider implements BindingAwareProvider, AutoCloseable, IITMProvider,ItmStateService {

    private static final Logger LOG = LoggerFactory.getLogger(ItmProvider.class);
    private IInterfaceManager interfaceManager;
    private ITMManager itmManager;
    private IMdsalApiManager mdsalManager;
    private DataBroker dataBroker;
    private NotificationPublishService notificationPublishService;
    private ItmManagerRpcService itmRpcService ;
    private NotificationService notificationService;
    private TransportZoneListener tzChangeListener;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("ItmProvider Session Initiated");
        try {
            dataBroker = session.getSALService(DataBroker.class);

            itmManager = new ITMManager(dataBroker);
            tzChangeListener = new TransportZoneListener(dataBroker) ;
            itmRpcService = new ItmManagerRpcService(dataBroker);

            itmManager.setMdsalManager(mdsalManager);
            itmManager.setNotificationPublishService(notificationPublishService);
            itmManager.setMdsalManager(mdsalManager);
            tzChangeListener.setItmManager(itmManager);
            tzChangeListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }
    
    public void setMdsalApiManager(IMdsalApiManager mdsalMgr) {
        this.mdsalManager = mdsalMgr;
    }
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void close() throws Exception {
        if (itmManager != null) {
            itmManager.close();
        }
        if (tzChangeListener != null) {
            tzChangeListener.close();
        }

        LOG.info("ItmProvider Closed");
    }

    @Override
    public Future<RpcResult<GetTunnelIdOutput>> getTunnelId(
         GetTunnelIdInput input) {
         // TODO Auto-generated method stub
         return null;
    }

}
