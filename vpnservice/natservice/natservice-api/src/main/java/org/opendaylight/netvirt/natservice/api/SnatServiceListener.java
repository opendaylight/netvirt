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
     * Configures the SNAT flows when an external router is added.
     * @param router the router.
     * @return returns success/failure.
     */
    boolean addExternalRouter(Routers router);

    /**
     * Updates the SNAT flows based on the router event.
     * @param routerBefore the router before update
     * @param routerAfter the router after update
     * @return returns success/failure.
     */
    boolean updateExternalRouter(Routers routerBefore, Routers routerAfter);

    /**
     * Removes the snat flows when the router is removed.
     * @param router the router.
     * @return returns success/failure.
     */
    boolean removeExternalRouter(Routers router);

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

}
