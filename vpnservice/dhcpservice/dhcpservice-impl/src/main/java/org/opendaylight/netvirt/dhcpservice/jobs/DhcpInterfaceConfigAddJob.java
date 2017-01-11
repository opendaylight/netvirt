/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.jobs;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.dhcpservice.DhcpManager;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceConfigAddJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpInterfaceConfigAddJob.class);
    DhcpManager dhcpManager;
    DataBroker dataBroker;
    Interface iface;

    public DhcpInterfaceConfigAddJob(DhcpManager dhcpManager, DataBroker dataBroker, Interface add) {
        super();
        this.dhcpManager = dhcpManager;
        this.dataBroker = dataBroker;
        this.iface = add;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        String interfaceName = iface.getName();
        Port port = dhcpManager.getNeutronPort(interfaceName);
        Subnet subnet = dhcpManager.getNeutronSubnet(port);
        if (null != subnet && subnet.isEnableDhcp()) {
            WriteTransaction bindServiceTx = dataBroker.newWriteOnlyTransaction();
            LOG.info("Binding DHCP service for interface {}", interfaceName);
            DhcpServiceUtils.bindDhcpService(interfaceName, NwConstants.DHCP_TABLE, bindServiceTx);
            futures.add(bindServiceTx.submit());
        }
        return futures;
    }
}