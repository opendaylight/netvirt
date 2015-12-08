/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

import org.opendaylight.controller.liblldp.Packet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;

/**
 * Protocol specific Handler interface defined by the Aliveness monitor service
 * Handler will be registered with Alivnessmonitor service along with the protocol type
 * it supports.
 *
 */
public interface AlivenessProtocolHandler {

    Class<?> getPacketClass();

    String handlePacketIn(Packet protocolPacket, PacketReceived packetReceived);

    void sendPacketOut(MonitoringInfo monitorInfo);

    String getUniqueMonitoringKey(MonitoringInfo monitorInfo);
}
