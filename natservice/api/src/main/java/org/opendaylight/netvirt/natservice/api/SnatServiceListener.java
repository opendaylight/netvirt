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

public interface SnatServiceListener {

    /**
     * Adds/removes snat flows for all dpns having ports on the router subnet.
     * @param routers the router.
     * @param primarySwitchId the primaryswitchId
     * @param addOrRemove add or remove the flow.
     * @return returns success/failure.
     */
    boolean handleSnatAllSwitch(Routers routers, BigInteger primarySwitchId,  int addOrRemove);

    /**
     * Adds/removes snat flows for the dpnId.
     * @param routers the router.
     * @param primarySwitchId the primaryswitchId.
     * @param dpnId the dpnId for which the flows needs to be added/removed.
     * @param addOrRemove add or remove the flow.
     * @return returns success/failure.
     */
    boolean handleSnat(Routers routers, BigInteger primarySwitchId, BigInteger dpnId,  int addOrRemove);

    /**
     * Handles changes to external router.
     * @param origRouter the Orignal router.
     * @param updatedRouter the Updated router.
     * @return returns success/failure.
     */
    boolean handleRouterUpdate(Routers origRouter, Routers updatedRouter);
}
