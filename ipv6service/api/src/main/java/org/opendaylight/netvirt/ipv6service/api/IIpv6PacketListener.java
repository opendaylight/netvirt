/*
 * Copyright (c) 2018 Alten Calsoft Labs India Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.api;

import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.packet.rev160620.NeighborAdvertisePacket;

public interface IIpv6PacketListener {

    /**
     * On IPv6 Neighbor Advertisement packet received.
     *
     * @param naPacket the IPv6 NA packet
     */
    void onNaReceived(NeighborAdvertisePacket naPacket);
}
