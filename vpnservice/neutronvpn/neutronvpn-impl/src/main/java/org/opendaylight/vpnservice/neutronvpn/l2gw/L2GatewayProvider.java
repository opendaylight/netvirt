/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.neutronvpn.l2gw;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class L2GatewayProvider {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayProvider.class);

    private L2GatewayListener l2GatewayListener;

    public L2GatewayProvider(DataBroker broker, RpcProviderRegistry rpcRegistry,
            EntityOwnershipService entityOwnershipService) {
        L2GatewayCacheUtils.createL2DeviceCache();
        l2GatewayListener = new L2GatewayListener(broker, rpcRegistry, entityOwnershipService);
        LOG.info("L2GatewayProvider Initialized");
    }

    public void close() throws Exception {
        l2GatewayListener.close();
        LOG.info("L2GatewayProvider Closed");
    }
}
