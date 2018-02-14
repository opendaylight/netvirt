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

public interface CentralizedSwitchScheduler {

    /**
     * Schedule the centralized switch for the router.
     * @param router the external router.
     * @return success/failure
     */
    boolean scheduleCentralizedSwitch(Routers router);

    /**
     * Update the centralized switch scheduled for the router.
     * @param oldRouter the existing external router.
     * @param newRouter the new external router.
     * @return  success/failure
     */
    boolean updateCentralizedSwitch(Routers oldRouter, Routers newRouter);

    /**
     * Releases the centralized switch scheduled for the router.
     * @param router the external router.
     * @return success/failure
     */
    boolean releaseCentralizedSwitch(Routers router);

    /**
     * Retrieves the centralized switch scheduled for the router.
     * @param routerName the router name.
     * @return success/failure
     */
    boolean getCentralizedSwitch(String routerName);

    /**
     * Adds a switch to the scheduler pool.
     * @param dpnId the switch id.
     * @return success/failure
     */
    boolean addSwitch(BigInteger dpnId);

    /**
     * Removes a switch from the scheduler pool.
     * @param dpnId the switch id.
     * @return success/failure
     */
    boolean removeSwitch(BigInteger dpnId);

}
