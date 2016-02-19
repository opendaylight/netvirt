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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.itm.listeners.TransportZoneListener;
import org.opendaylight.vpnservice.itm.rpc.ItmManagerRpcService;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;

import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItmProvider implements BindingAwareProvider, AutoCloseable, IITMProvider /*,ItmStateService */{

    private static final Logger LOG = LoggerFactory.getLogger(ItmProvider.class);
    private IInterfaceManager interfaceManager;
    private ITMManager itmManager;
    private IMdsalApiManager mdsalManager;
    private DataBroker dataBroker;
    private NotificationPublishService notificationPublishService;
    private ItmManagerRpcService itmRpcService ;
    private IdManagerService idManager;
    private NotificationService notificationService;
    private TransportZoneListener tzChangeListener;
    private RpcProviderRegistry rpcProviderRegistry;

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return this.rpcProviderRegistry;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("ItmProvider Session Initiated");
        try {
            dataBroker = session.getSALService(DataBroker.class);
            idManager = getRpcProviderRegistry().getRpcService(IdManagerService.class);

            itmManager = new ITMManager(dataBroker);
            tzChangeListener = new TransportZoneListener(dataBroker, idManager) ;
            itmRpcService = new ItmManagerRpcService(dataBroker, idManager);
            final BindingAwareBroker.RpcRegistration<ItmRpcService> rpcRegistration = getRpcProviderRegistry().addRpcImplementation(ItmRpcService.class, itmRpcService);
            itmRpcService.setMdsalManager(mdsalManager);
            itmManager.setMdsalManager(mdsalManager);
            itmManager.setNotificationPublishService(notificationPublishService);
            itmManager.setMdsalManager(mdsalManager);
            tzChangeListener.setMdsalManager(mdsalManager);
            tzChangeListener.setItmManager(itmManager);
            tzChangeListener.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            createIdPool();
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

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(ITMConstants.ITM_IDPOOL_NAME)
            .setLow(ITMConstants.ITM_IDPOOL_START)
            .setHigh(new BigInteger(ITMConstants.ITM_IDPOOL_SIZE).longValue())
            .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.debug("Created IdPool for ITM Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for ITM Service",e);
        }
    }
}
