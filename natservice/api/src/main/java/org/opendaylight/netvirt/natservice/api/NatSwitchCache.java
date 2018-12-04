/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.api;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public interface NatSwitchCache {

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

    /**
     * Check whether the switch has external bridge mappings.
     * @param dpnId the switch id.
     * @param providerNet the provider network.
     * @return whether connected to provider network or not.
     */
    boolean isSwitchConnectedToExternal(BigInteger dpnId, String providerNet);

    /**
     * Return the switches which has external bridge mappings.
     * @param providerNet the provider network.
     * @return the set of switches which has the mapping
     */
    Set<BigInteger> getSwitchesConnectedToExternal(String providerNet);

    /**
     * Return the switches map with weight.
     * @return the map of switches
     */
    Map<BigInteger,SwitchInfo>  getSwitches();

    /**
     * Register for switch added notification.
     * @param centralizedSwitchCacheListener the instance of a listener
     */
    void register(NatSwitchCacheListener centralizedSwitchCacheListener);

    /**
     * Register for switch removed notification.
     * @param centralizedSwitchCacheListener the instance of a listener
     */
    void deregister(NatSwitchCacheListener centralizedSwitchCacheListener);


}
