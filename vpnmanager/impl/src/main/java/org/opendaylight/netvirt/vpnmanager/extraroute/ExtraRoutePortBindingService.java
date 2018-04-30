/*
 * Copyright © 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.extraroute;

import com.google.common.base.Optional;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.neutronvpn.api.enums.Action;
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
    public ExtraRoutePortBindingService(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void bindIfPresent(final String vpnName, final String interfaceName, final String nextHop) {
        LOG.debug("bindIfPresent: Check and bind extra-route for interface {} vpn {} nexthop {}", interfaceName,
                vpnName, nextHop);
        updateExtraRouteAdjacency(vpnName, interfaceName, nextHop, Action.ADD);
    }

    @Override
    public void unbindIfPresent(String vpnName, String interfaceName, String nextHop) {
        LOG.debug("unbindIfPresent: Check and unbind extra-route for interface {} vpn {} nexthop {}", interfaceName,
                vpnName, nextHop);
        updateExtraRouteAdjacency(vpnName, interfaceName, nextHop, Action.REMOVE);
    }

    private void updateExtraRouteAdjacency(String vpnName, String interfaceName, String nextHop, Action action) {
        String lockKey = vpnName + "-" + nextHop;
        synchronized (lockKey.intern()) {
            try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
                InstanceIdentifier<Vpn> vpnIdentifier = InstanceIdentifier.builder(ExtraRouteAdjacency.class)
                        .child(Vpn.class, new VpnKey(vpnName)).build();
                Optional<Vpn> extraRoutesForVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, vpnIdentifier);
                if (extraRoutesForVpn.isPresent()) {
                    List<Destination> existingExtraRoutesOnVpn = extraRoutesForVpn.get().getDestination();
                    if (existingExtraRoutesOnVpn != null && !existingExtraRoutesOnVpn.isEmpty()) {
                        for (Destination extraRoute : existingExtraRoutesOnVpn) {
                            String extraRouteIp = extraRoute.getDestinationIp();
                            List<NextHop> existingNextHops = extraRoute.getNextHop();
                            if (existingNextHops != null && !existingNextHops.isEmpty()) {
                                for (NextHop existingNextHop : existingNextHops) {
                                    String nextHopIp = existingNextHop.getNextHopIp();
                                    if (nextHopIp.equals(nextHop)) {
                                        InstanceIdentifier<NextHop> nextHopIdentifier = InstanceIdentifier
                                                .builder(ExtraRouteAdjacency.class).child(Vpn.class,
                                                        new VpnKey(vpnName)).child(Destination.class,
                                                        new DestinationKey(extraRouteIp)).child(NextHop.class,
                                                        new NextHopKey(nextHopIp)).build();
                                        NextHopBuilder nextHopBuilder = new NextHopBuilder(existingNextHop);
                                        switch (action) {
                                            case ADD: {
                                                if (existingNextHop.getInterfaceName() == null) {
                                                    NextHop updatedNextHop = nextHopBuilder
                                                            .setInterfaceName(interfaceName).build();
                                                    SingleTransactionDataBroker.syncWrite(dataBroker,
                                                            LogicalDatastoreType.OPERATIONAL, nextHopIdentifier,
                                                            updatedNextHop);
                                                } else {
                                                    LOG.warn("updateExtraRouteAdjacency: Nexthop {} on interface {}"
                                                            + " already bound on vpn {} Ignoring bind action", nextHop,
                                                            interfaceName, vpnName);
                                                }
                                            }
                                            break;
                                            case REMOVE: {
                                                if (existingNextHop.getInterfaceName() != null) {
                                                    NextHop updatedNextHop = nextHopBuilder
                                                            .setInterfaceName(null).build();
                                                    SingleTransactionDataBroker.syncWrite(dataBroker,
                                                            LogicalDatastoreType.OPERATIONAL, nextHopIdentifier,
                                                            updatedNextHop);
                                                } else {
                                                    LOG.warn("updateExtraRouteAdjacency: Nexthop {} already unbound"
                                                            + " on vpn {} Ignoring unbind action", nextHop,
                                                            interfaceName, vpnName);
                                                }
                                            }
                                            break;
                                            default: {
                                                LOG.error("updateExtraRouteAdjacency: Incorrect value for"
                                                        + " switch case.");
                                            }
                                            break;
                                        }
                                        return;
                                    }
                                }
                            } else {
                                LOG.info("updateExtraRouteAdjacency: No nextHops configured for extra-route {}"
                                        + " on vpn {} action {}", extraRouteIp, vpnName, action);
                            }
                        }
                    } else {
                        LOG.info("updateExtraRouteAdjacency: No extra routes configured on vpn {} action {}", vpnName,
                                action);
                    }
                }
            } catch (ReadFailedException e) {
                LOG.error("bindIfPresent: Error when reading existing extra routes for vpn {} action {}", vpnName,
                        action);
            } catch (TransactionCommitFailedException e) {
                LOG.error("bindIfPresent: Error when updating port binding information for extra routes on vpn {}"
                        + " action {}", vpnName, action);
            }
        }
    }
}
