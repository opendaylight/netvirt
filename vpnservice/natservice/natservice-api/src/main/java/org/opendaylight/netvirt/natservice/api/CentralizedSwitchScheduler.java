/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.api;

import java.math.BigInteger;

public interface CentralizedSwitchScheduler {

    /**
     * Schedule the centralized switch for the router.
     * @param routerName the router name.
     * @return success/failure
     */
    boolean scheduleCentralizedSwitch(String routerName);

    /**
     * Releases the centralized switch scheduled for the router.
     * @param routerName the router name.
     * @return success/failure
     */
    boolean releaseCentralizedSwitch(String routerName);

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
