/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.api;

import java.util.Set;

import org.opendaylight.yangtools.yang.common.Uint64;

public interface NatSwitchCache {

    /**
     * Adds a switch to the scheduler pool.
     * @param dpnId the switch id.
     */
    void addSwitch(Uint64 dpnId);

    /**
     * Removes a switch from the scheduler pool.
     * @param dpnId the switch id.
     */
    void removeSwitch(Uint64 dpnId);

    /**
     * Check whether the switch has external bridge mappings.
     * @param dpnId the switch id.
     * @param providerNet the provider network.
     * @return whether connected to provider network or not.
     */
    boolean isSwitchConnectedToExternal(Uint64 dpnId, String providerNet);

    /**
     * Return the switches which has external bridge mappings.
     * @param providerNet the provider network.
     * @return the set of switches which has the mapping
     */
    Set<Uint64> getSwitchesConnectedToExternal(String providerNet);

    /**
     * Register for switch added notification.
     * @param centralizedSwitchCacheListener the instance of a listener
     */
    void register(NatSwitchCacheListener centralizedSwitchCacheListener);


}
