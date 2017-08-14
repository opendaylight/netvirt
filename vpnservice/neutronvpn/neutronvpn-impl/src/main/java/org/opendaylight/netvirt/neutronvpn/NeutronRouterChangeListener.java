/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
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
public class NeutronRouterChangeListener extends AsyncDataTreeChangeListenerBase<Router, NeutronRouterChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronRouterChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final NeutronSubnetGwMacResolver gwMacResolver;

    @Inject
    public NeutronRouterChangeListener(final DataBroker dataBroker, final NeutronvpnManager neutronvpnManager,
                                       final NeutronvpnNatManager neutronvpnNatManager,
                                       NeutronSubnetGwMacResolver gwMacResolver) {
        super(Router.class, NeutronRouterChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        nvpnNatManager = neutronvpnNatManager;
        this.gwMacResolver = gwMacResolver;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Router> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Routers.class).child(Router.class);
    }

    @Override
    protected NeutronRouterChangeListener getDataTreeChangeListener() {
        return NeutronRouterChangeListener.this;
    }


    @Override
    protected void add(InstanceIdentifier<Router> identifier, Router input) {
        LOG.trace("Adding Router : key: {}, value={}", identifier, input);
        NeutronvpnUtils.addToRouterCache(input);
        // Create internal VPN
        nvpnManager.createL3InternalVpn(input.getUuid(), null, null, null, null, null, input.getUuid(), null);
        nvpnNatManager.handleExternalNetworkForRouter(null, input);
        gwMacResolver.sendArpRequestsToExtGateways(input);
    }

    @Override
    protected void remove(InstanceIdentifier<Router> identifier, Router input) {
        LOG.trace("Removing router : key: {}, value={}", identifier, input);
        Uuid routerId = input.getUuid();
        // Handle router deletion for the NAT service
        /*External Router and networks is handled before deleting the internal VPN, as there is dependency
        on vpn operational data to release Lport tag in case of L3VPN over VxLAN*/
        if (input.getExternalGatewayInfo() != null) {
            Uuid extNetId = input.getExternalGatewayInfo().getExternalNetworkId();
            List<ExternalFixedIps> externalFixedIps = input.getExternalGatewayInfo().getExternalFixedIps();
            nvpnNatManager.removeExternalNetworkFromRouter(extNetId, input, externalFixedIps);
        }
        //NOTE: Pass an empty routerSubnetIds list, as router interfaces
        //will be removed from VPN by invocations from NeutronPortChangeListener
        List<Uuid> routerSubnetIds = new ArrayList<>();
        nvpnManager.handleNeutronRouterDeleted(routerId, routerSubnetIds);

        NeutronvpnUtils.removeFromRouterCache(input);
    }

    @Override
    protected void update(InstanceIdentifier<Router> identifier, Router original, Router update) {
        LOG.trace("Updating Router : key: {}, original value={}, update value={}", identifier, original, update);
        NeutronvpnUtils.addToRouterCache(update);
        Uuid routerId = update.getUuid();
        NeutronvpnUtils.addToRouterCache(update);
        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
        // internal vpn always present in case external vpn not found
        if (vpnId == null) {
            vpnId = routerId;
        }
        List<Routes> oldRoutes = (original.getRoutes() != null) ? original.getRoutes() : new ArrayList<>();
        List<Routes> newRoutes = (update.getRoutes() != null) ? update.getRoutes() : new ArrayList<>();
        if (!oldRoutes.equals(newRoutes)) {
            newRoutes.removeIf(oldRoutes::remove);

            handleChangedRoutes(vpnId, newRoutes, NwConstants.ADD_FLOW);

            if (!oldRoutes.isEmpty()) {
                handleChangedRoutes(vpnId, oldRoutes, NwConstants.DEL_FLOW);
            }
        }

        nvpnNatManager.handleExternalNetworkForRouter(original, update);
        gwMacResolver.sendArpRequestsToExtGateways(update);
    }

    private void handleChangedRoutes(Uuid vpnName, List<Routes> routes, int addedOrRemoved) {
        // Some routes may point to an InterVpnLink's endpoint, lets treat them differently
        List<Routes> interVpnLinkRoutes = new ArrayList<>();
        List<Routes> otherRoutes = new ArrayList<>();
        HashMap<String, InterVpnLink> nexthopsXinterVpnLinks = new HashMap<>();
        for (Routes route : routes) {
            String nextHop = String.valueOf(route.getNexthop().getValue());
            // Nexthop is another VPN?
            Optional<InterVpnLink> interVpnLink = NeutronvpnUtils.getInterVpnLinkByEndpointIp(dataBroker, nextHop);
            if (interVpnLink.isPresent()) {
                Optional<InterVpnLinkState> interVpnLinkState =
                        NeutronvpnUtils.getInterVpnLinkState(dataBroker, interVpnLink.get().getName());
                if (interVpnLinkState.isPresent()
                    && interVpnLinkState.get().getState() == InterVpnLinkState.State.Active) {
                    interVpnLinkRoutes.add(route);
                    nexthopsXinterVpnLinks.put(nextHop, interVpnLink.get());
                } else {
                    LOG.warn("Failed installing route to {}. Reason: InterVPNLink {} is not Active",
                            String.valueOf(route.getDestination().getValue()), interVpnLink.get().getName());
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
