/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;

public class DhcpPktHandler implements AutoCloseable, PacketProcessingListener {
    
    private static final Logger LOG = LoggerFactory.getLogger(DhcpPktHandler.class);
    private final DataBroker dataBroker;

    public DhcpPktHandler(final DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    public void onPacketReceived(PacketReceived pktReceived) {
        LOG.info("Pkt received: {}",pktReceived);
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
        
    }

}
