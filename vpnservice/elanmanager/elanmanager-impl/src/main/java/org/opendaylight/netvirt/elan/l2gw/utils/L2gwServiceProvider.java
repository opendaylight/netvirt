/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.utils;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elanmanager.api.IL2gwService;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by eaksahu on 3/15/2017.
 */
@Singleton
public class L2gwServiceProvider extends AbstractLifecycle implements IL2gwService {

    private static final Logger LOG = LoggerFactory.getLogger(L2gwServiceProvider.class);
    private final DataBroker dataBroker;
    private final ItmRpcService itmRpcService;
    private final ElanUtils elanUtils;
    private final EntityOwnershipService entityOwnershipService;

    @Inject
    public L2gwServiceProvider(final DataBroker dataBroker, final EntityOwnershipService entityOwnershipService,
                               final ItmRpcService itmRpcService, final ElanUtils elanUtils) {
        this.dataBroker = dataBroker;
        this.entityOwnershipService = entityOwnershipService;
        this.itmRpcService = itmRpcService;
        this.elanUtils = elanUtils;
    }

    @Override
    public void provisionItmAndL2gwConnection(L2GatewayDevice l2GwDevice, String psName,
                                              String hwvtepNodeId, IpAddress tunnelIpAddr) {
        ElanClusterUtils.runOnlyInLeaderNode(entityOwnershipService,
                "Handling Physical Switch add create itm tunnels ", () -> {
                ElanL2GatewayUtils.createItmTunnels(itmRpcService, hwvtepNodeId, psName, tunnelIpAddr);
                return Collections.emptyList();
            });

        List<L2gatewayConnection> l2GwConns = L2GatewayConnectionUtils.getAssociatedL2GwConnections(
                dataBroker, l2GwDevice.getL2GatewayIds());
        if (l2GwConns != null) {
            LOG.debug("L2GatewayConnections associated for {} physical switch", psName);
            for (L2gatewayConnection l2GwConn : l2GwConns) {
                LOG.trace("L2GatewayConnection {} changes executed on physical switch {}",
                        l2GwConn.getL2gatewayId(), psName);
                elanUtils.getL2GatewayConnectionUtils().addL2GatewayConnection(l2GwConn, psName);
            }
        }
    }

    @Override
    protected void start() throws Exception {
        LOG.info("Starting L2gwServiceProvider");
    }

    @Override
    protected void stop() throws Exception {
        LOG.info("Stopping L2gwServiceProvider");
    }
}
