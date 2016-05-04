/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.lockmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;

import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LockManagerServiceProvider implements BindingAwareProvider,
            AutoCloseable {

        private static final Logger LOG = LoggerFactory.getLogger(LockManagerServiceProvider.class);
        private LockManager lockManager;
        private RpcProviderRegistry rpcProviderRegistry;

    public RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

        @Override
    public void onSessionInitiated(ProviderContext session){
        LOG.info("LockManagerServiceProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            lockManager = new LockManager(dataBroker);
            final BindingAwareBroker.RpcRegistration<LockManagerService> rpcRegistration = getRpcProviderRegistry().addRpcImplementation(LockManagerService.class, lockManager);
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    public LockManagerServiceProvider(RpcProviderRegistry rpcRegistry) {
        this.rpcProviderRegistry = rpcRegistry;
    }

    @Override
    public void close() throws Exception {
        }
    }