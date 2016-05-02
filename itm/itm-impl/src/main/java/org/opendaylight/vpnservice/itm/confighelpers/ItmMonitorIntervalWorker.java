/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by eanraju on 23-Mar-16.
 */
public class ItmMonitorIntervalWorker implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger logger = LoggerFactory.getLogger(ItmMonitorIntervalWorker.class) ;
    private DataBroker dataBroker;
    private String tzone;
    private Integer interval;
    private List<HwVtep> hwVteps;
    private  Boolean exists;

    public ItmMonitorIntervalWorker(List<HwVtep> hwVteps,String tzone,Integer interval, DataBroker dataBroker, Boolean exists){
        this.dataBroker = dataBroker;
        this.tzone = tzone;
        this.interval = interval;
        this.hwVteps = hwVteps;
        this.exists = exists;
        logger.trace("ItmMonitorToggleWorker initialized with  tzone {} and Interval {}",tzone,interval );
    }

    @Override public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>() ;
        logger.debug("Invoking Tunnel Monitor Worker tzone = {} Interval= {}",tzone,interval );
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        toggleTunnelMonitoring(hwVteps,interval,tzone,t,exists);
        futures.add(t.submit());
        return futures;
    }

    private void toggleTunnelMonitoring(List<HwVtep> hwVteps,Integer interval, String tzone, WriteTransaction t,Boolean exists) {
        //exists means hwVteps exist for this tzone

        //List<String> TunnelList = ItmUtils.getTunnelsofTzone(hwVteps, tzone, dataBroker, exists);
        List<String> TunnelList = ItmUtils.getInternalTunnelsofTzone(tzone,dataBroker);
        if(TunnelList !=null &&!TunnelList.isEmpty()) {
            for (String tunnel : TunnelList)
                toggle(tunnel, interval,t);
        }
    }

    private void toggle(String tunnelInterfaceName, Integer interval, WriteTransaction t) {
        if (tunnelInterfaceName != null) {
            logger.debug("tunnel {} will have monitor interval {}", tunnelInterfaceName, interval);
            InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(tunnelInterfaceName);
            IfTunnel tunnel = new IfTunnelBuilder().setMonitorInterval(interval.longValue() * 1000).build();
            InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(tunnelInterfaceName))
                            .addAugmentation(IfTunnel.class, tunnel);
            t.merge(LogicalDatastoreType.CONFIGURATION, trunkIdentifier, builder.build());
        }
    }
}


