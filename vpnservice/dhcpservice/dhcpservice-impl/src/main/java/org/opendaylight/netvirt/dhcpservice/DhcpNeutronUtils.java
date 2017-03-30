/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp_allocation_pool.rev161214.dhcp_allocation_pool.network.allocation.pool.StaticRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.HostRoutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.HostRoutesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnet.attributes.HostRoutesKey;

public class DhcpNeutronUtils {

    public static List<HostRoutes> convertStaticRoutesToHostRoutes(List<StaticRoutes> staticRoutesList) {
        List<HostRoutes> hostRoutesList = new ArrayList<>();
        if (staticRoutesList != null) {
            for (StaticRoutes dhcpHostRoutes : staticRoutesList) {
                HostRoutes hostRoutes = new HostRoutesBuilder()
                        .setKey(new HostRoutesKey(dhcpHostRoutes.getDestination()))
                        .setDestination(dhcpHostRoutes.getDestination())
                        .setNexthop(dhcpHostRoutes.getNexthop()).build();
                hostRoutesList.add(hostRoutes);
            }
            return hostRoutesList;
        }
        return null;
    }

}
