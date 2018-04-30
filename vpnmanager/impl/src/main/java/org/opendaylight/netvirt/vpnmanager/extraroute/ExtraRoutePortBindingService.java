/*
 * Copyright © 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.extraroute;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.vpnmanager.api.extraroute.IExtraRoutePortBindingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ExtraRouteAdjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.Destination;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.DestinationKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.destination.NextHop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.destination.NextHopBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.extra.route.adjacency.vpn.destination.NextHopKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExtraRoutePortBindingService implements IExtraRoutePortBindingService {
    private static final Logger LOG = LoggerFactory.getLogger(ExtraRoutePortBindingService.class);
    private final DataBroker dataBroker;

    @Inject
    public ExtraRoutePortBindingService (DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void bindIfPresent(final String vpnName, final String interfaceName, final String nextHop) {
        LOG.debug("bindIfPresent: Check and bind extra-route for interface {} vpn {} nexthop {}", interfaceName,
                vpnName, nextHop);
        String lockKey = vpnName + "-" + nextHop;
        synchronized (lockKey.intern()) {
            try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
                InstanceIdentifier<Vpn> vpnIdentifier = InstanceIdentifier.builder(ExtraRouteAdjacency.class)
                        .child(Vpn.class, new VpnKey(vpnName)).build();
                Optional<Vpn> extraRoutesForVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, vpnIdentifier);
                if (extraRoutesForVpn.isPresent()) {
                    Optional<NextHop> existingNextHop = extraRoutesForVpn.get().stream()
                            .flatMap(extraRoute -> extraRoute.getNextHop().stream())
                            .filter(nextHopObject -> nextHopObject.getNextHopIp().equals(nextHop))
                            .findAny();
                    if (existingNextHop.isPresent()) {
                        NextHop extraRouteNextHop = existingNextHop.get();
                        String existingInterfaceName = extraRouteNextHop.getInterfaceName();
                        if (existingInterfaceName == null) {
                            String extraRoute = extraRouteNextHop.getKey().firstKeyOf(Destination.class)
                                    .getDestinationIp();
                            InstanceIdentifier<NextHop> nextHopIdentifier = InstanceIdentifier
                                    .builder(ExtraRouteAdjacency.class).child(Vpn.class, new VpnKey(vpnName))
                                    .child(Destination.class, new DestinationKey(extraRoute)).child(NextHop.class,
                                            new NextHopKey(nextHop)).build();
                            NextHop updatedNextHop = new NextHopBuilder(extraRouteNextHop)
                                    .setInterfaceName(interfaceName).build();
                            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                    nextHopIdentifier, updatedNextHop);
                        } else {
                            LOG.warn("bindIfPresent: Nexthop {} on interface {} already bound on vpn {}", nextHop,
                                    interfaceName, vpnName);
                        }
                    } else {
                        LOG.info("bindIfPresent: No extra-routes configures for nexthop {} vpn {}", nextHop, vpnName);
                    }
                }
            }
        }

    }

    @Override
    public void unbindIfPresent(String vpnName, String interfaceName, String nextHop) {
        LOG.debug("unbindIfPresent: Check and unbind extra-route for interface {} vpn {} nexthop {}", interfaceName,
                vpnName, nextHop);
    }
}
