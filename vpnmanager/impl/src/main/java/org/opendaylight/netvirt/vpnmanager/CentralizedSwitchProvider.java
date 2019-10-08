/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netvirt.vpnmanager.api.ICentralizedSwitchProvider;
import org.opendaylight.yangtools.yang.common.Uint64;

@Singleton
public class CentralizedSwitchProvider implements ICentralizedSwitchProvider {

    private final VpnUtil vpnUtil;

    @Inject
    public CentralizedSwitchProvider(VpnUtil vpnUtil) {
        this.vpnUtil = vpnUtil;

    }

    @Override
    @Nullable
    public Uint64 getPrimarySwitchForRouter(String routerName) {
        return vpnUtil.getPrimarySwitchForRouter(routerName);
    }

}
