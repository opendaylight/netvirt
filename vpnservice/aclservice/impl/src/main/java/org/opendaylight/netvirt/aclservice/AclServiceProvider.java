/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.listeners.AclEventListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceStateListener;
import org.opendaylight.netvirt.aclservice.listeners.AclNodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclServiceProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceProvider.class);

    private DataBroker broker;
    private IMdsalApiManager mdsalManager;
    private RpcProviderRegistry rpcProviderRegistry;
    private AclServiceManager aclServiceManager;
    private AclInterfaceStateListener aclInterfaceStateListener;
    private AclNodeListener aclNodeListener;
    private AclInterfaceListener aclInterfaceListener;
    private AclEventListener aclEventListener;

    /**
     * Set the rpc registery.
     *
     * @param rpcProviderRegistry the rpc registery instance.
     */
    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    /**
     * Set the mdsal manager.
     *
     * @param mdsalManager the mdsal manager instance.
     */
    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        broker = session.getSALService(DataBroker.class);
        aclServiceManager = new AclServiceManagerImpl();
        aclServiceManager.addAclServiceListner(new IngressAclServiceImpl(broker, mdsalManager));
        aclServiceManager.addAclServiceListner(new EgressAclServiceImpl(broker, mdsalManager));
        aclInterfaceStateListener = new AclInterfaceStateListener(aclServiceManager);
        aclInterfaceStateListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);
        aclNodeListener = new AclNodeListener(mdsalManager);
        aclNodeListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);
        aclInterfaceListener = new AclInterfaceListener(aclServiceManager, broker);
        aclInterfaceListener.registerListener(LogicalDatastoreType.CONFIGURATION, broker);
        aclEventListener = new AclEventListener(aclServiceManager);
        aclEventListener.registerListener(LogicalDatastoreType.CONFIGURATION, broker);

        LOG.info("ACL Service Initiated");
    }

    @Override
    public void close() throws Exception {
        aclInterfaceStateListener.close();
        aclNodeListener.close();
        aclInterfaceListener.close();
        aclEventListener.close();

        LOG.info("ACL Service closed");
    }
}
