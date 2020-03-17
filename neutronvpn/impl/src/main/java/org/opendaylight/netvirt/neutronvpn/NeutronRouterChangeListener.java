/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.router.external_gateway_info.ExternalFixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronRouterChangeListener extends AbstractAsyncDataTreeChangeListener<Router> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final NeutronSubnetGwMacResolver gwMacResolver;
    private final NeutronvpnUtils neutronvpnUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public NeutronRouterChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
                                       final NeutronvpnNatManager neutronvpnNatManager,
                                       final NeutronSubnetGwMacResolver gwMacResolver,
                                       final NeutronvpnUtils neutronvpnUtils,
                                       final JobCoordinator jobCoordinator) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Neutron.class)
                .child(Routers.class).child(Router.class),
                Executors.newSingleThreadExecutor("NeutronRouterChangeListener", LOG));
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        nvpnNatManager = neutronvpnNatManager;
        this.gwMacResolver = gwMacResolver;
        this.neutronvpnUtils = neutronvpnUtils;
        this.jobCoordinator = jobCoordinator;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void add(InstanceIdentifier<Router> identifier, Router input) {
        LOG.trace("Adding Router : key: {}, value={}", identifier, input);
        neutronvpnUtils.addToRouterCache(input);
        // Create internal VPN
        nvpnManager.createL3InternalVpn(input.getUuid(), null, null, null, null, null, input.getUuid(), null);
        jobCoordinator.enqueueJob(input.getUuid().toString(), () -> {
            nvpnNatManager.handleExternalNetworkForRouter(null, input);
            return Collections.emptyList();
        });
        gwMacResolver.sendArpRequestsToExtGateways(input);
    }

    @Override
    public void remove(InstanceIdentifier<Router> identifier, Router input) {
        LOG.trace("Removing router : key: {}, value={}", identifier, input);
        Uuid routerId = input.getUuid();
        // Handle router deletion for the NAT service
        /*External Router and networks is handled before deleting the internal VPN, as there is dependency
        on vpn operational data to release Lport tag in case of L3VPN over VxLAN*/
        if (input.getExternalGatewayInfo() != null) {
            Uuid extNetId = input.getExternalGatewayInfo().getExternalNetworkId();
            List<ExternalFixedIps> externalFixedIps = input.getExternalGatewayInfo().getExternalFixedIps();
            jobCoordinator.enqueueJob(input.getUuid().toString(), () -> {
                nvpnNatManager.removeExternalNetworkFromRouter(extNetId, input, externalFixedIps);
                return Collections.emptyList();
            });
        }
        //NOTE: Pass an empty routerSubnetIds list, as router interfaces
        //will be removed from VPN by invocations from NeutronPortChangeListener
        List<Uuid> routerSubnetIds = new ArrayList<>();
        nvpnManager.handleNeutronRouterDeleted(routerId, routerSubnetIds);

        neutronvpnUtils.removeFromRouterCache(input);
        nvpnNatManager.removeNeutronRouterDpns(input);
    }

    @Override
    public void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        LOG.trace("Updating Router : key: {}, original value={}, update value={}", identifier, original, update);
        neutronvpnUtils.addToRouterCache(update);
        Uuid routerId = update.getUuid();
        neutronvpnUtils.addToRouterCache(update);
        Uuid vpnId = neutronvpnUtils.getVpnForRouter(routerId, true);
        // internal vpn always present in case external vpn not found
        if (vpnId == null) {
            vpnId = routerId;
        }
        List<Routes> oldRoutes = new ArrayList<>(original.nonnullRoutes());
        List<Routes> newRoutes = new ArrayList<>(update.nonnullRoutes());
        if (!oldRoutes.equals(newRoutes)) {
            Iterator<Routes> iterator = newRoutes.iterator();
            while (iterator.hasNext()) {
                Routes route = iterator.next();
                if (oldRoutes.remove(route)) {
                    iterator.remove();
                }
            }

            LOG.debug("Updating Router : AddRoutes {}, DeleteRoutes {}", newRoutes, oldRoutes);
            if (!oldRoutes.isEmpty()) {
                handleChangedRoutes(vpnId, oldRoutes, NwConstants.DEL_FLOW);
            }

            //After initial extra-route configuration using cmd-"neutron router-update RouterA destination=IP-A,
            // nexthop=prefix-A",if another update is done using command - "neutron router-update RouterA
            // destination=IP-A,nexthop=prefix-B",neutron router listener calls update on prefix-A as well as prefix-B.
            // On prefix-A , secondary adj (IP-A) is removed ,where as its added on prefix-B. This back-to-back update
            // creates race-condition in Vrf Engine ,leading inconsistencies in l3nexthop, VpnExtraRoute,
            // VpnInterfaceOp DS. Hence a temporary fix of 2sec delay is introduced in neutron.
            // A better fix/design need to be thought to avoid race condition
            try {
                Thread.sleep(2000); // sleep for 2sec
            } catch (java.lang.InterruptedException e) {
                LOG.error("Exception while sleeping", e);
            }

            if (!newRoutes.isEmpty()) {
                handleChangedRoutes(vpnId, newRoutes, NwConstants.ADD_FLOW);
            }
        }

        jobCoordinator.enqueueJob(update.getUuid().toString(), () -> {
            nvpnNatManager.handleExternalNetworkForRouter(original, update);
            return Collections.emptyList();
        });
        gwMacResolver.sendArpRequestsToExtGateways(update);
    }

    private void handleChangedRoutes(Uuid vpnName, List<Routes> routes, int addedOrRemoved) {
        // Some routes may point to an InterVpnLink's endpoint, lets treat them differently
        List<Routes> interVpnLinkRoutes = new ArrayList<>();
        List<Routes> otherRoutes = new ArrayList<>();
        HashMap<String, InterVpnLink> nexthopsXinterVpnLinks = new HashMap<>();
        for (Routes route : routes) {
            String nextHop = route.getNexthop().stringValue();
            // Nexthop is another VPN?
            Optional<InterVpnLink> interVpnLink = neutronvpnUtils.getInterVpnLinkByEndpointIp(nextHop);
            if (interVpnLink.isPresent()) {
                Optional<InterVpnLinkState> interVpnLinkState =
                        neutronvpnUtils.getInterVpnLinkState(interVpnLink.get().getName());
                if (interVpnLinkState.isPresent() && interVpnLinkState.get().getState()
                        == InterVpnLinkState.State.Active) {
                    interVpnLinkRoutes.add(route);
                    nexthopsXinterVpnLinks.put(nextHop, interVpnLink.get());
                } else {
                    LOG.error("Failed installing route to {}. Reason: InterVPNLink {} is not Active",
                            route.getDestination().stringValue(), interVpnLink.get().getName());
                }
            } else {
                otherRoutes.add(route);
            }
        }

        if (addedOrRemoved == NwConstants.ADD_FLOW) {
            nvpnManager.addInterVpnRoutes(vpnName, interVpnLinkRoutes, nexthopsXinterVpnLinks);
            nvpnManager.updateVpnInterfaceWithExtraRouteAdjacency(vpnName, otherRoutes);
        } else {
            nvpnManager.removeAdjacencyforExtraRoute(vpnName, otherRoutes);
            nvpnManager.removeInterVpnRoutes(vpnName, interVpnLinkRoutes, nexthopsXinterVpnLinks);
        }
    }
}
