/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.api;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;

public interface SnatServiceManager {

    enum Action {
        SNAT_ALL_SWITCH_ENBL,
        SNAT_ALL_SWITCH_DISBL,
        SNAT_ROUTER_ENBL,
        SNAT_ROUTER_DISBL,
        SNAT_ROUTER_UPDATE
    }

    void addNatServiceListener(SnatServiceListener aclServiceListner);

    void removeNatServiceListener(SnatServiceListener aclServiceListner);

    void notify(Routers router, Routers oldRouter, BigInteger primarySwitchId, BigInteger dpnId, Action action);

}
