/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/*
 * Copyright © 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.mdsal.eos.binding.api.*;
import org.opendaylight.netvirt.vpnmanager.api.IVpnClusterOwnershipDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class VpnClusterShardBasedOwnershipDriver extends VpnClusterOwnershipDriverBase {

    private static final Logger LOG = LoggerFactory.getLogger(VpnClusterShardBasedOwnershipDriver.class);

    // This file resides to provide an implementation where it will drive amIOwner based on where
    // the Default Operational Shard (or) Default Config is placed in the 3 PL environment
    @Inject
    public VpnClusterShardBasedOwnershipDriver(final EntityOwnershipService entityOwnershipService) {
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() throws Exception {
        LOG.info("{} closed", getClass().getSimpleName());
    }
}
