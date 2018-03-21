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

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netvirt.vpnmanager.api.IVpnClusterOwnershipDriver;

@Singleton
public class VpnClusterOwnershipDriverBase implements IVpnClusterOwnershipDriver, AutoCloseable {

    public static final String VPN_SERVICE_ENTITY = "VPN_SERVICE";
    protected boolean amIOwner = false;

    @Inject
    public VpnClusterOwnershipDriverBase() { }

    @Override
    @PreDestroy
    public void close() throws Exception {}

    @Override
    public boolean amIOwner() {
        return amIOwner;
    }

}
