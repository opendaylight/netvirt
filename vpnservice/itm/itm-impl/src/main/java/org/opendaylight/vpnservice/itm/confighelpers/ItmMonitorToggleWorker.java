/*
 * Copyright (c) 2015, 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.ExternalTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.tunnel.list.InternalTunnel;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by eanraju on 23-Mar-16.
 */
public class ItmMonitorToggleWorker implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger logger = LoggerFactory.getLogger(ItmMonitorToggleWorker.class) ;
    private DataBroker dataBroker;
    private String tzone;
    private boolean enabled;
    private List<HwVtep> hwVteps;
    private  Boolean exists;

  public  ItmMonitorToggleWorker(List<HwVtep> hwVteps,String tzone,boolean enabled, DataBroker dataBroker, Boolean exists){
        this.dataBroker = dataBroker;
        this.tzone = tzone;
        this.enabled = enabled;
        this.hwVteps = hwVteps;
        this.exists = exists;
        logger.trace("ItmMonitorToggleWorker initialized with  tzone {} and toggleBoolean {}",tzone,enabled );
    }

    @Override public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>() ;
        logger.debug("Invoking Tunnel Monitor Worker tzone = {} enabled {}",tzone,enabled );
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        toggleTunnelMonitoring(hwVteps,enabled,tzone,t,exists);
        futures.add(t.submit());
        return futures;
    }

    private void toggleTunnelMonitoring(List<HwVtep> hwVteps,Boolean enabled, String tzone, WriteTransaction t,Boolean exists) {
        //exists means hwVteps exist for this tzone

        List<String> TunnelList = ItmUtils.getTunnelsofTzone(hwVteps,tzone,dataBroker,exists);
        if(TunnelList !=null &&!TunnelList.isEmpty()) {
            for (String tunnel : TunnelList)
                toggle(tunnel, enabled,t);
        }
    }

    private void toggle(String tunnelInterfaceName, boolean enabled, WriteTransaction t) {
        if(tunnelInterfaceName!=null) {
            InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(tunnelInterfaceName);
            IfTunnel tunnel = new IfTunnelBuilder().setMonitorEnabled(enabled).build();
            InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(tunnelInterfaceName))
                            .addAugmentation(IfTunnel.class, tunnel);
            t.merge(LogicalDatastoreType.CONFIGURATION, trunkIdentifier, builder.build());
        }
    }
}




