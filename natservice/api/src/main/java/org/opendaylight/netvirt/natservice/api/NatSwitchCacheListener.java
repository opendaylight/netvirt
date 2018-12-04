/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.api;

public interface NatSwitchCacheListener {

    /**
     * Switch is added.
     * @param switchInfo the switch details.
     */
    void switchAddedToCache(SwitchInfo switchInfo);

    /**
     * Switch is removed.
     * @param switchInfo the switch details.
     */
    void switchRemovedFromCache(SwitchInfo switchInfo);

}
