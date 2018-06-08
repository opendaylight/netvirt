/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netvirt.vpnmanager.api.ICentralizedSwitchProvider;

@Singleton
public class CentralizedSwitchProvider implements ICentralizedSwitchProvider {

    private final VpnUtil vpnUtil;

    @Inject
    public CentralizedSwitchProvider(VpnUtil vpnUtil) {
        this.vpnUtil = vpnUtil;

    }

    @Override
    public BigInteger getPrimarySwitchForRouter(String routerName) {
        return vpnUtil.getPrimarySwitchForRouter(routerName);
    }

}
