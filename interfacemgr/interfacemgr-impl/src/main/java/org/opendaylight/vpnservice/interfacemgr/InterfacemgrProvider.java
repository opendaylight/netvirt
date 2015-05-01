/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr;

import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;

import java.util.concurrent.Future;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.idmanager.IdManager;
import java.math.BigInteger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import java.util.List;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfacemgrProvider implements BindingAwareProvider, AutoCloseable, IInterfaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(InterfacemgrProvider.class);

    private InterfaceManager interfaceManager;
    private IfmNodeConnectorListener ifmNcListener;
    private IdManager idManager;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("InterfacemgrProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            idManager = new IdManager(dataBroker);
            interfaceManager = new InterfaceManager(dataBroker, idManager);
            ifmNcListener = new IfmNodeConnectorListener(dataBroker, interfaceManager);
            createIdPool();
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
        //TODO: Make this debug
        LOG.info("Interfacemgr services initiated");
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
        .setPoolName("interfaces")
        .setIdStart(1L)
        .setPoolSize(new BigInteger("65535"))
        .build();
        //TODO: Error handling
        Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
//        try {
//            LOG.info("Result2: {}",result.get());
//        } catch (InterruptedException | ExecutionException e) {
//            // TODO Auto-generated catch block
//            LOG.error("Error in result.get");
//        }
    }

    @Override
    public void close() throws Exception {
        LOG.info("InterfacemgrProvider Closed");
        interfaceManager.close();
        ifmNcListener.close();
    }

    @Override
    public Long getPortForInterface(String ifName) {
        return interfaceManager.getPortForInterface(ifName);
    }

    @Override
    public long getDpnForInterface(String ifName) {
        return interfaceManager.getDpnForInterface(ifName);
    }

    @Override
    public String getEndpointIpForDpn(long dpnId) {
        return interfaceManager.getEndpointIpForDpn(dpnId);
    }

    @Override
    public List<MatchInfo> getInterfaceIngressRule(String ifName) {
        return interfaceManager.getInterfaceIngressRule(ifName);
    }

    @Override
    public List<InstructionInfo> getInterfaceEgressActions(String ifName) {
        return interfaceManager.getInterfaceEgressActions(ifName);
    }
}
