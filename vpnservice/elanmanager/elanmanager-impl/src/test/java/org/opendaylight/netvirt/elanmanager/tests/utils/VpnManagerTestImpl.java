/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;

public abstract class VpnManagerTestImpl implements IVpnManager {

    @Override
    public String getVpnRd(DataBroker dataBroker, String evpnName) {
        return "100:1";
    }
}
