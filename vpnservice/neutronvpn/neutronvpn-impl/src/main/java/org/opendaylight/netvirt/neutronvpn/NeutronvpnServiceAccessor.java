/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import org.opendaylight.netvirt.elanmanager.api.IElanService;

/**
 * FIXME :This is a temp hack to access OSGi services from blueprint.
 * Can be removed after neutronvpn migration to blueprint is complete https://git.opendaylight.org/gerrit/#/c/39001/
 *
 */
public class NeutronvpnServiceAccessor {
    private static IElanService elanProvider;

    public NeutronvpnServiceAccessor(IElanService elanProvider) {
        NeutronvpnServiceAccessor.elanProvider = elanProvider;
    }

    public static IElanService getElanProvider() {
        return elanProvider;
    }

}
