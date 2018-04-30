/*
 * Copyright © 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.extraroute;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExtraRoutePortBindingService implements IExtraRoutePortBindingService {
    private static final Logger LOG = LoggerFactory.getLogger(ExtraRoutePortBindingService.class);

    @Inject
    public ExtraRoutePortBindingService () {

    }

    @Override
    public void bindIfPresent(String vpnName, String interfaceName, String nextHop) {
        LOG.debug("bindIfPresent: Check and bind extra-route for interface {} vpn {} nexthop {}", interfaceName,
                vpnName, nextHop);

    }

    @Override
    public void unbindIfPresent(String vpnName, String interfaceName, String nextHop) {
        LOG.debug("unbindIfPresent: Check and unbind extra-route for interface {} vpn {} nexthop {}", interfaceName,
                vpnName, nextHop);
    }
}
