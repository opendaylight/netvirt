/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.api;

import java.util.List;
import java.util.Set;

import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;

public interface IL2gwService {
    void provisionItmAndL2gwConnection(L2GatewayDevice l2GwDevice, String psName,
                                       String hwvtepNodeId, IpAddress tunnelIpAddr);

    List<L2gatewayConnection> getL2GwConnectionsByL2GatewayId(Uuid l2GatewayId);

    void addL2GatewayConnection(L2gatewayConnection input);

    void addL2GatewayConnection(L2gatewayConnection input,
                                String l2GwDeviceName,
                                L2gateway l2Gateway);

    List<L2gatewayConnection> getAssociatedL2GwConnections(Set<Uuid> l2GatewayIds);
}
