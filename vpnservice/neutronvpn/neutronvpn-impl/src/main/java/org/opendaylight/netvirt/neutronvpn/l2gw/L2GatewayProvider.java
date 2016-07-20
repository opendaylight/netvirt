/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.l2gw;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L2GatewayProvider {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayProvider.class);

    private L2GatewayListener l2GatewayListener;
    private L2GwTransportZoneListener l2GwTZoneListener;

    public L2GatewayProvider(DataBroker broker, RpcProviderRegistry rpcRegistry,
                             EntityOwnershipService entityOwnershipService) {
        ItmRpcService itmRpcService = rpcRegistry.getRpcService(ItmRpcService.class);

        L2GatewayCacheUtils.createL2DeviceCache();
        l2GatewayListener = new L2GatewayListener(broker, rpcRegistry, entityOwnershipService);
        l2GwTZoneListener = new L2GwTransportZoneListener(broker, itmRpcService);
        l2GwTZoneListener.registerListener(LogicalDatastoreType.CONFIGURATION, broker);

        LOG.info("L2GatewayProvider Initialized");
    }

    public void close() throws Exception {
        l2GatewayListener.close();
        l2GwTZoneListener.close();
        LOG.info("L2GatewayProvider Closed");
    }
}
