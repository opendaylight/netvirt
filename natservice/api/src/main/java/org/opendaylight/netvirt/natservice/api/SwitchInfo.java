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

public final class SwitchInfo {

    Uint64 dpnId;

    Set<String> providerNets;

    public Uint64 getDpnId() {
        return dpnId;
    }

    public void setDpnId(Uint64 dpnId) {
        this.dpnId = dpnId;
    }

    public Set<String> getProviderNets() {
        return providerNets;
    }

    public void setProviderNets(Set<String> providerNets) {
        this.providerNets = providerNets;
    }


}
